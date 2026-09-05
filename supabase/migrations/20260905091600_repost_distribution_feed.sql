-- Reposts are eligible to resurface the original canonical post to people who
-- follow the reposter. The original post keeps its own engagement counters;
-- the returned item only adds repost attribution metadata for the client.
create or replace function private_ranking.get_discovery_feed(
  p_limit integer default 40,
  p_offset integer default 0,
  p_as_of timestamptz default now(),
  p_gravity numeric default 1.5,
  p_w1 numeric default 2.0,
  p_w2 numeric default 1.0,
  p_w3 numeric default 250.0,
  p_w4 numeric default 4.0
)
returns table(
  item jsonb,
  feed_score numeric,
  ranking_components jsonb,
  feed_position integer,
  as_of timestamptz,
  next_offset integer
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_limit integer := greatest(1, least(coalesce(p_limit,40),100));
  v_offset integer := greatest(0,coalesce(p_offset,0));
  v_target_count integer;
  v_candidate_cap integer;
  v_position integer;
  v_want_reel boolean;
  v_want_tier text;
  v_pick record;
  v_last_creator_1 uuid;
  v_last_creator_2 uuid;
  v_viewer_location extensions.geography;
  v_positive_interests text[] := '{}'::text[];
  v_negative_interests text[] := '{}'::text[];
  v_positive_creators uuid[] := '{}'::uuid[];
  v_negative_creators uuid[] := '{}'::uuid[];
  v_tag_weights jsonb := '{}'::jsonb;
  v_creator_weights jsonb := '{}'::jsonb;
  v_feed_type text := lower(coalesce(nullif(current_setting('blink.feed_type', true), ''), 'all'));
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_as_of is null then p_as_of := now(); end if;
  if p_gravity < 0.5 or p_gravity > 3.0 then raise exception 'INVALID_GRAVITY'; end if;
  if least(p_w1,p_w2,p_w3,p_w4) < 0 then raise exception 'INVALID_WEIGHT'; end if;
  if v_offset > 5000 then raise exception 'OFFSET_TOO_LARGE'; end if;
  if v_feed_type not in ('all','posts','reels') then v_feed_type := 'all'; end if;

  v_target_count := least(5100,v_offset + v_limit);
  v_candidate_cap := least(10000,greatest(250,v_target_count * 20));

  select g.location into v_viewer_location
  from private_ranking.discovery_user_geo g
  where g.user_id=v_user;

  select c.positive_interests,c.negative_interests,c.positive_creators,c.negative_creators,c.tag_weights,c.creator_weights
  into v_positive_interests,v_negative_interests,v_positive_creators,v_negative_creators,v_tag_weights,v_creator_weights
  from private_ranking.discovery_interest_cache c
  where c.user_id=v_user;

  v_positive_interests := coalesce(v_positive_interests,'{}'::text[]);
  v_negative_interests := coalesce(v_negative_interests,'{}'::text[]);
  v_positive_creators := coalesce(v_positive_creators,'{}'::uuid[]);
  v_negative_creators := coalesce(v_negative_creators,'{}'::uuid[]);
  v_tag_weights := coalesce(v_tag_weights,'{}'::jsonb);
  v_creator_weights := coalesce(v_creator_weights,'{}'::jsonb);

  drop table if exists pg_temp.discovery_candidates;
  create temporary table discovery_candidates(
    post_id uuid primary key,
    creator_id uuid not null,
    is_reel boolean not null,
    creator_tier text not null,
    feed_score numeric not null,
    completion_rate numeric not null,
    verification_multiplier numeric not null,
    cold_start_multiplier numeric not null,
    affinity_component numeric not null,
    proximity_component numeric not null,
    created_at timestamptz not null
  ) on commit drop;

  drop table if exists pg_temp.discovery_selected;
  create temporary table discovery_selected(
    position integer primary key,
    post_id uuid unique not null
  ) on commit drop;

  insert into pg_temp.discovery_candidates(
    post_id,creator_id,is_reel,creator_tier,feed_score,completion_rate,
    verification_multiplier,cold_start_multiplier,affinity_component,proximity_component,created_at
  )
  with raw as (
    select
      fp.id as post_id,
      fp.user_id as creator_id,
      fp.is_reel,
      fp.creator_post_number,
      greatest(fp.created_at, coalesce(rr.created_at, fp.created_at)) as created_at,
      pr.created_at as creator_created_at,
      greatest(coalesce(pr.daily_streak,0),0) as creator_streak,
      greatest(coalesce(fp.like_count,0),0) as likes,
      greatest(coalesce(fp.comment_count,0),0) as comments,
      greatest(coalesce(fp.share_count,0),0) as shares,
      greatest(coalesce(fp.repost_count,0),0) as reposts,
      greatest(coalesce(fp.view_count,0),0) as views,
      coalesce(rm.completion_rate,0)::numeric as completion_rate,
      coalesce(vm.multiplier,1)::numeric as verification_multiplier,
      (rr.id is not null) as has_followed_repost,
      array(
        select distinct lower(btrim(x))
        from unnest(
          coalesce(fp.tags,'{}'::text[])
          || coalesce(fp.hashtags,'{}'::text[])
          || array[coalesce(fp.category,'')]
        ) x
        where nullif(btrim(x),'') is not null
      ) as labels,
      case
        when v_viewer_location is not null and cg.location is not null then
          extensions.st_distance(v_viewer_location,cg.location)
        else null
      end as distance_meters
    from public.feed_posts fp
    join public.profiles pr on pr.id=fp.user_id
    left join private_ranking.discovery_reel_metrics rm on rm.post_id=fp.id
    left join private_ranking.discovery_user_geo cg on cg.user_id=fp.user_id
    left join private_ranking.discovery_verification_multipliers vm
      on vm.tier = case upper(coalesce(pr.verification_badge,'NONE'))
        when 'GOLD' then 'gold'::public.discovery_verification_tier_enum
        when 'BLUE' then 'blue'::public.discovery_verification_tier_enum
        else 'standard'::public.discovery_verification_tier_enum
      end
    left join lateral (
      select r.id, r.user_id, r.created_at
      from public.post_reposts r
      where r.post_id = fp.id
        and r.user_id <> v_user
        and exists (
          select 1 from public.follows f
          where f.follower_id = v_user and f.following_id = r.user_id
        )
        and not exists (
          select 1 from public.blocks b
          where (b.blocker_id=v_user and b.blocked_id=r.user_id)
             or (b.blocker_id=r.user_id and b.blocked_id=v_user)
        )
        and not exists (
          select 1 from public.muted_users mu
          where mu.user_id=v_user and mu.muted_id=r.user_id
        )
      order by r.created_at desc
      limit 1
    ) rr on true
    where fp.is_active=true
      and fp.is_flagged=false
      and fp.is_sponsored=false
      and (
        v_feed_type = 'all'
        or (v_feed_type = 'posts' and not coalesce(fp.is_reel,false))
        or (v_feed_type = 'reels' and coalesce(fp.is_reel,false))
      )
      and (lower(coalesce(fp.audience,'everyone'))='everyone' or fp.user_id=v_user)
      and not exists (
        select 1 from public.blocks b
        where (b.blocker_id=v_user and b.blocked_id=fp.user_id)
           or (b.blocker_id=fp.user_id and b.blocked_id=v_user)
      )
      and not exists (
        select 1 from public.muted_users mu
        where mu.user_id=v_user and mu.muted_id=fp.user_id
      )
      and greatest(fp.created_at, coalesce(rr.created_at, fp.created_at)) > p_as_of - interval '7 days'
      and greatest(fp.created_at, coalesce(rr.created_at, fp.created_at)) <= p_as_of
      and (fp.expires_at is null or fp.expires_at > p_as_of)
      and not exists (
        select 1
        from public.feed_preferences pref
        where pref.user_id=v_user
          and pref.post_id=fp.id
          and lower(pref.preference) in ('hide','hidden','not_interested','not interested')
      )
  ), components as (
    select
      r.*,
      ln((r.creator_streak + 1)::numeric) as streak_component,
      (
        r.likes::numeric
        + r.comments::numeric * 3
        + r.shares::numeric * 5
        + r.reposts::numeric * 5
        + r.views::numeric * 0.1
      ) * case when r.is_reel and r.completion_rate > 0.80 then 3 else 1 end as virality_component,
      case
        when r.distance_meters is null then 0::numeric
        else (1.0 / greatest(r.distance_meters,25.0))::numeric
      end as proximity_component,
      least(32::numeric,greatest(-20::numeric,
        cardinality(array(
          select a from unnest(r.labels) a
          intersect
          select b from unnest(v_positive_interests) b
        ))::numeric * 1.5
        - cardinality(array(
          select a from unnest(r.labels) a
          intersect
          select b from unnest(v_negative_interests) b
        ))::numeric * 6
        + coalesce((v_creator_weights ->> r.creator_id::text)::numeric,0)
        + case when r.has_followed_repost then 12::numeric else 0::numeric end
      )) as affinity_component,
      greatest(0::numeric,extract(epoch from (p_as_of-r.created_at))::numeric / 3600::numeric) as age_hours,
      case when r.creator_post_number <= 5 then 2::numeric else 1::numeric end as cold_start_multiplier,
      case
        when r.creator_created_at >= p_as_of - interval '30 days' or r.creator_post_number <= 10 then 'new'
        else 'established'
      end as creator_tier
    from raw r
  ), scored as (
    select
      c.*,
      (
        (
          p_w1 * c.streak_component
          + p_w2 * c.virality_component
          + p_w3 * c.proximity_component
          + p_w4 * c.affinity_component
        ) / power(c.age_hours + 2::numeric,p_gravity)
      ) * c.verification_multiplier * c.cold_start_multiplier as final_score
    from components c
  )
  select
    s.post_id,s.creator_id,s.is_reel,s.creator_tier,s.final_score,s.completion_rate,
    s.verification_multiplier,s.cold_start_multiplier,s.affinity_component,s.proximity_component,s.created_at
  from scored s
  order by s.final_score desc,s.created_at desc,s.post_id desc
  limit v_candidate_cap;

  for v_position in 1..v_target_count loop
    v_want_reel := case
      when v_feed_type = 'reels' then true
      when v_feed_type = 'posts' then false
      else mod(v_position,4)=0
    end;
    v_want_tier := case when mod(v_position-1,10) in (2,5,8) then 'new' else 'established' end;

    select c.* into v_pick
    from pg_temp.discovery_candidates c
    where not exists(select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id)
      and c.is_reel=v_want_reel
      and c.creator_tier=v_want_tier
      and not (
        v_last_creator_1 is not null
        and v_last_creator_2=v_last_creator_1
        and c.creator_id=v_last_creator_1
      )
    order by c.feed_score desc,c.created_at desc,c.post_id desc
    limit 1;

    if not found then
      select c.* into v_pick
      from pg_temp.discovery_candidates c
      where not exists(select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id)
        and c.is_reel=v_want_reel
        and not (
          v_last_creator_1 is not null
          and v_last_creator_2=v_last_creator_1
          and c.creator_id=v_last_creator_1
        )
      order by c.feed_score desc,c.created_at desc,c.post_id desc
      limit 1;
    end if;

    if not found then
      select c.* into v_pick
      from pg_temp.discovery_candidates c
      where not exists(select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id)
        and c.creator_tier=v_want_tier
        and not (
          v_last_creator_1 is not null
          and v_last_creator_2=v_last_creator_1
          and c.creator_id=v_last_creator_1
        )
      order by c.feed_score desc,c.created_at desc,c.post_id desc
      limit 1;
    end if;

    if not found then
      select c.* into v_pick
      from pg_temp.discovery_candidates c
      where not exists(select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id)
        and not (
          v_last_creator_1 is not null
          and v_last_creator_2=v_last_creator_1
          and c.creator_id=v_last_creator_1
        )
      order by c.feed_score desc,c.created_at desc,c.post_id desc
      limit 1;
    end if;

    if not found then exit; end if;

    insert into pg_temp.discovery_selected(position,post_id)
    values (v_position,v_pick.post_id);

    v_last_creator_2 := v_last_creator_1;
    v_last_creator_1 := v_pick.creator_id;
  end loop;

  return query
  select
    to_jsonb(fp) || jsonb_build_object(
      'repost_id', dist.id,
      'reposted_by_id', dist.user_id,
      'reposted_by_username', dist.username,
      'repost_count', coalesce(fp.repost_count,0),
      'is_reposted_by_me', exists(
        select 1 from public.post_reposts mine
        where mine.post_id=fp.id and mine.user_id=v_user
      )
    ) as item,
    c.feed_score,
    jsonb_build_object(
      'model','weighted_decay_v1',
      'format',case when c.is_reel then 'reel' else 'post' end,
      'creator_tier',c.creator_tier,
      'verification_multiplier',c.verification_multiplier,
      'cold_start_boost',c.cold_start_multiplier,
      'reel_completion_boost',case when c.is_reel and c.completion_rate > 0.80 then 3 else 1 end,
      'proximity_applied',c.proximity_component > 0,
      'affinity_direction',case when c.affinity_component > 0 then 'positive' when c.affinity_component < 0 then 'negative' else 'neutral' end,
      'repost_distribution_boost',dist.id is not null
    ) as ranking_components,
    s.position as feed_position,
    p_as_of as as_of,
    (v_offset + count(*) over())::integer as next_offset
  from pg_temp.discovery_selected s
  join pg_temp.discovery_candidates c on c.post_id=s.post_id
  join public.feed_posts fp on fp.id=s.post_id
  left join lateral (
    select r.id, r.user_id, rp.username, r.created_at
    from public.post_reposts r
    join public.profiles rp on rp.id=r.user_id
    where r.post_id=fp.id
      and r.user_id<>v_user
      and exists (
        select 1 from public.follows f
        where f.follower_id=v_user and f.following_id=r.user_id
      )
      and not exists (
        select 1 from public.blocks b
        where (b.blocker_id=v_user and b.blocked_id=r.user_id)
           or (b.blocker_id=r.user_id and b.blocked_id=v_user)
      )
      and not exists (
        select 1 from public.muted_users mu
        where mu.user_id=v_user and mu.muted_id=r.user_id
      )
    order by r.created_at desc
    limit 1
  ) dist on true
  where s.position > v_offset
    and s.position <= v_offset + v_limit
  order by s.position;
end;
$$;
