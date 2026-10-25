-- Referral completion phase. Disposable-Postgres tested; never production-first.
-- Critical: milestone Coin rewards start DISABLED until owner approves an economy budget
-- and hardened anti-abuse checks. Config is private; clients cannot mint Coins.
create schema if not exists private;

create table if not exists private.blink_referral_reward_policy (
  milestone integer primary key check (milestone in (1,5,10,25,50,100)),
  coin_reward integer not null check (coin_reward between 0 and 1000),
  enabled boolean not null default false
);
revoke all on private.blink_referral_reward_policy from public,anon,authenticated;
insert into private.blink_referral_reward_policy(milestone,coin_reward,enabled)
values (1,5,false),(5,25,false),(10,50,false),
       (25,100,false),(50,250,false),(100,500,false)
on conflict(milestone) do nothing;

create table if not exists public.blink_referral_coin_milestones (
  user_id uuid not null references public.profiles(id) on delete cascade,
  milestone integer not null,
  coin_amount integer not null check(coin_amount>0),
  granted_at timestamptz not null default now(),
  primary key(user_id,milestone)
);
alter table public.blink_referral_coin_milestones enable row level security;
revoke all on public.blink_referral_coin_milestones from public,anon,authenticated,service_role;
grant select on public.blink_referral_coin_milestones to authenticated,service_role;
drop policy if exists blink_referral_coin_milestones_owner on public.blink_referral_coin_milestones;
create policy blink_referral_coin_milestones_owner
  on public.blink_referral_coin_milestones for select to authenticated
  using(user_id=(select auth.uid()));

create or replace function private.blink_award_referral_milestones()
returns trigger language plpgsql security definer set search_path=''
as $$
declare
  old_count bigint := case when tg_op='INSERT' then 0 else old.confirmed_referrals end;
  reward record;
  credited_user uuid;
  balance_after bigint;
begin
  if new.confirmed_referrals <= old_count then return new; end if;
  -- Check the actual confirmed-referral velocity BEFORE creating any Coin ledger entry.
  -- The later risk flag trigger alone runs too late to prevent the fifth payout.
  if (select count(*) from public.blink_referrals r
      where r.referrer_user_id=new.user_id and r.status='confirmed'
        and r.confirmed_at>now()-interval '1 hour')>=5
  then return new; end if;
  -- During deployment the velocity-review extension might not exist yet.
  -- Once present, hold Coin minting for suspicious bursts pending human review.
  if to_regclass('private.blink_referral_risk_reviews') is not null then
    if exists (
      select 1 from private.blink_referral_risk_reviews review
       where review.referrer_user_id=new.user_id and review.resolved_at is null
    ) then return new; end if;
  end if;
  -- Advisory serializes concurrent awards; the unique primary key prevents retries.
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(new.user_id::text));
  for reward in
    select milestone,coin_reward from private.blink_referral_reward_policy
     where enabled and coin_reward>0 and milestone>old_count
       and milestone<=new.confirmed_referrals order by milestone
  loop
    insert into public.blink_referral_coin_milestones(user_id,milestone,coin_amount)
    values(new.user_id,reward.milestone,reward.coin_reward)
    on conflict(user_id,milestone) do nothing
    returning user_id into credited_user;
    if credited_user is not null then
      insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
      values(new.user_id,0,now()) on conflict(user_id) do nothing;
      update public.user_balances
         set spendable_coin_balance=spendable_coin_balance+reward.coin_reward,
             updated_at=now()
       where user_id=new.user_id
       returning floor(spendable_coin_balance)::bigint into balance_after;
      insert into public.blink_coin_transactions(
        user_id,kind,item_name,amount,balance_after,metadata
      ) values(
        new.user_id,'referral_milestone',
        'Referral milestone '||reward.milestone,
        reward.coin_reward,balance_after,
        pg_catalog.jsonb_build_object('milestone',reward.milestone,'source','confirmed_referrals')
      );
    end if;
    credited_user := null;
  end loop;
  return new;
end
$$;
revoke all on function private.blink_award_referral_milestones() from public,anon,authenticated;
drop trigger if exists zz_blink_referral_coin_rewards on public.blink_referral_achievements;
create trigger zz_blink_referral_coin_rewards
after insert or update of confirmed_referrals on public.blink_referral_achievements
for each row execute function private.blink_award_referral_milestones();

-- No client-write privileges and no public visibility into policy amounts.
create or replace function public.blink_referral_coin_status()
returns table(earned_coins bigint, next_milestone integer,
              reward_enabled boolean, next_reward_coins integer)
language sql stable security definer set search_path=''
as $$
  select
    (select coalesce(sum(m.coin_amount),0)::bigint
     from public.blink_referral_coin_milestones m
     where m.user_id=(select auth.uid())),
    (select min(x.milestone) from private.blink_referral_reward_policy x
     where x.milestone>coalesce(a.confirmed_referrals,0)),
    coalesce((select bool_or(x.enabled) from private.blink_referral_reward_policy x),false),
    (select x.coin_reward from private.blink_referral_reward_policy x
     where x.milestone>coalesce(a.confirmed_referrals,0)
     order by x.milestone limit 1)
  from (select auth.uid() as id) viewer
  left join public.blink_referral_achievements a on a.user_id=viewer.id
  where viewer.id is not null;
$$;
revoke all on function public.blink_referral_coin_status() from public,anon;
grant execute on function public.blink_referral_coin_status() to authenticated;

-- Count first-party profile opens attributed to Search or new-member recommendations.
-- A deduplicated count avoids displaying fake impressions or rewarding repeated taps.
create table if not exists public.blink_referral_discovery_opens (
  viewer_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null references public.profiles(id) on delete cascade,
  source text not null check(source in ('search','recommendations')),
  opened_day date not null default (now() at time zone 'utc')::date,
  created_at timestamptz not null default now(),
  primary key(viewer_id,profile_id,source,opened_day),
  constraint referral_discovery_no_self check(viewer_id<>profile_id)
);
create index if not exists blink_referral_discovery_opens_profile_week_idx
  on public.blink_referral_discovery_opens(profile_id,created_at desc,source);
alter table public.blink_referral_discovery_opens enable row level security;
revoke all on public.blink_referral_discovery_opens from public,anon,authenticated,service_role;
grant select on public.blink_referral_discovery_opens to service_role;
-- No raw viewer histories exposed via the Data API.

create or replace function public.blink_record_referral_discovery_open(
  p_profile uuid, p_source text
) returns boolean language plpgsql security definer set search_path=''
as $$
declare viewer uuid := (select auth.uid());
begin
  if viewer is null or p_profile is null or p_profile=viewer
    or p_source not in ('search','recommendations') then return false; end if;
  if not exists (
    select 1 from public.profiles p
    where p.id=p_profile and p.onboarding_completed=true
      and not exists (select 1 from public.user_settings u
                      where u.user_id=p.id and u.private_account)
      and not exists (select 1 from public.blocks b
        where (b.blocker_id=viewer and b.blocked_id=p.id)
           or (b.blocker_id=p.id and b.blocked_id=viewer))
  ) then return false; end if;
  if (select count(*) from public.blink_referral_discovery_opens d
      where d.viewer_id=viewer and d.created_at>now()-interval '1 hour')>=40
  then return false; end if;
  insert into public.blink_referral_discovery_opens(viewer_id,profile_id,source)
  values(viewer,p_profile,p_source)
  on conflict(viewer_id,profile_id,source,opened_day) do nothing;
  return found;
end
$$;
revoke all on function public.blink_record_referral_discovery_open(uuid,text) from public,anon;
grant execute on function public.blink_record_referral_discovery_open(uuid,text) to authenticated;

create or replace function public.blink_referral_source_analytics()
returns table(search_profile_opens bigint, recommendation_profile_opens bigint)
language sql stable security definer set search_path=''
as $$
  select count(*) filter (where source='search')::bigint,
         count(*) filter (where source='recommendations')::bigint
  from public.blink_referral_discovery_opens
  where profile_id=(select auth.uid()) and created_at>=now()-interval '7 days'
$$;
revoke all on function public.blink_referral_source_analytics() from public,anon;
grant execute on function public.blink_referral_source_analytics() to authenticated;
