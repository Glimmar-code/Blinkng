-- Reconciles Supabase-hosted migration 20261026052615.
-- Applied to BLINK production 26 Oct 2026. Additive foundation only.
-- IMPORTANT: the production change did NOT activate signup triggers,
-- community membership writes, referral awards or client-side writes.
-- Replay-safe after the Oct 24-25 full candidate migrations: do not
-- revoke grants or overwrite security policies from a completed rollout.
begin;
set local lock_timeout='5s';
set local statement_timeout='30s';

create table if not exists public.blink_referrals(
  referred_user_id uuid primary key references auth.users(id) on delete cascade,
  referrer_user_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'pending' check(status in('pending','confirmed')),
  created_at timestamptz not null default now(),
  confirmed_at timestamptz,
  constraint blink_referrals_no_self check(referred_user_id<>referrer_user_id)
);
create index if not exists blink_referrals_referrer_idx
  on public.blink_referrals(referrer_user_id,confirmed_at desc) where status='confirmed';

create table if not exists public.blink_referral_achievements(
  user_id uuid primary key references auth.users(id) on delete cascade,
  confirmed_referrals bigint not null default 0 check(confirmed_referrals>=0),
  last_confirmed_at timestamptz,
  updated_at timestamptz not null default now()
);
create table if not exists public.blink_referral_share_events(
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  channel text not null check(channel in('copy','share','web','desktop')),
  created_at timestamptz not null default now()
);
create index if not exists blink_referral_share_events_owner_created_idx
  on public.blink_referral_share_events(user_id,created_at desc);

create table if not exists public.blink_communities(
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  name text not null check(length(btrim(name)) between 3 and 80),
  description text not null default '' check(length(description)<=1500),
  cover_url text,
  university text not null check(length(btrim(university)) between 2 and 150),
  category text not null check(length(btrim(category)) between 2 and 80),
  visibility text not null default 'public' check(visibility in('public','private')),
  rules text[] not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table if not exists public.blink_community_members(
  community_id uuid not null references public.blink_communities(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'member' check(role in('member','moderator')),
  status text not null default 'pending'
    check(status in('invited','pending','active','banned')),
  joined_at timestamptz not null default now(),
  primary key(community_id,user_id)
);
create index if not exists blink_communities_university_newest
  on public.blink_communities(university,created_at desc);
create index if not exists blink_community_members_user
  on public.blink_community_members(user_id,community_id);

alter table public.blink_referrals enable row level security;
alter table public.blink_referral_achievements enable row level security;
alter table public.blink_referral_share_events enable row level security;
alter table public.blink_communities enable row level security;
alter table public.blink_community_members enable row level security;

-- Explicit, per-table Data API access. ANON reads stay denied even when
-- a complete migration has already enabled authenticated feature RPCs.
revoke all on table public.blink_referrals from anon;
grant select on table public.blink_referrals to authenticated,service_role;
revoke all on table public.blink_referral_achievements from anon;
grant select on table public.blink_referral_achievements to authenticated,service_role;
revoke all on table public.blink_referral_share_events from anon;
grant select on table public.blink_referral_share_events to authenticated,service_role;
revoke all on table public.blink_communities from anon;
grant select on table public.blink_communities to authenticated,service_role;
revoke all on table public.blink_community_members from anon;
grant select on table public.blink_community_members to authenticated,service_role;

-- On partial deployments, deny client writes. Once the full reviewed
-- referral/community RPCs are deployed, retain their already-approved grants.
do $roles$
begin
 if to_regprocedure('public.blink_capture_signup_referral()') is null then
   revoke all on public.blink_referrals,public.blink_referral_achievements,
     public.blink_referral_share_events from public,anon,authenticated;
 end if;
 if to_regprocedure('public.blink_community_join(uuid)') is null then
   revoke all on public.blink_communities,public.blink_community_members
     from public,anon,authenticated;
 end if;
end;
$roles$;

grant select on public.blink_referrals,public.blink_referral_achievements,
  public.blink_referral_share_events,public.blink_communities,
  public.blink_community_members to authenticated,service_role;

do $policies$
begin
 if not exists(select 1 from pg_policies where schemaname='public'
     and tablename='blink_referrals' and policyname='blink_referrals_read_own') then
   create policy blink_referrals_read_own on public.blink_referrals
     for select to authenticated
     using((select auth.uid()) in(referred_user_id,referrer_user_id));
 end if;
 if not exists(select 1 from pg_policies where schemaname='public'
     and tablename='blink_referral_achievements' and policyname='blink_referral_achievements_read_own') then
   create policy blink_referral_achievements_read_own on public.blink_referral_achievements
     for select to authenticated using(user_id=(select auth.uid()));
 end if;
 if not exists(select 1 from pg_policies where schemaname='public'
     and tablename='blink_referral_share_events' and policyname='blink_referral_share_events_owner') then
   create policy blink_referral_share_events_owner on public.blink_referral_share_events
     for select to authenticated using(user_id=(select auth.uid()));
 end if;
 if not exists(select 1 from pg_policies where schemaname='public'
     and tablename='blink_communities' and policyname='blink_communities_select') then
   create policy blink_communities_select on public.blink_communities
     for select to authenticated
     using(visibility='public' or owner_id=(select auth.uid()));
 end if;
 if not exists(select 1 from pg_policies where schemaname='public'
     and tablename='blink_community_members' and policyname='blink_community_members_read') then
   create policy blink_community_members_read on public.blink_community_members
     for select to authenticated using(user_id=(select auth.uid()));
 end if;
end;
$policies$;

do $verify$
declare v_count integer;
begin
 select count(*)::integer into v_count from pg_class c
 join pg_namespace n on n.oid=c.relnamespace
 where n.nspname='public'
   and c.relname in('blink_referrals','blink_referral_achievements',
     'blink_referral_share_events','blink_communities','blink_community_members')
   and c.relkind='r' and c.relrowsecurity;
 if v_count<>5 then
   raise exception 'BLINK_FOUNDATION_TABLE_RLS_VERIFICATION_FAILED';
 end if;
 if has_table_privilege('anon','public.blink_referrals','SELECT') then
   raise exception 'BLINK_ANON_REFERRAL_LEAK';
 end if;
end;
$verify$;
commit;
