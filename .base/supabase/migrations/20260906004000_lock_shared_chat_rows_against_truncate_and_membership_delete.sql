BEGIN;

REVOKE TRUNCATE ON TABLE public.conversations FROM authenticated;
REVOKE TRUNCATE ON TABLE public.messages FROM authenticated;
REVOKE DELETE, TRUNCATE ON TABLE public.conversation_participants FROM authenticated;

DROP POLICY IF EXISTS cp_delete_own ON public.conversation_participants;

COMMIT;
