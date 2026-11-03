-- Blinkng professional messaging foundation: privacy, immutable identities and idempotent sends.
-- Additive/backward-compatible migration. Existing send_message_v2 remains available during rollout.

alter table public.messages
  add column if not exists client_message_id uuid,
  add column if not exists sequence_no bigint,
  add column if not exists expires_at timestamptz,
  add column if not exists forwarded_from_message_id uuid,
  add column if not exists formatting jsonb not null default '{}'::jsonb,
  add column if not exists link_preview jsonb not null default '{}'::jsonb;

create unique index if not exists messages_sender_client_message_uidx
  on public.messages(sender_id, client_message_id)
  where client_message_id is not null;

create index if not exists messages_conversation_sequence_idx
  on public.messages(conversation_id, sequence_no desc)
  where sequence_no is not null;

create index if not exists messages_expires_at_idx
  on public.messages(expires_at)
  where expires_at is not null and deleted_for_everyone = false;

create sequence if not exists public.messages_server_sequence_seq;

create or replace function private.assign_message_server_sequence()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.sequence_no is null then
    new.sequence_no := nextval('public.messages_server_sequence_seq'::regclass);
  end if;
  return new;
end;
$$;

revoke all on function private.assign_message_server_sequence() from public, anon, authenticated;

drop trigger if exists trg_assign_message_server_sequence on public.messages;
create trigger trg_assign_message_server_sequence
before insert on public.messages
for each row execute function private.assign_message_server_sequence();

create or replace function private.guard_message_identity_update()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
begin
  if new.id is distinct from old.id
     or new.conversation_id is distinct from old.conversation_id
     or new.sender_id is distinct from old.sender_id
     or new.created_at is distinct from old.created_at
     or new.sequence_no is distinct from old.sequence_no then
    raise exception 'MESSAGE_IDENTITY_IMMUTABLE';
  end if;

  if old.client_message_id is not null
     and new.client_message_id is distinct from old.client_message_id then
    raise exception 'CLIENT_MESSAGE_ID_IMMUTABLE';
  end if;

  -- A sender must not be able to manufacture delivery/read receipts for their own message.
  -- Server/service operations (auth.uid() is null) and the receiving participant remain allowed.
  if v_me is not null and v_me = old.sender_id and (
       new.delivered_at is distinct from old.delivered_at
       or new.read_at is distinct from old.read_at
       or new.is_read is distinct from old.is_read
     ) then
    raise exception 'MESSAGE_RECEIPTS_ARE_RECEIVER_CONTROLLED';
  end if;

  return new;
end;
$$;

revoke all on function private.guard_message_identity_update() from public, anon, authenticated;

drop trigger if exists trg_guard_message_identity_update on public.messages;
create trigger trg_guard_message_identity_update
before update on public.messages
for each row execute function private.guard_message_identity_update();

-- The compatibility table previously had a broad authenticated SELECT policy.
-- Restrict legacy rows to only the sender or receiver while old clients are phased out.
drop policy if exists "messages_compat_select_own" on public.messages_compat;
drop policy if exists "messages_compat_insert_own" on public.messages_compat;
drop policy if exists "messages_compat_delete_own" on public.messages_compat;

drop policy if exists "messages_compat_select_participant" on public.messages_compat;
create policy "messages_compat_select_participant"
on public.messages_compat for select
to authenticated
using (
  exists (
    select 1
    from public.profiles p
    where p.id = (select auth.uid())
      and p.username in (messages_compat.sender_username, messages_compat.receiver_username)
  )
);

drop policy if exists "messages_compat_insert_sender" on public.messages_compat;
create policy "messages_compat_insert_sender"
on public.messages_compat for insert
to authenticated
with check (
  exists (
    select 1
    from public.profiles sender
    where sender.id = (select auth.uid())
      and sender.username = messages_compat.sender_username
  )
  and exists (
    select 1
    from public.profiles receiver
    where receiver.username = messages_compat.receiver_username
  )
);

drop policy if exists "messages_compat_update_sender" on public.messages_compat;
create policy "messages_compat_update_sender"
on public.messages_compat for update
to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid())
      and p.username = messages_compat.sender_username
  )
)
with check (
  exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid())
      and p.username = messages_compat.sender_username
  )
);

drop policy if exists "messages_compat_delete_sender" on public.messages_compat;
create policy "messages_compat_delete_sender"
on public.messages_compat for delete
to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid())
      and p.username = messages_compat.sender_username
  )
);

revoke all on public.messages_compat from anon;
grant select, insert, update, delete on public.messages_compat to authenticated;

create or replace function public.send_message_v3(
  p_receiver_username text,
  p_content text,
  p_client_message_id uuid,
  p_reply_to_message_id uuid default null
)
returns table(
  message_id uuid,
  conversation_id uuid,
  created_at timestamptz,
  sequence_no bigint
)
language plpgsql
security invoker
set search_path = 'public', 'pg_temp'
as $$
declare
  v_me uuid := auth.uid();
  v_message_id uuid;
  v_conversation_id uuid;
  v_created_at timestamptz;
begin
  if v_me is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;
  if p_client_message_id is null then
    raise exception 'CLIENT_MESSAGE_ID_REQUIRED';
  end if;

  -- Serialize retries for the same client key so a reconnect cannot create duplicate rows.
  perform pg_advisory_xact_lock(
    hashtextextended(v_me::text || ':' || p_client_message_id::text, 0)
  );

  select m.id, m.conversation_id, m.created_at
    into v_message_id, v_conversation_id, v_created_at
  from public.messages m
  where m.sender_id = v_me
    and m.client_message_id = p_client_message_id
  limit 1;

  if v_message_id is not null then
    return query
      select m.id, m.conversation_id, m.created_at, m.sequence_no
      from public.messages m
      where m.id = v_message_id;
    return;
  end if;

  select r.message_id, r.conversation_id, r.created_at
    into v_message_id, v_conversation_id, v_created_at
  from public.send_message_v2(p_receiver_username, p_content) r;

  if v_message_id is null or v_conversation_id is null then
    raise exception 'MESSAGE_SEND_DID_NOT_RETURN_IDENTITY';
  end if;

  if p_reply_to_message_id is not null and not exists (
    select 1
    from public.messages parent
    where parent.id = p_reply_to_message_id
      and parent.conversation_id = v_conversation_id
      and coalesce(parent.deleted_for_everyone, false) = false
  ) then
    raise exception 'INVALID_REPLY_TARGET';
  end if;

  update public.messages m
     set client_message_id = p_client_message_id,
         reply_to_message_id = p_reply_to_message_id
   where m.id = v_message_id
     and m.sender_id = v_me;

  return query
    select m.id, m.conversation_id, m.created_at, m.sequence_no
    from public.messages m
    where m.id = v_message_id;
end;
$$;

revoke all on function public.send_message_v3(text, text, uuid, uuid) from public, anon;
grant execute on function public.send_message_v3(text, text, uuid, uuid) to authenticated;

comment on function public.send_message_v3(text, text, uuid, uuid) is
  'Idempotent direct-message send contract. A stable client UUID guarantees reconnect/retry deduplication.';
