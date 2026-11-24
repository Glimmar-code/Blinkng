-- Broadcast email unsubscribe support. Provider secrets are configured separately.
CREATE TABLE IF NOT EXISTS public.broadcast_email_unsubscribe_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  token_hash text NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS broadcast_email_unsubscribe_user_idx ON public.broadcast_email_unsubscribe_tokens(user_id,created_at DESC);
ALTER TABLE public.broadcast_email_unsubscribe_tokens ENABLE ROW LEVEL SECURITY;
CREATE POLICY broadcast_email_unsubscribe_admin_read ON public.broadcast_email_unsubscribe_tokens FOR SELECT TO authenticated USING(public.broadcast_admin_check());
GRANT SELECT ON public.broadcast_email_unsubscribe_tokens TO authenticated;
GRANT ALL ON public.broadcast_email_unsubscribe_tokens TO service_role;

CREATE OR REPLACE FUNCTION public.broadcast_email_unsubscribe(p_token text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_user uuid; v_hash text;
BEGIN
  IF p_token IS NULL OR length(p_token)<32 OR length(p_token)>256 THEN RETURN jsonb_build_object('ok',false); END IF;
  v_hash:=encode(extensions.digest(p_token,'sha256'),'hex');
  UPDATE public.broadcast_email_unsubscribe_tokens SET used_at=now()
    WHERE token_hash=v_hash AND used_at IS NULL AND expires_at>now() RETURNING user_id INTO v_user;
  IF v_user IS NULL THEN
    IF EXISTS(SELECT 1 FROM public.broadcast_email_unsubscribe_tokens WHERE token_hash=v_hash AND used_at IS NOT NULL) THEN
      RETURN jsonb_build_object('ok',true,'already_unsubscribed',true);
    END IF;
    RETURN jsonb_build_object('ok',false);
  END IF;
  INSERT INTO public.notification_prefs(user_id,email) VALUES(v_user,false) ON CONFLICT(user_id) DO UPDATE SET email=false,updated_at=now();
  RETURN jsonb_build_object('ok',true,'already_unsubscribed',false);
END
$$;
REVOKE EXECUTE ON FUNCTION public.broadcast_email_unsubscribe(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.broadcast_email_unsubscribe(text) TO anon,authenticated,service_role;
