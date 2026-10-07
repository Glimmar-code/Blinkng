-- Server-authoritative timed conversation notification mutes.
-- Existing indefinite mute behavior stays compatible.

alter table public.conversation_user_state
  add column if not exists muted_until timestamptz;

create index if not exists idx_conversation_user_state_active_mutes
  on public.conversation_user_state(user_id, conversation_id, muted_until)
  where is_muted = true;

create or replace function public.set_conversation_muted(
  p_conversation_id uuid,
  p_muted boolean
)
returns boolean
language plpgsql
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
begin
  if v_uid is null then
    return false;
  end if;

  if not exists (
    select 1
    from public.conversation_participants cp
    where cp.conversation_id = p_conversation_id
      and cp.user_id = v_uid
  ) then
    return false;
  end if;

  insert into public.conversation_user_state(
    conversation_id,
    user_id,
    is_muted,
    muted_until,
    updated_at
  )
  values(
    p_conversation_id,
    v_uid,
    p_muted,
    null,
    now()
  )
  on conflict(conversation_id,user_id)
  do update set
    is_muted = excluded.is_muted,
    muted_until = null,
    updated_at = now();

  return true;
end;
$$;

create or replace function public.set_conversation_muted_until(
  p_conversation_id uuid,
  p_muted_until timestamptz
)
returns boolean
language plpgsql
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
begin
  if v_uid is null or p_muted_until is null or p_muted_until <= now() then
    return false;
  end if;

  if not exists (
    select 1
    from public.conversation_participants cp
    where cp.conversation_id = p_conversation_id
      and cp.user_id = v_uid
  ) then
    return false;
  end if;

  insert into public.conversation_user_state(
    conversation_id,
    user_id,
    is_muted,
    muted_until,
    updated_at
  )
  values(
    p_conversation_id,
    v_uid,
    true,
    p_muted_until,
    now()
  )
  on conflict(conversation_id,user_id)
  do update set
    is_muted = true,
    muted_until = excluded.muted_until,
    updated_at = now();

  return true;
end;
$$;

create or replace function public.get_my_conversation_state(p_conversation_ids uuid[])
returns table(
  conversation_id uuid,
  cleared_at timestamptz,
  is_muted boolean
)
language sql
stable
set search_path = ''
as $$
  select
    cus.conversation_id,
    cus.cleared_at,
    (
      cus.is_muted
      and (cus.muted_until is null or cus.muted_until > now())
    ) as is_muted
  from public.conversation_user_state cus
  where cus.user_id = (select auth.uid())
    and cus.conversation_id = any(p_conversation_ids);
$$;

create or replace function public.get_my_unread_message_notifications(p_limit integer default 200)
returns table(
  message_id uuid,
  conversation_id uuid,
  sender_id uuid,
  sender_username text,
  sender_name text,
  sender_avatar text,
  content text,
  created_at timestamptz
)
language sql
security definer
set search_path = ''
as $$
  select
    m.id as message_id,
    m.conversation_id,
    m.sender_id,
    coalesce(p.username, '') as sender_username,
    coalesce(nullif(btrim(p.full_name), ''), nullif(btrim(p.username), ''), 'Blink user') as sender_name,
    coalesce(p.avatar_url, '') as sender_avatar,
    m.content,
    m.created_at
  from public.messages m
  join public.conversation_participants cp
    on cp.conversation_id = m.conversation_id
   and cp.user_id = auth.uid()
  left join public.profiles p
    on p.id = m.sender_id
  where auth.uid() is not null
    and m.sender_id <> auth.uid()
    and coalesce(m.is_read, false) = false
    and m.read_at is null
    and coalesce(m.deleted_for_everyone, false) = false
    and m.created_at > coalesce(cp.last_read_at, '-infinity'::timestamptz)
    and not exists (
      select 1
      from public.conversation_user_state cus
      where cus.conversation_id = m.conversation_id
        and cus.user_id = auth.uid()
        and cus.is_muted
        and (cus.muted_until is null or cus.muted_until > now())
    )
  order by m.created_at asc
  limit least(greatest(coalesce(p_limit, 200), 1), 200);
$$;

revoke all on function public.set_conversation_muted(uuid, boolean) from public, anon, authenticated;
revoke all on function public.set_conversation_muted_until(uuid, timestamptz) from public, anon, authenticated;
revoke all on function public.get_my_conversation_state(uuid[]) from public, anon, authenticated;
revoke all on function public.get_my_unread_message_notifications(integer) from public, anon, authenticated;

grant execute on function public.set_conversation_muted(uuid, boolean) to authenticated;
grant execute on function public.set_conversation_muted_until(uuid, timestamptz) to authenticated;
grant execute on function public.get_my_conversation_state(uuid[]) to authenticated;
grant execute on function public.get_my_unread_message_notifications(integer) to authenticated;
