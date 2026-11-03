-- Hot-path RLS performance hardening.
--
-- Semantics are unchanged: the same authenticated user must own bookmark rows and
-- share inserts. Wrapping auth.uid() in SELECT lets PostgreSQL evaluate it once per
-- statement instead of once per candidate row.

alter policy bookmarks_select_own
on public.post_bookmarks
using ((select auth.uid()) = user_id);

alter policy bookmarks_insert_own
on public.post_bookmarks
with check ((select auth.uid()) = user_id);

alter policy bookmarks_delete_own
on public.post_bookmarks
using ((select auth.uid()) = user_id);

alter policy shares_insert_own
on public.post_shares
with check ((select auth.uid()) = user_id);
