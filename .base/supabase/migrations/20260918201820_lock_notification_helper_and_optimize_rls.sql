-- Lock the server-only notification helper and optimize preference RLS evaluation.

revoke all on function public.notification_push_allowed(uuid, text, timestamptz)
from public, anon, authenticated;
grant execute on function public.notification_push_allowed(uuid, text, timestamptz)
to service_role;

drop policy if exists notification_preferences_select_own on public.notification_preferences;
create policy notification_preferences_select_own
on public.notification_preferences
for select
to authenticated
using (user_id = (select auth.uid()));

drop policy if exists notification_preferences_insert_own on public.notification_preferences;
create policy notification_preferences_insert_own
on public.notification_preferences
for insert
to authenticated
with check (user_id = (select auth.uid()));

drop policy if exists notification_preferences_update_own on public.notification_preferences;
create policy notification_preferences_update_own
on public.notification_preferences
for update
to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));
