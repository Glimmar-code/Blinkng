BEGIN;

DROP POLICY IF EXISTS conversations_delete_participant ON public.conversations;
DROP POLICY IF EXISTS conversations_insert_participant ON public.conversations;
DROP POLICY IF EXISTS conversations_select_participant ON public.conversations;
DROP POLICY IF EXISTS conversations_update_participant ON public.conversations;

REVOKE DELETE ON TABLE public.conversations FROM authenticated;
REVOKE DELETE ON TABLE public.messages FROM authenticated;

DROP POLICY IF EXISTS conv_select_member ON public.conversations;
CREATE POLICY conv_select_member ON public.conversations
FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1
    FROM public.conversation_participants cp
    WHERE cp.conversation_id = conversations.id
      AND cp.user_id = (SELECT auth.uid())
  )
);

DROP POLICY IF EXISTS conv_insert_own ON public.conversations;
CREATE POLICY conv_insert_own ON public.conversations
FOR INSERT TO authenticated
WITH CHECK (created_by = (SELECT auth.uid()));

DROP POLICY IF EXISTS conv_update_member ON public.conversations;
CREATE POLICY conv_update_member ON public.conversations
FOR UPDATE TO authenticated
USING (
  EXISTS (
    SELECT 1
    FROM public.conversation_participants cp
    WHERE cp.conversation_id = conversations.id
      AND cp.user_id = (SELECT auth.uid())
  )
)
WITH CHECK (
  EXISTS (
    SELECT 1
    FROM public.conversation_participants cp
    WHERE cp.conversation_id = conversations.id
      AND cp.user_id = (SELECT auth.uid())
  )
);

REVOKE ALL ON FUNCTION public.clear_conversation_for_me(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.clear_conversation_for_me(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.hide_message_for_me(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.hide_message_for_me(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.get_my_conversation_state(uuid[]) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_conversation_state(uuid[]) TO authenticated;
REVOKE ALL ON FUNCTION public.get_conversation_messages_page(uuid, integer, timestamptz, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_conversation_messages_page(uuid, integer, timestamptz, uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.get_conversation_summaries_page(integer, timestamptz, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_conversation_summaries_page(integer, timestamptz, uuid) TO authenticated;

COMMIT;
