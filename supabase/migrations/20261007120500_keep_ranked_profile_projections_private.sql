begin;

-- Compatibility repair for profile row privacy. Public discovery/ranking reads
-- only the existing public identity and ranking inputs through this projection.
-- Contacts, device tokens and wallet/account fields are never projected.
-- Private locality inputs are retained only for the authenticated owner.
-- Ranking formulas, weights, audience checks and all other table RLS stay intact.
-- Rollback: restore the prior function definitions with the prior profile-read
-- policy only as a coordinated rollback; never reopen private raw rows alone.
create or replace function public.get_blink_ranking_profiles(p_ids uuid[] default null)
returns table(
  id uuid,
  username text,
  full_name text,
  avatar_url text,
  university text,
  faculty text,
  department text,
  course_of_study text,
  academic_level text,
  professional_headline text,
  current_city_state text,
  campus_hostel_location text,
  bio text,
  core_skills text[],
  hobbies text[],
  languages text[],
  verification_badge text,
  verification_tier public.verification_tier_enum,
  is_verified boolean,
  relationship_status public.relationship_status_enum,
  is_online boolean,
  last_seen timestamp with time zone,
  follower_count integer,
  name text,
  created_at timestamp with time zone,
  updated_at timestamp with time zone,
  online_now boolean,
  last_seen_at timestamp with time zone
)
language sql stable security definer set search_path = ''
as $projection$
  select
    p.id,
    p.username,
    p.full_name,
    p.avatar_url,
    p.university,
    p.faculty,
    p.department,
    p.course_of_study,
    p.academic_level,
    p.professional_headline,
    case when p.id=auth.uid() then p.current_city_state else null end as current_city_state,
    case when p.id=auth.uid() then p.campus_hostel_location else null end as campus_hostel_location,
    p.bio,
    p.core_skills,
    p.hobbies,
    p.languages,
    p.verification_badge,
    p.verification_tier,
    p.is_verified,
    p.relationship_status,
    case when private.blink_profile_presence_visible(p.id) then coalesce(p.is_online,false) else false end as is_online,
    case when private.blink_profile_presence_visible(p.id) then p.last_seen else null end as last_seen,
    p.follower_count,
    p.name,
    p.created_at,
    p.updated_at,
    case when private.blink_profile_presence_visible(p.id) then coalesce(p.online_now,false) else false end as online_now,
    case when private.blink_profile_presence_visible(p.id) then p.last_seen_at else null end as last_seen_at
  from public.profiles p
  where auth.uid() is not null
    and (p_ids is null or p.id=any(p_ids))
    and not exists (
      select 1 from public.blocks b
      where (b.blocker_id=auth.uid() and b.blocked_id=p.id)
         or (b.blocker_id=p.id and b.blocked_id=auth.uid())
    );
$projection$;
revoke all on function public.get_blink_ranking_profiles(uuid[]) from public, anon;
grant execute on function public.get_blink_ranking_profiles(uuid[]) to authenticated;

CREATE OR REPLACE FUNCTION private_ranking.viewer_personalization_bonus(p_viewer uuid, p_post uuid, p_creator uuid, p_as_of timestamp with time zone)
 RETURNS numeric
 LANGUAGE sql
 STABLE
 SET search_path TO ''
AS $function$
with viewer as (
  select id, university, faculty, department
  from public.get_blink_ranking_profiles(array[p_viewer])
  where id = p_viewer
),
creator as (
  select id, university, faculty, department
  from public.get_blink_ranking_profiles(array[p_creator])
  where id = p_creator
),
post as (
  select id, user_id, category, faculty, type, tags, hashtags,
         coalesce(is_reel,false) as is_reel
  from public.feed_posts
  where id = p_post
),
affinity as (
  select least(18::numeric, coalesce(sum(signal),0::numeric)) as score
  from (
    select count(*)::numeric * 2.5 as signal
    from public.post_likes l
    join public.feed_posts fp on fp.id=l.post_id
    where l.user_id=p_viewer and fp.user_id=p_creator
      and l.created_at >= coalesce(p_as_of,now()) - interval '90 days'
    union all
    select count(*)::numeric * 4 as signal
    from public.post_bookmarks b
    join public.feed_posts fp on fp.id=b.post_id
    where b.user_id=p_viewer and fp.user_id=p_creator
      and b.created_at >= coalesce(p_as_of,now()) - interval '90 days'
    union all
    select count(*)::numeric * 4 as signal
    from public.comments c
    join public.feed_posts fp on fp.id=c.post_id
    where c.author_id=p_viewer and fp.user_id=p_creator
      and c.created_at >= coalesce(p_as_of,now()) - interval '90 days'
    union all
    select least(8::numeric, coalesce(sum(greatest(v.impression_count,1)),0)::numeric * 0.35) as signal
    from public.post_views v
    join public.feed_posts fp on fp.id=v.post_id
    where v.viewer_id=p_viewer and fp.user_id=p_creator
      and v.last_viewed_at >= coalesce(p_as_of,now()) - interval '45 days'
  ) s
),
interest as (
  select least(15::numeric, coalesce(sum(greatest(iw.weight,0)),0::numeric) * 0.75) as score
  from public.user_interest_weights iw
  cross join post p
  where iw.user_id=p_viewer
    and iw.surface = case when p.is_reel then 'reels' else 'feed' end
    and (
      (iw.feature_type='author' and iw.feature_value=p_creator::text)
      or (iw.feature_type='category' and iw.feature_value=lower(btrim(coalesce(p.category,''))))
      or (iw.feature_type='faculty' and iw.feature_value=lower(btrim(coalesce(p.faculty,''))))
      or (iw.feature_type='content_type' and iw.feature_value=lower(btrim(coalesce(p.type,''))))
      or (
        iw.feature_type='tag'
        and iw.feature_value = any(
          array(
            select lower(btrim(x))
            from unnest(coalesce(p.tags,'{}'::text[]) || coalesce(p.hashtags,'{}'::text[])) x
            where nullif(btrim(x),'') is not null
          )
        )
      )
    )
),
seen as (
  select coalesce(sum(greatest(v.impression_count,1)),0)::numeric as impressions
  from public.post_views v
  where v.viewer_id=p_viewer and v.post_id=p_post
    and v.last_viewed_at >= coalesce(p_as_of,now()) - interval '14 days'
),
parts as (
  select
    case when exists(
      select 1 from public.follows f
      where f.follower_id=p_viewer and f.following_id=p_creator
    ) then 18::numeric else 0::numeric end as follow_score,
    case
      when nullif(lower(btrim(v.university)),'') is not null
       and nullif(lower(btrim(v.university)),'')=nullif(lower(btrim(c.university)),'') then 8::numeric
      else 0::numeric
    end
    + case
      when nullif(lower(btrim(v.faculty)),'') is not null
       and nullif(lower(btrim(v.faculty)),'')=nullif(lower(btrim(c.faculty)),'') then 3::numeric
      else 0::numeric
    end
    + case
      when nullif(lower(btrim(v.department)),'') is not null
       and nullif(lower(btrim(v.department)),'')=nullif(lower(btrim(c.department)),'') then 5::numeric
      else 0::numeric
    end as campus_score,
    a.score as affinity_score,
    i.score as interest_score,
    case when s.impressions=0 then 4::numeric else 0::numeric end as unseen_bonus,
    least(15::numeric,s.impressions * 1.5) as exposure_penalty,
    (
      abs(mod(hashtextextended(
        p_post::text || ':' || p_viewer::text || ':' ||
        floor(extract(epoch from coalesce(p_as_of,now())) / 21600)::bigint::text,
        0
      ),10000))::numeric / 10000::numeric
    ) * 8::numeric as exploration_score,
    case when p_viewer=p_creator then 3::numeric else 0::numeric end as self_penalty
  from viewer v cross join creator c cross join affinity a cross join interest i cross join seen s
)
select coalesce(
  follow_score + campus_score + affinity_score + interest_score
  + unseen_bonus + exploration_score - exposure_penalty - self_penalty,
  0::numeric
)
from parts;
$function$;

CREATE OR REPLACE FUNCTION public.creator_badge_distribution_factor(p_post_id uuid)
 RETURNS numeric
 LANGUAGE sql
 STABLE
 SET search_path TO ''
AS $function$
  select coalesce(
    case
      when upper(coalesce(pr.verification_badge,''))='GOLD'
        or upper(coalesce(pr.verification_tier::text,''))='GOLD' then 5::numeric
      when upper(coalesce(pr.verification_badge,''))='BLUE'
        or upper(coalesce(pr.verification_tier::text,''))='STANDARD'
        or coalesce(pr.is_verified,false) then 3::numeric
      else 1::numeric
    end,
    1::numeric
  )
  from public.feed_posts fp
  join lateral public.get_blink_ranking_profiles(array[fp.user_id]) pr on pr.id=fp.user_id
  where fp.id=p_post_id;
$function$;

CREATE OR REPLACE FUNCTION public.creator_organic_distribution_factor(p_post_id uuid, p_at timestamp with time zone DEFAULT now())
 RETURNS numeric
 LANGUAGE sql
 STABLE
 SET search_path TO ''
AS $function$
with target as (
  select
    fp.user_id as creator_id,
    fp.created_at as post_created_at,
    greatest(coalesce(pr.follower_count,0),0)::numeric as follower_count
  from public.feed_posts fp
  join lateral public.get_blink_ranking_profiles(array[fp.user_id]) pr on pr.id=fp.user_id
  where fp.id=p_post_id
), early as (
  select count(*)::numeric as unique_viewers
  from public.post_views pv
  cross join target t
  where pv.post_id=p_post_id
    and pv.created_at>=t.post_created_at
    and pv.created_at<=least(
      t.post_created_at + interval '20 minutes',
      coalesce(p_at,now())
    )
), calc as (
  select
    t.follower_count,
    e.unique_viewers,
    greatest(
      1::numeric,
      least(
        20::numeric,
        greatest(
          0::numeric,
          extract(epoch from (coalesce(p_at,now())-t.post_created_at))::numeric / 60::numeric
        )
      )
    ) as elapsed_minutes
  from target t
  cross join early e
)
select coalesce(round(
  (
    1::numeric
    + least(0.60::numeric, ln(1::numeric + follower_count) * 0.05::numeric)
  )
  *
  (
    1::numeric
    + least(
        1.50::numeric,
        ln(1::numeric + (unique_viewers / elapsed_minutes)) * 0.35::numeric
        + ln(1::numeric + unique_viewers) * 0.08::numeric
      )
  ),
  6
),1::numeric)
from calc;
$function$;

CREATE OR REPLACE FUNCTION public.get_connect_matches(p_limit integer DEFAULT 24)
 RETURNS TABLE(id uuid, username text, full_name text, avatar_url text, university text, faculty text, department text, academic_level text, relationship_status text, online_now boolean, last_seen_at timestamp with time zone, compatibility_score integer, common_skills text[], common_hobbies text[])
 LANGUAGE sql
 STABLE
 SET search_path TO ''
AS $function$
with cfg as (
  select auth.uid() as caller_id, now() as rank_as_of
),
me as (
  select p.* from public.get_blink_ranking_profiles(array[auth.uid()]) p cross join cfg where p.id=cfg.caller_id
),
exposure as (
  select e.target_key,
    count(*) filter(where e.event_type='impression') as impressions,
    count(*) filter(where e.event_type='skip') as skips
  from public.recommendation_events e cross join cfg
  where e.user_id=cfg.caller_id and e.surface='connect' and e.target_type='profile'
    and e.created_at>=cfg.rank_as_of-interval '21 days'
  group by e.target_key
),
candidates as (
  select
    p.*,
    skills.values as shared_skills,
    hobbies.values as shared_hobbies,
    languages.values as shared_languages,
    coalesce(ex.impressions,0) as impressions,
    coalesce(ex.skips,0) as skips,
    least(10.0,0.5*coalesce((
      select sum(greatest(iw.weight,0))
      from public.user_interest_weights iw
      where iw.user_id=cfg.caller_id and iw.surface='connect'
        and (
          (iw.feature_type='university' and iw.feature_value=lower(btrim(coalesce(p.university,''))))
          or (iw.feature_type='faculty' and iw.feature_value=lower(btrim(coalesce(p.faculty,''))))
          or (iw.feature_type='department' and iw.feature_value=lower(btrim(coalesce(p.department,''))))
          or (iw.feature_type='academic_level' and iw.feature_value=lower(btrim(coalesce(p.academic_level,''))))
        )
    ),0)::double precision) as learned_component,
    cfg.rank_as_of
  from public.get_blink_ranking_profiles() p
  cross join cfg cross join me
  left join exposure ex on ex.target_key=p.id::text
  left join lateral (
    select coalesce(array_agg(v order by v),array[]::text[]) as values
    from (
      select distinct lower(btrim(x)) as v from unnest(coalesce(p.core_skills,'{}'::text[])) x
      where nullif(btrim(x),'') is not null
      intersect
      select distinct lower(btrim(x)) from unnest(coalesce(me.core_skills,'{}'::text[])) x
      where nullif(btrim(x),'') is not null
    ) s
  ) skills on true
  left join lateral (
    select coalesce(array_agg(v order by v),array[]::text[]) as values
    from (
      select distinct lower(btrim(x)) as v from unnest(coalesce(p.hobbies,'{}'::text[])) x
      where nullif(btrim(x),'') is not null
      intersect
      select distinct lower(btrim(x)) from unnest(coalesce(me.hobbies,'{}'::text[])) x
      where nullif(btrim(x),'') is not null
    ) h
  ) hobbies on true
  left join lateral (
    select coalesce(array_agg(v order by v),array[]::text[]) as values
    from (
      select distinct lower(btrim(x)) as v from unnest(coalesce(p.languages,'{}'::text[])) x
      where nullif(btrim(x),'') is not null
      intersect
      select distinct lower(btrim(x)) from unnest(coalesce(me.languages,'{}'::text[])) x
      where nullif(btrim(x),'') is not null
    ) l
  ) languages on true
  where cfg.caller_id is not null and p.id<>cfg.caller_id
    and nullif(btrim(p.username),'') is not null
    and not exists(
      select 1 from public.blocks b
      where (b.blocker_id=cfg.caller_id and b.blocked_id=p.id)
         or (b.blocker_id=p.id and b.blocked_id=cfg.caller_id)
    )
    and not exists(
      select 1 from public.muted_users mu where mu.user_id=cfg.caller_id and mu.muted_id=p.id
    )
    and not exists(
      select 1 from public.connection_requests cr
      where lower(cr.status) in ('pending','accepted')
        and ((cr.sender_id=cfg.caller_id and cr.receiver_id=p.id)
          or (cr.sender_id=p.id and cr.receiver_id=cfg.caller_id))
    )
),
ranked as (
  select c.*,
    least(100,greatest(0,round(
      (case when nullif(lower(me.university),'')=nullif(lower(c.university),'') then 25 else 0 end)
      +(case when nullif(lower(me.department),'')=nullif(lower(c.department),'') then 20 else 0 end)
      +(case when nullif(lower(me.faculty),'')=nullif(lower(c.faculty),'') then 8 else 0 end)
      +(case when nullif(lower(me.academic_level),'')=nullif(lower(c.academic_level),'') then 7 else 0 end)
      +least(15,cardinality(c.shared_skills)*5)
      +least(8,cardinality(c.shared_hobbies)*2)
      +least(5,cardinality(c.shared_languages)*2)
      +(case when coalesce(c.online_now,false) then 5 else 0 end)
      +(case when nullif(c.avatar_url,'') is not null and nullif(c.bio,'') is not null
              and nullif(c.department,'') is not null then 4 else 0 end)
      +c.learned_component
      +5.0*abs(mod(hashtextextended(
        c.id::text||':'||cfg.caller_id::text||':'||cfg.rank_as_of::date::text,0
      ),1000))::double precision/1000.0
      -least(12,c.impressions*1.5+c.skips*3.0)
    )::integer)) as score
  from candidates c cross join cfg cross join me
)
select
  r.id,r.username,coalesce(nullif(r.full_name,''),r.name,r.username),r.avatar_url,
  r.university,r.faculty,r.department,r.academic_level,r.relationship_status::text,
  coalesce(r.online_now,r.is_online,false),coalesce(r.last_seen_at,r.last_seen),r.score,
  r.shared_skills,r.shared_hobbies
from ranked r
order by r.score desc,coalesce(r.online_now,r.is_online,false) desc,
  coalesce(r.last_seen_at,r.last_seen) desc nulls last,r.id
limit greatest(1,least(coalesce(p_limit,24),50));
$function$;

-- The legacy chat list delegates to the authenticated membership and presence
-- contract; the old participant RLS cannot join a partner's raw membership row.
CREATE OR REPLACE FUNCTION public.get_conversation_summaries(p_limit integer DEFAULT 50)
 RETURNS TABLE(conversation_id uuid, partner_id uuid, partner_username text, partner_name text, partner_avatar text, partner_online boolean, partner_last_seen timestamp with time zone, last_message text, last_message_at timestamp with time zone, unread_count bigint)
 LANGUAGE sql STABLE SET search_path TO ''
AS $legacy_chat$
 select conversation_id,partner_id,partner_username,partner_name,partner_avatar,
        partner_online,partner_last_seen,last_message,last_message_at,unread_count
 from public.get_conversation_summaries_page(p_limit,null,null);
$legacy_chat$;
revoke all on function public.get_conversation_summaries(integer) from public, anon;
grant execute on function public.get_conversation_summaries(integer) to authenticated;

CREATE OR REPLACE FUNCTION public.get_my_study_circle_join_requests(p_limit integer DEFAULT 100)
 RETURNS TABLE(request_id uuid, circle_id uuid, circle_name text, requester_id uuid, requester_username text, requester_full_name text, requester_avatar_url text, status text, created_at timestamp with time zone)
 LANGUAGE sql
 STABLE
 SET search_path TO 'public', 'pg_temp'
AS $function$
  SELECT
    r.id,
    r.circle_id,
    c.name,
    r.user_id,
    p.username,
    p.full_name,
    p.avatar_url,
    r.status,
    r.created_at
  FROM public.study_circle_requests r
  JOIN public.study_circles c ON c.id=r.circle_id
  JOIN public.get_blink_ranking_profiles() p ON p.id=r.user_id
  WHERE c.owner_id=(select auth.uid())
    AND r.status='pending'
  ORDER BY r.created_at ASC
  LIMIT greatest(1,least(coalesce(p_limit,100),200));
$function$;

CREATE OR REPLACE FUNCTION public.get_ranked_connect_opportunities(p_mode text DEFAULT 'all'::text, p_limit integer DEFAULT 30, p_cursor_score numeric DEFAULT NULL::numeric, p_cursor_kind text DEFAULT NULL::text, p_cursor_id uuid DEFAULT NULL::uuid, p_as_of timestamp with time zone DEFAULT now())
 RETURNS TABLE(kind text, target_id uuid, payload jsonb, ranking_score numeric, ranking_reason text, as_of timestamp with time zone)
 LANGUAGE sql
 STABLE
 SET search_path TO ''
AS $function$
with cfg as (
  select auth.uid() as caller_id,
    lower(btrim(coalesce(p_mode,'all'))) as mode,
    least(coalesce(p_as_of,now()),now()+interval '1 minute') as rank_as_of
),
me as (
  select p.* from public.get_blink_ranking_profiles(array[auth.uid()]) p cross join cfg where p.id=cfg.caller_id
),
listing_results as (
  select
    coalesce(nullif(lower(cl.listing_type),''),'listing') as kind,
    cl.id as target_id,
    to_jsonb(cl)||jsonb_build_object(
      'target_type','connect_listing',
      'profile',jsonb_build_object(
        'id',p.id,'username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),
        'avatar_url',p.avatar_url,'university',p.university,'faculty',p.faculty,
        'department',p.department,'academic_level',p.academic_level,'online_now',p.online_now
      )
    ) as payload,
    (
      (case when nullif(lower(me.university),'')=nullif(lower(coalesce(cl.university,p.university)),'') then 28 else 0 end)
      +(case when nullif(lower(me.department),'')=nullif(lower(coalesce(cl.department,p.department)),'') then 18 else 0 end)
      +(case when nullif(lower(me.academic_level),'')=nullif(lower(coalesce(cl.academic_level,p.academic_level)),'') then 7 else 0 end)
      +least(15,3*coalesce((
        select count(*) from (
          select distinct lower(btrim(x)) v
          from unnest(coalesce(cl.tags,'{}'::text[])||coalesce(cl.subjects,'{}'::text[])) x
          where nullif(btrim(x),'') is not null
          intersect
          select distinct lower(btrim(x))
          from unnest(coalesce(me.core_skills,'{}'::text[])||coalesce(me.hobbies,'{}'::text[])) x
          where nullif(btrim(x),'') is not null
        ) common
      ),0))
      +12.0*exp(-greatest(0.0,extract(epoch from(cfg.rank_as_of-cl.updated_at))/3600.0)/336.0)
      +5.0*abs(mod(hashtextextended(cl.id::text||':'||cfg.caller_id::text||':'||cfg.rank_as_of::date::text,0),1000))::double precision/1000.0
      -least(12,1.5*coalesce((
        select count(*) from public.recommendation_events e
        where e.user_id=cfg.caller_id and e.surface='connect' and e.target_type='connect_listing'
          and e.target_key=cl.id::text and e.event_type in ('impression','skip')
          and e.created_at>=cfg.rank_as_of-interval '21 days'
      ),0))
    )::double precision as raw_score,
    case
      when nullif(lower(me.department),'')=nullif(lower(coalesce(cl.department,p.department)),'') then 'Matches your department'
      when nullif(lower(me.university),'')=nullif(lower(coalesce(cl.university,p.university)),'') then 'On your campus'
      else 'Recommended opportunity'
    end as reason,
    cl.updated_at as created_at
  from public.connect_listings cl join public.get_blink_ranking_profiles() p on p.id=cl.user_id
  cross join cfg cross join me
  where cfg.caller_id is not null and cl.is_active and cl.user_id<>cfg.caller_id
    and (cfg.mode in ('all','listing','listings') or cfg.mode=lower(cl.listing_type))
    and not exists(
      select 1 from public.blocks b
      where (b.blocker_id=cfg.caller_id and b.blocked_id=cl.user_id)
         or (b.blocker_id=cl.user_id and b.blocked_id=cfg.caller_id)
    )
    and not exists(
      select 1 from public.muted_users mu where mu.user_id=cfg.caller_id and mu.muted_id=cl.user_id
    )
),
roommate_results as (
  select
    'roommate'::text,
    rp.id,
    to_jsonb(rp)||jsonb_build_object(
      'target_type','roommate',
      'profile',jsonb_build_object(
        'id',p.id,'username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),
        'avatar_url',p.avatar_url,'university',p.university,'faculty',p.faculty,
        'department',p.department,'academic_level',p.academic_level,'online_now',p.online_now
      )
    ),
    (
      (case when nullif(lower(me.university),'')=nullif(lower(p.university),'') then 32 else 0 end)
      +(case when nullif(lower(me.department),'')=nullif(lower(p.department),'') then 12 else 0 end)
      +(case when nullif(lower(rp.location),'') in (
        nullif(lower(me.campus_hostel_location),''),nullif(lower(me.current_city_state),'')
      ) then 18 else 0 end)
      +12.0*exp(-greatest(0.0,extract(epoch from(cfg.rank_as_of-rp.updated_at))/3600.0)/336.0)
      +4.0*abs(mod(hashtextextended(rp.id::text||':'||cfg.caller_id::text||':'||cfg.rank_as_of::date::text,0),1000))::double precision/1000.0
      -least(12,1.5*coalesce((select count(*) from public.recommendation_events e
        where e.user_id=cfg.caller_id and e.target_type='roommate' and e.target_key=rp.id::text
          and e.event_type in ('impression','skip') and e.created_at>=cfg.rank_as_of-interval '21 days'),0))
    )::double precision,
    case when nullif(lower(rp.location),'') in (
      nullif(lower(me.campus_hostel_location),''),nullif(lower(me.current_city_state),'')
    ) then 'Matches your preferred area'
    when nullif(lower(me.university),'')=nullif(lower(p.university),'') then 'Same university'
    else 'Potential roommate match' end,
    rp.updated_at
  from public.roommate_profiles rp join public.get_blink_ranking_profiles() p on p.id=rp.user_id
  cross join cfg cross join me
  where cfg.caller_id is not null and rp.is_active and rp.user_id<>cfg.caller_id
    and cfg.mode in ('all','roommate','roommates')
    and not exists(select 1 from public.blocks b where
      (b.blocker_id=cfg.caller_id and b.blocked_id=rp.user_id) or
      (b.blocker_id=rp.user_id and b.blocked_id=cfg.caller_id))
),
mentor_results as (
  select
    'mentor'::text,
    mp.id,
    to_jsonb(mp)||jsonb_build_object(
      'target_type','mentor',
      'profile',jsonb_build_object(
        'id',p.id,'username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),
        'avatar_url',p.avatar_url,'university',p.university,'faculty',p.faculty,
        'department',p.department,'academic_level',p.academic_level,'online_now',p.online_now
      )
    ),
    (
      (case when nullif(lower(me.university),'')=nullif(lower(p.university),'') then 28 else 0 end)
      +(case when nullif(lower(me.department),'')=nullif(lower(p.department),'') then 18 else 0 end)
      +(case when nullif(lower(me.academic_level),'')=nullif(lower(mp.preferred_level),'') then 8 else 0 end)
      +least(18,5*coalesce((select count(*) from unnest(coalesce(mp.subjects,'{}'::text[])) x
        where lower(btrim(x))=lower(btrim(coalesce(me.course_of_study,'')))
           or lower(btrim(x))=any(array(select lower(btrim(s)) from unnest(coalesce(me.core_skills,'{}'::text[])) s))),0))
      +12.0*exp(-greatest(0.0,extract(epoch from(cfg.rank_as_of-mp.updated_at))/3600.0)/336.0)
      -least(12,1.5*coalesce((select count(*) from public.recommendation_events e
        where e.user_id=cfg.caller_id and e.target_type='mentor' and e.target_key=mp.id::text
          and e.event_type in ('impression','skip') and e.created_at>=cfg.rank_as_of-interval '21 days'),0))
    )::double precision,
    case when nullif(lower(me.department),'')=nullif(lower(p.department),'') then 'Mentor in your department'
      when nullif(lower(me.university),'')=nullif(lower(p.university),'') then 'Mentor on your campus'
      else 'Recommended mentor' end,
    mp.updated_at
  from public.mentor_profiles mp join public.get_blink_ranking_profiles() p on p.id=mp.user_id
  cross join cfg cross join me
  where cfg.caller_id is not null and mp.is_active and mp.user_id<>cfg.caller_id
    and cfg.mode in ('all','mentor','mentors')
    and not exists(select 1 from public.blocks b where
      (b.blocker_id=cfg.caller_id and b.blocked_id=mp.user_id) or
      (b.blocker_id=mp.user_id and b.blocked_id=cfg.caller_id))
),
reading_results as (
  select
    'reading_mate'::text,
    rp.id,
    to_jsonb(rp)||jsonb_build_object(
      'target_type','reading_mate',
      'profile',jsonb_build_object(
        'id',p.id,'username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),
        'avatar_url',p.avatar_url,'university',p.university,'faculty',p.faculty,
        'department',p.department,'academic_level',p.academic_level,'online_now',p.online_now
      )
    ),
    (
      (case when nullif(lower(me.university),'')=nullif(lower(p.university),'') then 28 else 0 end)
      +(case when nullif(lower(me.department),'')=nullif(lower(p.department),'') then 20 else 0 end)
      +least(20,6*coalesce((select count(*) from unnest(coalesce(rp.courses,'{}'::text[])) x
        where lower(btrim(x))=lower(btrim(coalesce(me.course_of_study,'')))
           or lower(btrim(x))=any(array(select lower(btrim(s)) from unnest(coalesce(me.core_skills,'{}'::text[])) s))),0))
      +12.0*exp(-greatest(0.0,extract(epoch from(cfg.rank_as_of-rp.updated_at))/3600.0)/336.0)
      -least(12,1.5*coalesce((select count(*) from public.recommendation_events e
        where e.user_id=cfg.caller_id and e.target_type='reading_mate' and e.target_key=rp.id::text
          and e.event_type in ('impression','skip') and e.created_at>=cfg.rank_as_of-interval '21 days'),0))
    )::double precision,
    case when nullif(lower(me.department),'')=nullif(lower(p.department),'') then 'Reading mate in your department'
      when nullif(lower(me.university),'')=nullif(lower(p.university),'') then 'Reading mate on your campus'
      else 'Recommended reading mate' end,
    rp.updated_at
  from public.reading_mate_profiles rp join public.get_blink_ranking_profiles() p on p.id=rp.user_id
  cross join cfg cross join me
  where cfg.caller_id is not null and rp.is_active and rp.user_id<>cfg.caller_id
    and cfg.mode in ('all','reading_mate','reading','reading_mates')
    and not exists(select 1 from public.blocks b where
      (b.blocker_id=cfg.caller_id and b.blocked_id=rp.user_id) or
      (b.blocker_id=rp.user_id and b.blocked_id=cfg.caller_id))
),
housing_results as (
  select
    'housing'::text,
    hr.id,
    to_jsonb(hr)||jsonb_build_object(
      'target_type','housing',
      'profile',jsonb_build_object(
        'id',p.id,'username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),
        'avatar_url',p.avatar_url,'university',p.university,'faculty',p.faculty,
        'department',p.department,'academic_level',p.academic_level
      )
    ),
    (
      (case when nullif(lower(me.university),'')=nullif(lower(p.university),'') then 30 else 0 end)
      +(case when nullif(lower(hr.preferred_location),'') in (
        nullif(lower(me.campus_hostel_location),''),nullif(lower(me.current_city_state),'')
      ) then 18 else 0 end)
      +15.0*exp(-greatest(0.0,extract(epoch from(cfg.rank_as_of-hr.updated_at))/3600.0)/240.0)
      -least(12,1.5*coalesce((select count(*) from public.recommendation_events e
        where e.user_id=cfg.caller_id and e.target_type='housing' and e.target_key=hr.id::text
          and e.event_type in ('impression','skip') and e.created_at>=cfg.rank_as_of-interval '21 days'),0))
    )::double precision,
    case when nullif(lower(hr.preferred_location),'') in (
      nullif(lower(me.campus_hostel_location),''),nullif(lower(me.current_city_state),'')
    ) then 'Housing request in your area'
    when nullif(lower(me.university),'')=nullif(lower(p.university),'') then 'Housing request on your campus'
    else 'Open housing request' end,
    hr.updated_at
  from public.housing_requests hr join public.get_blink_ranking_profiles() p on p.id=hr.student_id
  cross join cfg cross join me
  where cfg.caller_id is not null and hr.status='open' and hr.student_id<>cfg.caller_id
    and cfg.mode in ('all','housing','house','agent')
    and not exists(select 1 from public.blocks b where
      (b.blocker_id=cfg.caller_id and b.blocked_id=hr.student_id) or
      (b.blocker_id=hr.student_id and b.blocked_id=cfg.caller_id))
),
raw as (
  select * from listing_results
  union all select * from roommate_results
  union all select * from mentor_results
  union all select * from reading_results
  union all select * from housing_results
),
diversified as (
  select r.*,row_number() over(partition by kind order by raw_score desc,created_at desc,target_id) as kind_position
  from raw r
),
ranked as (
  select d.*,
    round((d.raw_score-greatest(0,d.kind_position-4)::double precision*3.0)::numeric,6) as final_score
  from diversified d
)
select r.kind,r.target_id,r.payload,r.final_score,r.reason,cfg.rank_as_of
from ranked r cross join cfg
where p_cursor_score is null
   or r.final_score<p_cursor_score
   or (r.final_score=p_cursor_score and p_cursor_kind is not null and (
     r.kind>p_cursor_kind or (r.kind=p_cursor_kind and p_cursor_id is not null and r.target_id>p_cursor_id)
   ))
order by r.final_score desc,r.kind,r.target_id
limit greatest(1,least(coalesce(p_limit,30),60));
$function$;

CREATE OR REPLACE FUNCTION public.get_ranked_feed_page(p_surface text DEFAULT 'feed'::text, p_limit integer DEFAULT 30, p_cursor_score numeric DEFAULT NULL::numeric, p_cursor_created_at timestamp with time zone DEFAULT NULL::timestamp with time zone, p_cursor_id uuid DEFAULT NULL::uuid, p_as_of timestamp with time zone DEFAULT now())
 RETURNS TABLE(item jsonb, ranking_score numeric, ranking_reason text, as_of timestamp with time zone)
 LANGUAGE sql
 STABLE
 SET search_path TO ''
AS $function$
with cfg as (
  select
    auth.uid() as caller_id,
    case lower(btrim(coalesce(p_surface,'feed')))
      when 'reel' then 'reels'
      when 'posts' then 'feed'
      else lower(btrim(coalesce(p_surface,'feed')))
    end as mode,
    least(coalesce(p_as_of,now()),now() + interval '1 minute') as rank_as_of
),
me as (
  select p.*
  from public.get_blink_ranking_profiles(array[auth.uid()]) p cross join cfg
  where p.id=cfg.caller_id
),
native_signals as (
  select fp.user_id as author_id, 3::numeric as signal
  from public.post_likes x join public.feed_posts fp on fp.id=x.post_id cross join cfg
  where x.user_id=cfg.caller_id and x.created_at >= cfg.rank_as_of-interval '90 days'
  union all
  select fp.user_id, 4::numeric
  from public.comments x join public.feed_posts fp on fp.id=x.post_id cross join cfg
  where x.author_id=cfg.caller_id and x.created_at >= cfg.rank_as_of-interval '90 days'
  union all
  select fp.user_id, 5::numeric
  from public.post_shares x join public.feed_posts fp on fp.id=x.post_id cross join cfg
  where x.user_id=cfg.caller_id and x.created_at >= cfg.rank_as_of-interval '90 days'
  union all
  select fp.user_id, 4::numeric
  from public.post_bookmarks x join public.feed_posts fp on fp.id=x.post_id cross join cfg
  where x.user_id=cfg.caller_id and x.created_at >= cfg.rank_as_of-interval '90 days'
  union all
  select fp.user_id, least(1::numeric,coalesce(x.view_weight,1)::numeric*0.2)
  from public.post_views x join public.feed_posts fp on fp.id=x.post_id cross join cfg
  where x.viewer_id=cfg.caller_id and x.created_at >= cfg.rank_as_of-interval '30 days'
),
author_affinity as (
  select author_id, least(16::numeric,sum(signal)) as affinity
  from native_signals group by author_id
),
exposure as (
  select
    e.target_key,
    count(*) filter(where e.event_type='impression') as impressions,
    count(*) filter(where e.event_type='skip') as skips
  from public.recommendation_events e cross join cfg
  where e.user_id=cfg.caller_id and e.target_type='post'
    and e.created_at >= cfg.rank_as_of-interval '14 days'
  group by e.target_key
),
candidates as materialized (
  select
    to_jsonb(fp) as item,
    fp.id, fp.user_id, fp.created_at, fp.category, fp.faculty, fp.type,
    fp.is_reel, fp.is_original, fp.report_count,
    fp.like_count, fp.comment_count, fp.share_count, fp.view_count,
    fp.text, fp.caption, fp.image_url, fp.video_url,
    ap.university as author_university,
    ap.faculty as author_faculty,
    ap.department as author_department,
    ap.is_verified as author_verified,
    ap.follower_count as author_followers,
    case when fp.is_reel or nullif(fp.video_url,'') is not null then 'reels' else 'feed' end as item_surface,
    exists(
      select 1 from public.follows f
      where f.follower_id=cfg.caller_id and f.following_id=fp.user_id
    ) as is_followed
  from public.feed_posts fp
  join public.get_blink_ranking_profiles() ap on ap.id=fp.user_id
  cross join cfg
  cross join me
  where cfg.caller_id is not null
    and fp.is_active=true
    and fp.is_flagged=false
    and fp.created_at <= cfg.rank_as_of
    and (fp.expires_at is null or fp.expires_at > cfg.rank_as_of)
    and not exists(
      select 1 from public.blocks b
      where (b.blocker_id=cfg.caller_id and b.blocked_id=fp.user_id)
         or (b.blocker_id=fp.user_id and b.blocked_id=cfg.caller_id)
    )
    and not exists(
      select 1 from public.muted_users mu
      where mu.user_id=cfg.caller_id and mu.muted_id=fp.user_id
    )
    and not exists(
      select 1 from public.feed_preferences pref
      where pref.user_id=cfg.caller_id and pref.post_id=fp.id
        and pref.preference in ('not_interested','hide')
    )
    and case cfg.mode
      when 'reels' then fp.is_reel or nullif(fp.video_url,'') is not null
      when 'following' then
        not(fp.is_reel or nullif(fp.video_url,'') is not null)
        and exists(
          select 1 from public.follows f
          where f.follower_id=cfg.caller_id and f.following_id=fp.user_id
        )
      when 'all' then true
      else not(fp.is_reel or nullif(fp.video_url,'') is not null)
    end
    and (
      fp.user_id=cfg.caller_id
      or lower(coalesce(fp.audience,'everyone')) in ('everyone','public')
      or (
        lower(coalesce(fp.audience,'')) in ('followers','following')
        and exists(
          select 1 from public.follows f
          where f.follower_id=cfg.caller_id and f.following_id=fp.user_id
        )
      )
      or (
        lower(coalesce(fp.audience,''))='university'
        and nullif(lower(me.university),'')=nullif(lower(ap.university),'')
      )
      or (
        lower(coalesce(fp.audience,''))='faculty'
        and nullif(lower(me.faculty),'')=nullif(lower(ap.faculty),'')
      )
    )
  order by fp.created_at desc,fp.id desc
  limit 1500
),
components as (
  select
    c.*,
    30.0 * exp(
      -greatest(0.0,extract(epoch from (cfg.rank_as_of-c.created_at))/3600.0)
      / 96.0
    ) as freshness_component,
    least(
      25.0,
      4.0 * ln(
        1.0 + greatest(0,c.like_count)::double precision
        + 2.0*greatest(0,c.comment_count)::double precision
        + 3.0*greatest(0,c.share_count)::double precision
        + 0.1*greatest(0,c.view_count)::double precision
      )
    ) as engagement_component,
    (case when c.is_followed then 18.0 else 0.0 end)
      + coalesce(aa.affinity,0)::double precision as relationship_component,
    (case when nullif(lower(me.university),'')=nullif(lower(c.author_university),'') then 8.0 else 0.0 end)
      + (case when nullif(lower(me.faculty),'')=nullif(lower(c.author_faculty),'') then 3.0 else 0.0 end)
      + (case when nullif(lower(me.department),'')=nullif(lower(c.author_department),'') then 5.0 else 0.0 end)
      as campus_component,
    least(
      15.0,
      0.75 * coalesce((
        select sum(greatest(iw.weight,0))
        from public.user_interest_weights iw
        where iw.user_id=cfg.caller_id and iw.surface=c.item_surface
          and (
            (iw.feature_type='author' and iw.feature_value=c.user_id::text)
            or (iw.feature_type='category' and iw.feature_value=lower(btrim(coalesce(c.category,''))))
            or (iw.feature_type='faculty' and iw.feature_value=lower(btrim(coalesce(c.faculty,''))))
            or (iw.feature_type='content_type' and iw.feature_value=lower(btrim(coalesce(c.type,''))))
            or (
              iw.feature_type='tag' and exists(
                select 1
                from jsonb_array_elements_text(
                  coalesce(c.item->'tags','[]'::jsonb) || coalesce(c.item->'hashtags','[]'::jsonb)
                ) tag(value)
                where lower(btrim(tag.value))=iw.feature_value
              )
            )
          )
      ),0)::double precision
    ) as interest_component,
    (case when c.is_original then 2.0 else 0.0 end)
      + (case when c.author_verified then 2.0 else 0.0 end)
      + (case when nullif(c.text,'') is not null or nullif(c.caption,'') is not null
                    or nullif(c.image_url,'') is not null or nullif(c.video_url,'') is not null then 2.0 else -6.0 end)
      + (case when coalesce(c.author_followers,0)<100 then 3.0 else 0.0 end)
      - least(12.0,greatest(0,c.report_count)::double precision*2.0)
      as quality_component,
    4.0 * abs(mod(hashtextextended(
      c.id::text || ':' || cfg.caller_id::text || ':' || cfg.rank_as_of::date::text,0
    ),1000))::double precision/1000.0 as exploration_component,
    least(
      12.0,
      coalesce(ex.impressions,0)::double precision*1.5
        + coalesce(ex.skips,0)::double precision*3.0
    ) as exposure_penalty,
    cfg.rank_as_of
  from candidates c
  cross join cfg
  cross join me
  left join author_affinity aa on aa.author_id=c.user_id
  left join exposure ex on ex.target_key=c.id::text
),
base as (
  select c.*,
    (freshness_component + engagement_component + relationship_component
      + campus_component + interest_component + quality_component
      + exploration_component - exposure_penalty)
      * public.creator_organic_distribution_factor(c.id,c.rank_as_of)
      * public.creator_badge_distribution_factor(c.id) as base_score
  from components c
),
diversified as (
  select b.*,
    row_number() over(
      partition by b.user_id,b.item_surface order by b.base_score desc,b.created_at desc,b.id desc
    ) as author_position
  from base b
),
ranked as (
  select d.*,
    round((d.base_score-greatest(0,d.author_position-2)::double precision*6.0)::numeric,6) as final_score
  from diversified d
)
select
  r.item,
  r.final_score,
  case
    when r.is_followed then 'From someone you follow'
    when r.campus_component>=8 then 'Popular on your campus'
    when r.interest_component>=4 then 'Matches your interests'
    when r.engagement_component>=10 then 'Trending now'
    else 'Fresh for you'
  end,
  r.rank_as_of
from ranked r
where p_cursor_score is null
   or r.final_score < p_cursor_score
   or (
     r.final_score=p_cursor_score and p_cursor_created_at is not null
     and (
       r.created_at<p_cursor_created_at
       or (r.created_at=p_cursor_created_at and p_cursor_id is not null and r.id<p_cursor_id)
     )
   )
order by r.final_score desc,r.created_at desc,r.id desc
limit greatest(1,least(coalesce(p_limit,30),60));
$function$;

CREATE OR REPLACE FUNCTION public.get_ranked_game_opponents(p_game_type text DEFAULT 'brain_mix'::text, p_limit integer DEFAULT 20)
 RETURNS TABLE(id uuid, username text, full_name text, avatar_url text, university text, academic_level text, game_score bigint, match_score integer, ranking_reason text)
 LANGUAGE sql
 STABLE
 SET search_path TO ''
AS $function$
with cfg as (
  select auth.uid() as caller_id,lower(btrim(coalesce(p_game_type,''))) as game_type,now() as rank_as_of
),
me as (
  select p.*,coalesce(gp.score,0)::bigint as game_score
  from public.get_blink_ranking_profiles(array[auth.uid()]) p cross join cfg
  left join public.game_profiles gp on gp.user_id=p.id
  where p.id=cfg.caller_id
),
candidates as (
  select p.*,coalesce(gp.score,0)::bigint as candidate_game_score,
    least(10,cardinality(array(
      select distinct lower(btrim(x)) from unnest(coalesce(p.core_skills,'{}'::text[])) x
      where nullif(btrim(x),'') is not null
      intersect
      select distinct lower(btrim(x)) from unnest(coalesce(me.core_skills,'{}'::text[])) x
      where nullif(btrim(x),'') is not null
    ))*3) as shared_skill_points,
    cfg.rank_as_of
  from public.get_blink_ranking_profiles() p cross join cfg cross join me
  left join public.game_profiles gp on gp.user_id=p.id
  where cfg.caller_id is not null and p.id<>cfg.caller_id
    and cfg.game_type in ('brain_mix','math_sprint','logic','memory','word_power','general_knowledge')
    and nullif(btrim(p.username),'') is not null
    and not exists(select 1 from public.blocks b where
      (b.blocker_id=cfg.caller_id and b.blocked_id=p.id) or
      (b.blocker_id=p.id and b.blocked_id=cfg.caller_id))
    and not exists(select 1 from public.muted_users mu where mu.user_id=cfg.caller_id and mu.muted_id=p.id)
    and not exists(
      select 1 from public.game_challenges gc
      where gc.game_type=cfg.game_type and lower(gc.status) in ('pending','accepted','active','in_progress')
        and ((gc.challenger_id=cfg.caller_id and gc.opponent_id=p.id)
          or (gc.challenger_id=p.id and gc.opponent_id=cfg.caller_id))
    )
),
ranked as (
  select c.*,
    least(100,greatest(0,round(
      45.0-least(45.0,5.0*ln(1.0+abs(c.candidate_game_score-me.game_score)::double precision))
      +(case when nullif(lower(me.university),'')=nullif(lower(c.university),'') then 22.0 else 0.0 end)
      +(case when nullif(lower(me.department),'')=nullif(lower(c.department),'') then 8.0 else 0.0 end)
      +c.shared_skill_points::double precision
      +(case when coalesce(c.online_now,c.is_online,false) then 7.0 else 0.0 end)
      +5.0*abs(mod(hashtextextended(
        c.id::text||':'||cfg.caller_id::text||':'||cfg.game_type||':'||cfg.rank_as_of::date::text,0
      ),1000))::double precision/1000.0
    )::integer)) as opponent_score
  from candidates c cross join cfg cross join me
)
select
  r.id,r.username,coalesce(nullif(r.full_name,''),r.name,r.username),r.avatar_url,
  r.university,r.academic_level,r.candidate_game_score,r.opponent_score,
  case
    when abs(r.candidate_game_score-me.game_score)<=100 then 'Close skill match'
    when nullif(lower(me.university),'')=nullif(lower(r.university),'') then 'Challenger on your campus'
    else 'Recommended challenger'
  end
from ranked r cross join me
order by r.opponent_score desc,coalesce(r.online_now,r.is_online,false) desc,r.id
limit greatest(1,least(coalesce(p_limit,20),50));
$function$;

CREATE OR REPLACE FUNCTION public.search_discovery(p_query text, p_types text[] DEFAULT ARRAY['post'::text, 'profile'::text, 'market_item'::text, 'connect_listing'::text], p_limit integer DEFAULT 30, p_cursor_score numeric DEFAULT NULL::numeric, p_cursor_type text DEFAULT NULL::text, p_cursor_id uuid DEFAULT NULL::uuid, p_as_of timestamp with time zone DEFAULT now())
 RETURNS TABLE(result_type text, result_id uuid, payload jsonb, relevance_score numeric, ranking_reason text, as_of timestamp with time zone)
 LANGUAGE sql
 STABLE
 SET search_path TO ''
AS $function$
with requested_types as (
  select coalesce(
    array_agg(distinct lower(btrim(x))) filter(where lower(btrim(x)) in ('post','profile','market_item','connect_listing')),
    array['post','profile','market_item','connect_listing']::text[]
  ) as types
  from unnest(coalesce(p_types,array[]::text[])) x
),
cfg as (
  select
    auth.uid() as caller_id,
    lower(left(btrim(coalesce(p_query,'')),100)) as term,
    case when btrim(coalesce(p_query,''))=''
      then null::tsquery
      else websearch_to_tsquery('simple'::regconfig,left(btrim(p_query),100))
    end as tsq,
    least(coalesce(p_as_of,now()),now()+interval '1 minute') as rank_as_of,
    rt.types
  from requested_types rt
),
me as (
  select p.* from public.get_blink_ranking_profiles(array[auth.uid()]) p cross join cfg where p.id=cfg.caller_id
),
post_results as (
  select
    'post'::text as result_type,
    fp.id as result_id,
    to_jsonb(fp) || jsonb_build_object(
      'author',jsonb_build_object(
        'id',ap.id,'username',ap.username,'full_name',coalesce(nullif(ap.full_name,''),ap.name),
        'avatar_url',ap.avatar_url,'university',ap.university,'faculty',ap.faculty,
        'department',ap.department,'is_verified',ap.is_verified
      )
    ) as payload,
    (
      60.0*ts_rank_cd(d.document,cfg.tsq)::double precision
      +20.0*extensions.similarity(d.document_text,cfg.term)::double precision
      +case when position(cfg.term in lower(coalesce(fp.text,'')||' '||coalesce(fp.caption,'')))=1 then 8.0 else 0.0 end
      +10.0*exp(-greatest(0.0,extract(epoch from(cfg.rank_as_of-fp.created_at))/3600.0)/168.0)
      +least(10.0,2.0*ln(1.0+greatest(0,fp.like_count)+2.0*greatest(0,fp.comment_count)+3.0*greatest(0,fp.share_count)))
      +least(8.0,0.5*coalesce((
        select sum(greatest(iw.weight,0))
        from public.user_interest_weights iw
        where iw.user_id=cfg.caller_id and iw.surface in ('feed','reels','search')
          and ((iw.feature_type='category' and iw.feature_value=lower(btrim(coalesce(fp.category,''))))
            or (iw.feature_type='author' and iw.feature_value=fp.user_id::text))
      ),0)::double precision)
    ) as raw_score,
    case
      when position(cfg.term in lower(coalesce(fp.text,'')||' '||coalesce(fp.caption,'')))>0 then 'Matching post'
      when nullif(lower(me.university),'')=nullif(lower(ap.university),'') then 'Campus result'
      else 'Relevant post'
    end as reason,
    fp.created_at
  from public.feed_posts fp
  join public.get_blink_ranking_profiles() ap on ap.id=fp.user_id
  cross join cfg cross join me
  cross join lateral (
    select
      to_tsvector('simple'::regconfig,
        coalesce(fp.text,'')||' '||coalesce(fp.caption,'')||' '||
        coalesce(fp.category,'')||' '||coalesce(fp.faculty,'')) as document,
      lower(coalesce(fp.text,'')||' '||coalesce(fp.caption,'')||' '||
        coalesce(fp.category,'')||' '||coalesce(fp.faculty,'')) as document_text
  ) d
  where cfg.caller_id is not null and cfg.term<>'' and 'post'=any(cfg.types)
    and fp.is_active and not fp.is_flagged and fp.created_at<=cfg.rank_as_of
    and (fp.expires_at is null or fp.expires_at>cfg.rank_as_of)
    and not exists(
      select 1 from public.blocks b
      where (b.blocker_id=cfg.caller_id and b.blocked_id=fp.user_id)
         or (b.blocker_id=fp.user_id and b.blocked_id=cfg.caller_id)
    )
    and not exists(
      select 1 from public.muted_users mu where mu.user_id=cfg.caller_id and mu.muted_id=fp.user_id
    )
    and (
      d.document@@cfg.tsq or position(cfg.term in d.document_text)>0
      or extensions.similarity(d.document_text,cfg.term)>=0.10
      or exists(
        select 1 from unnest(coalesce(fp.tags,'{}'::text[])||coalesce(fp.hashtags,'{}'::text[])) tag
        where position(cfg.term in lower(tag))>0
      )
    )
),
profile_results as (
  select
    'profile'::text,
    p.id,
    jsonb_build_object(
      'id',p.id,'username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),
      'avatar_url',p.avatar_url,'university',p.university,'faculty',p.faculty,
      'department',p.department,'academic_level',p.academic_level,
      'professional_headline',p.professional_headline,'bio',p.bio,
      'is_verified',p.is_verified,'verification_badge',p.verification_badge,
      'online_now',p.online_now,'last_seen_at',p.last_seen_at
    ),
    (
      75.0*ts_rank_cd(d.document,cfg.tsq)::double precision
      +25.0*greatest(
        extensions.similarity(lower(coalesce(p.username,'')),cfg.term),
        extensions.similarity(lower(coalesce(p.full_name,'')),cfg.term)
      )::double precision
      +case when lower(btrim(coalesce(p.username,'')))=cfg.term then 25.0 else 0.0 end
      +case when position(cfg.term in lower(coalesce(p.username,'')))=1 then 10.0 else 0.0 end
      +case when nullif(lower(me.university),'')=nullif(lower(p.university),'') then 8.0 else 0.0 end
      +case when nullif(lower(me.department),'')=nullif(lower(p.department),'') then 5.0 else 0.0 end
      +case when p.is_verified then 3.0 else 0.0 end
      +case when p.online_now then 2.0 else 0.0 end
      +least(6.0,0.45*coalesce((
        select sum(greatest(iw.weight,0))
        from public.user_interest_weights iw
        where iw.user_id=cfg.caller_id and iw.surface in ('connect','search')
          and ((iw.feature_type='university' and iw.feature_value=lower(btrim(coalesce(p.university,''))))
            or (iw.feature_type='department' and iw.feature_value=lower(btrim(coalesce(p.department,'')))))
      ),0)::double precision)
    ),
    case
      when lower(btrim(coalesce(p.username,'')))=cfg.term then 'Exact username'
      when nullif(lower(me.department),'')=nullif(lower(p.department),'') then 'Same department'
      when nullif(lower(me.university),'')=nullif(lower(p.university),'') then 'Same university'
      else 'Matching profile'
    end,
    p.created_at
  from public.get_blink_ranking_profiles() p
  cross join cfg cross join me
  cross join lateral (
    select
      to_tsvector('simple'::regconfig,
        coalesce(p.username,'')||' '||coalesce(p.full_name,'')||' '||coalesce(p.name,'')||' '||
        coalesce(p.university,'')||' '||coalesce(p.faculty,'')||' '||coalesce(p.department,'')||' '||
        coalesce(p.bio,'')) as document,
      lower(coalesce(p.username,'')||' '||coalesce(p.full_name,'')||' '||coalesce(p.name,'')||' '||
        coalesce(p.university,'')||' '||coalesce(p.faculty,'')||' '||coalesce(p.department,'')||' '||
        coalesce(p.bio,'')) as document_text
  ) d
  where cfg.caller_id is not null and cfg.term<>'' and 'profile'=any(cfg.types)
    and nullif(btrim(p.username),'') is not null
    and not exists(
      select 1 from public.blocks b
      where (b.blocker_id=cfg.caller_id and b.blocked_id=p.id)
         or (b.blocker_id=p.id and b.blocked_id=cfg.caller_id)
    )
    and (
      d.document@@cfg.tsq or position(cfg.term in d.document_text)>0
      or extensions.similarity(lower(coalesce(p.username,'')),cfg.term)>=0.10
      or extensions.similarity(lower(coalesce(p.full_name,'')),cfg.term)>=0.10
    )
),
market_results as (
  select
    'market_item'::text,
    mi.id,
    jsonb_build_object(
      'id',mi.id,'title',mi.title,'description',mi.description,'price',mi.price,
      'currency',mi.currency,'category',mi.category,'condition',mi.condition,
      'image_url',mi.image_url,'image_urls',mi.image_urls,'location',mi.location,
      'university',mi.university,'seller_id',mi.seller_id,'seller_name',mi.seller_name,
      'seller_username',mi.seller_username,'seller_avatar',mi.seller_avatar,
      'seller_is_verified',mi.seller_is_verified,'seller_rating',mi.seller_rating,
      'created_at',mi.created_at
    ),
    (
      65.0*ts_rank_cd(d.document,cfg.tsq)::double precision
      +20.0*extensions.similarity(lower(coalesce(mi.title,'')),cfg.term)::double precision
      +case when lower(btrim(mi.title))=cfg.term then 20.0 else 0.0 end
      +case when position(cfg.term in lower(mi.title))=1 then 8.0 else 0.0 end
      +case when nullif(lower(me.university),'')=nullif(lower(mi.university),'') then 8.0 else 0.0 end
      +case when mi.seller_is_verified then 3.0 else 0.0 end
      +8.0*exp(-greatest(0.0,extract(epoch from(cfg.rank_as_of-mi.created_at))/3600.0)/336.0)
      +least(7.0,0.45*coalesce((
        select sum(greatest(iw.weight,0))
        from public.user_interest_weights iw
        where iw.user_id=cfg.caller_id and iw.surface in ('market','search')
          and iw.feature_type='category' and iw.feature_value=lower(btrim(mi.category))
      ),0)::double precision)
    ),
    case when lower(btrim(mi.title))=cfg.term then 'Exact product'
      when nullif(lower(me.university),'')=nullif(lower(mi.university),'') then 'On your campus'
      else 'Matching market item' end,
    mi.created_at
  from public.market_items mi
  cross join cfg cross join me
  cross join lateral (
    select
      to_tsvector('simple'::regconfig,
        coalesce(mi.title,'')||' '||coalesce(mi.description,'')||' '||coalesce(mi.category,'')||' '||
        coalesce(mi.location,'')||' '||coalesce(mi.university,'')) as document,
      lower(coalesce(mi.title,'')||' '||coalesce(mi.description,'')||' '||coalesce(mi.category,'')||' '||
        coalesce(mi.location,'')||' '||coalesce(mi.university,'')) as document_text
  ) d
  where cfg.caller_id is not null and cfg.term<>'' and 'market_item'=any(cfg.types)
    and mi.status='active' and not mi.is_sold and mi.created_at<=cfg.rank_as_of
    and (d.document@@cfg.tsq or position(cfg.term in d.document_text)>0
      or extensions.similarity(lower(coalesce(mi.title,'')),cfg.term)>=0.10)
),
connect_results as (
  select
    'connect_listing'::text,
    cl.id,
    to_jsonb(cl)||jsonb_build_object(
      'profile',jsonb_build_object(
        'id',p.id,'username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),
        'avatar_url',p.avatar_url,'university',p.university,'faculty',p.faculty,
        'department',p.department,'academic_level',p.academic_level,'online_now',p.online_now
      )
    ),
    (
      65.0*ts_rank_cd(d.document,cfg.tsq)::double precision
      +20.0*extensions.similarity(d.document_text,cfg.term)::double precision
      +case when position(cfg.term in lower(cl.title))=1 then 10.0 else 0.0 end
      +case when nullif(lower(me.university),'')=nullif(lower(cl.university),'') then 8.0 else 0.0 end
      +case when nullif(lower(me.department),'')=nullif(lower(cl.department),'') then 6.0 else 0.0 end
      +8.0*exp(-greatest(0.0,extract(epoch from(cfg.rank_as_of-cl.updated_at))/3600.0)/336.0)
    ),
    case when nullif(lower(me.department),'')=nullif(lower(cl.department),'') then 'Same department'
      when nullif(lower(me.university),'')=nullif(lower(cl.university),'') then 'Same university'
      else 'Matching Connect opportunity' end,
    cl.created_at
  from public.connect_listings cl
  join public.get_blink_ranking_profiles() p on p.id=cl.user_id
  cross join cfg cross join me
  cross join lateral (
    select
      to_tsvector('simple'::regconfig,
        coalesce(cl.title,'')||' '||coalesce(cl.description,'')||' '||coalesce(cl.university,'')||' '||
        coalesce(cl.department,'')||' '||coalesce(cl.location,'')) as document,
      lower(coalesce(cl.title,'')||' '||coalesce(cl.description,'')||' '||coalesce(cl.university,'')||' '||
        coalesce(cl.department,'')||' '||coalesce(cl.location,'')) as document_text
  ) d
  where cfg.caller_id is not null and cfg.term<>'' and 'connect_listing'=any(cfg.types)
    and cl.is_active and cl.user_id<>cfg.caller_id
    and not exists(
      select 1 from public.blocks b
      where (b.blocker_id=cfg.caller_id and b.blocked_id=cl.user_id)
         or (b.blocker_id=cl.user_id and b.blocked_id=cfg.caller_id)
    )
    and (d.document@@cfg.tsq or position(cfg.term in d.document_text)>0
      or extensions.similarity(d.document_text,cfg.term)>=0.10
      or exists(
        select 1 from unnest(coalesce(cl.tags,'{}'::text[])||coalesce(cl.subjects,'{}'::text[])) tag
        where position(cfg.term in lower(tag))>0
      ))
),
raw as (
  select * from post_results
  union all select * from profile_results
  union all select * from market_results
  union all select * from connect_results
),
diversified as (
  select r.*,
    row_number() over(partition by r.result_type order by r.raw_score desc,r.created_at desc,r.result_id) as type_position
  from raw r
),
ranked as (
  select d.*,
    round((d.raw_score-greatest(0,d.type_position-4)::double precision*2.5)::numeric,6) as final_score
  from diversified d
)
select r.result_type,r.result_id,r.payload,r.final_score,r.reason,cfg.rank_as_of
from ranked r cross join cfg
where p_cursor_score is null
   or r.final_score<p_cursor_score
   or (r.final_score=p_cursor_score and p_cursor_type is not null and (
     r.result_type>p_cursor_type
     or (r.result_type=p_cursor_type and p_cursor_id is not null and r.result_id>p_cursor_id)
   ))
order by r.final_score desc,r.result_type,r.result_id
limit greatest(1,least(coalesce(p_limit,30),60));
$function$;


notify pgrst, 'reload schema';
commit;
