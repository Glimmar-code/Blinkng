# BLINK: Supabase Data API privileges from October 30, 2026

Supabase will stop automatically granting Data API access to newly created public tables. Existing tables keep their grants.

For each new public table migration:
1. Enable row level security (RLS).
2. Add table-specific GRANT or REVOKE statements for each role: anon, authenticated, service_role.
3. Grant only permissions required by actual callers.
4. If browser or app clients access a table, add ownership-scoped RLS policies and test their read/write paths.
5. Never give anon users access to payment, message or identity data just to silence a CI check.
6. Keep service_role and secret keys on the server; do not put them in Android, Windows or web code.

Backend-only example:

~~~sql
create table public.example_payment_audit (
  id bigint generated always as identity primary key,
  event_hash text not null
);
alter table public.example_payment_audit enable row level security;
revoke all on table public.example_payment_audit from anon, authenticated;
grant select, insert, update on table public.example_payment_audit to service_role;
~~~

Authenticated owner-only example:

~~~sql
create table public.example_private_notes (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id),
  note text not null
);
alter table public.example_private_notes enable row level security;
revoke all on table public.example_private_notes from anon;
grant select, insert, update, delete on table public.example_private_notes to authenticated;
grant select, insert, update, delete on table public.example_private_notes to service_role;
create policy "read own notes" on public.example_private_notes
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "insert own notes" on public.example_private_notes
  for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "update own notes" on public.example_private_notes
  for update to authenticated using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
create policy "delete own notes" on public.example_private_notes
  for delete to authenticated using ((select auth.uid()) = user_id);
~~~

CI:
- scripts/check_supabase_data_api_grants.py rejects new public tables without RLS and explicit role decisions.
- scripts/test_supabase_data_api_grants.py tests secure and insecure SQL examples.
- The Supabase safety workflow runs these checks automatically on main/Testlab pull requests.
- CI checks migration files timestamped 20261020 or later. Older migrations were written against the earlier default and need a separate clean-rebuild review before a fresh project, preview database or supabase db reset. The existing CI workflow already skips full rebuild when no complete baseline exists.
- Static checks do not replace end-to-end Supabase API and RLS tests.

Reference: https://supabase.com/docs/guides/api/securing-your-api
