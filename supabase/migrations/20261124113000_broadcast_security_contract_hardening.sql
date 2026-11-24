-- Broadcast security and contract hardening.
-- Reuse existing profiles, private admin roles, wallets, coin ledger and university identifiers.
-- Apply only after the schema, function recovery and access migrations in Testlab.

ALTER TABLE public.broadcast_daily_rollups
  ADD COLUMN IF NOT EXISTS dismissals bigint NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS conversions bigint NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS claims bigint NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE OR REPLACE FUNCTION public.broadcast_app_version_allowed(p_version text DEFAULT NULL)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE
  v_version text := nullif(trim(p_version),'');
  v_min text;
  v_force_min text;
BEGIN
  IF v_version IS NULL THEN
    BEGIN
      v_version := nullif(trim((current_setting('request.headers',true)::jsonb)->>'x-blink-app-version'),'');
    EXCEPTION WHEN OTHERS THEN v_version := null;
    END;
  END IF;
  SELECT min_app_version INTO v_min FROM public.broadcast_app_config WHERE id=true;
  SELECT min_app_version INTO v_force_min
    FROM public.broadcasts
   WHERE status='live' AND deleted_at IS NULL AND is_blocking=true
     AND block_kind='force_update'
     AND (starts_at IS NULL OR starts_at<=now())
     AND (ends_at IS NULL OR ends_at>now())
   ORDER BY priority DESC,starts_at DESC LIMIT 1;
  IF coalesce(v_min,'')='' AND coalesce(v_force_min,'')='' THEN RETURN true; END IF;
  IF v_version IS NULL THEN RETURN false; END IF;
  IF v_min IS NOT NULL AND v_min<>'' THEN
    IF public.broadcast_semver_compare(v_version,v_min) IS NULL OR public.broadcast_semver_compare(v_version,v_min)<0 THEN RETURN false; END IF;
  END IF;
  IF v_force_min IS NOT NULL AND v_force_min<>'' THEN
    IF public.broadcast_semver_compare(v_version,v_force_min) IS NULL OR public.broadcast_semver_compare(v_version,v_force_min)<0 THEN RETURN false; END IF;
  END IF;
  RETURN true;
END
$$;

CREATE OR REPLACE FUNCTION public.broadcast_writes_allowed()
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $
  SELECT public.broadcast_app_version_allowed(NULL)
    AND NOT coalesce((public.get_broadcast_blocking_state()->>'maintenance')::boolean,false)
$;
CREATE OR REPLACE FUNCTION public.broadcast_require_current_app()
RETURNS void LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $
BEGIN
  IF coalesce((public.get_broadcast_blocking_state()->>'maintenance')::boolean,false) THEN
    RAISE EXCEPTION 'MAINTENANCE_MODE' USING ERRCODE='55000';
  END IF;
  IF NOT public.broadcast_app_version_allowed(NULL) THEN
    RAISE EXCEPTION 'FORCE_UPDATE_REQUIRED' USING ERRCODE='55000';
  END IF;
END
$;

CREATE OR REPLACE FUNCTION public.broadcast_audience_match(p_user uuid,p_audience jsonb,p_app_version text DEFAULT NULL)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE
  prof public.profiles%rowtype;
  campus_arr jsonb;
  kind text;
  n integer;
  cmp integer;
  v_version text := nullif(trim(p_app_version),'');
BEGIN
  IF p_user IS NULL THEN RETURN false; END IF;
  SELECT * INTO prof FROM public.profiles WHERE id=p_user;
  IF NOT FOUND THEN RETURN false; END IF;
  kind := lower(coalesce(p_audience->>'kind','everyone'));
  campus_arr := coalesce(p_audience->'campus_ids',p_audience->'university_ids','[]'::jsonb);
  IF kind IN ('campus','campuses') OR (jsonb_typeof(campus_arr)='array' AND jsonb_array_length(campus_arr)>0) THEN
    IF jsonb_typeof(campus_arr)<>'array' OR jsonb_array_length(campus_arr)=0
       OR coalesce(prof.university_id,prof.university) IS NULL
       OR NOT campus_arr ? coalesce(prof.university_id,prof.university) THEN
      RETURN false;
    END IF;
  END IF;
  IF kind='verified' OR coalesce((p_audience->>'verified')::boolean,false) THEN
    IF NOT coalesce(prof.is_verified,false) THEN RETURN false; END IF;
  END IF;
  IF kind='sellers' OR coalesce((p_audience->>'sellers')::boolean,false) THEN
    IF NOT coalesce(prof.is_seller_active,false) THEN RETURN false; END IF;
  END IF;
  IF kind='new_users' OR p_audience ? 'new_users_days' THEN
    n:=greatest(0,coalesce((p_audience->>'new_users_days')::integer,30));
    IF prof.created_at<now()-make_interval(days=>n) THEN RETURN false; END IF;
  END IF;
  IF kind IN ('inactive_7_days','inactive_30_days') OR p_audience ? 'inactive_days' THEN
    n:=coalesce((p_audience->>'inactive_days')::integer,
      CASE kind WHEN 'inactive_7_days' THEN 7 WHEN 'inactive_30_days' THEN 30 ELSE 7 END);
    IF n<1 OR n>365 THEN RETURN false; END IF;
    IF prof.last_active_at IS NOT NULL AND prof.last_active_at>now()-make_interval(days=>n) THEN RETURN false; END IF;
  END IF;
  IF p_audience ? 'min_app_version' THEN
    cmp:=public.broadcast_semver_compare(v_version,p_audience->>'min_app_version');
    IF cmp IS NULL OR cmp<0 THEN RETURN false; END IF;
  END IF;
  IF p_audience ? 'max_app_version' THEN
    cmp:=public.broadcast_semver_compare(v_version,p_audience->>'max_app_version');
    IF cmp IS NULL OR cmp>0 THEN RETURN false; END IF;
  END IF;
  RETURN true;
END
$$;

CREATE OR REPLACE FUNCTION public.broadcast_audience_match_self(p_audience jsonb)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path=''
AS $$
  SELECT public.broadcast_audience_match(
    auth.uid(),
    p_audience,
    nullif((current_setting('request.headers',true)::jsonb)->>'x-blink-app-version','')
  )
$$;

CREATE OR REPLACE FUNCTION public.get_broadcast_blocking_state()
RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $
  SELECT jsonb_build_object(
    'maintenance',coalesce(c.maintenance_enabled,false) OR m.id IS NOT NULL,
    'message',coalesce(m.body,c.maintenance_message),'maintenance_broadcast_id',m.id,
    'min_app_version',c.min_app_version,'store_url',c.store_url,
    'force_update',coalesce(f.force_update,false),'force_update_message',f.body,
    'blocking_broadcast_id',f.id,'force_update_min_app_version',f.min_app_version,'force_update_store_url',f.store_url)
  FROM public.broadcast_app_config c
  LEFT JOIN LATERAL (
    SELECT b.id,b.body FROM public.broadcasts b
    WHERE b.status='live' AND b.deleted_at IS NULL AND b.is_blocking=true AND b.block_kind='maintenance'
      AND (b.starts_at IS NULL OR b.starts_at<=now()) AND (b.ends_at IS NULL OR b.ends_at>now())
    ORDER BY b.priority DESC,b.starts_at DESC LIMIT 1
  ) m ON true
  LEFT JOIN LATERAL (
    SELECT b.id,b.body,b.min_app_version,b.store_url,true force_update FROM public.broadcasts b
    WHERE b.status='live' AND b.deleted_at IS NULL AND b.is_blocking=true AND b.block_kind='force_update'
      AND (b.starts_at IS NULL OR b.starts_at<=now()) AND (b.ends_at IS NULL OR b.ends_at>now())
    ORDER BY b.priority DESC,b.starts_at DESC LIMIT 1
  ) f ON true
  WHERE c.id=true
$;

CREATE OR REPLACE FUNCTION public.broadcast_log(p_actor uuid,p_action text,p_broadcast uuid,p_before jsonb,p_after jsonb)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE v_ip inet;
BEGIN
  BEGIN
    v_ip:=nullif((current_setting('request.headers',true)::jsonb)->>'x-forwarded-for','')::inet;
  EXCEPTION WHEN OTHERS THEN v_ip:=null;
  END;
  INSERT INTO public.broadcast_audit_log(actor_id,action,broadcast_id,before,after,ip)
  VALUES(p_actor,left(p_action,100),p_broadcast,p_before,p_after,v_ip);
END
$$;

CREATE OR REPLACE FUNCTION public.admin_broadcast_analytics(p_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE v_summary jsonb; v_daily jsonb; v_budget jsonb; v_audience jsonb; v_campaign jsonb;
BEGIN
  PERFORM private.require_blink_admin();
  IF NOT EXISTS(SELECT 1 FROM public.broadcasts WHERE id=p_id AND deleted_at IS NULL) THEN
    RAISE EXCEPTION 'BROADCAST_NOT_FOUND' USING ERRCODE='P0002';
  END IF;
  SELECT jsonb_build_object(
    'reach',count(distinct i.user_id),
    'impressions',count(i.id),
    'clicks',(SELECT count(*) FROM public.broadcast_clicks c WHERE c.broadcast_id=p_id),
    'ctr_pct',coalesce(round(100.0*(SELECT count(*) FROM public.broadcast_clicks c WHERE c.broadcast_id=p_id)/nullif(count(i.id),0),2),0),
    'dismissals',(SELECT count(*) FROM public.broadcast_dismissals d WHERE d.broadcast_id=p_id),
    'claims',(SELECT count(*) FROM public.promo_claims c WHERE c.broadcast_id=p_id AND c.status='claimed'),
    'conversions',(SELECT count(*) FROM public.campaign_participants cp WHERE cp.broadcast_id=p_id AND cp.completed_at IS NOT NULL)
  ) INTO v_summary FROM public.broadcast_impressions i WHERE i.broadcast_id=p_id;
  IF v_summary IS NULL THEN
    SELECT jsonb_build_object('reach',0,'impressions',0,'clicks',(SELECT count(*) FROM public.broadcast_clicks c WHERE c.broadcast_id=p_id),'ctr_pct',0,
      'dismissals',(SELECT count(*) FROM public.broadcast_dismissals d WHERE d.broadcast_id=p_id),
      'claims',(SELECT count(*) FROM public.promo_claims c WHERE c.broadcast_id=p_id AND c.status='claimed'),
      'conversions',(SELECT count(*) FROM public.campaign_participants cp WHERE cp.broadcast_id=p_id AND cp.completed_at IS NOT NULL))
    INTO v_summary;
  END IF;
  SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY r.day),'[]'::jsonb) INTO v_daily
    FROM public.broadcast_daily_rollups r WHERE r.broadcast_id=p_id;
  SELECT jsonb_build_object('used',budget_used,'cap',total_budget_cap,'remaining',greatest(0,total_budget_cap-budget_used))
    INTO v_budget FROM public.promo_rules WHERE broadcast_id=p_id;
  SELECT jsonb_build_object(
    'campuses',(SELECT coalesce(jsonb_object_agg(x.campus,x.n),'{}'::jsonb) FROM (
      SELECT coalesce(p.university_id,p.university,'unknown') campus,count(distinct p.id) n
      FROM public.broadcast_impressions i JOIN public.profiles p ON p.id=i.user_id
      WHERE i.broadcast_id=p_id GROUP BY 1) x),
    'verified',(SELECT count(distinct p.id) FROM public.broadcast_impressions i JOIN public.profiles p ON p.id=i.user_id WHERE i.broadcast_id=p_id AND p.is_verified),
    'sellers',(SELECT count(distinct p.id) FROM public.broadcast_impressions i JOIN public.profiles p ON p.id=i.user_id WHERE i.broadcast_id=p_id AND p.is_seller_active)
  ) INTO v_audience;
  SELECT jsonb_build_object('participants',count(*),'completed',count(*) filter(where completed_at is not null),
    'progress_total',coalesce(sum(progress),0)) INTO v_campaign
  FROM public.campaign_participants WHERE broadcast_id=p_id;
  RETURN jsonb_build_object('summary',v_summary,'budget',v_budget,'daily',v_daily,'audience_breakdown',v_audience,'campaign',v_campaign);
END
$$;

CREATE OR REPLACE FUNCTION public.admin_get_broadcast_audit(p_id uuid DEFAULT NULL,p_limit integer DEFAULT 100)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path=''
AS $$
BEGIN
  PERFORM private.require_blink_admin();
  RETURN coalesce((SELECT jsonb_agg(to_jsonb(a) ORDER BY a.created_at DESC)
    FROM (SELECT * FROM public.broadcast_audit_log WHERE p_id IS NULL OR broadcast_id=p_id
      ORDER BY created_at DESC LIMIT least(greatest(coalesce(p_limit,100),1),250)) a),'[]'::jsonb);
END
$$;

CREATE OR REPLACE FUNCTION public.admin_estimate_broadcast_audience(p_filters jsonb,p_app_version text DEFAULT NULL)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
BEGIN
  PERFORM private.require_blink_admin();
  RETURN (SELECT count(*) FROM public.profiles p WHERE public.broadcast_audience_match(p.id,p_filters,p_app_version));
END
$$;

CREATE OR REPLACE FUNCTION public.broadcast_admin_publish_reserve(p_limit integer DEFAULT 10)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE v_actor uuid:=private.require_blink_admin(); v_count bigint;
BEGIN
  PERFORM pg_advisory_xact_lock(hashtext(v_actor::text||':broadcast_publish'));
  SELECT count(*) INTO v_count FROM public.broadcast_audit_log
   WHERE actor_id=v_actor AND action='broadcast_publish' AND created_at>now()-interval '1 hour';
  IF v_count>=greatest(1,least(p_limit,10)) THEN RETURN false; END IF;
  INSERT INTO public.broadcast_audit_log(actor_id,action,after)
    VALUES(v_actor,'broadcast_publish',jsonb_build_object('limit',p_limit));
  RETURN true;
END
$$;

CREATE OR REPLACE FUNCTION public.admin_create_broadcast(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE actor uuid:=private.require_blink_admin(); bid uuid; t public.broadcast_type; st public.broadcast_status; aud jsonb; ch jsonb; v_rules jsonb;
BEGIN
  PERFORM public.broadcast_require_current_app();
  t:=(p_payload->>'type')::public.broadcast_type;
  st:=coalesce((p_payload->>'status')::public.broadcast_status,'draft');
  aud:=coalesce(p_payload->'audience','{"kind":"everyone"}'::jsonb);
  ch:=coalesce(p_payload->'channels','{"in_app":true}'::jsonb);
  IF coalesce(aud->>'kind','everyone')='everyone' AND coalesce((p_payload->>'confirm_everyone')::boolean,false) IS NOT TRUE THEN RAISE EXCEPTION 'CONFIRM_EVERYONE_REQUIRED'; END IF;
  IF length(btrim(coalesce(p_payload->>'title','')))=0 OR length(p_payload->>'title')>60 OR length(coalesce(p_payload->>'body',''))>240 THEN RAISE EXCEPTION 'CONTENT_TOO_LONG'; END IF;
  IF p_payload->>'ends_at' IS NOT NULL AND p_payload->>'starts_at' IS NOT NULL AND (p_payload->>'ends_at')::timestamptz<=(p_payload->>'starts_at')::timestamptz THEN RAISE EXCEPTION 'INVALID_WINDOW'; END IF;
  IF coalesce((ch->>'frequency_cap')::integer,3) NOT BETWEEN 1 AND 20 THEN RAISE EXCEPTION 'INVALID_FREQUENCY_CAP'; END IF;
  IF coalesce((p_payload->>'is_blocking')::boolean,false) AND coalesce(p_payload->>'block_kind','') NOT IN ('maintenance','force_update') THEN RAISE EXCEPTION 'BLOCK_KIND_REQUIRED'; END IF;
  IF NOT coalesce((p_payload->>'is_blocking')::boolean,false) AND p_payload->>'block_kind' IS NOT NULL THEN RAISE EXCEPTION 'BLOCK_KIND_NOT_ALLOWED'; END IF;
  IF st='live' AND coalesce((ch->>'push')::boolean,false) AND NOT public.broadcast_admin_publish_reserve(10) THEN RAISE EXCEPTION 'ADMIN_PUBLISH_RATE_LIMIT'; END IF;
  INSERT INTO public.broadcasts(type,status,title,body,image_url,cta_label,deep_link,priority,starts_at,ends_at,timezone,dismissible,is_blocking,block_kind,min_app_version,store_url,audience,channels,created_by,updated_by)
  VALUES(t,st,btrim(p_payload->>'title'),coalesce(p_payload->>'body',''),p_payload->>'image_url',p_payload->>'cta_label',p_payload->>'deep_link',
    coalesce((p_payload->>'priority')::int,0),(p_payload->>'starts_at')::timestamptz,(p_payload->>'ends_at')::timestamptz,
    coalesce(p_payload->>'timezone','Africa/Lagos'),coalesce((p_payload->>'dismissible')::boolean,true),coalesce((p_payload->>'is_blocking')::boolean,false),
    (p_payload->>'block_kind')::public.broadcast_block_kind,p_payload->>'min_app_version',p_payload->>'store_url',aud,ch,actor,actor)
  RETURNING id INTO bid;
  IF t='CAMPAIGN' THEN
    v_rules:=coalesce(p_payload->'tiers','[]'::jsonb);
    IF jsonb_typeof(v_rules)<>'array' OR jsonb_array_length(v_rules)>10 THEN RAISE EXCEPTION 'INVALID_CAMPAIGN_TIERS'; END IF;
    INSERT INTO public.campaign_rules(broadcast_id,goal_type,target,tiers)
    VALUES(bid,(p_payload->>'goal_type')::public.broadcast_goal_type,(p_payload->>'target')::integer,v_rules);
  ELSIF t='PROMO' THEN
    INSERT INTO public.promo_rules(broadcast_id,reward_type,reward_amount,per_user_limit,total_budget_cap,min_purchase,reward_text,placement)
    VALUES(bid,(p_payload->>'reward_type')::public.broadcast_reward_type,(p_payload->>'reward_amount')::numeric,
      coalesce((p_payload->>'per_user_limit')::integer,1),(p_payload->>'total_budget_cap')::numeric,
      coalesce((p_payload->>'min_purchase')::numeric,0),p_payload->>'reward_text',
      coalesce(array(select jsonb_array_elements_text(p_payload->'placement')),'{}'));
  END IF;
  PERFORM public.broadcast_log(actor,'create',bid,null,(SELECT to_jsonb(b) FROM public.broadcasts b WHERE b.id=bid));
  RETURN jsonb_build_object('id',bid,'version',1);
END
$$;

CREATE OR REPLACE FUNCTION public.admin_update_broadcast(p_id uuid,p_version integer,p_patch jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE actor uuid:=private.require_blink_admin(); oldrow public.broadcasts%rowtype; newrow public.broadcasts%rowtype; ch jsonb; aud jsonb;
BEGIN
  PERFORM public.broadcast_require_current_app();
  SELECT * INTO oldrow FROM public.broadcasts WHERE id=p_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'NOT_FOUND'; END IF;
  IF oldrow.version<>p_version THEN RAISE EXCEPTION 'VERSION_CONFLICT'; END IF;
  IF length(coalesce(p_patch->>'title',oldrow.title))>60 OR length(coalesce(p_patch->>'body',oldrow.body))>240 THEN RAISE EXCEPTION 'CONTENT_TOO_LONG'; END IF;
  aud:=coalesce(p_patch->'audience',oldrow.audience);
  IF coalesce(aud->>'kind','everyone')='everyone' AND coalesce((p_patch->>'confirm_everyone')::boolean,false) IS NOT TRUE AND oldrow.audience->>'kind' IS DISTINCT FROM 'everyone' THEN RAISE EXCEPTION 'CONFIRM_EVERYONE_REQUIRED'; END IF;
  ch:=coalesce(p_patch->'channels',oldrow.channels);
  IF coalesce((ch->>'frequency_cap')::integer,3) NOT BETWEEN 1 AND 20 THEN RAISE EXCEPTION 'INVALID_FREQUENCY_CAP'; END IF;
  IF coalesce((p_patch->>'is_blocking')::boolean,oldrow.is_blocking) AND coalesce(p_patch->>'block_kind',oldrow.block_kind::text) NOT IN ('maintenance','force_update') THEN RAISE EXCEPTION 'BLOCK_KIND_REQUIRED'; END IF;
  IF coalesce((p_patch->>'status')::public.broadcast_status,oldrow.status)='live' AND oldrow.status<>'live' AND coalesce((ch->>'push')::boolean,false) AND NOT public.broadcast_admin_publish_reserve(10) THEN RAISE EXCEPTION 'ADMIN_PUBLISH_RATE_LIMIT'; END IF;
  UPDATE public.broadcasts SET
    title=coalesce(p_patch->>'title',title),body=coalesce(p_patch->>'body',body),status=coalesce((p_patch->>'status')::public.broadcast_status,status),
    image_url=case when p_patch ? 'image_url' then p_patch->>'image_url' else image_url end,
    cta_label=case when p_patch ? 'cta_label' then p_patch->>'cta_label' else cta_label end,
    deep_link=case when p_patch ? 'deep_link' then p_patch->>'deep_link' else deep_link end,
    priority=coalesce((p_patch->>'priority')::int,priority),
    starts_at=coalesce((p_patch->>'starts_at')::timestamptz,starts_at),
    ends_at=case when p_patch ? 'ends_at' then (p_patch->>'ends_at')::timestamptz else ends_at end,
    timezone=coalesce(p_patch->>'timezone',timezone),dismissible=coalesce((p_patch->>'dismissible')::boolean,dismissible),
    is_blocking=coalesce((p_patch->>'is_blocking')::boolean,is_blocking),
    block_kind=case
      when coalesce((p_patch->>'is_blocking')::boolean,is_blocking)=false then null
      when p_patch ? 'block_kind' then (p_patch->>'block_kind')::public.broadcast_block_kind
      else block_kind end,
    min_app_version=case when p_patch ? 'min_app_version' then p_patch->>'min_app_version' else min_app_version end,
    store_url=case when p_patch ? 'store_url' then p_patch->>'store_url' else store_url end,
    audience=aud,channels=ch,version=version+1,updated_by=actor,updated_at=now()
  WHERE id=p_id RETURNING * INTO newrow;
  IF oldrow.type='CAMPAIGN' AND p_patch ? 'campaign_rules' THEN
    UPDATE public.campaign_rules SET goal_type=(p_patch->'campaign_rules'->>'goal_type')::public.broadcast_goal_type,
      target=(p_patch->'campaign_rules'->>'target')::integer,tiers=coalesce(p_patch->'campaign_rules'->'tiers',tiers),updated_at=now()
    WHERE broadcast_id=p_id;
  END IF;
  IF oldrow.type='PROMO' AND p_patch ? 'promo_rules' THEN
    UPDATE public.promo_rules SET reward_type=(p_patch->'promo_rules'->>'reward_type')::public.broadcast_reward_type,
      reward_amount=(p_patch->'promo_rules'->>'reward_amount')::numeric,per_user_limit=coalesce((p_patch->'promo_rules'->>'per_user_limit')::integer,per_user_limit),
      total_budget_cap=case when p_patch->'promo_rules' ? 'total_budget_cap' then (p_patch->'promo_rules'->>'total_budget_cap')::numeric else total_budget_cap end,
      min_purchase=coalesce((p_patch->'promo_rules'->>'min_purchase')::numeric,min_purchase),
      reward_text=coalesce(p_patch->'promo_rules'->>'reward_text',reward_text),
      placement=coalesce(array(select jsonb_array_elements_text(p_patch->'promo_rules'->'placement')),placement),updated_at=now()
    WHERE broadcast_id=p_id;
  END IF;
  PERFORM public.broadcast_log(actor,'update',p_id,to_jsonb(oldrow),to_jsonb(newrow));
  RETURN to_jsonb(newrow);
END
$$;

CREATE OR REPLACE FUNCTION public.dismiss_broadcast(p_broadcast_id uuid)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE uid uuid:=auth.uid();
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
  PERFORM public.broadcast_require_current_app();
  IF NOT EXISTS(SELECT 1 FROM public.broadcasts b WHERE b.id=p_broadcast_id AND b.status='live' AND b.deleted_at IS NULL
    AND (b.starts_at IS NULL OR b.starts_at<=now()) AND (b.ends_at IS NULL OR b.ends_at>now())
    AND public.broadcast_audience_match(uid,b.audience,nullif((current_setting('request.headers',true)::jsonb)->>'x-blink-app-version',''))) THEN RETURN false; END IF;
  INSERT INTO public.broadcast_dismissals(user_id,broadcast_id) VALUES(uid,p_broadcast_id) ON CONFLICT DO NOTHING;
  RETURN FOUND;
END
$$;

CREATE OR REPLACE FUNCTION public.mark_read(p_broadcast_id uuid)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE uid uuid:=auth.uid();
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
  PERFORM public.broadcast_require_current_app();
  IF NOT EXISTS(SELECT 1 FROM public.broadcasts b WHERE b.id=p_broadcast_id AND b.status='live' AND b.deleted_at IS NULL
    AND (b.starts_at IS NULL OR b.starts_at<=now()) AND (b.ends_at IS NULL OR b.ends_at>now())
    AND public.broadcast_audience_match(uid,b.audience,nullif((current_setting('request.headers',true)::jsonb)->>'x-blink-app-version',''))) THEN RETURN false; END IF;
  INSERT INTO public.broadcast_reads(user_id,broadcast_id) VALUES(uid,p_broadcast_id) ON CONFLICT DO NOTHING;
  RETURN FOUND;
END
$$;

CREATE OR REPLACE FUNCTION public.mark_all_read()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE uid uuid:=auth.uid(); n integer;
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
  PERFORM public.broadcast_require_current_app();
  INSERT INTO public.broadcast_reads(user_id,broadcast_id)
  SELECT uid,b.id FROM public.broadcasts b
  WHERE b.status='live' AND b.deleted_at IS NULL
    AND (b.starts_at IS NULL OR b.starts_at<=now()) AND (b.ends_at IS NULL OR b.ends_at>now())
    AND public.broadcast_audience_match(uid,b.audience,nullif((current_setting('request.headers',true)::jsonb)->>'x-blink-app-version',''))
  ON CONFLICT DO NOTHING;
  GET DIAGNOSTICS n=ROW_COUNT; RETURN n;
END
$$;

CREATE OR REPLACE FUNCTION public.get_broadcast_inbox_keyset(p_tab text DEFAULT 'all',p_cursor_created_at timestamptz DEFAULT NULL,p_cursor_id uuid DEFAULT NULL,p_limit integer DEFAULT 20)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path=''
AS $$
SELECT coalesce(jsonb_agg(to_jsonb(q) ORDER BY q.created_at DESC,q.id DESC),'[]'::jsonb)
FROM (
  SELECT b.id,b.type,b.title,b.body,b.image_url,b.cta_label,b.deep_link,b.priority,b.created_at,b.starts_at,b.ends_at,
    (r.broadcast_id IS NOT NULL) is_read
  FROM public.broadcasts b
  LEFT JOIN public.broadcast_reads r ON r.broadcast_id=b.id AND r.user_id=auth.uid()
  WHERE auth.uid() IS NOT NULL AND b.status='live' AND b.deleted_at IS NULL
    AND (b.starts_at IS NULL OR b.starts_at<=now()) AND (b.ends_at IS NULL OR b.ends_at>now())
    AND public.broadcast_audience_match(auth.uid(),b.audience,nullif((current_setting('request.headers',true)::jsonb)->>'x-blink-app-version',''))
    AND (lower(coalesce(p_tab,'all'))='all' OR (lower(p_tab)='unread' AND r.broadcast_id IS NULL) OR lower(p_tab)=lower(b.type::text))
    AND (p_cursor_created_at IS NULL OR b.created_at<p_cursor_created_at OR (b.created_at=p_cursor_created_at AND b.id<p_cursor_id))
  ORDER BY b.created_at DESC,b.id DESC LIMIT least(greatest(coalesce(p_limit,20),1),50)
) q
$$;

CREATE OR REPLACE FUNCTION public.get_campaign_leaderboard(p_broadcast_id uuid)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path=''
AS $$
SELECT jsonb_build_object(
  'top',(SELECT coalesce(jsonb_agg(to_jsonb(x) ORDER BY x.progress DESC,x.joined_at ASC,x.user_id),'[]'::jsonb)
    FROM (SELECT cp.user_id,cp.progress,rank() OVER(ORDER BY cp.progress DESC) rank,cp.joined_at,p.name,p.handle,p.avatar_url
      FROM public.campaign_participants cp JOIN public.profiles p ON p.id=cp.user_id
      WHERE cp.broadcast_id=p_broadcast_id ORDER BY cp.progress DESC,cp.joined_at ASC,cp.user_id LIMIT 20) x),
  'me',(SELECT jsonb_build_object('rank',rank,'progress',progress) FROM (SELECT cp.user_id,cp.progress,rank() OVER(ORDER BY cp.progress DESC) rank
      FROM public.campaign_participants cp WHERE cp.broadcast_id=p_broadcast_id) r WHERE user_id=auth.uid())
)
$$;

CREATE OR REPLACE FUNCTION public.update_broadcast_prefs(p_patch jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE uid uuid:=auth.uid(); res jsonb;
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
  PERFORM public.broadcast_require_current_app();
  INSERT INTO public.notification_prefs(user_id) VALUES(uid) ON CONFLICT DO NOTHING;
  UPDATE public.notification_prefs SET
    push_enabled=coalesce((p_patch->>'push_enabled')::boolean,push_enabled),
    announcements=coalesce((p_patch->>'announcements')::boolean,announcements),
    campaigns=coalesce((p_patch->>'campaigns')::boolean,campaigns),
    offers=coalesce((p_patch->>'offers')::boolean,offers),
    email=coalesce((p_patch->>'email')::boolean,email),
    quiet_start=case when p_patch ? 'quiet_start' then (p_patch->>'quiet_start')::time else quiet_start end,
    quiet_end=case when p_patch ? 'quiet_end' then (p_patch->>'quiet_end')::time else quiet_end end,
    timezone=coalesce(p_patch->>'timezone',timezone),updated_at=now()
  WHERE user_id=uid RETURNING to_jsonb(notification_prefs) INTO res;
  RETURN res;
END
$$;

CREATE OR REPLACE FUNCTION public.register_broadcast_device_token(p_token text,p_platform text,p_app_version text DEFAULT NULL)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE uid uuid:=auth.uid(); outid uuid;
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
  PERFORM public.broadcast_require_current_app();
  IF length(p_token)<20 OR p_platform NOT IN ('android','ios','web') OR p_app_version IS NULL OR length(p_app_version)>40 THEN RAISE EXCEPTION 'INVALID_TOKEN'; END IF;
  INSERT INTO public.device_tokens(user_id,token,platform,app_version,last_seen_at,disabled_at)
  VALUES(uid,p_token,p_platform,p_app_version,now(),null)
  ON CONFLICT(token) DO UPDATE SET user_id=excluded.user_id,platform=excluded.platform,app_version=excluded.app_version,last_seen_at=now(),disabled_at=null
  RETURNING id INTO outid;
  RETURN outid;
END
$$;

CREATE OR REPLACE FUNCTION public.track_broadcast_impression(p_broadcast_id uuid,p_surface text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
  PERFORM public.broadcast_require_current_app();
  IF NOT public.broadcast_rate_limit('impression',90,60) THEN RETURN false; END IF;
  INSERT INTO public.broadcast_impressions(user_id,broadcast_id,surface)
  SELECT auth.uid(),b.id,left(coalesce(p_surface,'unknown'),50) FROM public.broadcasts b
  WHERE b.id=p_broadcast_id AND b.status='live' AND b.deleted_at IS NULL
    AND (b.starts_at IS NULL OR b.starts_at<=now()) AND (b.ends_at IS NULL OR b.ends_at>now())
    AND public.broadcast_audience_match(auth.uid(),b.audience,nullif((current_setting('request.headers',true)::jsonb)->>'x-blink-app-version',''))
  ON CONFLICT DO NOTHING;
  RETURN FOUND;
END
$$;

CREATE OR REPLACE FUNCTION public.track_broadcast_click(p_broadcast_id uuid,p_surface text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
  PERFORM public.broadcast_require_current_app();
  IF NOT public.broadcast_rate_limit('click',20,60) THEN RETURN false; END IF;
  INSERT INTO public.broadcast_clicks(user_id,broadcast_id,surface)
  SELECT auth.uid(),b.id,left(coalesce(p_surface,'unknown'),50) FROM public.broadcasts b
  WHERE b.id=p_broadcast_id AND b.status='live' AND b.deleted_at IS NULL
    AND (b.starts_at IS NULL OR b.starts_at<=now()) AND (b.ends_at IS NULL OR b.ends_at>now())
    AND public.broadcast_audience_match(auth.uid(),b.audience,nullif((current_setting('request.headers',true)::jsonb)->>'x-blink-app-version',''))
  ON CONFLICT DO NOTHING;
  RETURN FOUND;
END
$$;

CREATE OR REPLACE FUNCTION public.join_campaign(p_broadcast_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE uid uuid:=auth.uid(); was_joined boolean;
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
  PERFORM public.broadcast_require_current_app();
  IF NOT public.broadcast_rate_limit('join_campaign',10,60) THEN RETURN jsonb_build_object('ok',false,'error','RATE_LIMITED'); END IF;
  IF NOT EXISTS(SELECT 1 FROM public.broadcasts b JOIN public.campaign_rules cr ON cr.broadcast_id=b.id
    WHERE b.id=p_broadcast_id AND b.type='CAMPAIGN' AND b.status='live' AND b.deleted_at IS NULL
      AND (b.starts_at IS NULL OR b.starts_at<=now()) AND (b.ends_at IS NULL OR b.ends_at>now())
      AND public.broadcast_audience_match(uid,b.audience,nullif((current_setting('request.headers',true)::jsonb)->>'x-blink-app-version',''))) THEN
    RETURN jsonb_build_object('ok',false,'error','NOT_ELIGIBLE');
  END IF;
  INSERT INTO public.campaign_participants(broadcast_id,user_id) VALUES(p_broadcast_id,uid) ON CONFLICT DO NOTHING;
  was_joined:=FOUND;
  RETURN jsonb_build_object('ok',true,'joined',true,'idempotent',not was_joined);
END
$$;

-- Security-sensitive functions are not client RPCs. Trigger execution does not need client EXECUTE.
REVOKE EXECUTE ON FUNCTION public.broadcast_log(uuid,text,uuid,jsonb,jsonb) FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.record_broadcast_campaign_event(public.broadcast_goal_type,uuid,text,text,integer,timestamptz) FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_campaign_reward(uuid,uuid,integer) FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_audience_match(uuid,jsonb,text) FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_rate_limit(text,integer,integer) FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_lifecycle_tick() FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_purge_old_events() FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_push_cap_reserve(integer) FROM PUBLIC,anon;
REVOKE EXECUTE ON FUNCTION public.broadcast_audit_immutable() FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_rollup_trigger() FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_signal_trigger() FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_lifecycle_audit_trigger() FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_referral_campaign_trigger() FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_order_campaign_trigger() FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_vote_campaign_trigger() FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_last_active_trigger() FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_semver_compare(text,text) FROM PUBLIC,anon;
REVOKE EXECUTE ON FUNCTION public.broadcast_semver_at_least(text,text) FROM PUBLIC,anon;
REVOKE EXECUTE ON FUNCTION public.broadcast_admin_check() FROM PUBLIC,anon;
REVOKE EXECUTE ON FUNCTION public.broadcast_audience_match_self(jsonb) FROM PUBLIC,anon;
REVOKE EXECUTE ON FUNCTION public.broadcast_app_version_allowed(text) FROM PUBLIC,anon;
REVOKE EXECUTE ON FUNCTION public.broadcast_require_current_app(),public.broadcast_writes_allowed() FROM PUBLIC,anon;
REVOKE EXECUTE ON FUNCTION public.broadcast_require_current_app() FROM authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_admin_publish_reserve(integer) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.broadcast_app_version_allowed(text),public.broadcast_writes_allowed() TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.broadcast_admin_publish_reserve(integer) TO service_role;

GRANT EXECUTE ON FUNCTION public.broadcast_admin_check() TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.broadcast_audience_match(uuid,jsonb,text) TO service_role;
GRANT EXECUTE ON FUNCTION public.broadcast_audience_match_self(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.broadcast_semver_compare(text,text),public.broadcast_semver_at_least(text,text) TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.broadcast_push_cap_reserve(integer) TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.get_broadcast_blocking_state(),public.get_website_broadcasts() TO anon,authenticated;
GRANT EXECUTE ON FUNCTION public.get_active_broadcasts(text,text),public.dismiss_broadcast(uuid),public.mark_read(uuid),public.mark_all_read(),
  public.track_broadcast_impression(uuid,text),public.track_broadcast_click(uuid,text),public.join_campaign(uuid),
  public.get_campaign_progress(uuid),public.get_campaign_leaderboard(uuid),public.claim_promo(uuid,text,numeric),
  public.get_broadcast_inbox(text,timestamptz,integer),public.get_broadcast_inbox_keyset(text,timestamptz,uuid,integer),
  public.get_broadcast_prefs(),public.update_broadcast_prefs(jsonb),public.register_broadcast_device_token(text,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_create_broadcast(jsonb),public.admin_update_broadcast(uuid,integer,jsonb),
  public.admin_end_broadcast(uuid),public.admin_duplicate_broadcast(uuid),public.admin_delete_broadcast(uuid),
  public.admin_estimate_broadcast_audience(jsonb,text),public.admin_broadcast_analytics(uuid),public.admin_get_broadcast_audit(uuid,integer),
  public.admin_update_broadcast_config(jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.broadcast_log(uuid,text,uuid,jsonb,jsonb),public.record_broadcast_campaign_event(public.broadcast_goal_type,uuid,text,text,integer,timestamptz),
  public.broadcast_campaign_reward(uuid,uuid,integer),public.broadcast_rate_limit(text,integer,integer),
  public.broadcast_lifecycle_tick(),public.broadcast_purge_old_events() TO service_role;

-- Direct client table mutation is removed for privileged reward and admin tables.
REVOKE INSERT,UPDATE,DELETE ON public.broadcasts,public.campaign_rules,public.promo_rules,public.campaign_participants,
  public.campaign_progress_events,public.promo_claims,public.broadcast_impressions,public.broadcast_clicks,
  public.broadcast_audit_log,public.broadcast_daily_rollups,public.broadcast_deliveries,public.broadcast_rate_limits,
  public.broadcast_app_config FROM anon,authenticated;
REVOKE INSERT,UPDATE,DELETE ON public.user_balances,public.blink_coin_transactions FROM anon,authenticated;

-- App-version enforcement also protects direct owner-row writes that bypass RPCs.
DROP POLICY IF EXISTS dismiss_insert_own ON public.broadcast_dismissals;
CREATE POLICY dismiss_insert_own ON public.broadcast_dismissals FOR INSERT TO authenticated
  WITH CHECK(user_id=auth.uid() AND public.broadcast_writes_allowed());
DROP POLICY IF EXISTS reads_insert_own ON public.broadcast_reads;
CREATE POLICY reads_insert_own ON public.broadcast_reads FOR INSERT TO authenticated
  WITH CHECK(user_id=auth.uid() AND public.broadcast_app_version_allowed(NULL));
DROP POLICY IF EXISTS own_prefs_all ON public.notification_prefs;
CREATE POLICY own_prefs_all ON public.notification_prefs FOR ALL TO authenticated
  USING(user_id=auth.uid() AND public.broadcast_writes_allowed())
  WITH CHECK(user_id=auth.uid() AND public.broadcast_writes_allowed());
DROP POLICY IF EXISTS own_device_tokens_all ON public.device_tokens;
CREATE POLICY own_device_tokens_all ON public.device_tokens FOR ALL TO authenticated
  USING(user_id=auth.uid() AND public.broadcast_app_version_allowed(NULL))
  WITH CHECK(user_id=auth.uid() AND public.broadcast_app_version_allowed(NULL));

-- Function-specific grants are explicit; PUBLIC is never the default audience for a definer RPC.
REVOKE EXECUTE ON FUNCTION public.admin_broadcast_analytics(uuid),public.admin_get_broadcast_audit(uuid,integer),
  public.admin_estimate_broadcast_audience(jsonb,text),public.admin_create_broadcast(jsonb),public.admin_update_broadcast(uuid,integer,jsonb),
  public.admin_end_broadcast(uuid),public.admin_duplicate_broadcast(uuid),public.admin_delete_broadcast(uuid),
  public.admin_update_broadcast_config(jsonb),public.get_active_broadcasts(text,text),public.dismiss_broadcast(uuid),
  public.mark_read(uuid),public.mark_all_read(),public.track_broadcast_impression(uuid,text),public.track_broadcast_click(uuid,text),
  public.join_campaign(uuid),public.get_campaign_progress(uuid),public.get_campaign_leaderboard(uuid),public.claim_promo(uuid,text,numeric),
  public.get_broadcast_inbox(text,timestamptz,integer),public.get_broadcast_inbox_keyset(text,timestamptz,uuid,integer),
  public.get_broadcast_prefs(),public.update_broadcast_prefs(jsonb),public.register_broadcast_device_token(text,text,text),
  public.get_broadcast_blocking_state(),public.get_website_broadcasts() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.admin_broadcast_analytics(uuid),public.admin_get_broadcast_audit(uuid,integer),
  public.admin_estimate_broadcast_audience(jsonb,text),public.admin_create_broadcast(jsonb),public.admin_update_broadcast(uuid,integer,jsonb),
  public.admin_end_broadcast(uuid),public.admin_duplicate_broadcast(uuid),public.admin_delete_broadcast(uuid),
  public.admin_update_broadcast_config(jsonb) TO authenticated;
