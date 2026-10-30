-- Applied to BLINK Supabase on 2026-10-28, migration version 20261028200938.
-- Production SELECT access and elevated feed RPCs are not yet safe for "Only me".
-- Do NOT remove this guard until RLS, media storage, and every definer RPC
-- consistently enforce owner-only visibility, with tests for every API surface.
alter table public.feed_posts
add constraint blink_no_unprotected_private_audience
check (
  audience is null or
  lower(btrim(audience)) not in ('only me', 'private')
);
