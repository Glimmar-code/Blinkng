begin;

create or replace function public.send_message(p_receiver_username text, p_content text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_sender uuid := auth.uid();
  v_receiver uuid;
  v_conversation uuid;
  v_message uuid;
begin
  if v_sender is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if nullif(trim(p_receiver_username), '') is null then raise exception 'RECIPIENT_REQUIRED'; end if;
  if nullif(trim(p_content), '') is null then raise exception 'MESSAGE_REQUIRED'; end if;
  select id into v_receiver from public.profiles where lower(username)=lower(trim(p_receiver_username)) limit 1;
  if v_receiver is null then raise exception 'RECIPIENT_NOT_FOUND'; end if;
  if v_receiver=v_sender then raise exception 'CANNOT_MESSAGE_SELF'; end if;
  if exists (select 1 from public.blocks b where (b.blocker_id=v_sender and b.blocked_id=v_receiver) or (b.blocker_id=v_receiver and b.blocked_id=v_sender)) then raise exception 'USER_BLOCKED'; end if;
  select c.id into v_conversation from public.conversations c
  where c.is_group=false
    and exists (select 1 from public.conversation_participants cp where cp.conversation_id=c.id and cp.user_id=v_sender)
    and exists (select 1 from public.conversation_participants cp where cp.conversation_id=c.id and cp.user_id=v_receiver)
  limit 1;
  if v_conversation is null then
    insert into public.conversations(created_by,is_group,last_message_at) values(v_sender,false,now()) returning id into v_conversation;
    insert into public.conversation_participants(conversation_id,user_id) values(v_conversation,v_sender),(v_conversation,v_receiver) on conflict do nothing;
  end if;
  insert into public.messages(conversation_id,sender_id,content,message_type) values(v_conversation,v_sender,trim(p_content),'text') returning id into v_message;
  update public.conversations set last_message_at=now(),updated_at=now() where id=v_conversation;
  return v_message;
end;
$$;

revoke all on function public.send_message(text,text) from public, anon;
grant execute on function public.send_message(text,text) to authenticated;
commit;
