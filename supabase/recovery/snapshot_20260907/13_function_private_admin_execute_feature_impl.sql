-- Exact live definition captured from Blink Supabase on 2026-09-07.
-- Recovery-only baseline. Do not auto-run against production.

CREATE OR REPLACE FUNCTION private.admin_execute_feature_impl(p_feature_id integer, p_target_id uuid DEFAULT NULL::uuid, p_text text DEFAULT NULL::text, p_amount bigint DEFAULT NULL::bigint, p_duration_hours integer DEFAULT NULL::integer, p_extra jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_actor uuid;
  v_owner uuid:=private.blink_owner_id();
  v_result jsonb:='{}'::jsonb;
  v_hours integer:=greatest(1,least(coalesce(p_duration_hours,24),8760));
  v_count integer:=0;
  v_balance bigint;
  v_exp timestamptz;
  v_campaign uuid;
  v_uid uuid;
  v_text text:=trim(coalesce(p_text,''));
  v_reason text:=nullif(trim(coalesce(p_extra->>'reason','')),'');
  v_permissions text[];
  v_scopes text[];
  v_template text;
  v_enabled boolean;
  v_from timestamptz;
  v_to timestamptz;
  rec record;
begin
  v_actor:=private.admin_feature_access(p_feature_id);

  if private.admin_config_bool('require_admin_action_reason',false)
     and (
       p_feature_id between 21 and 33 or p_feature_id between 35 and 43 or
       p_feature_id between 48 and 68 or p_feature_id in (75,76) or
       p_feature_id between 81 and 87 or p_feature_id between 94 and 109 or
       p_feature_id=111 or p_feature_id between 113 and 120 or
       p_feature_id between 127 and 139 or p_feature_id between 142 and 146 or
       p_feature_id between 153 and 156 or p_feature_id between 160 and 178 or
       p_feature_id between 197 and 200
     )
     and v_reason is null then
    raise exception 'ADMIN_ACTION_REASON_REQUIRED';
  end if;

  if p_feature_id between 1 and 8 then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result
    from (
      select p.id,p.username,p.full_name,p.email,p.university,p.faculty,p.department,
             p.verification_badge,p.is_verified,p.created_at,p.last_seen_at,
             coalesce(g.coins,0) as coins,p.points,p.follower_count,p.following_count,p.posts_count,
             case
               when c.is_banned then 'banned'
               when c.account_disabled_until>now() then 'disabled'
               when c.account_locked_until>now() then 'locked'
               when c.suspended_until>now() then 'suspended'
               when c.under_review then 'under_review'
               when c.suspicious then 'suspicious'
               when c.trusted then 'trusted'
               else 'active'
             end as account_status
      from public.profiles p
      left join public.game_profiles g on g.user_id=p.id
      left join private.admin_user_controls c on c.user_id=p.id
      where case p_feature_id
        when 1 then coalesce(p.username,'') ilike '%'||v_text||'%'
        when 2 then coalesce(p.full_name,'') ilike '%'||v_text||'%'
        when 3 then coalesce(p.email,'') ilike '%'||v_text||'%'
        when 4 then coalesce(p.university,'') ilike '%'||v_text||'%'
        when 5 then coalesce(p.department,'') ilike '%'||v_text||'%'
        when 6 then coalesce(p.faculty,'') ilike '%'||v_text||'%'
        when 7 then upper(coalesce(p.verification_badge,'NONE'))=upper(v_text)
        when 8 then (
          case
            when c.is_banned then 'banned'
            when c.account_disabled_until>now() then 'disabled'
            when c.account_locked_until>now() then 'locked'
            when c.suspended_until>now() then 'suspended'
            when c.under_review then 'under_review'
            when c.suspicious then 'suspicious'
            when c.trusted then 'trusted'
            else 'active'
          end
        ) ilike '%'||v_text||'%'
      end
      order by p.created_at desc
      limit 50
    ) q;

  elsif p_feature_id between 9 and 19 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    select case p_feature_id
      when 9 then jsonb_build_object(
        'id',p.id,'username',p.username,'full_name',p.full_name,'email',p.email,
        'university',p.university,'faculty',p.faculty,'department',p.department,
        'course_of_study',p.course_of_study,'academic_level',p.academic_level,
        'phone',p.phone,'created_at',p.created_at,'last_seen',coalesce(p.last_seen_at,p.last_seen),
        'verification_badge',p.verification_badge,'verification_expires_at',p.verification_expires_at,
        'coins',coalesce(g.coins,0),'points',p.points,'followers',p.follower_count,
        'following',p.following_count,'posts',p.posts_count,
        'reels',(select count(*) from public.feed_posts fp where fp.user_id=p.id and fp.is_reel)
      )
      when 10 then jsonb_build_object('created_at',p.created_at)
      when 11 then jsonb_build_object('last_seen',coalesce(p.last_seen_at,p.last_seen),'online',coalesce(p.is_online,p.online_now,false))
      when 12 then jsonb_build_object('university',p.university,'faculty',p.faculty,'department',p.department)
      when 13 then jsonb_build_object('badge',p.verification_badge,'verified',p.is_verified,'expires_at',p.verification_expires_at)
      when 14 then jsonb_build_object('coins',coalesce(g.coins,0))
      when 15 then jsonb_build_object('points',coalesce(p.points,0))
      when 16 then jsonb_build_object('followers',coalesce(p.follower_count,0))
      when 17 then jsonb_build_object('following',coalesce(p.following_count,0))
      when 18 then jsonb_build_object('posts',(select count(*) from public.feed_posts fp where fp.user_id=p.id and not coalesce(fp.is_reel,false)))
      when 19 then jsonb_build_object('reels',(select count(*) from public.feed_posts fp where fp.user_id=p.id and coalesce(fp.is_reel,false)))
      end
    into v_result
    from public.profiles p left join public.game_profiles g on g.user_id=p.id
    where p.id=p_target_id;

  elsif p_feature_id=20 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result
    from (
      select r.id,r.reason,r.status,r.created_at,r.reported_post_id,r.reported_comment_id
      from public.reports r where r.reported_user_id=p_target_id order by r.created_at desc limit 100
    ) q;

  elsif p_feature_id in (21,22) then
    perform private.admin_ensure_control(v_actor,p_target_id);
    update private.admin_user_controls set suspended_until=now()+make_interval(hours=>v_hours),updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    update auth.users set banned_until=now()+make_interval(hours=>v_hours) where id=p_target_id;
    delete from auth.sessions where user_id=p_target_id;
    v_result:=jsonb_build_object('suspended_until',now()+make_interval(hours=>v_hours));

  elsif p_feature_id=23 then
    perform private.admin_ensure_control(v_actor,p_target_id);
    if private.is_blink_owner_id(p_target_id) then raise exception 'BLINK_OWNER_IS_IMMUTABLE'; end if;
    update private.admin_user_controls set is_banned=true,banned_until='9999-12-31'::timestamptz,updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    update auth.users set banned_until='9999-12-31'::timestamptz where id=p_target_id;
    delete from auth.sessions where user_id=p_target_id;
    v_result:=jsonb_build_object('banned',true);

  elsif p_feature_id=24 then
    perform private.admin_ensure_control(v_actor,p_target_id);
    update private.admin_user_controls set suspended_until=null,updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    if not exists(select 1 from private.admin_user_controls c where c.user_id=p_target_id and (c.is_banned or c.account_disabled_until>now() or c.account_locked_until>now())) then
      update auth.users set banned_until=null where id=p_target_id;
    end if;
    v_result:=jsonb_build_object('suspended',false);

  elsif p_feature_id=25 then
    perform private.admin_ensure_control(v_actor,p_target_id);
    if private.is_blink_owner_id(p_target_id) then raise exception 'BLINK_OWNER_IS_IMMUTABLE'; end if;
    update private.admin_user_controls set is_banned=false,banned_until=null,updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    update auth.users set banned_until=null where id=p_target_id;
    v_result:=jsonb_build_object('banned',false);

  elsif p_feature_id between 26 and 31 then
    perform private.admin_ensure_control(v_actor,p_target_id);
    if p_feature_id=26 then update private.admin_user_controls set post_restricted_until=now()+make_interval(hours=>v_hours),updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    elsif p_feature_id=27 then update private.admin_user_controls set comment_restricted_until=now()+make_interval(hours=>v_hours),updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    elsif p_feature_id=28 then update private.admin_user_controls set message_restricted_until=now()+make_interval(hours=>v_hours),updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    elsif p_feature_id=29 then update private.admin_user_controls set reel_restricted_until=now()+make_interval(hours=>v_hours),updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    elsif p_feature_id=30 then update private.admin_user_controls set marketplace_restricted_until=now()+make_interval(hours=>v_hours),updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    else update private.admin_user_controls set follow_restricted_until=now()+make_interval(hours=>v_hours),updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    end if;
    v_result:=jsonb_build_object('restricted_until',now()+make_interval(hours=>v_hours));

  elsif p_feature_id=32 then
    perform private.admin_ensure_control(v_actor,p_target_id);
    update private.admin_user_controls set under_review=true,updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    v_result:=jsonb_build_object('under_review',true);

  elsif p_feature_id=33 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    if v_text='' then raise exception 'NOTE_REQUIRED'; end if;
    insert into private.admin_notes(actor_id,target_user_id,note) values(v_actor,p_target_id,v_text);
    v_result:=jsonb_build_object('saved',true);

  elsif p_feature_id=34 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (
      select action,details,created_at from private.admin_audit_log where target_user_id=p_target_id order by created_at desc limit 100
    ) q;

  elsif p_feature_id in (35,36) then
    perform private.admin_ensure_control(v_actor,p_target_id);
    if v_text='' then v_text:=case when p_feature_id=35 then 'Your Blink account received an admin warning.' else 'Please review Blink community and account policies.' end; end if;
    update private.admin_user_controls set warning_count=warning_count+1,last_warning=v_text,updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    perform private.admin_send_system_notification(p_target_id,case when p_feature_id=35 then 'Blink warning' else 'Blink policy notice' end,v_text,null);
    v_result:=jsonb_build_object('warning_sent',true);

  elsif p_feature_id=37 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    delete from auth.sessions where user_id=p_target_id;
    get diagnostics v_count=row_count;
    v_result:=jsonb_build_object('sessions_revoked',v_count);

  elsif p_feature_id in (38,39) then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    if v_text='' then raise exception 'SESSION_ID_REQUIRED_IN_TEXT'; end if;
    delete from auth.sessions where user_id=p_target_id and id=v_text::uuid;
    get diagnostics v_count=row_count;
    v_result:=jsonb_build_object('sessions_revoked',v_count);

  elsif p_feature_id=40 then
    perform private.admin_ensure_control(v_actor,p_target_id);
    update private.admin_user_controls set account_locked_until=now()+make_interval(hours=>v_hours),updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    update auth.users set banned_until=now()+make_interval(hours=>v_hours) where id=p_target_id;
    delete from auth.sessions where user_id=p_target_id;
    v_result:=jsonb_build_object('locked_until',now()+make_interval(hours=>v_hours));

  elsif p_feature_id=41 then
    perform private.admin_ensure_control(v_actor,p_target_id);
    update private.admin_user_controls set account_locked_until=null,updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    if not exists(select 1 from private.admin_user_controls c where c.user_id=p_target_id and (c.is_banned or c.suspended_until>now() or c.account_disabled_until>now())) then update auth.users set banned_until=null where id=p_target_id; end if;
    v_result:=jsonb_build_object('locked',false);

  elsif p_feature_id=42 then
    perform private.admin_ensure_control(v_actor,p_target_id);
    update private.admin_user_controls set trusted=true,suspicious=false,updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    v_result:=jsonb_build_object('trusted',true);

  elsif p_feature_id=43 then
    perform private.admin_ensure_control(v_actor,p_target_id);
    update private.admin_user_controls set suspicious=true,trusted=false,updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    v_result:=jsonb_build_object('suspicious',true);

  elsif p_feature_id=44 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (
      select p2.id,p2.username,p2.full_name,p2.email,p2.phone,p2.whatsapp
      from public.profiles p1 join public.profiles p2 on p2.id<>p1.id
       and ((p1.email is not null and p1.email<>'' and lower(p2.email)=lower(p1.email)) or (p1.phone is not null and p1.phone<>'' and p2.phone=p1.phone) or (p1.whatsapp is not null and p1.whatsapp<>'' and p2.whatsapp=p1.whatsapp))
      where p1.id=p_target_id limit 50
    ) q;

  elsif p_feature_id=45 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    select jsonb_build_object(
      'devices',coalesce((select jsonb_agg(to_jsonb(d)) from (select id,device_platform,last_active_at,created_at from public.user_devices where user_id=p_target_id order by last_active_at desc limit 50)d),'[]'::jsonb),
      'sessions',coalesce((select jsonb_agg(to_jsonb(s)) from (select id,created_at,updated_at,not_after,user_agent,ip::text as ip from auth.sessions where user_id=p_target_id order by updated_at desc limit 50)s),'[]'::jsonb)
    ) into v_result;

  elsif p_feature_id=46 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    select jsonb_build_object('recent_notifications',(select count(*) from public.notifications where user_id=p_target_id and created_at>now()-interval '7 days'),'recent_messages',(select count(*) from public.messages where sender_id=p_target_id and created_at>now()-interval '7 days'),'recent_posts',(select count(*) from public.feed_posts where user_id=p_target_id and created_at>now()-interval '7 days'),'last_seen',(select coalesce(last_seen_at,last_seen) from public.profiles where id=p_target_id)) into v_result;

  elsif p_feature_id=47 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select id,created_at,updated_at,refreshed_at,ip::text as ip,user_agent from auth.sessions where user_id=p_target_id order by created_at desc limit 100) q;

  elsif p_feature_id=48 then
    perform private.admin_ensure_control(v_actor,p_target_id);
    if private.is_blink_owner_id(p_target_id) then raise exception 'BLINK_OWNER_IS_IMMUTABLE'; end if;
    update private.admin_user_controls set account_disabled_until=now()+make_interval(hours=>v_hours),updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    update auth.users set banned_until=now()+make_interval(hours=>v_hours) where id=p_target_id;
    delete from auth.sessions where user_id=p_target_id;
    v_result:=jsonb_build_object('disabled_until',now()+make_interval(hours=>v_hours));

  elsif p_feature_id=49 then
    perform private.admin_ensure_control(v_actor,p_target_id);
    update private.admin_user_controls set account_disabled_until=null,updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    if not exists(select 1 from private.admin_user_controls c where c.user_id=p_target_id and (c.is_banned or c.suspended_until>now() or c.account_locked_until>now())) then update auth.users set banned_until=null where id=p_target_id; end if;
    v_result:=jsonb_build_object('disabled',false);

  elsif p_feature_id=50 then
    perform private.admin_ensure_control(v_actor,p_target_id);
    if private.is_blink_owner_id(p_target_id) then raise exception 'BLINK_OWNER_IS_IMMUTABLE'; end if;
    update private.admin_user_controls set deletion_requested_at=now(),account_disabled_until='9999-12-31'::timestamptz,updated_by=v_actor,updated_at=now() where user_id=p_target_id;
    update auth.users set banned_until='9999-12-31'::timestamptz where id=p_target_id;
    delete from auth.sessions where user_id=p_target_id;
    v_result:=jsonb_build_object('deletion_review_queued',true);

  elsif p_feature_id between 51 and 56 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    if private.is_blink_owner_id(p_target_id) then raise exception 'BLINK_OWNER_IS_IMMUTABLE'; end if;
    if p_feature_id in (51,53) then
      insert into private.admin_roles(user_id,role,granted_by,granted_at,expires_at,role_label,permissions,scope_universities,suspended_until,can_manage_admins)
      values(p_target_id,'admin',v_actor,now(),now()+make_interval(hours=>v_hours),'admin',array['users','coins','verification','content','messages','analytics']::text[],'{}'::text[],null,false)
      on conflict(user_id) do update set role='admin',granted_by=v_actor,granted_at=now(),expires_at=excluded.expires_at,role_label='admin',suspended_until=null;
      v_result:=jsonb_build_object('admin',true,'expires_at',now()+make_interval(hours=>v_hours));
    elsif p_feature_id in (52,56) then
      delete from private.admin_roles where user_id=p_target_id and role='admin';
      v_result:=jsonb_build_object('admin',false);
    elsif p_feature_id=54 then
      update private.admin_roles set expires_at=now()+make_interval(hours=>v_hours),granted_by=v_actor where user_id=p_target_id and role='admin';
      if not found then raise exception 'TARGET_IS_NOT_ADMIN'; end if;
      v_result:=jsonb_build_object('expires_at',now()+make_interval(hours=>v_hours));
    else
      update private.admin_roles set expires_at=greatest(coalesce(expires_at,now()),now())+make_interval(hours=>v_hours),granted_by=v_actor where user_id=p_target_id and role='admin';
      if not found then raise exception 'TARGET_IS_NOT_ADMIN'; end if;
      select jsonb_build_object('expires_at',expires_at) into v_result from private.admin_roles where user_id=p_target_id;
    end if;

  elsif p_feature_id=57 then
    if v_text='' then raise exception 'TEMPLATE_KEY_REQUIRED'; end if;
    select array_agg(value) into v_permissions from jsonb_array_elements_text(coalesce(p_extra->'permissions','[]'::jsonb));
    if cardinality(coalesce(v_permissions,'{}'::text[]))=0 then raise exception 'PERMISSIONS_REQUIRED'; end if;
    insert into private.admin_role_templates(template_key,label,permissions,can_manage_admins) values(lower(replace(v_text,' ','_')),coalesce(nullif(p_extra->>'label',''),v_text),v_permissions,coalesce((p_extra->>'can_manage_admins')::boolean,false)) on conflict(template_key) do update set label=excluded.label,permissions=excluded.permissions,can_manage_admins=excluded.can_manage_admins;
    v_result:=jsonb_build_object('template',lower(replace(v_text,' ','_')));

  elsif p_feature_id between 58 and 64 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    v_template:=case p_feature_id when 58 then 'moderator' when 59 then 'verification_manager' when 60 then 'advertising_manager' when 61 then 'support_admin' when 62 then 'university_admin' when 63 then 'content_moderator' else 'finance_coin_manager' end;
    select permissions,can_manage_admins into v_permissions,v_enabled from private.admin_role_templates where template_key=v_template;
    insert into private.admin_roles(user_id,role,granted_by,granted_at,expires_at,role_label,permissions,scope_universities,suspended_until,can_manage_admins) values(p_target_id,'admin',v_actor,now(),now()+make_interval(hours=>v_hours),v_template,v_permissions,'{}'::text[],null,v_enabled) on conflict(user_id) do update set role='admin',granted_by=v_actor,expires_at=excluded.expires_at,role_label=v_template,permissions=v_permissions,suspended_until=null,can_manage_admins=v_enabled;
    v_result:=jsonb_build_object('role_template',v_template);

  elsif p_feature_id=65 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    select array_agg(value) into v_permissions from jsonb_array_elements_text(coalesce(p_extra->'permissions','[]'::jsonb));
    update private.admin_roles set permissions=coalesce(v_permissions,'{}'::text[]),granted_by=v_actor where user_id=p_target_id and role='admin';
    if not found then raise exception 'TARGET_IS_NOT_ADMIN'; end if;
    v_result:=jsonb_build_object('permissions',to_jsonb(coalesce(v_permissions,'{}'::text[])));

  elsif p_feature_id=66 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    select array_agg(value) into v_scopes from jsonb_array_elements_text(coalesce(p_extra->'universities','[]'::jsonb));
    update private.admin_roles set scope_universities=coalesce(v_scopes,'{}'::text[]),granted_by=v_actor where user_id=p_target_id and role='admin';
    if not found then raise exception 'TARGET_IS_NOT_ADMIN'; end if;
    v_result:=jsonb_build_object('universities',to_jsonb(coalesce(v_scopes,'{}'::text[])));

  elsif p_feature_id=67 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    update private.admin_roles set role_label='content_moderator',permissions=array['content']::text[],can_manage_admins=false,granted_by=v_actor where user_id=p_target_id and role='admin';
    if not found then raise exception 'TARGET_IS_NOT_ADMIN'; end if;
    v_result:=jsonb_build_object('moderation_only',true);

  elsif p_feature_id=68 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    update private.admin_roles set can_manage_admins=false,permissions=array_remove(permissions,'admins'),granted_by=v_actor where user_id=p_target_id and role='admin';
    if not found then raise exception 'TARGET_IS_NOT_ADMIN'; end if;
    v_result:=jsonb_build_object('can_manage_admins',false);

  elsif p_feature_id between 69 and 71 then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (
      select r.user_id,p.username,p.full_name,r.role,r.role_label,r.permissions,r.scope_universities,r.granted_at,r.expires_at,r.suspended_until from private.admin_roles r join public.profiles p on p.id=r.user_id where case p_feature_id when 69 then r.role='owner' or (r.role='admin' and (r.expires_at is null or r.expires_at>now()) and (r.suspended_until is null or r.suspended_until<=now())) when 70 then r.role='admin' and r.expires_at is not null and r.expires_at<=now() else r.role='admin' and r.suspended_until is not null and r.suspended_until>now() end order by r.granted_at desc limit 100
    ) q;

  elsif p_feature_id=72 then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (
      select s.user_id,p.username,s.id as session_id,s.created_at,s.updated_at,s.ip::text as ip,s.user_agent from auth.sessions s join private.admin_roles r on r.user_id=s.user_id join public.profiles p on p.id=s.user_id order by s.updated_at desc limit 100
    ) q;

  elsif p_feature_id=73 then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (
      select a.id,a.actor_id,p.username,a.action,a.target_user_id,a.target_post_id,a.details,a.created_at from private.admin_audit_log a join public.profiles p on p.id=a.actor_id order by a.created_at desc limit 200
    ) q;

  elsif p_feature_id=74 then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (
      select a.actor_id,p.username,a.action,a.details,a.created_at from private.admin_audit_log a join public.profiles p on p.id=a.actor_id where a.action ilike '%role%' or a.action ilike '%permission%' or a.action ilike '%feature_5%' or a.action ilike '%feature_6%' order by a.created_at desc limit 200
    ) q;

  elsif p_feature_id=75 then
    v_enabled:=coalesce((p_extra->>'enabled')::boolean,true);
    update private.admin_system_config set value=to_jsonb(v_enabled),updated_by=v_actor,updated_at=now() where key='require_admin_action_reason';
    v_result:=jsonb_build_object('required',v_enabled);

  elsif p_feature_id=76 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    if not exists(select 1 from private.admin_roles where user_id=p_target_id) then raise exception 'TARGET_IS_NOT_ADMIN'; end if;
    if v_text='' then raise exception 'NOTE_REQUIRED'; end if;
    insert into private.admin_notes(actor_id,target_admin_id,note) values(v_actor,p_target_id,v_text);
    v_result:=jsonb_build_object('saved',true);

  elsif p_feature_id=77 then
    select jsonb_build_object('user_id',r.user_id,'username',p.username,'badge','OVERALL OWNER','immutable',true) into v_result from private.admin_roles r join public.profiles p on p.id=r.user_id where r.role='owner' limit 1;

  elsif p_feature_id=78 then
    v_result:=jsonb_build_object('owner_removal_protected',exists(select 1 from pg_trigger where tgname='protect_blink_owner_role' and not tgisinternal));

  elsif p_feature_id=79 then
    v_result:=jsonb_build_object('owner_verification_protected',exists(select 1 from pg_trigger where tgname='trg_protect_blink_owner_verification' and not tgisinternal));

  elsif p_feature_id=80 then
    select jsonb_build_object('permissions',permissions,'can_manage_admins',can_manage_admins,'role',role) into v_result from private.admin_roles where role='owner' limit 1;

  elsif p_feature_id=81 then
    v_balance:=private.admin_change_coins(v_actor,p_target_id,greatest(coalesce(p_amount,0),1),'grant',coalesce(v_reason,'Admin grant'));
    v_result:=jsonb_build_object('balance',v_balance);

  elsif p_feature_id=82 then
    if jsonb_typeof(p_extra->'user_ids')<>'array' then raise exception 'USER_IDS_ARRAY_REQUIRED'; end if;
    for v_uid in select value::uuid from jsonb_array_elements_text(p_extra->'user_ids') loop perform private.admin_change_coins(v_actor,v_uid,greatest(coalesce(p_amount,0),1),'bulk_grant',coalesce(v_reason,'Bulk admin grant')); v_count:=v_count+1; end loop;
    v_result:=jsonb_build_object('users_updated',v_count);

  elsif p_feature_id=83 then
    if v_text='' then raise exception 'UNIVERSITY_REQUIRED_IN_TEXT'; end if;
    for v_uid in select id from public.profiles where university=v_text loop perform private.admin_change_coins(v_actor,v_uid,greatest(coalesce(p_amount,0),1),'university_grant',coalesce(v_reason,'University coin grant')); v_count:=v_count+1; end loop;
    v_result:=jsonb_build_object('users_updated',v_count,'university',v_text);

  elsif p_feature_id=84 then
    for v_uid in select id from public.profiles where is_verified and (v_text='' or v_text='all' or upper(verification_badge)=upper(v_text)) loop perform private.admin_change_coins(v_actor,v_uid,greatest(coalesce(p_amount,0),1),'verified_grant',coalesce(v_reason,'Verified user coin grant')); v_count:=v_count+1; end loop;
    v_result:=jsonb_build_object('users_updated',v_count);

  elsif p_feature_id=85 then
    v_balance:=private.admin_change_coins(v_actor,p_target_id,greatest(coalesce(p_amount,0),1),'promotion',coalesce(v_reason,'Promotional coins')); v_result:=jsonb_build_object('balance',v_balance);

  elsif p_feature_id=86 then
    v_balance:=private.admin_change_coins(v_actor,p_target_id,-greatest(abs(coalesce(p_amount,0)),1),'deduction',coalesce(v_reason,'Admin correction')); v_result:=jsonb_build_object('balance',v_balance);

  elsif p_feature_id=87 then
    v_balance:=private.admin_change_coins(v_actor,p_target_id,greatest(abs(coalesce(p_amount,0)),1),'refund',coalesce(v_reason,'Admin refund')); v_result:=jsonb_build_object('balance',v_balance);

  elsif p_feature_id=88 then
    perform private.admin_assert_target_user(v_actor,p_target_id);
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select id,amount,transaction_type,reason,actor_id,created_at from private.admin_coin_transactions where user_id=p_target_id order by created_at desc limit 200) q;

  elsif p_feature_id=89 then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (
      select t.id,t.user_id,p.username,t.amount,t.transaction_type,t.reason,t.created_at from private.admin_coin_transactions t join public.profiles p on p.id=t.user_id where t.transaction_type ilike '%'||v_text||'%' or coalesce(t.reason,'') ilike '%'||v_text||'%' or p.username ilike '%'||v_text||'%' order by t.created_at desc limit 200
    ) q;

  elsif p_feature_id=90 then
    v_from:=nullif(p_extra->>'from','')::timestamptz; v_to:=nullif(p_extra->>'to','')::timestamptz;
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select id,user_id,amount,transaction_type,reason,created_at from private.admin_coin_transactions where (v_from is null or created_at>=v_from) and (v_to is null or created_at<=v_to) order by created_at desc limit 500) q;

  elsif p_feature_id=91 then
    select jsonb_build_object('total_coins',coalesce(sum(coins),0),'accounts',count(*)) into v_result from public.game_profiles;

  elsif p_feature_id=92 then
    select jsonb_build_object('coins_from_ads',coalesce(sum(amount),0),'rewards',count(*)) into v_result from public.game_rewards where lower(coalesce(source,'')) like '%ad%';

  elsif p_feature_id=93 then
    select jsonb_build_object('verification_coin_spend',coalesce(sum(amount),0),'payments',count(*)) into v_result from public.verification_payments where lower(coalesce(provider,'')) like '%coin%' and lower(coalesce(status,'')) in ('paid','success','completed');

  elsif p_feature_id=94 then
    update private.admin_system_config set value=to_jsonb(greatest(coalesce(p_amount,1),0)),updated_by=v_actor,updated_at=now() where key='coin_reward_value'; v_result:=jsonb_build_object('coin_reward_value',greatest(coalesce(p_amount,1),0));

  elsif p_feature_id=95 then
    update private.admin_system_config set value=to_jsonb(greatest(coalesce(p_amount,1000),0)),updated_by=v_actor,updated_at=now() where key='daily_coin_limit'; v_result:=jsonb_build_object('daily_coin_limit',greatest(coalesce(p_amount,1000),0));

  elsif p_feature_id=96 then
    update private.admin_system_config set value=coalesce(p_extra,'{}'::jsonb),updated_by=v_actor,updated_at=now() where key='coin_bonus'; v_result:=coalesce(p_extra,'{}'::jsonb);

  elsif p_feature_id=97 then
    if v_text='' then raise exception 'UNIVERSITY_REQUIRED_IN_TEXT'; end if;
    for v_uid in select id from public.profiles where university=v_text loop perform private.admin_change_coins(v_actor,v_uid,greatest(coalesce(p_amount,0),1),'university_campaign',coalesce(v_reason,'University campaign')); v_count:=v_count+1; end loop;
    v_result:=jsonb_build_object('users_updated',v_count,'university',v_text);

  elsif p_feature_id=98 then
    update private.admin_system_config set value=coalesce(p_extra,'{}'::jsonb),updated_by=v_actor,updated_at=now() where key='coin_event'; v_result:=coalesce(p_extra,'{}'::jsonb);

  elsif p_feature_id in (99,100) then
    perform private.admin_ensure_control(v_actor,p_target_id); update private.admin_user_controls set coin_frozen=(p_feature_id=99),updated_by=v_actor,updated_at=now() where user_id=p_target_id; v_result:=jsonb_build_object('coin_frozen',p_feature_id=99);

  elsif p_feature_id in (101,102,103,106,107,115,117,118,119) then
    if p_feature_id=101 then v_text:='BLUE'; elsif p_feature_id=102 then v_text:='GOLD'; elsif p_feature_id=103 then v_text:='NONE'; elsif p_feature_id=106 then v_text:='GOLD'; elsif p_feature_id=107 then v_text:='BLUE'; elsif p_feature_id=115 then v_text:=upper(coalesce(nullif(p_extra->>'badge',''),'BLUE')); elsif p_feature_id=117 then v_text:='GOLD'; elsif p_feature_id=118 then v_text:='BLUE'; else v_text:='GOLD'; end if;
    v_exp:=private.admin_set_verification_internal(v_actor,p_target_id,v_text,v_hours); v_result:=jsonb_build_object('badge',v_text,'expires_at',v_exp);

  elsif p_feature_id=104 then
    perform private.admin_assert_target_user(v_actor,p_target_id); update public.profiles set verification_expires_at=now()+make_interval(hours=>v_hours) where id=p_target_id and is_verified; if not found then raise exception 'TARGET_NOT_VERIFIED'; end if; v_result:=jsonb_build_object('expires_at',now()+make_interval(hours=>v_hours));

  elsif p_feature_id=105 then
    perform private.admin_assert_target_user(v_actor,p_target_id); update public.profiles set verification_expires_at=greatest(coalesce(verification_expires_at,now()),now())+make_interval(hours=>v_hours) where id=p_target_id and is_verified; if not found then raise exception 'TARGET_NOT_VERIFIED'; end if; select jsonb_build_object('expires_at',verification_expires_at) into v_result from public.profiles where id=p_target_id;

  elsif p_feature_id in (108,109) then
    update public.verification_requests set status=case when p_feature_id=108 then 'approved' else 'rejected' end,review_notes=coalesce(nullif(v_text,''),review_notes),reviewed_at=now(),reviewed_by=v_actor where id=p_target_id returning user_id into v_uid;
    if not found then raise exception 'VERIFICATION_REQUEST_NOT_FOUND'; end if;
    if p_feature_id=108 then v_exp:=private.admin_set_verification_internal(v_actor,v_uid,upper(coalesce(nullif(p_extra->>'badge',''),'BLUE')),v_hours); end if;
    v_result:=jsonb_build_object('request_id',p_target_id,'user_id',v_uid,'status',case when p_feature_id=108 then 'approved' else 'rejected' end);

  elsif p_feature_id=110 then
    if p_target_id is not null then perform private.admin_assert_target_user(v_actor,p_target_id); end if;
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select vr.id,vr.user_id,p.username,vr.verification_type,vr.status,vr.review_notes,vr.submitted_at,vr.reviewed_at from public.verification_requests vr join public.profiles p on p.id=vr.user_id where p_target_id is null or vr.user_id=p_target_id order by vr.submitted_at desc limit 200) q;

  elsif p_feature_id=111 then
    if v_text='' then raise exception 'REJECTION_REASON_REQUIRED'; end if; update public.verification_requests set review_notes=v_text,status='rejected',reviewed_at=now(),reviewed_by=v_actor where id=p_target_id; if not found then raise exception 'VERIFICATION_REQUEST_NOT_FOUND'; end if; v_result:=jsonb_build_object('reason_saved',true);

  elsif p_feature_id=112 then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select id,username,verification_badge,verification_expires_at from public.profiles where is_verified and verification_expires_at is not null order by verification_expires_at asc limit 200) q;

  elsif p_feature_id=113 then
    update public.profiles set verification_badge='NONE',verification_tier='None'::public.verification_tier_enum,is_verified=false,verified_at=null,verification_expires_at=null where is_verified and verification_expires_at<=now() and not private.is_blink_owner_id(id); get diagnostics v_count=row_count; v_result:=jsonb_build_object('expired_removed',v_count);

  elsif p_feature_id=114 then
    for rec in select id,verification_expires_at from public.profiles where is_verified and verification_expires_at between now() and now()+interval '7 days' loop perform private.admin_send_system_notification(rec.id,'Verification expiring','Your Blink verification will expire soon.',null); v_count:=v_count+1; end loop; v_result:=jsonb_build_object('reminders_sent',v_count);

  elsif p_feature_id=116 then
    if jsonb_typeof(p_extra->'user_ids')<>'array' then raise exception 'USER_IDS_ARRAY_REQUIRED'; end if;
    for v_uid in select value::uuid from jsonb_array_elements_text(p_extra->'user_ids') loop perform private.admin_set_verification_internal(v_actor,v_uid,upper(coalesce(nullif(p_extra->>'badge',''),'BLUE')),v_hours); v_count:=v_count+1; end loop; v_result:=jsonb_build_object('users_verified',v_count);

  elsif p_feature_id=120 then
    perform private.admin_ensure_control(v_actor,p_target_id); update private.admin_user_controls set suspicious=true,under_review=true,updated_by=v_actor,updated_at=now() where user_id=p_target_id; v_result:=jsonb_build_object('suspicious',true,'under_review',true);

  elsif p_feature_id between 121 and 126 then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (
      select fp.id,fp.user_id,p.username,p.university,fp.text,fp.caption,fp.is_reel,fp.is_active,fp.is_flagged,fp.is_pinned,fp.is_sponsored,fp.like_count,fp.comment_count,fp.share_count,fp.view_count,fp.created_at
      from public.feed_posts fp join public.profiles p on p.id=fp.user_id
      where case p_feature_id when 121 then coalesce(fp.text,fp.caption,'') ilike '%'||v_text||'%' when 122 then fp.is_reel and coalesce(fp.text,fp.caption,'') ilike '%'||v_text||'%' when 123 then fp.user_id=p_target_id when 124 then p.university ilike '%'||v_text||'%' when 125 then coalesce(fp.text,'') ilike '%'||v_text||'%' or coalesce(fp.caption,'') ilike '%'||v_text||'%' when 126 then exists(select 1 from unnest(coalesce(fp.hashtags,'{}'::text[])) h where h ilike '%'||v_text||'%') end
      order by fp.created_at desc limit 100
    ) q;

  elsif p_feature_id between 127 and 139 then
    if p_feature_id not in (133,134) and p_target_id is null then raise exception 'POST_ID_REQUIRED'; end if;
    if p_feature_id=127 then update public.feed_posts set is_active=false,is_flagged=true,updated_at=now() where id=p_target_id;
    elsif p_feature_id=128 then update public.feed_posts set is_active=true,is_flagged=false,updated_at=now() where id=p_target_id;
    elsif p_feature_id=129 then update public.feed_posts set is_active=false,is_flagged=true,allow_comments=false,updated_at=now() where id=p_target_id;
    elsif p_feature_id=130 then update public.feed_posts set is_pinned=true,updated_at=now() where id=p_target_id;
    elsif p_feature_id=131 then update public.feed_posts set is_pinned=false,updated_at=now() where id=p_target_id;
    elsif p_feature_id=132 then update public.feed_posts fp set is_pinned=true,updated_at=now() where fp.id=p_target_id and exists(select 1 from public.profiles p where p.id=fp.user_id and p.university=v_text);
    elsif p_feature_id in (133,134) then update public.feed_posts set is_pinned=true,visibility_weight=greatest(coalesce(visibility_weight,0),1000),updated_at=now() where user_id=v_owner;
    elsif p_feature_id=135 then update public.feed_posts set is_pinned=true,visibility_weight=greatest(coalesce(visibility_weight,0),100),updated_at=now() where id=p_target_id;
    elsif p_feature_id=136 then update public.feed_posts set is_sponsored=true,ad_label='Sponsored',updated_at=now() where id=p_target_id;
    elsif p_feature_id=137 then update public.feed_posts set is_sponsored=false,ad_label=null,ad_cta=null,updated_at=now() where id=p_target_id;
    elsif p_feature_id=138 then
      update public.post_ads set status='active',starts_at=now(),ends_at=now()+make_interval(hours=>v_hours),updated_at=now() where id=(select id from public.post_ads where post_id=p_target_id order by created_at desc limit 1);
      if not found then insert into public.post_ads(post_id,advertiser_id,status,budget,spent,starts_at,ends_at,target) values(p_target_id,v_owner,'active',0,0,now(),now()+make_interval(hours=>v_hours),'{}'::jsonb); end if;
      update public.feed_posts set is_sponsored=true,ad_label='Sponsored',updated_at=now() where id=p_target_id;
    else update public.feed_posts set visibility_weight=greatest(coalesce(p_amount,12),1),updated_at=now() where id=p_target_id; end if;
    get diagnostics v_count=row_count; v_result:=jsonb_build_object('updated',v_count);

  elsif p_feature_id=140 then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select id,user_id,text,caption,is_reel,report_count,created_at from public.feed_posts where is_flagged or report_count>0 order by report_count desc,created_at desc limit 200) q;

  elsif p_feature_id=141 then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select r.id as report_id,r.reason,r.status,r.created_at,fp.id as post_id,fp.user_id,fp.caption,fp.text from public.reports r join public.feed_posts fp on fp.id=r.reported_post_id where fp.is_reel order by r.created_at desc limit 200) q;

  elsif p_feature_id in (142,143) then
    update public.feed_posts set allow_comments=(p_feature_id=143),updated_at=now() where id=p_target_id; if not found then raise exception 'POST_NOT_FOUND'; end if; v_result:=jsonb_build_object('allow_comments',p_feature_id=143);

  elsif p_feature_id in (144,145,146) then
    if p_target_id is null then raise exception 'COMMENT_ID_REQUIRED'; end if;
    if p_feature_id in (144,146) then
      insert into private.admin_comment_state(comment_id,original_content,is_removed,is_hidden,updated_by,updated_at) select c.id,c.content,p_feature_id=144,p_feature_id=146,v_actor,now() from public.comments c where c.id=p_target_id on conflict(comment_id) do update set original_content=coalesce(private.admin_comment_state.original_content,excluded.original_content),is_removed=excluded.is_removed,is_hidden=excluded.is_hidden,updated_by=v_actor,updated_at=now();
      update public.comments set content=case when p_feature_id=144 then '[Removed by Blink]' else '[Hidden by Blink]' end,updated_at=now() where id=p_target_id;
    else
      update public.comments c set content=s.original_content,updated_at=now() from private.admin_comment_state s where c.id=p_target_id and s.comment_id=c.id and s.original_content is not null;
      update private.admin_comment_state set is_removed=false,is_hidden=false,updated_by=v_actor,updated_at=now() where comment_id=p_target_id;
    end if;
    v_result:=jsonb_build_object('comment_id',p_target_id,'state',case when p_feature_id=144 then 'removed' when p_feature_id=146 then 'hidden' else 'restored' end);

  elsif p_feature_id=147 then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select r.id,r.reporter_id,r.reported_comment_id,r.reason,r.status,r.created_at from public.reports r where r.reported_comment_id is not null order by r.created_at desc limit 200) q;

  elsif p_feature_id=148 then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select r.id,r.reporter_id,r.reported_user_id,p.username,r.reason,r.status,r.created_at from public.reports r join public.profiles p on p.id=r.reported_user_id where r.reported_user_id is not null order by r.created_at desc limit 200) q;

  elsif p_feature_id=149 then
    select jsonb_build_object('messages',coalesce((select jsonb_agg(to_jsonb(q)) from (select mr.id,mr.message_id,mr.reporter_id,mr.reason,mr.created_at from public.message_reports mr order by mr.created_at desc limit 100) q),'[]'::jsonb),'conversations',coalesce((select jsonb_agg(to_jsonb(q2)) from (select cr.id,cr.conversation_id,cr.reporter_id,cr.reason,cr.created_at from public.conversation_reports cr order by cr.created_at desc limit 100) q2),'[]'::jsonb)) into v_result;

  elsif p_feature_id in (150,151,152) then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (
      select r.id,r.reason,r.status,r.reported_user_id,r.reported_post_id,r.reported_comment_id,r.created_at,coalesce(s.severity,'normal') as severity,s.escalated,s.assigned_to,p.university
      from public.reports r left join private.admin_report_state s on s.report_id=r.id left join public.profiles p on p.id=r.reported_user_id
      where (p_feature_id<>150 or lower(coalesce(r.status,'pending')) not in ('resolved','closed')) and (p_feature_id<>152 or coalesce(p.university,'') ilike '%'||v_text||'%')
      order by case when p_feature_id=151 then case coalesce(s.severity,'normal') when 'critical' then 4 when 'high' then 3 when 'normal' then 2 else 1 end else 0 end desc,r.created_at desc limit 300
    ) q;

  elsif p_feature_id=153 then
    if p_target_id is null or nullif(p_extra->>'admin_id','') is null then raise exception 'REPORT_ID_AND_ADMIN_ID_REQUIRED'; end if;
    v_uid:=(p_extra->>'admin_id')::uuid; if not exists(select 1 from private.admin_roles where user_id=v_uid and role in ('admin','owner')) then raise exception 'ASSIGNEE_IS_NOT_ADMIN'; end if;
    insert into private.admin_report_state(report_id,assigned_to,updated_by,updated_at) values(p_target_id,v_uid,v_actor,now()) on conflict(report_id) do update set assigned_to=v_uid,updated_by=v_actor,updated_at=now(); v_result:=jsonb_build_object('assigned_to',v_uid);

  elsif p_feature_id=154 then
    update public.reports set status='resolved' where id=p_target_id; if not found then raise exception 'REPORT_NOT_FOUND'; end if;
    insert into private.admin_report_state(report_id,resolved_at,updated_by,updated_at) values(p_target_id,now(),v_actor,now()) on conflict(report_id) do update set resolved_at=now(),updated_by=v_actor,updated_at=now(); v_result:=jsonb_build_object('resolved',true);

  elsif p_feature_id=155 then
    insert into private.admin_report_state(report_id,severity,escalated,updated_by,updated_at) values(p_target_id,'critical',true,v_actor,now()) on conflict(report_id) do update set severity='critical',escalated=true,updated_by=v_actor,updated_at=now(); v_result:=jsonb_build_object('severity','critical','escalated',true);

  elsif p_feature_id=156 then
    if v_text='' then raise exception 'MODERATION_NOTE_REQUIRED'; end if;
    insert into private.admin_report_state(report_id,admin_notes,updated_by,updated_at) values(p_target_id,v_text,v_actor,now()) on conflict(report_id) do update set admin_notes=v_text,updated_by=v_actor,updated_at=now(); v_result:=jsonb_build_object('note_saved',true);

  elsif p_feature_id=157 then
    select jsonb_build_object('total_reports',(select count(*) from public.reports),'open_reports',(select count(*) from public.reports where lower(coalesce(status,'pending')) not in ('resolved','closed')),'critical',(select count(*) from private.admin_report_state where severity='critical'),'flagged_posts',(select count(*) from public.feed_posts where is_flagged),'reported_comments',(select count(*) from public.reports where reported_comment_id is not null),'reported_users',(select count(*) from public.reports where reported_user_id is not null)) into v_result;

  elsif p_feature_id=158 then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select fp.user_id,p.username,count(*) as posts_last_hour from public.feed_posts fp join public.profiles p on p.id=fp.user_id where fp.created_at>now()-interval '1 hour' group by fp.user_id,p.username having count(*)>=greatest(coalesce(p_amount,5),3) order by count(*) desc limit 100) q;

  elsif p_feature_id=159 then
    select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select fp.user_id,p.username,coalesce(fp.text,fp.caption,'') as content,count(*) as repeats from public.feed_posts fp join public.profiles p on p.id=fp.user_id where fp.created_at>now()-interval '7 days' and coalesce(fp.text,fp.caption,'')<>'' group by fp.user_id,p.username,coalesce(fp.text,fp.caption,'') having count(*)>1 order by count(*) desc limit 100) q;

  elsif p_feature_id=160 then
    update private.admin_system_config set value=to_jsonb(greatest(coalesce(p_amount,30),1)),updated_by=v_actor,updated_at=now() where key='max_posts_per_hour'; v_result:=jsonb_build_object('max_posts_per_hour',greatest(coalesce(p_amount,30),1));

  elsif p_feature_id between 161 and 169 then
    if v_text='' then raise exception 'NOTIFICATION_MESSAGE_REQUIRED_IN_TEXT'; end if;
    insert into private.admin_notification_campaigns(sender_id,title,message,audience,status,scheduled_at) values(v_actor,coalesce(nullif(p_extra->>'title',''),'Blink'),v_text,case p_feature_id when 161 then jsonb_build_object('type','user','user_id',p_target_id) when 162 then jsonb_build_object('type','users','user_ids',coalesce(p_extra->'user_ids','[]'::jsonb)) when 163 then jsonb_build_object('type','university','university',p_extra->>'university') when 164 then jsonb_build_object('type','universities','universities',coalesce(p_extra->'universities','[]'::jsonb)) when 165 then jsonb_build_object('type','all') when 166 then jsonb_build_object('type','blue') when 167 then jsonb_build_object('type','gold') when 168 then jsonb_build_object('type','verified') else jsonb_build_object('type','unverified') end,'draft',now()) returning id into v_campaign;
    v_count:=private.dispatch_admin_campaign(v_campaign); v_result:=jsonb_build_object('campaign_id',v_campaign,'delivered',v_count);

  elsif p_feature_id=170 then
    if v_text='' then raise exception 'NOTIFICATION_MESSAGE_REQUIRED_IN_TEXT'; end if;
    insert into private.admin_notification_campaigns(sender_id,title,message,image_url,action_label,action_url,link_type,link_id,audience,scheduled_at,status) values(v_actor,coalesce(nullif(p_extra->>'title',''),'Blink'),v_text,p_extra->>'image_url',p_extra->>'action_label',p_extra->>'action_url',p_extra->>'link_type',nullif(p_extra->>'link_id','')::uuid,coalesce(p_extra->'audience','{"type":"all"}'::jsonb),coalesce(nullif(p_extra->>'scheduled_at','')::timestamptz,now()+make_interval(hours=>v_hours)),'scheduled') returning id into v_campaign;
    v_result:=jsonb_build_object('campaign_id',v_campaign,'scheduled',true);

  elsif p_feature_id=171 then
    update private.admin_notification_campaigns set status='cancelled',updated_at=now() where id=p_target_id and status in ('draft','scheduled','failed'); if not found then raise exception 'CAMPAIGN_NOT_CANCELLABLE'; end if; v_result:=jsonb_build_object('cancelled',true);

  elsif p_feature_id=172 then
    update private.admin_notification_campaigns set message=coalesce(nullif(v_text,''),message),audience=coalesce(p_extra->'audience',audience),scheduled_at=coalesce(nullif(p_extra->>'scheduled_at','')::timestamptz,scheduled_at),status=case when status='cancelled' then status else 'scheduled' end,updated_at=now() where id=p_target_id and status in ('draft','scheduled','failed'); if not found then raise exception 'CAMPAIGN_NOT_EDITABLE'; end if; v_result:=jsonb_build_object('edited',true);

  elsif p_feature_id=173 then
    update private.admin_notification_campaigns set title=left(v_text,120),updated_at=now() where id=p_target_id and status<>'sent'; if not found then raise exception 'CAMPAIGN_NOT_EDITABLE'; end if; v_result:=jsonb_build_object('title',v_text);

  elsif p_feature_id=174 then
    update private.admin_notification_campaigns set image_url=nullif(v_text,''),updated_at=now() where id=p_target_id and status<>'sent'; if not found then raise exception 'CAMPAIGN_NOT_EDITABLE'; end if; v_result:=jsonb_build_object('image_url',nullif(v_text,''));

  elsif p_feature_id=175 then
    update private.admin_notification_campaigns set action_label=p_extra->>'label',action_url=p_extra->>'url',updated_at=now() where id=p_target_id and status<>'sent'; if not found then raise exception 'CAMPAIGN_NOT_EDITABLE'; end if; v_result:=jsonb_build_object('action_label',p_extra->>'label','action_url',p_extra->>'url');

  elsif p_feature_id between 176 and 178 then
    update private.admin_notification_campaigns set link_type=case p_feature_id when 176 then 'post' when 177 then 'profile' else 'marketplace' end,link_id=nullif(p_extra->>'link_id','')::uuid,updated_at=now() where id=p_target_id and status<>'sent'; if not found then raise exception 'CAMPAIGN_NOT_EDITABLE'; end if; v_result:=jsonb_build_object('link_type',case p_feature_id when 176 then 'post' when 177 then 'profile' else 'marketplace' end,'link_id',p_extra->>'link_id');

  elsif p_feature_id in (179,180) then
    select case p_feature_id when 179 then jsonb_build_object('delivered_count',delivered_count,'status',status) else jsonb_build_object('opened_count',opened_count,'delivered_count',delivered_count,'open_rate',case when delivered_count>0 then round(opened_count::numeric/delivered_count*100,2) else 0 end) end into v_result from private.admin_notification_campaigns where id=p_target_id; if v_result is null then raise exception 'CAMPAIGN_NOT_FOUND'; end if;

  elsif p_feature_id between 181 and 196 then
    if p_feature_id=181 then select jsonb_build_object('active_users',count(*)) into v_result from public.profiles where coalesce(last_seen_at,last_seen)>now()-interval '15 minutes';
    elsif p_feature_id=182 then select jsonb_build_object('daily_active_users',count(*)) into v_result from public.profiles where coalesce(last_seen_at,last_seen)>now()-interval '1 day';
    elsif p_feature_id=183 then select jsonb_build_object('weekly_active_users',count(*)) into v_result from public.profiles where coalesce(last_seen_at,last_seen)>now()-interval '7 days';
    elsif p_feature_id=184 then select jsonb_build_object('monthly_active_users',count(*)) into v_result from public.profiles where coalesce(last_seen_at,last_seen)>now()-interval '30 days';
    elsif p_feature_id=185 then select jsonb_build_object('registrations_today',count(*),'registrations_7d',(select count(*) from public.profiles where created_at>now()-interval '7 days')) into v_result from public.profiles where created_at>date_trunc('day',now());
    elsif p_feature_id=186 then select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select coalesce(nullif(university,''),'Unknown') as university,count(*) as users from public.profiles group by 1 order by count(*) desc limit 100) q;
    elsif p_feature_id=187 then select jsonb_build_object('posts_today',count(*)) into v_result from public.feed_posts where created_at>date_trunc('day',now()) and not is_reel;
    elsif p_feature_id=188 then select jsonb_build_object('reels_today',count(*)) into v_result from public.feed_posts where created_at>date_trunc('day',now()) and is_reel;
    elsif p_feature_id=189 then select jsonb_build_object('messages_total',count(*),'messages_today',(select count(*) from public.messages where created_at>date_trunc('day',now()))) into v_result from public.messages;
    elsif p_feature_id=190 then select jsonb_build_object('comments_total',count(*),'comments_today',(select count(*) from public.comments where created_at>date_trunc('day',now()))) into v_result from public.comments;
    elsif p_feature_id=191 then select jsonb_build_object('likes_total',count(*),'likes_today',(select count(*) from public.post_likes where created_at>date_trunc('day',now()))) into v_result from public.post_likes;
    elsif p_feature_id=192 then select jsonb_build_object('shares_total',count(*),'shares_today',(select count(*) from public.post_shares where created_at>date_trunc('day',now()))) into v_result from public.post_shares;
    elsif p_feature_id=193 then select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select id,user_id,text,caption,like_count,comment_count,share_count,view_count,engagement_score,created_at from public.feed_posts where is_active and not is_reel order by coalesce(engagement_score,0) desc,created_at desc limit 50) q;
    elsif p_feature_id=194 then select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select id,user_id,caption,like_count,comment_count,share_count,view_count,engagement_score,created_at from public.feed_posts where is_active and is_reel order by coalesce(engagement_score,0) desc,created_at desc limit 50) q;
    elsif p_feature_id=195 then select coalesce(jsonb_agg(to_jsonb(q)),'[]'::jsonb) into v_result from (select coalesce(nullif(university,''),'Unknown') as university,count(*) as users,sum(coalesce(follower_count,0)) as followers from public.profiles group by 1 order by count(*) desc,sum(coalesce(follower_count,0)) desc limit 50) q;
    else select jsonb_build_object('server_time',now(),'profiles',(select count(*) from public.profiles),'active_admins',(select count(*) from private.admin_roles where role='owner' or (role='admin' and (expires_at is null or expires_at>now()) and (suspended_until is null or suspended_until<=now()))),'failed_push_dispatches',(select count(*) from public.message_push_dispatches where status='failed'),'pending_scheduled_posts',(select count(*) from public.scheduled_feed_posts where status in ('pending','scheduled')),'pending_admin_campaigns',(select count(*) from private.admin_notification_campaigns where status='scheduled'),'maintenance_mode',private.admin_config_bool('maintenance_mode',false)) into v_result; end if;

  elsif p_feature_id=197 then
    v_enabled:=coalesce((p_extra->>'enabled')::boolean,case when lower(v_text) in ('off','false','0') then false else true end); update private.admin_system_config set value=to_jsonb(v_enabled),updated_by=v_actor,updated_at=now() where key='maintenance_mode'; v_result:=jsonb_build_object('maintenance_mode',v_enabled);

  elsif p_feature_id=198 then
    if v_text not in ('registrations_enabled','posting_enabled','reels_enabled','comments_enabled','messaging_enabled','marketplace_enabled','verification_enabled','coin_rewards_enabled','ads_enabled') then raise exception 'INVALID_FEATURE_TOGGLE_KEY'; end if;
    v_enabled:=coalesce((p_extra->>'enabled')::boolean,true); update private.admin_system_config set value=to_jsonb(v_enabled),updated_by=v_actor,updated_at=now() where key=v_text; v_result:=jsonb_build_object('key',v_text,'enabled',v_enabled);

  elsif p_feature_id=199 then
    if v_text='' then raise exception 'REQUIRED_APP_VERSION_REQUIRED'; end if; update private.admin_system_config set value=to_jsonb(v_text),updated_by=v_actor,updated_at=now() where key='required_app_version'; v_result:=jsonb_build_object('required_app_version',v_text);

  elsif p_feature_id=200 then
    if lower(v_text)='restore' then
      update private.admin_system_config set value='false'::jsonb,updated_by=v_actor,updated_at=now() where key='maintenance_mode';
      update private.admin_system_config set value='true'::jsonb,updated_by=v_actor,updated_at=now() where key in ('registrations_enabled','posting_enabled','reels_enabled','comments_enabled','messaging_enabled','marketplace_enabled','verification_enabled','coin_rewards_enabled','ads_enabled');
      v_result:=jsonb_build_object('emergency_mode','restored');
    else
      update private.admin_system_config set value='true'::jsonb,updated_by=v_actor,updated_at=now() where key='maintenance_mode';
      update private.admin_system_config set value='false'::jsonb,updated_by=v_actor,updated_at=now() where key in ('registrations_enabled','posting_enabled','reels_enabled','comments_enabled','messaging_enabled','marketplace_enabled','verification_enabled','coin_rewards_enabled','ads_enabled');
      v_result:=jsonb_build_object('emergency_mode','lockdown');
    end if;
  else raise exception 'UNKNOWN_ADMIN_FEATURE';
  end if;

  perform private.admin_log_action(v_actor,'feature_'||p_feature_id::text,case when p_feature_id<=120 and p_feature_id not in (108,109,111) then p_target_id else null end,case when p_feature_id between 121 and 143 then p_target_id else null end,jsonb_build_object('title',(select title from private.admin_feature_catalog where feature_id=p_feature_id),'target_id',p_target_id,'text',left(v_text,500),'amount',p_amount,'duration_hours',p_duration_hours,'extra',coalesce(p_extra,'{}'::jsonb)));
  return jsonb_build_object('feature_id',p_feature_id,'ok',true,'result',coalesce(v_result,'{}'::jsonb));
end;
$function$
