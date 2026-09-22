-- TESTLAB DESIGN ONLY. Do not execute directly in production.
-- Convert to a generated Supabase migration only after validation on a Supabase dev branch.

create table if not exists public.call_quality_reports (
  call_id uuid not null references public.calls(id) on delete cascade,
  participant_id uuid not null references public.profiles(id) on delete cascade,
  setup_ms integer check (setup_ms is null or setup_ms between 0 and 300000),
  reconnect_count integer not null default 0 check (reconnect_count between 0 and 1000),
  terminal_status text not null
    check (terminal_status in ('declined','busy','missed','cancelled','ended','failed')),
  data_saver boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (call_id, participant_id)
);

alter table public.call_quality_reports enable row level security;

drop policy if exists "participants read own call quality" on public.call_quality_reports;
create policy "participants read own call quality"
on public.call_quality_reports for select
to authenticated
using (participant_id = (select auth.uid()));

grant select on public.call_quality_reports to authenticated;

create or replace function public.report_call_quality(
  p_call_id uuid,
  p_setup_ms integer,
  p_reconnect_count integer,
  p_terminal_status text,
  p_data_saver boolean
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
begin
  if v_user_id is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  if not exists (
    select 1
    from public.calls c
    where c.id = p_call_id
      and (c.caller_id = v_user_id or c.callee_id = v_user_id)
  ) then
    raise exception 'CALL_NOT_FOUND';
  end if;

  if p_terminal_status not in ('declined','busy','missed','cancelled','ended','failed') then
    raise exception 'INVALID_TERMINAL_STATUS';
  end if;

  insert into public.call_quality_reports (
    call_id,
    participant_id,
    setup_ms,
    reconnect_count,
    terminal_status,
    data_saver,
    updated_at
  )
  values (
    p_call_id,
    v_user_id,
    least(300000, greatest(0, p_setup_ms)),
    least(1000, greatest(0, p_reconnect_count)),
    p_terminal_status,
    coalesce(p_data_saver, false),
    now()
  )
  on conflict (call_id, participant_id)
  do update set
    setup_ms = excluded.setup_ms,
    reconnect_count = excluded.reconnect_count,
    terminal_status = excluded.terminal_status,
    data_saver = excluded.data_saver,
    updated_at = now();
end;
$$;

revoke all on function public.report_call_quality(uuid, integer, integer, text, boolean)
  from public, anon;
grant execute on function public.report_call_quality(uuid, integer, integer, text, boolean)
  to authenticated;
