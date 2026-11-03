-- BLINK text-message notification fan-out and departed-member isolation.
-- STAGING ONLY; do not deploy before two-account/group tests and push review.
-- Hosted notify_message_created() currently chooses LIMIT 1 from other
-- participants and omits left_at. This loses notifications in group chats
-- and can alert members who already left a direct conversation.
begin;
create or replace function public.notify_message_created()
returns trigger language plpgsql security definer set search_path=''
as $$
declare v_username text;
begin
  -- Do not fan out notifications from an invalid/departed sender. The caller
  -- and message-write RLS must separately enforce message authorization.
  if new.sender_id is null or not exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id=new.conversation_id
      and cp.user_id=new.sender_id and cp.left_at is null
  ) then return new; end if;

  select p.username into v_username from public.profiles p
  where p.id=new.sender_id;

  insert into public.notifications(
    user_id,actor_id,type,text,sub_text,is_read
  )
  select cp.user_id,new.sender_id,'system',
    coalesce('@'||v_username,'Someone')||' sent you a message',
    left(coalesce(new.content,''),160),false
  from public.conversation_participants cp
  where cp.conversation_id=new.conversation_id
    and cp.user_id<>new.sender_id
    and cp.left_at is null;
  return new;
end;
$$;
-- Trigger definition is unchanged; existing production trigger
-- trg_notify_message_created invokes this function after insert.
-- Revoke direct RPC EXECUTE: trigger functions are not user APIs.
revoke all on function public.notify_message_created() from public,anon,authenticated;
commit;
