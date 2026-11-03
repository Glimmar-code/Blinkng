begin;

alter table public.profiles add column if not exists points integer not null default 0;
create index if not exists idx_profiles_points_desc on public.profiles(points desc, created_at asc);

create or replace function public.award_points(p_user_id uuid,p_action_type text,p_reference_id uuid default null)
returns integer language plpgsql security definer set search_path=public,extensions as $$
declare v_delta integer; v_new_points integer;
begin
 v_delta:=case p_action_type when 'view_post' then 1 when 'like_post' then 1 when 'comment' then 2 when 'save_post' then 2 when 'share_post' then 2 when 'like_comment' then 2 when 'reply_comment' then 2 when 'follow_user' then 3 when 'like_status' then 3 when 'create_status' then 5 when 'message_user' then 5 when 'reply_status' then 5 when 'create_post' then 15 else 0 end;
 if v_delta=0 then raise exception 'Unknown action_type: %',p_action_type; end if;
 update public.profiles set points=coalesce(points,0)+v_delta,updated_at=now() where id=p_user_id returning points into v_new_points;
 insert into public.point_transactions(user_id,action_type,points_delta,reference_id) values(p_user_id,p_action_type,v_delta,p_reference_id);
 return coalesce(v_new_points,0);
end; $$;

create or replace view public.game_leaderboard with (security_invoker=true) as
select p.id as user_id,coalesce(p.points,0)::bigint as score,coalesce(gp.coins,0)::bigint as coins,coalesce(p.daily_streak,0)::integer as streak,coalesce(gp.best_streak,0)::integer as best_streak,p.name,p.username,p.avatar_url,p.university,rank() over(order by coalesce(p.points,0) desc,p.created_at asc) as world_rank
from public.profiles p left join public.game_profiles gp on gp.user_id=p.id
order by coalesce(p.points,0) desc,p.created_at asc;

create or replace function public.award_interaction_points() returns trigger language plpgsql security definer set search_path=public,extensions as $$
begin
 if tg_table_name='feed_posts' then perform public.award_points(new.user_id,'create_post',new.id);
 elsif tg_table_name='post_likes' then perform public.award_points(new.user_id,'like_post',new.post_id);
 elsif tg_table_name='comments' then perform public.award_points(new.user_id,'comment',new.post_id);
 elsif tg_table_name='comment_likes' then perform public.award_points(new.user_id,'like_comment',new.comment_id);
 elsif tg_table_name='follows' then perform public.award_points(new.follower_id,'follow_user',new.following_id);
 end if; return new; end; $$;

drop trigger if exists trg_points_create_post on public.feed_posts; create trigger trg_points_create_post after insert on public.feed_posts for each row execute function public.award_interaction_points();
drop trigger if exists trg_points_post_like on public.post_likes; create trigger trg_points_post_like after insert on public.post_likes for each row execute function public.award_interaction_points();
drop trigger if exists trg_points_comment on public.comments; create trigger trg_points_comment after insert on public.comments for each row execute function public.award_interaction_points();
drop trigger if exists trg_points_follow on public.follows; create trigger trg_points_follow after insert on public.follows for each row execute function public.award_interaction_points();

drop trigger if exists trg_points_message on public.messages;
create trigger trg_points_message after insert on public.messages for each row execute function public.award_message_points();

commit;
