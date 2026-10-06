-- Complete BLINK professional chat controls.
-- Additive/idempotent: preserves existing messages, receipts and conversation membership.

alter table public.messages
  add column if not exists forwarded_from_message_id uuid references public.messages(id) on delete set null;

create index if not exists messages_forwarded_from_idx
  on public.messages(forwarded_from_message_id)
  where forwarded_from_message_id is not null;

create table if not exists public.conversation_inbox_state (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  is_archived boolean not null default false,
  is_pinned boolean not null default false,
  marked_unread boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key (conversation_id, user_id)
);

alter table public.conversation_inbox_state enable row level security;

drop policy if exists conversation_inbox_state_self on public.conversation_inbox_state;
create policy conversation_inbox_state_self
on public.conversation_inbox_state
for all to authenticated
using (
  user_id = (select auth.uid())
  and exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = conversation_inbox_state.conversation_id
      and cp.user_id = (select auth.uid())
      and cp.left_at is null
  )
)
with check (
  user_id = (select auth.uid())
  and exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = conversation_inbox_state.conversation_id
      and cp.user_id = (select auth.uid())
      and cp.left_at is null
  )
);

revoke all on public.conversation_inbox_state from anon;
grant select, insert, update, delete on public.conversation_inbox_state to authenticated;

create table if not exists public.direct_message_requests (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  recipient_id uuid not null references public.profiles(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'pending'
    check (status in ('pending','accepted','declined')),
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  primary key (conversation_id, recipient_id)
);

create index if not exists direct_message_requests_recipient_status_idx
  on public.direct_message_requests(recipient_id, status, created_at desc);

alter table public.direct_message_requests enable row level security;

drop policy if exists direct_message_requests_recipient_select on public.direct_message_requests;
create policy direct_message_requests_recipient_select
on public.direct_message_requests
for select to authenticated
using (recipient_id = (select auth.uid()) or sender_id = (select auth.uid()));

drop policy if exists direct_message_requests_recipient_update on public.direct_message_requests;
create policy direct_message_requests_recipient_update
on public.direct_message_requests
for update to authenticated
using (recipient_id = (select auth.uid()))
with check (recipient_id = (select auth.uid()));

revoke all on public.direct_message_requests from anon;
grant select, update on public.direct_message_requests to authenticated;

create or replace function private.chat_users_are_connected(p_a uuid, p_b uuid)
returns boolean
language sql
security definer
stable
set search_path = ''
as $$
  select
    exists (
      select 1 from public.connection_requests r
      where r.status = 'accepted'
        and (
          (r.sender_id = p_a and r.receiver_id = p_b)
          or (r.sender_id = p_b and r.receiver_id = p_a)
        )
    )
    or (
      exists (
        select 1 from public.follows f
        where f.follower_id = p_a and f.following_id = p_b
      )
      and exists (
        select 1 from public.follows f
        where f.follower_id = p_b and f.following_id = p_a
      )
    );
$$;

revoke all on function private.chat_users_are_connected(uuid, uuid) from public, anon, authenticated;

create or replace function private.create_direct_message_request()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_creator uuid;
  v_is_group boolean;
begin
  select c.created_by, coalesce(c.is_group, false)
    into v_creator, v_is_group
  from public.conversations c
  where c.id = new.conversation_id;

  if v_is_group or v_creator is null or new.user_id = v_creator then
    return new;
  end if;

  if exists (
    select 1 from public.blocks b
    where (b.blocker_id = v_creator and b.blocked_id = new.user_id)
       or (b.blocker_id = new.user_id and b.blocked_id = v_creator)
  ) then
    return new;
  end if;

  insert into public.direct_message_requests(
    conversation_id, recipient_id, sender_id, status
  )
  values (
    new.conversation_id,
    new.user_id,
    v_creator,
    case
      when private.chat_users_are_connected(v_creator, new.user_id) then 'accepted'
      else 'pending'
    end
  )
  on conflict (conversation_id, recipient_id) do nothing;

  return new;
end;
$$;

drop trigger if exists create_direct_message_request_after_participant
  on public.conversation_participants;
create trigger create_direct_message_request_after_participant
after insert on public.conversation_participants
for each row execute function private.create_direct_message_request();

create or replace function public.get_chat_inbox_state(p_conversation_ids uuid[])
returns table(
  conversation_id uuid,
  is_archived boolean,
  is_pinned boolean,
  marked_unread boolean,
  request_status text
)
language sql
security invoker
set search_path = 'public', 'pg_temp'
as $$
  select
    ids.conversation_id,
    coalesce(s.is_archived, false),
    coalesce(s.is_pinned, false),
    coalesce(s.marked_unread, false),
    coalesce(r.status, 'accepted')
  from unnest(coalesce(p_conversation_ids, array[]::uuid[])) ids(conversation_id)
  left join public.conversation_inbox_state s
    on s.conversation_id = ids.conversation_id
   and s.user_id = auth.uid()
  left join public.direct_message_requests r
    on r.conversation_id = ids.conversation_id
   and r.recipient_id = auth.uid()
  where exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = ids.conversation_id
      and cp.user_id = auth.uid()
      and cp.left_at is null
  );
$$;

revoke all on function public.get_chat_inbox_state(uuid[]) from public, anon;
grant execute on function public.get_chat_inbox_state(uuid[]) to authenticated;

create or replace function public.set_chat_inbox_state(
  p_conversation_id uuid,
  p_archived boolean default null,
  p_pinned boolean default null,
  p_marked_unread boolean default null
)
returns boolean
language plpgsql
security invoker
set search_path = 'public', 'pg_temp'
as $$
declare
  v_me uuid := auth.uid();
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if not exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = p_conversation_id
      and cp.user_id = v_me
      and cp.left_at is null
  ) then
    raise exception 'NOT_A_CONVERSATION_MEMBER';
  end if;

  insert into public.conversation_inbox_state(
    conversation_id, user_id, is_archived, is_pinned, marked_unread, updated_at
  )
  values (
    p_conversation_id,
    v_me,
    coalesce(p_archived, false),
    coalesce(p_pinned, false),
    coalesce(p_marked_unread, false),
    now()
  )
  on conflict (conversation_id, user_id) do update
  set is_archived = coalesce(p_archived, conversation_inbox_state.is_archived),
      is_pinned = coalesce(p_pinned, conversation_inbox_state.is_pinned),
      marked_unread = coalesce(p_marked_unread, conversation_inbox_state.marked_unread),
      updated_at = now();

  return true;
end;
$$;

revoke all on function public.set_chat_inbox_state(uuid, boolean, boolean, boolean) from public, anon;
grant execute on function public.set_chat_inbox_state(uuid, boolean, boolean, boolean) to authenticated;

create or replace function public.respond_direct_message_request(
  p_conversation_id uuid,
  p_accept boolean
)
returns boolean
language plpgsql
security invoker
set search_path = 'public', 'pg_temp'
as $$
declare
  v_me uuid := auth.uid();
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;

  update public.direct_message_requests
  set status = case when p_accept then 'accepted' else 'declined' end,
      responded_at = now()
  where conversation_id = p_conversation_id
    and recipient_id = v_me
    and status = 'pending';

  if not found then return false; end if;

  if not p_accept then
    insert into public.conversation_inbox_state(
      conversation_id, user_id, is_archived, updated_at
    )
    values (p_conversation_id, v_me, true, now())
    on conflict (conversation_id, user_id) do update
      set is_archived = true, updated_at = now();
  end if;

  return true;
end;
$$;

revoke all on function public.respond_direct_message_request(uuid, boolean) from public, anon;
grant execute on function public.respond_direct_message_request(uuid, boolean) to authenticated;

create or replace function public.block_chat_user(
  p_username text,
  p_blocked boolean default true
)
returns boolean
language plpgsql
security invoker
set search_path = 'public', 'pg_temp'
as $$
declare
  v_me uuid := auth.uid();
  v_target uuid;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select p.id into v_target
  from public.profiles p
  where lower(p.username) = lower(btrim(p_username))
  limit 1;

  if v_target is null or v_target = v_me then return false; end if;

  if p_blocked then
    insert into public.blocks(blocker_id, blocked_id)
    select v_me, v_target
    where not exists (
      select 1 from public.blocks b
      where b.blocker_id = v_me and b.blocked_id = v_target
    );
  else
    delete from public.blocks
    where blocker_id = v_me and blocked_id = v_target;
  end if;

  return true;
end;
$$;

revoke all on function public.block_chat_user(text, boolean) from public, anon;
grant execute on function public.block_chat_user(text, boolean) to authenticated;

create or replace function public.forward_chat_message(
  p_message_id uuid,
  p_target_username text
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
  v_target uuid;
  v_source public.messages%rowtype;
  v_conversation uuid;
  v_new_id uuid;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;

  select m.* into v_source
  from public.messages m
  where m.id = p_message_id;

  if not found or coalesce(v_source.deleted_for_everyone, false) then
    raise exception 'MESSAGE_NOT_AVAILABLE';
  end if;

  if not exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = v_source.conversation_id
      and cp.user_id = v_me
      and cp.left_at is null
  ) then
    raise exception 'MESSAGE_NOT_VISIBLE';
  end if;

  select p.id into v_target
  from public.profiles p
  where lower(p.username) = lower(btrim(p_target_username))
  limit 1;

  if v_target is null or v_target = v_me then
    raise exception 'INVALID_FORWARD_TARGET';
  end if;

  if exists (
    select 1 from public.blocks b
    where (b.blocker_id = v_me and b.blocked_id = v_target)
       or (b.blocker_id = v_target and b.blocked_id = v_me)
  ) then
    raise exception 'MESSAGING_BLOCKED';
  end if;

  select c.id into v_conversation
  from public.conversations c
  where coalesce(c.is_group, false) = false
    and exists (
      select 1 from public.conversation_participants a
      where a.conversation_id = c.id and a.user_id = v_me and a.left_at is null
    )
    and exists (
      select 1 from public.conversation_participants b
      where b.conversation_id = c.id and b.user_id = v_target and b.left_at is null
    )
  order by c.created_at desc
  limit 1;

  if v_conversation is null then
    insert into public.conversations(created_by, is_group)
    values (v_me, false)
    returning id into v_conversation;

    insert into public.conversation_participants(conversation_id, user_id)
    values (v_conversation, v_me), (v_conversation, v_target);
  end if;

  insert into public.messages(
    conversation_id,
    sender_id,
    content,
    media_url,
    message_type,
    is_read,
    created_at,
    forwarded_from_message_id
  )
  values (
    v_conversation,
    v_me,
    v_source.content,
    v_source.media_url,
    v_source.message_type,
    false,
    now(),
    v_source.id
  )
  returning id into v_new_id;

  return v_new_id;
end;
$$;

revoke all on function public.forward_chat_message(uuid, text) from public, anon;
grant execute on function public.forward_chat_message(uuid, text) to authenticated;


alter table public.conversation_notification_preferences
  add column if not exists mute_until timestamptz;

create or replace function public.get_conversation_notification_settings(
  p_conversation_ids uuid[]
)
returns table(
  conversation_id uuid,
  notification_mode text,
  mute_until timestamptz,
  preview_mode text,
  vibration_enabled boolean
)
language sql
security invoker
set search_path = 'public', 'pg_temp'
as $$
  select
    ids.conversation_id,
    case
      when coalesce(p.messages_enabled, true) = false then 'none'
      when coalesce(p.mentions_only, false) = true then 'mentions'
      else 'all'
    end as notification_mode,
    p.mute_until,
    coalesce(p.preview_mode, 'inherit') as preview_mode,
    coalesce(p.vibration_enabled, true) as vibration_enabled
  from unnest(coalesce(p_conversation_ids, array[]::uuid[])) ids(conversation_id)
  left join public.conversation_notification_preferences p
    on p.conversation_id = ids.conversation_id
   and p.user_id = auth.uid()
  where exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = ids.conversation_id
      and cp.user_id = auth.uid()
      and cp.left_at is null
  );
$$;

revoke all on function public.get_conversation_notification_settings(uuid[]) from public, anon;
grant execute on function public.get_conversation_notification_settings(uuid[]) to authenticated;

create or replace function public.set_conversation_notification_settings(
  p_conversation_id uuid,
  p_notification_mode text,
  p_mute_until timestamptz default null,
  p_preview_mode text default 'inherit',
  p_vibration_enabled boolean default true
)
returns boolean
language plpgsql
security invoker
set search_path = 'public', 'pg_temp'
as $$
declare
  v_me uuid := auth.uid();
  v_mode text := lower(coalesce(p_notification_mode, 'all'));
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if v_mode not in ('all','mentions','none') then raise exception 'INVALID_NOTIFICATION_MODE'; end if;
  if p_preview_mode not in ('inherit','full','sender_only','hidden') then
    raise exception 'INVALID_PREVIEW_MODE';
  end if;
  if not exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = p_conversation_id
      and cp.user_id = v_me
      and cp.left_at is null
  ) then
    raise exception 'NOT_A_CONVERSATION_MEMBER';
  end if;

  insert into public.conversation_notification_preferences(
    conversation_id,
    user_id,
    messages_enabled,
    calls_enabled,
    mentions_only,
    preview_mode,
    vibration_enabled,
    mute_until,
    updated_at
  )
  values (
    p_conversation_id,
    v_me,
    v_mode <> 'none',
    true,
    v_mode = 'mentions',
    p_preview_mode,
    p_vibration_enabled,
    case when v_mode = 'none' then p_mute_until else null end,
    now()
  )
  on conflict (conversation_id, user_id) do update
  set messages_enabled = excluded.messages_enabled,
      mentions_only = excluded.mentions_only,
      preview_mode = excluded.preview_mode,
      vibration_enabled = excluded.vibration_enabled,
      mute_until = excluded.mute_until,
      updated_at = now();

  return true;
end;
$$;

revoke all on function public.set_conversation_notification_settings(uuid, text, timestamptz, text, boolean)
  from public, anon;
grant execute on function public.set_conversation_notification_settings(uuid, text, timestamptz, text, boolean)
  to authenticated;
