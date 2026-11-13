-- Additive repair for the client-facing Advanced Search Phase 3 RPC contract.
-- Reuses the established search/history schema, result projection and keyset cursor logic.
-- Image indexing remains private; callers may only submit normalized descriptors for their own current media.

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
    jsonb_build_object('id',cb.fp.id,'text',cb.fp.text,'caption',cb.fp.caption,'image_url',cb.fp.image_url,'images',cb.fp.images,
      'video_url',cb.fp.video_url,'created_at',cb.fp.created_at,'category',cb.fp.category,'is_reel',cb.fp.is_reel,
      'like_count',cb.fp.like_count,'comment_count',cb.fp.comment_count,'share_count',cb.fp.share_count,'view_count',cb.fp.view_count,
      'author_profile',jsonb_build_object('username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),'avatar_url',p.avatar_url,'is_verified',p.is_verified),
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
    jsonb_build_object('id',c.id,'name',c.name,'slug',c.slug,'description',c.description,'avatar_url',c.avatar_url,'cover_url',c.cover_url,'category',c.category,'university',c.university,'faculty',c.faculty,'department',c.department,'is_private',c.is_private,'is_verified',c.is_verified,'member_count',c.member_count,'post_count',c.post_count,'created_at',c.created_at,'joined',exists(select 1 from public.community_members cm where cm.community_id=c.id and cm.user_id=cfg.caller_id and cm.status='active')),
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
    jsonb_build_object('id',e.id,'title',e.title,'description',e.description,'banner_url',e.banner_url,'venue',e.venue,'location_label',e.location_label,'starts_at',e.starts_at,'ends_at',e.ends_at,'university',e.university,'faculty',e.faculty,'is_online',e.is_online,'external_url',e.external_url,'visibility',e.visibility,'status',e.status,'attendee_count',e.attendee_count,'created_at',e.created_at,'attending',exists(select 1 from public.event_attendees ea where ea.event_id=e.id and ea.user_id=cfg.caller_id and ea.status='going'),'distance_km',public.blink_distance_km(p_lat,p_lng,e.latitude,e.longitude)),
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
    jsonb_build_object('id',p.id,'name',p.name,'handle',p.handle,'page_type',p.page_type,'description',p.description,'avatar_url',p.avatar_url,'cover_url',p.cover_url,'category',p.category,'university',p.university,'website',p.website,'is_verified',p.is_verified,'follower_count',p.follower_count,'created_at',p.created_at,'following',exists(select 1 from public.page_followers pf where pf.page_id=p.id and pf.user_id=cfg.caller_id)),
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
    jsonb_build_object('id',m.id,'title',m.title,'description',m.description,'price',m.price,'currency',m.currency,'category',m.category,'condition',m.condition,
      'image_url',m.image_url,'image_urls',m.image_urls,'location',m.location,'university',m.university,'seller_id',m.seller_id,'seller_name',m.seller_name,
      'seller_username',m.seller_username,'seller_avatar',m.seller_avatar,'seller_is_verified',m.seller_is_verified,'seller_rating',m.seller_rating,'created_at',m.created_at,
      'saved',exists(select 1 from public.marketplace_wishlist mw where mw.item_id=m.id and mw.user_id=cfg.caller_id),'distance_km',public.blink_distance_km(p_lat,p_lng,m.latitude,m.longitude)),
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

-- These SECURITY DEFINER entry points are authenticated-only, including on projects
-- where older versions inherited PostgreSQL's default PUBLIC EXECUTE privilege.
revoke all on function public.upsert_search_history_v2(text,text,jsonb,boolean) from public, anon;
grant execute on function public.upsert_search_history_v2(text,text,jsonb,boolean) to authenticated;
revoke all on function public.get_search_history_v2(integer) from public, anon;
grant execute on function public.get_search_history_v2(integer) to authenticated;
revoke all on function public.search_discovery_v2(text,text[],integer,numeric,text,uuid,timestamptz,boolean,boolean,text,double precision,double precision) from public, anon;
grant execute on function public.search_discovery_v2(text,text[],integer,numeric,text,uuid,timestamptz,boolean,boolean,text,double precision,double precision) to authenticated;


-- The existing Phase 3 schema treats embeddings as private index data. Keep the
-- table inaccessible to client roles; only the RPCs below may use it.
do $migration$
begin
  if exists (select 1 from pg_catalog.pg_available_extensions where name = 'vector') then
    execute 'create extension if not exists vector with schema extensions';
    execute $ddl$
      create table if not exists public.search_image_embeddings (
        entity_type text not null check (entity_type in ('post','reel','market_item','profile','community','page','event')),
        entity_id uuid not null,
        image_url text not null,
        model text not null default 'blink-perceptual-rgb-luma-v1',
        embedding extensions.vector(512) not null,
        updated_at timestamptz not null default now(),
        primary key (entity_type, entity_id, image_url)
      )
    $ddl$;
    execute 'alter table public.search_image_embeddings enable row level security';
    execute 'revoke all on public.search_image_embeddings from public, anon, authenticated';
    execute 'create index if not exists search_image_embeddings_model_idx on public.search_image_embeddings(model, updated_at desc)';
  end if;
end;
$migration$;

-- Return only capabilities backed by installed database contracts/producers.
-- Image search is advertised only after at least one canonical real descriptor exists.
create or replace function public.search_capabilities_v2()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_image_similarity boolean := false;
  v_reel_moments boolean := false;
begin
  if to_regclass('public.search_image_embeddings') is not null
     and exists (select 1 from pg_catalog.pg_extension where extname = 'vector') then
    execute 'select exists (select 1 from public.search_image_embeddings where model = $1 limit 1)'
      into v_image_similarity
      using 'blink-perceptual-rgb-luma-v1';
  end if;
  if to_regclass('public.reel_transcript_segments') is not null then
    execute 'select exists (select 1 from public.reel_transcript_segments limit 1)'
      into v_reel_moments;
  end if;
  return jsonb_build_object(
    'communities', to_regclass('public.communities') is not null,
    'events', to_regclass('public.events') is not null,
    'pages_brands', to_regclass('public.pages') is not null,
    'marketplace', to_regclass('public.market_items') is not null,
    'saved', to_regclass('public.post_bookmarks') is not null,
    'following', to_regclass('public.follows') is not null,
    'synced_history', to_regclass('public.search_history') is not null,
    'mutual_ranking', to_regclass('public.follows') is not null,
    'distance_sort', to_regclass('public.profiles') is not null,
    'growth_metrics', to_regclass('public.profile_growth_snapshots') is not null,
    'trend_metrics', to_regclass('public.content_trend_snapshots') is not null,
    'cursor_pagination', true,
    'reel_matched_moments', v_reel_moments,
    'image_similarity', v_image_similarity,
    'autoplay_previews', true,
    'hero_transitions', true
  );
end;
$$;
revoke all on function public.search_capabilities_v2() from public, anon;
grant execute on function public.search_capabilities_v2() to authenticated;

-- A real pgvector cosine search over the 512-dimensional normalized image descriptor.
-- No lexical fallback or fabricated similarity score is returned if vector support is absent.
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
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_norm_sq double precision := 0;
  v_component real;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  if p_embedding is null or array_ndims(p_embedding) <> 1 or array_length(p_embedding, 1) <> 512 then
    return;
  end if;
  if to_regclass('public.search_image_embeddings') is null
     or not exists (select 1 from pg_catalog.pg_extension where extname = 'vector') then
    return;
  end if;
  foreach v_component in array p_embedding loop
    if v_component is null or v_component::text in ('NaN','Infinity','-Infinity') then return; end if;
    v_norm_sq := v_norm_sq + v_component::double precision * v_component::double precision;
  end loop;
  if v_norm_sq <= 1e-12 then return; end if;

  return query execute $query$
    with nearest as (
      select e.entity_type, e.entity_id, e.image_url,
             greatest(0.0, least(1.0, 1.0 - (e.embedding OPERATOR(extensions.<=>) ($1::real[]::extensions.vector)))) as similarity
      from public.search_image_embeddings e
      where e.model = 'blink-perceptual-rgb-luma-v1'
      order by e.embedding OPERATOR(extensions.<=>) ($1::real[]::extensions.vector)
      limit greatest(20, least(coalesce($3,24) * 5, 300))
    ), visible as (
      select n.entity_type as result_type, n.entity_id as result_id,
        case n.entity_type
          when 'profile' then (
            select jsonb_build_object('id',p.id,'username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),'avatar_url',p.avatar_url,
              'bio',p.bio,'university',p.university,'faculty',p.faculty,'department',p.department,'is_verified',p.is_verified,'follower_count',p.follower_count)
            from public.profiles p where p.id=n.entity_id
              and not exists(select 1 from public.blocks b where (b.blocker_id=$2 and b.blocked_id=p.id) or (b.blocker_id=p.id and b.blocked_id=$2))
          )
          when 'post' then (
            select jsonb_build_object('id',fp.id,'text',fp.text,'caption',fp.caption,'image_url',fp.image_url,'images',fp.images,'video_url',fp.video_url,'created_at',fp.created_at,
              'category',fp.category,'is_reel',fp.is_reel,'like_count',fp.like_count,'comment_count',fp.comment_count,'share_count',fp.share_count,'view_count',fp.view_count,
              'author_profile',jsonb_build_object('username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),'avatar_url',p.avatar_url,'is_verified',p.is_verified))
            from public.feed_posts fp join public.profiles p on p.id=fp.user_id
            where fp.id=n.entity_id and not fp.is_reel and fp.is_active and not fp.is_flagged
              and (fp.expires_at is null or fp.expires_at>now())
              and not exists(select 1 from public.blocks b where (b.blocker_id=$2 and b.blocked_id=fp.user_id) or (b.blocker_id=fp.user_id and b.blocked_id=$2))
              and not exists(select 1 from public.muted_users mu where mu.user_id=$2 and mu.muted_id=fp.user_id)
          )
          when 'reel' then (
            select jsonb_build_object('id',fp.id,'text',fp.text,'caption',fp.caption,'image_url',fp.image_url,'images',fp.images,'video_url',fp.video_url,'created_at',fp.created_at,
              'category',fp.category,'is_reel',fp.is_reel,'like_count',fp.like_count,'comment_count',fp.comment_count,'share_count',fp.share_count,'view_count',fp.view_count,
              'author_profile',jsonb_build_object('username',p.username,'full_name',coalesce(nullif(p.full_name,''),p.name),'avatar_url',p.avatar_url,'is_verified',p.is_verified))
            from public.feed_posts fp join public.profiles p on p.id=fp.user_id
            where fp.id=n.entity_id and fp.is_reel and fp.is_active and not fp.is_flagged
              and (fp.expires_at is null or fp.expires_at>now())
              and not exists(select 1 from public.blocks b where (b.blocker_id=$2 and b.blocked_id=fp.user_id) or (b.blocker_id=fp.user_id and b.blocked_id=$2))
              and not exists(select 1 from public.muted_users mu where mu.user_id=$2 and mu.muted_id=fp.user_id)
          )
          when 'market_item' then (
            select jsonb_build_object('id',m.id,'title',m.title,'description',m.description,'price',m.price,'currency',m.currency,'category',m.category,'condition',m.condition,'image_url',m.image_url,'image_urls',m.image_urls,'location',m.location,'university',m.university,'seller_id',m.seller_id,'seller_name',m.seller_name,'seller_username',m.seller_username,'seller_avatar',m.seller_avatar,'seller_is_verified',m.seller_is_verified,'seller_rating',m.seller_rating,'created_at',m.created_at) from public.market_items m where m.id=n.entity_id and m.status='active' and not m.is_sold
              and not exists(select 1 from public.blocks b where (b.blocker_id=$2 and b.blocked_id=m.seller_id) or (b.blocker_id=m.seller_id and b.blocked_id=$2))
          )
          when 'community' then (
            select jsonb_build_object('id',c.id,'name',c.name,'slug',c.slug,'description',c.description,'avatar_url',c.avatar_url,'cover_url',c.cover_url,'category',c.category,'university',c.university,'faculty',c.faculty,'department',c.department,'is_verified',c.is_verified,'member_count',c.member_count,'post_count',c.post_count,'joined',exists(select 1 from public.community_members cm where cm.community_id=c.id and cm.user_id=$2 and cm.status='active'))
            from public.communities c where c.id=n.entity_id and (not c.is_private or c.owner_id=$2 or exists(select 1 from public.community_members cm where cm.community_id=c.id and cm.user_id=$2 and cm.status='active'))
          )
          when 'page' then (select jsonb_build_object('id',p.id,'name',p.name,'handle',p.handle,'page_type',p.page_type,'description',p.description,'avatar_url',p.avatar_url,'cover_url',p.cover_url,'category',p.category,'university',p.university,'website',p.website,'is_verified',p.is_verified,'follower_count',p.follower_count) from public.pages p where p.id=n.entity_id)
          when 'event' then (
            select jsonb_build_object('id',e.id,'title',e.title,'description',e.description,'banner_url',e.banner_url,'venue',e.venue,'location_label',e.location_label,'starts_at',e.starts_at,'ends_at',e.ends_at,'university',e.university,'faculty',e.faculty,'is_online',e.is_online,'external_url',e.external_url,'visibility',e.visibility,'status',e.status,'attendee_count',e.attendee_count,'created_at',e.created_at,'attending',exists(select 1 from public.event_attendees ea where ea.event_id=e.id and ea.user_id=$2 and ea.status='going'))
            from public.events e where e.id=n.entity_id and e.status in ('scheduled','live')
              and coalesce(e.ends_at,e.starts_at)>=now()
              and (e.visibility in ('public','campus') or e.creator_id=$2 or exists(select 1 from public.event_attendees ea where ea.event_id=e.id and ea.user_id=$2))
          )
        end as payload,
        n.similarity
      from nearest n
    )
    select v.result_type,v.result_id,v.payload,
      round((v.similarity*100)::numeric,6),'Visual similarity'::text,now(),0,null::double precision,0::numeric,null::integer,
      case when v.result_type in ('post','reel') then exists(select 1 from public.post_bookmarks pb where pb.user_id=$2 and pb.post_id=v.result_id)
           when v.result_type='market_item' then exists(select 1 from public.marketplace_wishlist mw where mw.user_id=$2 and mw.item_id=v.result_id)
           else false end,
      case when v.result_type='profile' then exists(select 1 from public.follows f where f.follower_id=$2 and f.following_id=v.result_id)
           when v.result_type='page' then exists(select 1 from public.page_followers pf where pf.user_id=$2 and pf.page_id=v.result_id)
           when v.result_type='community' then exists(select 1 from public.community_members cm where cm.user_id=$2 and cm.community_id=v.result_id and cm.status='active')
           when v.result_type='event' then exists(select 1 from public.event_attendees ea where ea.user_id=$2 and ea.event_id=v.result_id and ea.status='going')
           else false end
    from visible v
    where v.payload is not null and v.similarity >= 0.42
    order by v.similarity desc,v.result_type,v.result_id
    limit greatest(1,least(coalesce($3,24),60))
  $query$ using p_embedding, v_uid, p_limit;
end;
$$;
revoke all on function public.search_image_similarity_v3(real[],integer) from public, anon;
grant execute on function public.search_image_similarity_v3(real[],integer) to authenticated;

-- Controlled client upsert: only the caller's own currently-published media URL can
-- be indexed. The worker continues to use its service-role write path for shared media.
create or replace function public.upsert_search_image_embedding_v2(
  p_entity_type text,
  p_entity_id uuid,
  p_image_url text,
  p_embedding real[]
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_type text := lower(btrim(coalesce(p_entity_type,'')));
  v_url text := btrim(coalesce(p_image_url,''));
  v_norm_sq double precision := 0;
  v_component real;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  if p_entity_id is null or v_url = '' or char_length(v_url) > 2048 then return false; end if;
  if p_embedding is null or array_ndims(p_embedding) <> 1 or array_length(p_embedding,1) <> 512 then return false; end if;
  foreach v_component in array p_embedding loop
    if v_component is null or v_component::text in ('NaN','Infinity','-Infinity') then return false; end if;
    v_norm_sq := v_norm_sq + v_component::double precision * v_component::double precision;
  end loop;
  if v_norm_sq < 0.98 or v_norm_sq > 1.02 then return false; end if;
  if to_regclass('public.search_image_embeddings') is null
     or not exists (select 1 from pg_catalog.pg_extension where extname = 'vector') then
    raise exception 'Visual search vector storage is unavailable';
  end if;

  if not (
    (v_type='profile' and exists(select 1 from public.profiles p where p.id=v_uid and p.avatar_url=v_url))
    or (v_type='post' and exists(select 1 from public.feed_posts f where f.id=p_entity_id and f.user_id=v_uid and not f.is_reel and f.is_active and not f.is_flagged and coalesce(nullif(f.image_url,''),(coalesce(f.images,'{}'::text[]))[1])=v_url))
    or (v_type='reel' and exists(select 1 from public.feed_posts f where f.id=p_entity_id and f.user_id=v_uid and f.is_reel and f.is_active and not f.is_flagged and coalesce(nullif(f.image_url,''),(coalesce(f.images,'{}'::text[]))[1])=v_url))
    or (v_type='market_item' and exists(select 1 from public.market_items m where m.id=p_entity_id and m.seller_id=v_uid and m.status='active' and not m.is_sold and coalesce(nullif(m.image_url,''),(coalesce(m.image_urls,'{}'::text[]))[1])=v_url))
    or (v_type='community' and exists(select 1 from public.communities c where c.id=p_entity_id and c.owner_id=v_uid and coalesce(nullif(c.avatar_url,''),c.cover_url)=v_url))
    or (v_type='page' and exists(select 1 from public.pages p where p.id=p_entity_id and p.owner_id=v_uid and coalesce(nullif(p.avatar_url,''),p.cover_url)=v_url))
    or (v_type='event' and exists(select 1 from public.events e where e.id=p_entity_id and e.creator_id=v_uid and e.banner_url=v_url))
  ) then
    raise exception 'Media is not an indexable asset owned by the authenticated user';
  end if;

  execute $upsert$
    insert into public.search_image_embeddings(entity_type,entity_id,image_url,model,embedding,updated_at)
    values($1,$2,$3,'blink-perceptual-rgb-luma-v1',$4::real[]::extensions.vector,now())
    on conflict(entity_type,entity_id,image_url) do update
      set model=excluded.model,embedding=excluded.embedding,updated_at=excluded.updated_at
  $upsert$ using v_type,p_entity_id,v_url,p_embedding;
  return true;
end;
$$;
revoke all on function public.upsert_search_image_embedding_v2(text,uuid,text,real[]) from public, anon;
grant execute on function public.upsert_search_image_embedding_v2(text,uuid,text,real[]) to authenticated;
