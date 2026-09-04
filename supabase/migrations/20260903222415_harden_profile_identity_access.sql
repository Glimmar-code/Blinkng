begin;

-- The old policies were created FOR ALL, so the broad read rule also authorized writes.
-- Replace them with command-specific ownership rules before relying on unique identities.
drop policy if exists profiles_delete_own on public.profiles;
drop policy if exists profiles_insert_own on public.profiles;
drop policy if exists profiles_select_all on public.profiles;
drop policy if exists profiles_update_own on public.profiles;

create policy profiles_select_authenticated
on public.profiles for select to authenticated
using (true);

create policy profiles_insert_own
on public.profiles for insert to authenticated
with check (id = (select auth.uid()));

create policy profiles_update_own
on public.profiles for update to authenticated
using (id = (select auth.uid()))
with check (id = (select auth.uid()));

create policy profiles_delete_own
on public.profiles for delete to authenticated
using (id = (select auth.uid()));

revoke all on table public.profiles from anon;
revoke all on table public.profiles from authenticated;
grant select, insert, update, delete on table public.profiles to authenticated;

notify pgrst, 'reload schema';
commit;
