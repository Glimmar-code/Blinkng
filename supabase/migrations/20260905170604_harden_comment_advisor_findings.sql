BEGIN;

-- The mention table is trigger-managed only. This explicit deny policy documents
-- that no client role can read or mutate the internal relationship rows.
DROP POLICY IF EXISTS comment_mentions_no_client_access ON public.comment_mentions;
CREATE POLICY comment_mentions_no_client_access
ON public.comment_mentions
FOR ALL
TO anon, authenticated
USING (false)
WITH CHECK (false);

-- Comment creation can rely on RLS while the validation trigger retains elevated
-- access for cross-table integrity checks.
ALTER FUNCTION public.create_comment(uuid, text, uuid) SECURITY INVOKER;

-- Avoid re-evaluating auth.uid() for every candidate like row.
DROP POLICY IF EXISTS comment_likes_insert_own ON public.comment_likes;
DROP POLICY IF EXISTS comment_likes_delete_own ON public.comment_likes;

CREATE POLICY comment_likes_insert_own
ON public.comment_likes
FOR INSERT
TO authenticated
WITH CHECK (user_id = (SELECT auth.uid()));

CREATE POLICY comment_likes_delete_own
ON public.comment_likes
FOR DELETE
TO authenticated
USING (user_id = (SELECT auth.uid()));

COMMIT;
