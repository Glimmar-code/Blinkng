-- TESTLAB DESIGN ONLY. Do not execute directly in production.
-- This file preserves the current live start_call behavior and adds recipient policy + throttling.
-- Convert to a generated migration only after validation on a Supabase development branch.

create or replace function public.call_allowed_by_recipient(
  p_caller_id uuid,
  p_callee_id uuid,
  p_call_type text
)
returns boolean
language plpgsql
security definer
set search_path = ''
stable
as $$
declare
  v_policy text;
  v_calls_enabled boolean := true;
  v_caller_follows boolean := false;
  v_callee_follows boolean := false;
begin
  if p_caller_id is null or p_callee_id is null or p_caller_id = p_callee_id then
    return false;
  end if;

  select coalesce(np.master_enabled, true) and coalesce(np.calls_enabled, true)
    into v_calls_enabled
  from public.notification_preferences np
  where np.user_id = p_callee_id;

  if not coalesce(v_calls_enabled, true) then
    return false;
  end if;

  select case when p_call_type = 'video' then cp.video_policy else cp.audio_policy end
    into v_policy
  from public.call_preferences cp
  where cp.user_id = p_callee_id;

  v_policy := coalesce(v_policy, 'mutuals');

  if v_policy = 'nobody' then return false; end if;
  if v_policy = 'everyone' then return true; end if;

  select exists (
    select 1 from public.follows f
    where f.follower_id = p_caller_id and f.following_id = p_callee_id
  ) into v_caller_follows;

  select exists (
    select 1 from public.follows f
    where f.follower_id = p_callee_id and f.following_id = p_caller_id
  ) into v_callee_follows;

  return case v_policy
    when 'followers' then v_caller_follows
    when 'following' then v_callee_follows
    when 'mutuals' then v_caller_follows and v_callee_follows
    else false
  end;
end;
$$;

revoke all on function public.call_allowed_by_recipient(uuid, uuid, text) from public, anon, authenticated;

create or replace function public.start_call(
  p_conversation_id uuid,
  p_callee_id uuid,
  p_call_type text
)
returns public.calls
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_caller_id uuid := auth.uid();
  v_call public.calls;
  v_recent_all integer := 0;
  v_recent_target integer := 0;
begin
  if v_caller_id is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;
  if p_callee_id is null or p_callee_id = v_caller_id then
    raise exception 'INVALID_CALLEE';
  end if;
  if p_call_type not in ('audio','video') then
    raise exception 'INVALID_CALL_TYPE';
  end if;

  if not exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = p_conversation_id and cp.user_id = v_caller_id
  ) or not exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = p_conversation_id and cp.user_id = p_callee_id
  ) then
    raise exception 'NOT_CONVERSATION_PARTICIPANT';
  end if;

  if exists (
    select 1 from public.blocks b
    where (b.blocker_id = v_caller_id and b.blocked_id = p_callee_id)
       or (b.blocker_id = p_callee_id and b.blocked_id = v_caller_id)
  ) then
    raise exception 'CALL_BLOCKED';
  end if;

  if not public.call_allowed_by_recipient(v_caller_id, p_callee_id, p_call_type) then
    raise exception 'CALL_NOT_ALLOWED';
  end if;

  update public.calls
     set status = 'missed', ended_at = now(), end_reason = 'timeout'
   where status = 'ringing' and timeout_at <= now()
     and (caller_id in (v_caller_id, p_callee_id) or callee_id in (v_caller_id, p_callee_id));

  if exists (
    select 1 from public.calls c
    where c.status in ('ringing','connecting','connected')
      and (c.caller_id in (v_caller_id, p_callee_id) or c.callee_id in (v_caller_id, p_callee_id))
  ) then
    raise exception 'USER_BUSY';
  end if;

  select count(*) into v_recent_all
  from public.calls c
  where c.caller_id = v_caller_id
    and c.created_at > now() - interval '5 minutes';

  if v_recent_all >= 8 then
    raise exception 'CALL_RATE_LIMITED';
  end if;

  select count(*) into v_recent_target
  from public.calls c
  where c.caller_id = v_caller_id
    and c.callee_id = p_callee_id
    and c.created_at > now() - interval '10 minutes'
    and c.status in ('ringing','declined','missed','cancelled','failed');

  if v_recent_target >= 3 then
    raise exception 'CALL_TARGET_COOLDOWN';
  end if;

  insert into public.calls (conversation_id, caller_id, callee_id, call_type)
  values (p_conversation_id, v_caller_id, p_callee_id, p_call_type)
  returning * into v_call;

  return v_call;
end;
$$;

revoke all on function public.start_call(uuid, uuid, text) from public, anon;
grant execute on function public.start_call(uuid, uuid, text) to authenticated;
