begin;

-- Authenticated users can already read follows through RLS. Keep this helper
-- SECURITY INVOKER so it never bypasses the table policies.
create or replace function public.get_my_following_ids()
returns setof uuid
language sql
stable
security invoker
set search_path = public
as $$
  select f.following_id
  from public.follows f
  where f.follower_id = auth.uid()
  order by f.created_at desc nulls last;
$$;

revoke all on function public.get_my_following_ids() from public, anon;
grant execute on function public.get_my_following_ids() to authenticated;

-- Preserve the existing void RPC contract used by the Android client while
-- updating both legacy and current presence columns on every heartbeat.
create or replace function public.set_my_presence(p_online boolean)
returns void
language sql
security invoker
set search_path = public
as $$
  update public.profiles
  set online_now = coalesce(p_online, false),
      is_online = coalesce(p_online, false),
      last_seen_at = now(),
      last_seen = now(),
      updated_at = now()
  where id = auth.uid();
$$;

revoke all on function public.set_my_presence(boolean) from public, anon;
grant execute on function public.set_my_presence(boolean) to authenticated;

-- A process can be killed without sending an offline event, so online state is
-- treated as a short heartbeat lease instead of a permanent boolean.
create or replace function public.reconcile_presence_lease()
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
      is_online = false,
      updated_at = now()
  where (online_now = true or is_online = true)
    and coalesce(last_seen_at, last_seen, updated_at, created_at) < now() - interval '2 minutes';

  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

revoke all on function public.reconcile_presence_lease() from public, anon, authenticated;

-- Keep the cleanup idempotent across repeated deployments.
do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    perform cron.unschedule(jobid)
      from cron.job
      where jobname = 'blink-presence-lease-reconcile';

    perform cron.schedule(
      'blink-presence-lease-reconcile',
      '* * * * *',
      'select public.reconcile_presence_lease();'
    );
  end if;
end;
$$;

-- Clear rows left stale by older clients immediately after deployment.
select public.reconcile_presence_lease();

commit;
