-- Follow-up hardening for the professional chat/call foundations.
-- Prevent self-elevation through broad participant UPDATE privileges and avoid recursive call RLS.

create or replace function private.is_call_participant(p_call_id uuid, p_user_id uuid)
returns boolean
language sql
security definer
set search_path = ''
stable
as $$
  select exists (
    select 1 from public.calls c
    where c.id = p_call_id and (c.caller_id = p_user_id or c.callee_id = p_user_id)
  ) or exists (
    select 1 from public.call_participants cp
    where cp.call_id = p_call_id and cp.user_id = p_user_id
  );
$$;

revoke all on function private.is_call_participant(uuid,uuid) from public, anon, authenticated;

drop policy if exists "call_participants_participant_select" on public.call_participants;
create policy "call_participants_participant_select" on public.call_participants
for select to authenticated
using (private.is_call_participant(call_participants.call_id, (select auth.uid())));

-- Ordinary users only need to advance their read cursor directly. Privileged participant
-- fields are changed through checked SECURITY DEFINER RPCs, not arbitrary table UPDATEs.
revoke update on public.conversation_participants from authenticated;
grant update(last_read_at) on public.conversation_participants to authenticated;

drop trigger if exists trg_guard_conversation_participant_privileges on public.conversation_participants;

create or replace function public.accept_group_invite(p_token uuid)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
  v_invite public.conversation_invites;
  v_requires_approval boolean;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select * into v_invite from public.conversation_invites where token=p_token for update;
  if not found then raise exception 'INVITE_NOT_FOUND'; end if;
  if v_invite.revoked_at is not null then raise exception 'INVITE_REVOKED'; end if;
  if v_invite.expires_at is not null and v_invite.expires_at<=now() then raise exception 'INVITE_EXPIRED'; end if;
  if v_invite.max_uses is not null and v_invite.uses>=v_invite.max_uses then raise exception 'INVITE_EXHAUSTED'; end if;
  if not private.chat_scope_allows(v_invite.created_by,v_me,'group_invite') then raise exception 'GROUP_INVITE_NOT_ALLOWED'; end if;

  select c.join_approval_required into v_requires_approval
  from public.conversations c where c.id=v_invite.conversation_id;

  if coalesce(v_requires_approval,false) then
    insert into public.conversation_join_requests(conversation_id,user_id,status,requested_at)
    values (v_invite.conversation_id,v_me,'pending',now())
    on conflict (conversation_id,user_id)
    do update set status='pending',requested_at=now(),resolved_at=null,resolved_by=null;
  else
    insert into public.conversation_participants(conversation_id,user_id,last_read_at,is_admin,joined_at,role,left_at)
    values (v_invite.conversation_id,v_me,now(),false,now(),'member',null)
    on conflict (conversation_id,user_id)
    do update set left_at=null,role='member',is_admin=false;

    update public.conversation_invites set uses=uses+1 where id=v_invite.id;
    insert into public.conversation_events(conversation_id,actor_id,target_user_id,event_type)
    values (v_invite.conversation_id,v_me,v_me,'member_joined');
  end if;
  return v_invite.conversation_id;
end;
$$;

create or replace function public.respond_group_join_request(p_request_id uuid,p_approve boolean)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
  v_request public.conversation_join_requests;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select * into v_request from public.conversation_join_requests where id=p_request_id for update;
  if not found or v_request.status<>'pending' then return false; end if;
  if not exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id=v_request.conversation_id
      and cp.user_id=v_me and cp.role in ('owner','admin') and cp.left_at is null
  ) then raise exception 'GROUP_ADMIN_REQUIRED'; end if;

  update public.conversation_join_requests
  set status=case when p_approve then 'approved' else 'declined' end,
      resolved_at=now(),resolved_by=v_me
  where id=p_request_id;

  if p_approve then
    insert into public.conversation_participants(conversation_id,user_id,last_read_at,is_admin,joined_at,role,left_at)
    values (v_request.conversation_id,v_request.user_id,now(),false,now(),'member',null)
    on conflict (conversation_id,user_id)
    do update set left_at=null,role='member',is_admin=false;
  end if;

  insert into public.conversation_events(conversation_id,actor_id,target_user_id,event_type)
  values (v_request.conversation_id,v_me,v_request.user_id,
          case when p_approve then 'join_request_approved' else 'join_request_declined' end);
  return true;
end;
$$;

revoke all on function public.accept_group_invite(uuid) from public, anon;
revoke all on function public.respond_group_join_request(uuid,boolean) from public, anon;
grant execute on function public.accept_group_invite(uuid) to authenticated;
grant execute on function public.respond_group_join_request(uuid,boolean) to authenticated;
