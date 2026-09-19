-- Keep profiles.profile_views_this_week synchronized with the canonical profile_views events.
-- Inserts/updates/deletes refresh the affected profile immediately; hourly cron handles
-- rolling seven-day expirations even when no new view event arrives.

create or replace function private.refresh_profile_views_this_week(
  p_profile_id uuid default null
)
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
declare
  v_updated integer := 0;
begin
  with counts as (
    select
      p.id as profile_id,
      count(pv.id) filter (
        where pv.created_at >= now() - interval '7 days'
      )::integer as view_count
    from public.profiles p
    left join public.profile_views pv
      on pv.profile_id = p.id
    where p_profile_id is null
       or p.id = p_profile_id
    group by p.id
  )
  update public.profiles p
  set profile_views_this_week = c.view_count
  from counts c
  where p.id = c.profile_id
    and p.profile_views_this_week is distinct from c.view_count;

  get diagnostics v_updated = row_count;
  return v_updated;
end;
$function$;

revoke all on function private.refresh_profile_views_this_week(uuid)
  from public, anon, authenticated;

create or replace function private.sync_profile_views_this_week()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
begin
  if tg_op = 'DELETE' then
    perform private.refresh_profile_views_this_week(old.profile_id);
    return old;
  end if;

  if tg_op = 'UPDATE' then
    perform private.refresh_profile_views_this_week(old.profile_id);
    if new.profile_id is distinct from old.profile_id then
      perform private.refresh_profile_views_this_week(new.profile_id);
    end if;
    return new;
  end if;

  perform private.refresh_profile_views_this_week(new.profile_id);
  return new;
end;
$function$;

revoke all on function private.sync_profile_views_this_week()
  from public, anon, authenticated;

drop trigger if exists trg_sync_profile_views_this_week
  on public.profile_views;

create trigger trg_sync_profile_views_this_week
after insert or update or delete
on public.profile_views
for each row
execute function private.sync_profile_views_this_week();

do $block$
declare
  v_job_id bigint;
begin
  for v_job_id in
    select jobid
    from cron.job
    where jobname = 'blink-refresh-profile-views-this-week'
  loop
    perform cron.unschedule(v_job_id);
  end loop;

  perform cron.schedule(
    'blink-refresh-profile-views-this-week',
    '7 * * * *',
    'select private.refresh_profile_views_this_week();'
  );
end;
$block$;

select private.refresh_profile_views_this_week();
