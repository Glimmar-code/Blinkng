-- Blink Search Phase 3 completion
-- Adds automatic media/reel indexing queues, real visual similarity, safer community
-- membership policies and capability reporting for the finished Android/Windows UI.

-- ------------------------------------------------------------
-- Community ownership and RLS recursion fix
-- ------------------------------------------------------------
create or replace function public.ensure_community_owner_membership()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.community_members(community_id, user_id, role, status, joined_at)
  values(new.id, new.owner_id, 'owner', 'active', now())
  on conflict(community_id, user_id) do update
    set role='owner', status='active';
  return new;
end;
$$;

drop trigger if exists community_owner_membership_trigger on public.communities;
create trigger community_owner_membership_trigger
after insert on public.communities
for each row execute function public.ensure_community_owner_membership();

insert into public.community_members(community_id,user_id,role,status,joined_at)
select c.id,c.owner_id,'owner','active',c.created_at
from public.communities c
on conflict(community_id,user_id) do update set role='owner',status='active';

-- Avoid policy recursion (communities -> community_members -> communities).
drop policy if exists community_members_authenticated_read on public.community_members;
create policy community_members_authenticated_read on public.community_members
for select to authenticated
using (
  user_id=auth.uid()
  or exists(
    select 1 from public.communities c
    where c.id=community_id and c.owner_id=auth.uid()
  )
);

-- ------------------------------------------------------------
-- Durable indexing queues
-- ------------------------------------------------------------
create table if not exists public.search_media_index_jobs (
  id uuid primary key default gen_random_uuid(),
  entity_type text not null check (entity_type in ('post','reel','market_item','profile','community','page','event')),
  entity_id uuid not null,
  image_url text not null check (char_length(btrim(image_url)) > 0),
  status text not null default 'pending' check (status in ('pending','running','done','failed')),
  attempts integer not null default 0 check (attempts >= 0),
  next_attempt_at timestamptz not null default now(),
  locked_at timestamptz,
  completed_at timestamptz,
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(entity_type,entity_id,image_url)
);
create index if not exists search_media_index_jobs_ready_idx
  on public.search_media_index_jobs(status,next_attempt_at,created_at);
alter table public.search_media_index_jobs enable row level security;
revoke all on public.search_media_index_jobs from anon, authenticated;

create table if not exists public.reel_search_index_jobs (
  post_id uuid primary key references public.feed_posts(id) on delete cascade,
  video_url text not null check (char_length(btrim(video_url)) > 0),
  status text not null default 'pending' check (status in ('pending','running','done','failed')),
  attempts integer not null default 0 check (attempts >= 0),
  next_attempt_at timestamptz not null default now(),
  locked_at timestamptz,
  completed_at timestamptz,
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists reel_search_index_jobs_ready_idx
  on public.reel_search_index_jobs(status,next_attempt_at,created_at);
alter table public.reel_search_index_jobs enable row level security;
revoke all on public.reel_search_index_jobs from anon, authenticated;

create or replace function public.enqueue_search_media_job(
  p_type text,
  p_id uuid,
  p_url text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if p_id is null or nullif(btrim(coalesce(p_url,'')),'') is null then return; end if;
  insert into public.search_media_index_jobs(entity_type,entity_id,image_url,status,next_attempt_at,updated_at)
  values(lower(p_type),p_id,btrim(p_url),'pending',now(),now())
  on conflict(entity_type,entity_id,image_url) do update
    set status = case when public.search_media_index_jobs.status='done' then 'done' else 'pending' end,
        next_attempt_at = case when public.search_media_index_jobs.status='done' then public.search_media_index_jobs.next_attempt_at else now() end,
        updated_at = now();
end;
$$;
revoke all on function public.enqueue_search_media_job(text,uuid,text) from public,anon,authenticated;

create or replace function public.enqueue_feed_search_index()
returns trigger language plpgsql security definer set search_path = '' as $$
declare v_url text;
begin
  if not new.is_active or new.is_flagged then return new; end if;
  v_url := coalesce(nullif(new.image_url,''), (coalesce(new.images,'{}'::text[]))[1]);
  if v_url is not null then
    perform public.enqueue_search_media_job(case when new.is_reel then 'reel' else 'post' end,new.id,v_url);
  end if;
  if new.is_reel and nullif(btrim(coalesce(new.video_url,'')),'') is not null then
    insert into public.reel_search_index_jobs(post_id,video_url,status,next_attempt_at,updated_at)
    values(new.id,btrim(new.video_url),'pending',now(),now())
    on conflict(post_id) do update
      set video_url=excluded.video_url,
          status=case when public.reel_search_index_jobs.video_url is distinct from excluded.video_url then 'pending' else public.reel_search_index_jobs.status end,
          next_attempt_at=case when public.reel_search_index_jobs.video_url is distinct from excluded.video_url then now() else public.reel_search_index_jobs.next_attempt_at end,
          updated_at=now();
  end if;
  return new;
end;
$$;
drop trigger if exists feed_search_index_enqueue_trigger on public.feed_posts;
create trigger feed_search_index_enqueue_trigger
after insert or update of image_url,images,video_url,is_reel,is_active,is_flagged on public.feed_posts
for each row execute function public.enqueue_feed_search_index();

create or replace function public.enqueue_market_search_index()
returns trigger language plpgsql security definer set search_path = '' as $$
declare v_url text;
begin
  if new.is_sold or new.status <> 'active' then return new; end if;
  v_url := coalesce(nullif(new.image_url,''), (coalesce(new.image_urls,'{}'::text[]))[1]);
  perform public.enqueue_search_media_job('market_item',new.id,v_url);
  return new;
end;
$$;
drop trigger if exists market_search_index_enqueue_trigger on public.market_items;
create trigger market_search_index_enqueue_trigger
after insert or update of image_url,image_urls,status,is_sold on public.market_items
for each row execute function public.enqueue_market_search_index();

create or replace function public.enqueue_profile_search_index()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform public.enqueue_search_media_job('profile',new.id,new.avatar_url);
  return new;
end;
$$;
drop trigger if exists profile_search_index_enqueue_trigger on public.profiles;
create trigger profile_search_index_enqueue_trigger
after insert or update of avatar_url on public.profiles
for each row execute function public.enqueue_profile_search_index();

create or replace function public.enqueue_community_search_index()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform public.enqueue_search_media_job('community',new.id,coalesce(nullif(new.avatar_url,''),new.cover_url));
  return new;
end;
$$;
drop trigger if exists community_search_index_enqueue_trigger on public.communities;
create trigger community_search_index_enqueue_trigger
after insert or update of avatar_url,cover_url on public.communities
for each row execute function public.enqueue_community_search_index();

create or replace function public.enqueue_page_search_index()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform public.enqueue_search_media_job('page',new.id,coalesce(nullif(new.avatar_url,''),new.cover_url));
  return new;
end;
$$;
drop trigger if exists page_search_index_enqueue_trigger on public.pages;
create trigger page_search_index_enqueue_trigger
after insert or update of avatar_url,cover_url on public.pages
for each row execute function public.enqueue_page_search_index();

create or replace function public.enqueue_event_search_index()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform public.enqueue_search_media_job('event',new.id,new.banner_url);
  return new;
end;
$$;
drop trigger if exists event_search_index_enqueue_trigger on public.events;
create trigger event_search_index_enqueue_trigger
after insert or update of banner_url on public.events
for each row execute function public.enqueue_event_search_index();

-- Backfill queues from existing visible content. These rows contain URLs/IDs only;
-- the Edge Function performs the actual image/video processing later.
insert into public.search_media_index_jobs(entity_type,entity_id,image_url)
select case when fp.is_reel then 'reel' else 'post' end, fp.id,
       coalesce(nullif(fp.image_url,''),(coalesce(fp.images,'{}'::text[]))[1])
from public.feed_posts fp
where fp.is_active and not fp.is_flagged
  and coalesce(nullif(fp.image_url,''),(coalesce(fp.images,'{}'::text[]))[1]) is not null
on conflict do nothing;

insert into public.search_media_index_jobs(entity_type,entity_id,image_url)
select 'market_item',m.id,coalesce(nullif(m.image_url,''),(coalesce(m.image_urls,'{}'::text[]))[1])
from public.market_items m
where m.status='active' and not m.is_sold
  and coalesce(nullif(m.image_url,''),(coalesce(m.image_urls,'{}'::text[]))[1]) is not null
on conflict do nothing;

insert into public.search_media_index_jobs(entity_type,entity_id,image_url)
select 'profile',p.id,p.avatar_url from public.profiles p where nullif(btrim(coalesce(p.avatar_url,'')),'') is not null
on conflict do nothing;
insert into public.search_media_index_jobs(entity_type,entity_id,image_url)
select 'community',c.id,coalesce(nullif(c.avatar_url,''),c.cover_url) from public.communities c
where coalesce(nullif(c.avatar_url,''),c.cover_url) is not null on conflict do nothing;
insert into public.search_media_index_jobs(entity_type,entity_id,image_url)
select 'page',p.id,coalesce(nullif(p.avatar_url,''),p.cover_url) from public.pages p
where coalesce(nullif(p.avatar_url,''),p.cover_url) is not null on conflict do nothing;
insert into public.search_media_index_jobs(entity_type,entity_id,image_url)
select 'event',e.id,e.banner_url from public.events e where nullif(btrim(coalesce(e.banner_url,'')),'') is not null
on conflict do nothing;

insert into public.reel_search_index_jobs(post_id,video_url)
select fp.id,fp.video_url from public.feed_posts fp
where fp.is_reel and fp.is_active and not fp.is_flagged and nullif(btrim(coalesce(fp.video_url,'')),'') is not null
on conflict(post_id) do nothing;

-- Reset abandoned claims so a crashed Edge Function does not strand work forever.
create or replace function public.reset_stale_search_index_jobs_v2()
returns void language plpgsql security definer set search_path = '' as $$
begin
  update public.search_media_index_jobs
  set status='pending',locked_at=null,next_attempt_at=now(),updated_at=now(),last_error='Recovered stale media index claim'
  where status='running' and locked_at < now()-interval '20 minutes';
  update public.reel_search_index_jobs
  set status='pending',locked_at=null,next_attempt_at=now(),updated_at=now(),last_error='Recovered stale reel index claim'
  where status='running' and locked_at < now()-interval '20 minutes';
end;
$$;
revoke all on function public.reset_stale_search_index_jobs_v2() from public,anon,authenticated;

do $$
declare v_jobid bigint;
begin
  if exists(select 1 from pg_extension where extname='pg_cron') then
    select jobid into v_jobid from cron.job where jobname='blink-search-index-recovery-v1' limit 1;
    if v_jobid is not null then perform cron.unschedule(v_jobid); end if;
    perform cron.schedule('blink-search-index-recovery-v1','*/10 * * * *','select public.reset_stale_search_index_jobs_v2();');
  end if;
end;
$$;

-- ------------------------------------------------------------
-- Visual descriptor persistence and search
-- ------------------------------------------------------------
-- The first Phase 3 migration creates this vector table whenever pgvector is available.
-- Supabase exposes pgvector on Blink, so ensure it exists deterministically here too.
create extension if not exists vector with schema extensions;
create table if not exists public.search_image_embeddings (
  entity_type text not null check (entity_type in ('post','reel','market_item','profile','community','page','event')),
  entity_id uuid not null,
  image_url text not null,
  model text not null default 'blink-perceptual-rgb-luma-v1',
  embedding extensions.vector(512) not null,
  updated_at timestamptz not null default now(),
  primary key(entity_type,entity_id,image_url)
);
alter table public.search_image_embeddings enable row level security;
revoke all on public.search_image_embeddings from anon,authenticated;
create index if not exists search_image_embeddings_model_idx on public.search_image_embeddings(model,updated_at desc);

create or replace function public.search_image_similarity_v3(
  p_embedding real[],
  p_limit integer default 24
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
language plpgsql
stable
security definer
set search_path=''
as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  if p_embedding is null or array_length(p_embedding,1) <> 512 then return; end if;
  return query
  with nearest as (
    select e.entity_type,e.entity_id,e.image_url,
           greatest(0.0,least(1.0,1.0-(e.embedding <=> p_embedding::extensions.vector))) similarity
    from public.search_image_embeddings e
    where e.model='blink-perceptual-rgb-luma-v1'
    order by e.embedding <=> p_embedding::extensions.vector
    limit greatest(20,least(coalesce(p_limit,24)*5,300))
  ), visible as (
    select n.entity_type result_type,n.entity_id result_id,
      case n.entity_type
        when 'profile' then (
          select jsonb_build_object('id',p.id,'username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),'avatar_url',p.avatar_url,
            'bio',p.bio,'university',p.university,'faculty',p.faculty,'department',p.department,'is_verified',p.is_verified,'follower_count',p.follower_count)
          from public.profiles p where p.id=n.entity_id
            and not exists(select 1 from public.blocks b where (b.blocker_id=v_uid and b.blocked_id=p.id) or (b.blocker_id=p.id and b.blocked_id=v_uid))
        )
        when 'post' then (
          select to_jsonb(fp)||jsonb_build_object('author_profile',jsonb_build_object('username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),'avatar_url',p.avatar_url,'is_verified',p.is_verified))
          from public.feed_posts fp join public.profiles p on p.id=fp.user_id
          where fp.id=n.entity_id and fp.is_active and not fp.is_flagged and not fp.is_reel
            and (fp.expires_at is null or fp.expires_at>now())
            and not exists(select 1 from public.blocks b where (b.blocker_id=v_uid and b.blocked_id=fp.user_id) or (b.blocker_id=fp.user_id and b.blocked_id=v_uid))
            and not exists(select 1 from public.muted_users mu where mu.user_id=v_uid and mu.muted_id=fp.user_id)
        )
        when 'reel' then (
          select to_jsonb(fp)||jsonb_build_object('author_profile',jsonb_build_object('username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),'avatar_url',p.avatar_url,'is_verified',p.is_verified))
          from public.feed_posts fp join public.profiles p on p.id=fp.user_id
          where fp.id=n.entity_id and fp.is_active and not fp.is_flagged and fp.is_reel
            and (fp.expires_at is null or fp.expires_at>now())
            and not exists(select 1 from public.blocks b where (b.blocker_id=v_uid and b.blocked_id=fp.user_id) or (b.blocker_id=fp.user_id and b.blocked_id=v_uid))
            and not exists(select 1 from public.muted_users mu where mu.user_id=v_uid and mu.muted_id=fp.user_id)
        )
        when 'market_item' then (
          select to_jsonb(m) from public.market_items m where m.id=n.entity_id and m.status='active' and not m.is_sold
        )
        when 'community' then (
          select to_jsonb(c)||jsonb_build_object('joined',exists(select 1 from public.community_members cm where cm.community_id=c.id and cm.user_id=v_uid and cm.status='active'))
          from public.communities c where c.id=n.entity_id and (not c.is_private or c.owner_id=v_uid or exists(select 1 from public.community_members cm where cm.community_id=c.id and cm.user_id=v_uid and cm.status='active'))
        )
        when 'page' then (select to_jsonb(p) from public.pages p where p.id=n.entity_id)
        when 'event' then (
          select to_jsonb(e)||jsonb_build_object('attending',exists(select 1 from public.event_attendees ea where ea.event_id=e.id and ea.user_id=v_uid and ea.status='going'))
          from public.events e where e.id=n.entity_id and e.status in ('scheduled','live')
            and coalesce(e.ends_at,e.starts_at)>=now()
            and (e.visibility in ('public','campus') or e.creator_id=v_uid or exists(select 1 from public.event_attendees ea where ea.event_id=e.id and ea.user_id=v_uid))
        )
      end payload,
      n.similarity
    from nearest n
  )
  select v.result_type,v.result_id,v.payload,
         round((v.similarity*100)::numeric,6),
         'Visual similarity'::text,now(),0,null::double precision,0::numeric,null::integer,
         case when v.result_type in ('post','reel') then exists(select 1 from public.post_bookmarks pb where pb.user_id=v_uid and pb.post_id=v.result_id)
              when v.result_type='market_item' then exists(select 1 from public.marketplace_wishlist mw where mw.user_id=v_uid and mw.item_id=v.result_id)
              else false end,
         case when v.result_type='profile' then exists(select 1 from public.follows f where f.follower_id=v_uid and f.following_id=v.result_id)
              when v.result_type='page' then exists(select 1 from public.page_followers pf where pf.user_id=v_uid and pf.page_id=v.result_id)
              when v.result_type='community' then exists(select 1 from public.community_members cm where cm.user_id=v_uid and cm.community_id=v.result_id and cm.status='active')
              when v.result_type='event' then exists(select 1 from public.event_attendees ea where ea.user_id=v_uid and ea.event_id=v.result_id and ea.status='going')
              else false end
  from visible v
  where v.payload is not null and v.similarity >= 0.42
  order by v.similarity desc,v.result_type,v.result_id
  limit greatest(1,least(coalesce(p_limit,24),60));
end;
$$;
grant execute on function public.search_image_similarity_v3(real[],integer) to authenticated;

-- ------------------------------------------------------------
-- Capability contract now represents the complete pipeline.
-- ------------------------------------------------------------
create or replace function public.search_capabilities_v2()
returns jsonb
language sql stable security definer set search_path=''
as $$
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
    'reel_matched_moments',true,
    'image_similarity',true,
    'autoplay_previews',true,
    'hero_transitions',true
  );
$$;
grant execute on function public.search_capabilities_v2() to authenticated;

-- Warm the snapshot series immediately. Future values are cron-maintained.
select public.capture_discovery_snapshots();
