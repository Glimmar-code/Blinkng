-- BLINK Live Campaigns v1. Additive; staging-only until all CI and client tests pass.
-- Supports scheduling, university/verification targeting, placement, styling,
-- drafts and owner approval, preview, templates, version gates and analytics.
-- Rollback: revoke RPC grants; pause all campaigns; drop RPCs/tables only after
-- app compatibility review. Never use campaigns to authorize monetary actions.
begin;

create table if not exists public.blink_live_campaigns (
 id uuid primary key default gen_random_uuid(),
 kind text not null default 'announcement'
   check (kind in ('announcement','promotion','marketplace','game','challenge','event','maintenance','emergency')),
 title text not null default '' check (char_length(title) <= 80),
 message text not null default '' check (char_length(message) <= 500),
 status text not null default 'draft'
   check (status in ('draft','pending','approved','paused','archived')),
 placements text[] not null default array['home']::text[],
 audience_universities text[] not null default '{}'::text[],
 audience_badge text not null default 'all'
   check (audience_badge in ('all','free','verified','blue','gold')),
 image_url text check (image_url is null or (char_length(image_url) <= 500 and image_url ~ '^https://[^[:space:]]+$')),
 action_label text check (action_label is null or char_length(action_label) <= 36),
 action_route text check (action_route is null or action_route in
  ('home','reels','marketplace','connect','messages','games','notifications','store','leaderboard','profile')),
 style text not null default 'standard'
   check (style in ('standard','highlight','urgent')),
 accent text not null default 'neutral' check (accent in ('neutral','blue','gold')),
 priority int not null default 10 check (priority between 0 and 100),
 pinned boolean not null default false,
 starts_at timestamptz,
 ends_at timestamptz,
 min_app_version integer check (min_app_version is null or min_app_version >= 0),
 max_app_version integer check (max_app_version is null or max_app_version >= 0),
 frequency_hours integer not null default 24 check (frequency_hours between 0 and 168),
 is_template boolean not null default false,
 revision bigint not null default 1,
 created_by uuid references auth.users(id),
 updated_by uuid references auth.users(id),
 approved_by uuid references auth.users(id),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 constraint blink_live_placements_valid check (
  cardinality(placements) between 1 and 6 and
  placements <@ array['home','reels','marketplace','connect','messages','games']::text[]
 ),
 constraint blink_live_audience_universities_valid check (
   cardinality(audience_universities) <= 30
 ),
 constraint blink_live_campaign_dates check (ends_at is null or starts_at is null or ends_at > starts_at),
 constraint blink_live_version_range check (max_app_version is null or min_app_version is null or max_app_version >= min_app_version),
 constraint blink_live_action_valid check ((action_label is null and action_route is null) or
    (action_label is not null and action_route is not null)),
 constraint blink_live_publish_requires_content check (
  status not in ('approved') or (length(trim(title)) > 0 and length(trim(message)) > 0))
);

create index if not exists blink_live_campaign_active_idx
 on public.blink_live_campaigns (status,priority desc,starts_at,ends_at);
alter table public.blink_live_campaigns enable row level security;
revoke all on public.blink_live_campaigns from public,anon,authenticated;

create table if not exists private.blink_live_campaign_history (
 id bigint generated always as identity primary key,
 campaign_id uuid not null,
 revision bigint not null,
 actor_id uuid not null references auth.users(id),
 operation text not null,
 old_row jsonb,
 new_row jsonb,
 at timestamptz not null default now()
);
alter table private.blink_live_campaign_history enable row level security;
revoke all on private.blink_live_campaign_history from public,anon,authenticated;
create index if not exists blink_live_campaign_history_lookup
 on private.blink_live_campaign_history (campaign_id, revision desc);

create table if not exists private.blink_live_campaign_events (
 campaign_id uuid not null references public.blink_live_campaigns(id) on delete cascade,
 user_id uuid not null references auth.users(id) on delete cascade,
 event_day date not null default (now() at time zone 'UTC')::date,
 event_kind text not null check (event_kind in ('impression','click','dismiss')),
 created_at timestamptz not null default now(),
 primary key (campaign_id,user_id,event_day,event_kind)
);
alter table private.blink_live_campaign_events enable row level security;
revoke all on private.blink_live_campaign_events from public,anon,authenticated;
create index if not exists blink_live_campaign_events_date_idx
 on private.blink_live_campaign_events (event_day);

-- Check suitability for one account without exposing draft or audience targeting.
create or replace function private.blink_live_visible(
 p_id uuid,p_placement text,p_app_version integer,p_user uuid
) returns boolean language sql stable security definer set search_path = '' as $$
 select exists(
  select 1 from public.blink_live_campaigns c
  where c.id=p_id and c.status='approved' and not c.is_template
   and p_placement=any(c.placements)
   and (c.starts_at is null or c.starts_at<=now())
   and (c.ends_at is null or c.ends_at>now())
   and (c.min_app_version is null or p_app_version>=c.min_app_version)
   and (c.max_app_version is null or p_app_version<=c.max_app_version)
   and (
     cardinality(c.audience_universities)=0
     or exists (select 1 from public.profiles p where p.id=p_user
          and lower(trim(coalesce(p.university,'')))=any(
          select lower(trim(q)) from unnest(c.audience_universities) q))
   )
   and (
    c.audience_badge='all'
    or exists(select 1 from public.profiles p where p.id=p_user and
      case c.audience_badge
       when 'free' then not coalesce(p.is_verified,false)
       when 'verified' then coalesce(p.is_verified,false)
       when 'blue' then coalesce(p.is_verified,false) and upper(coalesce(p.verification_badge,''))='BLUE'
       when 'gold' then coalesce(p.is_verified,false) and upper(coalesce(p.verification_badge,''))='GOLD'
       else false end
    )
   )
 );
$$;
revoke all on function private.blink_live_visible(uuid,text,integer,uuid) from public,anon,authenticated;

-- All viewers get only their own eligible *approved* campaigns.
create or replace function public.get_blink_live_campaigns(
 p_placement text, p_app_version integer default 0
) returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare v_uid uuid:=auth.uid();
begin
 if p_placement not in ('home','reels','marketplace','connect','messages','games')
     or p_app_version is null or p_app_version < 0 then
   return '[]'::jsonb;
 end if;
 return coalesce((
  select jsonb_agg(jsonb_build_object(
    'id',q.id,'kind',q.kind,'title',q.title,'message',q.message,
    'image_url',q.image_url,'action_label',q.action_label,
    'action_route',q.action_route,'style',q.style,'accent',q.accent,
    'pinned',q.pinned,'frequency_hours',q.frequency_hours,
    'priority',q.priority,'ends_at',q.ends_at
  ) order by (q.kind='emergency') desc,q.pinned desc,q.priority desc,q.created_at desc)
  from (
    select c.*
    from public.blink_live_campaigns c
    where private.blink_live_visible(c.id,p_placement,p_app_version,v_uid)
    order by (c.kind='emergency') desc,c.pinned desc,c.priority desc,c.created_at desc
    limit 4
  ) q
 ),'[]'::jsonb);
end; $$;

-- Analytics: authenticated only, once per event per user per UTC day.
-- These are directional engagement analytics, NOT billing-quality ad impressions.
create or replace function public.record_blink_live_campaign_event(
 p_id uuid,p_placement text,p_app_version integer,p_event text
) returns boolean language plpgsql security definer set search_path = '' as $$
declare v_uid uuid:=auth.uid();
begin
 if v_uid is null or p_event not in ('impression','click','dismiss')
    or p_app_version is null or p_app_version < 0
    or not private.blink_live_visible(p_id,p_placement,p_app_version,v_uid) then
   return false;
 end if;
 if p_event='click' and not exists (
    select 1 from private.blink_live_campaign_events e
    where e.campaign_id=p_id and e.user_id=v_uid
      and e.event_kind='impression'
      and e.event_day=(now() at time zone 'UTC')::date
 ) then return false; end if;
 insert into private.blink_live_campaign_events(campaign_id,user_id,event_day,event_kind)
 values(p_id,v_uid,(now() at time zone 'UTC')::date,p_event)
 on conflict do nothing;
 return true;
end; $$;

-- Preview, scheduling calendar and per-campaign totals for authorized admins.
create or replace function public.admin_list_blink_live_campaigns()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
 perform private.require_blink_admin();
 return coalesce((
   select jsonb_agg(jsonb_build_object(
     'id',c.id,'kind',c.kind,'title',c.title,'message',c.message,
     'status',c.status,'placements',c.placements,
     'audience_universities',c.audience_universities,
     'audience_badge',c.audience_badge,'image_url',c.image_url,
     'action_label',c.action_label,'action_route',c.action_route,
     'style',c.style,'accent',c.accent,'priority',c.priority,
     'pinned',c.pinned,'starts_at',c.starts_at,'ends_at',c.ends_at,
     'min_app_version',c.min_app_version,'max_app_version',c.max_app_version,
     'frequency_hours',c.frequency_hours,'is_template',c.is_template,
     'revision',c.revision,'created_at',c.created_at,
     'impressions',coalesce(v.impressions,0),
     'clicks',coalesce(v.clicks,0),
     'dismissals',coalesce(v.dismissals,0)
   ) order by c.created_at desc)
   from public.blink_live_campaigns c
   left join lateral (
     select count(*) filter(where event_kind='impression') as impressions,
            count(*) filter(where event_kind='click') as clicks,
            count(*) filter(where event_kind='dismiss') as dismissals
     from private.blink_live_campaign_events e where e.campaign_id=c.id
   ) v on true
 ),'[]'::jsonb);
end; $$;

-- Admin authors can save drafts; only owner can modify approved/paused content,
-- approve requests, or directly publish. All actions are revision-guarded.
create or replace function public.admin_save_blink_live_campaign(
 p_id uuid,p_data jsonb,p_expected_revision bigint default null
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
 v_actor uuid:=private.require_blink_admin();
 v_role text:=private.active_blink_admin_role(v_actor);
 v_old public.blink_live_campaigns%rowtype;
 v_new public.blink_live_campaigns%rowtype;
 v_key text;
 v_universities text[];
 v_places text[];
 v_starts timestamptz;
 v_ends timestamptz;
begin
 if p_data is null or jsonb_typeof(p_data)<>'object' or octet_length(p_data::text)>10000 then
   raise exception 'INVALID_CAMPAIGN_PAYLOAD';
 end if;
 for v_key in select jsonb_object_keys(p_data) loop
   if v_key not in ('kind','title','message','placements','audience_universities',
      'audience_badge','image_url','action_label','action_route','style','accent',
      'priority','pinned','starts_at','ends_at','min_app_version','max_app_version',
      'frequency_hours','is_template') then
     raise exception 'UNSUPPORTED_CAMPAIGN_FIELD';
   end if;
 end loop;
 if jsonb_typeof(p_data->'placements')<>'array'
    or jsonb_typeof(p_data->'audience_universities')<>'array'
    or exists (select 1 from jsonb_array_elements(p_data->'placements') e where jsonb_typeof(e)<>'string')
    or exists (select 1 from jsonb_array_elements(p_data->'audience_universities') e where jsonb_typeof(e)<>'string') then
    raise exception 'INVALID_TARGETING_ARRAYS';
 end if;
 select coalesce(array_agg(x),'{}') into v_places from jsonb_array_elements_text(p_data->'placements') x;
 select coalesce(array_agg(x),'{}') into v_universities
   from jsonb_array_elements_text(p_data->'audience_universities') x;
 if cardinality(v_universities)>30 or exists(
   select 1 from unnest(v_universities) x where char_length(x)>100 or trim(x)='' or x is null
 ) then raise exception 'INVALID_UNIVERSITY_TARGET'; end if;
 if coalesce(p_data->>'starts_at','')<>'' then v_starts:=(p_data->>'starts_at')::timestamptz; end if;
 if coalesce(p_data->>'ends_at','')<>'' then v_ends:=(p_data->>'ends_at')::timestamptz; end if;
 if p_id is not null then
   select * into v_old from public.blink_live_campaigns where id=p_id for update;
   if not found then raise exception 'CAMPAIGN_NOT_FOUND'; end if;
   if p_expected_revision is null or p_expected_revision<>v_old.revision then
     raise exception 'LIVE_CAMPAIGN_CHANGED_RELOAD' using errcode='40001';
   end if;
   if v_old.status in ('approved','paused','archived') and v_role<>'owner' then
     raise exception 'OWNER_REQUIRED' using errcode='42501';
   end if;
   update public.blink_live_campaigns
   set kind=(p_data->>'kind'),title=(p_data->>'title'),message=(p_data->>'message'),
     placements=v_places,audience_universities=v_universities,
     audience_badge=(p_data->>'audience_badge'),
     image_url=nullif(p_data->>'image_url',''),
     action_label=nullif(p_data->>'action_label',''),
     action_route=nullif(p_data->>'action_route',''),
     style=(p_data->>'style'),accent=(p_data->>'accent'),
     priority=(p_data->>'priority')::integer,
     pinned=(p_data->>'pinned')::boolean,
     starts_at=v_starts,ends_at=v_ends,
     min_app_version=nullif(p_data->>'min_app_version','')::integer,
     max_app_version=nullif(p_data->>'max_app_version','')::integer,
     frequency_hours=(p_data->>'frequency_hours')::integer,
     is_template=(p_data->>'is_template')::boolean,
     status='draft',approved_by=null,
     revision=revision+1,updated_by=v_actor,updated_at=now()
   where id=p_id returning * into v_new;
 else
   insert into public.blink_live_campaigns(
    kind,title,message,placements,audience_universities,audience_badge,
    image_url,action_label,action_route,style,accent,priority,pinned,
    starts_at,ends_at,min_app_version,max_app_version,frequency_hours,
    is_template,created_by,updated_by
   ) values (
    p_data->>'kind',p_data->>'title',p_data->>'message',v_places,v_universities,
    p_data->>'audience_badge',nullif(p_data->>'image_url',''),
    nullif(p_data->>'action_label',''),nullif(p_data->>'action_route',''),
    p_data->>'style',p_data->>'accent',(p_data->>'priority')::integer,
    (p_data->>'pinned')::boolean,v_starts,v_ends,
    nullif(p_data->>'min_app_version','')::integer,
    nullif(p_data->>'max_app_version','')::integer,
    (p_data->>'frequency_hours')::integer,(p_data->>'is_template')::boolean,
    v_actor,v_actor
   ) returning * into v_new;
 end if;
 insert into private.blink_live_campaign_history(
  campaign_id,revision,actor_id,operation,old_row,new_row
 ) values(v_new.id,v_new.revision,v_actor,'save_draft',to_jsonb(v_old),to_jsonb(v_new));
 return jsonb_build_object('id',v_new.id,'revision',v_new.revision,'status',v_new.status);
end; $$;

-- State transitions are separate and cannot be spoofed by changing JSON.
create or replace function public.admin_transition_blink_live_campaign(
 p_id uuid,p_expected_revision bigint,p_action text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
 v_actor uuid:=private.require_blink_admin();
 v_old public.blink_live_campaigns%rowtype;
 v_new public.blink_live_campaigns%rowtype;
begin
 select * into v_old from public.blink_live_campaigns where id=p_id for update;
 if not found or p_expected_revision is null or v_old.revision<>p_expected_revision then
   raise exception 'LIVE_CAMPAIGN_CHANGED_RELOAD' using errcode='40001';
 end if;
 if p_action='submit' and v_old.status='draft' then
   update public.blink_live_campaigns set status='pending',revision=revision+1,
      updated_by=v_actor,updated_at=now() where id=p_id returning * into v_new;
 elsif p_action in ('approve','pause','archive') then
   perform private.require_blink_owner();
   if p_action='approve' and v_old.status in ('pending','draft','paused')
      and not v_old.is_template and length(trim(v_old.title))>0
      and length(trim(v_old.message))>0 then
     update public.blink_live_campaigns set status='approved',revision=revision+1,
       approved_by=v_actor,updated_by=v_actor,updated_at=now()
       where id=p_id returning * into v_new;
   elsif p_action='pause' and v_old.status='approved' then
     update public.blink_live_campaigns set status='paused',revision=revision+1,
       updated_by=v_actor,updated_at=now() where id=p_id returning * into v_new;
   elsif p_action='archive' and v_old.status<>'archived' then
     update public.blink_live_campaigns set status='archived',revision=revision+1,
       updated_by=v_actor,updated_at=now() where id=p_id returning * into v_new;
   end if;
 end if;
 if v_new.id is null then raise exception 'INVALID_CAMPAIGN_TRANSITION'; end if;
 insert into private.blink_live_campaign_history(
  campaign_id,revision,actor_id,operation,old_row,new_row
 ) values(p_id,v_new.revision,v_actor,p_action,to_jsonb(v_old),to_jsonb(v_new));
 return jsonb_build_object('id',v_new.id,'revision',v_new.revision,'status',v_new.status);
end; $$;

-- Owner can restore a previous revision as an unpublished draft for review;
-- never silently restore a live promotion.
create or replace function public.admin_restore_blink_live_campaign(
 p_id uuid,p_expected_revision bigint,p_history_revision bigint
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
 v_actor uuid:=private.require_blink_owner();
 v_current public.blink_live_campaigns%rowtype;
 v_history jsonb;
 v_new public.blink_live_campaigns%rowtype;
begin
 select * into v_current from public.blink_live_campaigns where id=p_id for update;
 if not found or p_expected_revision<>v_current.revision then
   raise exception 'LIVE_CAMPAIGN_CHANGED_RELOAD' using errcode='40001';
 end if;
 select h.new_row into v_history from private.blink_live_campaign_history h
 where h.campaign_id=p_id and h.revision=p_history_revision
 order by h.id desc limit 1;
 if v_history is null then raise exception 'HISTORY_REVISION_NOT_FOUND'; end if;
 update public.blink_live_campaigns set
  kind=v_history->>'kind',title=v_history->>'title',message=v_history->>'message',
  placements=(select array_agg(x) from jsonb_array_elements_text(v_history->'placements') x),
  audience_universities=coalesce((select array_agg(x)
    from jsonb_array_elements_text(v_history->'audience_universities') x),'{}'::text[]),
  audience_badge=v_history->>'audience_badge',image_url=v_history->>'image_url',
  action_label=v_history->>'action_label',action_route=v_history->>'action_route',
  style=v_history->>'style',accent=v_history->>'accent',
  priority=(v_history->>'priority')::int,pinned=(v_history->>'pinned')::boolean,
  starts_at=(v_history->>'starts_at')::timestamptz,
  ends_at=(v_history->>'ends_at')::timestamptz,
  min_app_version=(v_history->>'min_app_version')::integer,
  max_app_version=(v_history->>'max_app_version')::integer,
  frequency_hours=(v_history->>'frequency_hours')::integer,
  is_template=(v_history->>'is_template')::boolean,
  status='draft',approved_by=null,revision=revision+1,
  updated_by=v_actor,updated_at=now()
 where id=p_id returning * into v_new;
 insert into private.blink_live_campaign_history(
  campaign_id,revision,actor_id,operation,old_row,new_row
 ) values(p_id,v_new.revision,v_actor,'restore_draft',to_jsonb(v_current),to_jsonb(v_new));
 return jsonb_build_object('id',v_new.id,'revision',v_new.revision,'status',v_new.status);
end; $$;

create or replace function public.admin_blink_live_campaign_history(
 p_id uuid
) returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
 perform private.require_blink_admin();
 return coalesce((
  select jsonb_agg(jsonb_build_object(
    'revision',h.revision,'operation',h.operation,'at',h.at
  ) order by h.revision desc) from (
    select * from private.blink_live_campaign_history
    where campaign_id=p_id order by revision desc limit 50
  ) h
 ),'[]'::jsonb);
end; $$;

-- Expose a narrow API. No direct access to private tables.
revoke all on function public.get_blink_live_campaigns(text,integer) from public,anon,authenticated;
revoke all on function public.record_blink_live_campaign_event(uuid,text,integer,text) from public,anon,authenticated;
revoke all on function public.admin_list_blink_live_campaigns() from public,anon,authenticated;
revoke all on function public.admin_save_blink_live_campaign(uuid,jsonb,bigint) from public,anon,authenticated;
revoke all on function public.admin_transition_blink_live_campaign(uuid,bigint,text) from public,anon,authenticated;
revoke all on function public.admin_restore_blink_live_campaign(uuid,bigint,bigint) from public,anon,authenticated;
revoke all on function public.admin_blink_live_campaign_history(uuid) from public,anon,authenticated;

grant execute on function public.get_blink_live_campaigns(text,integer) to anon,authenticated;
grant execute on function public.record_blink_live_campaign_event(uuid,text,integer,text) to authenticated;
grant execute on function public.admin_list_blink_live_campaigns() to authenticated;
grant execute on function public.admin_save_blink_live_campaign(uuid,jsonb,bigint) to authenticated;
grant execute on function public.admin_transition_blink_live_campaign(uuid,bigint,text) to authenticated;
grant execute on function public.admin_restore_blink_live_campaign(uuid,bigint,bigint) to authenticated;
grant execute on function public.admin_blink_live_campaign_history(uuid) to authenticated;

commit;
