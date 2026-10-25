-- Opt-in group invitations. Never add third-party users to a group before acceptance.
-- Staged-only. Uses existing public.conversations and conversation_participants.

create table if not exists public.blink_group_invitations (
  group_id uuid not null references public.conversations(id) on delete cascade,
  invited_user_id uuid not null references public.profiles(id) on delete cascade,
  invited_by uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending','accepted','declined')),
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  primary key(group_id,invited_user_id),
  constraint blink_group_no_self_invite check(invited_user_id<>invited_by)
);
create index if not exists blink_group_invites_recipient_idx
  on public.blink_group_invitations(invited_user_id,created_at desc)
  where status='pending';
alter table public.blink_group_invitations enable row level security;
revoke all on public.blink_group_invitations from public,anon,authenticated,service_role;
grant select on public.blink_group_invitations to authenticated,service_role;
create policy blink_group_invites_read_own on public.blink_group_invitations
 for select to authenticated
 using(invited_user_id=(select auth.uid()) or invited_by=(select auth.uid()));
-- No direct INSERT or UPDATE policies: only SECURITY DEFINER RPCs are allowed.

create or replace function public.blink_create_invite_only_group(
 p_title text,p_member_ids uuid[]
) returns uuid language plpgsql security definer set search_path=''
as $$
declare
  owner_id uuid := (select auth.uid());
  new_group uuid;
  requested integer;
begin
  if owner_id is null then raise exception 'SIGN_IN_REQUIRED'; end if;
  if char_length(trim(coalesce(p_title,''))) not between 2 and 80 then
    raise exception 'GROUP_TITLE_INVALID';
  end if;
  select count(distinct person)::integer into requested
    from unnest(coalesce(p_member_ids,'{}'::uuid[])) as person where person is not null;
  if requested<2 or requested>30 then raise exception 'GROUP_INVITE_LIMIT'; end if;
  if exists(
    select 1 from unnest(p_member_ids) as person
    where person=owner_id or not exists(
      select 1 from public.profiles p
      where p.id=person and p.onboarding_completed=true
      and not exists(
        select 1 from public.user_settings us where us.user_id=p.id and us.private_account=true
      )
    ) or exists(
      select 1 from public.blocks b
      where (b.blocker_id=owner_id and b.blocked_id=person)
         or (b.blocker_id=person and b.blocked_id=owner_id)
    )
  ) then raise exception 'GROUP_INVITEE_NOT_ELIGIBLE'; end if;
  -- Limit mass-invite abuse while preserving a normal student's ability to create groups.
  if (select count(*) from public.conversations c
      where c.created_by=owner_id and c.is_group=true
        and c.created_at>now()-interval '24 hours')>=3
  then raise exception 'GROUP_CREATE_RATE_LIMIT'; end if;
  if (select count(*) from public.blink_group_invitations i
      where i.invited_by=owner_id and i.created_at>now()-interval '24 hours') + requested > 30
  then raise exception 'GROUP_INVITE_DAILY_LIMIT'; end if;
  insert into public.conversations(is_group,created_by,title,last_message_at)
  values(true,owner_id,trim(p_title),now())
  returning id into new_group;
  insert into public.conversation_participants(conversation_id,user_id,is_admin)
  values(new_group,owner_id,true);
  insert into public.blink_group_invitations(group_id,invited_user_id,invited_by)
  select new_group,person,owner_id from (select distinct unnest(p_member_ids) as person) candidate;
  -- Only notify actual invited users. Notifications are persisted even if push is off.
  insert into public.notifications(user_id,actor_id,type,target_type,target_id,text,sub_text,is_read,metadata)
  select i.invited_user_id,owner_id,'system','conversation',new_group,
      'invited you to a group',
      'Open Messages → Groups & invitations to accept or decline.',
      false, pg_catalog.jsonb_build_object('kind','blink_group_invite','group_id',new_group)
  from public.blink_group_invitations i where i.group_id=new_group;
  return new_group;
end $$;
revoke all on function public.blink_create_invite_only_group(text,uuid[]) from public,anon,authenticated;
grant execute on function public.blink_create_invite_only_group(text,uuid[]) to authenticated;

create or replace function public.blink_my_group_invitations(p_limit integer default 20)
returns table(group_id uuid,group_title text,invited_by uuid,inviter_username text,created_at timestamptz)
language sql stable security definer set search_path=''
as $$
 select i.group_id,c.title,i.invited_by,p.username,i.created_at
 from public.blink_group_invitations i
 join public.conversations c on c.id=i.group_id and c.is_group=true
 join public.profiles p on p.id=i.invited_by
 where i.invited_user_id=(select auth.uid()) and i.status='pending'
 order by i.created_at desc
 limit greatest(1,least(coalesce(p_limit,20),30));
$$;
revoke all on function public.blink_my_group_invitations(integer) from public,anon,authenticated;
grant execute on function public.blink_my_group_invitations(integer) to authenticated;

create or replace function public.blink_respond_to_group_invite(
 p_group_id uuid,p_accept boolean
) returns boolean language plpgsql security definer set search_path=''
as $$
declare
  target_id uuid := (select auth.uid());
  pending public.blink_group_invitations%rowtype;
  existing_count integer;
begin
  if target_id is null or p_group_id is null or p_accept is null then
    return false;
  end if;
  select * into pending from public.blink_group_invitations i
  where i.group_id=p_group_id and i.invited_user_id=target_id and i.status='pending'
  for update;
  if not found then return false; end if;
  if p_accept then
    perform 1 from public.conversations c
     where c.id=p_group_id and c.is_group=true for update;
    if not found then return false; end if;
    select count(*) into existing_count from public.conversation_participants m
    where m.conversation_id=p_group_id and m.left_at is null;
    if existing_count>=50 then raise exception 'GROUP_IS_FULL'; end if;
    if exists(select 1 from public.blocks b
      where (b.blocker_id=target_id and b.blocked_id=pending.invited_by)
         or (b.blocker_id=pending.invited_by and b.blocked_id=target_id))
    then raise exception 'GROUP_INVITATION_BLOCKED'; end if;
    insert into public.conversation_participants(conversation_id,user_id,is_admin)
    values(p_group_id,target_id,false)
    on conflict(conversation_id,user_id) do update set left_at=null;
  end if;
  update public.blink_group_invitations set status=case when p_accept then 'accepted' else 'declined' end,
     responded_at=now() where group_id=p_group_id and invited_user_id=target_id;
  return true;
end $$;
revoke all on function public.blink_respond_to_group_invite(uuid,boolean) from public,anon,authenticated;
grant execute on function public.blink_respond_to_group_invite(uuid,boolean) to authenticated;
