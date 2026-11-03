-- Referral growth fraud containment. All RPCs keep their existing signatures.
-- Pauses only temporary Search/recommendation advantage while an abuse review is
-- unresolved. Achievements and existing legitimate confirmed counts are preserved.
-- This does NOT activate referral Coin milestone rewards.
begin;
-- A fraud review pauses *temporary* discovery boosts without deleting durable
-- achievements or revealing risk-review details to other students.
create or replace function public.blink_my_referral_visibility_paused()
returns boolean
language sql stable security definer set search_path=''
as $$
  select (select auth.uid()) is not null and exists (
    select 1 from private.blink_referral_risk_reviews v
     where v.referrer_user_id = (select auth.uid()) and v.resolved_at is null
  );
$$;
revoke all on function public.blink_my_referral_visibility_paused() from public,anon,authenticated;
grant execute on function public.blink_my_referral_visibility_paused() to authenticated;


create or replace function public.blink_referral_dashboard()
returns table(
  total_referrals bigint, active_referrals bigint, visibility_points integer,
  visibility_label text, achievement_tier text, achievement_next_target integer
)
language sql stable security invoker set search_path = ''
as $$
  with viewer as (select auth.uid() as user_id),
  stats as (
    select coalesce(a.confirmed_referrals,0) as total,
           count(r.referred_user_id) filter (
             where r.status='confirmed'
               and r.confirmed_at <= now()
               and r.confirmed_at > now() - interval '30 days'
           ) as active,
           least(30,coalesce(sum(
             case when r.status <> 'confirmed' or r.confirmed_at > now() then 0
                  when r.confirmed_at > now()-interval '10 days' then 3
                  when r.confirmed_at > now()-interval '20 days' then 2
                  when r.confirmed_at > now()-interval '30 days' then 1
                  else 0 end
           ),0)::integer) as points
    from viewer v
    left join public.blink_referral_achievements a on a.user_id=v.user_id
    left join public.blink_referrals r on r.referrer_user_id=v.user_id
    where v.user_id is not null
    group by a.confirmed_referrals
  )
  select total, active,
         case when public.blink_my_referral_visibility_paused() then 0 else points end,
         case
           when public.blink_my_referral_visibility_paused() then 'Discovery paused for review'
           when points >= 24 then 'High visibility'
           when points >= 15 then 'Enhanced discovery'
           when points >= 6 then 'Growing visibility'
           when points >= 1 then 'Discovery boost active'
           else 'Normal discovery'
         end,
         case
           when total >= 100 then 'Ambassador'
           when total >= 50 then 'Champion'
           when total >= 25 then 'Connector'
           when total >= 10 then 'Advocate'
           when total >= 5 then 'Builder'
           when total >= 1 then 'Starter'
           else 'New member'
         end,
         case
           when total >= 100 then null::integer
           when total >= 50 then 100
           when total >= 25 then 50
           when total >= 10 then 25
           when total >= 5 then 10
           when total >= 1 then 5
           else 1
         end
  from stats;
$$;
revoke all on function public.blink_referral_dashboard() from public, anon;
grant execute on function public.blink_referral_dashboard() to authenticated;

create or replace function public.blink_referral_recommendations(p_limit integer default 10)
returns table(id uuid, username text, full_name text, avatar_url text, university text,
              referral_featured boolean, is_verified boolean, verification_badge text)
language sql stable security definer set search_path = ''
as $$
  with viewer as (
    select p.id, p.university from public.profiles p where p.id = (select auth.uid())
  ),
  active as (
    select r.referrer_user_id,
           least(30,sum(
             case
               when r.confirmed_at > now() then 0
               when r.confirmed_at > now()-interval '10 days' then 3
               when r.confirmed_at > now()-interval '20 days' then 2
               when r.confirmed_at > now()-interval '30 days' then 1
               else 0
             end
           )::integer) as visibility_points
    from public.blink_referrals r
    where r.status = 'confirmed' and r.confirmed_at > now() - interval '30 days'
      and not exists (
        select 1 from private.blink_referral_risk_reviews flagged
        where flagged.referrer_user_id=r.referrer_user_id and flagged.resolved_at is null
      )
    group by r.referrer_user_id
  ),
  candidates as (
    select p.id, p.username, p.full_name, p.avatar_url, p.university,
           coalesce(p.is_verified,false) as is_verified,
           coalesce(p.verification_badge,'') as verification_badge,
           coalesce(a.visibility_points,0) as visibility_points
    from public.profiles p cross join viewer v
    left join active a on a.referrer_user_id = p.id
    where p.id <> v.id and p.onboarding_completed is true
      and not exists (
        select 1 from public.user_settings settings
        where settings.user_id = p.id and settings.private_account is true
      )
      and nullif(trim(p.username),'') is not null
      and (v.university is null or v.university = '' or p.university = v.university)
      and not exists (
        select 1 from public.blocks b
        where (b.blocker_id = v.id and b.blocked_id = p.id)
           or (b.blocker_id = p.id and b.blocked_id = v.id)
      )
  ),
  boosted as (
    select *, true as featured from candidates
    where visibility_points > 0
    order by visibility_points desc, id
    limit 3
  ),
  organic as (
    select *, false as featured from candidates c
    where not exists (select 1 from boosted b where b.id = c.id)
    order by id
    limit greatest(1,least(coalesce(p_limit,10),10))
  )
  select id, username, full_name, avatar_url, university, featured,
         is_verified, verification_badge
  from (
    select boosted.*, 0 as priority from boosted
    union all
    select organic.*, 1 as priority from organic
  ) results
  order by priority, visibility_points desc, id
  limit greatest(1,least(coalesce(p_limit,10),10));
$$;
revoke all on function public.blink_referral_recommendations(integer) from public, anon;
grant execute on function public.blink_referral_recommendations(integer) to authenticated;

create or replace function public.blink_referral_profile_search(
  p_query text, p_limit integer default 20
)
returns table(id uuid, username text, full_name text, avatar_url text,
              university text, is_verified boolean, verification_badge text,
              visibility_points integer)
language sql stable security definer set search_path = ''
as $$
  with term as (
    select lower(regexp_replace(trim(coalesce(p_query,'')), '^@', '')) as q
  ),
  viewer as (
    select p.id, p.university from public.profiles p where p.id = (select auth.uid())
  ),
  boost as (
    select referrer_user_id,
           least(30, sum(case
             when confirmed_at > now() then 0
             when confirmed_at > now()-interval '10 days' then 3
             when confirmed_at > now()-interval '20 days' then 2
             when confirmed_at > now()-interval '30 days' then 1
             else 0 end))::integer as points
    from public.blink_referrals r
    where r.status = 'confirmed' and r.confirmed_at > now()-interval '30 days'
      and not exists (
        select 1 from private.blink_referral_risk_reviews flagged
        where flagged.referrer_user_id=r.referrer_user_id and flagged.resolved_at is null
      )
    group by r.referrer_user_id
  ),
  relevant as (
    select p.id,p.username,p.full_name,p.avatar_url,p.university,
           coalesce(p.is_verified,false) as is_verified,
           coalesce(p.verification_badge,'') as verification_badge,
           coalesce(b.points,0) as visibility_points,
           case
             when lower(p.username) = t.q then 0
             when starts_with(lower(p.username), t.q) then 1
             when starts_with(lower(coalesce(p.full_name,'')), t.q) then 2
             when position(t.q in lower(p.username)) > 0 then 3
             else 4 end as relevance,
           case when p.university=v.university then 0 else 1 end as campus_tie
    from public.profiles p
    cross join term t
    cross join viewer v
    left join boost b on b.referrer_user_id=p.id
    where length(t.q) between 1 and 40 and p.onboarding_completed is true
      and p.id <> v.id
      and (position(t.q in lower(p.username)) > 0 or
           position(t.q in lower(coalesce(p.full_name,''))) > 0)
      and not exists (
        select 1 from public.user_settings st
        where st.user_id=p.id and st.private_account is true
      )
      and not exists (
        select 1 from public.blocks bl
        where (bl.blocker_id=v.id and bl.blocked_id=p.id)
           or (bl.blocked_id=v.id and bl.blocker_id=p.id)
      )
  )
  select id,username,full_name,avatar_url,university,is_verified,
         verification_badge,visibility_points
    from relevant
   order by relevance,campus_tie,visibility_points desc,lower(username),id
   limit greatest(1,least(coalesce(p_limit,20),20));
$$;
revoke all on function public.blink_referral_profile_search(text,integer) from public, anon;
grant execute on function public.blink_referral_profile_search(text,integer) to authenticated;

commit;
