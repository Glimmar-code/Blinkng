-- Keep post media mutually consistent: only reels may carry video media.
create or replace function public.normalize_feed_post_media()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if coalesce(new.type, '') <> 'reel' then
    new.video_url := null;
    new.is_reel := false;
  elsif nullif(trim(coalesce(new.video_url, '')), '') is null then
    new.type := case when coalesce(array_length(new.images, 1), 0) > 0 then 'photo' else 'text' end;
    new.is_reel := false;
    new.video_url := null;
  else
    new.type := 'reel';
    new.is_reel := true;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_normalize_feed_post_media on public.feed_posts;
create trigger trg_normalize_feed_post_media
before insert or update of type, video_url, is_reel on public.feed_posts
for each row execute function public.normalize_feed_post_media();

update public.feed_posts
set video_url = null, is_reel = false
where coalesce(type, '') <> 'reel' and (video_url is not null or is_reel is true);

-- Normal viewer = 1 weighted view; BLUE = 3; GOLD = 7.
create or replace function public.record_post_view(p_post_id uuid)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_weight integer := 1;
  v_new_views integer;
  v_badge text;
begin
  if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;

  select upper(coalesce(verification_badge::text, '')) into v_badge
  from public.profiles where id = auth.uid();

  v_weight := case
    when v_badge = 'GOLD' then 7
    when v_badge = 'BLUE' then 3
    else 1
  end;

  if not exists(select 1 from public.feed_posts where id = p_post_id and is_active = true) then
    return 0;
  end if;

  insert into public.post_views(post_id, viewer_id, view_weight)
  values(p_post_id, auth.uid(), v_weight);

  update public.feed_posts
  set view_count = coalesce(view_count,0) + v_weight, updated_at = now()
  where id = p_post_id
  returning view_count into v_new_views;

  begin
    perform public.award_points(auth.uid(), 'view_post', p_post_id);
  exception when undefined_function then null;
  end;

  return coalesce(v_new_views,0);
end;
$$;

create or replace function public.record_post_view(p_post_id uuid, p_viewer_username text)
returns integer
language plpgsql
security definer
set search_path = public
as $$
begin
  return public.record_post_view(p_post_id);
end;
$$;
