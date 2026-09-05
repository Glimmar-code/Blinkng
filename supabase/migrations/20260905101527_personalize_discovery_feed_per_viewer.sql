create or replace function private_ranking.viewer_personalization_bonus(
  p_viewer uuid,
  p_post uuid,
  p_creator uuid,
  p_as_of timestamptz
)
returns numeric
language sql
stable
security invoker
set search_path = ''
as $$
with viewer as (
  select id, university, faculty, department
  from public.profiles
  where id = p_viewer
),
creator as (
  select id, university, faculty, department
  from public.profiles
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
$$;

revoke all on function private_ranking.viewer_personalization_bonus(uuid,uuid,uuid,timestamptz) from public;

-- Inject the per-viewer bonus into the existing proven discovery model without
-- changing its 3:1 post/reel mixing, new-creator reservation, decay, or creator guards.
do $$
declare
  v_oid oid;
  v_def text;
  v_old text := ') * c.verification_multiplier * c.cold_start_multiplier as final_score';
  v_new text := ') * c.verification_multiplier * c.cold_start_multiplier + private_ranking.viewer_personalization_bonus(v_user,c.post_id,c.creator_id,p_as_of) as final_score';
begin
  select p.oid into v_oid
  from pg_proc p
  join pg_namespace n on n.oid=p.pronamespace
  where n.nspname='private_ranking'
    and p.proname='get_discovery_feed'
    and pg_get_function_identity_arguments(p.oid) = 'p_limit integer, p_offset integer, p_as_of timestamp with time zone, p_gravity numeric, p_w1 numeric, p_w2 numeric, p_w3 numeric, p_w4 numeric';

  if v_oid is null then
    raise exception 'private_ranking.get_discovery_feed signature not found';
  end if;

  v_def := pg_get_functiondef(v_oid);
  if position(v_old in v_def)=0 then
    raise exception 'Expected discovery scoring expression was not found';
  end if;

  v_def := replace(v_def,v_old,v_new);
  execute v_def;
end $$;

-- Keep the internal scorer private. Public access remains through the authenticated wrapper.
revoke all on function private_ranking.get_discovery_feed(integer,integer,timestamptz,numeric,numeric,numeric,numeric,numeric) from public;
grant execute on function private_ranking.get_discovery_feed(integer,integer,timestamptz,numeric,numeric,numeric,numeric,numeric) to authenticated;
