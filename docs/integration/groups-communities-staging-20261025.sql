-- BLINK COMMUNITIES / GROUPS — DESIGN CANDIDATE, NOT A DEPLOYED MIGRATION.
-- DO NOT run on production. First create a versioned Supabase migration with
-- Supabase CLI and test it on an isolated free local Postgres/Supabase instance.
--
-- Verified production 2026-10-25:
--   create_group_conversation(p_title text,p_member_ids uuid[]) exists,
--   enforces authenticated creator, 3–50 members, valid profiles and no blocks.
--   get_conversation_summaries_page currently JOINS one row per other member;
--   for groups it repeats the same conversation and returns one member's name.
--   It must not be treated as a production-ready group inbox.
--   No public communities/community_members tables currently exist.
--
-- Community foundation proposal (server-vetted community and moderation operations):
begin;

create table if not exists public.blink_communities (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    name text not null check (length(btrim(name)) between 3 and 80),
    description text not null default '' check (length(description) <= 1500),
    cover_url text,
    university text not null check (length(btrim(university)) between 2 and 150),
    category text not null check (length(btrim(category)) between 2 and 80),
    visibility text not null default 'public' check (visibility in ('public','private')),
    rules text[] not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.blink_community_members (
    community_id uuid not null references public.blink_communities(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null default 'member' check (role in ('member','moderator')),
    status text not null default 'pending' check (status in ('invited','pending','active','banned')),
    joined_at timestamptz not null default now(),
    primary key (community_id,user_id)
);

create index if not exists blink_communities_university_newest
    on public.blink_communities (university,created_at desc);
create index if not exists blink_community_members_user
    on public.blink_community_members (user_id,community_id);

alter table public.blink_communities enable row level security;
alter table public.blink_community_members enable row level security;

-- Active or invited members can see their community before accepting without exposing private
-- communities to general discovery. SECURITY DEFINER avoids recursive member
-- and community RLS lookups; it never returns member identities or private data.
create or replace function public.blink_community_member_active(p_community_id uuid)
returns boolean language sql stable security definer set search_path = ''
as $$
  select (select auth.uid()) is not null and exists (
    select 1 from public.blink_community_members cm
    where cm.community_id = p_community_id
      and cm.user_id = (select auth.uid())
      and cm.status in ('active','invited')
  )
$$;
revoke all on function public.blink_community_member_active(uuid) from public,anon;
grant execute on function public.blink_community_member_active(uuid) to authenticated;

drop policy if exists blink_communities_select on public.blink_communities;
create policy blink_communities_select on public.blink_communities
  for select to authenticated
  using (
    visibility='public' or owner_id=(select auth.uid())
    or public.blink_community_member_active(id)
  );
drop policy if exists blink_communities_insert on public.blink_communities;
create policy blink_communities_insert on public.blink_communities
  for insert to authenticated with check (owner_id=(select auth.uid()));
drop policy if exists blink_communities_update on public.blink_communities;
create policy blink_communities_update on public.blink_communities
  for update to authenticated using (owner_id=(select auth.uid()))
  with check (owner_id=(select auth.uid()));
drop policy if exists blink_communities_delete on public.blink_communities;
create policy blink_communities_delete on public.blink_communities
  for delete to authenticated using (owner_id=(select auth.uid()));

-- Members may see their own membership; owners may see join requests.
-- Raw user writes are deliberately denied: all join/invite/moderation actions
-- below run server-side and check caller privileges and state transitions.
drop policy if exists blink_community_members_read on public.blink_community_members;
create policy blink_community_members_read on public.blink_community_members
  for select to authenticated
  using (
    user_id=(select auth.uid()) or exists (
      select 1 from public.blink_communities c
      where c.id=community_id and c.owner_id=(select auth.uid())
    )
  );

grant select,insert,update,delete on public.blink_communities to authenticated;
grant select on public.blink_community_members to authenticated;
revoke insert,update,delete on public.blink_community_members from anon,authenticated;

-- A public community accepts immediate joins. Private communities require an
-- invitation; nobody can bypass a ban with a fresh self-join.
create or replace function public.blink_community_join(p_community_id uuid)
returns text language plpgsql security definer set search_path = ''
as $$
declare v_user uuid := auth.uid(); v_visibility text; v_status text;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select visibility into v_visibility from public.blink_communities
    where id=p_community_id;
  if v_visibility is null then raise exception 'COMMUNITY_NOT_FOUND'; end if;
  select status into v_status from public.blink_community_members
    where community_id=p_community_id and user_id=v_user for update;
  if v_status='banned' then raise exception 'COMMUNITY_MEMBERSHIP_BANNED'; end if;
  if v_status='active' then return 'active'; end if;
  if v_visibility='private' and v_status is distinct from 'invited' then
    raise exception 'COMMUNITY_INVITE_REQUIRED';
  end if;
  insert into public.blink_community_members(community_id,user_id,role,status)
  values (p_community_id,v_user,'member','active')
  on conflict (community_id,user_id) do update set status='active';
  return 'active';
end $$;
revoke all on function public.blink_community_join(uuid) from public,anon;
grant execute on function public.blink_community_join(uuid) to authenticated;

-- Private join requests remain pending until an owner/moderator approves.
-- A banned member cannot re-request membership, even if their previous row
-- remains for audit and moderation.
create or replace function public.blink_community_request_join(p_community_id uuid)
returns text language plpgsql security definer set search_path = ''
as $$
declare v_user uuid:=auth.uid(); v_visibility text; v_status text;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select visibility into v_visibility
    from public.blink_communities where id=p_community_id;
  if v_visibility is null then raise exception 'COMMUNITY_NOT_FOUND'; end if;
  select status into v_status from public.blink_community_members
    where community_id=p_community_id and user_id=v_user for update;
  if v_status='banned' then raise exception 'COMMUNITY_MEMBERSHIP_BANNED'; end if;
  if v_status='active' then return 'active'; end if;
  if v_status='invited' then
    return public.blink_community_join(p_community_id);
  end if;
  if v_visibility='public' then
    return public.blink_community_join(p_community_id);
  end if;
  insert into public.blink_community_members(community_id,user_id,role,status)
    values(p_community_id,v_user,'member','pending')
  on conflict(community_id,user_id)
    do update set status='pending';
  return 'pending';
end $$;
revoke all on function public.blink_community_request_join(uuid) from public,anon;
grant execute on function public.blink_community_request_join(uuid) to authenticated;

create or replace function public.blink_community_invite(p_community_id uuid,p_user_id uuid)
returns text language plpgsql security definer set search_path = ''
as $$
declare v_user uuid:=auth.uid(); v_owner uuid; v_allowed boolean; v_status text;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_user_id is null or p_user_id=v_user then raise exception 'INVALID_INVITEE'; end if;
  select owner_id into v_owner from public.blink_communities where id=p_community_id;
  if v_owner is null then raise exception 'COMMUNITY_NOT_FOUND'; end if;
  select v_owner=v_user or exists (
    select 1 from public.blink_community_members cm
    where cm.community_id=p_community_id and cm.user_id=v_user
      and cm.status='active' and cm.role='moderator'
  ) into v_allowed;
  if not v_allowed then raise exception 'COMMUNITY_ADMIN_REQUIRED'; end if;
  if not exists(select 1 from public.profiles p where p.id=p_user_id) then
    raise exception 'USER_NOT_FOUND';
  end if;
  if exists(select 1 from public.blocks b where
    (b.blocker_id=v_user and b.blocked_id=p_user_id)
    or (b.blocker_id=p_user_id and b.blocked_id=v_user)
  ) then raise exception 'COMMUNITY_INVITATION_BLOCKED'; end if;
  select status into v_status from public.blink_community_members
   where community_id=p_community_id and user_id=p_user_id for update;
  if v_status='banned' then raise exception 'COMMUNITY_MEMBERSHIP_BANNED'; end if;
  if v_status='active' then return 'active'; end if;
  insert into public.blink_community_members(community_id,user_id,role,status)
  values (p_community_id,p_user_id,'member','invited')
  on conflict(community_id,user_id) do update set status='invited';
  return 'invited';
end $$;
revoke all on function public.blink_community_invite(uuid,uuid) from public,anon;
grant execute on function public.blink_community_invite(uuid,uuid) to authenticated;

create or replace function public.blink_community_leave(p_community_id uuid)
returns boolean language plpgsql security definer set search_path = ''
as $$
declare v_user uuid:=auth.uid(); v_owner uuid;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select owner_id into v_owner from public.blink_communities where id=p_community_id;
  if v_owner=v_user then raise exception 'OWNER_MUST_TRANSFER_OR_DELETE_COMMUNITY'; end if;
  delete from public.blink_community_members
    where community_id=p_community_id and user_id=v_user;
  return found;
end $$;
revoke all on function public.blink_community_leave(uuid) from public,anon;
grant execute on function public.blink_community_leave(uuid) to authenticated;

create or replace function public.blink_community_moderate(
  p_community_id uuid, p_target uuid, p_action text
) returns boolean language plpgsql security definer set search_path = ''
as $$
declare v_actor uuid:=auth.uid(); v_owner uuid; v_admin boolean;
begin
  if v_actor is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select owner_id into v_owner from public.blink_communities where id=p_community_id;
  if v_owner is null then raise exception 'COMMUNITY_NOT_FOUND'; end if;
  v_admin := v_owner=v_actor or exists(
    select 1 from public.blink_community_members cm
    where cm.community_id=p_community_id and cm.user_id=v_actor
      and cm.status='active' and cm.role='moderator'
  );
  if not v_admin then raise exception 'COMMUNITY_ADMIN_REQUIRED'; end if;
  if p_target=v_owner or p_target=v_actor then raise exception 'CANNOT_MODERATE_OWNER_OR_SELF'; end if;
  if p_action not in ('approve','ban','remove','promote','demote') then
    raise exception 'INVALID_MODERATION_ACTION'; end if;
  if p_action in ('promote','demote') and v_actor<>v_owner then
    raise exception 'COMMUNITY_OWNER_REQUIRED'; end if;
  if p_action='approve' then
    update public.blink_community_members set status='active'
      where community_id=p_community_id and user_id=p_target
        and status in ('pending','invited');
  elsif p_action='ban' then
    update public.blink_community_members set status='banned',role='member'
      where community_id=p_community_id and user_id=p_target;
  elsif p_action='remove' then
    delete from public.blink_community_members
      where community_id=p_community_id and user_id=p_target;
  elsif p_action='promote' then
    update public.blink_community_members set role='moderator'
      where community_id=p_community_id and user_id=p_target and status='active';
  else
    update public.blink_community_members set role='member'
      where community_id=p_community_id and user_id=p_target and status='active';
  end if;
  return found;
end $$;
revoke all on function public.blink_community_moderate(uuid,uuid,text) from public,anon;
grant execute on function public.blink_community_moderate(uuid,uuid,text) to authenticated;

-- A community's roster and join-request queue is visible only to an
-- authorized owner/active moderator. All profile names are sourced from
-- authenticated database rows, never from client-supplied member claims.
create or replace function public.blink_community_members_page(
  p_community_id uuid,p_limit integer default 50
) returns table (
  user_id uuid, username text, full_name text, avatar_url text,
  role text,status text,joined_at timestamptz
)
language plpgsql stable security definer set search_path=''
as $$
declare v_uid uuid:=auth.uid();v_owner uuid;v_can_moderate boolean;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select c.owner_id into v_owner from public.blink_communities c
    where c.id=p_community_id;
  if v_owner is null then raise exception 'COMMUNITY_NOT_FOUND'; end if;
  v_can_moderate := v_uid=v_owner or exists (
    select 1 from public.blink_community_members cm
    where cm.community_id=p_community_id and cm.user_id=v_uid
      and cm.role='moderator' and cm.status='active'
  );
  if not v_can_moderate then raise exception 'COMMUNITY_ADMIN_REQUIRED'; end if;
  return query
    select cm.user_id,p.username,p.full_name,p.avatar_url,cm.role,cm.status,cm.joined_at
    from public.blink_community_members cm
    join public.profiles p on p.id=cm.user_id
    where cm.community_id=p_community_id
    order by case cm.status when 'pending' then 0 when 'invited' then 1
      when 'active' then 2 else 3 end,cm.joined_at desc,cm.user_id
    limit greatest(1,least(coalesce(p_limit,50),100));
end $$;
revoke all on function public.blink_community_members_page(uuid,integer)
  from public,anon;
grant execute on function public.blink_community_members_page(uuid,integer)
  to authenticated;

-- Isolated server-backed group listing; never modify existing DM summary RPC
-- until Android/Windows consumers support group titles and member counts.
create or replace function public.blink_my_group_conversations(p_limit integer default 50)
returns table (
    group_id uuid, title text, description text,
    avatar_url text, member_count bigint, updated_at timestamptz
)
language sql stable security definer set search_path = ''
as $$
    select
        c.id,
        coalesce(nullif(c.title,''),'Group'),
        coalesce(c.description,''),
        c.avatar_url,
        (select count(*) from public.conversation_participants cp
         where cp.conversation_id=c.id and cp.left_at is null),
        c.updated_at
    from public.conversations c
    join public.conversation_participants mine
      on mine.conversation_id=c.id
      and mine.user_id=auth.uid()
      and mine.left_at is null
    where auth.uid() is not null and c.is_group=true
    order by c.updated_at desc,c.id desc
    limit greatest(1,least(coalesce(p_limit,50),100))
$$;
revoke all on function public.blink_my_group_conversations(integer)
    from public,anon;
grant execute on function public.blink_my_group_conversations(integer)
    to authenticated;

commit;

-- RELEASE HOLD / FOLLOW-ON TESTS:
-- a. Verify owner cannot reassign community ownership; blocked/banned accounts
--    cannot rejoin or view private content.
-- b. Private communities require a vetted membership-aware read policy BEFORE
--    enabling member views; the above owner/public SELECT is intentionally limited.
-- c. Fix moderator permission escalation and self-banning/role takeover tests.
-- d. Test SQL RLS with two different authenticated IDs and no JWT.
-- e. Test group message send/read/retry, online/offline and 3–50 members;
--    group summary must show ONE row per group, not a fake 1:1 conversation.
-- f. Test Android and Windows parity, invite/leave/member moderation screens.
-- g. No production deployment until security review and all staging checks pass.
