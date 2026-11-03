-- BLINK active-membership direct message hardening (STAGING CANDIDATE).
-- Not a production migration. Test against production-compatible local DB
-- and explicit two-account privacy/re-entry scenarios before deployment.
--
-- Existing SECURITY DEFINER DM RPCs can select a conversation after either
-- participant has left (left_at IS NOT NULL), despite hardened message RLS.
-- A fresh deliberate DM must use a new direct conversation, not silently
-- rejoin a closed conversation or mutate its previous members.
begin;

create or replace function public.get_or_create_direct_conversation(
  p_receiver_username text
) returns uuid language plpgsql security definer set search_path=''
as $$
declare v_me uuid:=auth.uid(); v_receiver uuid; v_conversation uuid;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if nullif(btrim(p_receiver_username),'') is null then
    raise exception 'RECIPIENT_REQUIRED';
  end if;
  select p.id into v_receiver from public.profiles p
  where lower(btrim(p.username))=lower(btrim(p_receiver_username))
  limit 1;
  if v_receiver is null then raise exception 'RECIPIENT_NOT_FOUND'; end if;
  if v_receiver=v_me then raise exception 'CANNOT_MESSAGE_SELF'; end if;
  if exists (
    select 1 from public.blocks b
    where (b.blocker_id=v_me and b.blocked_id=v_receiver)
       or (b.blocker_id=v_receiver and b.blocked_id=v_me)
  ) then raise exception 'USER_BLOCKED'; end if;

  select c.id into v_conversation
  from public.conversations c
  where coalesce(c.is_group,false)=false
    and exists (
      select 1 from public.conversation_participants cp
      where cp.conversation_id=c.id and cp.user_id=v_me
        and cp.left_at is null
    )
    and exists (
      select 1 from public.conversation_participants cp
      where cp.conversation_id=c.id and cp.user_id=v_receiver
        and cp.left_at is null
    )
    and (select count(*) from public.conversation_participants cp
         where cp.conversation_id=c.id)=2
  order by c.created_at desc,c.id desc
  limit 1;

  if v_conversation is null then
    insert into public.conversations(created_by,is_group,last_message_at)
    values(v_me,false,now()) returning id into v_conversation;
    insert into public.conversation_participants(conversation_id,user_id)
    values(v_conversation,v_me),(v_conversation,v_receiver)
    on conflict do nothing;
  end if;
  return v_conversation;
end;
$$;
revoke all on function public.get_or_create_direct_conversation(text)
  from public,anon;
grant execute on function public.get_or_create_direct_conversation(text)
  to authenticated;

-- Preserve the legacy username/full_name/handle resolution and return type,
-- but never send through a conversation whose participant left.
create or replace function public.send_message(
  p_receiver_username text,p_content text
) returns uuid language plpgsql security definer set search_path=''
as $$
declare
  v_sender uuid:=auth.uid();
  v_receiver uuid; v_conversation uuid; v_message uuid;
  v_identifier text:=lower(trim(leading '@' from coalesce(p_receiver_username,'')));
begin
  if v_sender is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if v_identifier='' then raise exception 'RECIPIENT_REQUIRED'; end if;
  if nullif(btrim(p_content),'') is null then raise exception 'MESSAGE_REQUIRED'; end if;

  select p.id into v_receiver
  from public.profiles p
  where lower(btrim(p.username))=v_identifier
     or lower(btrim(coalesce(p.handle,'')))=v_identifier
     or (lower(btrim(p.full_name))=v_identifier
         and lower(btrim(p.full_name))<>'blink user')
  order by case when lower(btrim(p.username))=v_identifier then 0 else 1 end
  limit 1;
  if v_receiver is null then raise exception 'RECIPIENT_NOT_FOUND'; end if;
  if v_receiver=v_sender then raise exception 'CANNOT_MESSAGE_SELF'; end if;
  if exists (
    select 1 from public.blocks b
    where (b.blocker_id=v_sender and b.blocked_id=v_receiver)
       or (b.blocker_id=v_receiver and b.blocked_id=v_sender)
  ) then raise exception 'USER_BLOCKED'; end if;

  select c.id into v_conversation
  from public.conversations c
  where coalesce(c.is_group,false)=false
    and exists (
      select 1 from public.conversation_participants cp
      where cp.conversation_id=c.id and cp.user_id=v_sender
        and cp.left_at is null
    )
    and exists (
      select 1 from public.conversation_participants cp
      where cp.conversation_id=c.id and cp.user_id=v_receiver
        and cp.left_at is null
    )
    and (select count(*) from public.conversation_participants cp
         where cp.conversation_id=c.id)=2
  order by c.created_at desc,c.id desc
  limit 1;

  if v_conversation is null then
    insert into public.conversations(created_by,is_group,last_message_at)
    values(v_sender,false,now()) returning id into v_conversation;
    insert into public.conversation_participants(conversation_id,user_id)
    values(v_conversation,v_sender),(v_conversation,v_receiver)
    on conflict do nothing;
  end if;

  insert into public.messages(conversation_id,sender_id,content,message_type)
  values(v_conversation,v_sender,btrim(p_content),'text')
  returning id into v_message;
  update public.conversations
    set last_message_at=now(),updated_at=now()
    where id=v_conversation;
  return v_message;
end;
$$;
revoke all on function public.send_message(text,text) from public,anon;
grant execute on function public.send_message(text,text) to authenticated;
commit;
