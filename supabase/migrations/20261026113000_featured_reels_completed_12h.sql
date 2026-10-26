-- Five server-ranked public reels for the most recently completed 12-hour window.
-- The entire cohort switches at 00:00 and 12:00 UTC without a paid scheduler.
-- Use only server-recorded weighted view events; never trust client counts.
create index if not exists content_view_events_reel_window_idx
  on private.content_view_events(created_at desc, post_id)
  where content_type = 'reel';

create or replace function public.blink_featured_reels_12h()
returns table (
  reel_id uuid,
  author_id uuid,
  username text,
  display_name text,
  avatar_url text,
  reel_caption text,
  cover_url text,
  video_url text,
  window_views bigint,
  total_views integer,
  likes integer,
  comments integer,
  shares integer,
  window_start timestamptz,
  next_refresh timestamptz,
  verified boolean,
  verification_badge text,
  created_at timestamptz
)
language plpgsql stable security definer
set search_path = ''
as $$
declare
  v_viewer uuid := auth.uid();
  v_start timestamptz :=
    to_timestamp(floor(extract(epoch from now()) / 43200.0) * 43200.0);
begin
  if v_viewer is null then
    raise exception 'AUTHENTICATION_REQUIRED' using errcode = '42501';
  end if;

  return query
  with counters as (
    select e.post_id,
      sum(e.view_weight::bigint)::bigint as twelve_hour_views
    from private.content_view_events e
    where e.content_type = 'reel'
      and e.created_at >= v_start - interval '12 hours'
      and e.created_at < v_start
    group by e.post_id
  )
  select
    fp.id,
    fp.user_id,
    p.username::text,
    coalesce(nullif(p.full_name,''),p.username)::text,
    coalesce(p.avatar_url,'')::text,
    coalesce(nullif(fp.caption,''),fp.text,'')::text,
    coalesce(nullif(fp.image_url,''),fp.images[1],'')::text,
    fp.video_url::text,
    c.twelve_hour_views,
    coalesce(fp.view_count,0),
    coalesce(fp.like_count,0),
    coalesce(fp.comment_count,0),
    coalesce(fp.share_count,0),
    v_start - interval '12 hours',
    v_start + interval '12 hours',
    coalesce(p.is_verified,false),
    coalesce(p.verification_badge::text,'NONE')::text,
    fp.created_at
  from counters c
  join public.feed_posts fp on fp.id = c.post_id
  join public.profiles p on p.id = fp.user_id
  where fp.is_reel is true
    and fp.is_active is true
    and coalesce(fp.is_flagged,false) is false
    and fp.audience = 'Everyone'
    and fp.video_url is not null and length(trim(fp.video_url)) > 0
    and (fp.expires_at is null or fp.expires_at > now())
    and coalesce(p.onboarding_completed,false) is true
    and not exists (
      select 1 from public.user_settings settings
      where settings.user_id = fp.user_id and settings.private_account is true
    )
    and not exists (
      select 1 from public.blocks b
      where (b.blocker_id = v_viewer and b.blocked_id = fp.user_id)
         or (b.blocker_id = fp.user_id and b.blocked_id = v_viewer)
    )
  order by c.twelve_hour_views desc, fp.created_at desc, fp.id
  limit 5;
end;
$$;

revoke all on function public.blink_featured_reels_12h() from public, anon, authenticated;
grant execute on function public.blink_featured_reels_12h() to authenticated;
comment on function public.blink_featured_reels_12h() is
  'Top five eligible public reels by server-recorded weighted views in the last completed 12h UTC window. No scheduled job, no viewer IDs exposed.';
