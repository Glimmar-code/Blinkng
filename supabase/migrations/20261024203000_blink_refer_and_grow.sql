-- BLINK Refer & Grow. Staging-first: DO NOT deploy directly to production.
-- One referrer per new account; referrals become active only after email confirmation.
create table if not exists public.blink_referrals (
  referred_user_id uuid primary key references auth.users(id) on delete cascade,
  referrer_user_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending', 'confirmed')),
  created_at timestamptz not null default now(),
  confirmed_at timestamptz,
  constraint blink_referrals_no_self check (referred_user_id <> referrer_user_id)
);
create index if not exists blink_referrals_referrer_idx
  on public.blink_referrals(referrer_user_id, confirmed_at desc)
  where status = 'confirmed';

alter table public.blink_referrals enable row level security;
revoke all on public.blink_referrals from public, anon, authenticated;
grant select on public.blink_referrals to authenticated;
drop policy if exists blink_referrals_read_own on public.blink_referrals;
create policy blink_referrals_read_own on public.blink_referrals
  for select to authenticated
  using ((select auth.uid()) in (referrer_user_id, referred_user_id));
-- No browser/mobile/desktop INSERT or UPDATE policy: all credits are server-owned.

-- Lifetime achievement totals are durable, privacy-minimal aggregates. Removing a
-- referred account clears its relationship row, but does not erase an earned badge.
-- Deleting the REFERRER's account removes their achievements.
create table if not exists public.blink_referral_achievements (
  user_id uuid primary key references auth.users(id) on delete cascade,
  confirmed_referrals bigint not null default 0 check (confirmed_referrals >= 0),
  last_confirmed_at timestamptz,
  updated_at timestamptz not null default now()
);
alter table public.blink_referral_achievements enable row level security;
revoke all on public.blink_referral_achievements from public, anon, authenticated;
grant select on public.blink_referral_achievements to authenticated;
drop policy if exists blink_referral_achievements_read_own on public.blink_referral_achievements;
create policy blink_referral_achievements_read_own on public.blink_referral_achievements
  for select to authenticated using (user_id = (select auth.uid()));

-- Auth metadata is an untrusted suggestion. Resolve it on the server, never in the client.
-- The function only runs as an internal auth.users trigger.
create or replace function public.blink_capture_signup_referral()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  raw_referrer text;
  resolved_referrer uuid;
  credited boolean := false;
begin
  if tg_op = 'INSERT' then
    raw_referrer := new.raw_user_meta_data->>'blink_referrer_id';
    if raw_referrer is null or raw_referrer !~
       '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
    then
      return new;
    end if;
    resolved_referrer := raw_referrer::uuid;
    -- Existing, previously registered and completed profile required.
    if resolved_referrer = new.id or not exists (
      select 1 from public.profiles p
      join auth.users au on au.id = p.id
      where p.id = resolved_referrer
        and p.onboarding_completed is true
        and au.created_at < new.created_at
    ) then
      return new;
    end if;
    insert into public.blink_referrals(referred_user_id, referrer_user_id, status, confirmed_at)
    values (
      new.id, resolved_referrer,
      case when new.email_confirmed_at is null then 'pending' else 'confirmed' end,
      new.email_confirmed_at
    )
    on conflict (referred_user_id) do nothing;
    -- Conflict/replay cannot grant the same referral a second time.
    credited := found and new.email_confirmed_at is not null;
  elsif tg_op = 'UPDATE' then
    if old.email_confirmed_at is not null or new.email_confirmed_at is null then
      return new;
    end if;
    update public.blink_referrals r
       set status = 'confirmed', confirmed_at = new.email_confirmed_at
     where r.referred_user_id = new.id and r.status = 'pending';
    credited := found;
  end if;

  if credited then
    -- Atomic permanent accounting: count the transition into confirmed exactly once.
    -- The aggregate contains no referred user's identity and is never decremented.
    insert into public.blink_referral_achievements(
      user_id, confirmed_referrals, last_confirmed_at, updated_at
    )
    select r.referrer_user_id, 1, r.confirmed_at, now()
      from public.blink_referrals r
     where r.referred_user_id = new.id and r.status = 'confirmed'
    on conflict (user_id) do update
      set confirmed_referrals = public.blink_referral_achievements.confirmed_referrals + 1,
          last_confirmed_at = greatest(
            public.blink_referral_achievements.last_confirmed_at,
            excluded.last_confirmed_at
          ),
          updated_at = now();

    -- A safe retry guard protects against duplicate push on any repeated auth update.
    insert into public.notifications(
      user_id, actor_id, type, target_type, target_id, text, sub_text, is_read,
      metadata
    )
    select r.referrer_user_id, new.id, 'system', 'profile', new.id,
           'Your BLINK network is growing!',
           'A student joined with your referral. You have more opportunities to be discovered.',
           false, jsonb_build_object('kind','blink_referral_confirmed')
      from public.blink_referrals r
      join public.profiles p on p.id = new.id
     where r.referred_user_id = new.id
       and r.status = 'confirmed'
       and not exists (
         select 1 from public.notifications n
         where n.user_id = r.referrer_user_id
           and n.actor_id = new.id
           and n.metadata->>'kind' = 'blink_referral_confirmed'
       );
  end if;
  return new;
end;
$$;
revoke all on function public.blink_capture_signup_referral() from public, anon, authenticated;
drop trigger if exists zz_blink_signup_referral on auth.users;
create trigger zz_blink_signup_referral
after insert or update of email_confirmed_at on auth.users
for each row execute function public.blink_capture_signup_referral();

-- For signups where profile creation happens AFTER an immediately confirmed auth signup.
create or replace function public.blink_notify_confirmed_referral_profile()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
  insert into public.notifications(
    user_id, actor_id, type, target_type, target_id, text, sub_text, is_read, metadata
  )
  select r.referrer_user_id, new.id, 'system', 'profile', new.id,
         'Your BLINK network is growing!',
         'A student joined with your referral. You have more opportunities to be discovered.',
         false, jsonb_build_object('kind','blink_referral_confirmed')
    from public.blink_referrals r
   where r.referred_user_id = new.id and r.status = 'confirmed'
     and not exists (
       select 1 from public.notifications n
        where n.user_id = r.referrer_user_id and n.actor_id = new.id
          and n.metadata->>'kind' = 'blink_referral_confirmed'
     );
  return new;
end;
$$;
revoke all on function public.blink_notify_confirmed_referral_profile() from public, anon, authenticated;
drop trigger if exists zz_blink_notify_referral_on_profile on public.profiles;
create trigger zz_blink_notify_referral_on_profile
after insert on public.profiles for each row
execute function public.blink_notify_confirmed_referral_profile();

-- Public sign-up autocomplete exposes only opted-in-by-onboarding public discovery fields.
-- Prefix matches first, then substrings; no private contact details or email addresses.
create or replace function public.blink_referrer_suggestions(
  p_query text, p_limit integer default 10, p_offset integer default 0
)
returns table(id uuid, username text, full_name text, avatar_url text, university text)
language sql stable security definer set search_path = ''
as $$
  select p.id, p.username, p.full_name, p.avatar_url, p.university
    from public.profiles p
   where length(trim(coalesce(p_query,''))) between 1 and 40
     and p.onboarding_completed is true
     and not exists (
       select 1 from public.user_settings settings
       where settings.user_id = p.id and settings.private_account is true
     )
     and nullif(trim(p.username),'') is not null
     and (
       lower(p.username) like '%' || lower(trim(p_query)) || '%'
       or lower(coalesce(p.full_name,'')) like '%' || lower(trim(p_query)) || '%'
     )
   order by
     case
       when lower(p.username) = lower(trim(p_query)) then 0
       when lower(p.username) like lower(trim(p_query)) || '%' then 1
       when lower(coalesce(p.full_name,'')) like lower(trim(p_query)) || '%' then 2
       when lower(p.username) like '%' || lower(trim(p_query)) || '%' then 3
       else 4
     end,
     lower(p.username), p.id
   limit greatest(1,least(coalesce(p_limit,10),10))
  offset greatest(0,least(coalesce(p_offset,0),500));
$$;
revoke all on function public.blink_referrer_suggestions(text,integer,integer) from public;
grant execute on function public.blink_referrer_suggestions(text,integer,integer) to anon, authenticated;

-- Permanent achievements are based on an immutable lifetime count, while
-- the OPTIONAL discovery advantage is calculated from fresh confirmations.
-- Each referral contributes 3 points for days 0-9, 2 for 10-19, 1 for 20-29,
-- and 0 thereafter. New genuine referrals replenish points; lifetime count stays.
-- Tier milestones: 1 Starter, 5 Builder, 10 Advocate, 25 Connector,
-- 50 Champion, 100 Ambassador. No coins, followers, or views awarded.
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
  select total, active, points,
         case
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

-- Discovery surface: at most 3 boosted recommendations in a 10-person set.
-- Keep the user's campus relevant and exclude their own account and block relationships.
create or replace function public.blink_referral_recommendations(p_limit integer default 10)
returns table(id uuid, username text, full_name text, avatar_url text, university text, referral_featured boolean)
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
    group by r.referrer_user_id
  ),
  candidates as (
    select p.id, p.username, p.full_name, p.avatar_url, p.university,
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
  select id, username, full_name, avatar_url, university, featured
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


-- Auth-trigger notifications may be committed without an HTTP JWT request context.
-- After the referred student signs in, return only their own confirmed referral's
-- notification ID so the existing actor-authenticated, idempotent FCM dispatcher
-- can send the push to a referrer even when the referrer is offline.
create or replace function public.blink_my_referral_push_ids()
returns table(notification_id uuid)
language sql stable security definer set search_path = ''
as $$
  select n.id
    from public.notifications n
    join public.blink_referrals r
      on r.referred_user_id = n.actor_id
     and r.referrer_user_id = n.user_id
   where n.actor_id = (select auth.uid())
     and r.status = 'confirmed'
     and n.metadata->>'kind' = 'blink_referral_confirmed'
   order by n.id
   limit 3;
$$;
revoke all on function public.blink_my_referral_push_ids() from public, anon;
grant execute on function public.blink_my_referral_push_ids() to authenticated;
