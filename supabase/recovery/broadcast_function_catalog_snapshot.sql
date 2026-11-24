-- RECOVERY SNAPSHOT ONLY: Exact public function definitions exported from production.
-- Project: jhwgifrlxwspoedxjaly
-- Retrieved: 2026-11-24
-- DO NOT replay this file as a migration or execute blindly against production.
-- Source is for comparison, migration reconstruction and Testlab validation.

-- Function: admin_blink_live_campaign_analytics(p_days integer)
CREATE OR REPLACE FUNCTION public.admin_blink_live_campaign_analytics(p_days integer DEFAULT 30)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_today date := (now() at time zone 'UTC')::date;
  v_since date;
  v_summary jsonb;
  v_daily jsonb;
  v_top jsonb;
begin
  perform private.require_blink_admin();
  if p_days is null or p_days not in (7, 30, 90) then
    raise exception 'INVALID_ANALYTICS_WINDOW' using errcode = '22023';
  end if;

  v_since := v_today - (p_days - 1);

  -- Summary is computed from immutable daily deduplicated engagement events.
  select jsonb_build_object(
    'impressions', count(*) filter (where event_kind='impression'),
    'clicks', count(*) filter (where event_kind='click'),
    'dismissals', count(*) filter (where event_kind='dismiss'),
    'ctr_pct', coalesce(round(
      100.0 * (count(*) filter (where event_kind='click'))
      / nullif(count(*) filter (where event_kind='impression'),0),1
    ),0)
  )
  into v_summary
  from private.blink_live_campaign_events
  where event_day between v_since and v_today;

  -- Every UTC day appears, even with zero events.
  select coalesce(jsonb_agg(
      jsonb_build_object(
        'day', d.day::date,
        'impressions', coalesce(t.impressions,0),
        'clicks', coalesce(t.clicks,0),
        'dismissals', coalesce(t.dismissals,0)
      ) order by d.day
    ), '[]'::jsonb)
  into v_daily
  from generate_series(v_since,v_today,interval '1 day') as d(day)
  left join (
    select e.event_day,
      count(*) filter (where e.event_kind='impression') as impressions,
      count(*) filter (where e.event_kind='click') as clicks,
      count(*) filter (where e.event_kind='dismiss') as dismissals
    from private.blink_live_campaign_events e
    where e.event_day between v_since and v_today
    group by e.event_day
  ) t on t.event_day=d.day::date;

  -- Ranking uses this same requested period, not lifetime values.
  -- Includes historical archived/paused campaigns to avoid hiding results.
  select coalesce(jsonb_agg(
    jsonb_build_object(
      'id', x.id, 'title', x.title, 'kind', x.kind,
      'status', x.status, 'impressions', x.impressions,
      'clicks', x.clicks, 'dismissals', x.dismissals,
      'ctr_pct', x.ctr_pct
    ) order by x.clicks desc,x.impressions desc,x.title,x.id
  ), '[]'::jsonb)
  into v_top
  from (
    select c.id,c.title,c.kind,c.status,
      coalesce(e.impressions,0) as impressions,
      coalesce(e.clicks,0) as clicks,
      coalesce(e.dismissals,0) as dismissals,
      coalesce(round(100.0 * e.clicks / nullif(e.impressions,0),1),0) as ctr_pct
    from public.blink_live_campaigns c
    left join (
      select campaign_id,
        count(*) filter (where event_kind='impression') as impressions,
        count(*) filter (where event_kind='click') as clicks,
        count(*) filter (where event_kind='dismiss') as dismissals
      from private.blink_live_campaign_events
      where event_day between v_since and v_today
      group by campaign_id
    ) e on e.campaign_id = c.id
    where not c.is_template and coalesce(e.impressions,0)>0
    order by coalesce(e.clicks,0) desc,coalesce(e.impressions,0) desc,c.title,c.id
    limit 15
  ) x;

  return jsonb_build_object(
    'days', p_days,
    'from_date', v_since,
    'through_date', v_today,
    'summary', v_summary,
    'daily', v_daily,
    'top_campaigns', v_top,
    'metric_note', 'Daily unique user/campaign actions in UTC. Estimates, not billable advertising impressions.'
  );
end;
$function$
;

-- Function: admin_blink_live_campaign_history(p_id uuid)
CREATE OR REPLACE FUNCTION public.admin_blink_live_campaign_history(p_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
end; $function$
;

-- Function: admin_broadcast_analytics(p_id uuid)
CREATE OR REPLACE FUNCTION public.admin_broadcast_analytics(p_id uuid)
 RETURNS jsonb
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select jsonb_build_object('reach',(select count(distinct user_id) from broadcast_impressions where broadcast_id=p_id),'impressions',(select count(*) from broadcast_impressions where broadcast_id=p_id),'clicks',(select count(*) from broadcast_clicks where broadcast_id=p_id),'ctr',case when (select count(*) from broadcast_impressions where broadcast_id=p_id)=0 then 0 else (select count(*) from broadcast_clicks where broadcast_id=p_id)::numeric/(select count(*) from broadcast_impressions where broadcast_id=p_id) end,'dismissals',(select count(*) from broadcast_dismissals where broadcast_id=p_id),'claims',(select count(*) from promo_claims where broadcast_id=p_id and status='claimed'),'budget',(select jsonb_build_object('used',budget_used,'cap',total_budget_cap) from promo_rules where broadcast_id=p_id),'daily',(select jsonb_agg(to_jsonb(d)) from broadcast_daily_rollups d where broadcast_id=p_id)) $function$
;

-- Function: admin_create_broadcast(p_payload jsonb)
CREATE OR REPLACE FUNCTION public.admin_create_broadcast(p_payload jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare actor uuid:=private.require_blink_admin(); bid uuid; t public.broadcast_type; aud jsonb;begin t:=(p_payload->>'type')::public.broadcast_type; aud:=coalesce(p_payload->'audience','{"kind":"everyone"}'); if coalesce(aud->>'kind','everyone')='everyone' and coalesce((p_payload->>'confirm_everyone')::boolean,false) is not true then raise exception 'CONFIRM_EVERYONE_REQUIRED'; end if; if length(coalesce(p_payload->>'title',''))>60 or length(coalesce(p_payload->>'body',''))>240 then raise exception 'CONTENT_TOO_LONG'; end if; if p_payload ? 'starts_at' and p_payload ? 'ends_at' and (p_payload->>'ends_at')::timestamptz<=(p_payload->>'starts_at')::timestamptz then raise exception 'INVALID_WINDOW'; end if; insert into broadcasts(type,status,title,body,image_url,cta_label,deep_link,priority,starts_at,ends_at,timezone,dismissible,is_blocking,block_kind,min_app_version,store_url,audience,channels,created_by,updated_by) values(t,coalesce((p_payload->>'status')::public.broadcast_status,'draft'),p_payload->>'title',p_payload->>'body',p_payload->>'image_url',p_payload->>'cta_label',p_payload->>'deep_link',coalesce((p_payload->>'priority')::int,0),(p_payload->>'starts_at')::timestamptz,(p_payload->>'ends_at')::timestamptz,coalesce(p_payload->>'timezone','Africa/Lagos'),coalesce((p_payload->>'dismissible')::boolean,true),coalesce((p_payload->>'is_blocking')::boolean,false),(p_payload->>'block_kind')::public.broadcast_block_kind,p_payload->>'min_app_version',p_payload->>'store_url',aud,coalesce(p_payload->'channels','{}'),actor,actor) returning id into bid; if t='CAMPAIGN' then insert into campaign_rules(broadcast_id,goal_type,target,tiers) values(bid,(p_payload->>'goal_type')::broadcast_goal_type,(p_payload->>'target')::int,coalesce(p_payload->'tiers','[]')); elsif t='PROMO' then insert into promo_rules(broadcast_id,reward_type,reward_amount,per_user_limit,total_budget_cap,min_purchase,reward_text,placement) values(bid,(p_payload->>'reward_type')::broadcast_reward_type,(p_payload->>'reward_amount')::numeric,coalesce((p_payload->>'per_user_limit')::int,1),(p_payload->>'total_budget_cap')::numeric,coalesce((p_payload->>'min_purchase')::numeric,0),p_payload->>'reward_text',coalesce(array(select jsonb_array_elements_text(p_payload->'placement')),'{}')); end if; perform broadcast_log(actor,'create',bid,null,(select to_jsonb(b) from broadcasts b where id=bid)); return jsonb_build_object('id',bid,'version',1);end $function$
;

-- Function: admin_delete_broadcast(p_id uuid)
CREATE OR REPLACE FUNCTION public.admin_delete_broadcast(p_id uuid)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare actor uuid:=private.require_blink_admin(); oldrow broadcasts%rowtype;begin select * into oldrow from broadcasts where id=p_id for update; if not found or oldrow.status<>'draft' then raise exception 'DRAFT_ONLY_DELETE'; end if; update broadcasts set deleted_at=now(),updated_at=now(),updated_by=actor,version=version+1 where id=p_id; perform broadcast_log(actor,'delete',p_id,to_jsonb(oldrow),null); return true;end $function$
;

-- Function: admin_duplicate_broadcast(p_id uuid)
CREATE OR REPLACE FUNCTION public.admin_duplicate_broadcast(p_id uuid)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare actor uuid:=private.require_blink_admin(); oldrow broadcasts%rowtype; newid uuid;begin select * into oldrow from broadcasts where id=p_id; if not found then raise exception 'NOT_FOUND'; end if; insert into broadcasts(type,status,title,body,image_url,cta_label,deep_link,priority,starts_at,ends_at,timezone,dismissible,is_blocking,block_kind,min_app_version,store_url,audience,channels,created_by,updated_by) values(oldrow.type,'draft',oldrow.title,oldrow.body,oldrow.image_url,oldrow.cta_label,oldrow.deep_link,oldrow.priority,null,null,oldrow.timezone,oldrow.dismissible,false,null,oldrow.min_app_version,oldrow.store_url,oldrow.audience,oldrow.channels,actor,actor) returning id into newid; if oldrow.type='CAMPAIGN' then insert into campaign_rules select newid,goal_type,target,tiers,now(),now() from campaign_rules where broadcast_id=p_id; elsif oldrow.type='PROMO' then insert into promo_rules(broadcast_id,reward_type,reward_amount,per_user_limit,total_budget_cap,min_purchase,reward_text,placement) select newid,reward_type,reward_amount,per_user_limit,total_budget_cap,min_purchase,reward_text,placement from promo_rules where broadcast_id=p_id; end if; perform broadcast_log(actor,'duplicate',newid,to_jsonb(oldrow),null); return newid;end $function$
;

-- Function: admin_end_broadcast(p_id uuid)
CREATE OR REPLACE FUNCTION public.admin_end_broadcast(p_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare actor uuid:=private.require_blink_admin(); oldrow broadcasts%rowtype;begin update broadcasts set status='ended',ends_at=now(),updated_by=actor,updated_at=now(),version=version+1 where id=p_id returning * into oldrow; if not found then raise exception 'NOT_FOUND'; end if; perform broadcast_log(actor,'end',p_id,null,to_jsonb(oldrow)); return to_jsonb(oldrow);end $function$
;

-- Function: admin_estimate_broadcast_audience(p_filters jsonb, p_app_version text)
CREATE OR REPLACE FUNCTION public.admin_estimate_broadcast_audience(p_filters jsonb, p_app_version text DEFAULT NULL::text)
 RETURNS bigint
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select count(*) from profiles p where broadcast_audience_match(p.id,p_filters,p_app_version) $function$
;

-- Function: admin_get_broadcast_audit(p_id uuid, p_limit integer)
CREATE OR REPLACE FUNCTION public.admin_get_broadcast_audit(p_id uuid DEFAULT NULL::uuid, p_limit integer DEFAULT 100)
 RETURNS jsonb
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select coalesce(jsonb_agg(to_jsonb(a) order by created_at desc),'[]') from (select * from broadcast_audit_log where p_id is null or broadcast_id=p_id order by created_at desc limit least(greatest(p_limit,1),250)) a $function$
;

-- Function: admin_list_blink_live_campaigns()
CREATE OR REPLACE FUNCTION public.admin_list_blink_live_campaigns()
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
end; $function$
;

-- Function: admin_restore_blink_live_campaign(p_id uuid, p_expected_revision bigint, p_history_revision bigint)
CREATE OR REPLACE FUNCTION public.admin_restore_blink_live_campaign(p_id uuid, p_expected_revision bigint, p_history_revision bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
end; $function$
;

-- Function: admin_save_blink_live_campaign(p_id uuid, p_data jsonb, p_expected_revision bigint)
CREATE OR REPLACE FUNCTION public.admin_save_blink_live_campaign(p_id uuid, p_data jsonb, p_expected_revision bigint DEFAULT NULL::bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
end; $function$
;

-- Function: admin_transition_blink_live_campaign(p_id uuid, p_expected_revision bigint, p_action text)
CREATE OR REPLACE FUNCTION public.admin_transition_blink_live_campaign(p_id uuid, p_expected_revision bigint, p_action text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
end; $function$
;

-- Function: admin_update_broadcast(p_id uuid, p_version integer, p_patch jsonb)
CREATE OR REPLACE FUNCTION public.admin_update_broadcast(p_id uuid, p_version integer, p_patch jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare actor uuid:=private.require_blink_admin(); oldrow broadcasts%rowtype; newrow broadcasts%rowtype;begin select * into oldrow from broadcasts where id=p_id for update; if not found then raise exception 'NOT_FOUND'; end if; if oldrow.version<>p_version then raise exception 'VERSION_CONFLICT'; end if; if coalesce(p_patch->>'title',oldrow.title) is null or length(coalesce(p_patch->>'title',oldrow.title))>60 or length(coalesce(p_patch->>'body',oldrow.body))>240 then raise exception 'CONTENT_TOO_LONG'; end if; if coalesce(p_patch->'audience',oldrow.audience)->>'kind'='everyone' and coalesce((p_patch->>'confirm_everyone')::boolean,false) is not true and oldrow.audience->>'kind' is distinct from 'everyone' then raise exception 'CONFIRM_EVERYONE_REQUIRED'; end if; update broadcasts set title=coalesce(p_patch->>'title',title),body=coalesce(p_patch->>'body',body),status=coalesce((p_patch->>'status')::broadcast_status,status),image_url=coalesce(p_patch->>'image_url',image_url),cta_label=coalesce(p_patch->>'cta_label',cta_label),deep_link=coalesce(p_patch->>'deep_link',deep_link),priority=coalesce((p_patch->>'priority')::int,priority),starts_at=coalesce((p_patch->>'starts_at')::timestamptz,starts_at),ends_at=coalesce((p_patch->>'ends_at')::timestamptz,ends_at),audience=coalesce(p_patch->'audience',audience),channels=coalesce(p_patch->'channels',channels),version=version+1,updated_by=actor,updated_at=now() where id=p_id returning * into newrow; perform broadcast_log(actor,'update',p_id,to_jsonb(oldrow),to_jsonb(newrow)); return to_jsonb(newrow);end $function$
;

-- Function: admin_update_broadcast_config(p_patch jsonb)
CREATE OR REPLACE FUNCTION public.admin_update_broadcast_config(p_patch jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare actor uuid:=private.require_blink_admin(); out jsonb;begin update broadcast_app_config set maintenance_enabled=coalesce((p_patch->>'maintenance_enabled')::boolean,maintenance_enabled),maintenance_message=coalesce(p_patch->>'maintenance_message',maintenance_message),min_app_version=coalesce(p_patch->>'min_app_version',min_app_version),store_url=coalesce(p_patch->>'store_url',store_url),updated_by=actor,updated_at=now() where id=true returning to_jsonb(broadcast_app_config.*) into out; insert into broadcast_audit_log(actor_id,action,before,after) values(actor,'config_update',null,out);return out;end $function$
;

-- Function: broadcast_admin_check()
CREATE OR REPLACE FUNCTION public.broadcast_admin_check()
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$ select auth.uid() is not null and private.active_blink_admin_role(auth.uid()) is not null and (private.active_blink_admin_role(auth.uid())='owner' or exists(select 1 from private.admin_roles r where r.user_id=auth.uid() and r.permissions && array['broadcasts','campaigns','announcements','*'] and (r.expires_at is null or r.expires_at>now()))) $function$
;

-- Function: broadcast_audience_match(p_user uuid, p_audience jsonb, p_app_version text)
CREATE OR REPLACE FUNCTION public.broadcast_audience_match(p_user uuid, p_audience jsonb, p_app_version text DEFAULT NULL::text)
 RETURNS boolean
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare prof public.profiles%rowtype; campus_arr jsonb; n integer; inactive integer; cmp integer;begin select * into prof from profiles where id=p_user; if not found then return false; end if; campus_arr:=coalesce(p_audience->'campus_ids',p_audience->'university_ids','[]'::jsonb); if jsonb_typeof(campus_arr)='array' and jsonb_array_length(campus_arr)>0 and not campus_arr ? prof.university_id then return false; end if; if coalesce((p_audience->>'verified')::boolean,false) and not prof.is_verified then return false; end if; if coalesce((p_audience->>'sellers')::boolean,false) and not prof.is_seller_active then return false; end if; if p_audience ? 'new_users_days' then n:=greatest(0,(p_audience->>'new_users_days')::int); if prof.created_at<now()-make_interval(days=>n) then return false; end if; end if; if p_audience ? 'inactive_days' then inactive:=(p_audience->>'inactive_days')::int; if prof.last_active_at>now()-make_interval(days=>inactive) then return false; end if; end if; if p_audience ? 'min_app_version' then cmp:=broadcast_semver_compare(p_app_version,p_audience->>'min_app_version'); if cmp is null or cmp<0 then return false; end if; end if; if p_audience ? 'max_app_version' then cmp:=broadcast_semver_compare(p_app_version,p_audience->>'max_app_version'); if cmp is null or cmp>0 then return false; end if; end if; return true;end $function$
;

-- Function: broadcast_audience_match_self(p_audience jsonb)
CREATE OR REPLACE FUNCTION public.broadcast_audience_match_self(p_audience jsonb)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select public.broadcast_audience_match(auth.uid(),p_audience,null) $function$
;

-- Function: broadcast_audit_immutable()
CREATE OR REPLACE FUNCTION public.broadcast_audit_immutable()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$ begin raise exception 'AUDIT_LOG_IMMUTABLE'; end $function$
;

-- Function: broadcast_campaign_reward(p_broadcast_id uuid, p_user uuid, p_progress integer)
CREATE OR REPLACE FUNCTION public.broadcast_campaign_reward(p_broadcast_id uuid, p_user uuid, p_progress integer)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare participant campaign_participants%rowtype; rule campaign_rules%rowtype; tier jsonb; reward_amount bigint; reward_type text; bal numeric;begin select * into participant from campaign_participants where broadcast_id=p_broadcast_id and user_id=p_user for update; if not found or participant.reward_claimed_at is not null then return false; end if; select * into rule from campaign_rules where broadcast_id=p_broadcast_id; select x into tier from jsonb_array_elements(rule.tiers) x where coalesce((x->>'threshold')::int,0)<=p_progress order by (x->>'threshold')::int desc limit 1; if tier is null then return false; end if; reward_type:=tier->>'reward_type'; if reward_type<>'coins' then return false; end if; reward_amount:=coalesce((tier->>'amount')::bigint,0); if reward_amount<=0 or reward_amount>1000000 then return false; end if; update campaign_participants set reward_claimed_at=now(),reward_tier=(tier->>'threshold')::int where broadcast_id=p_broadcast_id and user_id=p_user and reward_claimed_at is null; if not found then return false; end if; insert into user_balances(user_id,spendable_coin_balance,updated_at) values(p_user,0,now()) on conflict(user_id) do nothing; update user_balances set spendable_coin_balance=spendable_coin_balance+reward_amount,updated_at=now() where user_id=p_user returning spendable_coin_balance into bal; insert into blink_coin_transactions(user_id,kind,item_name,amount,balance_after,metadata) values(p_user,'campaign_reward','Broadcast campaign reward',reward_amount,floor(bal)::bigint,jsonb_build_object('broadcast_id',p_broadcast_id,'reward_tier',tier->>'threshold','reason','campaign_reward')); return true;end $function$
;

-- Function: broadcast_last_active_trigger()
CREATE OR REPLACE FUNCTION public.broadcast_last_active_trigger()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$ begin if new.last_seen_at is distinct from old.last_seen_at then new.last_active_at:=new.last_seen_at; end if; return new;end $function$
;

-- Function: broadcast_lifecycle_audit_trigger()
CREATE OR REPLACE FUNCTION public.broadcast_lifecycle_audit_trigger()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ begin if old.status is distinct from new.status then insert into broadcast_audit_log(actor_id,action,broadcast_id,before,after) values(null,'system_transition',new.id,to_jsonb(old),to_jsonb(new)); end if; return new;end $function$
;

-- Function: broadcast_lifecycle_tick()
CREATE OR REPLACE FUNCTION public.broadcast_lifecycle_tick()
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare n integer:=0;changed integer;begin update broadcasts set status='live',updated_at=now(),version=version+1 where status='scheduled' and starts_at<=now() and (ends_at is null or ends_at>now()); get diagnostics changed=row_count; n:=n+changed; update broadcasts set status='ended',updated_at=now(),version=version+1 where status='live' and ends_at<=now(); get diagnostics changed=row_count; n:=n+changed; update broadcasts b set status='ended',updated_at=now(),version=version+1 from promo_rules p where b.id=p.broadcast_id and b.status='live' and p.total_budget_cap is not null and p.budget_used>=p.total_budget_cap; get diagnostics changed=row_count; n:=n+changed; return n;end $function$
;

-- Function: broadcast_log(p_actor uuid, p_action text, p_id uuid, p_before jsonb, p_after jsonb)
CREATE OR REPLACE FUNCTION public.broadcast_log(p_actor uuid, p_action text, p_id uuid, p_before jsonb, p_after jsonb)
 RETURNS void
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ insert into broadcast_audit_log(actor_id,action,broadcast_id,before,after) values(p_actor,p_action,p_id,p_before,p_after) $function$
;

-- Function: broadcast_order_campaign_trigger()
CREATE OR REPLACE FUNCTION public.broadcast_order_campaign_trigger()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ begin if new.status in ('completed','delivered','paid') and (tg_op='INSERT' or old.status is distinct from new.status) then perform record_broadcast_campaign_event('orders',new.buyer_id,'marketplace_order',new.id::text,1,new.updated_at); perform record_broadcast_campaign_event('purchases',new.buyer_id,'marketplace_order',new.id::text,1,new.updated_at); end if; return new;end $function$
;

-- Function: broadcast_purge_old_events()
CREATE OR REPLACE FUNCTION public.broadcast_purge_old_events()
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare n integer;begin delete from broadcast_impressions where created_at<now()-interval '90 days'; get diagnostics n=row_count; delete from broadcast_clicks where created_at<now()-interval '180 days'; return n;end $function$
;

-- Function: broadcast_push_cap_reserve(p_count integer)
CREATE OR REPLACE FUNCTION public.broadcast_push_cap_reserve(p_count integer)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare actor uuid:=private.require_blink_admin(); used bigint;begin if p_count<1 or p_count>10000 then return false; end if; perform pg_advisory_xact_lock(hashtext(actor::text)); select coalesce(sum((after->>'push_count')::bigint),0) into used from broadcast_audit_log where actor_id=actor and action='push_send' and created_at>now()-interval '1 hour'; if used+p_count>10000 then return false; end if; insert into broadcast_audit_log(actor_id,action,after) values(actor,'push_send',jsonb_build_object('push_count',p_count)); return true;end $function$
;

-- Function: broadcast_rate_limit(p_action text, p_max integer, p_seconds integer)
CREATE OR REPLACE FUNCTION public.broadcast_rate_limit(p_action text, p_max integer, p_seconds integer)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare uid uuid:=auth.uid(); n integer;begin if uid is null then return false; end if; insert into broadcast_rate_limits(user_id,action,window_started_at,count) values(uid,p_action,now(),1) on conflict(user_id,action) do update set count=case when broadcast_rate_limits.window_started_at<now()-make_interval(secs=>p_seconds) then 1 else broadcast_rate_limits.count+1 end,window_started_at=case when broadcast_rate_limits.window_started_at<now()-make_interval(secs=>p_seconds) then now() else broadcast_rate_limits.window_started_at end returning count into n; return n<=p_max;end $function$
;

-- Function: broadcast_referral_campaign_trigger()
CREATE OR REPLACE FUNCTION public.broadcast_referral_campaign_trigger()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ begin if new.status='confirmed' and new.confirmed_at is not null then perform record_broadcast_campaign_event('invites',new.referrer_user_id,'referral',new.referred_user_id::text,1,new.confirmed_at); end if; return new;end $function$
;

-- Function: broadcast_rollup_trigger()
CREATE OR REPLACE FUNCTION public.broadcast_rollup_trigger()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ begin insert into broadcast_daily_rollups(day,broadcast_id,impressions,clicks) values((new.created_at at time zone 'UTC')::date,new.broadcast_id,case when tg_table_name='broadcast_impressions' then 1 else 0 end,case when tg_table_name='broadcast_clicks' then 1 else 0 end) on conflict(day,broadcast_id) do update set impressions=broadcast_daily_rollups.impressions+excluded.impressions,clicks=broadcast_daily_rollups.clicks+excluded.clicks; return new;end $function$
;

-- Function: broadcast_semver_at_least(a text, b text)
CREATE OR REPLACE FUNCTION public.broadcast_semver_at_least(a text, b text)
 RETURNS boolean
 LANGUAGE plpgsql
 IMMUTABLE
AS $function$ declare aa int[];bb int[];i int;begin if b is null or b='' then return true; end if; if a is null or a='' then return false; end if; aa:=regexp_split_to_array(regexp_replace(a,'^[vV]',''), '\\.')::int[]; bb:=regexp_split_to_array(regexp_replace(b,'^[vV]',''), '\\.')::int[]; for i in 1..greatest(array_length(aa,1),array_length(bb,1),3) loop if coalesce(aa[i],0)>coalesce(bb[i],0) then return true; elsif coalesce(aa[i],0)<coalesce(bb[i],0) then return false; end if; end loop; return true; exception when others then return false; end $function$
;

-- Function: broadcast_semver_compare(a text, b text)
CREATE OR REPLACE FUNCTION public.broadcast_semver_compare(a text, b text)
 RETURNS integer
 LANGUAGE plpgsql
 IMMUTABLE
AS $function$ declare aa int[];bb int[];i int;va text;vb text;begin if a is null or b is null then return null; end if; va:=split_part(split_part(regexp_replace(a,'^[vV]',''),'+',1),'-',1); vb:=split_part(split_part(regexp_replace(b,'^[vV]',''),'+',1),'-',1); aa:=string_to_array(va,'.')::int[]; bb:=string_to_array(vb,'.')::int[]; for i in 1..greatest(array_length(aa,1),array_length(bb,1),3) loop if coalesce(aa[i],0)>coalesce(bb[i],0) then return 1; elsif coalesce(aa[i],0)<coalesce(bb[i],0) then return -1; end if; end loop; return 0; exception when others then return null; end $function$
;

-- Function: broadcast_signal_trigger()
CREATE OR REPLACE FUNCTION public.broadcast_signal_trigger()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ begin insert into broadcasts_changed(signal_id,changed_at) values(1,now()) on conflict(signal_id) do update set changed_at=now(); return coalesce(new,old);end $function$
;

-- Function: broadcast_vote_campaign_trigger()
CREATE OR REPLACE FUNCTION public.broadcast_vote_campaign_trigger()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ begin if new.review_status='valid' then perform record_broadcast_campaign_event('votes',new.voter_id,'contest_vote',new.id::text,1,new.created_at); end if; return new;end $function$
;

-- Function: cancel_blink_boost_campaign(p_campaign_id uuid)
CREATE OR REPLACE FUNCTION public.cancel_blink_boost_campaign(p_campaign_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
    v_user uuid := auth.uid();
    v_campaign public.blink_boost_campaigns_v2%rowtype;
    v_refund bigint := 0;
    v_balance numeric;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;

    select * into v_campaign
    from public.blink_boost_campaigns_v2 c
    where c.id=p_campaign_id and c.user_id=v_user
    for update;
    if not found then raise exception 'BOOST_CAMPAIGN_NOT_FOUND'; end if;
    if v_campaign.status<>'ACTIVE' then raise exception 'BOOST_CAMPAIGN_NOT_ACTIVE'; end if;

    v_refund := greatest(
        0,
        v_campaign.coin_budget - v_campaign.coin_spent - v_campaign.coin_refunded
    );

    update public.blink_boost_campaigns_v2
       set status='CANCELLED',
           cancelled_at=now(),
           coin_refunded=coin_refunded+v_refund,
           updated_at=now()
     where id=v_campaign.id;

    insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
    values(v_user,0,now())
    on conflict(user_id) do nothing;

    if v_refund>0 then
        update public.user_balances
           set spendable_coin_balance=spendable_coin_balance+v_refund,
               updated_at=now()
         where user_id=v_user
        returning spendable_coin_balance into v_balance;

        insert into public.blink_coin_transactions(
            user_id,kind,catalog_id,item_name,amount,balance_after,metadata
        ) values (
            v_user,'BOOST_CAMPAIGN_REFUND',null,'Boost campaign refund',
            v_refund,floor(v_balance)::bigint,
            jsonb_build_object(
                'campaign_id',v_campaign.id,
                'refund_method','unused_reserved_budget',
                'coin_spent',v_campaign.coin_spent
            )
        );
    else
        select spendable_coin_balance into v_balance
        from public.user_balances where user_id=v_user;
    end if;

    return jsonb_build_object(
        'success',true,
        'campaign_id',v_campaign.id,
        'spent',v_campaign.coin_spent,
        'refunded',v_refund,
        'balance',coalesce(floor(v_balance),0)::bigint
    );
end;
$function$
;

-- Function: claim_promo(p_broadcast_id uuid, p_idempotency_key text, p_purchase_amount numeric)
CREATE OR REPLACE FUNCTION public.claim_promo(p_broadcast_id uuid, p_idempotency_key text, p_purchase_amount numeric DEFAULT 0)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare uid uuid:=auth.uid(); pr promo_rules%rowtype; b broadcasts%rowtype; existing promo_claims%rowtype; claim_id uuid; bal numeric; result_status public.broadcast_claim_status; reward_json jsonb;begin if uid is null then raise exception 'AUTH_REQUIRED'; end if; if p_idempotency_key is null or length(trim(p_idempotency_key))<8 or length(p_idempotency_key)>128 then raise exception 'INVALID_IDEMPOTENCY_KEY'; end if; if not broadcast_rate_limit('claim',10,60) then return jsonb_build_object('status','NOT_ELIGIBLE','reason','RATE_LIMITED'); end if; select * into existing from promo_claims where broadcast_id=p_broadcast_id and user_id=uid and idempotency_key=p_idempotency_key; if found then return jsonb_build_object('status',upper(existing.status::text),'claim_id',existing.id,'reward',existing.reward,'idempotent',true); end if; select * into pr from promo_rules where broadcast_id=p_broadcast_id for update; if not found then return jsonb_build_object('status','EXPIRED'); end if; select * into existing from promo_claims where broadcast_id=p_broadcast_id and user_id=uid and idempotency_key=p_idempotency_key; if found then return jsonb_build_object('status',upper(existing.status::text),'claim_id',existing.id,'reward',existing.reward,'idempotent',true); end if; select * into b from broadcasts where id=p_broadcast_id; if pr.reward_type<>'coins' then return jsonb_build_object('status','NOT_ELIGIBLE','reason','reward_handler_not_configured'); end if; if b.status<>'live' or b.deleted_at is not null or (b.starts_at is not null and b.starts_at>now()) or (b.ends_at is not null and b.ends_at<=now()) then result_status:='expired'; elsif not broadcast_audience_match(uid,b.audience,null) then result_status:='not_eligible'; elsif pr.min_purchase>0 then return jsonb_build_object('status','NOT_ELIGIBLE','reason','purchase_verification_not_integrated'); elsif (select count(*) from promo_claims where broadcast_id=p_broadcast_id and user_id=uid and status='claimed')>=pr.per_user_limit then result_status:='limit_reached'; elsif pr.total_budget_cap is not null and pr.budget_used+pr.reward_amount>pr.total_budget_cap then result_status:='sold_out'; else result_status:='claimed'; end if; if result_status<>'claimed' then return jsonb_build_object('status',upper(result_status::text),'claim_id',null,'reward',null); end if; if pr.reward_amount<>floor(pr.reward_amount) or pr.reward_amount>1000000 then raise exception 'INVALID_COIN_REWARD_AMOUNT'; end if; reward_json:=jsonb_build_object('type',pr.reward_type,'amount',pr.reward_amount,'text',pr.reward_text); insert into promo_claims(broadcast_id,user_id,status,reward,idempotency_key) values(p_broadcast_id,uid,result_status,reward_json,p_idempotency_key) returning id into claim_id; update promo_rules set budget_used=budget_used+pr.reward_amount,updated_at=now() where broadcast_id=p_broadcast_id; insert into user_balances(user_id,spendable_coin_balance,updated_at) values(uid,0,now()) on conflict(user_id) do nothing; update user_balances set spendable_coin_balance=spendable_coin_balance+pr.reward_amount,updated_at=now() where user_id=uid returning spendable_coin_balance into bal; insert into blink_coin_transactions(user_id,kind,item_name,amount,balance_after,metadata) values(uid,'promo_claim','Broadcast promo reward',pr.reward_amount::bigint,floor(bal)::bigint,jsonb_build_object('broadcast_id',p_broadcast_id,'claim_id',claim_id,'reason','promo_claim')); return jsonb_build_object('status','CLAIMED','claim_id',claim_id,'reward',reward_json,'idempotent',false);end $function$
;

-- Function: create_blink_boost_campaign(p_target_type text, p_target_id uuid, p_boost_power integer, p_objective text, p_audience_scope text, p_duration_days integer, p_target_university text)
CREATE OR REPLACE FUNCTION public.create_blink_boost_campaign(p_target_type text, p_target_id uuid, p_boost_power integer, p_objective text, p_audience_scope text, p_duration_days integer, p_target_university text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
    v_user uuid := auth.uid();
    v_quote jsonb;
    v_cost bigint;
    v_balance numeric;
    v_campaign public.blink_boost_campaigns_v2%rowtype;
    v_university text;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;

    v_quote := public.quote_blink_boost_campaign(
        p_target_type,p_target_id,p_boost_power,p_objective,
        p_audience_scope,p_duration_days,p_target_university
    );
    v_cost := (v_quote->>'coin_cost')::bigint;
    v_university := nullif(v_quote->>'target_university','');

    insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
    values(v_user,0,now())
    on conflict(user_id) do nothing;

    select b.spendable_coin_balance into v_balance
    from public.user_balances b
    where b.user_id=v_user
    for update;

    if coalesce(v_balance,0)<v_cost then raise exception 'INSUFFICIENT_BLINK_COINS'; end if;

    update public.user_balances
       set spendable_coin_balance=spendable_coin_balance-v_cost,
           updated_at=now()
     where user_id=v_user
    returning spendable_coin_balance into v_balance;

    insert into public.blink_boost_campaigns_v2(
        user_id,target_type,target_id,objective,boost_power,
        audience_scope,target_university,duration_days,coin_budget,
        estimated_reach_low,estimated_reach_high,starts_at,ends_at
    ) values (
        v_user,
        upper(p_target_type),
        p_target_id,
        upper(p_objective),
        p_boost_power,
        upper(p_audience_scope),
        v_university,
        p_duration_days,
        v_cost,
        (v_quote->>'estimated_reach_low')::bigint,
        (v_quote->>'estimated_reach_high')::bigint,
        now(),
        now()+make_interval(days=>p_duration_days)
    )
    returning * into v_campaign;

    insert into public.blink_coin_transactions(
        user_id,kind,catalog_id,item_name,amount,balance_after,metadata
    ) values (
        v_user,
        'BOOST_CAMPAIGN_RESERVE',
        null,
        'Boost '||initcap(lower(v_campaign.target_type)),
        -v_cost,
        floor(v_balance)::bigint,
        jsonb_build_object(
            'campaign_id',v_campaign.id,
            'target_type',v_campaign.target_type,
            'target_id',v_campaign.target_id,
            'boost_power',v_campaign.boost_power,
            'objective',v_campaign.objective,
            'audience_scope',v_campaign.audience_scope,
            'duration_days',v_campaign.duration_days
        )
    );

    return jsonb_build_object(
        'success',true,
        'campaign_id',v_campaign.id,
        'reserved',v_cost,
        'charged',v_cost,
        'balance',floor(v_balance)::bigint,
        'campaign',to_jsonb(v_campaign),
        'quote',v_quote
    );
end;
$function$
;

-- Function: create_blink_boost_campaign_v2(p_target_type text, p_target_id uuid, p_boost_power integer, p_objective text, p_audience_scope text, p_duration_days integer, p_target_university text, p_request_id uuid)
CREATE OR REPLACE FUNCTION public.create_blink_boost_campaign_v2(p_target_type text, p_target_id uuid, p_boost_power integer, p_objective text, p_audience_scope text, p_duration_days integer, p_target_university text, p_request_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_user uuid := auth.uid();
  v_result jsonb;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  if p_request_id is null then raise exception 'REQUEST_ID_REQUIRED'; end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text||':BOOST:'||p_request_id::text));

  select i.result into v_result
  from private.blink_growth_request_idempotency i
  where i.user_id=v_user and i.scope='BOOST' and i.request_id=p_request_id;
  if v_result is not null then return v_result; end if;

  v_result := public.create_blink_boost_campaign(
    p_target_type,p_target_id,p_boost_power,p_objective,
    p_audience_scope,p_duration_days,p_target_university
  );

  insert into private.blink_growth_request_idempotency(user_id,scope,request_id,result)
  values(v_user,'BOOST',p_request_id,v_result)
  on conflict(user_id,scope,request_id) do update set result=excluded.result;

  return v_result;
end;
$function$
;

-- Function: create_broadcast(p_payload jsonb)
CREATE OR REPLACE FUNCTION public.create_broadcast(p_payload jsonb)
 RETURNS jsonb
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select public.admin_create_broadcast(p_payload) $function$
;

-- Function: delete_broadcast(p_id uuid)
CREATE OR REPLACE FUNCTION public.delete_broadcast(p_id uuid)
 RETURNS boolean
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select public.admin_delete_broadcast(p_id) $function$
;

-- Function: dismiss_broadcast(p_broadcast_id uuid)
CREATE OR REPLACE FUNCTION public.dismiss_broadcast(p_broadcast_id uuid)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ begin if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if; if not public.broadcast_rate_limit('dismiss',60,60) then raise exception 'RATE_LIMITED'; end if; insert into broadcast_dismissals(user_id,broadcast_id) select auth.uid(),id from broadcasts where id=p_broadcast_id and status='live' and dismissible on conflict do nothing; return found;end $function$
;

-- Function: duplicate_broadcast(p_id uuid)
CREATE OR REPLACE FUNCTION public.duplicate_broadcast(p_id uuid)
 RETURNS uuid
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select public.admin_duplicate_broadcast(p_id) $function$
;

-- Function: end_broadcast(p_id uuid)
CREATE OR REPLACE FUNCTION public.end_broadcast(p_id uuid)
 RETURNS jsonb
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select public.admin_end_broadcast(p_id) $function$
;

-- Function: get_active_broadcasts(p_surface text, p_app_version text)
CREATE OR REPLACE FUNCTION public.get_active_broadcasts(p_surface text DEFAULT 'in_app'::text, p_app_version text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare uid uuid:=auth.uid(); res jsonb;begin if uid is null then return '[]'::jsonb; end if; select coalesce(jsonb_agg(jsonb_build_object('broadcast',to_jsonb(b),'campaign_rule',to_jsonb(cr),'promo_rule',to_jsonb(pr),'dismissed',exists(select 1 from broadcast_dismissals d where d.user_id=uid and d.broadcast_id=b.id),'read',exists(select 1 from broadcast_reads r where r.user_id=uid and r.broadcast_id=b.id),'claim_state',(select jsonb_build_object('claims',count(*),'last_status',max(status::text)) from promo_claims pc where pc.user_id=uid and pc.broadcast_id=b.id)) order by b.priority desc,b.starts_at asc), '[]') into res from broadcasts b left join campaign_rules cr on cr.broadcast_id=b.id left join promo_rules pr on pr.broadcast_id=b.id where b.status='live' and b.deleted_at is null and (b.starts_at is null or b.starts_at<=now()) and (b.ends_at is null or b.ends_at>now()) and coalesce((b.channels->>p_surface)::boolean,false) and public.broadcast_audience_match(uid,b.audience,p_app_version) and not exists(select 1 from broadcast_dismissals d where d.user_id=uid and d.broadcast_id=b.id); return res;end $function$
;

-- Function: get_blink_live_campaigns(p_placement text, p_app_version integer)
CREATE OR REPLACE FUNCTION public.get_blink_live_campaigns(p_placement text, p_app_version integer DEFAULT 0)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
end; $function$
;

-- Function: get_blink_promoted_slots(p_surface text, p_limit integer)
CREATE OR REPLACE FUNCTION public.get_blink_promoted_slots(p_surface text DEFAULT 'HOME'::text, p_limit integer DEFAULT 3)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
    v_user uuid := auth.uid();
    v_surface text := upper(coalesce(p_surface,'HOME'));
    v_limit integer := greatest(1,least(coalesce(p_limit,3),10));
    v_items jsonb := '[]'::jsonb;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
    if v_surface not in ('HOME','SEARCH','DISCOVER','MISSIONS') then raise exception 'INVALID_BOOST_SURFACE'; end if;

    select coalesce(jsonb_agg(x.payload order by x.delivery_score desc,x.starts_at asc),'[]'::jsonb)
      into v_items
    from (
        select
            c.starts_at,
            (c.boost_power::numeric + least(25,extract(epoch from (now()-c.starts_at))/21600)) as delivery_score,
            jsonb_build_object(
                'campaign_id',c.id,
                'target_type',c.target_type,
                'target_id',c.target_id,
                'objective',c.objective,
                'boost_power',c.boost_power,
                'ad_label','Promoted',
                'surface',v_surface,
                'owner',jsonb_build_object(
                    'id',owner.id,
                    'username',owner.username,
                    'full_name',owner.full_name,
                    'avatar_url',owner.avatar_url,
                    'verification_badge',owner.verification_badge,
                    'university',owner.university
                ),
                'post',case when c.target_type in ('POST','REEL') then (
                    select to_jsonb(fp) from public.feed_posts fp where fp.id=c.target_id and fp.is_active=true
                ) else null end,
                'profile',case when c.target_type='PROFILE' then (
                    select jsonb_build_object(
                        'id',p.id,'username',p.username,'full_name',p.full_name,
                        'avatar_url',p.avatar_url,'verification_badge',p.verification_badge,
                        'university',p.university,'faculty',p.faculty,'department',p.department,
                        'follower_count',p.follower_count,'bio',p.bio
                    ) from public.profiles p where p.id=c.target_id
                ) else null end,
                'listing',case when c.target_type='LISTING' then (
                    select to_jsonb(m) from public.market_items m
                    where m.id=c.target_id and coalesce(m.is_sold,false)=false
                ) else null end
            ) as payload
        from public.blink_boost_campaigns_v2 c
        join public.profiles owner on owner.id=c.user_id
        where private.blink_boost_viewer_matches(c,v_user)
          and (
            (c.target_type in ('POST','REEL') and exists(
                select 1 from public.feed_posts fp where fp.id=c.target_id and fp.is_active=true
            ))
            or (c.target_type='PROFILE' and exists(
                select 1 from public.profiles p where p.id=c.target_id
            ))
            or (c.target_type='LISTING' and exists(
                select 1 from public.market_items m where m.id=c.target_id and coalesce(m.is_sold,false)=false
            ))
          )
        order by delivery_score desc,c.starts_at asc
        limit v_limit
    ) x;

    return jsonb_build_object('surface',v_surface,'items',v_items);
end;
$function$
;

-- Function: get_broadcast_blocking_state()
CREATE OR REPLACE FUNCTION public.get_broadcast_blocking_state()
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select jsonb_build_object('maintenance',maintenance_enabled,'message',maintenance_message,'min_app_version',min_app_version,'store_url',store_url) from broadcast_app_config where id=true $function$
;

-- Function: get_broadcast_inbox(p_tab text, p_cursor timestamp with time zone, p_limit integer)
CREATE OR REPLACE FUNCTION public.get_broadcast_inbox(p_tab text DEFAULT 'all'::text, p_cursor timestamp with time zone DEFAULT NULL::timestamp with time zone, p_limit integer DEFAULT 20)
 RETURNS jsonb
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select coalesce(jsonb_agg(to_jsonb(q) order by q.created_at desc),'[]') from (select b.id,b.type,b.title,b.body,b.image_url,b.cta_label,b.deep_link,b.priority,b.created_at,b.starts_at,b.ends_at,(r.broadcast_id is not null) is_read from broadcasts b left join broadcast_reads r on r.broadcast_id=b.id and r.user_id=auth.uid() where b.status='live' and b.deleted_at is null and (b.starts_at is null or b.starts_at<=now()) and (b.ends_at is null or b.ends_at>now()) and broadcast_audience_match(auth.uid(),b.audience,null) and (lower(p_tab)='all' or (lower(p_tab)='unread' and r.broadcast_id is null) or lower(p_tab)=lower(b.type::text)) and (p_cursor is null or b.created_at<p_cursor) order by b.created_at desc limit least(greatest(p_limit,1),50)) q $function$
;

-- Function: get_broadcast_prefs()
CREATE OR REPLACE FUNCTION public.get_broadcast_prefs()
 RETURNS jsonb
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select to_jsonb(p) from notification_prefs p where p.user_id=auth.uid() $function$
;

-- Function: get_campaign_leaderboard(p_broadcast_id uuid)
CREATE OR REPLACE FUNCTION public.get_campaign_leaderboard(p_broadcast_id uuid)
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select jsonb_build_object('top',(select jsonb_agg(to_jsonb(x)) from (select cp.user_id,cp.progress,rank() over(order by cp.progress desc,cp.joined_at asc) rank,p.name,p.handle,p.avatar_url from campaign_participants cp join profiles p on p.id=cp.user_id where cp.broadcast_id=p_broadcast_id order by cp.progress desc,cp.joined_at asc limit 20) x),'me',(select jsonb_build_object('rank',rank,'progress',progress) from (select cp.user_id,cp.progress,rank() over(order by cp.progress desc,cp.joined_at asc) rank from campaign_participants cp where cp.broadcast_id=p_broadcast_id) r where user_id=auth.uid())) $function$
;

-- Function: get_campaign_progress(p_broadcast_id uuid)
CREATE OR REPLACE FUNCTION public.get_campaign_progress(p_broadcast_id uuid)
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select jsonb_build_object('participant',to_jsonb(cp),'rule',to_jsonb(cr),'leaderboard_position',(select rank from (select user_id,rank() over(order by progress desc,joined_at asc) rank from campaign_participants where broadcast_id=p_broadcast_id) r where user_id=auth.uid())) from campaign_participants cp join campaign_rules cr on cr.broadcast_id=cp.broadcast_id where cp.broadcast_id=p_broadcast_id and cp.user_id=auth.uid() $function$
;

-- Function: get_market_promoted_slots(p_surface text, p_limit integer)
CREATE OR REPLACE FUNCTION public.get_market_promoted_slots(p_surface text DEFAULT 'MARKET'::text, p_limit integer DEFAULT 3)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'private', 'pg_temp'
AS $function$
DECLARE v_user uuid:=auth.uid();v_slots jsonb;
BEGIN
 IF v_user IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
 IF upper(coalesce(p_surface,'')) NOT IN ('MARKET','HOME')
 THEN RAISE EXCEPTION 'INVALID_MARKET_SURFACE'; END IF;
 SELECT coalesce(jsonb_agg(s.payload ORDER BY s.power DESC,s.created_at),'[]'::jsonb)
 INTO v_slots FROM (
  SELECT c.boost_power AS power,c.created_at,
   jsonb_build_object(
    'campaign_id',c.id,'target_type','LISTING','target_id',c.target_id,
    'surface',upper(p_surface),'ad_label','Sponsored','objective',c.objective,
    'boost_power',c.boost_power,
    'listing',jsonb_build_object(
      'id',m.id,'title',m.title,'price',m.price,'category',m.category,
      'condition',m.condition,'description',m.description,
      'image_url',m.image_url,'image_urls',m.image_urls,
      'seller_id',m.seller_id,'seller_name',m.seller_name,
      'seller_username',m.seller_username,'seller_avatar',m.seller_avatar,
      'seller_is_verified',true,'university',m.university,
      'location',m.location,'status',m.status,'is_sold',m.is_sold,
      'quantity',m.quantity,'video_url',m.video_url,'created_at',m.created_at)
   ) AS payload
  FROM public.blink_boost_campaigns_v2 c
  JOIN public.market_items m ON m.id=c.target_id
  WHERE c.target_type='LISTING' AND private.blink_boost_viewer_matches(c,v_user)
  ORDER BY c.boost_power DESC,c.created_at
  LIMIT greatest(1,least(coalesce(p_limit,3),5))
 ) s;
 RETURN jsonb_build_object('surface',upper(p_surface),'items',v_slots);
END;
$function$
;

-- Function: get_my_blink_boost_campaigns()
CREATE OR REPLACE FUNCTION public.get_my_blink_boost_campaigns()
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
    with me as (select auth.uid() uid),
    rows as (
        select
            c.*,
            (select count(*)::bigint from public.blink_boost_delivery_events_v2 e
             where e.campaign_id=c.id and e.event_type='IMPRESSION') promoted_impressions,
            (select count(*)::bigint from public.blink_boost_delivery_events_v2 e
             where e.campaign_id=c.id and e.event_type in ('CARD_OPEN','PROFILE_OPEN','LISTING_OPEN')) promoted_opens
        from public.blink_boost_campaigns_v2 c,me
        where c.user_id=me.uid
        order by c.created_at desc
        limit 100
    )
    select jsonb_build_object(
        'items',coalesce(jsonb_agg(to_jsonb(rows) order by rows.created_at desc),'[]'::jsonb)
    )
    from rows
    where (select uid from me) is not null;
$function$
;

-- Function: get_website_broadcasts()
CREATE OR REPLACE FUNCTION public.get_website_broadcasts()
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select coalesce(jsonb_agg(jsonb_build_object('id',b.id,'type',b.type,'title',b.title,'body',b.body,'image_url',b.image_url,'cta_label',b.cta_label,'deep_link',b.deep_link,'priority',b.priority,'starts_at',b.starts_at,'ends_at',b.ends_at) order by b.priority desc,b.starts_at),'[]') from broadcasts b where b.status='live' and b.deleted_at is null and coalesce((b.channels->>'website')::boolean,false) and (b.starts_at is null or b.starts_at<=now()) and (b.ends_at is null or b.ends_at>now()) and coalesce(b.audience->>'kind','everyone')='everyone' and not b.is_blocking $function$
;

-- Function: join_campaign(p_broadcast_id uuid)
CREATE OR REPLACE FUNCTION public.join_campaign(p_broadcast_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare uid uuid:=auth.uid();begin if uid is null then raise exception 'AUTH_REQUIRED'; end if; if not broadcast_rate_limit('join_campaign',10,60) then return jsonb_build_object('ok',false,'error','RATE_LIMITED'); end if; if not exists(select 1 from broadcasts b join campaign_rules cr on cr.broadcast_id=b.id where b.id=p_broadcast_id and b.type='CAMPAIGN' and b.status='live' and b.deleted_at is null and (b.starts_at is null or b.starts_at<=now()) and (b.ends_at is null or b.ends_at>now()) and broadcast_audience_match(uid,b.audience,null)) then return jsonb_build_object('ok',false,'error','NOT_ELIGIBLE'); end if; insert into campaign_participants(broadcast_id,user_id) values(p_broadcast_id,uid) on conflict do nothing; return jsonb_build_object('ok',true,'joined',true,'idempotent',not found);end $function$
;

-- Function: quote_blink_boost_campaign(p_target_type text, p_target_id uuid, p_boost_power integer, p_objective text, p_audience_scope text, p_duration_days integer, p_target_university text)
CREATE OR REPLACE FUNCTION public.quote_blink_boost_campaign(p_target_type text, p_target_id uuid, p_boost_power integer, p_objective text, p_audience_scope text, p_duration_days integer, p_target_university text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
    v_user uuid := auth.uid();
    v_scope text := upper(coalesce(p_audience_scope,''));
    v_university text;
    v_quote jsonb;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
    perform private.blink_boost_assert_owned_target(v_user,p_target_type,p_target_id);

    if v_scope='MY_UNIVERSITY' then
        select nullif(btrim(coalesce(p.university,'')),'') into v_university
        from public.profiles p where p.id=v_user;
        if v_university is null then raise exception 'UNIVERSITY_REQUIRED_FOR_BOOST'; end if;
    elsif v_scope='SELECTED_UNIVERSITY' then
        v_university := nullif(btrim(coalesce(p_target_university,'')),'');
        if v_university is null then raise exception 'BOOST_TARGET_UNIVERSITY_REQUIRED'; end if;
    elsif v_scope='ALL_CAMPUSES' then
        v_university := null;
    else
        raise exception 'INVALID_BOOST_AUDIENCE';
    end if;

    v_quote := private.blink_boost_quote(
        p_target_type,p_boost_power,p_objective,v_scope,p_duration_days
    );
    return v_quote || jsonb_build_object(
        'target_id',p_target_id,
        'target_university',v_university
    );
end;
$function$
;

-- Function: quote_blink_boost_campaign_v2(p_target_type text, p_target_id uuid, p_boost_power integer, p_objective text, p_audience_scope text, p_duration_days integer, p_target_university text)
CREATE OR REPLACE FUNCTION public.quote_blink_boost_campaign_v2(p_target_type text, p_target_id uuid, p_boost_power integer, p_objective text, p_audience_scope text, p_duration_days integer, p_target_university text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_user uuid := auth.uid();
  v_quote jsonb;
  v_scope text := upper(coalesce(p_audience_scope,''));
  v_university text;
  v_audience_count bigint := 0;
  v_balance bigint := 0;
  v_cost bigint := 0;
  v_low bigint := 0;
  v_high bigint := 0;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  v_quote := public.quote_blink_boost_campaign(
    p_target_type,p_target_id,p_boost_power,p_objective,
    p_audience_scope,p_duration_days,p_target_university
  );
  v_university := nullif(v_quote->>'target_university','');

  select coalesce(floor(b.spendable_coin_balance),0)::bigint
    into v_balance
  from public.user_balances b
  where b.user_id=v_user;
  v_balance := coalesce(v_balance,0);

  select count(*)::bigint into v_audience_count
  from public.profiles p
  where p.id<>v_user
    and (
      v_scope='ALL_CAMPUSES'
      or lower(coalesce(p.university,''))=lower(coalesce(v_university,''))
    )
    and not exists (
      select 1 from public.blocks b
      where (b.blocker_id=v_user and b.blocked_id=p.id)
         or (b.blocker_id=p.id and b.blocked_id=v_user)
    );

  v_cost := coalesce((v_quote->>'coin_cost')::bigint,0);
  v_low := coalesce((v_quote->>'estimated_reach_low')::bigint,0);
  v_high := coalesce((v_quote->>'estimated_reach_high')::bigint,0);

  return v_quote || jsonb_build_object(
    'audience_user_count',v_audience_count,
    'estimated_daily_reach_low',case when p_duration_days>0 then floor(v_low::numeric/p_duration_days)::bigint else v_low end,
    'estimated_daily_reach_high',case when p_duration_days>0 then ceil(v_high::numeric/p_duration_days)::bigint else v_high end,
    'balance',v_balance,
    'remaining_balance_after_reserve',v_balance-v_cost
  );
end;
$function$
;

-- Function: record_blink_live_campaign_event(p_id uuid, p_placement text, p_app_version integer, p_event text)
CREATE OR REPLACE FUNCTION public.record_blink_live_campaign_event(p_id uuid, p_placement text, p_app_version integer, p_event text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
end; $function$
;

-- Function: record_broadcast_campaign_event(p_goal broadcast_goal_type, p_user uuid, p_source text, p_source_id text, p_delta integer, p_event_at timestamp with time zone)
CREATE OR REPLACE FUNCTION public.record_broadcast_campaign_event(p_goal broadcast_goal_type, p_user uuid, p_source text, p_source_id text, p_delta integer, p_event_at timestamp with time zone)
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare c record; inserted_id uuid; total integer;begin if p_user is null or p_source_id is null or p_delta<=0 then return 0; end if; for c in select cp.broadcast_id,cp.joined_at,cr.target,b.starts_at,b.ends_at from campaign_participants cp join campaign_rules cr on cr.broadcast_id=cp.broadcast_id join broadcasts b on b.id=cp.broadcast_id where cp.user_id=p_user and cr.goal_type=p_goal and cp.completed_at is null and cp.joined_at<=p_event_at and (b.starts_at is null or b.starts_at<=p_event_at) and (b.ends_at is null or b.ends_at>=p_event_at) loop insert into campaign_progress_events(broadcast_id,user_id,source_type,source_id,delta,created_at) values(c.broadcast_id,p_user,p_source,p_source_id,p_delta,p_event_at) on conflict(broadcast_id,source_type,source_id) do nothing returning id into inserted_id; if inserted_id is not null then update campaign_participants set progress=progress+p_delta,completed_at=case when progress+p_delta>=c.target then coalesce(completed_at,now()) else completed_at end where broadcast_id=c.broadcast_id and user_id=p_user returning progress into total; if total>=c.target then perform broadcast_campaign_reward(c.broadcast_id,p_user,total); end if; end if; inserted_id:=null; end loop; return 1;end $function$
;

-- Function: register_broadcast_device_token(p_token text, p_platform text, p_app_version text)
CREATE OR REPLACE FUNCTION public.register_broadcast_device_token(p_token text, p_platform text, p_app_version text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare uid uuid:=auth.uid(); outid uuid;begin if uid is null then raise exception 'AUTH_REQUIRED'; end if; if length(p_token)<20 or p_platform not in ('android','ios','web') then raise exception 'INVALID_TOKEN'; end if; insert into device_tokens(user_id,token,platform,app_version,last_seen_at,disabled_at) values(uid,p_token,p_platform,p_app_version,now(),null) on conflict(token) do update set user_id=excluded.user_id,platform=excluded.platform,app_version=excluded.app_version,last_seen_at=now(),disabled_at=null returning id into outid; return outid;end $function$
;

-- Function: track_broadcast_click(p_broadcast_id uuid, p_surface text)
CREATE OR REPLACE FUNCTION public.track_broadcast_click(p_broadcast_id uuid, p_surface text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$begin if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if; if not broadcast_rate_limit('click',20,60) then return false; end if; insert into broadcast_clicks(user_id,broadcast_id,surface) select auth.uid(),b.id,left(coalesce(p_surface,'unknown'),50) from broadcasts b where b.id=p_broadcast_id and b.status='live' and (b.starts_at is null or b.starts_at<=now()) and (b.ends_at is null or b.ends_at>now()) on conflict do nothing; return found;end $function$
;

-- Function: track_broadcast_impression(p_broadcast_id uuid, p_surface text)
CREATE OR REPLACE FUNCTION public.track_broadcast_impression(p_broadcast_id uuid, p_surface text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$begin if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if; if not broadcast_rate_limit('impression',90,60) then return false; end if; insert into broadcast_impressions(user_id,broadcast_id,surface) select auth.uid(),b.id,left(coalesce(p_surface,'unknown'),50) from broadcasts b where b.id=p_broadcast_id and b.status='live' and (b.starts_at is null or b.starts_at<=now()) and (b.ends_at is null or b.ends_at>now()) on conflict do nothing; return found;end $function$
;

-- Function: update_broadcast(p_id uuid, p_version integer, p_patch jsonb)
CREATE OR REPLACE FUNCTION public.update_broadcast(p_id uuid, p_version integer, p_patch jsonb)
 RETURNS jsonb
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ select public.admin_update_broadcast(p_id,p_version,p_patch) $function$
;

-- Function: update_broadcast_prefs(p_patch jsonb)
CREATE OR REPLACE FUNCTION public.update_broadcast_prefs(p_patch jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ declare uid uuid:=auth.uid(); res jsonb;begin if uid is null then raise exception 'AUTH_REQUIRED'; end if; insert into notification_prefs(user_id) values(uid) on conflict do nothing; update notification_prefs set push_enabled=coalesce((p_patch->>'push_enabled')::boolean,push_enabled),announcements=coalesce((p_patch->>'announcements')::boolean,announcements),campaigns=coalesce((p_patch->>'campaigns')::boolean,campaigns),offers=coalesce((p_patch->>'offers')::boolean,offers),email=coalesce((p_patch->>'email')::boolean,email),quiet_start=coalesce((p_patch->>'quiet_start')::time,quiet_start),quiet_end=coalesce((p_patch->>'quiet_end')::time,quiet_end),timezone=coalesce(p_patch->>'timezone',timezone),updated_at=now() where user_id=uid returning to_jsonb(notification_prefs.*) into res; return res;end $function$
;
