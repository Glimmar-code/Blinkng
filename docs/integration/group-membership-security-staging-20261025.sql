-- BLINK GROUP SECURITY HARDENING — STAGED SQL CANDIDATE ONLY.
-- Do NOT apply directly to hosted Supabase. Run isolated full PostgreSQL/RLS
-- tests and inspect current policies and consumers before versioned migration.
--
-- 2026-10-25 audit: existing authenticated cp_insert_own only checks user_id,
-- cp_update_own allows edits to is_admin, and conversations/messages membership
-- policies omit left_at. That can compromise group isolation after departures.
-- This candidate fixes the boundaries while preserving legacy 1-to-1 reads.
begin;

-- A tightly scoped predicate is needed for the membership INSERT policy:
-- the joining user does not yet have RLS visibility into the private group
-- row, so an ordinary invoker subquery can return a misleading NOT EXISTS.
create or replace function public.blink_conversation_is_group(p_conversation_id uuid)
returns boolean
language sql stable security definer set search_path = ''
as $$
  select coalesce(
    (select c.is_group from public.conversations c
     where c.id = p_conversation_id), false
  );
$$;
revoke all on function public.blink_conversation_is_group(uuid) from public, anon;
grant execute on function public.blink_conversation_is_group(uuid) to authenticated;

-- Critical IDOR guard: never let a caller join an existing stranger's DM.
-- Legacy cp_insert_own permitted arbitrary self-join by UUID. Allow a narrow
-- compatibility path only for the creator's *own* direct conversation with
-- fewer than two participants. The verified get_or_create_direct_conversation
-- server RPC remains the primary route; SECURITY DEFINER writes bypass RLS.
create or replace function public.blink_can_self_join_creator_direct(
  p_conversation_id uuid
) returns boolean language sql stable security definer set search_path=''
as $$
  select (select auth.uid()) is not null and exists (
    select 1 from public.conversations c
    where c.id=p_conversation_id
      and c.created_by=(select auth.uid())
      and coalesce(c.is_group,false)=false
      and (select count(*) from public.conversation_participants cp
           where cp.conversation_id=c.id) < 2
  );
$$;
revoke all on function public.blink_can_self_join_creator_direct(uuid)
  from public,anon;
grant execute on function public.blink_can_self_join_creator_direct(uuid)
  to authenticated;

drop policy if exists cp_insert_own on public.conversation_participants;
create policy cp_insert_own on public.conversation_participants
for insert to authenticated
with check (
  (select auth.uid()) = user_id
  and public.blink_can_self_join_creator_direct(conversation_id)
);

create or replace function public.blink_protect_conversation_membership()
returns trigger language plpgsql security invoker
set search_path = public, pg_temp
as $$
begin
  if current_user = 'authenticated' and (
    old.user_id is distinct from new.user_id or
    old.conversation_id is distinct from new.conversation_id or
    old.is_admin is distinct from new.is_admin or
    old.joined_at is distinct from new.joined_at or
    (old.left_at is not null and new.left_at is null)
  ) then
    raise exception 'BLINK_MEMBERSHIP_CHANGE_REQUIRES_SERVER'
      using errcode='42501';
  end if;
  if current_user='authenticated'
    and old.left_at is null and new.left_at is not null
    and exists(
      select 1 from public.conversations c
      where c.id=old.conversation_id and c.is_group=true
        and c.created_by=old.user_id
    )
  then
    raise exception 'GROUP_OWNER_MUST_TRANSFER_FIRST' using errcode='42501';
  end if;
  return new;
end;
$$;
drop trigger if exists blink_protect_conversation_membership_guard
  on public.conversation_participants;
create trigger blink_protect_conversation_membership_guard
before update on public.conversation_participants
for each row execute function public.blink_protect_conversation_membership();

-- A departed participant must not read the conversation or old/new messages
-- simply because the relationship row remains for audit/history.
drop policy if exists conv_select_member on public.conversations;
create policy conv_select_member on public.conversations
for select to authenticated
using (exists (
  select 1 from public.conversation_participants cp
  where cp.conversation_id = conversations.id
    and cp.user_id = (select auth.uid()) and cp.left_at is null
));

drop policy if exists conv_update_member on public.conversations;
create policy conv_update_member on public.conversations
for update to authenticated
using (exists (
  select 1 from public.conversation_participants cp
  where cp.conversation_id = conversations.id
    and cp.user_id = (select auth.uid()) and cp.left_at is null
))
with check (exists (
  select 1 from public.conversation_participants cp
  where cp.conversation_id = conversations.id
    and cp.user_id = (select auth.uid()) and cp.left_at is null
));

drop policy if exists messages_select_member on public.messages;
create policy messages_select_member on public.messages
for select to authenticated
using (exists (
  select 1 from public.conversation_participants cp
  where cp.conversation_id = messages.conversation_id
    and cp.user_id = (select auth.uid()) and cp.left_at is null
));

drop policy if exists messages_insert_member on public.messages;
create policy messages_insert_member on public.messages
for insert to authenticated
with check (
  sender_id = (select auth.uid()) and exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = messages.conversation_id
      and cp.user_id = (select auth.uid()) and cp.left_at is null
  )
);

-- Ordinary group participants must never take over metadata (title,
-- avatar, description) or turn a private group into a direct conversation.
-- Server-owned row counters/timestamps remain compatible with legacy triggers.
create or replace function public.blink_guard_group_metadata()
returns trigger language plpgsql security invoker set search_path=public,pg_temp
as $$
begin
  if current_user='authenticated' and (
    old.is_group or new.is_group
  ) then
    if old.is_group is distinct from new.is_group or
       old.created_by is distinct from new.created_by then
      raise exception 'BLINK_GROUP_IDENTITY_REQUIRES_SERVER' using errcode='42501';
    end if;
    if old.title is distinct from new.title or
       old.description is distinct from new.description or
       old.avatar_url is distinct from new.avatar_url then
      if not exists (
        select 1 from public.conversation_participants cp
         where cp.conversation_id=old.id and cp.user_id=auth.uid()
           and cp.is_admin=true and cp.left_at is null
      ) then
        raise exception 'BLINK_GROUP_ADMIN_REQUIRED' using errcode='42501';
      end if;
    end if;
  end if;
  return new;
end $$;
drop trigger if exists blink_guard_group_metadata_trigger on public.conversations;
create trigger blink_guard_group_metadata_trigger
before update on public.conversations
for each row execute function public.blink_guard_group_metadata();

-- Strictly authenticate actual, ACTIVE members. The deployed version of
-- send_group_message currently fails to filter left_at and therefore allowed
-- historic/departed participants to satisfy its permission lookup.
create or replace function public.send_group_message(
  p_conversation_id uuid, p_content text
) returns uuid language plpgsql security invoker
set search_path = public, pg_temp
as $$
declare v_id uuid; v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if nullif(trim(coalesce(p_content,'')),'') is null
    then raise exception 'MESSAGE_REQUIRED'; end if;
  if length(trim(p_content))>5000 then raise exception 'MESSAGE_TOO_LONG'; end if;
  if not exists(
    select 1 from public.conversations c
    join public.conversation_participants cp on cp.conversation_id=c.id
    where c.id=p_conversation_id and c.is_group=true
      and cp.user_id=v_uid and cp.left_at is null
  ) then raise exception 'NOT_AN_ACTIVE_GROUP_MEMBER'; end if;
  if (select count(*) from public.messages m
      where m.sender_id=v_uid and m.created_at>now()-interval '1 minute') >=60
  then raise exception 'MESSAGE_RATE_LIMITED'; end if;
  insert into public.messages(conversation_id,sender_id,content,message_type)
  values (p_conversation_id,v_uid,trim(p_content),'text')
  returning id into v_id;
  return v_id;
end $$;
revoke all on function public.send_group_message(uuid,text) from public,anon;
grant execute on function public.send_group_message(uuid,text) to authenticated;

-- Group creators/admins can invite existing accounts without granting users a
-- raw direct INSERT permission to the protected membership table.
create or replace function public.blink_group_invite(
  p_conversation_id uuid, p_user_id uuid
) returns boolean language plpgsql security definer set search_path = ''
as $$
declare v_actor uuid := auth.uid();
begin
  if v_actor is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_user_id is null or p_user_id=v_actor then raise exception 'INVALID_INVITEE'; end if;
  if not exists(
    select 1 from public.conversations c
    join public.conversation_participants cp on cp.conversation_id=c.id
    where c.id=p_conversation_id and c.is_group=true
      and cp.user_id=v_actor and cp.is_admin=true and cp.left_at is null
  ) then raise exception 'GROUP_ADMIN_REQUIRED'; end if;
  if (select count(*) from public.conversation_participants cp
      where cp.conversation_id=p_conversation_id and cp.left_at is null)>=50
  then raise exception 'GROUP_MEMBER_LIMIT'; end if;
  if not exists(select 1 from public.profiles p where p.id=p_user_id)
  then raise exception 'USER_NOT_FOUND'; end if;
  -- Do not allow administrators to force blocked students into shared
  -- conversations. Check the invitee against EVERY currently active member,
  -- not merely the administrator.
  if exists (
    select 1 from public.blocks b
    join public.conversation_participants member
      on member.conversation_id=p_conversation_id
       and member.left_at is null
    where (b.blocker_id=member.user_id and b.blocked_id=p_user_id)
       or (b.blocker_id=p_user_id and b.blocked_id=member.user_id)
  ) then raise exception 'GROUP_INVITATION_BLOCKED'; end if;
  update public.conversation_participants
    set left_at=null,joined_at=now(),is_admin=false
    where conversation_id=p_conversation_id and user_id=p_user_id
      and left_at is not null;
  if found then return true; end if;
  if exists (select 1 from public.conversation_participants cp
    where cp.conversation_id=p_conversation_id
      and cp.user_id=p_user_id and cp.left_at is null)
  then return true; end if;
  insert into public.conversation_participants(conversation_id,user_id,is_admin)
  values(p_conversation_id,p_user_id,false);
  return true;
end $$;
revoke all on function public.blink_group_invite(uuid,uuid) from public,anon;
grant execute on function public.blink_group_invite(uuid,uuid) to authenticated;

-- Group roster may only be fetched by an active member. The function
-- returns server-owned profile labels; it never relies on client-supplied roles.
create or replace function public.blink_group_members(p_conversation_id uuid)
returns table (
  user_id uuid, username text, full_name text, avatar_url text,
  is_admin boolean, joined_at timestamptz
)
language plpgsql stable security definer set search_path = ''
as $$
begin
  if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if not exists(
    select 1 from public.conversations c
    join public.conversation_participants cp on cp.conversation_id=c.id
    where c.id=p_conversation_id and c.is_group=true
      and cp.user_id=auth.uid() and cp.left_at is null
  ) then raise exception 'NOT_AN_ACTIVE_GROUP_MEMBER'; end if;
  return query
    select cp.user_id,p.username,p.full_name,p.avatar_url,
           cp.is_admin,cp.joined_at
    from public.conversation_participants cp
    join public.profiles p on p.id=cp.user_id
    where cp.conversation_id=p_conversation_id and cp.left_at is null
    order by cp.is_admin desc,cp.joined_at asc,cp.user_id asc
    limit 50;
end $$;
revoke all on function public.blink_group_members(uuid) from public,anon;
grant execute on function public.blink_group_members(uuid) to authenticated;

create or replace function public.blink_group_leave(p_conversation_id uuid)
returns boolean language plpgsql security definer set search_path = ''
as $$
declare v_user uuid:=auth.uid(); v_owner uuid;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select created_by into v_owner from public.conversations
    where id=p_conversation_id and is_group=true;
  if v_owner is null then raise exception 'GROUP_NOT_FOUND'; end if;
  if v_owner=v_user then raise exception 'GROUP_OWNER_MUST_TRANSFER_FIRST'; end if;
  update public.conversation_participants set left_at=now(),is_admin=false
   where conversation_id=p_conversation_id and user_id=v_user and left_at is null;
  return found;
end $$;
revoke all on function public.blink_group_leave(uuid) from public,anon;
grant execute on function public.blink_group_leave(uuid) to authenticated;

create or replace function public.blink_group_manage_member(
  p_conversation_id uuid, p_user_id uuid, p_action text
) returns boolean language plpgsql security definer set search_path = ''
as $$
declare v_actor uuid:=auth.uid(); v_owner uuid; v_admin boolean;
begin
  if v_actor is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select created_by into v_owner from public.conversations c
    where c.id=p_conversation_id and c.is_group=true;
  if v_owner is null then raise exception 'GROUP_NOT_FOUND'; end if;
  v_admin := exists(
    select 1 from public.conversation_participants cp
     where cp.conversation_id=p_conversation_id and cp.user_id=v_actor
       and cp.is_admin=true and cp.left_at is null
  );
  if not v_admin then raise exception 'GROUP_ADMIN_REQUIRED'; end if;
  if p_user_id is null or p_user_id=v_actor or p_user_id=v_owner
  then raise exception 'CANNOT_MODERATE_GROUP_OWNER_OR_SELF'; end if;
  if p_action not in ('remove','promote','demote') then
    raise exception 'INVALID_GROUP_ACTION'; end if;
  if p_action in ('promote','demote') and v_actor<>v_owner then
    raise exception 'GROUP_OWNER_REQUIRED'; end if;
  if p_action='remove' then
    update public.conversation_participants set left_at=now(),is_admin=false
      where conversation_id=p_conversation_id
        and user_id=p_user_id and left_at is null;
  else
    update public.conversation_participants set is_admin=(p_action='promote')
      where conversation_id=p_conversation_id
        and user_id=p_user_id and left_at is null;
  end if;
  return found;
end $$;
revoke all on function public.blink_group_manage_member(uuid,uuid,text) from public,anon;
grant execute on function public.blink_group_manage_member(uuid,uuid,text) to authenticated;

-- A creator must transfer ownership to an already active member before
-- leaving. This atomic RPC is the only supported ownership transition; raw
-- client edits to created_by remain blocked by the metadata trigger.
create or replace function public.blink_group_transfer_owner(
  p_conversation_id uuid, p_new_owner_id uuid
) returns boolean language plpgsql security definer set search_path=''
as $$
declare v_actor uuid:=auth.uid(); v_owner uuid;
begin
  if v_actor is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select c.created_by into v_owner from public.conversations c
    where c.id=p_conversation_id and c.is_group=true for update;
  if v_owner is null then raise exception 'GROUP_NOT_FOUND'; end if;
  if v_owner<>v_actor then raise exception 'GROUP_OWNER_REQUIRED'; end if;
  if p_new_owner_id is null or p_new_owner_id=v_actor then
    raise exception 'INVALID_NEW_GROUP_OWNER';
  end if;
  if not exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id=p_conversation_id
      and cp.user_id=p_new_owner_id and cp.left_at is null
  ) then raise exception 'NEW_OWNER_MUST_BE_ACTIVE_MEMBER'; end if;
  update public.conversation_participants
    set is_admin=true
    where conversation_id=p_conversation_id and user_id=p_new_owner_id
      and left_at is null;
  update public.conversations set created_by=p_new_owner_id
    where id=p_conversation_id and created_by=v_actor and is_group=true;
  if not found then raise exception 'GROUP_OWNER_CHANGED'; end if;
  return true;
end;
$$;
revoke all on function public.blink_group_transfer_owner(uuid,uuid)
  from public,anon;
grant execute on function public.blink_group_transfer_owner(uuid,uuid)
  to authenticated;

commit;

-- Required local tests:
-- 1. Self-join private group denied by cp_insert_own even when group is
--    hidden from the caller by conversations RLS.
-- 2. Prevent unauthorized self-join to ANY stranger DM or group; self-promote,
--    account reassignment, forged joined_at and
--    departed-member rejoin denied.
-- 3. Owner/server-authorized group creation still produces 3–50 member rows.
-- 4. Departed account cannot SELECT group/messages or send_group_message.
-- 5. DM send/reconnect and legacy receipts still work with active members.
-- 6. No anonymous exposure and repeated migrations are idempotent.
-- 7. Inspect all other permissive RLS policies before promotion: PostgreSQL
--    combines permissive policies with OR, which can defeat these changes.
-- 8. Version with Supabase CLI and test against full preview schema. Production
--    deployment requires a separately authorized, verified release.
