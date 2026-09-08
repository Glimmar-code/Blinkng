-- Reel retention telemetry and ranking quality factor.
-- Adds real watch percentage, completion, rewatch and strong skip signals
-- without changing the public discovery-feed RPC signature.

alter table private_ranking.discovery_reel_metrics
  add column if not exists watch_samples bigint not null default 0 check (watch_samples >= 0),
  add column if not exists watch_percentage_sum numeric not null default 0 check (watch_percentage_sum >= 0),
  add column if not exists completed_count bigint not null default 0 check (completed_count >= 0),
  add column if not exists rewatch_count bigint not null default 0 check (rewatch_count >= 0),
  add column if not exists skip_count bigint not null default 0 check (skip_count >= 0);

create table if not exists private_ranking.reel_engagement_sessions (
  session_id uuid primary key,
  viewer_id uuid not null references auth.users(id) on delete cascade,
  post_id uuid not null references public.feed_posts(id) on delete cascade,
  watched_ms integer not null check (watched_ms >= 0),
  duration_ms integer not null check (duration_ms > 0),
  created_at timestamptz not null default now()
);

create index if not exists reel_engagement_sessions_viewer_post_created_idx
  on private_ranking.reel_engagement_sessions(viewer_id, post_id, created_at desc);

create or replace function private_ranking.reel_quality_factor(p_post_id uuid)
returns numeric
language sql
stable
set search_path = ''
as $$
with target as (
  select coalesce(fp.is_reel,false) as is_reel
  from public.feed_posts fp
  where fp.id = p_post_id
), metrics as (
  select
    greatest(coalesce(m.watch_samples,0),0)::numeric as samples,
    case when coalesce(m.watch_samples,0) > 0
      then least(1::numeric, greatest(0::numeric, coalesce(m.watch_percentage_sum,0) / m.watch_samples::numeric))
      else 0::numeric end as avg_watch_pct,
    case when coalesce(m.watch_samples,0) > 0
      then least(1::numeric, greatest(0::numeric, coalesce(m.completed_count,0)::numeric / m.watch_samples::numeric))
      else 0::numeric end as completion_rate,
    case when coalesce(m.watch_samples,0) > 0
      then least(1::numeric, greatest(0::numeric, coalesce(m.rewatch_count,0)::numeric / m.watch_samples::numeric))
      else 0::numeric end as rewatch_rate,
    case when coalesce(m.watch_samples,0) > 0
      then least(1::numeric, greatest(0::numeric, coalesce(m.skip_count,0)::numeric / m.watch_samples::numeric))
      else 0::numeric end as skip_rate
  from private_ranking.discovery_reel_metrics m
  where m.post_id = p_post_id
), calc as (
  select
    t.is_reel,
    coalesce(m.samples,0::numeric) as samples,
    coalesce(m.avg_watch_pct,0::numeric) as avg_watch_pct,
    coalesce(m.completion_rate,0::numeric) as completion_rate,
    coalesce(m.rewatch_rate,0::numeric) as rewatch_rate,
    coalesce(m.skip_rate,0::numeric) as skip_rate,
    least(1::numeric, coalesce(m.samples,0::numeric) / 12::numeric) as reliability
  from target t
  left join metrics m on true
)
select coalesce(
  case
    when not is_reel or samples <= 0 then 1::numeric
    else round(
      greatest(0.35::numeric, least(2.60::numeric,
        (1::numeric + ((0.80::numeric + 0.50::numeric * avg_watch_pct) - 1::numeric) * reliability)
        * (1::numeric + (0.60::numeric * completion_rate) * reliability)
        * (1::numeric + least(0.50::numeric, 1.25::numeric * rewatch_rate) * reliability)
        * (1::numeric + (greatest(0.25::numeric, 1::numeric - 0.85::numeric * skip_rate) - 1::numeric) * reliability)
      )), 6)
  end,
  1::numeric
)
from calc;
$$;

create or replace function private_ranking.active_blink_boost_factor(
  p_post_id uuid,
  p_at timestamptz default now()
)
returns numeric
language sql
stable
security definer
set search_path = ''
as $$
  select
    coalesce(max(b.multiplier),1)::numeric
    * public.creator_organic_distribution_factor(p_post_id,p_at)
    * private_ranking.reel_quality_factor(p_post_id)
  from public.blink_boosts b
  where b.content_id=p_post_id
    and b.status='ACTIVE'
    and b.starts_at<=p_at
    and b.ends_at>p_at;
$$;

create or replace function public.record_reel_engagement(
  p_post_id uuid,
  p_watched_ms integer,
  p_duration_ms integer,
  p_session_id uuid,
  p_completed boolean default false,
  p_rewatched boolean default false
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_viewer uuid := auth.uid();
  v_watch_ms integer;
  v_duration_ms integer;
  v_watch_pct numeric;
  v_completed boolean;
  v_rewatched boolean;
  v_skipped boolean;
  v_event_type text;
  v_inserted integer;
begin
  if v_viewer is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  if p_session_id is null then
    raise exception 'SESSION_ID_REQUIRED';
  end if;

  if p_duration_ms is null or p_duration_ms < 1000 or p_duration_ms > 600000 then
    raise exception 'INVALID_REEL_DURATION';
  end if;

  if not exists (
    select 1
    from public.feed_posts fp
    where fp.id = p_post_id
      and coalesce(fp.is_reel,false) = true
      and fp.is_active = true
      and fp.is_flagged = false
      and fp.user_id <> v_viewer
  ) then
    raise exception 'INVALID_REEL';
  end if;

  v_duration_ms := p_duration_ms;
  v_watch_ms := least(greatest(coalesce(p_watched_ms,0),0), v_duration_ms * 3);
  v_watch_pct := least(1::numeric, v_watch_ms::numeric / v_duration_ms::numeric);
  v_completed := coalesce(p_completed,false) or v_watch_ms >= round(v_duration_ms * 0.90)::integer;
  v_rewatched := coalesce(p_rewatched,false) or v_watch_ms >= round(v_duration_ms * 1.05)::integer;
  v_skipped := (not v_completed) and (v_watch_ms <= 2500 or v_watch_pct < 0.20::numeric);
  v_event_type := case when v_skipped then 'skip' else 'view' end;

  insert into private_ranking.reel_engagement_sessions(
    session_id, viewer_id, post_id, watched_ms, duration_ms
  ) values (
    p_session_id, v_viewer, p_post_id, v_watch_ms, v_duration_ms
  )
  on conflict (session_id) do nothing;

  get diagnostics v_inserted = row_count;
  if v_inserted = 0 then
    return;
  end if;

  insert into private_ranking.discovery_reel_metrics(
    post_id,
    completion_samples,
    completion_rate,
    watch_samples,
    watch_percentage_sum,
    completed_count,
    rewatch_count,
    skip_count,
    updated_at
  ) values (
    p_post_id,
    1,
    case when v_completed then 1::numeric else 0::numeric end,
    1,
    v_watch_pct,
    case when v_completed then 1 else 0 end,
    case when v_rewatched then 1 else 0 end,
    case when v_skipped then 1 else 0 end,
    now()
  )
  on conflict (post_id) do update set
    watch_samples = private_ranking.discovery_reel_metrics.watch_samples + 1,
    watch_percentage_sum = private_ranking.discovery_reel_metrics.watch_percentage_sum + excluded.watch_percentage_sum,
    completed_count = private_ranking.discovery_reel_metrics.completed_count + excluded.completed_count,
    rewatch_count = private_ranking.discovery_reel_metrics.rewatch_count + excluded.rewatch_count,
    skip_count = private_ranking.discovery_reel_metrics.skip_count + excluded.skip_count,
    completion_samples = private_ranking.discovery_reel_metrics.watch_samples + 1,
    completion_rate = (
      private_ranking.discovery_reel_metrics.completed_count + excluded.completed_count
    )::numeric / (private_ranking.discovery_reel_metrics.watch_samples + 1)::numeric,
    updated_at = now();

  -- Preserve the existing recommendation/interest route while avoiding the old
  -- completed=true-only metric hook; the dedicated metrics above are canonical.
  perform public.record_recommendation_event(
    'reels',
    'post',
    p_post_id::text,
    v_event_type,
    v_watch_ms,
    p_session_id,
    jsonb_build_object(
      'watch_percentage', v_watch_pct,
      'watched_ms', v_watch_ms,
      'duration_ms', v_duration_ms,
      'rewatched', v_rewatched,
      'skip', v_skipped
    )
  );
end;
$$;

revoke all on function public.record_reel_engagement(uuid,integer,integer,uuid,boolean,boolean) from public;
revoke all on function public.record_reel_engagement(uuid,integer,integer,uuid,boolean,boolean) from anon;
grant execute on function public.record_reel_engagement(uuid,integer,integer,uuid,boolean,boolean) to authenticated;
