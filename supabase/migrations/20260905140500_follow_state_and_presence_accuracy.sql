begin;

-- Give the app one RLS-safe source of truth for the signed-in user's follows.
create or replace function public.get_my_following_ids()
returns setof uuid
language sql
stable
security definer
set search_path = public
as $$
  select f.following_id
  from public.follows f
  where f.follower_id = auth.uid()
  order by f.created_at desc nulls last;
$$;

revoke all on function public.get_my_following_ids() from public, anon;
grant execute on function public.get_my_following_ids() to authenticated;

-- Presence is a heartbeat, not a permanent boolean. Always stamp last_seen_at
-- when the app changes state so a killed process cannot leave someone active forever.
create or replace function public.set_my_presence(p_online boolean)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  update public.profiles
  set online_now = p_online,
      last_seen_at = now(),
      updated_at = now()
  where id = auth.uid();

  return p_online;
end;
$$;

revoke all on function public.set_my_presence(boolean) from public, anon;
grant execute on function public.set_my_presence(boolean) to authenticated;

create or replace function public.expire_stale_presence()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count integer := 0;
begin
  update public.profiles
  set online_now = false,
      updated_at = now()
  where online_now = true
    and coalesce(last_seen_at, updated_at, created_at) < now() - interval '2 minutes';

  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

revoke all on function public.expire_stale_presence() from public, anon, authenticated;

-- Keep the cleanup idempotent across repeated deployments.
do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    perform cron.unschedule(jobid)
      from cron.job
      where jobname = 'blink-expire-stale-presence';

    perform cron.schedule(
      'blink-expire-stale-presence',
      '* * * * *',
      'select public.expire_stale_presence();'
    );
  end if;
end;
$$;

-- Immediately clear stale rows left by older clients.
select public.expire_stale_presence();

commit;
