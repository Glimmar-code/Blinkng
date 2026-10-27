-- destructive-change-reviewed
-- rollback-plan: Restore the backed-up pg_get_functiondef definitions of
-- get_discovery_feed, get_feed_page, record_content_view and
-- complete_blink_rewarded_ad_claim in a separate reviewed staging rollback;
-- revert AdMob Edge worker, and remove the new service-only settlement function
-- after checking for pending verified callbacks. Do not touch coin ledger rows.
-- Algorithm integrity repair, stage-only until the database and both clients pass validation.
-- Does not alter wallet amounts, HP, verification prices, or existing balances.
-- All source rewrites abort on unexpected function definitions instead of silently diverging.
begin;
set local lock_timeout = '5s';
set local statement_timeout = '90s';

-- A privileged account must not bypass a viewer's blocks/mutes/Following choices,
-- nor get an effectively infinite discovery score.
do $algorithm$
declare
  v_discovery text := pg_get_functiondef('private_ranking.get_discovery_feed(integer,integer,timestamp with time zone,numeric,numeric,numeric,numeric,numeric)'::regprocedure);
  v_feed text := pg_get_functiondef('public.get_feed_page(integer,timestamptz,uuid,text,uuid)'::regprocedure);
  v_owner_bonus text := 'case when private.is_blink_owner_id(c.creator_id) then 1000000000::numeric else 0::numeric end';
  v_privacy_anchor text := 'where fp.is_active=true';
  v_reel_factor text := '(case when c.is_reel and c.reel_watch_samples >= 12 then least(1.15::numeric,greatest(0.90::numeric,0.90::numeric + 0.25::numeric * c.completion_rate)) else 1::numeric end)';
begin
  if v_discovery is null or position(v_owner_bonus in v_discovery)=0
     or position(v_privacy_anchor in v_discovery)=0 then
    raise exception 'ALGORITHM_DISCOVERY_SOURCE_DRIFT';
  end if;
  v_discovery := replace(v_discovery, v_owner_bonus, '0::numeric');
  v_discovery := replace(
    v_discovery,
    v_privacy_anchor,
    v_privacy_anchor || E'\n      and (fp.user_id=v_user or not exists (select 1 from public.user_settings privacy where privacy.user_id=fp.user_id and privacy.private_account is true))'
  );

  -- Completion is scored only after 12 received samples, with a bounded 0.90..1.15
  -- multiplier. Keep the metadata exactly aligned with the actual ranking factor.
  if position('completion_rate numeric not null,' in v_discovery)=0
     or position('coalesce(rm.completion_rate,0)::numeric as completion_rate,' in v_discovery)=0
     or position('feed_score,completion_rate,' in v_discovery)=0
     or position('s.final_score,s.completion_rate,' in v_discovery)=0
     or position('* c.verification_multiplier * c.cold_start_multiplier' in v_discovery)=0
     or position('''reel_completion_boost'',1,' in v_discovery)=0 then
    raise exception 'REEL_COMPLETION_SCORE_SOURCE_DRIFT';
  end if;
  v_discovery := replace(v_discovery,'completion_rate numeric not null,',
    'completion_rate numeric not null, reel_watch_samples numeric not null,');
  v_discovery := replace(v_discovery,
    'coalesce(rm.completion_rate,0)::numeric as completion_rate,',
    'coalesce(rm.completion_rate,0)::numeric as completion_rate, coalesce(rm.watch_samples,0)::numeric as reel_watch_samples,');
  v_discovery := replace(v_discovery,'feed_score,completion_rate,',
    'feed_score,completion_rate,reel_watch_samples,');
  v_discovery := replace(v_discovery,'s.final_score,s.completion_rate,',
    's.final_score,s.completion_rate,s.reel_watch_samples,');
  v_discovery := replace(v_discovery,
    '* c.verification_multiplier * c.cold_start_multiplier',
    '* '||v_reel_factor||' * c.verification_multiplier * c.cold_start_multiplier');
  v_discovery := replace(v_discovery,'''reel_completion_boost'',1,',
    '''reel_completion_boost'','||v_reel_factor||',');
  execute v_discovery;

  if v_feed is null or position('private.is_blink_owner_id(fp.user_id)' in v_feed)=0 then
    raise exception 'ALGORITHM_FEED_SOURCE_DRIFT';
  end if;
  v_feed := replace(v_feed, 'private.is_blink_owner_id(fp.user_id)', 'false');
  execute v_feed;
end;
$algorithm$;

-- Existing public view calls can no longer increment repeatedly within a minute.
-- A UUID alone is not proof of a distinct human impression.
do $views$
declare
  v_source text := pg_get_functiondef('public.record_content_view(uuid,uuid)'::regprocedure);
  v_anchor text := 'v_user_view_count := coalesce(v_user_view_count, 0);';
  v_eligibility_anchor text := 'v_content_type := case when v_is_reel then ''reel'' else ''post'' end;';
begin
  if v_source is null or position(v_anchor in v_source)=0
     or position('v_user_contribution' in v_source)=0 then
    raise exception 'CONTENT_VIEW_SOURCE_DRIFT';
  end if;
  if position(v_eligibility_anchor in v_source)=0 then
    raise exception 'CONTENT_VIEW_VISIBILITY_SOURCE_DRIFT';
  end if;
  v_source := replace(v_source,v_eligibility_anchor,$visibility$
    -- Enforce the same visibility constraints used when serving a Feed item.
    -- SECURITY DEFINER must not count hidden or private creator content.
    if not exists (
      select 1 from public.feed_posts allowed
      where allowed.id=p_post_id
        and allowed.is_active=true
        and coalesce(allowed.is_flagged,false)=false
        and (allowed.expires_at is null or allowed.expires_at>now())
        and (
          lower(coalesce(allowed.audience,'everyone'))='everyone'
          or allowed.user_id=v_viewer_id
          or (lower(coalesce(allowed.audience,''))='followers' and exists (
            select 1 from public.follows audience_follow
            where audience_follow.follower_id=v_viewer_id
              and audience_follow.following_id=allowed.user_id
          ))
        )
        and (allowed.user_id=v_viewer_id or not exists (
          select 1 from public.user_settings privacy
          where privacy.user_id=allowed.user_id and privacy.private_account=true
        ))
        and not exists (
          select 1 from public.blocks deny
          where (deny.blocker_id=v_viewer_id and deny.blocked_id=allowed.user_id)
             or (deny.blocked_id=v_viewer_id and deny.blocker_id=allowed.user_id)
        )
        and not exists (
          select 1 from public.muted_users hidden
          where hidden.user_id=v_viewer_id and hidden.muted_id=allowed.user_id
        )
    ) then
      raise exception 'CONTENT_NOT_VISIBLE';
    end if;
    v_content_type := case when v_is_reel then 'reel' else 'post' end;
$visibility$);
  if position('ALGORITHM_VIEW_REPLAY_COOLDOWN' in v_source)=0 then
    v_source := replace(v_source,v_anchor,v_anchor || $block$

    -- ALGORITHM_VIEW_REPLAY_COOLDOWN: suppress rapid repeats; do not count failed retries.
    if v_user_view_count > 0 and exists (
      select 1 from public.post_views recent
      where recent.post_id=p_post_id and recent.viewer_id=v_viewer_id
        and recent.last_viewed_at > now() - interval '60 seconds'
    ) then
      return jsonb_build_object(
        'accepted',false,'duplicate',false,'cap_reached',false,
        'view_count',v_view_count,'user_view_count',v_user_view_count,
        'user_contribution',v_user_contribution,
        'max_contribution',v_max_contribution,'view_weight',v_view_weight,
        'content_type',v_content_type,'event_id',p_event_id
      );
    end if;
$block$);
    execute v_source;
  end if;
end;
$views$;

-- Re-install Reel retention telemetry: the Android RPC exists in source but was
-- missing from the connected live schema. These tables are server-owned.
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
alter table private_ranking.reel_engagement_sessions enable row level security;
revoke all on private_ranking.reel_engagement_sessions from public,anon,authenticated;

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
      and lower(coalesce(fp.audience,'everyone'))='everyone'
      and (fp.expires_at is null or fp.expires_at>now())
      and not exists (select 1 from public.user_settings settings
        where settings.user_id=fp.user_id and settings.private_account=true)
      and not exists (select 1 from public.blocks blocked
        where (blocked.blocker_id=v_viewer and blocked.blocked_id=fp.user_id)
           or (blocked.blocker_id=fp.user_id and blocked.blocked_id=v_viewer))
      and not exists (select 1 from public.muted_users muted
        where muted.user_id=v_viewer and muted.muted_id=fp.user_id)
  ) then
    raise exception 'INVALID_REEL';
  end if;

  v_duration_ms := p_duration_ms;
  v_watch_ms := least(greatest(coalesce(p_watched_ms,0),0), v_duration_ms * 3);
  v_watch_pct := least(1::numeric, v_watch_ms::numeric / v_duration_ms::numeric);
  v_completed := v_watch_ms >= round(v_duration_ms * 0.90)::integer;
  v_rewatched := v_watch_ms >= round(v_duration_ms * 1.05)::integer;
  v_skipped := (not v_completed) and (v_watch_ms <= 2500 or v_watch_pct < 0.20::numeric);
  v_event_type := case when v_skipped then 'skip' else 'view' end;

  -- Repeated sessions within a brief interval are telemetry, not independent voters.
  -- This is a best-effort cooldown in addition to the session_id idempotency key.
  if exists (
    select 1 from private_ranking.reel_engagement_sessions existing
    where existing.viewer_id=v_viewer and existing.post_id=p_post_id
      and existing.created_at > now()-interval '20 seconds'
  ) then return; end if;

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


-- Reward coins must require the cryptographically verified Google SSV callback.
-- Existing credited claims remain idempotent. The SSV worker settles newly verified
-- claims even if the user left the app; Android's existing retry handling recognizes
-- REWARDED_AD_VERIFICATION_PENDING.
do $rewards$
declare
  v_source text := pg_get_functiondef('public.complete_blink_rewarded_ad_claim(uuid)'::regprocedure);
  v_anchor text := 'if v_claim.started_at < now()-interval ''30 minutes''';
  v_ad_cap_anchor text := 'if v_ads_before >= v_limit then raise exception ''REWARDED_AD_DAILY_LIMIT''; end if;';
  v_old_user text := 'v_user uuid := auth.uid();';
  v_service text;
begin
  if v_source is null or position(v_anchor in v_source)=0
    or position(v_old_user in v_source)=0
    or position('ssv_verified_at' in v_source)>0 then
    raise exception 'REWARDED_AD_SOURCE_DRIFT';
  end if;
  v_source := replace(v_source,v_anchor,
    'if v_claim.ssv_verified_at is null then raise exception ''REWARDED_AD_VERIFICATION_PENDING''; end if;' || E'\n  ' || v_anchor
  );
  if position(v_ad_cap_anchor in v_source)=0 then
    raise exception 'REWARDED_AD_VERIFIED_LIMIT_SOURCE_DRIFT';
  end if;
  -- Android already restricts ads 11-15 to actively verified members.
  -- Enforce the identical entitlement inside the authoritative coin RPC.
  v_source := replace(v_source,v_ad_cap_anchor,$verified$
if v_ads_before >= (case when exists (
      select 1 from public.profiles verified
      where verified.id=v_user
        and upper(coalesce(verified.verification_badge,'')) in ('BLUE','GOLD')
        and (verified.verification_expires_at > now()
             or (upper(verified.verification_badge)='GOLD' and verified.verification_expires_at is null))
    ) then v_limit else least(v_limit,10) end)
  then raise exception 'REWARDED_AD_DAILY_LIMIT'; end if;
$verified$);
  execute v_source;

  -- Service-role-only sibling: identical transaction, cap, ledger and milestone
  -- rules; identity is looked up only from the already verified claim.
  v_service := replace(v_source,
    'public.complete_blink_rewarded_ad_claim', 'public.settle_blink_rewarded_ad_verified');
  v_service := replace(v_service,v_old_user,
    'v_user uuid := coalesce(auth.uid(), (select c.user_id from public.blink_rewarded_ad_claims c where c.id=p_claim_id and c.ssv_verified_at is not null));');
  if v_service=v_source or position(v_old_user in v_service)>0 then
    raise exception 'REWARDED_AD_SETTLEMENT_REWRITE_FAILED';
  end if;
  execute v_service;
end;
$rewards$;
revoke all on function public.settle_blink_rewarded_ad_verified(uuid) from public,anon,authenticated;
grant execute on function public.settle_blink_rewarded_ad_verified(uuid) to service_role;
revoke all on function public.complete_blink_rewarded_ad_claim(uuid) from public,anon;
grant execute on function public.complete_blink_rewarded_ad_claim(uuid) to authenticated;
commit;
