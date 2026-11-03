-- Referral discovery impression reporting (STAGED ONLY).
-- Clients report a visible card after viewability checks; never award Coins or
-- permanent referrals from these client-reported, approximate events.
create table if not exists public.blink_referral_discovery_impressions (
  viewer_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null references public.profiles(id) on delete cascade,
  source text not null check(source in ('search','recommendations')),
  impression_day date not null default (now() at time zone 'utc')::date,
  created_at timestamptz not null default now(),
  primary key(viewer_id,profile_id,source,impression_day),
  constraint blink_referral_impression_no_self check (viewer_id<>profile_id)
);
create index if not exists blink_referral_impressions_profile_date
  on public.blink_referral_discovery_impressions(profile_id,created_at desc,source);
alter table public.blink_referral_discovery_impressions enable row level security;
revoke all on public.blink_referral_discovery_impressions
  from public,anon,authenticated,service_role;
grant select on public.blink_referral_discovery_impressions to service_role;
-- No client raw-access to viewer histories.

create or replace function public.blink_record_referral_impression(
  p_profile uuid,p_source text
) returns boolean language plpgsql security definer set search_path=''
as $$
declare
  viewer uuid:=(select auth.uid());
begin
  if viewer is null or p_profile is null or p_profile=viewer
     or p_source not in ('search','recommendations') then return false; end if;
  if not exists(
    select 1 from public.profiles p
    where p.id=p_profile and p.onboarding_completed=true
      and not exists(select 1 from public.user_settings u
        where u.user_id=p.id and u.private_account=true)
      and not exists(select 1 from public.blocks b
        where (b.blocker_id=viewer and b.blocked_id=p_profile)
           or (b.blocker_id=p_profile and b.blocked_id=viewer))
  ) then return false; end if;
  if (select count(*) from public.blink_referral_discovery_impressions i
      where i.viewer_id=viewer and i.created_at>now()-interval '1 hour')>=60
  then return false; end if;
  insert into public.blink_referral_discovery_impressions(viewer_id,profile_id,source)
  values(viewer,p_profile,p_source) on conflict do nothing;
  return found;
end $$;
revoke all on function public.blink_record_referral_impression(uuid,text)
  from public,anon,authenticated;
grant execute on function public.blink_record_referral_impression(uuid,text)
  to authenticated;

create or replace function public.blink_referral_impression_stats()
returns table(search_impressions_7d bigint,recommendation_impressions_7d bigint)
language sql stable security definer set search_path=''
as $$
select count(*) filter(where i.source='search')::bigint,
       count(*) filter(where i.source='recommendations')::bigint
from public.blink_referral_discovery_impressions i
where i.profile_id=(select auth.uid())
  and i.created_at>=now()-interval '7 days';
$$;
revoke all on function public.blink_referral_impression_stats()
  from public,anon,authenticated;
grant execute on function public.blink_referral_impression_stats()
  to authenticated;
