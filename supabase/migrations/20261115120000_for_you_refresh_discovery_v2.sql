-- BLINK For You refresh, cursor pagination, discovery tiers and telemetry.
-- Additive migration: keeps the legacy offset RPC available for older app versions.
begin;

create index if not exists feed_posts_for_you_eligible_created_idx
  on public.feed_posts (created_at desc, id desc)
  where is_active = true and is_flagged = false;

create index if not exists feed_posts_for_you_creator_idx
  on public.feed_posts (user_id, created_at desc, id desc)
  where is_active = true and is_flagged = false;

create index if not exists profiles_for_you_campus_idx
  on public.profiles (university, faculty, department);

create index if not exists user_interest_weights_for_you_lookup_idx
  on public.user_interest_weights (user_id, surface, feature_type, feature_value);

create index if not exists post_views_viewer_recent_post_idx
  on public.post_views (viewer_id, last_viewed_at desc, post_id);

create index if not exists post_views_post_recent_viewer_idx
  on public.post_views (post_id, last_viewed_at desc, viewer_id);

create index if not exists recommendation_events_post_impression_idx
  on public.recommendation_events (target_key, created_at desc)
  where target_type = 'post' and event_type = 'impression';

create index if not exists recommendation_events_user_surface_recent_idx
  on public.recommendation_events (user_id, surface, created_at desc);

create index if not exists recommendation_events_viewer_post_impression_idx
  on public.recommendation_events (user_id, target_key, created_at desc)
  where target_type = 'post' and event_type = 'impression';

create index if not exists follows_follower_following_discovery_idx
  on public.follows (follower_id, following_id);

create index if not exists feed_preferences_user_post_preference_idx
  on public.feed_preferences (user_id, post_id, preference);

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

create index if not exists reel_engagement_sessions_viewer_post_recent_idx
  on private_ranking.reel_engagement_sessions (viewer_id, post_id, created_at desc);

update private_ranking.discovery_reel_metrics
   set watch_samples = completion_samples,
       completed_count = round(completion_rate * completion_samples)::bigint,
       watch_percentage_sum = completion_rate * completion_samples
 where watch_samples = 0 and completion_samples > 0;

alter table private_ranking.reel_engagement_sessions enable row level security;
revoke all on private_ranking.reel_engagement_sessions from public, anon, authenticated;

-- Preserve legacy aggregate views that predate per-viewer event rows. The
-- baseline is the old counter minus already-recorded weighted view rows.
create table if not exists private_ranking.feed_view_count_baselines (
  post_id uuid primary key references public.feed_posts(id) on delete cascade,
  baseline_view_count bigint not null default 0 check (baseline_view_count >= 0),
  captured_at timestamptz not null default now()
);
alter table private_ranking.feed_view_count_baselines enable row level security;
revoke all on private_ranking.feed_view_count_baselines from public,anon,authenticated;

insert into private_ranking.feed_view_count_baselines(post_id,baseline_view_count)
select fp.id,
       greatest(0::bigint,coalesce(fp.view_count,0)::bigint-coalesce(sum(pv.weighted_view_count),0)::bigint)
  from public.feed_posts fp
  left join public.post_views pv on pv.post_id=fp.id
 group by fp.id,fp.view_count
on conflict(post_id) do nothing;

-- A view event is authoritative only once per accepted event UUID. Keep the
-- aggregate counter equal to its legacy baseline plus canonical per-viewer rows.
create or replace function private_ranking.sync_feed_post_view_count()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_post_id uuid := coalesce(new.post_id, old.post_id);
  v_total bigint;
  v_baseline bigint;
begin
  select coalesce(sum(pv.weighted_view_count), 0)::bigint
    into v_total
    from public.post_views pv
   where pv.post_id = v_post_id;
  select coalesce(b.baseline_view_count,0)
    into v_baseline
    from private_ranking.feed_view_count_baselines b
   where b.post_id=v_post_id;
  v_baseline := coalesce(v_baseline,0);

  update public.feed_posts
     set view_count = least(2147483647::bigint, greatest(0::bigint, v_total+v_baseline))::integer,
         updated_at = now()
   where id = v_post_id;

  if tg_op = 'UPDATE' and old.post_id is distinct from new.post_id then
    select coalesce(sum(pv.weighted_view_count), 0)::bigint
      into v_total
      from public.post_views pv
     where pv.post_id = old.post_id;
    select coalesce(b.baseline_view_count,0)
      into v_baseline
      from private_ranking.feed_view_count_baselines b
     where b.post_id=old.post_id;
    v_baseline := coalesce(v_baseline,0);
    update public.feed_posts
       set view_count = least(2147483647::bigint, greatest(0::bigint, v_total+v_baseline))::integer,
           updated_at = now()
     where id = old.post_id;
  end if;

  if tg_op = 'DELETE' then return old; end if;
  return new;
end;
$$;

drop trigger if exists trg_sync_feed_post_view_count on public.post_views;
create trigger trg_sync_feed_post_view_count
after insert or update or delete on public.post_views
for each row execute function private_ranking.sync_feed_post_view_count();

update public.feed_posts fp
   set view_count = least(2147483647::bigint,
       greatest(0::bigint, coalesce(totals.weighted_views,0)+coalesce(baseline.baseline_view_count,0)))::integer
  from (
    select fp2.id as post_id, coalesce(sum(pv.weighted_view_count),0)::bigint as weighted_views
      from public.feed_posts fp2
      left join public.post_views pv on pv.post_id=fp2.id
     group by fp2.id
  ) totals
  left join private_ranking.feed_view_count_baselines baseline on baseline.post_id=totals.post_id
 where totals.post_id=fp.id;

-- Preserve the existing anti-replay/cap checks in record_content_view. Only
-- replace its final arithmetic increment with the canonical aggregate read.
do $$
declare
  v_source text := pg_get_functiondef('public.record_content_view(uuid,uuid)'::regprocedure);
  v_before text := $old$
    update public.feed_posts
       set view_count = coalesce(view_count, 0) + v_view_weight,
           updated_at = now()
     where id = p_post_id
    returning view_count into v_view_count;
$old$;
  v_after text := $new$
    select least(2147483647::bigint,
        coalesce((select sum(pv.weighted_view_count) from public.post_views pv where pv.post_id=p_post_id),0)
        +coalesce((select baseline_view_count from private_ranking.feed_view_count_baselines b where b.post_id=p_post_id),0)
      )::integer
      into v_view_count;
    update public.feed_posts
       set view_count = least(2147483647::bigint, greatest(0::bigint, v_view_count::bigint))::integer,
           updated_at = now()
     where id = p_post_id;
$new$;
  v_anchor text := '    v_user_view_count := v_new_user_view_count;';
begin
  if v_source is null or position(v_before in v_source) = 0 then
    raise exception 'FOR_YOU_VIEW_COUNTER_SOURCE_DRIFT';
  end if;
  v_source := replace(v_source, v_before, v_after);
  if position('perform private_ranking.record_recommendation_event' in v_source) = 0 then
    if position(v_anchor in v_source) = 0 then
      raise exception 'FOR_YOU_VIEW_IMPRESSION_ANCHOR_DRIFT';
    end if;
    v_source := replace(v_source, v_anchor, v_anchor || E'\n\n    -- Accepted qualified views also establish a recommendation impression.\n    begin\n      perform private_ranking.record_recommendation_event(\n        case when v_is_reel then ''reels'' else ''feed'' end,\n        ''post'', p_post_id::text, ''impression'', 0, p_event_id,\n        jsonb_build_object(''source'',''qualified_view'',''model'',''weighted_decay_v1'')\n      );\n    exception when others then\n      null;\n    end;');
  end if;
  execute v_source;
end;
$$;

-- Record impressions for a delivered page on all clients. The function checks
-- visibility again; a client cannot manufacture exposure for hidden content.
create or replace function public.record_feed_impressions(
  p_surface text,
  p_post_ids uuid[],
  p_session_id uuid default null
)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_surface text := lower(btrim(coalesce(p_surface, 'feed')));
  v_post_id uuid;
  v_count integer := 0;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if v_surface not in ('feed','reels') then raise exception 'INVALID_RECOMMENDATION_SURFACE'; end if;
  if coalesce(cardinality(p_post_ids),0) > 60 then raise exception 'TOO_MANY_IMPRESSION_TARGETS'; end if;

  foreach v_post_id in array coalesce(p_post_ids, array[]::uuid[]) loop
    if exists (
      select 1
        from public.feed_posts fp
        join public.profiles author on author.id = fp.user_id
       where fp.id = v_post_id
         and fp.is_active = true
         and fp.is_flagged = false
         and (fp.expires_at is null or fp.expires_at > now())
         and (v_surface <> 'reels' or fp.is_reel = true)
         and (v_surface <> 'feed' or fp.is_reel = false)
         and (
           fp.user_id = v_user
           or lower(coalesce(fp.audience,'everyone')) in ('everyone','public')
           or (lower(coalesce(fp.audience,'')) in ('followers','following') and exists (
             select 1 from public.follows f
              where f.follower_id = v_user and f.following_id = fp.user_id
           ))
           or (lower(coalesce(fp.audience,'')) = 'university'
               and exists (select 1 from public.profiles me where me.id=v_user
                 and nullif(lower(me.university),'')=nullif(lower(author.university),'')))
           or (lower(coalesce(fp.audience,'')) = 'faculty'
               and exists (select 1 from public.profiles me where me.id=v_user
                 and nullif(lower(me.faculty),'')=nullif(lower(author.faculty),'')))
         )
         and (
           fp.user_id = v_user
           or not exists (select 1 from public.user_settings s
             where s.user_id=fp.user_id and s.private_account=true)
           or exists (select 1 from public.follows f
             where f.follower_id=v_user and f.following_id=fp.user_id)
         )
         and not exists (select 1 from public.blocks b
           where (b.blocker_id=v_user and b.blocked_id=fp.user_id)
              or (b.blocker_id=fp.user_id and b.blocked_id=v_user))
         and not exists (select 1 from public.muted_users m
           where m.user_id=v_user and m.muted_id=fp.user_id)
         and not exists (select 1 from public.feed_preferences pref
           where pref.user_id=v_user and pref.post_id=fp.id
             and lower(pref.preference::text) in ('hide','hidden','not_interested','not interested'))
    ) then
      begin
        perform private_ranking.record_recommendation_event(
          v_surface, 'post', v_post_id::text, 'impression', 0, p_session_id,
          jsonb_build_object('source','for_you_page','model','weighted_decay_v1')
        );
        v_count := v_count + 1;
      exception when others then
        null;
      end;
    end if;
  end loop;

  return v_count;
end;
$$;

revoke all on function public.record_feed_impressions(text,uuid[],uuid) from public, anon;
grant execute on function public.record_feed_impressions(text,uuid[],uuid) to authenticated;

-- Cursor-based weighted_decay_v1. The old offset overload remains available
-- only for clients that have not yet upgraded.
create or replace function private_ranking.get_discovery_feed(
  p_limit integer,
  p_cursor_score numeric,
  p_cursor_post_id uuid,
  p_as_of timestamptz,
  p_request_seed uuid,
  p_surface text
)
returns table(
  item jsonb,
  ranking_score numeric,
  ranking_reason text,
  ranking_components jsonb,
  post_id uuid,
  next_cursor_score text,
  next_cursor_post_id uuid,
  as_of timestamptz,
  request_seed uuid
)
language sql
volatile
security definer
set search_path = ''
as $$
with cfg as (
  select
    auth.uid() as viewer_id,
    least(60,greatest(1,coalesce(p_limit,20))) as page_limit,
    least(coalesce(p_as_of,now()),now()+interval '1 minute') as rank_as_of,
    coalesce(p_request_seed,gen_random_uuid()) as seed,
    case lower(btrim(coalesce(p_surface,'feed')))
      when 'posts' then 'posts'
      when 'reels' then 'reels'
      when 'all' then 'all'
      when 'following' then 'following'
      else 'feed'
    end as surface
),
me as (
  select p.*
    from public.profiles p
    join cfg on p.id=cfg.viewer_id
),
viewer_exposure as (
  select
    e.target_key::uuid as post_id,
    count(*)::numeric as exposure_count,
    max(e.created_at) as last_exposed_at
  from public.recommendation_events e
  join cfg on cfg.viewer_id=e.user_id
  where e.target_type='post'
    and e.event_type='impression'
    and e.target_key ~* '^[0-9a-f-]{36}
global_exposure as (
  select
    e.target_key::uuid as post_id,
    count(*) filter (where e.event_type='impression')::numeric as impressions
  from public.recommendation_events e
  where e.target_type='post'
    and e.created_at >= coalesce(p_as_of,now()) - interval '30 days'
    and e.target_key ~* '^[0-9a-f-]{36}$'
  group by e.target_key::uuid
),
raw_candidates as (
  select
    fp.id,
    fp.user_id,
    greatest(fp.created_at,coalesce(rr.created_at,fp.created_at)) as created_at,
    fp.is_reel,
    fp.audience,
    greatest(
      coalesce(fp.like_count,0)-(
        select count(*)::integer from public.blink_drop_incentivized_interactions di
         where di.target_id=fp.id and di.action='LIKE'
      ),0
    ) as likes,
    greatest(
      coalesce(fp.comment_count,0)-(
        select count(*)::integer from public.blink_drop_incentivized_interactions di
         where di.target_id=fp.id and di.action='COMMENT'
      ),0
    ) as comments,
    greatest(coalesce(fp.share_count,0),0) as shares,
    greatest(
      coalesce(fp.repost_count,0)-(
        select count(*)::integer from public.blink_drop_incentivized_interactions di
         where di.target_id=fp.id and di.action='REPOST'
      ),0
    ) as reposts,
    greatest(coalesce(fp.view_count,0),0) as views,
    greatest(coalesce(fp.report_count,0),0) as report_count,
    fp.is_original,
    fp.category,
    fp.faculty,
    fp.type,
    fp.tags,
    fp.hashtags,
    fp.text,
    fp.caption,
    fp.image_url,
    fp.video_url,
    fp.images,
    fp.link_url,
    fp.creator_post_number,
    ap.university as author_university,
    ap.faculty as author_faculty,
    ap.department as author_department,
    ap.created_at as creator_created_at,
    greatest(coalesce(ap.daily_streak,0),0) as creator_streak,
    ap.follower_count as creator_followers,
    ap.verification_badge,
    coalesce(vm.multiplier,1)::numeric as verification_multiplier,
    coalesce(rm.completion_rate,0)::numeric as reel_completion_rate,
    coalesce(rm.completion_samples,0)::numeric as reel_completion_samples,
    coalesce(rm.completed_count,0)::numeric as reel_completed_count,
    coalesce(s.viewer_views,0)::numeric as viewer_views,
    s.last_viewed_at,
    coalesce(ex.impressions,0)::numeric as impressions,
    exists (
      select 1 from public.follows f
       where f.follower_id=cfg.viewer_id and f.following_id=fp.user_id
    ) as is_followed,
    (rr.id is not null) as has_followed_repost,
    rr.id as repost_id,
    rr.user_id as reposted_by_id,
    rr.username as reposted_by_username,
    array(
      select distinct lower(btrim(label_value))
        from unnest(
          coalesce(fp.tags,'{}'::text[])
          || coalesce(fp.hashtags,'{}'::text[])
          || array[coalesce(fp.category,'')]
        ) label_value
       where nullif(btrim(label_value),'') is not null
    ) as labels,
    coalesce(ic.positive_interests,'{}'::text[]) as positive_interests,
    coalesce(ic.negative_interests,'{}'::text[]) as negative_interests,
    coalesce(ic.positive_creators,'{}'::uuid[]) as positive_creators,
    coalesce(ic.negative_creators,'{}'::uuid[]) as negative_creators,
    coalesce(ic.creator_weights,'{}'::jsonb) as creator_weights,
    coalesce(private_ranking.active_blink_boost_factor(fp.id,cfg.rank_as_of),1.0)::numeric as active_boost_factor,
    coalesce(private_ranking.viewer_personalization_bonus(cfg.viewer_id,fp.id,fp.user_id,cfg.rank_as_of),0)::numeric as personalization_bonus,
    case
      when vg.location is not null and cg.location is not null
        then extensions.st_distance(vg.location,cg.location)
      else null
    end as distance_meters,
    (
      nullif(lower(me.university),'') is not null
      and nullif(lower(me.university),'')=nullif(lower(ap.university),'')
    ) as same_university,
    (
      nullif(lower(me.faculty),'') is not null
      and nullif(lower(me.faculty),'')=nullif(lower(ap.faculty),'')
    ) as same_faculty,
    (
      nullif(lower(me.department),'') is not null
      and nullif(lower(me.department),'')=nullif(lower(ap.department),'')
    ) as same_department,
    cfg.rank_as_of,
    cfg.seed,
    cfg.surface,
    cfg.viewer_id
  from public.feed_posts fp
  join public.profiles ap on ap.id=fp.user_id
  cross join cfg
  cross join me
  left join private_ranking.discovery_user_geo vg on vg.user_id=cfg.viewer_id
  left join private_ranking.discovery_user_geo cg on cg.user_id=fp.user_id
  left join private_ranking.discovery_interest_cache ic on ic.user_id=cfg.viewer_id
  left join private_ranking.discovery_verification_multipliers vm
    on vm.tier = case upper(coalesce(ap.verification_badge,'NONE'))
      when 'GOLD' then 'gold'::public.discovery_verification_tier_enum
      when 'BLUE' then 'blue'::public.discovery_verification_tier_enum
      else 'standard'::public.discovery_verification_tier_enum
    end
  left join seen s on s.post_id=fp.id
  left join global_exposure ex on ex.post_id=fp.id
  left join private_ranking.discovery_reel_metrics rm on rm.post_id=fp.id
  left join lateral (
    select r.id,r.user_id,rp.username,r.created_at
      from public.post_reposts r
      join public.profiles rp on rp.id=r.user_id
     where r.post_id=fp.id
       and r.user_id<>cfg.viewer_id
       and not exists (
         select 1 from public.blink_drop_incentivized_interactions di
          where di.target_id=fp.id and di.action='REPOST' and di.user_id=r.user_id
       )
       and exists (
         select 1 from public.follows f
          where f.follower_id=cfg.viewer_id and f.following_id=r.user_id
       )
       and not exists (
         select 1 from public.blocks b
          where (b.blocker_id=cfg.viewer_id and b.blocked_id=r.user_id)
             or (b.blocker_id=r.user_id and b.blocked_id=cfg.viewer_id)
       )
       and not exists (
         select 1 from public.muted_users mu
          where mu.user_id=cfg.viewer_id and mu.muted_id=r.user_id
       )
     order by r.created_at desc
     limit 1
  ) rr on true
  where cfg.viewer_id is not null
    and fp.is_active=true
    and coalesce(fp.is_flagged,false)=false
    and fp.created_at<=cfg.rank_as_of
    and (fp.expires_at is null or fp.expires_at>cfg.rank_as_of)
    and (
      cfg.surface='all'
      or (cfg.surface='reels' and coalesce(fp.is_reel,false))
      or (cfg.surface='posts' and not coalesce(fp.is_reel,false))
      or (cfg.surface='following' and not coalesce(fp.is_reel,false) and exists (
        select 1 from public.follows f where f.follower_id=cfg.viewer_id and f.following_id=fp.user_id
      ))
      or (cfg.surface='feed' and not coalesce(fp.is_reel,false))
    )
    and (
      fp.user_id=cfg.viewer_id
      or lower(coalesce(fp.audience,'everyone')) in ('everyone','public')
      or (lower(coalesce(fp.audience,'')) in ('followers','following') and exists (
        select 1 from public.follows f where f.follower_id=cfg.viewer_id and f.following_id=fp.user_id
      ))
      or (lower(coalesce(fp.audience,''))='university'
          and nullif(lower(me.university),'')=nullif(lower(ap.university),''))
      or (lower(coalesce(fp.audience,''))='faculty'
          and nullif(lower(me.faculty),'')=nullif(lower(ap.faculty),''))
    )
    and (
      fp.user_id=cfg.viewer_id
      or not exists (select 1 from public.user_settings us
        where us.user_id=fp.user_id and us.private_account=true)
      or exists (select 1 from public.follows f
        where f.follower_id=cfg.viewer_id and f.following_id=fp.user_id)
    )
    and not exists (
      select 1 from public.blocks b
       where (b.blocker_id=cfg.viewer_id and b.blocked_id=fp.user_id)
          or (b.blocker_id=fp.user_id and b.blocked_id=cfg.viewer_id)
    )
    and not exists (
      select 1 from public.muted_users mu
       where mu.user_id=cfg.viewer_id and mu.muted_id=fp.user_id
    )
    and not exists (
      select 1 from public.feed_preferences pref
       where pref.user_id=cfg.viewer_id and pref.post_id=fp.id
         and lower(pref.preference::text) in ('hide','hidden','not_interested','not interested')
    )
),
candidate_source as (
  select
    r.*,
    ln((r.creator_streak+1)::numeric) as streak_component,
    (
      r.likes::numeric
      + r.comments::numeric*3
      + r.shares::numeric*5
      + r.reposts::numeric*5
      + r.views::numeric*0.1
    ) as virality_component,
    case
      when r.distance_meters is null then 0::numeric
      else (1.0/greatest(r.distance_meters,25.0))::numeric
    end as proximity_component,
    least(32::numeric,greatest(-20::numeric,
      cardinality(array(
        select label_value from unnest(r.labels) label_value
        intersect
        select interest_value from unnest(r.positive_interests) interest_value
      ))::numeric*1.5
      - cardinality(array(
        select label_value from unnest(r.labels) label_value
        intersect
        select interest_value from unnest(r.negative_interests) interest_value
      ))::numeric*6
      + coalesce((r.creator_weights->>r.user_id::text)::numeric,0)
      + case when r.user_id=any(r.positive_creators) then 6::numeric else 0::numeric end
      - case when r.user_id=any(r.negative_creators) then 12::numeric else 0::numeric end
      + case when r.has_followed_repost then 12::numeric else 0::numeric end
    )) as affinity_component,
    greatest(0::numeric,extract(epoch from (r.rank_as_of-r.created_at))::numeric/3600::numeric) as age_hours,
    case when r.creator_post_number<=5 then 2::numeric else 1::numeric end as cold_start_multiplier,
    case
      when r.creator_created_at>=r.rank_as_of-interval '30 days'
        or r.creator_post_number<=10 then 'new'
      else 'established'
    end as creator_tier,
    (
      r.creator_created_at>=r.rank_as_of-interval '30 days'
      or r.creator_post_number<=10
    ) as new_creator
  from raw_candidates r
),
scored as (
  select
    c.*,
    (c.affinity_component>0) as matches_interest,
    least(4::numeric,
      (case when c.same_university then 2::numeric else 0::numeric end)
      +(case when c.same_faculty then 1.5::numeric else 0::numeric end)
      +(case when c.same_department then 0.5::numeric else 0::numeric end)
    ) as campus_bonus,
    case when c.reel_completion_samples>=5
      then least(4::numeric,greatest(0::numeric,c.reel_completion_rate)*4::numeric)
      else 0::numeric
    end as reel_completion_bonus,
    coalesce(
      (
        (
          2.0*c.streak_component
          +1.0*c.virality_component
          +250.0*c.proximity_component
          +4.0*c.affinity_component
        )/power(c.age_hours+2::numeric,1.5)
      )*c.verification_multiplier*c.cold_start_multiplier
        *c.active_boost_factor
        +c.personalization_bonus
    ,0)
    + least(4::numeric,
        (case when c.same_university then 2::numeric else 0::numeric end)
        +(case when c.same_faculty then 1.5::numeric else 0::numeric end)
        +(case when c.same_department then 0.5::numeric else 0::numeric end)
      )
    + case when c.reel_completion_samples>=5
        then least(4::numeric,greatest(0::numeric,c.reel_completion_rate)*4::numeric)
        else 0::numeric
      end
    - least(15::numeric,greatest(0,c.report_count)::numeric*2::numeric)
      as quality_score,
    case
      when c.viewer_views>0 then 5
      when c.impressions>=20 and (
        (c.likes+c.comments+c.shares+c.reposts+c.reel_completed_count)::numeric
        / greatest(1,c.views)::numeric
      ) < 0.05 then 4
      when exists (select 1 from public.follows f
        where f.follower_id=c.viewer_id and f.following_id=c.user_id)
        or c.affinity_component>0 then 1
      when c.created_at>=c.rank_as_of-interval '48 hours'
        and (c.same_university or c.same_faculty or c.same_department) then 2
      when c.created_at>=c.rank_as_of-interval '7 days' then 3
      else 4
    end as candidate_tier,
    (
      c.viewer_views=0
      and c.impressions<20
      and (c.new_creator or c.views<50)
    ) as is_exploration,
    case
      when c.viewer_views>0 then
        least(120.0,
          c.viewer_views*1.5
          +40.0*exp(-greatest(0,extract(epoch from(c.rank_as_of-coalesce(c.last_viewed_at,c.rank_as_of)))/86400.0)/2.0)
        )
      else 0.0
    end::numeric as seen_penalty,
    (
      abs(mod(hashtextextended(c.id::text||':'||c.seed::text,0),100000))::numeric/100000.0
    ) as request_jitter
  from candidate_source c
),
tiered as (
  select
    s.*,
    case when s.candidate_tier=5 then
      -extract(epoch from coalesce(s.last_viewed_at,s.rank_as_of))::numeric/1000000.0
      -s.viewer_views
      -least(120.0,greatest(0.0,s.seen_penalty))
      +s.request_jitter*0.01
    else
      least(500.0,greatest(-100.0,s.quality_score-s.seen_penalty+s.request_jitter*0.25))
    end as tier_sort_score
  from scored s
),
grouped as (
  select
    t.*,
    row_number() over (
      partition by t.candidate_tier,t.is_exploration,t.is_reel
      order by t.tier_sort_score desc,t.request_jitter desc,t.created_at desc,t.id desc
    ) as group_rank
  from tiered t
),
interleaved as (
  select
    g.*,
    case
      when g.is_reel then g.group_rank*4
      else g.group_rank + ((g.group_rank-1)/3)
    end as format_order
  from grouped g
),
ordered as (
  select
    i.*,
    row_number() over (
      partition by i.is_exploration
      order by i.candidate_tier asc,i.format_order asc,
        i.tier_sort_score desc,i.request_jitter desc,i.created_at desc,i.id desc
    ) as global_group_rank,
    count(*) filter (where not i.is_exploration) over () as normal_count,
    count(*) filter (where i.is_exploration) over () as exploration_count
  from interleaved i
),
ranked as (
  select
    o.*,
    case
      when o.is_exploration then
        case when o.normal_count=0 then o.global_group_rank else o.global_group_rank*5 end
      else
        case when o.exploration_count=0 then o.global_group_rank
             else o.global_group_rank+((o.global_group_rank-1)/4) end
    end::bigint as feed_order,
    (
      -(
        case
          when o.is_exploration then
            case when o.normal_count=0 then o.global_group_rank else o.global_group_rank*5 end
          else
            case when o.exploration_count=0 then o.global_group_rank
                 else o.global_group_rank+((o.global_group_rank-1)/4) end
        end
      )::numeric*1000000
      + least(999999.0,greatest(-999999.0,o.quality_score))
      + o.request_jitter
    )::numeric as final_score
  from ordered o
),
after_cursor as (
  select r.*
  from ranked r
  cross join cfg
  where cfg.viewer_id is not null
    and (
      p_cursor_score is null
      or r.final_score < p_cursor_score
      or (r.final_score=p_cursor_score and r.id<p_cursor_post_id)
    )
),
creator_limited as (
  select
    a.*,
    row_number() over (
      partition by a.user_id
      order by a.final_score desc,a.id desc
    ) as creator_page_position
  from after_cursor a
),
page as (
  select
    cl.id,
    cl.user_id,
    cl.is_reel,
    cl.final_score,
    cl.candidate_tier,
    cl.is_exploration,
    cl.same_university,
    cl.matches_interest,
    cl.impressions,
    cl.reel_completion_rate,
    cl.reel_completion_samples,
    cl.streak_component,
    cl.virality_component,
    cl.proximity_component,
    cl.affinity_component,
    cl.verification_multiplier,
    cl.cold_start_multiplier,
    cl.active_boost_factor,
    cl.personalization_bonus,
    cl.creator_tier,
    cl.has_followed_repost,
    cl.seen_penalty,
    cl.request_jitter,
    cl.campus_bonus,
    cl.reel_completion_bonus,
    cl.tier_sort_score,
    cl.created_at,
    to_jsonb(fp) || jsonb_build_object(
      'repost_id',cl.repost_id,
      'reposted_by_id',cl.reposted_by_id,
      'reposted_by_username',cl.reposted_by_username,
      'is_reposted_by_me',exists(
        select 1 from public.post_reposts mine
         where mine.post_id=cl.id and mine.user_id=cfg.viewer_id
      )
    ) as item
  from creator_limited cl
  join public.feed_posts fp on fp.id=cl.id
  cross join cfg
  where cl.creator_page_position<=2
  order by cl.final_score desc,cl.id desc
  limit (select page_limit from cfg)
),
last_cursor as (
  select p.final_score,p.id
  from page p
  order by p.final_score asc,p.id asc
  limit 1
)
select
  p.item,
  p.final_score as ranking_score,
  case
    when p.candidate_tier=1 then 'From someone you follow or your interests'
    when p.candidate_tier=2 then 'Trending on your campus'
    when p.candidate_tier=3 then 'Fresh from the last 7 days'
    when p.candidate_tier=4 then 'Quality discovery backfill'
    else 'Returning to a post you have seen before'
  end as ranking_reason,
  jsonb_build_object(
    'model','weighted_decay_v1',
    'candidate_tier',p.candidate_tier,
    'exploration',p.is_exploration,
    'creator_tier',p.creator_tier,
    'streak_component',p.streak_component,
    'virality_component',p.virality_component,
    'proximity_component',p.proximity_component,
    'affinity_component',p.affinity_component,
    'verification_multiplier',p.verification_multiplier,
    'cold_start_multiplier',p.cold_start_multiplier,
    'active_boost_factor',p.active_boost_factor,
    'personalization_bonus',p.personalization_bonus,
    'campus_bonus',p.campus_bonus,
    'reel_completion_rate',p.reel_completion_rate,
    'reel_completion_samples',p.reel_completion_samples,
    'reel_completion_bonus',p.reel_completion_bonus,
    'followed_repost',p.has_followed_repost,
    'seen_penalty',p.seen_penalty,
    'request_jitter',p.request_jitter
  ) as ranking_components,
  p.id as post_id,
  (select lc.final_score::text from last_cursor lc) as next_cursor_score,
  (select lc.id from last_cursor lc) as next_cursor_post_id,
  cfg.rank_as_of as as_of,
  cfg.seed as request_seed
from page p
cross join cfg
order by p.final_score desc,p.id desc;
$$;

create or replace function public.get_for_you_feed_page(
  p_surface text default 'feed',
  p_limit integer default 20,
  p_cursor_score numeric default null,
  p_cursor_post_id uuid default null,
  p_as_of timestamptz default null,
  p_request_seed uuid default null
)
returns table(
  item jsonb,
  ranking_score numeric,
  ranking_reason text,
  ranking_components jsonb,
  post_id uuid,
  next_cursor_score text,
  next_cursor_post_id uuid,
  as_of timestamptz,
  request_seed uuid
)
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if lower(btrim(coalesce(p_surface,'feed'))) not in ('feed','posts','reels','all','following') then
    raise exception 'INVALID_FEED_SURFACE';
  end if;
  return query
    select *
      from private_ranking.get_discovery_feed(
        greatest(1,least(coalesce(p_limit,20),60)),
        p_cursor_score,
        p_cursor_post_id,
        coalesce(p_as_of,now()),
        coalesce(p_request_seed,gen_random_uuid()),
        p_surface
      );
end;
$$;

revoke all on function private_ranking.get_discovery_feed(integer,numeric,uuid,timestamptz,uuid,text) from public,anon,authenticated;
revoke all on function public.get_for_you_feed_page(text,integer,numeric,uuid,timestamptz,uuid) from public,anon;
grant execute on function public.get_for_you_feed_page(text,integer,numeric,uuid,timestamptz,uuid) to authenticated;

-- Server-authoritative Reel watch-time telemetry with session idempotency and
-- anti-replay cooldowns. The same RPC is used by Android, Windows and Web.
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
  v_watch_pct numeric;
  v_completed boolean;
  v_rewatched boolean;
  v_skipped boolean;
  v_inserted integer;
begin
  if v_viewer is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_session_id is null then raise exception 'SESSION_ID_REQUIRED'; end if;
  if p_duration_ms is null or p_duration_ms<1000 or p_duration_ms>600000 then
    raise exception 'INVALID_REEL_DURATION';
  end if;

  if not exists (
    select 1 from public.feed_posts fp
    where fp.id=p_post_id and fp.is_reel=true and fp.is_active=true
      and fp.is_flagged=false and fp.user_id<>v_viewer
      and (fp.expires_at is null or fp.expires_at>now())
      and (
        lower(coalesce(fp.audience,'everyone')) in ('everyone','public')
        or (lower(coalesce(fp.audience,'')) in ('followers','following') and exists (
          select 1 from public.follows f where f.follower_id=v_viewer and f.following_id=fp.user_id
        ))
        or (lower(coalesce(fp.audience,''))='university' and exists (
          select 1 from public.profiles me join public.profiles author on author.id=fp.user_id
          where me.id=v_viewer and nullif(lower(me.university),'')=nullif(lower(author.university),'')
        ))
        or (lower(coalesce(fp.audience,''))='faculty' and exists (
          select 1 from public.profiles me join public.profiles author on author.id=fp.user_id
          where me.id=v_viewer and nullif(lower(me.faculty),'')=nullif(lower(author.faculty),'')
        ))
      )
      and (not exists(select 1 from public.user_settings s where s.user_id=fp.user_id and s.private_account=true)
        or exists(select 1 from public.follows f where f.follower_id=v_viewer and f.following_id=fp.user_id))
      and not exists(select 1 from public.blocks b
        where (b.blocker_id=v_viewer and b.blocked_id=fp.user_id)
           or (b.blocker_id=fp.user_id and b.blocked_id=v_viewer))
      and not exists(select 1 from public.muted_users m where m.user_id=v_viewer and m.muted_id=fp.user_id)
      and not exists(select 1 from public.feed_preferences pref
        where pref.user_id=v_viewer and pref.post_id=fp.id
          and lower(pref.preference::text) in ('hide','hidden','not_interested','not interested'))
  ) then raise exception 'INVALID_REEL'; end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtext(v_viewer::text),pg_catalog.hashtext(p_post_id::text)
  );

  if exists(select 1 from private_ranking.reel_engagement_sessions s
    where s.viewer_id=v_viewer and s.post_id=p_post_id
      and s.created_at>now()-interval '20 seconds') then return; end if;

  if (select count(*) from private_ranking.reel_engagement_sessions s
      where s.viewer_id=v_viewer and s.post_id=p_post_id
        and s.created_at>now()-interval '24 hours')>=24 then return; end if;

  v_watch_ms := least(greatest(coalesce(p_watched_ms,0),0),p_duration_ms*3);
  v_watch_pct := least(1::numeric,v_watch_ms::numeric/p_duration_ms::numeric);
  v_completed := v_watch_ms>=round(p_duration_ms*0.90)::integer;
  v_rewatched := v_watch_ms>=round(p_duration_ms*1.05)::integer;
  v_skipped := not v_completed and (v_watch_ms<=2500 or v_watch_pct<0.20);

  insert into private_ranking.reel_engagement_sessions(
    session_id,viewer_id,post_id,watched_ms,duration_ms
  ) values(p_session_id,v_viewer,p_post_id,v_watch_ms,p_duration_ms)
  on conflict(session_id) do nothing;
  get diagnostics v_inserted=row_count;
  if v_inserted=0 then return; end if;

  insert into private_ranking.discovery_reel_metrics(
    post_id,completion_samples,completion_rate,watch_samples,
    watch_percentage_sum,completed_count,rewatch_count,skip_count,updated_at
  ) values(
    p_post_id,1,case when v_completed then 1 else 0 end,1,
    v_watch_pct,case when v_completed then 1 else 0 end,
    case when v_rewatched then 1 else 0 end,
    case when v_skipped then 1 else 0 end,now()
  )
  on conflict(post_id) do update set
    watch_samples=private_ranking.discovery_reel_metrics.watch_samples+1,
    watch_percentage_sum=private_ranking.discovery_reel_metrics.watch_percentage_sum+excluded.watch_percentage_sum,
    completed_count=private_ranking.discovery_reel_metrics.completed_count+excluded.completed_count,
    rewatch_count=private_ranking.discovery_reel_metrics.rewatch_count+excluded.rewatch_count,
    skip_count=private_ranking.discovery_reel_metrics.skip_count+excluded.skip_count,
    completion_samples=private_ranking.discovery_reel_metrics.watch_samples+1,
    completion_rate=(private_ranking.discovery_reel_metrics.completed_count+excluded.completed_count)::numeric
      /(private_ranking.discovery_reel_metrics.watch_samples+1)::numeric,
    updated_at=now();

  begin
    perform private_ranking.record_recommendation_event(
      'reels','post',p_post_id::text,case when v_skipped then 'skip' else 'dwell' end,
      v_watch_ms,p_session_id,
      jsonb_build_object('model','weighted_decay_v1','watch_percentage',v_watch_pct,'watched_ms',v_watch_ms,
        'duration_ms',p_duration_ms,'completed',v_completed,'rewatched',v_rewatched)
    );
  exception when others then
    null;
  end;
end;
$$;

revoke all on function public.record_reel_engagement(uuid,integer,integer,uuid,boolean,boolean) from public,anon;
grant execute on function public.record_reel_engagement(uuid,integer,integer,uuid,boolean,boolean) to authenticated;

commit;
