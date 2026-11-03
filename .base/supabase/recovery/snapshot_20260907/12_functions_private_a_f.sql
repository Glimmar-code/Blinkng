-- Blink Supabase recovery snapshot: private functions A-F
-- Generated from live pg_get_functiondef output; no secret values detected.

CREATE OR REPLACE FUNCTION private.activate_blink_vip_pass(p_user uuid, p_inventory uuid, p_gifted_by uuid DEFAULT NULL::uuid)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v_base timestamptz; v_pass uuid; v_auto boolean:=false; begin
  select greatest(now(),coalesce(max(expires_at),now())) into v_base from public.blink_vip_passes where user_id=p_user and expires_at>now();
  select coalesce((select auto_renew from public.blink_vip_passes where user_id=p_user order by expires_at desc limit 1),false) into v_auto;
  insert into public.blink_vip_passes(user_id,inventory_id,starts_at,expires_at,auto_renew,gifted_by) values(p_user,p_inventory,v_base,v_base+interval '10 days',v_auto,p_gifted_by) returning id into v_pass;
  insert into public.blink_vip_benefit_balances(pass_id,user_id) values(v_pass,p_user);
  update public.profiles set blink_vip_until=v_base+interval '10 days' where id=p_user;
  return v_pass;
end $function$

CREATE OR REPLACE FUNCTION private.active_blink_admin_role(p_user_id uuid)
 RETURNS text
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
  select r.role from private.admin_roles r where r.user_id=p_user_id
    and (r.role='owner' or r.expires_at is null or r.expires_at > now()) limit 1;
$function$

CREATE OR REPLACE FUNCTION private.admin_assert_target_user(p_actor uuid, p_target uuid)
 RETURNS void
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$ declare v_scopes text[]; v_university text; begin
 if p_target is null or not exists(select 1 from public.profiles where id=p_target) then raise exception 'VALID_TARGET_USER_REQUIRED'; end if;
 if private.is_blink_owner_id(p_actor) then return; end if;
 select scope_universities into v_scopes from private.admin_roles where user_id=p_actor;
 if cardinality(coalesce(v_scopes,'{}'::text[]))=0 then return; end if;
 select university into v_university from public.profiles where id=p_target;
 if not coalesce(v_university,'')=any(v_scopes) then raise exception 'TARGET_OUTSIDE_ADMIN_UNIVERSITY_SCOPE' using errcode='42501'; end if;
end; $function$

CREATE OR REPLACE FUNCTION private.admin_change_coins(p_actor uuid, p_target uuid, p_amount bigint, p_type text, p_reason text DEFAULT NULL::text)
 RETURNS bigint
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v_balance bigint; v_used bigint := 0; begin
  perform private.admin_ensure_control(p_actor,p_target);
  if p_amount=0 then raise exception 'COIN_AMOUNT_CANNOT_BE_ZERO'; end if;
  if p_amount > 0 and not private.is_blink_owner_id(p_actor) then
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(p_actor::text)::bigint);
    select coalesce(sum(amount),0) into v_used from private.admin_coin_transactions where actor_id=p_actor and amount>0 and created_at > now() - interval '1 hour';
    if v_used + p_amount > 50 then raise exception 'NORMAL_ADMIN_HOURLY_COIN_LIMIT_EXCEEDED: % coins remaining in the current 60-minute window', greatest(50-v_used,0) using errcode='42501'; end if;
  end if;
  insert into public.game_profiles(user_id,coins) values(p_target,greatest(p_amount,0)) on conflict(user_id) do update set coins=greatest(0,public.game_profiles.coins+p_amount),updated_at=now() returning coins into v_balance;
  insert into private.admin_coin_transactions(actor_id,user_id,amount,transaction_type,reason) values(p_actor,p_target,p_amount,left(coalesce(p_type,'adjustment'),80),left(p_reason,500));
  return v_balance;
end;
$function$

CREATE OR REPLACE FUNCTION private.admin_config_bool(p_key text, p_default boolean)
 RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ select coalesce((select case when jsonb_typeof(c.value)='boolean' then (c.value #>> '{}')::boolean else p_default end from private.admin_system_config c where c.key=p_key),p_default); $function$

CREATE OR REPLACE FUNCTION private.admin_config_int(p_key text, p_default integer)
 RETURNS integer LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ select coalesce((select case when jsonb_typeof(c.value)='number' then (c.value #>> '{}')::integer else p_default end from private.admin_system_config c where c.key=p_key),p_default); $function$

CREATE OR REPLACE FUNCTION private.admin_dashboard_stats_impl()
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$
declare v_actor uuid; begin
 v_actor := private.require_blink_admin();
 return jsonb_build_object('users',(select count(*) from public.profiles),'verified',(select count(*) from public.profiles where upper(coalesce(verification_badge,'NONE')) in ('BLUE','GOLD')),'active_admins',(select count(*) from private.admin_roles where role='owner' or expires_at is null or expires_at > now()),'posts',(select count(*) from public.feed_posts where is_active=true),'owner_posts',(select count(*) from public.feed_posts fp where fp.is_active=true and private.is_blink_owner_id(fp.user_id)));
end;
$function$

CREATE OR REPLACE FUNCTION private.admin_ensure_control(p_actor uuid, p_target uuid)
 RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ begin perform private.admin_assert_target_user(p_actor,p_target); insert into private.admin_user_controls(user_id,updated_by) values(p_target,p_actor) on conflict(user_id) do update set updated_by=excluded.updated_by,updated_at=now(); end; $function$

-- NOTE: admin_execute_feature_impl is very large and is intentionally kept verbatim in normal migrations.
-- The recovery manifest in 18_function_manifest.sql records its exact live definition hash. During a clean
-- restore use the versioned migration that defines it, then verify the live hash against the manifest.

CREATE OR REPLACE FUNCTION private.admin_execute_feature_v2_impl(p_feature_id integer, p_entity_ref text DEFAULT NULL::text, p_text text DEFAULT NULL::text, p_amount bigint DEFAULT NULL::bigint, p_duration_hours integer DEFAULT NULL::integer, p_options jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$
declare v_feature private.admin_feature_registry_v2%rowtype; v_target uuid; v_options jsonb:=coalesce(p_options,'{}'::jsonb); v_link uuid; begin
 perform private.admin_feature_access_v2(p_feature_id); select * into v_feature from private.admin_feature_registry_v2 where feature_id=p_feature_id;
 if p_feature_id>200 then return private.admin_execute_pro_feature_impl(p_feature_id); end if;
 if v_feature.target_type<>'none' and nullif(trim(coalesce(p_entity_ref,'')),'') is not null then v_target:=private.admin_resolve_entity_ref_impl(p_entity_ref,v_feature.target_type); end if;
 if v_feature.target_type<>'none' and nullif(trim(coalesce(p_entity_ref,'')),'') is not null and v_target is null then raise exception 'ADMIN_TARGET_NOT_FOUND'; end if;
 if p_feature_id between 176 and 178 and nullif(trim(coalesce(v_options->>'link_ref','')),'') is not null then v_link:=private.admin_resolve_entity_ref_impl(v_options->>'link_ref',case p_feature_id when 176 then 'post' when 177 then 'user' else 'marketplace' end); if v_link is null then raise exception 'ADMIN_LINK_TARGET_NOT_FOUND'; end if; v_options:=v_options||jsonb_build_object('link_id',v_link); end if;
 return private.admin_execute_feature_impl(p_feature_id,v_target,p_text,p_amount,p_duration_hours,v_options);
end $function$

CREATE OR REPLACE FUNCTION private.admin_execute_feature_v3_impl(p_feature_id integer, p_entity_ref text DEFAULT NULL::text, p_text text DEFAULT NULL::text, p_amount bigint DEFAULT NULL::bigint, p_duration_hours integer DEFAULT NULL::integer, p_options jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$
declare v_result jsonb; v_section text; v_route text; begin
 perform private.admin_feature_access_v2(p_feature_id); select section_key,route_key into v_section,v_route from private.admin_feature_registry_v2 where feature_id=p_feature_id;
 if p_feature_id between 1 and 200 then v_result:=private.admin_execute_feature_v2_impl(p_feature_id,p_entity_ref,p_text,p_amount,p_duration_hours,coalesce(p_options,'{}'::jsonb)); elsif p_feature_id between 201 and 700 then v_result:=private.admin_execute_pro_feature_impl(p_feature_id); else raise exception 'FEATURE_ID_OUT_OF_RANGE'; end if;
 return coalesce(v_result,'{}'::jsonb) || jsonb_build_object('section_key',v_section,'route_key',v_route);
end $function$

CREATE OR REPLACE FUNCTION private.admin_execute_pro_feature_impl(p_feature_id integer)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$
declare v_actor uuid; v_idx integer; v_metric integer; v_window integer; v_since timestamptz; v_value numeric:=0; v_route text; v_section text; v_title text; begin
 v_actor:=private.admin_feature_access_v2(p_feature_id); if p_feature_id<201 or p_feature_id>700 then raise exception 'PRO_FEATURE_ID_OUT_OF_RANGE'; end if;
 v_idx:=p_feature_id-201; v_metric:=v_idx/10; v_window:=mod(v_idx,10);
 v_since:=now()-case v_window when 0 then interval '15 minutes' when 1 then interval '1 hour' when 2 then interval '6 hours' when 3 then interval '12 hours' when 4 then interval '1 day' when 5 then interval '3 days' when 6 then interval '7 days' when 7 then interval '14 days' when 8 then interval '30 days' else interval '90 days' end;
 select route_key,section_key,title into v_route,v_section,v_title from private.admin_feature_registry_v2 where feature_id=p_feature_id;
 case v_metric
  when 0 then select count(*)::numeric into v_value from public.profiles where coalesce(last_seen_at,last_seen)>v_since;
  when 1 then select count(*)::numeric into v_value from public.profiles where created_at>v_since;
  when 2 then select count(*)::numeric into v_value from public.profiles where updated_at>v_since;
  when 3 then select count(*)::numeric into v_value from public.user_devices where created_at>v_since;
  when 4 then select count(distinct nullif(trim(university),''))::numeric into v_value from public.profiles where coalesce(last_seen_at,last_seen)>v_since;
  when 5 then select count(*)::numeric into v_value from public.verification_requests where submitted_at>v_since;
  when 6 then select count(*)::numeric into v_value from private.admin_roles where granted_at>v_since;
  when 7 then select count(*)::numeric into v_value from private.admin_audit_log where created_at>v_since and (action ilike '%role%' or action ilike '%admin%' or action ilike '%permission%');
  when 8 then select count(*)::numeric into v_value from public.feed_posts where not is_reel and created_at>v_since;
  when 9 then select count(*)::numeric into v_value from public.feed_posts where is_reel and created_at>v_since;
  when 10 then select count(*)::numeric into v_value from public.stories where created_at>v_since;
  when 11 then select count(*)::numeric into v_value from public.comments where created_at>v_since;
  when 12 then select count(*)::numeric into v_value from public.messages where created_at>v_since;
  when 13 then select count(*)::numeric into v_value from public.conversations where created_at>v_since;
  when 14 then select count(*)::numeric into v_value from public.reports where created_at>v_since;
  when 15 then select count(*)::numeric into v_value from private.admin_audit_log where created_at>v_since and (action like 'feature_%' or action like 'post_%');
  when 16 then select count(*)::numeric into v_value from (select created_at from public.message_reports where created_at>v_since union all select created_at from public.conversation_reports where created_at>v_since) s;
  when 17 then select count(*)::numeric into v_value from private.admin_user_controls where updated_at>v_since;
  when 18 then select count(*)::numeric into v_value from public.notifications where created_at>v_since;
  when 19 then select count(*)::numeric into v_value from private.admin_announcements where created_at>v_since;
  when 20 then select count(*)::numeric into v_value from public.blink_coin_gifts where created_at>v_since;
  when 21 then select count(*)::numeric into v_value from public.point_transactions where created_at>v_since;
  when 22 then select count(*)::numeric into v_value from public.token_transactions where created_at>v_since;
  when 23 then select count(*)::numeric into v_value from public.market_items where created_at>v_since;
  when 24 then select count(*)::numeric into v_value from public.marketplace_orders where created_at>v_since;
  when 25 then select count(*)::numeric into v_value from public.connect_listings where created_at>v_since;
  when 26 then select count(*)::numeric into v_value from public.housing_requests where created_at>v_since;
  when 27 then select count(*)::numeric into v_value from public.mentor_requests where created_at>v_since;
  when 28 then select count(*)::numeric into v_value from public.reading_mate_requests where created_at>v_since;
  when 29 then select count(*)::numeric into v_value from public.study_circles where created_at>v_since;
  when 30 then select count(*)::numeric into v_value from public.game_sessions where started_at>v_since;
  when 31 then select count(*)::numeric into v_value from public.game_rewards where created_at>v_since;
  when 32 then select count(*)::numeric into v_value from public.recommendation_events where created_at>v_since;
  when 33 then select count(*)::numeric into v_value from private.admin_system_config where updated_at>v_since;
  when 34 then select count(*)::numeric into v_value from public.message_push_dispatches where updated_at>v_since and status='failed';
  when 35 then select count(*)::numeric into v_value from private.admin_audit_log where created_at>v_since;
  when 36 then select count(*)::numeric into v_value from public.follows where created_at>v_since;
  when 37 then select count(*)::numeric into v_value from public.profile_views where created_at>v_since;
  when 38 then select count(*)::numeric into v_value from public.post_views where created_at>v_since;
  when 39 then select count(*)::numeric into v_value from public.polls where created_at>v_since;
  when 40 then select count(*)::numeric into v_value from public.poll_votes where created_at>v_since;
  when 41 then select count(*)::numeric into v_value from public.scheduled_feed_posts where created_at>v_since;
  when 42 then select count(*)::numeric into v_value from public.marketplace_inquiries where created_at>v_since;
  when 43 then select count(*)::numeric into v_value from public.housing_request_applications where created_at>v_since;
  when 44 then select count(*)::numeric into v_value from public.mentor_profiles where created_at>v_since;
  when 45 then select count(*)::numeric into v_value from public.reading_mate_profiles where created_at>v_since;
  when 46 then select count(*)::numeric into v_value from public.study_circle_requests where created_at>v_since;
  when 47 then select count(*)::numeric into v_value from public.game_challenges where created_at>v_since;
  when 48 then select count(*)::numeric into v_value from public.verification_payments where created_at>v_since;
  when 49 then select count(*)::numeric into v_value from public.blocks where created_at>v_since;
  else raise exception 'PRO_METRIC_NOT_IMPLEMENTED';
 end case;
 return jsonb_build_object('feature_id',p_feature_id,'ok',true,'route_key',v_route,'section_key',v_section,'title',v_title,'result',jsonb_build_object('value',coalesce(v_value,0),'window_start',v_since,'window_end',now()));
end $function$

CREATE OR REPLACE FUNCTION private.admin_feature_access(p_feature_id integer)
 RETURNS uuid LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_uid uuid:=auth.uid(); v_feature private.admin_feature_catalog%rowtype; v_role private.admin_roles%rowtype; begin
 if v_uid is null then raise exception 'AUTH_REQUIRED' using errcode='42501'; end if; select * into v_feature from private.admin_feature_catalog where feature_id=p_feature_id and enabled; if not found then raise exception 'ADMIN_FEATURE_NOT_AVAILABLE'; end if; select * into v_role from private.admin_roles where user_id=v_uid; if not found then raise exception 'ADMIN_REQUIRED' using errcode='42501'; end if; if v_role.role='owner' then return v_uid; end if; if v_role.role<>'admin' or (v_role.expires_at is not null and v_role.expires_at<=now()) or (v_role.suspended_until is not null and v_role.suspended_until>now()) then raise exception 'ADMIN_ACCESS_EXPIRED_OR_SUSPENDED' using errcode='42501'; end if; if v_feature.owner_only then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if; if not ('*'=any(v_role.permissions) or v_feature.category=any(v_role.permissions)) then raise exception 'ADMIN_PERMISSION_REQUIRED:%',v_feature.category using errcode='42501'; end if; return v_uid; end; $function$

CREATE OR REPLACE FUNCTION private.admin_feature_access_v2(p_feature_id integer)
 RETURNS uuid LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_uid uuid:=auth.uid(); v_feature private.admin_feature_registry_v2%rowtype; v_role private.admin_roles%rowtype; begin
 if v_uid is null then raise exception 'AUTH_REQUIRED' using errcode='42501'; end if; select * into v_feature from private.admin_feature_registry_v2 where feature_id=p_feature_id and enabled; if not found then raise exception 'ADMIN_FEATURE_NOT_AVAILABLE'; end if; select * into v_role from private.admin_roles where user_id=v_uid; if not found then raise exception 'ADMIN_REQUIRED' using errcode='42501'; end if; if v_role.role='owner' then return v_uid; end if; if v_role.role<>'admin' or (v_role.expires_at is not null and v_role.expires_at<=now()) or (v_role.suspended_until is not null and v_role.suspended_until>now()) then raise exception 'ADMIN_ACCESS_EXPIRED_OR_SUSPENDED' using errcode='42501'; end if; if v_feature.owner_only then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if; if not ('*'=any(v_role.permissions) or v_feature.category=any(v_role.permissions)) then raise exception 'ADMIN_PERMISSION_REQUIRED:%',v_feature.category using errcode='42501'; end if; return v_uid; end $function$

CREATE OR REPLACE FUNCTION private.admin_get_capability_impl()
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_uid uuid := auth.uid(); v_role text; v_expires timestamptz; begin if v_uid is null then return jsonb_build_object('is_admin',false,'is_owner',false,'role','none','expires_at',null); end if; select r.role,r.expires_at into v_role,v_expires from private.admin_roles r where r.user_id=v_uid and (r.role='owner' or r.expires_at is null or r.expires_at>now()) limit 1; return jsonb_build_object('is_admin',v_role is not null,'is_owner',coalesce(v_role='owner',false),'role',coalesce(v_role,'none'),'expires_at',v_expires); end; $function$

CREATE OR REPLACE FUNCTION private.admin_get_universities_impl()
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_actor uuid; begin v_actor:=private.require_blink_admin(); return coalesce((select jsonb_agg(u order by u) from (select distinct trim(university) u from public.profiles where nullif(trim(university),'') is not null)s),'[]'::jsonb); end; $function$

CREATE OR REPLACE FUNCTION private.admin_global_search_v2_impl(p_query text)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ begin perform private.require_blink_admin(); return jsonb_build_object('users',private.admin_search_users_v2_impl(p_query,8),'posts',private.admin_search_posts_v2_impl(p_query,8),'universities',private.admin_search_universities_v2_impl(p_query,12)); end $function$

CREATE OR REPLACE FUNCTION private.admin_grant_coins_impl(p_user_id uuid, p_amount bigint, p_reason text DEFAULT 'Admin grant'::text)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_actor uuid; v_owner uuid; v_balance bigint; begin v_actor:=private.require_blink_admin(); if p_user_id is null or p_amount is null or p_amount<1 or p_amount>1000000 then raise exception 'INVALID_COIN_GRANT' using errcode='22023'; end if; if not exists(select 1 from public.profiles where id=p_user_id) then raise exception 'PROFILE_NOT_FOUND' using errcode='P0002'; end if; v_balance:=private.admin_change_coins(v_actor,p_user_id,p_amount,'grant',coalesce(nullif(trim(p_reason),''),'Admin grant')); select user_id into v_owner from private.admin_roles where role='owner' limit 1; insert into public.activities(recipient_id,actor_id,activity_type,entity_type,message,is_read) values(p_user_id,v_owner,'admin_coin_grant','system','Blink • You received '||p_amount||' Blink Coins.',false); insert into public.notifications(user_id,actor_id,type,text,sub_text,is_read) values(p_user_id,v_owner,'system'::public.notification_type_enum,'You received '||p_amount||' Blink Coins.',coalesce(nullif(trim(p_reason),''),'Admin grant'),false); insert into private.admin_audit_log(actor_id,action,target_user_id,details) values(v_actor,'grant_coins',p_user_id,jsonb_build_object('amount',p_amount,'reason',coalesce(p_reason,''))); return jsonb_build_object('ok',true,'balance',v_balance); end; $function$

CREATE OR REPLACE FUNCTION private.admin_grant_role_impl(p_user_id uuid, p_duration_hours integer DEFAULT 168)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_owner uuid; v_expires timestamptz; begin v_owner:=private.require_blink_owner(); if p_user_id is null or private.is_blink_owner_id(p_user_id) then raise exception 'INVALID_ADMIN_TARGET' using errcode='22023'; end if; if p_duration_hours is null or p_duration_hours<1 or p_duration_hours>8760 then raise exception 'INVALID_DURATION' using errcode='22023'; end if; if not exists(select 1 from public.profiles where id=p_user_id) then raise exception 'PROFILE_NOT_FOUND' using errcode='P0002'; end if; v_expires:=now()+make_interval(hours=>p_duration_hours); insert into private.admin_roles(user_id,role,granted_by,granted_at,expires_at) values(p_user_id,'admin',v_owner,now(),v_expires) on conflict(user_id) do update set role='admin',granted_by=v_owner,granted_at=now(),expires_at=v_expires; insert into public.activities(recipient_id,actor_id,activity_type,entity_type,message,is_read) values(p_user_id,v_owner,'admin_role','system','Blink • Admin access granted temporarily.',false); insert into private.admin_audit_log(actor_id,action,target_user_id,details) values(v_owner,'grant_admin',p_user_id,jsonb_build_object('expires_at',v_expires)); return jsonb_build_object('ok',true,'expires_at',v_expires); end; $function$

CREATE OR REPLACE FUNCTION private.admin_history_v2_impl(p_limit integer DEFAULT 100, p_offset integer DEFAULT 0)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_actor uuid; begin v_actor:=private.require_blink_admin(); return coalesce((select jsonb_agg(to_jsonb(q) order by q.created_at desc) from (select a.id,a.actor_id,coalesce(ap.username,'Admin') actor_username,a.action,a.target_user_id,tp.username target_username,a.target_post_id,a.details,a.created_at,(r.action_id is not null) as reversed,r.reversed_at,r.note as reversal_note,case when r.action_id is not null then false when a.action in ('grant_coins','grant_admin','post_hide','post_restore','post_pin','post_unpin','post_promote','post_unpromote') then true when a.action='set_verification' and upper(coalesce(a.details->>'badge','NONE'))<>'NONE' then true when a.action ~ '^feature_(21|22|23|26|27|28|29|30|31|32|40|42|43|48|51|53|58|59|60|61|62|63|64|81|85|86|87|99|100|101|102|106|107|115|117|118|119|120|127|128|129|130|131|132|135|136|137|142|143|144|145|146|154|155|170|197|198|200)$' then true else false end as can_revert from private.admin_audit_log a left join private.admin_action_reversals r on r.action_id=a.id left join public.profiles ap on ap.id=a.actor_id left join public.profiles tp on tp.id=a.target_user_id order by a.created_at desc limit greatest(1,least(coalesce(p_limit,100),250)) offset greatest(coalesce(p_offset,0),0))q),'[]'::jsonb); end $function$

CREATE OR REPLACE FUNCTION private.admin_list_features_impl()
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_uid uuid; begin v_uid:=private.require_blink_admin(); return coalesce((select jsonb_agg(jsonb_build_object('feature_id',f.feature_id,'title',f.title,'category',f.category,'owner_only',f.owner_only,'enabled',f.enabled) order by f.feature_id) from private.admin_feature_catalog f where f.enabled and (not f.owner_only or private.is_blink_owner_id(v_uid))),'[]'::jsonb); end; $function$

CREATE OR REPLACE FUNCTION private.admin_list_features_v2_impl()
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_uid uuid; v_role private.admin_roles%rowtype; begin v_uid:=private.require_blink_admin(); select * into v_role from private.admin_roles where user_id=v_uid; return coalesce((select jsonb_agg(jsonb_build_object('feature_id',f.feature_id,'title',f.title,'category',f.category,'module',f.module,'route_key',f.route_key,'target_type',f.target_type,'input_kind',f.input_kind,'owner_only',f.owner_only,'enabled',f.enabled,'reversible',f.reversible,'description',f.description) order by f.feature_id) from private.admin_feature_registry_v2 f where f.enabled and (v_role.role='owner' or (not f.owner_only and ('*'=any(v_role.permissions) or f.category=any(v_role.permissions))))),'[]'::jsonb); end $function$

CREATE OR REPLACE FUNCTION private.admin_list_features_v3_impl(p_section_key text DEFAULT NULL::text, p_query text DEFAULT ''::text)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_actor uuid; v_section text:=nullif(trim(coalesce(p_section_key,'')),''); v_q text:=trim(coalesce(p_query,'')); begin v_actor:=private.require_blink_admin(); return coalesce((select jsonb_agg(jsonb_build_object('feature_id',f.feature_id,'title',f.title,'category',f.category,'module',f.module,'section_key',f.section_key,'route_key',f.route_key,'target_type',f.target_type,'input_kind',f.input_kind,'owner_only',f.owner_only,'enabled',f.enabled,'reversible',f.reversible,'description',f.description,'permission_key',coalesce(f.permission_key,''),'risk_level',f.risk_level,'confirmation_kind',f.confirmation_kind) order by f.feature_id) from private.admin_feature_registry_v2 f where f.enabled and (v_section is null or f.section_key=v_section) and (v_q='' or f.title ilike '%'||v_q||'%' or f.route_key ilike '%'||v_q||'%' or f.description ilike '%'||v_q||'%')),'[]'::jsonb); end $function$

CREATE OR REPLACE FUNCTION private.admin_list_sections_v3_impl()
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_actor uuid; begin v_actor:=private.require_blink_admin(); return coalesce((select jsonb_agg(jsonb_build_object('section_key',s.section_key,'title',s.title,'description',s.description,'sort_order',s.sort_order,'show_in_sidebar',s.show_in_sidebar,'is_top_action',s.is_top_action,'enabled',s.enabled,'feature_count',(select count(*) from private.admin_feature_registry_v2 f where f.section_key=s.section_key and f.enabled)) order by s.sort_order) from private.admin_sections_v3 s where s.enabled),'[]'::jsonb); end $function$

CREATE OR REPLACE FUNCTION private.admin_log_action(p_actor uuid, p_action text, p_target_user uuid DEFAULT NULL::uuid, p_target_post uuid DEFAULT NULL::uuid, p_details jsonb DEFAULT '{}'::jsonb)
 RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ begin insert into private.admin_audit_log(actor_id,action,target_user_id,target_post_id,details) values(p_actor,left(p_action,200),p_target_user,p_target_post,coalesce(p_details,'{}'::jsonb)); end; $function$

CREATE OR REPLACE FUNCTION private.admin_post_action_impl(p_post_id uuid, p_action text, p_weight numeric DEFAULT 12)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_actor uuid; v_action text:=lower(trim(coalesce(p_action,''))); begin v_actor:=private.require_blink_admin(); if p_post_id is null or v_action not in ('hide','restore','pin','unpin','promote','unpromote') then raise exception 'INVALID_POST_ACTION' using errcode='22023'; end if; if v_action='hide' then update public.feed_posts set is_active=false,updated_at=now() where id=p_post_id; elsif v_action='restore' then update public.feed_posts set is_active=true,is_flagged=false,updated_at=now() where id=p_post_id; elsif v_action='pin' then update public.feed_posts set is_pinned=true,visibility_weight=greatest(coalesce(visibility_weight,1),p_weight),updated_at=now() where id=p_post_id; elsif v_action='unpin' then update public.feed_posts set is_pinned=false,updated_at=now() where id=p_post_id; elsif v_action='promote' then update public.feed_posts set is_sponsored=true,ad_label='Sponsored',visibility_weight=greatest(coalesce(visibility_weight,1),greatest(1,p_weight)),updated_at=now() where id=p_post_id; elsif v_action='unpromote' then update public.feed_posts set is_sponsored=false,ad_label=null,ad_cta=null,visibility_weight=1,updated_at=now() where id=p_post_id; end if; if not found then raise exception 'POST_NOT_FOUND' using errcode='P0002'; end if; insert into private.admin_audit_log(actor_id,action,target_post_id,details) values(v_actor,'post_'||v_action,p_post_id,jsonb_build_object('weight',p_weight)); return jsonb_build_object('ok',true,'action',v_action); end; $function$

CREATE OR REPLACE FUNCTION private.admin_resolve_entity_ref_impl(p_ref text, p_entity_type text)
 RETURNS uuid LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_actor uuid; v_q text:=trim(coalesce(p_ref,'')); v_type text:=lower(trim(coalesce(p_entity_type,''))); v_uuid_text text; v_id uuid; begin v_actor:=private.require_blink_admin(); if v_q='' then return null; end if; v_uuid_text:=substring(v_q from '([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})'); if v_uuid_text is not null then v_id:=v_uuid_text::uuid; end if; if v_type in ('user','admin','profile') then if v_id is not null and exists(select 1 from public.profiles where id=v_id) then return v_id; end if; select id into v_id from public.profiles where lower(username)=lower(ltrim(v_q,'@')) or lower(coalesce(email,''))=lower(v_q) order by created_at desc limit 1; if v_id is null then select id into v_id from public.profiles where username ilike '%'||ltrim(v_q,'@')||'%' or full_name ilike '%'||v_q||'%' or coalesce(email,'') ilike '%'||v_q||'%' order by created_at desc limit 1; end if; elsif v_type in ('post','reel') then if v_id is not null and exists(select 1 from public.feed_posts where id=v_id and (v_type<>'reel' or is_reel)) then return v_id; end if; select id into v_id from public.feed_posts where (v_type<>'reel' or is_reel) and (coalesce(text,'') ilike '%'||v_q||'%' or coalesce(caption,'') ilike '%'||v_q||'%') order by created_at desc limit 1; elsif v_type='comment' then if v_id is not null and exists(select 1 from public.comments where id=v_id) then return v_id; end if; select id into v_id from public.comments where content ilike '%'||v_q||'%' order by created_at desc limit 1; elsif v_type='report' then if v_id is not null and exists(select 1 from public.reports where id=v_id) then return v_id; end if; select id into v_id from public.reports where reason ilike '%'||v_q||'%' order by created_at desc limit 1; elsif v_type='campaign' then if v_id is not null and exists(select 1 from private.admin_notification_campaigns where id=v_id) then return v_id; end if; select id into v_id from private.admin_notification_campaigns where title ilike '%'||v_q||'%' or message ilike '%'||v_q||'%' order by created_at desc limit 1; elsif v_type='verification_request' then if v_id is not null and exists(select 1 from public.verification_requests where id=v_id) then return v_id; end if; select vr.id into v_id from public.verification_requests vr join public.profiles p on p.id=vr.user_id where p.username ilike '%'||ltrim(v_q,'@')||'%' or p.full_name ilike '%'||v_q||'%' order by vr.submitted_at desc limit 1; else return v_id; end if; return v_id; end $function$

-- admin_revert_action_v2_impl is another large function preserved verbatim in migrations and verified by the manifest.

CREATE OR REPLACE FUNCTION private.admin_revoke_role_impl(p_user_id uuid)
 RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_owner uuid; begin v_owner:=private.require_blink_owner(); if private.is_blink_owner_id(p_user_id) then raise exception 'BLINK_OWNER_IS_IMMUTABLE' using errcode='42501'; end if; delete from private.admin_roles where user_id=p_user_id and role='admin'; insert into private.admin_audit_log(actor_id,action,target_user_id) values(v_owner,'revoke_admin',p_user_id); return true; end; $function$

CREATE OR REPLACE FUNCTION private.admin_search_posts_v2_impl(p_query text DEFAULT ''::text, p_limit integer DEFAULT 40)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_actor uuid; v_q text:=trim(coalesce(p_query,'')); v_uuid_text text; v_uuid uuid; begin v_actor:=private.require_blink_admin(); v_uuid_text:=substring(v_q from '([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})'); if v_uuid_text is not null then v_uuid:=v_uuid_text::uuid; end if; return coalesce((select jsonb_agg(to_jsonb(q) order by q.created_at desc) from (select fp.id,fp.user_id,p.username,p.full_name,p.university,fp.caption,fp.text,fp.is_reel,fp.is_active,fp.is_flagged,fp.is_pinned,fp.is_sponsored,fp.like_count,fp.comment_count,fp.share_count,fp.view_count,fp.created_at from public.feed_posts fp join public.profiles p on p.id=fp.user_id where v_q='' or (v_uuid is not null and fp.id=v_uuid) or coalesce(fp.caption,'') ilike '%'||v_q||'%' or coalesce(fp.text,'') ilike '%'||v_q||'%' or p.username ilike '%'||ltrim(v_q,'@')||'%' or exists(select 1 from unnest(coalesce(fp.hashtags,'{}'::text[]))h where h ilike '%'||replace(v_q,'#','')||'%') order by case when v_uuid is not null and fp.id=v_uuid then 0 else 1 end,fp.created_at desc limit greatest(1,least(coalesce(p_limit,40),100)))q),'[]'::jsonb); end $function$

CREATE OR REPLACE FUNCTION private.admin_search_universities_v2_impl(p_query text DEFAULT ''::text, p_limit integer DEFAULT 100)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_actor uuid; v_q text:=trim(coalesce(p_query,'')); begin v_actor:=private.require_blink_admin(); return coalesce((select jsonb_agg(u order by u) from (select distinct trim(university)u from public.profiles where nullif(trim(university),'') is not null and (v_q='' or university ilike '%'||v_q||'%') order by 1 limit greatest(1,least(coalesce(p_limit,100),250)))s),'[]'::jsonb); end $function$

CREATE OR REPLACE FUNCTION private.admin_search_universities_v3_impl(p_query text DEFAULT ''::text, p_limit integer DEFAULT 100)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_actor uuid; v_q text:=trim(coalesce(p_query,'')); begin v_actor:=private.require_blink_admin(); return coalesce((select jsonb_agg(name order by name) from (select distinct name from (select c.name from private.admin_university_catalog c where c.active and (v_q='' or c.name ilike '%'||v_q||'%') union select trim(p.university) from public.profiles p where nullif(trim(p.university),'') is not null and (v_q='' or p.university ilike '%'||v_q||'%'))u(name) order by name limit greatest(1,least(coalesce(p_limit,100),300)))q),'[]'::jsonb); end $function$

CREATE OR REPLACE FUNCTION private.admin_search_users_impl(p_query text DEFAULT ''::text, p_limit integer DEFAULT 40)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_actor uuid; v_q text:=lower(trim(coalesce(p_query,''))); begin v_actor:=private.require_blink_admin(); return coalesce((select jsonb_agg(x order by lower(x->>'username')) from (select jsonb_build_object('id',p.id,'username',p.username,'full_name',p.full_name,'avatar_url',p.avatar_url,'university',coalesce(p.university,''),'verification_badge',coalesce(p.verification_badge,'NONE'),'verification_expires_at',p.verification_expires_at,'coins',coalesce(gp.coins,0),'admin_role',coalesce(private.active_blink_admin_role(p.id),'none'),'admin_expires_at',(select r.expires_at from private.admin_roles r where r.user_id=p.id limit 1))as x from public.profiles p left join public.game_profiles gp on gp.user_id=p.id where v_q='' or lower(p.username) like '%'||v_q||'%' or lower(p.full_name) like '%'||v_q||'%' or lower(coalesce(p.university,'')) like '%'||v_q||'%' order by p.created_at desc limit greatest(1,least(coalesce(p_limit,40),100)))q),'[]'::jsonb); end; $function$

CREATE OR REPLACE FUNCTION private.admin_search_users_v2_impl(p_query text DEFAULT ''::text, p_limit integer DEFAULT 40)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_actor uuid; v_q text:=lower(trim(coalesce(p_query,''))); v_uuid_text text; v_uuid uuid; begin v_actor:=private.require_blink_admin(); v_uuid_text:=substring(v_q from '([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})'); if v_uuid_text is not null then v_uuid:=v_uuid_text::uuid; end if; return coalesce((select jsonb_agg(x order by lower(x->>'username')) from (select jsonb_build_object('id',p.id,'username',p.username,'full_name',p.full_name,'email',p.email,'avatar_url',p.avatar_url,'university',coalesce(p.university,''),'faculty',coalesce(p.faculty,''),'department',coalesce(p.department,''),'verification_badge',coalesce(p.verification_badge,'NONE'),'verification_expires_at',p.verification_expires_at,'coins',coalesce(gp.coins,0),'admin_role',coalesce(private.active_blink_admin_role(p.id),'none'),'admin_expires_at',(select r.expires_at from private.admin_roles r where r.user_id=p.id limit 1),'created_at',p.created_at,'last_seen',coalesce(p.last_seen_at,p.last_seen))x from public.profiles p left join public.game_profiles gp on gp.user_id=p.id where v_q='' or (v_uuid is not null and p.id=v_uuid) or lower(coalesce(p.username,'')) like '%'||ltrim(v_q,'@')||'%' or lower(coalesce(p.full_name,'')) like '%'||v_q||'%' or lower(coalesce(p.email,'')) like '%'||v_q||'%' or lower(coalesce(p.university,'')) like '%'||v_q||'%' order by case when lower(coalesce(p.username,''))=ltrim(v_q,'@') then 0 when lower(coalesce(p.email,''))=v_q then 1 else 2 end,p.created_at desc limit greatest(1,least(coalesce(p_limit,40),100)))q),'[]'::jsonb); end $function$

CREATE OR REPLACE FUNCTION private.admin_send_announcement_impl(p_message text, p_target_user_id uuid DEFAULT NULL::uuid, p_target_university text DEFAULT NULL::text, p_verification_filter text DEFAULT 'all'::text)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_actor uuid; v_owner uuid; v_message text:=trim(coalesce(p_message,'')); v_filter text:=lower(trim(coalesce(p_verification_filter,'all'))); v_count integer:=0; v_announcement uuid; begin v_actor:=private.require_blink_admin(); if length(v_message)<1 or length(v_message)>2000 then raise exception 'INVALID_MESSAGE' using errcode='22023'; end if; if v_filter not in ('all','blue','gold') then raise exception 'INVALID_VERIFICATION_FILTER' using errcode='22023'; end if; select user_id into v_owner from private.admin_roles where role='owner' limit 1; insert into private.admin_announcements(sender_id,message,target_user_id,target_university,verification_filter) values(v_actor,v_message,p_target_user_id,nullif(trim(coalesce(p_target_university,'')),''),v_filter) returning id into v_announcement; with recipients as (select p.id from public.profiles p where (p_target_user_id is null or p.id=p_target_user_id) and (p_target_user_id is not null or p_target_university is null or trim(p_target_university)='' or lower(trim(p_target_university))='all universities' or lower(coalesce(p.university,''))=lower(trim(p_target_university))) and (p_target_user_id is not null or v_filter='all' or upper(coalesce(p.verification_badge,'NONE'))=upper(v_filter))),ins as (insert into public.activities(recipient_id,actor_id,activity_type,entity_type,message,is_read) select r.id,v_owner,'admin_announcement','system','Blink • '||v_message,false from recipients r returning recipient_id) select count(*) into v_count from ins; insert into public.notifications(user_id,actor_id,type,text,sub_text,is_read) select p.id,v_owner,'system'::public.notification_type_enum,v_message,'Blink',false from public.profiles p where (p_target_user_id is null or p.id=p_target_user_id) and (p_target_user_id is not null or p_target_university is null or trim(p_target_university)='' or lower(trim(p_target_university))='all universities' or lower(coalesce(p.university,''))=lower(trim(p_target_university))) and (p_target_user_id is not null or v_filter='all' or upper(coalesce(p.verification_badge,'NONE'))=upper(v_filter)); update private.admin_announcements set delivered_count=v_count where id=v_announcement; insert into private.admin_audit_log(actor_id,action,details) values(v_actor,'send_announcement',jsonb_build_object('announcement_id',v_announcement,'delivered',v_count,'university',p_target_university,'verification_filter',v_filter)); return jsonb_build_object('ok',true,'delivered',v_count,'announcement_id',v_announcement); end; $function$

CREATE OR REPLACE FUNCTION private.admin_send_system_notification(p_target uuid, p_title text, p_message text, p_post_id uuid DEFAULT NULL::uuid)
 RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_owner uuid:=private.blink_owner_id(); v_id uuid; begin if v_owner is null then raise exception 'BLINK_OWNER_NOT_CONFIGURED'; end if; insert into public.notifications(user_id,actor_id,type,post_id,text,sub_text,is_read) values(p_target,v_owner,'system'::public.notification_type_enum,p_post_id,left(coalesce(p_title,'Blink'),120),left(coalesce(p_message,''),2000),false) returning id into v_id; insert into public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message,is_read) values(p_target,v_owner,'system','notification',v_id,left('Blink • '||coalesce(p_message,''),2000),false); return v_id; end; $function$

CREATE OR REPLACE FUNCTION private.admin_set_verification_impl(p_user_id uuid, p_badge text, p_duration_hours integer DEFAULT 720)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_actor uuid; v_owner uuid; v_badge text:=upper(trim(coalesce(p_badge,''))); v_hours integer; v_expires timestamptz; begin v_actor:=private.require_blink_admin(); if p_user_id is null or v_badge not in ('NONE','BLUE','GOLD') then raise exception 'INVALID_VERIFICATION' using errcode='22023'; end if; if v_badge='NONE' then v_expires:=null; update public.profiles set verification_badge='NONE',verification_tier='None'::public.verification_tier_enum,is_verified=false,verified_at=null,verification_expires_at=null,updated_at=now() where id=p_user_id; else if p_duration_hours is null or p_duration_hours<1 or p_duration_hours>8760 then raise exception 'INVALID_DURATION' using errcode='22023'; end if; v_hours:=p_duration_hours; if v_badge='GOLD' and not private.is_blink_owner_id(v_actor) then raise exception 'ONLY_BLINK_OWNER_CAN_GRANT_GOLD_VERIFICATION' using errcode='42501'; end if; if v_badge='BLUE' and v_hours>48 and not private.is_blink_owner_id(v_actor) then raise exception 'ONLY_BLINK_OWNER_CAN_GRANT_BLUE_VERIFICATION_OVER_48_HOURS' using errcode='42501'; end if; v_expires:=now()+make_interval(hours=>v_hours); update public.profiles set verification_badge=v_badge,verification_tier=(case when v_badge='GOLD' then 'Gold' else 'Standard' end)::public.verification_tier_enum,is_verified=true,verified_at=now(),verification_expires_at=v_expires,updated_at=now() where id=p_user_id; end if; if not found then raise exception 'PROFILE_NOT_FOUND' using errcode='P0002'; end if; select user_id into v_owner from private.admin_roles where role='owner' limit 1; insert into public.activities(recipient_id,actor_id,activity_type,entity_type,message,is_read) values(p_user_id,v_owner,'admin_verification','system',case when v_badge='NONE' then 'Blink • Your verification was updated.' else 'Blink • '||v_badge||' verification activated.' end,false); insert into private.admin_audit_log(actor_id,action,target_user_id,details) values(v_actor,'set_verification',p_user_id,jsonb_build_object('badge',v_badge,'expires_at',v_expires)); return jsonb_build_object('ok',true,'badge',v_badge,'expires_at',v_expires); end; $function$

CREATE OR REPLACE FUNCTION private.admin_set_verification_internal(p_actor uuid, p_target uuid, p_badge text, p_hours integer DEFAULT 720)
 RETURNS timestamp with time zone LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_badge text:=upper(coalesce(p_badge,'NONE')); v_hours integer:=greatest(1,least(coalesce(p_hours,720),8760)); v_exp timestamptz; begin perform private.admin_assert_target_user(p_actor,p_target); if v_badge not in ('NONE','BLUE','GOLD') then raise exception 'INVALID_VERIFICATION_BADGE'; end if; if private.is_blink_owner_id(p_target) and not private.is_blink_owner_id(p_actor) then raise exception 'BLINK_OWNER_VERIFICATION_IS_PROTECTED' using errcode='42501'; end if; if v_badge='GOLD' and not private.is_blink_owner_id(p_actor) then raise exception 'ONLY_BLINK_OWNER_CAN_GRANT_GOLD_VERIFICATION' using errcode='42501'; end if; if v_badge='BLUE' and v_hours>48 and not private.is_blink_owner_id(p_actor) then raise exception 'ONLY_BLINK_OWNER_CAN_GRANT_BLUE_VERIFICATION_OVER_48_HOURS' using errcode='42501'; end if; if v_badge='NONE' then update public.profiles set verification_badge='NONE',verification_tier='None'::public.verification_tier_enum,is_verified=false,verified_at=null,verification_expires_at=null where id=p_target; return null; end if; v_exp:=now()+make_interval(hours=>v_hours); update public.profiles set verification_badge=v_badge,verification_tier=case when v_badge='GOLD' then 'Gold'::public.verification_tier_enum else 'Standard'::public.verification_tier_enum end,is_verified=true,verified_at=coalesce(verified_at,now()),verification_expires_at=v_exp where id=p_target; return v_exp; end; $function$

CREATE OR REPLACE FUNCTION private.admin_user_is_blocked(p_user uuid, p_action text)
 RETURNS boolean LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare c private.admin_user_controls%rowtype; begin if private.is_blink_owner_id(p_user) then return false; end if; select * into c from private.admin_user_controls where user_id=p_user; if not found then return false; end if; if c.is_banned or (c.banned_until is not null and c.banned_until>now()) or (c.suspended_until is not null and c.suspended_until>now()) or (c.account_locked_until is not null and c.account_locked_until>now()) or (c.account_disabled_until is not null and c.account_disabled_until>now()) then return true; end if; return case p_action when 'post' then c.post_restricted_until is not null and c.post_restricted_until>now() when 'reel' then c.reel_restricted_until is not null and c.reel_restricted_until>now() when 'comment' then c.comment_restricted_until is not null and c.comment_restricted_until>now() when 'message' then c.message_restricted_until is not null and c.message_restricted_until>now() when 'marketplace' then c.marketplace_restricted_until is not null and c.marketplace_restricted_until>now() when 'follow' then c.follow_restricted_until is not null and c.follow_restricted_until>now() else false end; end; $function$

CREATE OR REPLACE FUNCTION private.blink_current_pass(p_user uuid)
 RETURNS uuid LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ select v.id from public.blink_vip_passes v where v.user_id=p_user and v.starts_at<=now() and v.expires_at>now() order by v.expires_at desc limit 1; $function$

CREATE OR REPLACE FUNCTION private.blink_effective_price(p_user uuid, p_item blink_store_catalog, p_multiplier integer DEFAULT 1)
 RETURNS integer LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ declare v_price integer; begin v_price:=case when cardinality(p_item.boost_multipliers)>0 then case p_multiplier when 1 then p_item.price when 2 then floor(p_item.price*1.65)::integer when 3 then floor(p_item.price*2.35)::integer when 5 then floor(p_item.price*3.65)::integer else p_item.price end else p_item.price end; if private.is_blink_vip_id(p_user,now()) then v_price:=greatest(0,floor(v_price*0.90)::integer); end if; return v_price; end $function$

CREATE OR REPLACE FUNCTION private.blink_owner_id()
 RETURNS uuid LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO ''
AS $function$ select r.user_id from private.admin_roles r where r.role='owner' order by r.granted_at asc limit 1; $function$

CREATE OR REPLACE FUNCTION private.decorate_blink_notification()
 RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ begin new.actor_is_vip:=private.is_blink_vip_id(new.actor_id,coalesce(new.created_at,now())); new.vip_priority:=new.actor_is_vip and new.type in ('like','comment','repost','follow','mention'); return new; end $function$

CREATE OR REPLACE FUNCTION private.dispatch_admin_campaign(p_campaign_id uuid)
 RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ declare c private.admin_notification_campaigns%rowtype; v_owner uuid:=private.blink_owner_id(); v_count integer:=0; v_preview text; begin select * into c from private.admin_notification_campaigns where id=p_campaign_id for update; if not found then raise exception 'CAMPAIGN_NOT_FOUND'; end if; if c.status in ('sent','cancelled') then return c.delivered_count; end if; if c.scheduled_at is not null and c.scheduled_at>now() then return 0; end if; update private.admin_notification_campaigns set status='sending',updated_at=now() where id=c.id; v_preview:=case when nullif(trim(coalesce(c.subtopic,'')),'') is not null then c.subtopic||' — '||coalesce(c.message,'') else coalesce(c.message,'') end; with targets as (select p.id from public.profiles p where case coalesce(c.audience->>'type','all') when 'user' then p.id=(c.audience->>'user_id')::uuid when 'users' then p.id in (select value::uuid from jsonb_array_elements_text(coalesce(c.audience->'user_ids','[]'::jsonb))) when 'university' then coalesce(p.university,'')=coalesce(c.audience->>'university','') when 'universities' then coalesce(p.university,'') in (select value from jsonb_array_elements_text(coalesce(c.audience->'universities','[]'::jsonb))) when 'blue' then upper(coalesce(p.verification_badge,''))='BLUE' and p.is_verified when 'gold' then upper(coalesce(p.verification_badge,''))='GOLD' and p.is_verified when 'verified' then p.is_verified when 'unverified' then not coalesce(p.is_verified,false) else true end),ins as (insert into public.notifications(user_id,actor_id,type,post_id,text,sub_text,is_read) select t.id,v_owner,'system'::public.notification_type_enum,case when c.link_type='post' then c.link_id else null end,left(coalesce(c.title,'Blink'),120),left(v_preview,2000),false from targets t where not exists(select 1 from private.admin_notification_deliveries d where d.campaign_id=c.id and d.user_id=t.id) returning id,user_id) insert into private.admin_notification_deliveries(campaign_id,user_id,notification_id) select c.id,ins.user_id,ins.id from ins on conflict(campaign_id,user_id) do nothing; insert into public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message,is_read) select d.user_id,v_owner,'system','notification',d.notification_id,left(coalesce(c.title,'Blink')||' • '||v_preview,2000),false from private.admin_notification_deliveries d where d.campaign_id=c.id and not exists(select 1 from public.activities a where a.entity_type='notification' and a.entity_id=d.notification_id and a.recipient_id=d.user_id); select count(*) into v_count from private.admin_notification_deliveries where campaign_id=c.id; update private.admin_notification_campaigns set status='sent',delivered_count=v_count,updated_at=now() where id=c.id; return v_count; exception when others then update private.admin_notification_campaigns set status='failed',updated_at=now() where id=p_campaign_id and status<>'cancelled'; raise; end; $function$

CREATE OR REPLACE FUNCTION private.dispatch_due_admin_campaigns()
 RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ declare r record; v_total integer:=0; begin for r in select id from private.admin_notification_campaigns where status='scheduled' and scheduled_at<=now() order by scheduled_at limit 50 loop begin v_total:=v_total+private.dispatch_admin_campaign(r.id); exception when others then null; end; end loop; return v_total; end; $function$

CREATE OR REPLACE FUNCTION private.enforce_blink_owner_post()
 RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ begin if private.is_blink_owner_id(new.user_id) then new.is_pinned:=true; new.visibility_weight:=greatest(coalesce(new.visibility_weight,1),1000); end if; return new; end; $function$

CREATE OR REPLACE FUNCTION private.ensure_user_balance_row()
 RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public', 'pg_temp'
AS $function$ begin insert into public.user_balances(user_id) values(new.id) on conflict(user_id) do nothing; return new; end; $function$

CREATE OR REPLACE FUNCTION private.expire_blink_admin_state()
 RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ begin update public.profiles set verification_badge='NONE',verification_tier='None'::public.verification_tier_enum,is_verified=false,verified_at=null,verification_expires_at=null,updated_at=now() where verification_expires_at is not null and verification_expires_at<=now(); delete from private.admin_roles where role='admin' and expires_at is not null and expires_at<=now(); end; $function$

CREATE OR REPLACE FUNCTION private.expire_blink_items_and_remind_vip()
 RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path TO ''
AS $function$ begin update public.blink_inventory set status='EXPIRED',updated_at=now() where status='ACTIVE' and expires_at is not null and expires_at<=now(); update public.blink_boosts set status='ENDED' where status='ACTIVE' and ends_at<=now(); insert into public.notifications(user_id,type,comment) select v.user_id,'system','👑 Your Blink VIP expires in less than 24 hours. Open Blink Store to review or renew.' from public.blink_vip_passes v where v.expires_at>now() and v.expires_at<=now()+interval '24 hours' and not v.expiry_reminder_sent; update public.blink_vip_passes set expiry_reminder_sent=true where expires_at>now() and expires_at<=now()+interval '24 hours' and not expiry_reminder_sent; update public.profiles p set blink_vip_until=(select max(v.expires_at) from public.blink_vip_passes v where v.user_id=p.id and v.expires_at>now()) where p.blink_vip_until is not null and p.blink_vip_until<=now(); end $function$
