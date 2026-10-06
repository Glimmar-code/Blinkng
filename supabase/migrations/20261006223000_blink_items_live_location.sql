-- BLINK Items: private, explicitly shared live location.
-- Stores only the current position for an active session; no movement history is retained.

create table if not exists public.live_location_sessions (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null,
    expires_at timestamptz not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint live_location_sessions_future_expiry check (expires_at > created_at)
);

create table if not exists public.live_location_recipients (
    session_id uuid not null references public.live_location_sessions(id) on delete cascade,
    owner_id uuid not null,
    recipient_id uuid not null,
    created_at timestamptz not null default now(),
    primary key (session_id, recipient_id),
    constraint live_location_recipient_not_owner check (owner_id <> recipient_id)
);

create table if not exists public.live_location_positions (
    session_id uuid primary key references public.live_location_sessions(id) on delete cascade,
    owner_id uuid not null,
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    accuracy_meters real not null default 0 check (accuracy_meters >= 0),
    updated_at timestamptz not null default now()
);

create index if not exists live_location_sessions_owner_active_idx
    on public.live_location_sessions(owner_id, active, expires_at desc);
create index if not exists live_location_recipients_recipient_idx
    on public.live_location_recipients(recipient_id, session_id);
create index if not exists live_location_positions_updated_idx
    on public.live_location_positions(updated_at desc);

alter table public.live_location_sessions enable row level security;
alter table public.live_location_recipients enable row level security;
alter table public.live_location_positions enable row level security;

-- The mobile client uses the audited RPCs below. Direct mutations stay unavailable.
revoke all on public.live_location_sessions from anon, authenticated;
revoke all on public.live_location_recipients from anon, authenticated;
revoke all on public.live_location_positions from anon, authenticated;

create or replace function public.start_live_location_session(
    p_recipient_ids uuid[],
    p_duration_minutes integer
)
returns table(session_id uuid, expires_at timestamptz)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_owner uuid := auth.uid();
    v_session uuid;
    v_expires timestamptz;
begin
    if v_owner is null then
        raise exception 'Authentication required';
    end if;

    if p_duration_minutes not in (15, 60, 240) then
        raise exception 'Duration must be 15, 60, or 240 minutes';
    end if;

    if p_recipient_ids is null or coalesce(array_length(p_recipient_ids, 1), 0) = 0 then
        raise exception 'Choose at least one recipient';
    end if;

    -- Starting a new share replaces any older owner session and its current coordinate.
    update public.live_location_sessions
       set active = false, updated_at = now()
     where owner_id = v_owner
       and active = true;

    delete from public.live_location_positions p
     using public.live_location_sessions s
     where p.session_id = s.id
       and s.owner_id = v_owner
       and s.active = false;

    v_expires := now() + make_interval(mins => p_duration_minutes);

    insert into public.live_location_sessions(owner_id, expires_at)
    values (v_owner, v_expires)
    returning id into v_session;

    insert into public.live_location_recipients(session_id, owner_id, recipient_id)
    select v_session, v_owner, candidate.recipient_id
      from (
        select distinct unnest(p_recipient_ids) as recipient_id
      ) candidate
      join public.profiles p on p.id = candidate.recipient_id
     where candidate.recipient_id <> v_owner
    on conflict do nothing;

    if not exists (
        select 1
          from public.live_location_recipients
         where session_id = v_session
    ) then
        delete from public.live_location_sessions where id = v_session;
        raise exception 'No valid recipients were selected';
    end if;

    return query select v_session, v_expires;
end;
$$;

create or replace function public.update_live_location_position(
    p_session_id uuid,
    p_latitude double precision,
    p_longitude double precision,
    p_accuracy_meters real default 0
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_owner uuid := auth.uid();
begin
    if v_owner is null then
        raise exception 'Authentication required';
    end if;
    if p_latitude not between -90 and 90 or p_longitude not between -180 and 180 then
        raise exception 'Invalid coordinates';
    end if;
    if not exists (
        select 1
          from public.live_location_sessions s
         where s.id = p_session_id
           and s.owner_id = v_owner
           and s.active = true
           and s.expires_at > now()
    ) then
        raise exception 'Live location session is inactive or expired';
    end if;

    insert into public.live_location_positions(
        session_id, owner_id, latitude, longitude, accuracy_meters, updated_at
    )
    values (
        p_session_id,
        v_owner,
        p_latitude,
        p_longitude,
        greatest(coalesce(p_accuracy_meters, 0), 0),
        now()
    )
    on conflict (session_id) do update
       set latitude = excluded.latitude,
           longitude = excluded.longitude,
           accuracy_meters = excluded.accuracy_meters,
           updated_at = now();
end;
$$;

create or replace function public.stop_live_location_session(
    p_session_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_owner uuid := auth.uid();
    v_changed integer;
begin
    if v_owner is null then
        raise exception 'Authentication required';
    end if;

    update public.live_location_sessions
       set active = false, updated_at = now()
     where id = p_session_id
       and owner_id = v_owner
       and active = true;
    get diagnostics v_changed = row_count;

    delete from public.live_location_positions
     where session_id = p_session_id
       and owner_id = v_owner;

    return v_changed > 0;
end;
$$;

create or replace function public.get_my_active_live_location_session()
returns table(session_id uuid, expires_at timestamptz, recipient_count bigint)
language sql
security definer
set search_path = public, pg_temp
stable
as $$
    select s.id,
           s.expires_at,
           count(r.recipient_id)
      from public.live_location_sessions s
      join public.live_location_recipients r on r.session_id = s.id
     where s.owner_id = auth.uid()
       and s.active = true
       and s.expires_at > now()
     group by s.id, s.expires_at
     order by s.created_at desc
     limit 1;
$$;

create or replace function public.get_active_live_locations()
returns table(
    session_id uuid,
    owner_id uuid,
    username text,
    full_name text,
    avatar_url text,
    latitude double precision,
    longitude double precision,
    accuracy_meters real,
    updated_at timestamptz,
    expires_at timestamptz
)
language sql
security definer
set search_path = public, pg_temp
stable
as $$
    select s.id,
           s.owner_id,
           coalesce(p.username, ''),
           coalesce(p.full_name, ''),
           coalesce(p.avatar_url, ''),
           pos.latitude,
           pos.longitude,
           pos.accuracy_meters,
           pos.updated_at,
           s.expires_at
      from public.live_location_recipients r
      join public.live_location_sessions s
        on s.id = r.session_id
       and s.active = true
       and s.expires_at > now()
      join public.live_location_positions pos on pos.session_id = s.id
      left join public.profiles p on p.id = s.owner_id
     where r.recipient_id = auth.uid()
       and pos.updated_at > now() - interval '5 minutes'
     order by pos.updated_at desc;
$$;

revoke all on function public.start_live_location_session(uuid[], integer) from public, anon;
revoke all on function public.update_live_location_position(uuid, double precision, double precision, real) from public, anon;
revoke all on function public.stop_live_location_session(uuid) from public, anon;
revoke all on function public.get_my_active_live_location_session() from public, anon;
revoke all on function public.get_active_live_locations() from public, anon;

grant execute on function public.start_live_location_session(uuid[], integer) to authenticated;
grant execute on function public.update_live_location_position(uuid, double precision, double precision, real) to authenticated;
grant execute on function public.stop_live_location_session(uuid) to authenticated;
grant execute on function public.get_my_active_live_location_session() to authenticated;
grant execute on function public.get_active_live_locations() to authenticated;
