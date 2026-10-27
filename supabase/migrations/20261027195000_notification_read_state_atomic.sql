-- BLINK notification read-state repair.
-- Old clients PATCH only is_read; never grant UPDATE on all notification columns.
-- Authenticated users may update their own read state under strict RLS.
alter table public.notifications enable row level security;

revoke update on table public.notifications from anon, authenticated;
grant update (is_read) on table public.notifications to authenticated;

drop policy if exists notifications_update_own on public.notifications;
create policy notifications_update_own
on public.notifications for update to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

-- One transaction for both legacy activity mirrors and canonical notification rows.
-- SECURITY INVOKER deliberately honors column GRANTs and existing RLS policies.
create or replace function public.mark_all_my_notifications_read_atomic()
returns boolean
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
  update public.activities
     set is_read = true
   where recipient_id = v_uid and is_read = false;
  update public.notifications
     set is_read = true
   where user_id = v_uid and is_read = false;
  return true;
end;
$$;

revoke all on function public.mark_all_my_notifications_read_atomic() from public, anon;
grant execute on function public.mark_all_my_notifications_read_atomic() to authenticated;
