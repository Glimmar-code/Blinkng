-- BLINK group invitation consent (STAGING CANDIDATE ONLY).
-- Deploy only after production-compatible Supabase/RLS and multi-account
-- integration tests. Replaces immediate membership from blink_group_invite.
begin;

create table if not exists public.blink_group_invitations (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  invitee_id uuid not null references auth.users(id) on delete cascade,
  inviter_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'pending'
    check (status in ('pending','accepted','declined')),
  invited_at timestamptz not null default now(),
  responded_at timestamptz,
  primary key (conversation_id,invitee_id)
);
create index if not exists blink_group_invitations_invitee_status
  on public.blink_group_invitations(invitee_id,status,invited_at desc);

alter table public.blink_group_invitations enable row level security;
revoke all on table public.blink_group_invitations from public,anon,authenticated;
grant select on table public.blink_group_invitations to authenticated;
drop policy if exists blink_group_invitation_read_own on public.blink_group_invitations;
create policy blink_group_invitation_read_own
on public.blink_group_invitations for select to authenticated
using (invitee_id=(select auth.uid()));

create or replace function public.blink_group_invite(
  p_conversation_id uuid,p_user_id uuid
) returns boolean language plpgsql security definer set search_path=''
as $$
declare v_actor uuid:=auth.uid(); v_admin boolean; v_active integer; v_pending integer;
begin
  if v_actor is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_user_id is null or p_user_id=v_actor then raise exception 'INVALID_INVITEE'; end if;
  -- Lock the group for synchronized invite/accept capacity checks.
  perform 1 from public.conversations c
    where c.id=p_conversation_id and c.is_group=true for update;
  if not found then raise exception 'GROUP_NOT_FOUND'; end if;
  select exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id=p_conversation_id
      and cp.user_id=v_actor and cp.is_admin=true and cp.left_at is null
  ) into v_admin;
  if not v_admin then raise exception 'GROUP_ADMIN_REQUIRED'; end if;
  if not exists(select 1 from public.profiles p where p.id=p_user_id) then
    raise exception 'USER_NOT_FOUND'; end if;
  if exists(select 1 from public.conversation_participants cp
    where cp.conversation_id=p_conversation_id and cp.user_id=p_user_id
      and cp.left_at is null
  ) then return true; end if;
  if exists(
    select 1 from public.blocks b
    join public.conversation_participants cp
      on cp.conversation_id=p_conversation_id and cp.left_at is null
    where (b.blocker_id=p_user_id and b.blocked_id=cp.user_id)
       or (b.blocker_id=cp.user_id and b.blocked_id=p_user_id)
  ) then raise exception 'GROUP_INVITATION_BLOCKED'; end if;
  select count(*)::integer into v_active
  from public.conversation_participants cp
  where cp.conversation_id=p_conversation_id and cp.left_at is null;
  select count(*)::integer into v_pending
  from public.blink_group_invitations inv
  where inv.conversation_id=p_conversation_id and inv.status='pending'
    and inv.invitee_id<>p_user_id;
  if v_active+v_pending>=50 then raise exception 'GROUP_MEMBER_LIMIT'; end if;
  insert into public.blink_group_invitations(
    conversation_id,invitee_id,inviter_id,status,invited_at,responded_at
  ) values(p_conversation_id,p_user_id,v_actor,'pending',now(),null)
  on conflict(conversation_id,invitee_id) do update set
    inviter_id=excluded.inviter_id,status='pending',
    invited_at=now(),responded_at=null;
  return true;
end;
$$;
revoke all on function public.blink_group_invite(uuid,uuid) from public,anon;
grant execute on function public.blink_group_invite(uuid,uuid) to authenticated;

create or replace function public.blink_group_pending_invitations(
  p_limit integer default 50
) returns table(
  conversation_id uuid,title text,inviter_id uuid,
  inviter_username text,invited_at timestamptz
) language sql stable security definer set search_path=''
as $$
  select inv.conversation_id,coalesce(c.title,'Group chat')::text,
    inv.inviter_id,coalesce(p.username,'')::text,inv.invited_at
  from public.blink_group_invitations inv
  join public.conversations c on c.id=inv.conversation_id and c.is_group=true
  left join public.profiles p on p.id=inv.inviter_id
  where (select auth.uid()) is not null
    and inv.invitee_id=(select auth.uid()) and inv.status='pending'
  order by inv.invited_at desc,inv.conversation_id desc
  limit least(greatest(coalesce(p_limit,50),1),100)
$$;
revoke all on function public.blink_group_pending_invitations(integer) from public,anon;
grant execute on function public.blink_group_pending_invitations(integer) to authenticated;

create or replace function public.blink_group_respond_invitation(
  p_conversation_id uuid,p_accept boolean
) returns boolean language plpgsql security definer set search_path=''
as $$
declare v_actor uuid:=auth.uid(); v_inviter uuid; v_active integer;
begin
  if v_actor is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_accept is null then raise exception 'INVITATION_DECISION_REQUIRED'; end if;
  perform 1 from public.conversations c
    where c.id=p_conversation_id and c.is_group=true for update;
  if not found then raise exception 'GROUP_NOT_FOUND'; end if;
  select inv.inviter_id into v_inviter
  from public.blink_group_invitations inv
  where inv.conversation_id=p_conversation_id and inv.invitee_id=v_actor
    and inv.status='pending' for update;
  if v_inviter is null then raise exception 'GROUP_INVITATION_NOT_PENDING'; end if;

  if p_accept then
    if not exists(
      select 1 from public.conversation_participants cp
      where cp.conversation_id=p_conversation_id
        and cp.user_id=v_inviter and cp.is_admin=true and cp.left_at is null
    ) then raise exception 'GROUP_INVITER_NO_LONGER_ADMIN'; end if;
    if exists(
      select 1 from public.blocks b
      join public.conversation_participants cp
        on cp.conversation_id=p_conversation_id and cp.left_at is null
      where (b.blocker_id=v_actor and b.blocked_id=cp.user_id)
         or (b.blocker_id=cp.user_id and b.blocked_id=v_actor)
    ) then raise exception 'GROUP_INVITATION_BLOCKED'; end if;
    select count(*)::integer into v_active
    from public.conversation_participants cp
    where cp.conversation_id=p_conversation_id and cp.left_at is null;
    if v_active>=50 and not exists (
      select 1 from public.conversation_participants cp
      where cp.conversation_id=p_conversation_id
        and cp.user_id=v_actor and cp.left_at is null
    ) then raise exception 'GROUP_MEMBER_LIMIT'; end if;

    insert into public.conversation_participants(
      conversation_id,user_id,is_admin,joined_at,left_at
    ) values(p_conversation_id,v_actor,false,now(),null)
    on conflict(conversation_id,user_id) do update set
      left_at=null,joined_at=now(),is_admin=false;
  end if;

  update public.blink_group_invitations
  set status=case when p_accept then 'accepted' else 'declined' end,
    responded_at=now()
  where conversation_id=p_conversation_id and invitee_id=v_actor;
  return true;
end;
$$;
revoke all on function public.blink_group_respond_invitation(uuid,boolean)
  from public,anon;
grant execute on function public.blink_group_respond_invitation(uuid,boolean)
  to authenticated;

-- Group creation must not silently enroll non-consenting students either.
-- Validate the full requested 3–50-person roster, but activate only the
-- authenticated founder. Every other student gets a pending invitation.
-- The Android/Windows existing RPC signatures and group IDs stay unchanged.
create or replace function public.create_group_conversation(
  p_title text,p_member_ids uuid[]
) returns uuid language plpgsql security definer set search_path=''
as $$
declare v_actor uuid:=auth.uid(); v_members uuid[]; v_group uuid;
begin
  if v_actor is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if length(btrim(coalesce(p_title,'')))<2 then
    raise exception 'GROUP_TITLE_REQUIRED'; end if;
  select array_agg(distinct x) into v_members
    from unnest(array_append(coalesce(p_member_ids,'{}'::uuid[]),v_actor)) x
    where x is not null;
  if cardinality(v_members)<3 or cardinality(v_members)>50 then
    raise exception 'GROUP_MEMBER_LIMIT'; end if;
  if exists (
    select 1 from unnest(v_members) x
    where not exists (select 1 from public.profiles p where p.id=x)
  ) then raise exception 'GROUP_MEMBER_NOT_FOUND'; end if;
  if exists (
    select 1 from public.blocks b
    where b.blocker_id=any(v_members) and b.blocked_id=any(v_members)
  ) then raise exception 'GROUP_CONTAINS_BLOCKED_USERS'; end if;

  insert into public.conversations(is_group,created_by,title,last_message_at)
    values(true,v_actor,left(btrim(p_title),80),now())
    returning id into v_group;
  insert into public.conversation_participants(
    conversation_id,user_id,is_admin
  ) values(v_group,v_actor,true);
  insert into public.blink_group_invitations(
    conversation_id,invitee_id,inviter_id,status
  ) select v_group,x,v_actor,'pending' from unnest(v_members) x
    where x<>v_actor;
  return v_group;
end;
$$;
revoke all on function public.create_group_conversation(text,uuid[])
  from public,anon;
grant execute on function public.create_group_conversation(text,uuid[])
  to authenticated;

commit;
