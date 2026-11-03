create or replace function public.refresh_leaderboards()
returns integer
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_inserted integer := 0;
begin
  delete from public.leaderboard_snapshots
  where snapshot_at < now() - interval '30 days';

  insert into public.leaderboard_snapshots (
    user_id,
    name,
    handle,
    university,
    avatar_url,
    verification_tier,
    world_score,
    world_rank,
    campus_score,
    campus_rank,
    snapshot_at
  )
  select
    p.id,
    coalesce(nullif(p.name, ''), nullif(p.full_name, ''), p.username),
    p.username,
    nullif(nullif(p.university, ''), 'null'),
    nullif(p.avatar_url, ''),
    case upper(coalesce(p.verification_badge::text, ''))
      when 'GOLD' then 'Gold'::public.verification_tier_enum
      when 'BLUE' then 'Standard'::public.verification_tier_enum
      else 'None'::public.verification_tier_enum
    end,
    coalesce(p.points, 0)::bigint,
    rank() over (order by coalesce(p.points, 0) desc, p.created_at asc)::integer,
    coalesce(p.points, 0)::bigint,
    rank() over (
      partition by nullif(nullif(p.university, ''), 'null')
      order by coalesce(p.points, 0) desc, p.created_at asc
    )::integer,
    now()
  from public.profiles p
  where coalesce(trim(p.username), '') <> '';

  get diagnostics v_inserted = row_count;
  return v_inserted;
end;
$$;

create or replace function public.expire_statuses()
returns integer
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_expired integer := 0;
begin
  update public.statuses
  set is_active = false
  where is_active = true
    and coalesce(expires_at, created_at + interval '24 hours') <= now();

  get diagnostics v_expired = row_count;
  return v_expired;
end;
$$;

revoke execute on function public.refresh_leaderboards() from public, anon, authenticated;
revoke execute on function public.expire_statuses() from public, anon, authenticated;

select cron.unschedule('hourly-leaderboard-refresh');
select cron.unschedule('blink-hourly-leaderboard-refresh');
select cron.unschedule('expire-statuses');
select cron.unschedule('blink-expire-statuses');

select cron.schedule(
  'blink-refresh-leaderboards',
  '0 * * * *',
  $$select public.refresh_leaderboards();$$
);

select cron.schedule(
  'blink-expire-statuses',
  '*/15 * * * *',
  $$select public.expire_statuses();$$
);
