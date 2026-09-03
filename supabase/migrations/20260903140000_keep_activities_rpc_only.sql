-- Keep notification creation behind the validated create_activity RPC.
-- The SECURITY DEFINER function validates auth.uid(), recipient existence,
-- block relationships and a per-actor rate limit before inserting.
drop policy if exists activities_insert_actor on public.activities;
revoke insert on public.activities from anon, authenticated;
