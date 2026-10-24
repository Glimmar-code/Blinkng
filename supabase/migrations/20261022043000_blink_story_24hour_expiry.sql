-- STAGING FIRST: existing Android and Windows clients must see the same 24-hour result.
-- The Stories app reads public.stories, not the legacy public.statuses table.
-- Keep rows for audit/archive: do not delete story data or storage from this migration.
begin;

alter table public.stories enable row level security;

-- Fail closed at query time, even during cron delays. Existing stories are public
-- to authenticated members only: close-friends/campus privacy must be added later
-- with server-authorized audience checks AND private media storage.
drop policy if exists select_stories on public.stories;
create policy select_stories
on public.stories for select to authenticated
using (
    active = true
    and created_at <= now()
    and created_at > now() - interval '24 hours'
);

create schema if not exists private;

create or replace function private.expire_blink_stories()
returns integer
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
    v_updated integer := 0;
begin
    update public.stories
    set active = false
    where active = true
      and created_at <= now() - interval '24 hours';
    get diagnostics v_updated = row_count;
    return v_updated;
end
$$;

revoke all on function private.expire_blink_stories() from public, anon, authenticated;
grant execute on function private.expire_blink_stories() to service_role;

-- pg_cron already exists in the production project. Avoid duplicate scheduled jobs.
do $$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron')
       and not exists (select 1 from cron.job where jobname = 'blink-expire-stories') then
        perform cron.schedule(
            'blink-expire-stories',
            '*/15 * * * *',
            'select private.expire_blink_stories()'
        );
    end if;
end $$;

commit;
