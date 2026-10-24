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
    credited := new.email_confirmed_at is not null;
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

-- Only actual confirmed referrals count; a visibility advantage decays after 30 days.
create or replace function public.blink_referral_dashboard()
returns table(total_referrals bigint, active_referrals bigint, visibility_points integer, visibility_label text)
language sql stable security invoker set search_path = ''
as $$
  with stats as (
    select
      count(*) filter(where status = 'confirmed') total,
      count(*) filter(where status = 'confirmed' and confirmed_at >= now() - interval '30 days') active
    from public.blink_referrals where referrer_user_id = (select auth.uid())
  )
  select total, active,
         least(30, active::integer * 3),
         case
           when active >= 10 then 'High visibility'
           when active >= 5 then 'Enhanced discovery'
           when active >= 3 then 'Growing visibility'
           when active >= 1 then 'Discovery boost active'
           else 'Normal discovery'
         end
    from stats where (select auth.uid()) is not null;
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
    select r.referrer_user_id, count(*) as active_referrals
    from public.blink_referrals r
    where r.status = 'confirmed' and r.confirmed_at >= now() - interval '30 days'
    group by r.referrer_user_id
  ),
  candidates as (
    select p.id, p.username, p.full_name, p.avatar_url, p.university,
           coalesce(a.active_referrals,0) as active_referrals
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
    where active_referrals > 0
    order by least(active_referrals,10) desc, id
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
    select * from boosted
    union all
    select * from organic
  ) results
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
