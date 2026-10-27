-- STAGING-ONLY. BLINK 30-day hashtags and ranked trending creators.
-- Not a versioned production migration. Validate against an isolated compatible
-- PostgreSQL fixture and perform live migration reviews before generating one
-- using the Supabase CLI. No production writes during this audit.
-- One public post is counted once per hashtag. Caller must be authenticated.
-- Sensitive/private audience posts and mutually blocked creators are excluded.
begin;

create or replace function public.blink_trending_tags_30d(
  p_limit integer default 5, p_university text default null
) returns table(
  tag text, trend_score numeric, post_count bigint,
  search_users bigint, campus_posts bigint
)
language sql stable security definer set search_path = ''
as $sql$
 with eligible as (
   select fp.id, fp.user_id, fp.created_at,
     greatest(coalesce(fp.like_count,0),0) likes,
     greatest(coalesce(fp.comment_count,0),0) comments,
     greatest(coalesce(fp.share_count,0),0) shares,
     greatest(coalesce(fp.view_count,0),0) views,
     fp.tags, fp.hashtags, pr.university
   from public.feed_posts fp
   join public.profiles pr on pr.id=fp.user_id
   where (select auth.uid()) is not null
     and fp.is_active is true
     and coalesce(fp.is_flagged,false) is false
     and lower(coalesce(fp.audience,'everyone')) in ('everyone','public')
     and fp.created_at >= now()-interval '30 days'
     and (fp.expires_at is null or fp.expires_at > now())
     and not exists(
       select 1 from public.blocks b
       where (b.blocker_id=(select auth.uid()) and b.blocked_id=fp.user_id)
          or (b.blocked_id=(select auth.uid()) and b.blocker_id=fp.user_id)
     )
 ), normalized as (
   select e.*, raw.tag
   from eligible e
   cross join lateral (
     select distinct lower(trim(leading '#' from btrim(val))) tag
     from unnest(coalesce(e.tags,'{}'::text[]) ||
                 coalesce(e.hashtags,'{}'::text[])) vals(val)
   ) raw
   where raw.tag ~ '^[a-z0-9_]{2,32}$'
 ), aggregate_posts as (
   select tag, count(distinct id) post_count, count(distinct user_id) creators,
     count(*) filter(where created_at >= now()-interval '24 hours') new_posts,
     count(*) filter(where created_at >= now()-interval '1 hour') fresh_posts,
     coalesce(sum(least(likes,100)+least(comments*2,100)+
                      least(shares*3,100)+least(views/10,50)),0) engagement,
     count(*) filter(where nullif(btrim(p_university),'') is not null
       and lower(university)=lower(btrim(p_university))) campus_posts
   from normalized group by tag
 ), searches as (
   select tse.tag, count(distinct tse.user_id) search_users
   from public.blink_tag_search_events tse
   where tse.searched_hour >= now()-interval '30 days'
   group by tse.tag
 )
 select a.tag,
   round(30*least(1.0,a.new_posts::numeric/10)
       +25*least(1.0,coalesce(s.search_users,0)::numeric/150)
       +20*least(1.0,a.engagement::numeric/1200)
       +15*least(1.0,a.creators::numeric/25)
       +5*least(1.0,a.fresh_posts::numeric/10)
       +5*least(1.0,a.campus_posts::numeric/10),2) trend_score,
   a.post_count,coalesce(s.search_users,0),a.campus_posts
 from aggregate_posts a left join searches s on s.tag=a.tag
 where a.post_count>0
 order by trend_score desc,a.post_count desc,a.tag
 limit least(greatest(coalesce(p_limit,5),1),50)
$sql$;
revoke all on function public.blink_trending_tags_30d(integer,text)
  from public, anon, authenticated;
grant execute on function public.blink_trending_tags_30d(integer,text)
  to authenticated;

create or replace function public.blink_trending_creators_30d(
  p_limit integer default 3,p_university text default null
) returns table(
  id uuid,username text,full_name text,avatar_url text,university text,
  is_verified boolean,trend_score numeric,post_count bigint
)
language sql stable security definer set search_path = ''
as $sql$
 with visible as (
  select fp.user_id,
    greatest(coalesce(fp.view_count,0),0) views,
    greatest(coalesce(fp.like_count,0),0) likes,
    greatest(coalesce(fp.comment_count,0),0) comments,
    greatest(coalesce(fp.share_count,0),0) shares
  from public.feed_posts fp
  where (select auth.uid()) is not null
    and fp.is_active is true
    and coalesce(fp.is_flagged,false) is false
    and lower(coalesce(fp.audience,'everyone')) in ('everyone','public')
    and fp.created_at >= now()-interval '30 days'
     and (fp.expires_at is null or fp.expires_at > now())
    and fp.user_id <> (select auth.uid())
    and not exists (
      select 1 from public.blocks b
      where (b.blocker_id=(select auth.uid()) and b.blocked_id=fp.user_id)
         or (b.blocked_id=(select auth.uid()) and b.blocker_id=fp.user_id)
    )
 )
 select p.id,p.username,p.full_name,p.avatar_url,p.university,
   coalesce(p.is_verified,false),
   round(sum(least(v.views,10000)*0.01
     +least(v.likes,200)*2
     +least(v.comments,100)*3
     +least(v.shares,100)*4)::numeric,2) trend_score,
   count(*) post_count
 from visible v join public.profiles p on p.id=v.user_id
 where p.username is not null and length(btrim(p.username))>0
   and coalesce(p.onboarding_completed,false) is true
   and (nullif(btrim(p_university),'') is null
        or lower(p.university)=lower(btrim(p_university)))
 group by p.id,p.username,p.full_name,p.avatar_url,p.university,p.is_verified
 order by trend_score desc,post_count desc,p.username
 limit least(greatest(coalesce(p_limit,3),1),3)
$sql$;
revoke all on function public.blink_trending_creators_30d(integer,text)
  from public, anon, authenticated;
grant execute on function public.blink_trending_creators_30d(integer,text)
  to authenticated;
commit;
