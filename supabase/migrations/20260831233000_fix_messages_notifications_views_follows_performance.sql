begin;

alter table if exists public.post_views drop constraint if exists post_views_post_id_viewer_id_key;
alter table if exists public.post_views drop constraint if exists post_views_post_id_viewer_id_unique;
create index if not exists idx_post_views_post_created on public.post_views(post_id, created_at desc);
create index if not exists idx_feed_posts_created_active on public.feed_posts(is_active, created_at desc);
create index if not exists idx_feed_posts_reels on public.feed_posts(is_reel, created_at desc) where is_active = true;
create index if not exists idx_profiles_username_lower on public.profiles(lower(username));
create index if not exists idx_follows_follower_following on public.follows(follower_id, following_id);
create index if not exists idx_follows_following on public.follows(following_id);
create index if not exists idx_messages_conversation_created on public.messages(conversation_id, created_at desc);
create index if not exists idx_conversation_participants_user on public.conversation_participants(user_id, conversation_id);
create index if not exists idx_notifications_user_created on public.notifications(user_id, created_at desc);
create index if not exists idx_notifications_unread on public.notifications(user_id, is_read, created_at desc);

create or replace function public.record_post_view(p_post_id uuid)
returns integer language plpgsql security definer set search_path=public as $$
declare v_weight integer := 1; v_new_views integer;
begin
 if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
 select case verification_tier::text when 'Gold' then 3 when 'Elite' then 3 when 'Standard' then 2 else 1 end into v_weight from public.profiles where id=auth.uid();
 if not exists(select 1 from public.feed_posts where id=p_post_id and is_active=true) then return 0; end if;
 insert into public.post_views(post_id,viewer_id,view_weight) values(p_post_id,auth.uid(),coalesce(v_weight,1));
 update public.feed_posts set view_count=coalesce(view_count,0)+coalesce(v_weight,1),updated_at=now() where id=p_post_id returning view_count into v_new_views;
 begin perform public.award_points(auth.uid(),'view_post',p_post_id); exception when undefined_function then null; end;
 return coalesce(v_new_views,0);
end; $$;
grant execute on function public.record_post_view(uuid) to authenticated;

create or replace function public.record_post_view(p_post_id uuid,p_viewer_username text)
returns integer language plpgsql security definer set search_path=public as $$
begin return public.record_post_view(p_post_id); end; $$;
grant execute on function public.record_post_view(uuid,text) to authenticated;

create or replace function public.follow_user(p_following_id uuid)
returns boolean language plpgsql security definer set search_path=public as $$
declare follower uuid:=auth.uid();
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
begin if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if; delete from public.follows where follower_id=auth.uid() and following_id=p_following_id; return true; end; $$;
grant execute on function public.follow_user(uuid) to authenticated;
grant execute on function public.unfollow_user(uuid) to authenticated;

create or replace function public.notify_follow_created()
returns trigger language plpgsql security definer set search_path=public as $$
begin
 if new.follower_id<>new.following_id then insert into public.notifications(user_id,actor_id,type,text,sub_text,is_read) values(new.following_id,new.follower_id,'follow','started following you',null,false); end if;
 return new;
end; $$;
drop trigger if exists trg_notify_follow_created on public.follows;
create trigger trg_notify_follow_created after insert on public.follows for each row execute function public.notify_follow_created();

create or replace function public.notify_message_created()
returns trigger language plpgsql security definer set search_path=public as $$
declare recipient uuid;
begin
 select cp.user_id into recipient from public.conversation_participants cp where cp.conversation_id=new.conversation_id and cp.user_id<>new.sender_id limit 1;
 if recipient is not null then insert into public.notifications(user_id,actor_id,type,text,sub_text,is_read) values(recipient,new.sender_id,'system','sent you a message',left(coalesce(new.content,''),160),false); end if;
 return new;
end; $$;
drop trigger if exists trg_notify_message_created on public.messages;
create trigger trg_notify_message_created after insert on public.messages for each row execute function public.notify_message_created();

do $$ begin
 if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='messages') then alter publication supabase_realtime add table public.messages; end if;
 if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='follows') then alter publication supabase_realtime add table public.follows; end if;
 if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='notifications') then alter publication supabase_realtime add table public.notifications; end if;
end $$;
commit;
