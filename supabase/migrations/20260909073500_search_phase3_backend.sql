-- Blink Search Phase 3
-- Production-safe discovery contracts for Communities, Events, Pages/Brands,
-- Marketplace, Saved/Following search, synced history, graph ranking, distance,
-- real growth/trend deltas, reel transcript moments and optional image embeddings.

create extension if not exists pg_trgm with schema extensions;

-- ------------------------------------------------------------
-- Search-owned durable entities
-- ------------------------------------------------------------
create table if not exists public.search_history (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  query text not null check (char_length(query) between 1 and 200),
  normalized_query text generated always as (lower(btrim(query))) stored,
  category text not null default 'all',
  filters jsonb not null default '{}'::jsonb,
  search_count integer not null default 1 check (search_count > 0),
  pinned boolean not null default false,
  last_searched_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  unique (user_id, normalized_query, category)
);

create index if not exists search_history_user_recent_idx
  on public.search_history(user_id, pinned desc, last_searched_at desc);

alter table public.search_history enable row level security;
drop policy if exists search_history_owner_select on public.search_history;
create policy search_history_owner_select on public.search_history
  for select to authenticated using (user_id = auth.uid());
drop policy if exists search_history_owner_insert on public.search_history;
create policy search_history_owner_insert on public.search_history
  for insert to authenticated with check (user_id = auth.uid());
drop policy if exists search_history_owner_update on public.search_history;
create policy search_history_owner_update on public.search_history
  for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
drop policy if exists search_history_owner_delete on public.search_history;
create policy search_history_owner_delete on public.search_history
  for delete to authenticated using (user_id = auth.uid());

create table if not exists public.communities (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(btrim(name)) between 2 and 80),
  slug text,
  description text not null default '',
  avatar_url text,
  cover_url text,
  category text not null default 'General',
  university text not null default '',
  faculty text not null default '',
  department text not null default '',
  is_private boolean not null default false,
  is_verified boolean not null default false,
  member_count integer not null default 1 check (member_count >= 0),
  post_count integer not null default 0 check (post_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index if not exists communities_slug_unique_idx on public.communities(lower(slug)) where slug is not null;
create index if not exists communities_name_trgm_idx on public.communities using gin (name extensions.gin_trgm_ops);
create index if not exists communities_scope_idx on public.communities(university, faculty, is_private, created_at desc);

create table if not exists public.community_members (
  community_id uuid not null references public.communities(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'member' check (role in ('owner','admin','moderator','member')),
  status text not null default 'active' check (status in ('active','pending','blocked')),
  joined_at timestamptz not null default now(),
  primary key (community_id, user_id)
);
create index if not exists community_members_user_idx on public.community_members(user_id, status, joined_at desc);

create table if not exists public.events (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references auth.users(id) on delete cascade,
  community_id uuid references public.communities(id) on delete set null,
  title text not null check (char_length(btrim(title)) between 2 and 120),
  description text not null default '',
  banner_url text,
  venue text not null default '',
  location_label text not null default '',
  latitude double precision check (latitude is null or latitude between -90 and 90),
  longitude double precision check (longitude is null or longitude between -180 and 180),
  starts_at timestamptz not null,
  ends_at timestamptz,
  university text not null default '',
  faculty text not null default '',
  is_online boolean not null default false,
  external_url text,
  visibility text not null default 'public' check (visibility in ('public','campus','community','private')),
  status text not null default 'scheduled' check (status in ('draft','scheduled','live','ended','cancelled')),
  attendee_count integer not null default 0 check (attendee_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists events_title_trgm_idx on public.events using gin (title extensions.gin_trgm_ops);
create index if not exists events_upcoming_idx on public.events(status, starts_at, university);

create table if not exists public.event_attendees (
  event_id uuid not null references public.events(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'going' check (status in ('going','interested')),
  created_at timestamptz not null default now(),
  primary key (event_id, user_id)
);
create index if not exists event_attendees_user_idx on public.event_attendees(user_id, created_at desc);

create table if not exists public.pages (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(btrim(name)) between 2 and 100),
  handle text not null check (char_length(btrim(handle)) between 2 and 50),
  page_type text not null default 'page' check (page_type in ('page','brand','organization')),
  description text not null default '',
  avatar_url text,
  cover_url text,
  category text not null default 'General',
  university text not null default '',
  website text,
  is_verified boolean not null default false,
  follower_count integer not null default 0 check (follower_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index if not exists pages_handle_unique_idx on public.pages(lower(handle));
create index if not exists pages_name_trgm_idx on public.pages using gin (name extensions.gin_trgm_ops);

create table if not exists public.page_followers (
  page_id uuid not null references public.pages(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (page_id, user_id)
);
create index if not exists page_followers_user_idx on public.page_followers(user_id, created_at desc);

-- ------------------------------------------------------------
-- Privacy-aware optional coordinates. Device coordinates are passed ephemerally
-- to search_discovery_v2; profile coordinates are only eligible when opted in.
-- ------------------------------------------------------------
alter table public.profiles add column if not exists discovery_latitude double precision;
alter table public.profiles add column if not exists discovery_longitude double precision;
alter table public.profiles add column if not exists discovery_location_enabled boolean not null default false;
alter table public.feed_posts add column if not exists latitude double precision;
alter table public.feed_posts add column if not exists longitude double precision;
alter table public.market_items add column if not exists latitude double precision;
alter table public.market_items add column if not exists longitude double precision;

-- ------------------------------------------------------------
-- Real snapshots for growth/trend deltas (never fabricated percentages)
-- ------------------------------------------------------------
create table if not exists public.profile_growth_snapshots (
  profile_id uuid not null references public.profiles(id) on delete cascade,
  bucket_start timestamptz not null,
  follower_count integer not null default 0,
  post_count integer not null default 0,
  primary key (profile_id, bucket_start)
);
create index if not exists profile_growth_snapshots_lookup_idx
  on public.profile_growth_snapshots(profile_id, bucket_start desc);

create table if not exists public.content_trend_snapshots (
  post_id uuid not null references public.feed_posts(id) on delete cascade,
  bucket_start timestamptz not null,
  view_count integer not null default 0,
  like_count integer not null default 0,
  comment_count integer not null default 0,
  share_count integer not null default 0,
  primary key (post_id, bucket_start)
);
create index if not exists content_trend_snapshots_lookup_idx
  on public.content_trend_snapshots(post_id, bucket_start desc);

alter table public.profile_growth_snapshots enable row level security;
alter table public.content_trend_snapshots enable row level security;
revoke all on public.profile_growth_snapshots from anon, authenticated;
revoke all on public.content_trend_snapshots from anon, authenticated;

create or replace function public.capture_discovery_snapshots()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_bucket timestamptz := date_trunc('hour', now());
begin
  insert into public.profile_growth_snapshots(profile_id, bucket_start, follower_count, post_count)
  select p.id, v_bucket, greatest(coalesce(p.follower_count,0),0), greatest(coalesce(p.posts_count,0),0)
  from public.profiles p
  on conflict (profile_id, bucket_start) do update
    set follower_count = excluded.follower_count,
        post_count = excluded.post_count;

  insert into public.content_trend_snapshots(post_id, bucket_start, view_count, like_count, comment_count, share_count)
  select fp.id, v_bucket,
         greatest(coalesce(fp.view_count,0),0),
         greatest(coalesce(fp.like_count,0),0),
         greatest(coalesce(fp.comment_count,0),0),
         greatest(coalesce(fp.share_count,0),0)
  from public.feed_posts fp
  where fp.is_active and not fp.is_flagged and fp.created_at >= now() - interval '30 days'
  on conflict (post_id, bucket_start) do update
    set view_count = excluded.view_count,
        like_count = excluded.like_count,
        comment_count = excluded.comment_count,
        share_count = excluded.share_count;
end;
$$;
revoke all on function public.capture_discovery_snapshots() from public, anon, authenticated;

-- pg_cron is already available in the live Blink project. Keep this guarded so local
-- Supabase environments without pg_cron can still apply the migration.
do $$
declare
  v_jobid bigint;
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    select jobid into v_jobid from cron.job where jobname = 'blink-discovery-snapshots-v1' limit 1;
    if v_jobid is not null then
      perform cron.unschedule(v_jobid);
    end if;
    perform cron.schedule(
      'blink-discovery-snapshots-v1',
      '*/15 * * * *',
      'select public.capture_discovery_snapshots();'
    );
  end if;
end;
$$;

-- ------------------------------------------------------------
-- Timestamped reel transcripts. A speech-to-text worker can populate this table;
-- search remains capability-gated until real segments exist.
-- ------------------------------------------------------------
create table if not exists public.reel_transcript_segments (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.feed_posts(id) on delete cascade,
  start_ms integer not null check (start_ms >= 0),
  end_ms integer not null check (end_ms > start_ms),
  transcript text not null check (char_length(btrim(transcript)) > 0),
  language text not null default 'und',
  confidence real,
  created_at timestamptz not null default now(),
  unique(post_id, start_ms, end_ms)
);
create index if not exists reel_transcript_post_time_idx on public.reel_transcript_segments(post_id, start_ms);
create index if not exists reel_transcript_text_trgm_idx on public.reel_transcript_segments using gin (transcript extensions.gin_trgm_ops);
alter table public.reel_transcript_segments enable row level security;
revoke all on public.reel_transcript_segments from anon, authenticated;

-- Optional pgvector storage. The migration succeeds even when the extension is not
-- available. UI reports image search as unavailable until embeddings truly exist.
do $$
begin
  if exists (select 1 from pg_available_extensions where name = 'vector') then
    execute 'create extension if not exists vector with schema extensions';
    execute $sql$
      create table if not exists public.search_image_embeddings (
        entity_type text not null check (entity_type in ('post','reel','market_item','profile','community','page','event')),
        entity_id uuid not null,
        image_url text not null,
        model text not null default 'blink-image-v1',
        embedding extensions.vector(512) not null,
        updated_at timestamptz not null default now(),
        primary key(entity_type, entity_id, image_url)
      )
    $sql$;
    execute 'alter table public.search_image_embeddings enable row level security';
    execute 'revoke all on public.search_image_embeddings from anon, authenticated';
  end if;
end;
$$;

-- ------------------------------------------------------------
-- Counts and membership actions
-- ------------------------------------------------------------
create or replace function public.sync_community_member_count()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.communities c
  set member_count = (select count(*)::int from public.community_members cm where cm.community_id = coalesce(new.community_id, old.community_id) and cm.status='active'),
      updated_at = now()
  where c.id = coalesce(new.community_id, old.community_id);
  return coalesce(new, old);
end;
$$;
drop trigger if exists community_member_count_trigger on public.community_members;
create trigger community_member_count_trigger
after insert or update or delete on public.community_members
for each row execute function public.sync_community_member_count();

create or replace function public.sync_event_attendee_count()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.events e
  set attendee_count = (select count(*)::int from public.event_attendees ea where ea.event_id = coalesce(new.event_id, old.event_id) and ea.status='going'),
      updated_at = now()
  where e.id = coalesce(new.event_id, old.event_id);
  return coalesce(new, old);
end;
$$;
drop trigger if exists event_attendee_count_trigger on public.event_attendees;
create trigger event_attendee_count_trigger
after insert or update or delete on public.event_attendees
for each row execute function public.sync_event_attendee_count();

create or replace function public.sync_page_follower_count()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.pages p
  set follower_count = (select count(*)::int from public.page_followers pf where pf.page_id = coalesce(new.page_id, old.page_id)),
      updated_at = now()
  where p.id = coalesce(new.page_id, old.page_id);
  return coalesce(new, old);
end;
$$;
drop trigger if exists page_follower_count_trigger on public.page_followers;
create trigger page_follower_count_trigger
after insert or delete on public.page_followers
for each row execute function public.sync_page_follower_count();

create or replace function public.toggle_community_membership_v2(p_community_id uuid)
returns boolean language plpgsql security definer set search_path = '' as $$
declare v_uid uuid := auth.uid(); v_joined boolean;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  if exists(select 1 from public.community_members where community_id=p_community_id and user_id=v_uid and status='active') then
    delete from public.community_members where community_id=p_community_id and user_id=v_uid and role <> 'owner';
    v_joined := false;
  else
    insert into public.community_members(community_id,user_id,role,status)
    values(p_community_id,v_uid,'member','active')
    on conflict(community_id,user_id) do update set status='active', joined_at=now();
    v_joined := true;
  end if;
  return v_joined;
end;
$$;

create or replace function public.toggle_event_attendance_v2(p_event_id uuid)
returns boolean language plpgsql security definer set search_path = '' as $$
declare v_uid uuid := auth.uid(); v_going boolean;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  if exists(select 1 from public.event_attendees where event_id=p_event_id and user_id=v_uid and status='going') then
    delete from public.event_attendees where event_id=p_event_id and user_id=v_uid;
    v_going := false;
  else
    insert into public.event_attendees(event_id,user_id,status)
    values(p_event_id,v_uid,'going')
    on conflict(event_id,user_id) do update set status='going';
    v_going := true;
  end if;
  return v_going;
end;
$$;

create or replace function public.toggle_page_follow_v2(p_page_id uuid)
returns boolean language plpgsql security definer set search_path = '' as $$
declare v_uid uuid := auth.uid(); v_following boolean;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  if exists(select 1 from public.page_followers where page_id=p_page_id and user_id=v_uid) then
    delete from public.page_followers where page_id=p_page_id and user_id=v_uid;
    v_following := false;
  else
    insert into public.page_followers(page_id,user_id) values(p_page_id,v_uid) on conflict do nothing;
    v_following := true;
  end if;
  return v_following;
end;
$$;

grant execute on function public.toggle_community_membership_v2(uuid) to authenticated;
grant execute on function public.toggle_event_attendance_v2(uuid) to authenticated;
grant execute on function public.toggle_page_follow_v2(uuid) to authenticated;

-- ------------------------------------------------------------
-- Entity RLS
-- ------------------------------------------------------------
alter table public.communities enable row level security;
alter table public.community_members enable row level security;
alter table public.events enable row level security;
alter table public.event_attendees enable row level security;
alter table public.pages enable row level security;
alter table public.page_followers enable row level security;

drop policy if exists communities_authenticated_read on public.communities;
create policy communities_authenticated_read on public.communities for select to authenticated using (
  not is_private or owner_id=auth.uid() or exists(
    select 1 from public.community_members cm where cm.community_id=id and cm.user_id=auth.uid() and cm.status='active'
  )
);
drop policy if exists communities_owner_write on public.communities;
create policy communities_owner_write on public.communities for all to authenticated using (owner_id=auth.uid()) with check (owner_id=auth.uid());

drop policy if exists community_members_authenticated_read on public.community_members;
create policy community_members_authenticated_read on public.community_members for select to authenticated using (
  user_id=auth.uid() or exists(select 1 from public.communities c where c.id=community_id and (not c.is_private or c.owner_id=auth.uid()))
);
drop policy if exists community_members_self_insert on public.community_members;
create policy community_members_self_insert on public.community_members for insert to authenticated with check (user_id=auth.uid());
drop policy if exists community_members_self_delete on public.community_members;
create policy community_members_self_delete on public.community_members for delete to authenticated using (user_id=auth.uid() and role <> 'owner');

drop policy if exists events_authenticated_read on public.events;
create policy events_authenticated_read on public.events for select to authenticated using (
  visibility in ('public','campus') or creator_id=auth.uid() or exists(select 1 from public.event_attendees ea where ea.event_id=id and ea.user_id=auth.uid())
);
drop policy if exists events_creator_write on public.events;
create policy events_creator_write on public.events for all to authenticated using (creator_id=auth.uid()) with check (creator_id=auth.uid());

drop policy if exists event_attendees_self on public.event_attendees;
create policy event_attendees_self on public.event_attendees for all to authenticated using (user_id=auth.uid()) with check (user_id=auth.uid());

drop policy if exists pages_authenticated_read on public.pages;
create policy pages_authenticated_read on public.pages for select to authenticated using (true);
drop policy if exists pages_owner_write on public.pages;
create policy pages_owner_write on public.pages for all to authenticated using (owner_id=auth.uid()) with check (owner_id=auth.uid());

drop policy if exists page_followers_self on public.page_followers;
create policy page_followers_self on public.page_followers for all to authenticated using (user_id=auth.uid()) with check (user_id=auth.uid());

grant select, insert, update, delete on public.search_history to authenticated;
grant select, insert, update, delete on public.communities, public.community_members, public.events, public.event_attendees, public.pages, public.page_followers to authenticated;

-- ------------------------------------------------------------
-- Synced search-history RPCs
-- ------------------------------------------------------------
create or replace function public.upsert_search_history_v2(
  p_query text,
  p_category text default 'all',
  p_filters jsonb default '{}'::jsonb,
  p_private boolean default false
)
returns public.search_history
language plpgsql security definer set search_path = '' as $$
declare v_row public.search_history; v_uid uuid := auth.uid(); v_query text := btrim(coalesce(p_query,''));
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  if p_private or v_query='' then return null; end if;
  if char_length(v_query)>200 then v_query := left(v_query,200); end if;
  insert into public.search_history(user_id,query,category,filters,last_searched_at)
  values(v_uid,v_query,lower(coalesce(nullif(btrim(p_category),''),'all')),coalesce(p_filters,'{}'::jsonb),now())
  on conflict(user_id,normalized_query,category) do update
    set query=excluded.query,
        filters=excluded.filters,
        search_count=public.search_history.search_count+1,
        last_searched_at=now()
  returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.get_search_history_v2(p_limit integer default 20)
returns setof public.search_history
language sql stable security definer set search_path = '' as $$
  select sh.* from public.search_history sh
  where sh.user_id=auth.uid() and sh.last_searched_at >= now()-interval '90 days'
  order by sh.pinned desc, sh.last_searched_at desc
  limit greatest(1,least(coalesce(p_limit,20),50));
$$;

grant execute on function public.upsert_search_history_v2(text,text,jsonb,boolean) to authenticated;
grant execute on function public.get_search_history_v2(integer) to authenticated;

create or replace function public.blink_distance_km(
  p_lat1 double precision, p_lng1 double precision,
  p_lat2 double precision, p_lng2 double precision
)
returns double precision
language sql immutable strict set search_path = '' as $$
  select 6371.0088 * 2 * asin(sqrt(
    power(sin(radians((p_lat2-p_lat1)/2)),2) +
    cos(radians(p_lat1))*cos(radians(p_lat2))*power(sin(radians((p_lng2-p_lng1)/2)),2)
  ));
$$;

-- ------------------------------------------------------------
-- Universal discovery RPC with keyset cursor pagination.
-- ------------------------------------------------------------
create or replace function public.search_discovery_v2(
  p_query text default '',
  p_types text[] default array['profile','post','reel','community','event','page','market_item']::text[],
  p_limit integer default 24,
  p_cursor_score numeric default null,
  p_cursor_type text default null,
  p_cursor_id uuid default null,
  p_as_of timestamptz default now(),
  p_following_only boolean default false,
  p_saved_only boolean default false,
  p_sort text default 'relevant',
  p_lat double precision default null,
  p_lng double precision default null
)
returns table(
  result_type text,
  result_id uuid,
  payload jsonb,
  relevance_score numeric,
  ranking_reason text,
  as_of timestamptz,
  mutual_count integer,
  distance_km double precision,
  trend_percent numeric,
  matched_moment_ms integer,
  is_saved boolean,
  is_following boolean
)
language sql
stable
security definer
set search_path = ''
as $$
with cfg as (
  select auth.uid() caller_id,
         lower(left(btrim(coalesce(p_query,'')),120)) term,
         least(coalesce(p_as_of,now()),now()+interval '1 minute') rank_as_of,
         lower(coalesce(nullif(btrim(p_sort),''),'relevant')) sort_mode,
         greatest(1,least(coalesce(p_limit,24),60)) page_size,
         coalesce(p_types,array[]::text[]) types
),
me as (
  select p.* from public.profiles p cross join cfg where p.id=cfg.caller_id
),
profile_base as (
  select p,
    coalesce((select count(*)::int from public.follows f1 join public.follows f2 on f2.follower_id=f1.following_id
      where f1.follower_id=cfg.caller_id and f2.following_id=p.id),0) mutuals,
    exists(select 1 from public.follows f where f.follower_id=cfg.caller_id and f.following_id=p.id) following,
    case when p.discovery_location_enabled then public.blink_distance_km(p_lat,p_lng,p.discovery_latitude,p.discovery_longitude) end dist,
    coalesce((
      select case when old.follower_count > 0 then round(((greatest(p.follower_count,0)-old.follower_count)::numeric/old.follower_count::numeric)*100,2) else 0 end
      from public.profile_growth_snapshots old
      where old.profile_id=p.id and old.bucket_start <= cfg.rank_as_of-interval '24 hours'
      order by old.bucket_start desc limit 1
    ),0) growth
  from public.profiles p cross join cfg
  where cfg.caller_id is not null
    and 'profile'=any(cfg.types)
    and p.id<>cfg.caller_id
    and nullif(btrim(p.username),'') is not null
    and not exists(select 1 from public.blocks b where (b.blocker_id=cfg.caller_id and b.blocked_id=p.id) or (b.blocker_id=p.id and b.blocked_id=cfg.caller_id))
    and (cfg.term='' or lower(coalesce(p.username,'')||' '||coalesce(p.full_name,'')||' '||coalesce(p.name,'')||' '||coalesce(p.bio,'')||' '||coalesce(p.university,'')||' '||coalesce(p.faculty,'')||' '||coalesce(p.department,'')) like '%'||cfg.term||'%'
      or extensions.similarity(lower(coalesce(p.username,'')),cfg.term)>=0.18
      or extensions.similarity(lower(coalesce(p.full_name,'')),cfg.term)>=0.18)
    and (not p_following_only or exists(select 1 from public.follows f where f.follower_id=cfg.caller_id and f.following_id=p.id))
    and not p_saved_only
),
profile_results as (
  select 'profile'::text result_type,pb.p.id result_id,
    jsonb_build_object('id',pb.p.id,'username',pb.p.username,'full_name',coalesce(nullif(pb.p.full_name,''),pb.p.name),'avatar_url',pb.p.avatar_url,
      'bio',pb.p.bio,'university',pb.p.university,'faculty',pb.p.faculty,'department',pb.p.department,'academic_level',pb.p.academic_level,
      'is_verified',pb.p.is_verified,'verification_badge',pb.p.verification_badge,'follower_count',pb.p.follower_count,'online_now',pb.p.online_now,
      'growth_percent',pb.growth,'mutual_count',pb.mutuals,'distance_km',pb.dist) payload,
    (case cfg.sort_mode
      when 'growing' then 1000+pb.growth::double precision
      when 'distance' then case when pb.dist is null then -100000 else 10000-pb.dist end
      when 'recent' then extract(epoch from pb.p.created_at)/1000000
      else (case when cfg.term='' then 0 else 40*extensions.similarity(lower(coalesce(pb.p.username,'')||' '||coalesce(pb.p.full_name,'')),cfg.term) end)
           +least(20,ln(1+greatest(pb.p.follower_count,0))*2)+least(20,pb.mutuals*4)+case when pb.p.is_verified then 4 else 0 end+greatest(-20,least(pb.growth::double precision,20)) end)::numeric score,
    case when pb.mutuals>0 then pb.mutuals||' mutual connection'||case when pb.mutuals=1 then '' else 's' end
         when pb.growth>0 then 'Growing '||pb.growth||'% in 24h'
         when pb.following then 'You follow this account' else 'Relevant profile' end reason,
    pb.mutuals,pb.dist,pb.growth::numeric,null::int,false,pb.following,pb.p.created_at
  from profile_base pb cross join cfg
),
content_base as (
  select fp,
    exists(select 1 from public.post_bookmarks b where b.user_id=cfg.caller_id and b.post_id=fp.id) saved,
    exists(select 1 from public.follows f where f.follower_id=cfg.caller_id and f.following_id=fp.user_id) following,
    public.blink_distance_km(p_lat,p_lng,fp.latitude,fp.longitude) dist,
    coalesce((select case when (old.view_count+old.like_count*2+old.comment_count*4+old.share_count*6)>0 then
      round((((greatest(fp.view_count,0)+greatest(fp.like_count,0)*2+greatest(fp.comment_count,0)*4+greatest(fp.share_count,0)*6)-
      (old.view_count+old.like_count*2+old.comment_count*4+old.share_count*6))::numeric /
      (old.view_count+old.like_count*2+old.comment_count*4+old.share_count*6)::numeric)*100,2) else 0 end
      from public.content_trend_snapshots old where old.post_id=fp.id and old.bucket_start<=cfg.rank_as_of-interval '24 hours'
      order by old.bucket_start desc limit 1),0) trend,
    (select rts.start_ms from public.reel_transcript_segments rts
      where rts.post_id=fp.id and cfg.term<>'' and lower(rts.transcript) like '%'||cfg.term||'%'
      order by extensions.similarity(lower(rts.transcript),cfg.term) desc,rts.start_ms limit 1) matched_ms
  from public.feed_posts fp cross join cfg
  where cfg.caller_id is not null
    and (case when fp.is_reel then 'reel' else 'post' end)=any(cfg.types)
    and fp.is_active and not fp.is_flagged and fp.created_at<=cfg.rank_as_of
    and (fp.expires_at is null or fp.expires_at>cfg.rank_as_of)
    and not exists(select 1 from public.blocks b where (b.blocker_id=cfg.caller_id and b.blocked_id=fp.user_id) or (b.blocker_id=fp.user_id and b.blocked_id=cfg.caller_id))
    and not exists(select 1 from public.muted_users mu where mu.user_id=cfg.caller_id and mu.muted_id=fp.user_id)
    and (cfg.term='' or lower(coalesce(fp.text,'')||' '||coalesce(fp.caption,'')||' '||coalesce(fp.category,'')||' '||coalesce(fp.faculty,'')||' '||array_to_string(coalesce(fp.tags,'{}'::text[])||coalesce(fp.hashtags,'{}'::text[]),' ')) like '%'||cfg.term||'%'
      or exists(select 1 from public.reel_transcript_segments rts where rts.post_id=fp.id and lower(rts.transcript) like '%'||cfg.term||'%'))
    and (not p_following_only or exists(select 1 from public.follows f where f.follower_id=cfg.caller_id and f.following_id=fp.user_id))
    and (not p_saved_only or exists(select 1 from public.post_bookmarks b where b.user_id=cfg.caller_id and b.post_id=fp.id))
),
content_results as (
  select case when cb.fp.is_reel then 'reel' else 'post' end result_type,cb.fp.id,
    to_jsonb(cb.fp)||jsonb_build_object('author_profile',jsonb_build_object('username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),'avatar_url',p.avatar_url,'is_verified',p.is_verified),
      'trend_percent',cb.trend,'distance_km',cb.dist,'matched_moment_ms',cb.matched_ms,'is_saved',cb.saved,'is_following',cb.following) payload,
    (case cfg.sort_mode
      when 'trending' then 1000+greatest(-500,least(cb.trend::double precision,500))
      when 'distance' then case when cb.dist is null then -100000 else 10000-cb.dist end
      when 'recent' then extract(epoch from cb.fp.created_at)/1000000
      else (case when cfg.term='' then 0 else 25*extensions.similarity(lower(coalesce(cb.fp.text,'')||' '||coalesce(cb.fp.caption,'')),cfg.term) end)
         +ln(1+greatest(cb.fp.view_count,0))+2*ln(1+greatest(cb.fp.like_count,0))+4*ln(1+greatest(cb.fp.comment_count,0))+6*ln(1+greatest(cb.fp.share_count,0))
         +least(20,greatest(-20,cb.trend::double precision/5))+case when cb.following then 6 else 0 end end)::numeric,
    case when cb.matched_ms is not null then 'Matched at '||(cb.matched_ms/60000)::text||':'||lpad(((cb.matched_ms/1000)%60)::text,2,'0')
         when cb.trend>0 then 'Up '||cb.trend||'% in 24h'
         when cb.saved then 'Saved by you'
         when cb.following then 'From an account you follow' else 'Relevant content' end,
    0,cb.dist,cb.trend::numeric,cb.matched_ms,cb.saved,cb.following,cb.fp.created_at
  from content_base cb join public.profiles p on p.id=cb.fp.user_id cross join cfg
),
community_results as (
  select 'community'::text,c.id,
    to_jsonb(c)||jsonb_build_object('joined',exists(select 1 from public.community_members cm where cm.community_id=c.id and cm.user_id=cfg.caller_id and cm.status='active')),
    ((case when cfg.term='' then 0 else 40*extensions.similarity(lower(c.name),cfg.term) end)+ln(1+greatest(c.member_count,0))*3+case when c.is_verified then 5 else 0 end)::numeric,
    case when exists(select 1 from public.community_members cm where cm.community_id=c.id and cm.user_id=cfg.caller_id and cm.status='active') then 'Your community' else 'Community on Blink' end,
    0,null::double precision,0::numeric,null::int,false,
    exists(select 1 from public.community_members cm where cm.community_id=c.id and cm.user_id=cfg.caller_id and cm.status='active'),c.created_at
  from public.communities c cross join cfg
  where cfg.caller_id is not null and 'community'=any(cfg.types)
    and (not c.is_private or c.owner_id=cfg.caller_id or exists(select 1 from public.community_members cm where cm.community_id=c.id and cm.user_id=cfg.caller_id and cm.status='active'))
    and (cfg.term='' or lower(c.name||' '||c.description||' '||c.category||' '||c.university||' '||c.faculty||' '||c.department) like '%'||cfg.term||'%'
      or extensions.similarity(lower(c.name),cfg.term)>=0.18)
    and (not p_following_only or exists(select 1 from public.community_members cm where cm.community_id=c.id and cm.user_id=cfg.caller_id and cm.status='active'))
    and not p_saved_only
),
event_results as (
  select 'event'::text,e.id,
    to_jsonb(e)||jsonb_build_object('attending',exists(select 1 from public.event_attendees ea where ea.event_id=e.id and ea.user_id=cfg.caller_id and ea.status='going'),
      'distance_km',public.blink_distance_km(p_lat,p_lng,e.latitude,e.longitude)),
    (case cfg.sort_mode when 'distance' then case when e.latitude is null or p_lat is null then -100000 else 10000-public.blink_distance_km(p_lat,p_lng,e.latitude,e.longitude) end
      when 'recent' then extract(epoch from e.created_at)/1000000
      else (case when cfg.term='' then 0 else 40*extensions.similarity(lower(e.title),cfg.term) end)+ln(1+greatest(e.attendee_count,0))*4+
        case when e.starts_at between cfg.rank_as_of and cfg.rank_as_of+interval '7 days' then 10 else 0 end end)::numeric,
    case when e.starts_at between cfg.rank_as_of and cfg.rank_as_of+interval '1 day' then 'Starting soon' else 'Upcoming event' end,
    0,public.blink_distance_km(p_lat,p_lng,e.latitude,e.longitude),0::numeric,null::int,false,
    exists(select 1 from public.event_attendees ea where ea.event_id=e.id and ea.user_id=cfg.caller_id and ea.status='going'),e.created_at
  from public.events e cross join cfg
  where cfg.caller_id is not null and 'event'=any(cfg.types) and e.status in ('scheduled','live') and coalesce(e.ends_at,e.starts_at)>=cfg.rank_as_of
    and (e.visibility in ('public','campus') or e.creator_id=cfg.caller_id or exists(select 1 from public.event_attendees ea where ea.event_id=e.id and ea.user_id=cfg.caller_id))
    and (cfg.term='' or lower(e.title||' '||e.description||' '||e.venue||' '||e.location_label||' '||e.university||' '||e.faculty) like '%'||cfg.term||'%'
      or extensions.similarity(lower(e.title),cfg.term)>=0.18)
    and (not p_following_only or exists(select 1 from public.follows f where f.follower_id=cfg.caller_id and f.following_id=e.creator_id)
      or exists(select 1 from public.community_members cm where cm.community_id=e.community_id and cm.user_id=cfg.caller_id and cm.status='active'))
    and not p_saved_only
),
page_results as (
  select 'page'::text,p.id,
    to_jsonb(p)||jsonb_build_object('following',exists(select 1 from public.page_followers pf where pf.page_id=p.id and pf.user_id=cfg.caller_id)),
    ((case when cfg.term='' then 0 else 45*greatest(extensions.similarity(lower(p.name),cfg.term),extensions.similarity(lower(p.handle),cfg.term)) end)+ln(1+greatest(p.follower_count,0))*3+case when p.is_verified then 5 else 0 end)::numeric,
    case when p.page_type='brand' then 'Brand on Blink' else 'Page on Blink' end,
    0,null::double precision,0::numeric,null::int,false,
    exists(select 1 from public.page_followers pf where pf.page_id=p.id and pf.user_id=cfg.caller_id),p.created_at
  from public.pages p cross join cfg
  where cfg.caller_id is not null and 'page'=any(cfg.types)
    and (cfg.term='' or lower(p.name||' '||p.handle||' '||p.description||' '||p.category||' '||p.university) like '%'||cfg.term||'%'
      or extensions.similarity(lower(p.name),cfg.term)>=0.18 or extensions.similarity(lower(p.handle),cfg.term)>=0.18)
    and (not p_following_only or exists(select 1 from public.page_followers pf where pf.page_id=p.id and pf.user_id=cfg.caller_id))
    and not p_saved_only
),
market_results as (
  select 'market_item'::text,m.id,
    to_jsonb(m)||jsonb_build_object('saved',exists(select 1 from public.marketplace_wishlist mw where mw.item_id=m.id and mw.user_id=cfg.caller_id),
      'distance_km',public.blink_distance_km(p_lat,p_lng,m.latitude,m.longitude)),
    (case cfg.sort_mode when 'distance' then case when m.latitude is null or p_lat is null then -100000 else 10000-public.blink_distance_km(p_lat,p_lng,m.latitude,m.longitude) end
      when 'recent' then extract(epoch from m.created_at)/1000000
      else (case when cfg.term='' then 0 else 45*extensions.similarity(lower(m.title),cfg.term) end)+case when m.is_featured then 8 else 0 end+case when m.seller_is_verified then 4 else 0 end end)::numeric,
    case when m.is_featured then 'Featured marketplace item' else 'Marketplace result' end,
    0,public.blink_distance_km(p_lat,p_lng,m.latitude,m.longitude),0::numeric,null::int,
    exists(select 1 from public.marketplace_wishlist mw where mw.item_id=m.id and mw.user_id=cfg.caller_id),
    exists(select 1 from public.follows f where f.follower_id=cfg.caller_id and f.following_id=m.seller_id),m.created_at
  from public.market_items m cross join cfg
  where cfg.caller_id is not null and 'market_item'=any(cfg.types) and m.status='active' and not m.is_sold
    and (cfg.term='' or lower(m.title||' '||m.description||' '||m.category||' '||m.location||' '||m.university) like '%'||cfg.term||'%'
      or extensions.similarity(lower(m.title),cfg.term)>=0.18)
    and (not p_following_only or exists(select 1 from public.follows f where f.follower_id=cfg.caller_id and f.following_id=m.seller_id))
    and (not p_saved_only or exists(select 1 from public.marketplace_wishlist mw where mw.item_id=m.id and mw.user_id=cfg.caller_id))
),
raw as (
  select * from profile_results union all select * from content_results union all select * from community_results
  union all select * from event_results union all select * from page_results union all select * from market_results
),
ranked as (
  select r.*, row_number() over(partition by r.result_type order by r.score desc,r.created_at desc,r.result_id) type_position
  from raw r
),
final as (
  select r.*, round((r.score-greatest(0,r.type_position-6)*1.25)::numeric,6) final_score from ranked r
)
select f.result_type,f.result_id,f.payload,f.final_score,f.reason,cfg.rank_as_of,f.mutuals,f.dist,f.trend,f.matched_ms,f.saved,f.following
from final f cross join cfg
where p_cursor_score is null or f.final_score<p_cursor_score
   or (f.final_score=p_cursor_score and (p_cursor_type is null or f.result_type>p_cursor_type or (f.result_type=p_cursor_type and p_cursor_id is not null and f.result_id>p_cursor_id)))
order by f.final_score desc,f.result_type,f.result_id
limit cfg.page_size;
$$;

grant execute on function public.search_discovery_v2(text,text[],integer,numeric,text,uuid,timestamptz,boolean,boolean,text,double precision,double precision) to authenticated;

-- Capability contract keeps advanced UI honest. Features requiring external producers
-- are enabled only when real backend data exists.
create or replace function public.search_capabilities_v2()
returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'communities',true,
    'events',true,
    'pages_brands',true,
    'marketplace',true,
    'saved',true,
    'following',true,
    'synced_history',true,
    'mutual_ranking',true,
    'distance_sort',true,
    'growth_metrics',true,
    'trend_metrics',true,
    'cursor_pagination',true,
    'reel_matched_moments',exists(select 1 from public.reel_transcript_segments limit 1),
    'image_similarity',to_regclass('public.search_image_embeddings') is not null and exists(select 1 from pg_catalog.pg_class where oid=to_regclass('public.search_image_embeddings')),
    'autoplay_previews',true,
    'hero_transitions',true
  );
$$;
grant execute on function public.search_capabilities_v2() to authenticated;

-- Optional image similarity RPC. It accepts a real[] so the function can exist even
-- when pgvector is unavailable; the vector cast lives inside dynamic SQL.
create or replace function public.search_image_similarity_v2(p_embedding real[], p_limit integer default 24)
returns table(result_type text,result_id uuid,image_url text,similarity double precision)
language plpgsql stable security definer set search_path = '' as $$
begin
  if auth.uid() is null or to_regclass('public.search_image_embeddings') is null or p_embedding is null then return; end if;
  return query execute
    'select entity_type,entity_id,image_url,(1-(embedding <=> $1::extensions.vector))::double precision from public.search_image_embeddings order by embedding <=> $1::extensions.vector limit $2'
    using p_embedding, greatest(1,least(coalesce(p_limit,24),60));
end;
$$;
grant execute on function public.search_image_similarity_v2(real[],integer) to authenticated;

-- Seed the first real snapshot immediately so the 24-hour comparison becomes useful
-- automatically without inventing values.
select public.capture_discovery_snapshots();
