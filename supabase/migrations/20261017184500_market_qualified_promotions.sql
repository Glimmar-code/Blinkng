-- BLINK Market: verified-seller sponsored placement and server-timed qualified views.
-- These promotions spend existing Blink Coins. Cash/ngn Boost settlement is NOT live.
-- Rollback: revoke new RPCs, remove Market impression trigger and archive view sessions.
BEGIN;
CREATE TABLE IF NOT EXISTS private.market_boost_view_sessions(
 campaign_id uuid NOT NULL REFERENCES public.blink_boost_campaigns_v2(id) ON DELETE CASCADE,
 viewer_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
 started_at timestamptz NOT NULL DEFAULT now(),
 qualified_at timestamptz,
 PRIMARY KEY(campaign_id,viewer_id)
);
REVOKE ALL ON private.market_boost_view_sessions FROM PUBLIC,anon,authenticated;

-- Safety and badge expiry must apply to promoted placements too.
CREATE OR REPLACE FUNCTION private.blink_boost_viewer_matches(
 p_campaign public.blink_boost_campaigns_v2,p_viewer uuid
) RETURNS boolean LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path=public,private,pg_temp AS $market_boost_valid$
DECLARE v_uni text;
BEGIN
 IF p_viewer IS NULL OR p_campaign.user_id=p_viewer
   OR p_campaign.status<>'ACTIVE' OR p_campaign.starts_at>now()
   OR p_campaign.ends_at<=now() OR p_campaign.coin_spent>=p_campaign.coin_budget
 THEN RETURN false; END IF;
 IF p_campaign.target_type='LISTING' AND NOT EXISTS(
   SELECT 1 FROM public.market_items m
   JOIN public.profiles p ON p.id=m.seller_id
   WHERE m.id=p_campaign.target_id AND m.seller_id=p_campaign.user_id
    AND m.status='active' AND NOT coalesce(m.is_sold,false)
    AND m.moderation_state='approved'
    AND p.is_seller_active AND p.seller_status='active' AND p.is_verified
    AND upper(p.verification_badge) IN('BLUE','GOLD')
    AND (p.verification_expires_at>now()
      OR (upper(p.verification_badge)='GOLD' AND p.verification_expires_at IS NULL))
 ) THEN RETURN false; END IF;
 IF p_campaign.audience_scope='ALL_CAMPUSES' THEN RETURN true; END IF;
 SELECT nullif(trim(coalesce(university,'')),'') INTO v_uni
 FROM public.profiles WHERE id=p_viewer;
 RETURN v_uni IS NOT NULL AND
   lower(v_uni)=lower(coalesce(p_campaign.target_university,''));
END;
$market_boost_valid$;
REVOKE ALL ON FUNCTION private.blink_boost_viewer_matches(public.blink_boost_campaigns_v2,uuid)
 FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.get_market_promoted_slots(
 p_surface text DEFAULT 'MARKET',p_limit integer DEFAULT 3
) RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path=public,private,pg_temp AS $market_promoted$
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
$market_promoted$;
REVOKE ALL ON FUNCTION public.get_market_promoted_slots(text,integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.get_market_promoted_slots(text,integer) TO authenticated;

CREATE OR REPLACE FUNCTION public.record_market_qualified_view(
 p_campaign uuid,p_stage text
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER
SET search_path=public,private,pg_temp AS $market_view$
DECLARE v_viewer uuid:=auth.uid();v_campaign public.blink_boost_campaigns_v2%ROWTYPE;
DECLARE v_session private.market_boost_view_sessions%ROWTYPE;v_delivered jsonb;
BEGIN
 IF v_viewer IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
 IF upper(p_stage) NOT IN ('START','QUALIFIED') THEN RAISE EXCEPTION 'INVALID_VIEW_STAGE'; END IF;
 SELECT * INTO v_campaign FROM public.blink_boost_campaigns_v2 WHERE id=p_campaign;
 IF NOT FOUND OR v_campaign.target_type<>'LISTING'
   OR NOT private.blink_boost_viewer_matches(v_campaign,v_viewer)
 THEN RAISE EXCEPTION 'MARKET_CAMPAIGN_NOT_AVAILABLE'; END IF;
 IF upper(p_stage)='START' THEN
   INSERT INTO private.market_boost_view_sessions(campaign_id,viewer_id)
   VALUES(p_campaign,v_viewer) ON CONFLICT DO NOTHING;
   RETURN jsonb_build_object('started',true,'charged',false);
 END IF;
 SELECT * INTO v_session FROM private.market_boost_view_sessions
  WHERE campaign_id=p_campaign AND viewer_id=v_viewer FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'MARKET_IMPRESSION_NOT_STARTED'; END IF;
 IF v_session.qualified_at IS NOT NULL THEN
   RETURN jsonb_build_object('qualified',true,'already_recorded',true,'charged',false);
 END IF;
 IF now()-v_session.started_at<interval '2 seconds' THEN
   RAISE EXCEPTION 'VIEW_TOO_SHORT';
 END IF;
 -- The existing coin ledger charges only one distinct campaign/viewer/surface
 -- impression; no user-editable values determine delivery or coin charges.
 v_delivered:=public.record_blink_boost_delivery(p_campaign,'IMPRESSION','MARKET');
 UPDATE private.market_boost_view_sessions SET qualified_at=now()
 WHERE campaign_id=p_campaign AND viewer_id=v_viewer;
 RETURN jsonb_build_object('qualified',true,'delivery',v_delivered);
END;
$market_view$;
REVOKE ALL ON FUNCTION public.record_market_qualified_view(uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.record_market_qualified_view(uuid,text) TO authenticated;

-- Client cannot call the older impression RPC directly for a MARKET listing
-- unless the server observed a real START stage and at least two seconds elapsed.
CREATE OR REPLACE FUNCTION private.market_verify_impression_session()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,private,pg_temp AS $market_verify$
BEGIN
 IF NEW.event_type='IMPRESSION' AND NEW.surface='MARKET'
   AND EXISTS(SELECT 1 FROM public.blink_boost_campaigns_v2 c
       WHERE c.id=NEW.campaign_id AND c.target_type='LISTING')
   AND NOT EXISTS(SELECT 1 FROM private.market_boost_view_sessions s
       WHERE s.campaign_id=NEW.campaign_id AND s.viewer_id=NEW.viewer_id
         AND now()-s.started_at >= interval '2 seconds')
 THEN RAISE EXCEPTION 'MARKET_VIEW_NOT_QUALIFIED'; END IF;
 RETURN NEW;
END;
$market_verify$;
DROP TRIGGER IF EXISTS market_verify_impression_session_trigger ON public.blink_boost_delivery_events_v2;
CREATE TRIGGER market_verify_impression_session_trigger
BEFORE INSERT ON public.blink_boost_delivery_events_v2
FOR EACH ROW EXECUTE FUNCTION private.market_verify_impression_session();

-- Robust idempotent credit settlement for expired LISTING promotions.
CREATE OR REPLACE FUNCTION private.market_settle_expired_coin_campaigns()
RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,private,pg_temp AS $market_credit$
DECLARE row_campaign public.blink_boost_campaigns_v2%ROWTYPE;
DECLARE v_unused bigint;v_balance numeric;v_count integer:=0;
BEGIN
 FOR row_campaign IN
   SELECT * FROM public.blink_boost_campaigns_v2
   WHERE target_type='LISTING' AND status='ACTIVE' AND ends_at<=now()
   FOR UPDATE SKIP LOCKED
 LOOP
   v_unused:=greatest(0,row_campaign.coin_budget-row_campaign.coin_spent-row_campaign.coin_refunded);
   UPDATE public.blink_boost_campaigns_v2 SET status='ENDED',
      coin_refunded=coin_refunded+v_unused,updated_at=now()
   WHERE id=row_campaign.id;
   IF v_unused>0 THEN
     INSERT INTO public.user_balances(user_id,spendable_coin_balance,updated_at)
       VALUES(row_campaign.user_id,0,now()) ON CONFLICT(user_id) DO NOTHING;
     UPDATE public.user_balances SET
       spendable_coin_balance=spendable_coin_balance+v_unused,updated_at=now()
     WHERE user_id=row_campaign.user_id RETURNING spendable_coin_balance INTO v_balance;
     INSERT INTO public.blink_coin_transactions(
       user_id,kind,catalog_id,item_name,amount,balance_after,metadata)
     VALUES(row_campaign.user_id,'BOOST_CAMPAIGN_REFUND',null,
       'Undelivered Market promotion credit',v_unused,floor(v_balance)::bigint,
       jsonb_build_object('campaign_id',row_campaign.id,'reason','campaign_end'));
   END IF;
   v_count:=v_count+1;
 END LOOP;
 RETURN v_count;
END;
$market_credit$;
REVOKE ALL ON FUNCTION private.market_settle_expired_coin_campaigns() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION private.market_settle_expired_coin_campaigns() TO service_role;

DO $market_credit_cron$
BEGIN
 IF to_regprocedure('cron.schedule(text,text,text)') IS NOT NULL
   AND to_regclass('cron.job') IS NOT NULL THEN
   IF NOT EXISTS(SELECT 1 FROM cron.job WHERE jobname='blink-market-credit-daily') THEN
     PERFORM cron.schedule('blink-market-credit-daily','55 4 * * *',
       'select private.market_settle_expired_coin_campaigns()');
   END IF;
 END IF;
EXCEPTION WHEN insufficient_privilege OR undefined_table OR undefined_function THEN
 RAISE NOTICE 'Market credit settlement requires a privileged scheduler';
END;
$market_credit_cron$;
COMMIT;
