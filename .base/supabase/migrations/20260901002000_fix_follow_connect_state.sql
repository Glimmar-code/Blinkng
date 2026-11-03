begin;

create or replace function public.follow_user(p_following_id uuid)
returns boolean language plpgsql security definer set search_path=public as $$
declare follower uuid := auth.uid();
begin
 if follower is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
 if follower=p_following_id then raise exception 'VALIDATION_ERROR'; end if;
 if not exists(select 1 from public.profiles where id=p_following_id) then raise exception 'NOT_FOUND'; end if;
 if exists(select 1 from public.blocks where (blocker_id=follower and blocked_id=p_following_id) or (blocker_id=p_following_id and blocked_id=follower)) then raise exception 'NOT_AUTHORIZED'; end if;
 insert into public.follows(follower_id,following_id) values(follower,p_following_id) on conflict do nothing;
 return true;
end; $$;

create or replace function public.unfollow_user(p_following_id uuid)
returns boolean language plpgsql security definer set search_path=public as $$
begin
 if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
 delete from public.follows where follower_id=auth.uid() and following_id=p_following_id;
 return false;
end; $$;

grant execute on function public.follow_user(uuid) to authenticated;
grant execute on function public.unfollow_user(uuid) to authenticated;

create or replace function public.sync_follow_counts()
returns trigger language plpgsql security definer set search_path=public as $$
begin
 update public.profiles set following_count=(select count(*) from public.follows where follower_id=coalesce(new.follower_id,old.follower_id)),updated_at=now() where id=coalesce(new.follower_id,old.follower_id);
 update public.profiles set follower_count=(select count(*) from public.follows where following_id=coalesce(new.following_id,old.following_id)),updated_at=now() where id=coalesce(new.following_id,old.following_id);
 return coalesce(new,old);
end; $$;

drop trigger if exists trg_sync_follow_counts on public.follows;
create trigger trg_sync_follow_counts after insert or delete on public.follows for each row execute function public.sync_follow_counts();

create or replace function public.send_connection_request(p_receiver_id uuid)
returns uuid language plpgsql security definer set search_path=public as $$
declare new_id uuid;
begin
 if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
 if auth.uid()=p_receiver_id then raise exception 'VALIDATION_ERROR'; end if;
 if not exists(select 1 from public.profiles where id=p_receiver_id) then raise exception 'NOT_FOUND'; end if;
 if exists(select 1 from public.blocks where (blocker_id=auth.uid() and blocked_id=p_receiver_id) or (blocker_id=p_receiver_id and blocked_id=auth.uid())) then raise exception 'NOT_AUTHORIZED'; end if;
 if exists(select 1 from public.connection_requests where sender_id=auth.uid() and receiver_id=p_receiver_id and status in ('pending','accepted')) then raise exception 'CONFLICT'; end if;
 insert into public.connection_requests(sender_id,receiver_id,status) values(auth.uid(),p_receiver_id,'pending') returning id into new_id;
 insert into public.notifications(user_id,actor_id,type,text,sub_text,is_read) values(p_receiver_id,auth.uid(),'system','sent you a connection request','Open Connect to respond',false);
 return new_id;
end; $$;

grant execute on function public.send_connection_request(uuid) to authenticated;

update public.profiles p set following_count=(select count(*) from public.follows f where f.follower_id=p.id),follower_count=(select count(*) from public.follows f where f.following_id=p.id);
commit;
