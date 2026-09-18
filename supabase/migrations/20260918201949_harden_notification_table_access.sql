-- Restrict client access to notification rows to reading and marking them read.

revoke all on table public.notifications from anon, authenticated;
grant select on table public.notifications to authenticated;
grant update (is_read) on table public.notifications to authenticated;
grant all on table public.notifications to service_role;

drop policy if exists notifications_select_own on public.notifications;
drop policy if exists notifications_update_own on public.notifications;
drop policy if exists notifications_delete_own on public.notifications;

create policy notifications_select_own
on public.notifications
for select
to authenticated
using (user_id = (select auth.uid()));

create policy notifications_update_own
on public.notifications
for update
to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));
