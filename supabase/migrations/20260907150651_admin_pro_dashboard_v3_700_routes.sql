-- Maps the existing 200 operational admin tools into professional sections,
-- gives every tool a stable route/permission key, and turns features 201..700
-- into 500 real Supabase-backed operational insights.

update private.admin_feature_registry_v2
set section_key = case
  when module='users' then 'users'
  when module='account_safety' then 'safety_restrictions'
  when module='sessions_devices' then 'sessions_devices'
  when module='universities' then 'universities'
  when module='verification' then 'verification'
  when module='admin_roles' and (title ilike '%permission%' or title ilike '%moderator%' or title ilike '%role%') then 'roles_permissions'
  when module='admin_roles' then 'admins'
  when module='permissions' then 'roles_permissions'
  when module='posts' then 'posts'
  when module='reels' then 'reels'
  when module='comments' then 'comments'
  when module='reports_flags' then 'reports'
  when module='moderation_queue' then 'moderation_queue'
  when module='messages' then 'messages'
  when module='notifications' then 'notifications'
  when module='coins_wallet' then 'coins_balances'
  when module='ads_monetization' then 'transactions'
  when module in ('analytics','growth') then 'analytics'
  when module='engagement' then 'engagement'
  when module='scheduled_content' then 'scheduled_content'
  when module='support_helpdesk' then 'support_appeals'
  when module='trust_safety' then 'safety_restrictions'
  when module='audit_logs' then 'audit_logs'
  when module in ('performance_health','system_security') then 'developer_ops'
  when module in ('feature_flags','system') then 'system_config'
  else 'analytics'
end
where feature_id between 1 and 200;

update private.admin_feature_registry_v2
set section_key = case
    when title ilike '%profile%' and module='users' then 'profiles'
    when title ilike '%account%' and module='users' then 'accounts'
    when title ilike '%university%' then 'universities'
    when title ilike '%session%' or title ilike '%device%' then 'sessions_devices'
    else section_key
  end,
  route_key = 'admin.' || coalesce(section_key,module,'general') || '.' || feature_id::text || '.' || trim(both '.' from regexp_replace(lower(title),'[^a-z0-9]+','.','g')),
  permission_key = 'admin.feature.' || feature_id::text,
  risk_level = case
    when owner_only then 'critical'
    when title ilike '%permanent%ban%' or title ilike '%delete%' or title ilike '%disable%' then 'high'
    when reversible then 'medium'
    else 'low'
  end,
  confirmation_kind = case
    when owner_only or title ilike '%permanent%ban%' or title ilike '%delete%' or title ilike '%disable%' then 'typed'
    when reversible then 'confirm'
    else 'none'
  end
where feature_id between 1 and 200;

with metrics(metric_no,metric_title,section_key,route_slug) as (values
 (0,'Active users','users','active_users'),
 (1,'New accounts','accounts','new_accounts'),
 (2,'Profile updates','profiles','profile_updates'),
 (3,'New device sessions','sessions_devices','device_sessions'),
 (4,'Active universities','universities','active_universities'),
 (5,'Verification requests','verification','verification_requests'),
 (6,'Admin grants','admins','admin_grants'),
 (7,'Role and permission changes','roles_permissions','role_permission_changes'),
 (8,'Posts published','posts','posts_published'),
 (9,'Reels published','reels','reels_published'),
 (10,'Stories published','stories','stories_published'),
 (11,'Comments created','comments','comments_created'),
 (12,'Messages sent','messages','messages_sent'),
 (13,'Conversations created','conversations','conversations_created'),
 (14,'Reports submitted','reports','reports_submitted'),
 (15,'Moderation actions','moderation_queue','moderation_actions'),
 (16,'Support and appeal reports','support_appeals','support_appeal_reports'),
 (17,'Safety restriction changes','safety_restrictions','safety_restrictions'),
 (18,'Notifications created','notifications','notifications_created'),
 (19,'Admin announcements','announcements','admin_announcements'),
 (20,'Blink Coin gifts','coins_balances','coin_gifts'),
 (21,'Point transactions','points_rewards','point_transactions'),
 (22,'Token transactions','transactions','token_transactions'),
 (23,'Marketplace listings','marketplace','marketplace_listings'),
 (24,'Marketplace orders','orders_sellers','marketplace_orders'),
 (25,'Connect Hub listings','connect_hub','connect_listings'),
 (26,'Housing requests','housing','housing_requests'),
 (27,'Mentor requests','mentorship','mentor_requests'),
 (28,'Reading Mate requests','reading_mate','reading_mate_requests'),
 (29,'Study circles created','study_circles','study_circles'),
 (30,'Game sessions','games','game_sessions'),
 (31,'Leaderboard reward events','leaderboards','leaderboard_rewards'),
 (32,'Recommendation events','analytics','recommendation_events'),
 (33,'System configuration changes','system_config','system_config_changes'),
 (34,'Failed push dispatches','developer_ops','failed_push_dispatches'),
 (35,'Admin history events','audit_logs','admin_history_events'),
 (36,'Follows created','engagement','follows_created'),
 (37,'Profile views','engagement','profile_views'),
 (38,'Content views','engagement','content_views'),
 (39,'Polls created','posts','polls_created'),
 (40,'Poll votes','engagement','poll_votes'),
 (41,'Scheduled content created','scheduled_content','scheduled_content'),
 (42,'Marketplace inquiries','marketplace','marketplace_inquiries'),
 (43,'Housing applications','housing','housing_applications'),
 (44,'Mentor profiles created','mentorship','mentor_profiles'),
 (45,'Reading Mate profiles created','reading_mate','reading_mate_profiles'),
 (46,'Study circle requests','study_circles','study_circle_requests'),
 (47,'Game challenges','games','game_challenges'),
 (48,'Verification payments','verification','verification_payments'),
 (49,'User blocks','safety_restrictions','user_blocks')
), windows(window_no,window_label,window_slug) as (values
 (0,'Last 15 minutes','15m'),
 (1,'Last 1 hour','1h'),
 (2,'Last 6 hours','6h'),
 (3,'Last 12 hours','12h'),
 (4,'Last 24 hours','1d'),
 (5,'Last 3 days','3d'),
 (6,'Last 7 days','7d'),
 (7,'Last 14 days','14d'),
 (8,'Last 30 days','30d'),
 (9,'Last 90 days','90d')
), generated as (
 select 201 + m.metric_no*10 + w.window_no as feature_id,
        m.metric_title || ' • ' || w.window_label as title,
        m.section_key,
        'admin.'||m.section_key||'.insight.'||m.route_slug||'.'||w.window_slug as route_key,
        'Live Supabase-backed professional admin insight: '||m.metric_title||' for '||lower(w.window_label)||'.' as description
 from metrics m cross join windows w
)
update private.admin_feature_registry_v2 f
set title=g.title,
    section_key=g.section_key,
    route_key=g.route_key,
    target_type='none',
    input_kind='insight',
    reversible=false,
    description=g.description,
    permission_key='admin.feature.'||f.feature_id::text,
    risk_level='low',
    confirmation_kind='none'
from generated g
where f.feature_id=g.feature_id;

create or replace function private.admin_execute_pro_feature_impl(p_feature_id integer)
returns jsonb
language plpgsql security definer set search_path=''
as $$
declare
  v_actor uuid;
  v_idx integer;
  v_metric integer;
  v_window integer;
  v_since timestamptz;
  v_value numeric:=0;
  v_route text;
  v_section text;
  v_title text;
begin
  v_actor:=private.admin_feature_access_v2(p_feature_id);
  if p_feature_id<201 or p_feature_id>700 then raise exception 'PRO_FEATURE_ID_OUT_OF_RANGE'; end if;
  v_idx:=p_feature_id-201;
  v_metric:=v_idx/10;
  v_window:=mod(v_idx,10);
  v_since:=now()-case v_window
    when 0 then interval '15 minutes'
    when 1 then interval '1 hour'
    when 2 then interval '6 hours'
    when 3 then interval '12 hours'
    when 4 then interval '1 day'
    when 5 then interval '3 days'
    when 6 then interval '7 days'
    when 7 then interval '14 days'
    when 8 then interval '30 days'
    else interval '90 days' end;

  select route_key,section_key,title into v_route,v_section,v_title
  from private.admin_feature_registry_v2 where feature_id=p_feature_id;

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

  return jsonb_build_object(
    'feature_id',p_feature_id,
    'ok',true,
    'route_key',v_route,
    'section_key',v_section,
    'title',v_title,
    'result',jsonb_build_object('value',coalesce(v_value,0),'window_start',v_since,'window_end',now())
  );
end $$;

create or replace function private.admin_execute_feature_v3_impl(
 p_feature_id integer,
 p_entity_ref text default null,
 p_text text default null,
 p_amount bigint default null,
 p_duration_hours integer default null,
 p_options jsonb default '{}'::jsonb
)
returns jsonb language plpgsql security definer set search_path=''
as $$
declare v_result jsonb; v_section text; v_route text;
begin
  perform private.admin_feature_access_v2(p_feature_id);
  select section_key,route_key into v_section,v_route
  from private.admin_feature_registry_v2 where feature_id=p_feature_id;

  if p_feature_id between 1 and 200 then
    v_result:=private.admin_execute_feature_v2_impl(
      p_feature_id,p_entity_ref,p_text,p_amount,p_duration_hours,coalesce(p_options,'{}'::jsonb)
    );
  elsif p_feature_id between 201 and 700 then
    v_result:=private.admin_execute_pro_feature_impl(p_feature_id);
  else
    raise exception 'FEATURE_ID_OUT_OF_RANGE';
  end if;

  return coalesce(v_result,'{}'::jsonb) || jsonb_build_object('section_key',v_section,'route_key',v_route);
end $$;

create or replace function public.admin_execute_feature_v3(
 p_feature_id integer,
 p_entity_ref text default null,
 p_text text default null,
 p_amount bigint default null,
 p_duration_hours integer default null,
 p_options jsonb default '{}'::jsonb
)
returns jsonb language sql security definer set search_path=''
as $$
  select private.admin_execute_feature_v3_impl(
    p_feature_id,p_entity_ref,p_text,p_amount,p_duration_hours,p_options
  )
$$;

revoke all on function public.admin_execute_feature_v3(integer,text,text,bigint,integer,jsonb) from public,anon;
grant execute on function public.admin_execute_feature_v3(integer,text,text,bigint,integer,jsonb) to authenticated;
