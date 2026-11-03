begin;

create or replace function public.award_save_points() returns trigger language plpgsql security definer set search_path=public,extensions as $$
begin perform public.award_points(new.user_id,'save_post',new.post_id); return new; end; $$;
drop trigger if exists trg_points_post_bookmark on public.post_bookmarks;
create trigger trg_points_post_bookmark after insert on public.post_bookmarks for each row execute function public.award_save_points();

create or replace function public.award_share_points() returns trigger language plpgsql security definer set search_path=public,extensions as $$
begin perform public.award_points(new.user_id,'share_post',new.post_id); return new; end; $$;
drop trigger if exists trg_points_post_share on public.post_shares;
create trigger trg_points_post_share after insert on public.post_shares for each row execute function public.award_share_points();

commit;
