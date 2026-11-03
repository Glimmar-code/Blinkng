-- Avoid re-evaluating auth.uid() for every row touched by post like writes.
-- This preserves the existing ownership rules while using Supabase's
-- recommended init-plan form for authenticated-user checks.

alter policy post_likes_insert_own
on public.post_likes
with check ((select auth.uid()) = user_id);

alter policy post_likes_delete_own
on public.post_likes
using ((select auth.uid()) = user_id);
