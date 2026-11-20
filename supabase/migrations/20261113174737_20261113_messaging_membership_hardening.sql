-- BLINK messaging membership hardening, applied to project jhwgifrlxwspoedxjaly
-- Live migration record: 20261113174737 / 20261113_messaging_membership_hardening
--
-- The central rule is that historical participants are not members. Every read,
-- write, receipt, reaction, pin, report, and conversation-state path must require
-- an active participant row (left_at IS NULL).

create or replace function private.is_active_conversation_member(
  p_conversation_id uuid,
  p_user_id uuid default auth.uid()
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select p_user_id is not null and exists (
    select 1
    from public.conversation_participants cp
    where cp.conversation_id = p_conversation_id
      and cp.user_id = p_user_id
      and cp.left_at is null
  );
$$;

-- Conversations and participant rows.
drop policy if exists conv_select_member on public.conversations;
create policy conv_select_member on public.conversations
  for select to authenticated
  using (private.is_active_conversation_member(id));

drop policy if exists conv_update_member on public.conversations;
create policy conv_update_member on public.conversations
  for update to authenticated
  using (private.is_active_conversation_member(id))
  with check (private.is_active_conversation_member(id));

drop policy if exists cp_select_own on public.conversation_participants;
create policy cp_select_own on public.conversation_participants
  for select to authenticated
  using (auth.uid() = user_id and left_at is null);

drop policy if exists cp_update_own on public.conversation_participants;
create policy cp_update_own on public.conversation_participants
  for update to authenticated
  using (auth.uid() = user_id and left_at is null)
  with check (auth.uid() = user_id);

drop policy if exists cp_insert_own on public.conversation_participants;
create policy cp_insert_own on public.conversation_participants
  for insert to authenticated
  with check (
    auth.uid() = user_id
    and (
      private.is_active_conversation_member(conversation_id)
      or exists (
        select 1 from public.conversations c
        where c.id = conversation_id and c.created_by = auth.uid()
      )
    )
  );

-- Messages, reactions, pins, and per-user message state.
drop policy if exists messages_select_member on public.messages;
create policy messages_select_member on public.messages
  for select to authenticated
  using (private.is_active_conversation_member(conversation_id));

drop policy if exists messages_insert_member on public.messages;
create policy messages_insert_member on public.messages
  for insert to authenticated
  with check (sender_id = auth.uid() and private.is_active_conversation_member(conversation_id));

drop policy if exists messages_update_own on public.messages;
create policy messages_update_own on public.messages
  for update to authenticated
  using (sender_id = auth.uid() and private.is_active_conversation_member(conversation_id))
  with check (sender_id = auth.uid() and private.is_active_conversation_member(conversation_id));

drop policy if exists message_reactions_select_participant on public.message_reactions;
create policy message_reactions_select_participant on public.message_reactions
  for select to authenticated
  using (exists (
    select 1 from public.messages m
    where m.id = message_id and private.is_active_conversation_member(m.conversation_id)
  ));

drop policy if exists message_reactions_insert_self on public.message_reactions;
create policy message_reactions_insert_self on public.message_reactions
  for insert to authenticated
  with check (user_id = auth.uid() and exists (
    select 1 from public.messages m
    where m.id = message_id and private.is_active_conversation_member(m.conversation_id)
  ));

drop policy if exists message_reactions_delete_self on public.message_reactions;
create policy message_reactions_delete_self on public.message_reactions
  for delete to authenticated
  using (user_id = auth.uid() and exists (
    select 1 from public.messages m
    where m.id = message_id and private.is_active_conversation_member(m.conversation_id)
  ));

drop policy if exists message_pins_select_participant on public.message_pins;
create policy message_pins_select_participant on public.message_pins
  for select to authenticated using (private.is_active_conversation_member(conversation_id));
drop policy if exists message_pins_delete_participant on public.message_pins;
create policy message_pins_delete_participant on public.message_pins
  for delete to authenticated using (private.is_active_conversation_member(conversation_id));
drop policy if exists message_pins_insert_participant on public.message_pins;
create policy message_pins_insert_participant on public.message_pins
  for insert to authenticated
  with check (
    pinned_by = auth.uid()
    and private.is_active_conversation_member(conversation_id)
    and exists (
      select 1 from public.messages m
      where m.id = message_id and m.conversation_id = message_pins.conversation_id
    )
  );

-- Conversation and message state/report access is account-scoped and active-member scoped.
drop policy if exists conversation_user_state_insert_self on public.conversation_user_state;
create policy conversation_user_state_insert_self on public.conversation_user_state
  for insert to authenticated with check (user_id = auth.uid() and private.is_active_conversation_member(conversation_id));
drop policy if exists conversation_user_state_update_self on public.conversation_user_state;
create policy conversation_user_state_update_self on public.conversation_user_state
  for update to authenticated using (user_id = auth.uid() and private.is_active_conversation_member(conversation_id)) with check (user_id = auth.uid() and private.is_active_conversation_member(conversation_id));
drop policy if exists conversation_user_state_delete_self on public.conversation_user_state;
create policy conversation_user_state_delete_self on public.conversation_user_state
  for delete to authenticated using (user_id = auth.uid() and private.is_active_conversation_member(conversation_id));
drop policy if exists conversation_user_state_select_self on public.conversation_user_state;
create policy conversation_user_state_select_self on public.conversation_user_state
  for select to authenticated using (user_id = auth.uid());

drop policy if exists conversation_reports_insert_self on public.conversation_reports;
create policy conversation_reports_insert_self on public.conversation_reports
  for insert to authenticated with check (reporter_id = auth.uid() and private.is_active_conversation_member(conversation_id));
drop policy if exists conversation_reports_select_self on public.conversation_reports;
create policy conversation_reports_select_self on public.conversation_reports
  for select to authenticated using (reporter_id = auth.uid());

-- Compatibility readers and send/read/receipt helpers all enforce active membership.
create or replace function public.get_conversation_messages_page(
  p_conversation_id uuid,
  p_limit integer default 40,
  p_before timestamptz default null,
  p_before_id uuid default null
)
returns setof public.messages
language sql
stable
set search_path = public, pg_temp
as $$
  select m.*
  from public.messages m
  where m.conversation_id = p_conversation_id
    and coalesce(m.deleted_for_everyone, false) = false
    and private.is_active_conversation_member(p_conversation_id)
    and (p_before is null or m.created_at < p_before or
      (m.created_at = p_before and (p_before_id is null or m.id < p_before_id)))
  order by m.created_at desc, m.id desc
  limit greatest(1, least(coalesce(p_limit, 40), 100));
$$;

create or replace function public.get_my_conversation_partners()
returns table(conversation_id uuid, partner_id uuid)
language sql stable security definer
set search_path = public, pg_temp
as $$
  select mine.conversation_id, other.user_id
  from public.conversation_participants mine
  join public.conversation_participants other
    on other.conversation_id = mine.conversation_id
   and other.user_id <> mine.user_id
   and other.left_at is null
  join public.conversations c on c.id = mine.conversation_id
  where mine.user_id = auth.uid()
    and mine.left_at is null
    and coalesce(c.is_group, false) = false;
$$;

create or replace function public.get_my_conversation_state(p_conversation_ids uuid[])
returns table(conversation_id uuid, cleared_at timestamptz, is_muted boolean)
language sql stable
set search_path = ''
as $$
  select cus.conversation_id, cus.cleared_at, cus.is_muted
  from public.conversation_user_state cus
  where cus.user_id = auth.uid()
    and cus.conversation_id = any(p_conversation_ids)
    and private.is_active_conversation_member(cus.conversation_id);
$$;

create or replace function public.get_message_action_state(p_message_ids uuid[])
returns table(message_id uuid, reactions jsonb, my_reactions text[], is_starred boolean, is_hidden boolean, is_pinned boolean)
language sql stable
set search_path = ''
as $$
  select m.id,
    coalesce((select jsonb_object_agg(x.emoji, x.cnt) from (
      select mr.emoji, count(*)::int cnt from public.message_reactions mr
      where mr.message_id = m.id group by mr.emoji
    ) x), '{}'::jsonb),
    coalesce((select array_agg(mr.emoji order by mr.created_at) from public.message_reactions mr
      where mr.message_id = m.id and mr.user_id = auth.uid()), '{}'::text[]),
    coalesce(mus.is_starred, false), coalesce(mus.is_hidden, false),
    exists(select 1 from public.message_pins mp where mp.message_id = m.id)
  from public.messages m
  left join public.message_user_state mus on mus.message_id = m.id and mus.user_id = auth.uid()
  where m.id = any(p_message_ids)
    and private.is_active_conversation_member(m.conversation_id);
$$;

create or replace function public.ack_pending_message_deliveries()
returns integer language plpgsql security definer
set search_path = public, pg_temp
as $$
declare v_me uuid := auth.uid(); v_changed integer := 0;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  update public.messages m
  set delivered_at = coalesce(m.delivered_at, now())
  where m.sender_id <> v_me and m.delivered_at is null
    and coalesce(m.deleted_for_everyone, false) = false
    and private.is_active_conversation_member(m.conversation_id, v_me);
  get diagnostics v_changed = row_count;
  return v_changed;
end;
$$;

-- Signed-in clients use these RPCs; anonymous execution is never permitted.
revoke execute on function public.get_conversation_messages_page(uuid, integer, timestamptz, uuid) from anon;
revoke execute on function public.get_my_conversation_partners() from anon;
revoke execute on function public.get_my_conversation_state(uuid[]) from anon;
revoke execute on function public.get_message_action_state(uuid[]) from anon;
revoke execute on function public.ack_pending_message_deliveries() from anon;
