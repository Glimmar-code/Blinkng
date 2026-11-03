begin;

create or replace function public.notify_follow_created()
returns trigger language plpgsql security definer set search_path=public as $$
declare actor_username text;
begin
 if new.follower_id<>new.following_id then
   select username into actor_username from public.profiles where id=new.follower_id;
   insert into public.notifications(user_id,actor_id,type,text,sub_text,is_read)
   values(new.following_id,new.follower_id,'follow',coalesce('@'||actor_username,'Someone')||' started following you',null,false);
 end if;
 return new;
end; $$;

create or replace function public.notify_message_created()
returns trigger language plpgsql security definer set search_path=public as $$
declare recipient uuid; actor_username text;
begin
 select cp.user_id into recipient
 from public.conversation_participants cp
 where cp.conversation_id=new.conversation_id and cp.user_id<>new.sender_id
 limit 1;
 select username into actor_username from public.profiles where id=new.sender_id;
 if recipient is not null then
   insert into public.notifications(user_id,actor_id,type,text,sub_text,is_read)
   values(recipient,new.sender_id,'system',coalesce('@'||actor_username,'Someone')||' sent you a message',left(coalesce(new.content,''),160),false);
 end if;
 return new;
end; $$;

commit;
