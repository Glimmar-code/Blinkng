BEGIN;

CREATE OR REPLACE FUNCTION public.delete_my_account(p_confirmation text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,auth,pg_temp
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_username text;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'AUTHENTICATION_REQUIRED';
  END IF;

  SELECT username INTO v_username
  FROM public.profiles
  WHERE id=v_uid;

  IF lower(trim(coalesce(p_confirmation,'')))<>lower(trim(coalesce(v_username,''))) THEN
    RAISE EXCEPTION 'CONFIRMATION_MISMATCH';
  END IF;

  DELETE FROM auth.sessions WHERE user_id=v_uid;
  DELETE FROM auth.refresh_tokens WHERE user_id::text=v_uid::text;
  DELETE FROM auth.users WHERE id=v_uid;

  RETURN true;
END;
$$;

REVOKE ALL ON FUNCTION public.delete_my_account(text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.delete_my_account(text) TO authenticated;

COMMIT;
