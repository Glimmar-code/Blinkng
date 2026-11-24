-- Server-side Broadcast authoring guardrails and reward config validation.
-- Client UI validation is not trusted. These checks protect every write path.

CREATE OR REPLACE FUNCTION public.broadcast_validate_row()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE v_kind text; v_channels jsonb; v_audience jsonb; v_tz boolean;
BEGIN
  IF length(btrim(coalesce(NEW.title,'')))=0 OR length(NEW.title)>60 OR length(coalesce(NEW.body,''))>240 THEN RAISE EXCEPTION 'BROADCAST_CONTENT_LIMIT'; END IF;
  IF NEW.starts_at IS NOT NULL AND NEW.ends_at IS NOT NULL AND NEW.ends_at<=NEW.starts_at THEN RAISE EXCEPTION 'INVALID_BROADCAST_WINDOW'; END IF;
  IF NEW.deep_link IS NOT NULL AND NEW.deep_link !~ '^blink://(spin|market|contest|campaign/[0-9a-fA-F-]{36}|promo/[0-9a-fA-F-]{36}|wallet|profile/[0-9a-fA-F-]{36})$' THEN RAISE EXCEPTION 'INVALID_DEEP_LINK'; END IF;
  IF NEW.store_url IS NOT NULL AND NEW.store_url !~ '^https://' THEN RAISE EXCEPTION 'STORE_URL_MUST_BE_HTTPS'; END IF;
  IF NEW.image_url IS NOT NULL AND NEW.image_url !~ '^https://' THEN RAISE EXCEPTION 'IMAGE_URL_MUST_BE_HTTPS'; END IF;
  IF NEW.cta_label IS NOT NULL AND length(NEW.cta_label)>32 THEN RAISE EXCEPTION 'CTA_LABEL_TOO_LONG'; END IF;
  SELECT EXISTS(SELECT 1 FROM pg_timezone_names WHERE name=NEW.timezone) INTO v_tz;
  IF NOT coalesce(v_tz,false) THEN RAISE EXCEPTION 'INVALID_TIMEZONE'; END IF;
  v_channels:=coalesce(NEW.channels,'{}'::jsonb);
  IF jsonb_typeof(v_channels)<>'object' THEN RAISE EXCEPTION 'INVALID_CHANNELS'; END IF;
  IF coalesce((v_channels->>'frequency_cap')::integer,3) NOT BETWEEN 1 AND 20 THEN RAISE EXCEPTION 'INVALID_FREQUENCY_CAP'; END IF;
  IF coalesce(v_channels->>'quiet_hours_respect','true') NOT IN ('true','false') THEN RAISE EXCEPTION 'INVALID_QUIET_HOURS'; END IF;
  v_audience:=coalesce(NEW.audience,'{"kind":"everyone"}'::jsonb);
  IF jsonb_typeof(v_audience)<>'object' THEN RAISE EXCEPTION 'INVALID_AUDIENCE'; END IF;
  v_kind:=lower(coalesce(v_audience->>'kind','everyone'));
  IF v_kind NOT IN ('everyone','campus','campuses','new_users','inactive_7_days','inactive_30_days','sellers','verified','app_version') THEN RAISE EXCEPTION 'UNSUPPORTED_AUDIENCE_KIND'; END IF;
  IF v_audience ? 'campus_ids' AND jsonb_typeof(v_audience->'campus_ids')<>'array' THEN RAISE EXCEPTION 'INVALID_CAMPUS_FILTER'; END IF;
  IF coalesce(jsonb_array_length(v_audience->'campus_ids'),0)>1000 THEN RAISE EXCEPTION 'CAMPUS_FILTER_TOO_LARGE'; END IF;
  IF coalesce((v_audience->>'new_users_days')::integer,0)<0 OR coalesce((v_audience->>'new_users_days')::integer,0)>3650 THEN RAISE EXCEPTION 'INVALID_NEW_USER_FILTER'; END IF;
  IF coalesce((v_audience->>'inactive_days')::integer,0)<0 OR coalesce((v_audience->>'inactive_days')::integer,0)>365 THEN RAISE EXCEPTION 'INVALID_INACTIVE_FILTER'; END IF;
  IF v_audience ? 'min_app_version' AND v_audience->>'min_app_version' !~ '^[0-9]+\.[0-9]+\.[0-9]+([+-][0-9A-Za-z.-]+)?$' THEN RAISE EXCEPTION 'INVALID_MIN_APP_VERSION'; END IF;
  IF v_audience ? 'max_app_version' AND v_audience->>'max_app_version' !~ '^[0-9]+\.[0-9]+\.[0-9]+([+-][0-9A-Za-z.-]+)?$' THEN RAISE EXCEPTION 'INVALID_MAX_APP_VERSION'; END IF;
  IF NEW.is_blocking AND NEW.block_kind='force_update' AND (coalesce(NEW.min_app_version,'')='' OR coalesce(NEW.store_url,'')='') THEN RAISE EXCEPTION 'FORCE_UPDATE_REQUIRES_VERSION_AND_STORE_URL'; END IF;
  IF NEW.is_blocking AND NEW.block_kind='maintenance' AND coalesce(NEW.body,'')='' THEN RAISE EXCEPTION 'MAINTENANCE_MESSAGE_REQUIRED'; END IF;
  IF NOT NEW.is_blocking AND NEW.block_kind IS NOT NULL THEN RAISE EXCEPTION 'BLOCK_KIND_NOT_ALLOWED'; END IF;
  IF NEW.min_app_version IS NOT NULL AND NEW.min_app_version !~ '^[0-9]+\.[0-9]+\.[0-9]+([+-][0-9A-Za-z.-]+)?$' THEN RAISE EXCEPTION 'INVALID_MIN_APP_VERSION'; END IF;
  RETURN NEW;
END
$$;
DROP TRIGGER IF EXISTS broadcast_validate_row_guard ON public.broadcasts;
CREATE TRIGGER broadcast_validate_row_guard BEFORE INSERT OR UPDATE ON public.broadcasts
FOR EACH ROW EXECUTE FUNCTION public.broadcast_validate_row();

CREATE OR REPLACE FUNCTION public.broadcast_reward_config_guard()
RETURNS trigger
LANGUAGE plpgsql
SET search_path=''
AS $$
DECLARE v_tier jsonb; v_threshold integer; v_previous integer:=0; v_reward_type text; v_amount numeric;
BEGIN
  IF TG_TABLE_NAME='campaign_rules' THEN
    IF jsonb_typeof(NEW.tiers)<>'array' OR jsonb_array_length(NEW.tiers)>10 THEN RAISE EXCEPTION 'INVALID_CAMPAIGN_TIERS'; END IF;
    FOR v_tier IN SELECT value FROM jsonb_array_elements(NEW.tiers) LOOP
      v_threshold:=coalesce((v_tier->>'threshold')::integer,0);
      v_reward_type:=coalesce(v_tier->>'reward_type','');
      v_amount:=coalesce((v_tier->>'amount')::numeric,0);
      IF v_threshold<=v_previous OR v_threshold>NEW.target THEN RAISE EXCEPTION 'CAMPAIGN_TIERS_MUST_BE_ASCENDING_AND_WITHIN_TARGET'; END IF;
      IF v_reward_type NOT IN ('coins','free_spin','fee_discount','boost') THEN RAISE EXCEPTION 'INVALID_CAMPAIGN_REWARD_TYPE'; END IF;
      IF v_amount<=0 OR v_amount>1000000 THEN RAISE EXCEPTION 'CAMPAIGN_REWARD_AMOUNT_LIMIT'; END IF;
      IF v_reward_type='coins' AND (v_amount<>floor(v_amount) OR v_amount>1000000) THEN RAISE EXCEPTION 'INVALID_COIN_REWARD'; END IF;
      IF v_reward_type='free_spin' AND (v_amount<>floor(v_amount) OR v_amount>100) THEN RAISE EXCEPTION 'INVALID_FREE_SPIN_REWARD'; END IF;
      IF v_reward_type='boost' AND v_amount NOT IN (1,2,3,5) THEN RAISE EXCEPTION 'INVALID_BOOST_REWARD'; END IF;
      IF v_reward_type='fee_discount' AND v_amount>100 THEN RAISE EXCEPTION 'INVALID_FEE_DISCOUNT'; END IF;
      v_previous:=v_threshold;
    END LOOP;
  ELSIF TG_TABLE_NAME='promo_rules' THEN
    IF NEW.reward_amount<=0 OR NEW.reward_amount>1000000 OR NEW.per_user_limit NOT BETWEEN 1 AND 1000 OR NEW.min_purchase NOT BETWEEN 0 AND 100000000 OR (NEW.total_budget_cap IS NOT NULL AND NEW.total_budget_cap NOT BETWEEN 0 AND 100000000) THEN RAISE EXCEPTION 'INVALID_PROMO_LIMITS'; END IF;
    IF NEW.reward_type='coins' AND (NEW.reward_amount<>floor(NEW.reward_amount) OR NEW.reward_amount>1000000) THEN RAISE EXCEPTION 'INVALID_PROMO_COIN_AMOUNT'; END IF;
    IF NEW.reward_type='free_spin' AND (NEW.reward_amount<>floor(NEW.reward_amount) OR NEW.reward_amount>100) THEN RAISE EXCEPTION 'INVALID_PROMO_FREE_SPIN'; END IF;
    IF NEW.reward_type='boost' AND NEW.reward_amount NOT IN (1,2,3,5) THEN RAISE EXCEPTION 'INVALID_PROMO_BOOST'; END IF;
    IF NEW.reward_type='fee_discount' AND NEW.reward_amount>100 THEN RAISE EXCEPTION 'INVALID_PROMO_FEE_DISCOUNT'; END IF;
    IF coalesce(array_length(NEW.placement,1),0)>0 AND EXISTS(SELECT 1 FROM unnest(NEW.placement) p WHERE p NOT IN ('feed','market','coin_purchase','spin')) THEN RAISE EXCEPTION 'INVALID_PROMO_PLACEMENT'; END IF;
    IF NEW.total_budget_cap IS NOT NULL AND NEW.budget_used>NEW.total_budget_cap THEN RAISE EXCEPTION 'BUDGET_CAP_BELOW_SPENT'; END IF;
  END IF;
  RETURN NEW;
END
$$;
DROP TRIGGER IF EXISTS broadcast_campaign_reward_config_guard ON public.campaign_rules;
CREATE TRIGGER broadcast_campaign_reward_config_guard BEFORE INSERT OR UPDATE OF target,tiers ON public.campaign_rules
FOR EACH ROW EXECUTE FUNCTION public.broadcast_reward_config_guard();
DROP TRIGGER IF EXISTS broadcast_promo_reward_config_guard ON public.promo_rules;
CREATE TRIGGER broadcast_promo_reward_config_guard BEFORE INSERT OR UPDATE OF reward_type,reward_amount,per_user_limit,total_budget_cap,min_purchase,placement,budget_used ON public.promo_rules
FOR EACH ROW EXECUTE FUNCTION public.broadcast_reward_config_guard();

-- Every audience=everyone create/edit must explicitly carry confirmation.
CREATE OR REPLACE FUNCTION public.admin_create_broadcast(p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE actor uuid:=private.require_blink_admin(); bid uuid; t public.broadcast_type; st public.broadcast_status; aud jsonb; ch jsonb; v_rules jsonb;
BEGIN
  PERFORM public.broadcast_require_current_app();
  t:=(p_payload->>'type')::public.broadcast_type; st:=coalesce((p_payload->>'status')::public.broadcast_status,'draft');
  aud:=coalesce(p_payload->'audience','{"kind":"everyone"}'::jsonb); ch:=coalesce(p_payload->'channels','{"in_app":true}'::jsonb);
  IF coalesce(aud->>'kind','everyone')='everyone' AND coalesce((p_payload->>'confirm_everyone')::boolean,false) IS NOT TRUE THEN RAISE EXCEPTION 'CONFIRM_EVERYONE_REQUIRED'; END IF;
  IF length(btrim(coalesce(p_payload->>'title','')))=0 OR length(p_payload->>'title')>60 OR length(coalesce(p_payload->>'body',''))>240 THEN RAISE EXCEPTION 'CONTENT_TOO_LONG'; END IF;
  IF p_payload->>'ends_at' IS NOT NULL AND p_payload->>'starts_at' IS NOT NULL AND (p_payload->>'ends_at')::timestamptz<=(p_payload->>'starts_at')::timestamptz THEN RAISE EXCEPTION 'INVALID_WINDOW'; END IF;
  IF coalesce((ch->>'frequency_cap')::integer,3) NOT BETWEEN 1 AND 20 THEN RAISE EXCEPTION 'INVALID_FREQUENCY_CAP'; END IF;
  IF coalesce((p_payload->>'is_blocking')::boolean,false) AND coalesce(p_payload->>'block_kind','') NOT IN ('maintenance','force_update') THEN RAISE EXCEPTION 'BLOCK_KIND_REQUIRED'; END IF;
  IF NOT coalesce((p_payload->>'is_blocking')::boolean,false) AND p_payload->>'block_kind' IS NOT NULL THEN RAISE EXCEPTION 'BLOCK_KIND_NOT_ALLOWED'; END IF;
  IF st='live' AND coalesce((ch->>'push')::boolean,false) AND NOT public.broadcast_admin_publish_reserve(10) THEN RAISE EXCEPTION 'ADMIN_PUBLISH_RATE_LIMIT'; END IF;
  INSERT INTO public.broadcasts(type,status,title,body,image_url,cta_label,deep_link,priority,starts_at,ends_at,timezone,dismissible,is_blocking,block_kind,min_app_version,store_url,audience,channels,created_by,updated_by)
  VALUES(t,st,btrim(p_payload->>'title'),coalesce(p_payload->>'body',''),p_payload->>'image_url',p_payload->>'cta_label',p_payload->>'deep_link',coalesce((p_payload->>'priority')::int,0),
    (p_payload->>'starts_at')::timestamptz,(p_payload->>'ends_at')::timestamptz,coalesce(p_payload->>'timezone','Africa/Lagos'),coalesce((p_payload->>'dismissible')::boolean,true),
    coalesce((p_payload->>'is_blocking')::boolean,false),(p_payload->>'block_kind')::public.broadcast_block_kind,p_payload->>'min_app_version',p_payload->>'store_url',aud,ch,actor,actor)
  RETURNING id INTO bid;
  IF t='CAMPAIGN' THEN
    v_rules:=coalesce(p_payload->'tiers','[]'::jsonb);
    IF jsonb_typeof(v_rules)<>'array' OR jsonb_array_length(v_rules)>10 THEN RAISE EXCEPTION 'INVALID_CAMPAIGN_TIERS'; END IF;
    INSERT INTO public.campaign_rules(broadcast_id,goal_type,target,tiers) VALUES(bid,(p_payload->>'goal_type')::public.broadcast_goal_type,(p_payload->>'target')::integer,v_rules);
  ELSIF t='PROMO' THEN
    INSERT INTO public.promo_rules(broadcast_id,reward_type,reward_amount,per_user_limit,total_budget_cap,min_purchase,reward_text,placement)
    VALUES(bid,(p_payload->>'reward_type')::public.broadcast_reward_type,(p_payload->>'reward_amount')::numeric,coalesce((p_payload->>'per_user_limit')::integer,1),
      (p_payload->>'total_budget_cap')::numeric,coalesce((p_payload->>'min_purchase')::numeric,0),p_payload->>'reward_text',coalesce(array(select jsonb_array_elements_text(p_payload->'placement')),'{}'));
  END IF;
  PERFORM public.broadcast_log(actor,'create',bid,null,(SELECT to_jsonb(b) FROM public.broadcasts b WHERE b.id=bid));
  RETURN jsonb_build_object('id',bid,'version',1);
END
$$;

CREATE OR REPLACE FUNCTION public.admin_update_broadcast(p_id uuid,p_version integer,p_patch jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE actor uuid:=private.require_blink_admin(); oldrow public.broadcasts%rowtype; newrow public.broadcasts%rowtype; ch jsonb; aud jsonb; v_start timestamptz; v_end timestamptz;
BEGIN
  PERFORM public.broadcast_require_current_app();
  SELECT * INTO oldrow FROM public.broadcasts WHERE id=p_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'NOT_FOUND'; END IF;
  IF oldrow.version<>p_version THEN RAISE EXCEPTION 'VERSION_CONFLICT'; END IF;
  IF length(coalesce(p_patch->>'title',oldrow.title))>60 OR length(coalesce(p_patch->>'body',oldrow.body))>240 THEN RAISE EXCEPTION 'CONTENT_TOO_LONG'; END IF;
  aud:=coalesce(p_patch->'audience',oldrow.audience);
  IF coalesce(aud->>'kind','everyone')='everyone' AND coalesce((p_patch->>'confirm_everyone')::boolean,false) IS NOT TRUE THEN RAISE EXCEPTION 'CONFIRM_EVERYONE_REQUIRED'; END IF;
  ch:=coalesce(p_patch->'channels',oldrow.channels);
  IF coalesce((ch->>'frequency_cap')::integer,3) NOT BETWEEN 1 AND 20 THEN RAISE EXCEPTION 'INVALID_FREQUENCY_CAP'; END IF;
  v_start:=coalesce((p_patch->>'starts_at')::timestamptz,oldrow.starts_at); v_end:=case when p_patch ? 'ends_at' then (p_patch->>'ends_at')::timestamptz else oldrow.ends_at end;
  IF v_start IS NOT NULL AND v_end IS NOT NULL AND v_end<=v_start THEN RAISE EXCEPTION 'INVALID_WINDOW'; END IF;
  IF coalesce((p_patch->>'is_blocking')::boolean,oldrow.is_blocking) AND coalesce(p_patch->>'block_kind',oldrow.block_kind::text) NOT IN ('maintenance','force_update') THEN RAISE EXCEPTION 'BLOCK_KIND_REQUIRED'; END IF;
  IF coalesce((p_patch->>'is_blocking')::boolean,oldrow.is_blocking) AND coalesce(p_patch->>'block_kind',oldrow.block_kind::text)='force_update'
     AND coalesce(p_patch->>'min_app_version',oldrow.min_app_version,'')='' THEN RAISE EXCEPTION 'FORCE_UPDATE_VERSION_REQUIRED'; END IF;
  IF coalesce((p_patch->>'status')::public.broadcast_status,oldrow.status)='live' AND oldrow.status<>'live' AND coalesce((ch->>'push')::boolean,false) AND NOT public.broadcast_admin_publish_reserve(10) THEN RAISE EXCEPTION 'ADMIN_PUBLISH_RATE_LIMIT'; END IF;
  UPDATE public.broadcasts SET title=coalesce(p_patch->>'title',title),body=coalesce(p_patch->>'body',body),status=coalesce((p_patch->>'status')::public.broadcast_status,status),
    image_url=case when p_patch ? 'image_url' then p_patch->>'image_url' else image_url end,cta_label=case when p_patch ? 'cta_label' then p_patch->>'cta_label' else cta_label end,
    deep_link=case when p_patch ? 'deep_link' then p_patch->>'deep_link' else deep_link end,priority=coalesce((p_patch->>'priority')::int,priority),
    starts_at=case when p_patch ? 'starts_at' then (p_patch->>'starts_at')::timestamptz else starts_at end,
    ends_at=case when p_patch ? 'ends_at' then (p_patch->>'ends_at')::timestamptz else ends_at end,
    timezone=coalesce(p_patch->>'timezone',timezone),dismissible=coalesce((p_patch->>'dismissible')::boolean,dismissible),
    is_blocking=coalesce((p_patch->>'is_blocking')::boolean,is_blocking),
    block_kind=case when coalesce((p_patch->>'is_blocking')::boolean,is_blocking)=false then null when p_patch ? 'block_kind' then (p_patch->>'block_kind')::public.broadcast_block_kind else block_kind end,
    min_app_version=case when p_patch ? 'min_app_version' then p_patch->>'min_app_version' else min_app_version end,
    store_url=case when p_patch ? 'store_url' then p_patch->>'store_url' else store_url end,
    audience=aud,channels=ch,version=version+1,updated_by=actor,updated_at=now()
    WHERE id=p_id RETURNING * INTO newrow;
  IF oldrow.type='CAMPAIGN' AND p_patch ? 'campaign_rules' THEN
    UPDATE public.campaign_rules SET goal_type=(p_patch->'campaign_rules'->>'goal_type')::public.broadcast_goal_type,
      target=(p_patch->'campaign_rules'->>'target')::integer,tiers=coalesce(p_patch->'campaign_rules'->'tiers',tiers),updated_at=now() WHERE broadcast_id=p_id;
  END IF;
  IF oldrow.type='PROMO' AND p_patch ? 'promo_rules' THEN
    UPDATE public.promo_rules SET reward_type=(p_patch->'promo_rules'->>'reward_type')::public.broadcast_reward_type,
      reward_amount=(p_patch->'promo_rules'->>'reward_amount')::numeric,per_user_limit=coalesce((p_patch->'promo_rules'->>'per_user_limit')::integer,per_user_limit),
      total_budget_cap=case when p_patch->'promo_rules' ? 'total_budget_cap' then (p_patch->'promo_rules'->>'total_budget_cap')::numeric else total_budget_cap end,
      min_purchase=coalesce((p_patch->'promo_rules'->>'min_purchase')::numeric,min_purchase),reward_text=coalesce(p_patch->'promo_rules'->>'reward_text',reward_text),
      placement=coalesce(array(select jsonb_array_elements_text(p_patch->'promo_rules'->'placement')),placement),updated_at=now() WHERE broadcast_id=p_id;
  END IF;
  PERFORM public.broadcast_log(actor,'update',p_id,to_jsonb(oldrow),to_jsonb(newrow));
  RETURN to_jsonb(newrow);
END
$$;

-- Legacy admin aliases bypass confirmation/rules payload shape; use the repository contract RPCs only.
REVOKE EXECUTE ON FUNCTION public.create_broadcast(jsonb),public.update_broadcast(uuid,integer,jsonb),public.end_broadcast(uuid),
  public.delete_broadcast(uuid),public.duplicate_broadcast(uuid),public.estimate_broadcast_audience(jsonb,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.admin_create_broadcast(jsonb),public.admin_update_broadcast(uuid,integer,jsonb) TO authenticated;
