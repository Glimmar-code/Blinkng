-- Tested in disposable Postgres; idempotent, additive, RLS-protected.
-- BLINK read/write chat preview settings: isolated, non-destructive production drift repair.
-- Restore the original settings contract in the 20260908133100 migration.
-- First validate in disposable Postgres with staging user accounts.
CREATE TABLE IF NOT EXISTS public.user_chat_privacy (
    user_id uuid PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    who_can_message text NOT NULL DEFAULT 'everyone' CHECK (who_can_message IN ('everyone','followers','mutuals','connections','nobody')),
    who_can_call text NOT NULL DEFAULT 'everyone' CHECK (who_can_call IN ('everyone','followers','mutuals','connections','nobody')),
    who_can_group_invite text NOT NULL DEFAULT 'everyone' CHECK (who_can_group_invite IN ('everyone','followers','mutuals','connections','nobody')),
    show_online boolean NOT NULL DEFAULT true,
    show_last_seen boolean NOT NULL DEFAULT true,
    send_read_receipts boolean NOT NULL DEFAULT true,
    show_typing boolean NOT NULL DEFAULT true,
    show_recording boolean NOT NULL DEFAULT true,
    show_profile_photo_in_chat boolean NOT NULL DEFAULT true,
    allow_link_previews boolean NOT NULL DEFAULT true,
    notification_preview text NOT NULL DEFAULT 'full' CHECK (notification_preview IN ('full','sender_only','hidden')),
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.user_chat_privacy ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='user_chat_privacy' AND policyname='user_chat_privacy_select_self') THEN
      CREATE POLICY user_chat_privacy_select_self ON public.user_chat_privacy
        FOR SELECT TO authenticated USING (user_id = (SELECT auth.uid()));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='user_chat_privacy' AND policyname='user_chat_privacy_insert_self') THEN
      CREATE POLICY user_chat_privacy_insert_self ON public.user_chat_privacy
        FOR INSERT TO authenticated WITH CHECK (user_id = (SELECT auth.uid()));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='user_chat_privacy' AND policyname='user_chat_privacy_update_self') THEN
      CREATE POLICY user_chat_privacy_update_self ON public.user_chat_privacy
        FOR UPDATE TO authenticated USING (user_id = (SELECT auth.uid()))
        WITH CHECK (user_id = (SELECT auth.uid()));
    END IF;
END $$;

REVOKE ALL ON public.user_chat_privacy FROM anon;
GRANT SELECT, INSERT, UPDATE ON public.user_chat_privacy TO authenticated;

CREATE OR REPLACE FUNCTION public.update_chat_privacy(
    p_who_can_message text,
    p_who_can_call text,
    p_who_can_group_invite text,
    p_show_online boolean,
    p_show_last_seen boolean,
    p_send_read_receipts boolean,
    p_show_typing boolean,
    p_show_recording boolean,
    p_show_profile_photo_in_chat boolean,
    p_allow_link_previews boolean,
    p_notification_preview text
) RETURNS boolean LANGUAGE plpgsql SECURITY INVOKER SET search_path = ''
AS $body$
DECLARE v_me uuid := auth.uid();
BEGIN
    IF v_me IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
    INSERT INTO public.user_chat_privacy (
        user_id,who_can_message,who_can_call,who_can_group_invite,
        show_online,show_last_seen,send_read_receipts,show_typing,show_recording,
        show_profile_photo_in_chat,allow_link_previews,notification_preview,updated_at
    ) VALUES (
        v_me,p_who_can_message,p_who_can_call,p_who_can_group_invite,
        p_show_online,p_show_last_seen,p_send_read_receipts,p_show_typing,p_show_recording,
        p_show_profile_photo_in_chat,p_allow_link_previews,p_notification_preview,now()
    ) ON CONFLICT (user_id) DO UPDATE SET
        who_can_message = EXCLUDED.who_can_message,
        who_can_call = EXCLUDED.who_can_call,
        who_can_group_invite = EXCLUDED.who_can_group_invite,
        show_online = EXCLUDED.show_online,
        show_last_seen = EXCLUDED.show_last_seen,
        send_read_receipts = EXCLUDED.send_read_receipts,
        show_typing = EXCLUDED.show_typing,
        show_recording = EXCLUDED.show_recording,
        show_profile_photo_in_chat = EXCLUDED.show_profile_photo_in_chat,
        allow_link_previews = EXCLUDED.allow_link_previews,
        notification_preview = EXCLUDED.notification_preview,
        updated_at = NOW();
    RETURN TRUE;
END;
$body$;
REVOKE ALL ON FUNCTION public.update_chat_privacy(text,text,text,boolean,boolean,boolean,boolean,boolean,boolean,boolean,text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_chat_privacy(text,text,text,boolean,boolean,boolean,boolean,boolean,boolean,boolean,text) TO authenticated;
