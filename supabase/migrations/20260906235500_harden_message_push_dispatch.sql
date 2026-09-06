-- Keep push-dispatch internals unavailable through PostgREST.
revoke all on function public.dispatch_message_push_after_insert() from public, anon, authenticated;

create policy "deny_client_message_push_dispatch_access"
on public.message_push_dispatches
for all
to anon, authenticated
using (false)
with check (false);
