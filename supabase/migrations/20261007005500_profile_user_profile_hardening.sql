-- destructive-change-reviewed
-- rollback-plan: Restore the previous profile policies/schema from backup, then drop only the profile hardening objects/columns introduced by this migration after confirming no newer migration depends on them.
begin;

alter table public.profiles
    add column if not exists email_visibility text not null default 'PRIVATE',
    add column if not exists phone_visibility text not null default 'PRIVATE',
    add column if not exists whatsapp_visibility text not null default 'PRIVATE',
    add column if not exists presence_visibility text not null default 'PUBLIC';

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'profiles_email_visibility_check') then
    alter table public.profiles add constraint profiles_email_visibility_check
      check (email_visibility in ('PUBLIC','FOLLOWERS','PRIVATE'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'profiles_phone_visibility_check') then
    alter table public.profiles add constraint profiles_phone_visibility_check
      check (phone_visibility in ('PUBLIC','FOLLOWERS','PRIVATE'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'profiles_whatsapp_visibility_check') then
    alter table public.profiles add constraint profiles_whatsapp_visibility_check
      check (whatsapp_visibility in ('PUBLIC','FOLLOWERS','PRIVATE'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'profiles_presence_visibility_check') then
    alter table public.profiles add constraint profiles_presence_visibility_check
      check (presence_visibility in ('PUBLIC','FOLLOWERS','PRIVATE'));
  end if;
end $$;

create table if not exists public.profile_notification_preferences (
    subscriber_id uuid not null references public.profiles(id) on delete cascade,
    profile_id uuid not null references public.profiles(id) on delete cascade,
    mode text not null default 'OFF' check (mode in ('OFF','ALL','REELS','IMPORTANT')),
    updated_at timestamptz not null default now(),
    primary key (subscriber_id, profile_id),
    check (subscriber_id <> profile_id)
);
alter table public.profile_notification_preferences enable row level security;
revoke all on public.profile_notification_preferences from anon;
revoke all on public.profile_notification_preferences from authenticated;
grant select, insert, update, delete on public.profile_notification_preferences to authenticated;
drop policy if exists profile_notification_preferences_own on public.profile_notification_preferences;
create policy profile_notification_preferences_own
on public.profile_notification_preferences
for all to authenticated
using (subscriber_id = (select auth.uid()))
with check (subscriber_id = (select auth.uid()));

-- Reuse the existing public.muted_users table; do not create a second mute system.

create table if not exists public.profile_follower_daily_snapshots (
    profile_id uuid not null references public.profiles(id) on delete cascade,
    snapshot_date date not null default current_date,
    follower_count integer not null default 0 check (follower_count >= 0),
    primary key (profile_id, snapshot_date)
);
alter table public.profile_follower_daily_snapshots enable row level security;
revoke all on public.profile_follower_daily_snapshots from anon;
revoke all on public.profile_follower_daily_snapshots from authenticated;
grant select on public.profile_follower_daily_snapshots to authenticated;
drop policy if exists profile_follower_snapshots_read on public.profile_follower_daily_snapshots;
create policy profile_follower_snapshots_read
on public.profile_follower_daily_snapshots
for select to authenticated
using (profile_id = (select auth.uid()));

create or replace function private.capture_profile_follower_snapshot(p_profile_id uuid)
returns void
language sql
security definer
set search_path = public, pg_temp
as $$
  insert into public.profile_follower_daily_snapshots(profile_id, snapshot_date, follower_count)
  select p.id, current_date, greatest(coalesce(p.follower_count, 0), 0)
  from public.profiles p
  where p.id = p_profile_id
  on conflict (profile_id, snapshot_date)
  do update set follower_count = excluded.follower_count;
$$;
revoke all on function private.capture_profile_follower_snapshot(uuid) from public, anon, authenticated;

create or replace function private.capture_follow_snapshot_trigger()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  perform private.capture_profile_follower_snapshot(coalesce(new.following_id, old.following_id));
  return coalesce(new, old);
end;
$$;
revoke all on function private.capture_follow_snapshot_trigger() from public, anon, authenticated;

drop trigger if exists trg_capture_profile_follower_snapshot on public.follows;
create trigger trg_capture_profile_follower_snapshot
after insert or delete on public.follows
for each row execute function private.capture_follow_snapshot_trigger();

insert into public.profile_follower_daily_snapshots(profile_id, snapshot_date, follower_count)
select id, current_date, greatest(coalesce(follower_count,0),0)
from public.profiles
on conflict (profile_id, snapshot_date) do update
set follower_count = excluded.follower_count;

create or replace function public.get_profile_follower_history(p_profile_id uuid, p_days integer default 30)
returns table(snapshot_date date, follower_count integer)
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
  select s.snapshot_date, s.follower_count
  from public.profile_follower_daily_snapshots s
  where s.profile_id = p_profile_id
    and p_profile_id = (select auth.uid())
    and s.snapshot_date >= current_date - greatest(1, least(coalesce(p_days,30), 90))
  order by s.snapshot_date;
$$;
revoke all on function public.get_profile_follower_history(uuid, integer) from public, anon;
grant execute on function public.get_profile_follower_history(uuid, integer) to authenticated;

create or replace function public.set_profile_notification_preference(p_profile_id uuid, p_mode text)
returns text
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_mode text := upper(coalesce(trim(p_mode),'OFF'));
begin
  if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_profile_id = auth.uid() then raise exception 'CANNOT_SUBSCRIBE_SELF'; end if;
  if v_mode not in ('OFF','ALL','REELS','IMPORTANT') then raise exception 'INVALID_MODE'; end if;

  if v_mode = 'OFF' then
    delete from public.profile_notification_preferences
    where subscriber_id = auth.uid() and profile_id = p_profile_id;
  else
    insert into public.profile_notification_preferences(subscriber_id, profile_id, mode, updated_at)
    values(auth.uid(), p_profile_id, v_mode, now())
    on conflict (subscriber_id, profile_id)
    do update set mode = excluded.mode, updated_at = now();
  end if;
  return v_mode;
end;
$$;
revoke all on function public.set_profile_notification_preference(uuid,text) from public, anon;
grant execute on function public.set_profile_notification_preference(uuid,text) to authenticated;

create or replace function public.get_profile_notification_preference(p_profile_id uuid)
returns text
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
  select coalesce((
    select mode from public.profile_notification_preferences
    where subscriber_id = auth.uid() and profile_id = p_profile_id
  ), 'OFF');
$$;
revoke all on function public.get_profile_notification_preference(uuid) from public, anon;
grant execute on function public.get_profile_notification_preference(uuid) to authenticated;

create or replace function public.set_profile_muted(p_profile_id uuid, p_muted boolean)
returns boolean
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
begin
  if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_profile_id = auth.uid() then raise exception 'CANNOT_MUTE_SELF'; end if;
  if coalesce(p_muted,false) then
    insert into public.muted_users(user_id, muted_id)
    values(auth.uid(), p_profile_id)
    on conflict do nothing;
  else
    delete from public.muted_users
    where user_id = auth.uid() and muted_id = p_profile_id;
  end if;
  return coalesce(p_muted,false);
end;
$$;
revoke all on function public.set_profile_muted(uuid,boolean) from public, anon;
grant execute on function public.set_profile_muted(uuid,boolean) to authenticated;

create or replace function public.is_profile_muted(p_profile_id uuid)
returns boolean
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
  select exists(
    select 1 from public.muted_users
    where user_id = auth.uid() and muted_id = p_profile_id
  );
$$;
revoke all on function public.is_profile_muted(uuid) from public, anon;
grant execute on function public.is_profile_muted(uuid) to authenticated;

create or replace function public.set_profile_pin(p_content_id uuid, p_pinned boolean)
returns boolean
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_owner uuid;
  v_pinned_count integer;
begin
  if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select user_id into v_owner from public.feed_posts
  where id = p_content_id and is_active = true;
  if v_owner is null then raise exception 'CONTENT_NOT_FOUND'; end if;
  if v_owner <> auth.uid() then raise exception 'NOT_CONTENT_OWNER'; end if;

  if coalesce(p_pinned,false) then
    select count(*) into v_pinned_count
    from public.feed_posts
    where user_id = auth.uid() and is_active = true and coalesce(is_pinned,false) = true
      and id <> p_content_id;
    if v_pinned_count >= 3 then raise exception 'PROFILE_PIN_LIMIT_REACHED'; end if;
  end if;

  update public.feed_posts
  set is_pinned = coalesce(p_pinned,false), updated_at = now()
  where id = p_content_id and user_id = auth.uid();
  return coalesce(p_pinned,false);
end;
$$;
revoke all on function public.set_profile_pin(uuid,boolean) from public, anon;
grant execute on function public.set_profile_pin(uuid,boolean) to authenticated;

create or replace function public.get_profile_connections(
    p_profile_id uuid,
    p_kind text,
    p_limit integer default 100
)
returns table(
    id uuid,
    username text,
    full_name text,
    avatar_url text,
    university text,
    faculty text,
    department text,
    academic_level text,
    verification_badge text,
    is_verified boolean,
    online_now boolean,
    last_seen_at timestamptz
)
language sql
stable
security definer
set search_path = public, pg_temp
as $
  select p.id, p.username, p.full_name, p.avatar_url, p.university, p.faculty,
         p.department, p.academic_level, p.verification_badge, p.is_verified,
         case
           when p.id = auth.uid() then p.online_now
           when p.presence_visibility = 'PUBLIC' then p.online_now
           when p.presence_visibility = 'FOLLOWERS' and exists(
              select 1 from public.follows f
              where f.follower_id = auth.uid() and f.following_id = p.id
           ) then p.online_now
           else false
         end,
         case
           when p.id = auth.uid() then p.last_seen_at
           when p.presence_visibility = 'PUBLIC' then p.last_seen_at
           when p.presence_visibility = 'FOLLOWERS' and exists(
              select 1 from public.follows f
              where f.follower_id = auth.uid() and f.following_id = p.id
           ) then p.last_seen_at
           else null
         end
  from public.profiles p
  where auth.uid() is not null
    and p.id in (
    select case
      when upper(coalesce(p_kind,'')) = 'FOLLOWERS' then f.follower_id
      else f.following_id
    end
    from public.follows f
    where (upper(coalesce(p_kind,'')) = 'FOLLOWERS' and f.following_id = p_profile_id)
       or (upper(coalesce(p_kind,'')) = 'FOLLOWING' and f.follower_id = p_profile_id)
  )
  and not exists (
    select 1 from public.blocks b
    where (b.blocker_id = auth.uid() and b.blocked_id = p.id)
       or (b.blocker_id = p.id and b.blocked_id = auth.uid())
  )
  order by lower(p.username), p.id
  limit greatest(1, least(coalesce(p_limit,100), 200));
$$;
revoke all on function public.get_profile_connections(uuid,text,integer) from public, anon;
grant execute on function public.get_profile_connections(uuid,text,integer) to authenticated;

create or replace function private.notify_profile_subscribers_after_feed_post()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_creator_name text;
  v_creator_username text;
  v_is_vip boolean := false;
begin
  if not coalesce(new.is_active, true)
     or upper(coalesce(new.audience, 'EVERYONE')) <> 'EVERYONE'
     or coalesce(new.is_sponsored, false) then
    return new;
  end if;

  select
    coalesce(nullif(trim(p.full_name), ''), nullif(trim(p.username), ''), 'Someone'),
    coalesce(nullif(trim(p.username), ''), 'user'),
    coalesce(p.blink_vip_until > now(), false)
  into v_creator_name, v_creator_username, v_is_vip
  from public.profiles p
  where p.id = new.user_id;

  insert into public.notifications(
    user_id,
    actor_id,
    type,
    post_id,
    text,
    sub_text,
    actor_is_vip,
    vip_priority,
    target_type,
    target_id,
    metadata
  )
  select
    pref.subscriber_id,
    new.user_id,
    'system'::public.notification_type_enum,
    new.id,
    case when coalesce(new.is_reel, false)
      then v_creator_name || ' posted a new reel'
      else v_creator_name || ' posted a new post'
    end,
    '@' || v_creator_username,
    v_is_vip,
    v_is_vip,
    case when coalesce(new.is_reel, false) then 'REEL' else 'POST' end,
    new.id,
    jsonb_build_object(
      'source', 'profile_subscription',
      'profile_id', new.user_id,
      'mode', pref.mode
    )
  from public.profile_notification_preferences pref
  where pref.profile_id = new.user_id
    and (
      pref.mode = 'ALL'
      or (pref.mode = 'REELS' and coalesce(new.is_reel, false))
    )
    and not exists (
      select 1
      from public.blocks b
      where (b.blocker_id = pref.subscriber_id and b.blocked_id = new.user_id)
         or (b.blocker_id = new.user_id and b.blocked_id = pref.subscriber_id)
    );

  return new;
end;
$$;
revoke all on function private.notify_profile_subscribers_after_feed_post()
  from public, anon, authenticated;

drop trigger if exists trg_notify_profile_subscribers_after_feed_post
  on public.feed_posts;
create trigger trg_notify_profile_subscribers_after_feed_post
after insert on public.feed_posts
for each row
execute function private.notify_profile_subscribers_after_feed_post();

create or replace function private.notify_important_profile_subscribers_after_pin()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_creator_name text;
  v_creator_username text;
  v_is_vip boolean := false;
begin
  if not coalesce(new.is_pinned, false)
     or coalesce(old.is_pinned, false)
     or not coalesce(new.is_active, true)
     or upper(coalesce(new.audience, 'EVERYONE')) <> 'EVERYONE'
     or coalesce(new.is_sponsored, false) then
    return new;
  end if;

  select
    coalesce(nullif(trim(p.full_name), ''), nullif(trim(p.username), ''), 'Someone'),
    coalesce(nullif(trim(p.username), ''), 'user'),
    coalesce(p.blink_vip_until > now(), false)
  into v_creator_name, v_creator_username, v_is_vip
  from public.profiles p
  where p.id = new.user_id;

  insert into public.notifications(
    user_id,
    actor_id,
    type,
    post_id,
    text,
    sub_text,
    actor_is_vip,
    vip_priority,
    target_type,
    target_id,
    metadata
  )
  select
    pref.subscriber_id,
    new.user_id,
    'system'::public.notification_type_enum,
    new.id,
    v_creator_name || ' pinned an important update',
    '@' || v_creator_username,
    v_is_vip,
    true,
    case when coalesce(new.is_reel, false) then 'REEL' else 'POST' end,
    new.id,
    jsonb_build_object(
      'source', 'profile_subscription',
      'profile_id', new.user_id,
      'mode', 'IMPORTANT'
    )
  from public.profile_notification_preferences pref
  where pref.profile_id = new.user_id
    and pref.mode = 'IMPORTANT'
    and not exists (
      select 1
      from public.blocks b
      where (b.blocker_id = pref.subscriber_id and b.blocked_id = new.user_id)
         or (b.blocker_id = new.user_id and b.blocked_id = pref.subscriber_id)
    );

  return new;
end;
$$;
revoke all on function private.notify_important_profile_subscribers_after_pin()
  from public, anon, authenticated;

drop trigger if exists trg_notify_important_profile_subscribers_after_pin
  on public.feed_posts;
create trigger trg_notify_important_profile_subscribers_after_pin
after update of is_pinned on public.feed_posts
for each row
execute function private.notify_important_profile_subscribers_after_pin();

-- Safe public profile projections. Raw public.profiles rows become owner-only below.
create or replace function public.get_public_profiles_by_ids(p_ids uuid[])
returns table(
  id uuid,
  username text,
  avatar_url text,
  is_verified boolean,
  verification_badge text,
  full_name text,
  blink_vip_until timestamptz,
  university text,
  faculty text
)
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select
    p.id,
    p.username,
    p.avatar_url,
    coalesce(p.is_verified, false),
    coalesce(p.verification_badge, p.verification_tier::text, ''),
    coalesce(p.full_name, p.username, ''),
    p.blink_vip_until,
    p.university,
    p.faculty
  from public.profiles p
  where auth.uid() is not null
    and p.id = any(coalesce(p_ids, array[]::uuid[]))
    and not exists (
      select 1 from public.blocks b
      where (b.blocker_id = auth.uid() and b.blocked_id = p.id)
         or (b.blocker_id = p.id and b.blocked_id = auth.uid())
    );
$$;
revoke all on function public.get_public_profiles_by_ids(uuid[]) from public, anon;
grant execute on function public.get_public_profiles_by_ids(uuid[]) to authenticated;

drop function if exists public.search_profiles_page(text, integer, text, uuid);
create function public.search_profiles_page(
  p_query text,
  p_limit integer default 30,
  p_after_username text default null,
  p_after_id uuid default null
)
returns table(
  id uuid,
  full_name text,
  name text,
  username text,
  handle text,
  avatar_url text,
  university text,
  faculty text,
  department text,
  academic_level text,
  bio text,
  professional_headline text,
  is_verified boolean,
  verification_badge text,
  verification_tier text,
  follower_count integer,
  following_count integer,
  posts_count integer,
  online_now boolean,
  is_online boolean,
  last_seen_at timestamptz,
  points integer,
  total_xp bigint,
  xp_level integer,
  created_at timestamptz,
  blink_vip_until timestamptz,
  verified_at timestamptz,
  daily_streak integer,
  world_rank integer,
  campus_rank integer
)
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  with q as (
    select lower(trim(coalesce(p_query,''))) as term
  )
  select
    p.id,
    coalesce(p.full_name, p.username, ''),
    coalesce(p.name, p.full_name, p.username, ''),
    p.username,
    coalesce(p.handle, p.username, ''),
    p.avatar_url,
    p.university,
    p.faculty,
    p.department,
    p.academic_level,
    p.bio,
    p.professional_headline,
    coalesce(p.is_verified, false),
    coalesce(p.verification_badge, p.verification_tier::text, ''),
    coalesce(p.verification_tier::text, ''),
    greatest(coalesce(p.follower_count,0),0),
    greatest(coalesce(p.following_count,0),0),
    greatest(coalesce(p.posts_count,0),0),
    case
      when p.id = auth.uid() then coalesce(p.online_now,false)
      when p.presence_visibility = 'PUBLIC' then coalesce(p.online_now,false)
      when p.presence_visibility = 'FOLLOWERS' and exists(
        select 1 from public.follows f
        where f.follower_id = auth.uid() and f.following_id = p.id
      ) then coalesce(p.online_now,false)
      else false
    end,
    case
      when p.id = auth.uid() then coalesce(p.is_online,false)
      when p.presence_visibility = 'PUBLIC' then coalesce(p.is_online,false)
      when p.presence_visibility = 'FOLLOWERS' and exists(
        select 1 from public.follows f
        where f.follower_id = auth.uid() and f.following_id = p.id
      ) then coalesce(p.is_online,false)
      else false
    end,
    case
      when p.id = auth.uid() then p.last_seen_at
      when p.presence_visibility = 'PUBLIC' then p.last_seen_at
      when p.presence_visibility = 'FOLLOWERS' and exists(
        select 1 from public.follows f
        where f.follower_id = auth.uid() and f.following_id = p.id
      ) then p.last_seen_at
      else null
    end,
    greatest(coalesce(p.points,0),0),
    greatest(coalesce(p.total_xp,0),0),
    greatest(coalesce(p.xp_level,1),1),
    p.created_at,
    p.blink_vip_until,
    p.verified_at,
    greatest(coalesce(p.daily_streak,0),0),
    greatest(coalesce(p.world_rank,0),0),
    greatest(coalesce(p.campus_rank,0),0)
  from public.profiles p, q
  where auth.uid() is not null
    and q.term <> ''
    and nullif(trim(p.username),'') is not null
    and (
      lower(coalesce(p.username,'')) like '%' || q.term || '%'
      or lower(coalesce(p.full_name,'')) like '%' || q.term || '%'
      or lower(coalesce(p.university,'')) like '%' || q.term || '%'
      or lower(coalesce(p.faculty,'')) like '%' || q.term || '%'
      or lower(coalesce(p.department,'')) like '%' || q.term || '%'
    )
    and (
      p_after_username is null
      or lower(p.username) > lower(p_after_username)
      or (lower(p.username) = lower(p_after_username) and (p_after_id is null or p.id > p_after_id))
    )
    and not exists (
      select 1 from public.blocks b
      where (b.blocker_id = auth.uid() and b.blocked_id = p.id)
         or (b.blocker_id = p.id and b.blocked_id = auth.uid())
    )
  order by lower(p.username), p.id
  limit greatest(1, least(coalesce(p_limit,30),60));
$$;
revoke all on function public.search_profiles_page(text,integer,text,uuid) from public, anon;
grant execute on function public.search_profiles_page(text,integer,text,uuid) to authenticated;

create or replace function public.get_profile_directory(p_limit integer default 100)
returns table(
  id uuid,
  full_name text,
  name text,
  username text,
  handle text,
  avatar_url text,
  university text,
  faculty text,
  department text,
  academic_level text,
  bio text,
  is_verified boolean,
  verification_badge text,
  verification_tier text,
  follower_count integer,
  following_count integer,
  posts_count integer,
  online_now boolean,
  is_online boolean,
  last_seen_at timestamptz,
  points integer,
  total_xp bigint,
  xp_level integer,
  created_at timestamptz,
  blink_vip_until timestamptz,
  verified_at timestamptz
)
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select
    p.id,
    coalesce(p.full_name, p.username, ''),
    coalesce(p.name, p.full_name, p.username, ''),
    p.username,
    coalesce(p.handle, p.username, ''),
    p.avatar_url,
    p.university,
    p.faculty,
    p.department,
    p.academic_level,
    p.bio,
    coalesce(p.is_verified,false),
    coalesce(p.verification_badge, p.verification_tier::text, ''),
    coalesce(p.verification_tier::text, ''),
    greatest(coalesce(p.follower_count,0),0),
    greatest(coalesce(p.following_count,0),0),
    greatest(coalesce(p.posts_count,0),0),
    case
      when p.id = auth.uid() then coalesce(p.online_now,false)
      when p.presence_visibility = 'PUBLIC' then coalesce(p.online_now,false)
      when p.presence_visibility = 'FOLLOWERS' and exists(
        select 1 from public.follows f
        where f.follower_id = auth.uid() and f.following_id = p.id
      ) then coalesce(p.online_now,false)
      else false
    end,
    case
      when p.id = auth.uid() then coalesce(p.is_online,false)
      when p.presence_visibility = 'PUBLIC' then coalesce(p.is_online,false)
      when p.presence_visibility = 'FOLLOWERS' and exists(
        select 1 from public.follows f
        where f.follower_id = auth.uid() and f.following_id = p.id
      ) then coalesce(p.is_online,false)
      else false
    end,
    case
      when p.id = auth.uid() then p.last_seen_at
      when p.presence_visibility = 'PUBLIC' then p.last_seen_at
      when p.presence_visibility = 'FOLLOWERS' and exists(
        select 1 from public.follows f
        where f.follower_id = auth.uid() and f.following_id = p.id
      ) then p.last_seen_at
      else null
    end,
    greatest(coalesce(p.points,0),0),
    greatest(coalesce(p.total_xp,0),0),
    greatest(coalesce(p.xp_level,1),1),
    p.created_at,
    p.blink_vip_until,
    p.verified_at
  from public.profiles p
  where auth.uid() is not null
    and p.id <> auth.uid()
    and nullif(trim(p.username),'') is not null
    and not exists (
      select 1 from public.blocks b
      where (b.blocker_id = auth.uid() and b.blocked_id = p.id)
         or (b.blocker_id = p.id and b.blocked_id = auth.uid())
    )
  order by coalesce(p.online_now,false) desc, lower(p.username), p.id
  limit greatest(1, least(coalesce(p_limit,100),200));
$$;
revoke all on function public.get_profile_directory(integer) from public, anon;
grant execute on function public.get_profile_directory(integer) to authenticated;

-- SECURITY DEFINER is used only to construct a redacted projection from the legacy
-- profiles row. Execution is restricted to authenticated and auth.uid() is mandatory.
create or replace function public.get_profile_detail(p_identifier text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_viewer uuid := auth.uid();
  v_profile public.profiles%rowtype;
  v_is_following boolean := false;
  v_identifier text := lower(ltrim(trim(coalesce(p_identifier,'')), '@'));
begin
  if v_viewer is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if v_identifier = '' then return null; end if;

  select * into v_profile
  from public.profiles
  where lower(username) = v_identifier
     or id::text = v_identifier
  limit 1;

  if not found then return null; end if;

  if exists(
    select 1 from public.blocks b
    where (b.blocker_id=v_viewer and b.blocked_id=v_profile.id)
       or (b.blocker_id=v_profile.id and b.blocked_id=v_viewer)
  ) then return null; end if;

  select exists(
    select 1 from public.follows
    where follower_id = v_viewer and following_id = v_profile.id
  ) into v_is_following;

  return (to_jsonb(v_profile)
    - 'current_wallet_balance'
    - 'fcm_token'
    - 'profile_views_this_week'
    - 'campus_hostel_location')
    || jsonb_build_object(
      'email', case
        when v_viewer=v_profile.id then v_profile.email
        when v_profile.email_visibility='PUBLIC' then v_profile.email
        when v_profile.email_visibility='FOLLOWERS' and v_is_following then v_profile.email
        else ''
      end,
      'phone', case
        when v_viewer=v_profile.id then v_profile.phone
        when v_profile.phone_visibility='PUBLIC' then v_profile.phone
        when v_profile.phone_visibility='FOLLOWERS' and v_is_following then v_profile.phone
        else ''
      end,
      'whatsapp', case
        when v_viewer=v_profile.id then v_profile.whatsapp
        when v_profile.whatsapp_visibility='PUBLIC' then v_profile.whatsapp
        when v_profile.whatsapp_visibility='FOLLOWERS' and v_is_following then v_profile.whatsapp
        else ''
      end,
      'online_now', case
        when v_viewer=v_profile.id then v_profile.online_now
        when v_profile.presence_visibility='PUBLIC' then v_profile.online_now
        when v_profile.presence_visibility='FOLLOWERS' and v_is_following then v_profile.online_now
        else false
      end,
      'is_online', case
        when v_viewer=v_profile.id then v_profile.is_online
        when v_profile.presence_visibility='PUBLIC' then v_profile.is_online
        when v_profile.presence_visibility='FOLLOWERS' and v_is_following then v_profile.is_online
        else false
      end,
      'last_seen_at', case
        when v_viewer=v_profile.id then to_jsonb(v_profile.last_seen_at)
        when v_profile.presence_visibility='PUBLIC' then to_jsonb(v_profile.last_seen_at)
        when v_profile.presence_visibility='FOLLOWERS' and v_is_following then to_jsonb(v_profile.last_seen_at)
        else 'null'::jsonb
      end,
      'last_seen', case
        when v_viewer=v_profile.id then to_jsonb(v_profile.last_seen)
        when v_profile.presence_visibility='PUBLIC' then to_jsonb(v_profile.last_seen)
        when v_profile.presence_visibility='FOLLOWERS' and v_is_following then to_jsonb(v_profile.last_seen)
        else 'null'::jsonb
      end,
      'profile_views_this_week', case when v_viewer=v_profile.id then v_profile.profile_views_this_week else 0 end
    );
end;
$$;
revoke all on function public.get_profile_detail(text) from public, anon;
grant execute on function public.get_profile_detail(text) to authenticated;

-- Raw profile rows contain private/auth-adjacent fields. Public reads must use the
-- redacted functions above; authenticated users may select only their own raw row.
drop policy if exists profiles_select_authenticated on public.profiles;
drop policy if exists profiles_select_owner_only on public.profiles;
create policy profiles_select_owner_only
on public.profiles
for select to authenticated
using (id = (select auth.uid()));

notify pgrst, 'reload schema';
commit;
