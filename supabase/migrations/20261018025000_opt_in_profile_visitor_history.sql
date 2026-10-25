-- destructive-change-reviewed: Opting out explicitly deletes the account's old
-- identifiable visit events and legacy profile-view activity. Trigger replacement
-- changes only profile-view activity and enforces mutual privacy consent.
-- rollback-plan: In staging, drop the new legacy consent triggers first; restore
-- previous create_activity behavior from the prior pinned migration if needed.
-- Drop new profile-visitor RPCs/tables only after exporting staging configuration
-- and reconciling client versions. Opt-out erasures are intentionally irreversible;
-- restoring deleted personal visit data is neither supported nor appropriate.
-- Profile visitor identities are explicitly opt-in and accessible only via
-- authenticated, account-bound SECURITY DEFINER RPCs. This does not reuse the
-- legacy weekly impression count or expose raw views through PostgREST.
begin;

create table if not exists public.profile_visit_settings (
  user_id uuid primary key references auth.users(id) on delete cascade,
  participate boolean not null default false,
  notify_visits boolean not null default false,
  updated_at timestamptz not null default now()
);

create table if not exists public.profile_visit_events (
  profile_id uuid not null references public.profiles(id) on delete cascade,
  viewer_id uuid not null references public.profiles(id) on delete cascade,
  last_visited_at timestamptz not null default now(),
  primary key (profile_id, viewer_id),
  constraint profile_visit_no_self check (profile_id <> viewer_id)
);

create index if not exists idx_profile_visit_events_recent
  on public.profile_visit_events (profile_id, last_visited_at desc);

create table if not exists public.profile_visit_receipts (
  user_id uuid primary key references auth.users(id) on delete cascade,
  last_seen_at timestamptz not null default '-infinity'::timestamptz
);

alter table public.profile_visit_settings enable row level security;
alter table public.profile_visit_events enable row level security;
alter table public.profile_visit_receipts enable row level security;

-- No direct access, even to an authenticated user. All queries check auth.uid().
revoke all on public.profile_visit_settings from public, anon, authenticated;
revoke all on public.profile_visit_events from public, anon, authenticated;
revoke all on public.profile_visit_receipts from public, anon, authenticated;

create or replace function public.get_my_profile_visit_settings()
returns jsonb language plpgsql stable security definer
set search_path = public, pg_temp as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  return coalesce(
    (select jsonb_build_object(
      'participate', s.participate, 'notify_visits', s.notify_visits
    ) from public.profile_visit_settings s where s.user_id = v_uid),
    '{"participate":false,"notify_visits":false}'::jsonb
  );
end; $$;

create or replace function public.set_my_profile_visit_settings(
  p_participate boolean, p_notify_visits boolean default false
)
returns jsonb language plpgsql security definer
set search_path = public, pg_temp as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_participate is null or p_notify_visits is null then
    raise exception 'INVALID_VISITOR_SETTINGS';
  end if;
  insert into public.profile_visit_settings(user_id, participate, notify_visits, updated_at)
  values (v_uid, p_participate, p_participate and p_notify_visits, now())
  on conflict (user_id) do update set
    participate = excluded.participate,
    notify_visits = excluded.notify_visits,
    updated_at = now();

  if not p_participate then
    -- Withdrawal removes existing identifiable history, including legacy alerts.
    delete from public.profile_visit_events
      where viewer_id = v_uid or profile_id = v_uid;
    delete from public.profile_visit_receipts where user_id = v_uid;
    delete from public.profile_views
      where viewer_id = v_uid or profile_id = v_uid;
    delete from public.activities
      where actor_id = v_uid
        and upper(coalesce(entity_type,'')) = 'PROFILE'
        and message = 'viewed your profile';
  end if;

  return jsonb_build_object('participate', p_participate,
                            'notify_visits', p_participate and p_notify_visits);
end; $$;

create or replace function public.record_opt_in_profile_visit(p_profile_id uuid)
returns boolean language plpgsql security definer
set search_path = public, pg_temp as $$
declare
  v_uid uuid := auth.uid();
  v_now timestamptz := now();
  v_prior timestamptz;
  v_notify boolean;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_profile_id is null or p_profile_id = v_uid then return false; end if;
  if not exists (select 1 from public.profiles p where p.id = p_profile_id) then
    return false;
  end if;
  -- Both sides must explicitly participate before identities are recorded.
  if not exists (select 1 from public.profile_visit_settings s
                 where s.user_id = v_uid and s.participate) then return false; end if;
  select s.notify_visits into v_notify
  from public.profile_visit_settings s
  where s.user_id = p_profile_id and s.participate;
  if not found then return false; end if;

  if exists (
    select 1 from public.blocks b
    where (b.blocker_id = v_uid and b.blocked_id = p_profile_id)
       or (b.blocker_id = p_profile_id and b.blocked_id = v_uid)
  ) then return false; end if;

  -- Serialize writes for this profile+viewer pair, preventing races/double alerts.
  perform pg_advisory_xact_lock(
    hashtextextended(p_profile_id::text || ':' || v_uid::text, 0)
  );
  select e.last_visited_at into v_prior
    from public.profile_visit_events e
    where e.profile_id = p_profile_id and e.viewer_id = v_uid;
  if v_prior is not null and v_prior > v_now - interval '6 hours' then return false; end if;

  insert into public.profile_visit_events(profile_id, viewer_id, last_visited_at)
  values (p_profile_id, v_uid, v_now)
  on conflict(profile_id, viewer_id) do update
  set last_visited_at = excluded.last_visited_at;

  -- Preserve the existing weekly profile-view analytics source and its
  -- cache trigger. This insert is also guarded by reciprocal consent.
  insert into public.profile_views(profile_id, viewer_id)
  values (p_profile_id, v_uid);

  -- Existing activity -> push dispatch remains subject to the owner's opt-in.
  -- Maximum one notification from this viewer per recipient per 24 hours.
  if v_notify and (v_prior is null or v_prior <= v_now - interval '24 hours') then
    insert into public.activities(
      recipient_id, actor_id, activity_type, entity_type, entity_id, message
    ) values (
      p_profile_id, v_uid, 'ALL', 'PROFILE', p_profile_id, 'viewed your profile'
    );
  end if;
  return true;
end; $$;

-- Exactly one non-mutating snapshot for a profile owner; private/blocked
-- visitors are never returned. Use snapshot watermark for race-safe receipts.
create or replace function public.list_my_profile_visitors()
returns jsonb language plpgsql stable security definer
set search_path = public, pg_temp as $$
declare
  v_uid uuid := auth.uid();
  v_time timestamptz := now();
  v_active boolean;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select s.participate into v_active from public.profile_visit_settings s where s.user_id = v_uid;
  if coalesce(v_active, false) = false then
    return jsonb_build_object('enabled', false, 'seen_through', v_time, 'visitors', '[]'::jsonb);
  end if;
  return jsonb_build_object(
    'enabled', true,
    'seen_through', v_time,
    'visitors', coalesce((
      select jsonb_agg(to_jsonb(q) order by q.last_visited_at desc)
      from (
        select p.id, p.username, p.full_name, p.avatar_url, e.last_visited_at
        from public.profile_visit_events e
        join public.profiles p on p.id = e.viewer_id
        join public.profile_visit_settings s on s.user_id = e.viewer_id and s.participate
        where e.profile_id = v_uid and e.last_visited_at <= v_time
          and not exists (
            select 1 from public.blocks b
            where (b.blocker_id = v_uid and b.blocked_id = e.viewer_id)
               or (b.blocker_id = e.viewer_id and b.blocked_id = v_uid)
          )
        order by e.last_visited_at desc
        limit 100
      ) q
    ), '[]'::jsonb)
  );
end; $$;

create or replace function public.count_my_new_profile_visitors()
returns integer language plpgsql stable security definer
set search_path = public, pg_temp as $$
declare v_uid uuid := auth.uid(); v_count integer;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if not exists (select 1 from public.profile_visit_settings s
                 where s.user_id = v_uid and s.participate) then return 0; end if;
  select count(*)::integer into v_count
  from public.profile_visit_events e
  join public.profile_visit_settings s on s.user_id = e.viewer_id and s.participate
  where e.profile_id = v_uid
    and e.last_visited_at > coalesce((
      select r.last_seen_at from public.profile_visit_receipts r where r.user_id = v_uid
    ), '-infinity'::timestamptz)
    and not exists (
      select 1 from public.blocks b
      where (b.blocker_id = v_uid and b.blocked_id = e.viewer_id)
         or (b.blocker_id = e.viewer_id and b.blocked_id = v_uid)
    );
  return coalesce(v_count, 0);
end; $$;

create or replace function public.ack_my_profile_visitors_seen(p_seen_through timestamptz)
returns boolean language plpgsql security definer
set search_path = public, pg_temp as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_seen_through is null or p_seen_through > now() + interval '2 seconds' then
    raise exception 'INVALID_VISITOR_RECEIPT';
  end if;
  if not exists (select 1 from public.profile_visit_settings s
                 where s.user_id = v_uid and s.participate) then return false; end if;
  insert into public.profile_visit_receipts(user_id, last_seen_at)
  values (v_uid, least(now(), p_seen_through))
  on conflict(user_id) do update
  set last_seen_at = greatest(public.profile_visit_receipts.last_seen_at, excluded.last_seen_at);
  return true;
end; $$;

-- Legacy Android versions may still use create_activity(PROFILE). Stop those
-- paths leaking visitor identity when either participant has opted out.
create or replace function public.guard_unconsented_legacy_profile_visit()
returns trigger language plpgsql security definer
set search_path = public, pg_temp as $$
declare v_owner uuid; v_viewer uuid;
begin
  if tg_table_name = 'profile_views' then
    v_owner := new.profile_id;
    v_viewer := new.viewer_id;
  else
    if upper(coalesce(new.entity_type, '')) <> 'PROFILE'
       or new.message <> 'viewed your profile' then return new; end if;
    v_owner := new.recipient_id;
    v_viewer := new.actor_id;
  end if;
  if v_owner is null or v_viewer is null or v_owner = v_viewer then return null; end if;
  if not exists (
    select 1 from public.profile_visit_settings s
    where s.user_id = v_owner and s.participate
  ) or not exists (
    select 1 from public.profile_visit_settings s
    where s.user_id = v_viewer and s.participate
  ) then return null; end if;
  if exists (
    select 1 from public.blocks b
    where (b.blocker_id = v_viewer and b.blocked_id = v_owner)
       or (b.blocker_id = v_owner and b.blocked_id = v_viewer)
  ) then return null; end if;
  -- A visit may be visible in owner-only history without generating alerts.
  -- Old clients must respect the owner's separate notify_visits toggle too.
  if tg_table_name = 'activities' and not exists (
    select 1 from public.profile_visit_settings s
    where s.user_id = v_owner and s.participate and s.notify_visits
  ) then return null; end if;
  return new;
end; $$;

revoke all on function public.guard_unconsented_legacy_profile_visit() from public, anon, authenticated;

drop trigger if exists trg_profile_visit_legacy_consent
  on public.profile_views;
create trigger trg_profile_visit_legacy_consent before insert
  on public.profile_views for each row
  execute function public.guard_unconsented_legacy_profile_visit();

drop trigger if exists trg_profile_activity_legacy_consent
  on public.activities;
create trigger trg_profile_activity_legacy_consent before insert
  on public.activities for each row
  execute function public.guard_unconsented_legacy_profile_visit();

revoke all on function public.get_my_profile_visit_settings() from public, anon;
revoke all on function public.set_my_profile_visit_settings(boolean, boolean) from public, anon;
revoke all on function public.record_opt_in_profile_visit(uuid) from public, anon;
revoke all on function public.list_my_profile_visitors() from public, anon;
revoke all on function public.count_my_new_profile_visitors() from public, anon;
revoke all on function public.ack_my_profile_visitors_seen(timestamptz) from public, anon;

grant execute on function public.get_my_profile_visit_settings() to authenticated;
grant execute on function public.set_my_profile_visit_settings(boolean, boolean) to authenticated;
grant execute on function public.record_opt_in_profile_visit(uuid) to authenticated;
grant execute on function public.list_my_profile_visitors() to authenticated;
grant execute on function public.count_my_new_profile_visitors() to authenticated;
grant execute on function public.ack_my_profile_visitors_seen(timestamptz) to authenticated;

-- Keep PostgREST service-role grants explicit under the October 30 change.
grant all on public.profile_visit_settings, public.profile_visit_events, public.profile_visit_receipts to service_role;

commit;
