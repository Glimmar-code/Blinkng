-- STAGING REVIEW SCRIPT ONLY. Do not run against the existing BLINK production DB.
-- Run in a separate TEST Supabase project after DBA review, backup and policy checks.
-- No BLINK Coins, fiat money, payouts, ad rewards or purchases.
-- Only the trusted service_role may execute these world-state RPCs.
begin;

create schema if not exists private;

create table if not exists private.blink_life_world_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  game_cash integer not null default 500 check (game_cash between 0 and 1000000),
  xp integer not null default 0 check (xp between 0 and 1000000),
  house_owned boolean not null default false,
  vehicle_owned boolean not null default false,
  furniture smallint not null default 0 check (furniture between 0 and 12),
  revision bigint not null default 1 check (revision > 0),
  updated_at timestamptz not null default now()
);

create table if not exists private.blink_life_verified_campus (
  user_id uuid primary key references auth.users(id) on delete cascade,
  university_code text not null check (university_code ~ '^[a-z0-9_-]{2,32}$'),
  verified_at timestamptz not null default now()
);
-- Only a trusted registrar/admin process may insert/modify membership records.
-- Never infer verified school from profiles.university or user_metadata.

create table if not exists private.blink_life_world_events (
  event_id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  kind text not null check (kind in ('depot_shift','buy_house','buy_vehicle','buy_furniture','duel_win')),
  game_cash_delta integer not null,
  xp_delta integer not null,
  campus_points integer not null default 0 check (campus_points between 0 and 1),
  university_code text,
  created_at timestamptz not null default now()
);
create index if not exists blink_life_world_event_user_day_idx
  on private.blink_life_world_events (user_id, kind, created_at desc);
create index if not exists blink_life_world_event_campus_idx
  on private.blink_life_world_events (university_code, created_at desc)
  where campus_points > 0;

alter table private.blink_life_world_profiles enable row level security;
alter table private.blink_life_world_profiles force row level security;
alter table private.blink_life_verified_campus enable row level security;
alter table private.blink_life_verified_campus force row level security;
alter table private.blink_life_world_events enable row level security;
alter table private.blink_life_world_events force row level security;

revoke all on schema private from public, anon, authenticated;
revoke all on private.blink_life_world_profiles from public, anon, authenticated;
revoke all on private.blink_life_verified_campus from public, anon, authenticated;
revoke all on private.blink_life_world_events from public, anon, authenticated;

create or replace function public.blink_life_stage_profile(p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_profile private.blink_life_world_profiles%rowtype;
begin
  if p_user_id is null then raise exception 'MISSING_ACCOUNT_ID'; end if;
  insert into private.blink_life_world_profiles(user_id)
  values (p_user_id) on conflict (user_id) do nothing;
  select * into strict v_profile
    from private.blink_life_world_profiles where user_id = p_user_id;
  return jsonb_build_object(
    'user_id',v_profile.user_id,'game_cash',v_profile.game_cash,
    'xp',v_profile.xp,'house_owned',v_profile.house_owned,
    'vehicle_owned',v_profile.vehicle_owned,'furniture',v_profile.furniture,
    'revision',v_profile.revision
  );
end;
$$;

create or replace function public.blink_life_stage_event(
  p_event_id uuid, p_user_id uuid, p_kind text
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_profile private.blink_life_world_profiles%rowtype;
  v_existing private.blink_life_world_events%rowtype;
  v_gc integer := 0;
  v_xp integer := 0;
  v_points integer := 0;
  v_school text;
  v_today timestamptz := date_trunc('day', now() at time zone 'utc') at time zone 'utc';
begin
  if p_event_id is null or p_user_id is null or p_kind not in
    ('depot_shift','buy_house','buy_vehicle','buy_furniture','duel_win') then
    raise exception 'BAD_WORLD_EVENT';
  end if;
  select * into v_existing from private.blink_life_world_events
  where event_id = p_event_id;
  if found then
    if v_existing.user_id <> p_user_id or v_existing.kind <> p_kind then
      raise exception 'EVENT_ID_CONFLICT';
    end if;
    return jsonb_build_object('duplicate',true,'event_id',p_event_id);
  end if;
  insert into private.blink_life_world_profiles(user_id)
    values (p_user_id) on conflict (user_id) do nothing;
  select * into strict v_profile from private.blink_life_world_profiles
  where user_id = p_user_id for update;

  if p_kind = 'depot_shift' then
    if (select count(*) from private.blink_life_world_events
         where user_id = p_user_id and kind = p_kind and created_at >= v_today) >= 20 then
      raise exception 'DAILY_SHIFT_LIMIT';
    end if;
    v_gc := 80;
    v_xp := 3;
  elsif p_kind = 'buy_house' then
    if v_profile.house_owned then raise exception 'ALREADY_OWNS_HOME'; end if;
    v_gc := -400;
  elsif p_kind = 'buy_vehicle' then
    if v_profile.vehicle_owned then raise exception 'ALREADY_OWNS_CAR'; end if;
    v_gc := -300;
  elsif p_kind = 'buy_furniture' then
    if not v_profile.house_owned or v_profile.furniture >= 12 then
      raise exception 'FURNITURE_NOT_ALLOWED';
    end if;
    v_gc := -150;
  elsif p_kind = 'duel_win' then
    if (select count(*) from private.blink_life_world_events
         where user_id = p_user_id and kind = p_kind and created_at >= v_today) >= 5 then
      raise exception 'DAILY_QUIZ_LIMIT';
    end if;
    v_xp := 15; -- non-redeemable XP only
    select university_code into v_school from private.blink_life_verified_campus
    where user_id = p_user_id;
    if v_school is not null then v_points := 1; end if;
  end if;

  if v_profile.game_cash + v_gc < 0 or v_profile.game_cash + v_gc > 1000000 then
    raise exception 'INSUFFICIENT_GAME_CASH';
  end if;
  update private.blink_life_world_profiles
  set game_cash = game_cash + v_gc,
      xp = least(1000000, xp + v_xp),
      house_owned = house_owned or p_kind = 'buy_house',
      vehicle_owned = vehicle_owned or p_kind = 'buy_vehicle',
      furniture = furniture + (case when p_kind = 'buy_furniture' then 1 else 0 end),
      revision = revision + 1,
      updated_at = now()
  where user_id = p_user_id;
  insert into private.blink_life_world_events(
    event_id, user_id, kind, game_cash_delta, xp_delta, campus_points, university_code
  ) values (p_event_id, p_user_id, p_kind, v_gc, v_xp, v_points, v_school);
  return jsonb_build_object(
    'duplicate',false,'event_id',p_event_id,
    'profile',public.blink_life_stage_profile(p_user_id)
  );
end;
$$;

create or replace function public.blink_life_stage_campus_rankings()
returns jsonb
language sql
security definer
set search_path = ''
as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'university', university_code,
    'points', points,
    'players', players
  ) order by points desc, university_code asc), '[]'::jsonb)
  from (
    select university_code,
           sum(campus_points)::bigint as points,
           count(distinct user_id)::integer as players
    from private.blink_life_world_events
    where campus_points > 0 and university_code is not null
    group by university_code
    order by points desc, university_code asc
    limit 50
  ) leaderboard;
$$;

revoke all on function public.blink_life_stage_profile(uuid) from public, anon, authenticated;
revoke all on function public.blink_life_stage_event(uuid,uuid,text) from public, anon, authenticated;
revoke all on function public.blink_life_stage_campus_rankings() from public, anon, authenticated;
grant execute on function public.blink_life_stage_profile(uuid) to service_role;
grant execute on function public.blink_life_stage_event(uuid,uuid,text) to service_role;
grant execute on function public.blink_life_stage_campus_rankings() to service_role;

commit;
