-- Reassert least-privilege direct table access for the three tables reported
-- with RLS disabled by the source audit. Their original migrations already
-- enabled RLS; this additive migration also repairs environments where that
-- migration state drifted.
--
-- No anon/authenticated table policies are created intentionally: repository
-- call sites use authenticated SECURITY DEFINER contest RPCs (which validate
-- auth.uid(), and admin RPCs validate private.require_blink_admin()), while
-- Store settings are accessed by a private SECURITY DEFINER helper and the
-- service_role. No application code requires direct client table access.
-- With RLS enabled and no client policies/grants, direct client access is
-- denied; access remains through those checked RPCs and service_role only.

begin;

alter table public.blink_contest_topic_follows enable row level security;
revoke all on table public.blink_contest_topic_follows from public, anon, authenticated;
grant select, insert, delete on table public.blink_contest_topic_follows to service_role;

alter table public.blink_contest_history_participants enable row level security;
revoke all on table public.blink_contest_history_participants from public, anon, authenticated;
grant select, insert on table public.blink_contest_history_participants to service_role;

alter table public.blink_store_settings enable row level security;
revoke all on table public.blink_store_settings from public, anon, authenticated;
grant select, update on table public.blink_store_settings to service_role;

commit;
