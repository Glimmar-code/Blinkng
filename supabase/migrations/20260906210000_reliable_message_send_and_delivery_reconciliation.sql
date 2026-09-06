-- Reliable message send identity + missed-delivery reconciliation.
-- Keeps the legacy send_message RPC intact and adds a richer client contract.

create or replace function public.send_message_v2(
  p_receiver_username text,
  p_content text
)
returns table(
  message_id uuid,
  conversation_id uuid,
  created_at timestamptz
)
language plpgsql
security invoker
set search_path = 'public', 'pg_temp'
as $$
declare
  v_message_id uuid;
begin
  v_message_id := public.send_message(p_receiver_username, p_content);

  return query
  select m.id, m.conversation_id, m.created_at
  from public.messages m
  where m.id = v_message_id
    and m.sender_id = (select auth.uid());
end;
$$;

revoke all on function public.send_message_v2(text, text) from public;
grant execute on function public.send_message_v2(text, text) to authenticated;

create or replace function public.ack_pending_message_deliveries()
returns integer
language plpgsql
security definer
set search_path = 'public', 'pg_temp'
as $$
declare
  v_me uuid := auth.uid();
  v_changed integer := 0;
begin
  if v_me is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  update public.messages m
     set delivered_at = coalesce(m.delivered_at, now())
   where m.sender_id <> v_me
     and m.delivered_at is null
     and coalesce(m.deleted_for_everyone, false) = false
     and exists (
       select 1
       from public.conversation_participants cp
       where cp.conversation_id = m.conversation_id
         and cp.user_id = v_me
     );

  get diagnostics v_changed = row_count;
  return v_changed;
end;
$$;

revoke all on function public.ack_pending_message_deliveries() from public;
grant execute on function public.ack_pending_message_deliveries() to authenticated;
