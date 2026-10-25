-- Cash-funded BLINK boosts: reserve a full campaign only after Paystack verifies.
-- Default cash conversion rate is 0 (disabled until merchant approves pricing).
-- Prepaid undelivered views result in existing BLINK Coin credit, not cash refunds.
INSERT INTO private.blink_economy_config(key,int_value)
VALUES ('paystack_boost_naira_per_coin',0) ON CONFLICT(key) DO NOTHING;
ALTER TABLE public.blink_paystack_payments
 DROP CONSTRAINT IF EXISTS blink_paystack_payments_purchase_kind_check;
ALTER TABLE public.blink_paystack_payments
 ADD CONSTRAINT blink_paystack_payments_purchase_kind_check
 CHECK(purchase_kind IN('verification','coin_pack','boost'));
ALTER TABLE public.blink_paystack_payments DROP CONSTRAINT IF EXISTS paystack_order_shape;
ALTER TABLE public.blink_paystack_payments ADD CONSTRAINT paystack_order_shape CHECK(
  (purchase_kind='verification' AND item_code IN('BLUE','GOLD')
      AND coin_order_id IS NULL AND coin_amount IS NULL AND duration_days IS NOT NULL)
  OR (purchase_kind='coin_pack' AND coin_order_id IS NOT NULL
      AND coin_amount IS NOT NULL AND duration_days IS NULL)
  OR (purchase_kind='boost' AND item_code='BOOST'
      AND coin_order_id IS NULL AND coin_amount IS NULL AND duration_days IS NULL
      AND duration_months IS NULL)
);

CREATE TABLE IF NOT EXISTS public.blink_paystack_boost_orders (
 payment_id uuid PRIMARY KEY REFERENCES public.blink_paystack_payments(id),
 user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
 target_type text NOT NULL CHECK(target_type IN('POST','REEL','PROFILE','LISTING')),
 target_id uuid NOT NULL,
 objective text NOT NULL,
 boost_power integer NOT NULL CHECK(boost_power BETWEEN 1 AND 100),
 audience_scope text NOT NULL,
 target_university text,
 duration_days integer NOT NULL CHECK(duration_days BETWEEN 1 AND 30),
 coin_budget bigint NOT NULL CHECK(coin_budget>0),
 estimated_reach_low bigint NOT NULL CHECK(estimated_reach_low>=0),
 estimated_reach_high bigint NOT NULL CHECK(estimated_reach_high>=0),
 campaign_id uuid UNIQUE REFERENCES public.blink_boost_campaigns_v2(id),
 created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.blink_paystack_boost_orders ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.blink_paystack_boost_orders FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.blink_paystack_boost_orders TO authenticated;
GRANT SELECT,INSERT,UPDATE ON public.blink_paystack_boost_orders TO service_role;
DROP POLICY IF EXISTS paystack_boost_own_read ON public.blink_paystack_boost_orders;
CREATE POLICY paystack_boost_own_read ON public.blink_paystack_boost_orders
 FOR SELECT TO authenticated USING(user_id=(SELECT auth.uid()));

CREATE OR REPLACE FUNCTION public.create_blink_paystack_boost_order(
 p_target_type text,p_target_id uuid,p_boost_power integer,p_objective text,
 p_audience_scope text,p_duration_days integer,p_target_university text DEFAULT NULL
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $boost_order$
DECLARE v_user uuid:=auth.uid();
 v_rate integer;v_quote jsonb;v_coins bigint;v_total bigint;
 v_payment uuid;v_ref text;v_recent integer;
 v_enabled boolean;
BEGIN
 IF v_user IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
 IF p_target_id IS NULL OR p_duration_days NOT IN(1,3,7,14,30)
 OR p_boost_power NOT BETWEEN 1 AND 100 THEN
   RAISE EXCEPTION 'INVALID_BOOST_SELECTION'; END IF;
 SELECT bool_value INTO v_enabled FROM private.blink_economy_config
 WHERE key='cash_checkout_enabled';
 SELECT int_value::integer INTO v_rate FROM private.blink_economy_config
 WHERE key='paystack_boost_naira_per_coin';
 IF NOT coalesce(v_enabled,false) OR coalesce(v_rate,0)<=0 THEN
   RAISE EXCEPTION 'CASH_BOOST_NOT_ENABLED'; END IF;
 PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text)::bigint);
 SELECT count(*) INTO v_recent FROM public.blink_paystack_payments
 WHERE user_id=v_user AND created_at>now()-interval '30 minutes';
 IF v_recent>=8 THEN RAISE EXCEPTION 'PAYMENT_RATE_LIMIT'; END IF;
 -- This quote checks campaign ownership and current target eligibility.
 v_quote:=public.quote_blink_boost_campaign_v2(p_target_type,p_target_id,
  p_boost_power,p_objective,p_audience_scope,p_duration_days,p_target_university);
 v_coins:=(v_quote->>'coin_cost')::bigint;
 IF v_coins IS NULL OR v_coins<50 OR v_coins>1000000 THEN
   RAISE EXCEPTION 'BOOST_BUDGET_INVALID'; END IF;
 v_total:=v_coins*v_rate;
 IF v_total<50 OR v_total>1000000 THEN RAISE EXCEPTION 'BOOST_PRICE_UNAVAILABLE'; END IF;
 v_ref:='blink-ps-'||replace(gen_random_uuid()::text,'-','');
 INSERT INTO public.blink_paystack_payments(
   user_id,reference,purchase_kind,item_code,amount_ngn)
 VALUES (v_user,v_ref,'boost','BOOST',v_total)
 RETURNING id INTO v_payment;
 INSERT INTO public.blink_paystack_boost_orders(
   payment_id,user_id,target_type,target_id,objective,boost_power,
   audience_scope,target_university,duration_days,coin_budget,
   estimated_reach_low,estimated_reach_high)
 VALUES(v_payment,v_user,upper(p_target_type),p_target_id,upper(p_objective),
   p_boost_power,upper(p_audience_scope),nullif(v_quote->>'target_university',''),
   p_duration_days,v_coins,(v_quote->>'estimated_reach_low')::bigint,
   (v_quote->>'estimated_reach_high')::bigint);
 RETURN jsonb_build_object('reference',v_ref,'kind','boost',
    'item_code','BOOST','amount_ngn',v_total,'currency','NGN',
    'coin_budget',v_coins,'status','pending');
END
$boost_order$;
REVOKE ALL ON FUNCTION public.create_blink_paystack_boost_order(
 text,uuid,integer,text,text,integer,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.create_blink_paystack_boost_order(
 text,uuid,integer,text,text,integer,text) TO authenticated;

-- Trusted fulfilment: no free user coins are minted. The paid campaign uses
-- coin-denominated delivery accounting so existing ranking controls still apply.
CREATE OR REPLACE FUNCTION public.fulfill_blink_paystack_boost(
 p_payment_id uuid
) RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path=''
AS $paid_boost$
DECLARE v_boost public.blink_paystack_boost_orders%rowtype;
 v_payment public.blink_paystack_payments%rowtype;
 v_quote jsonb;v_campaign uuid;v_old_claim text;
BEGIN
 SELECT * INTO v_payment FROM public.blink_paystack_payments
 WHERE id=p_payment_id FOR UPDATE;
 IF NOT FOUND OR v_payment.purchase_kind<>'boost' THEN
    RAISE EXCEPTION 'BOOST_PAYMENT_NOT_FOUND'; END IF;
 SELECT * INTO v_boost FROM public.blink_paystack_boost_orders
 WHERE payment_id=p_payment_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'BOOST_ORDER_NOT_FOUND'; END IF;
 IF v_boost.campaign_id IS NOT NULL THEN
   RETURN jsonb_build_object('campaign_id',v_boost.campaign_id,'already_fulfilled',true);
 END IF;
 -- Recheck ownership and listing eligibility before starting the paid ad.
 v_old_claim:=current_setting('request.jwt.claim.sub',true);
 PERFORM set_config('request.jwt.claim.sub',v_payment.user_id::text,true);
 v_quote:=public.quote_blink_boost_campaign_v2(
   v_boost.target_type,v_boost.target_id,v_boost.boost_power,
   v_boost.objective,v_boost.audience_scope,v_boost.duration_days,
   v_boost.target_university);
 PERFORM set_config('request.jwt.claim.sub',coalesce(v_old_claim,''),true);
 IF v_quote IS NULL THEN RAISE EXCEPTION 'PAID_BOOST_TARGET_UNAVAILABLE'; END IF;
 INSERT INTO public.blink_boost_campaigns_v2(
  user_id,target_type,target_id,objective,boost_power,
  audience_scope,target_university,duration_days,coin_budget,
  estimated_reach_low,estimated_reach_high,starts_at,ends_at)
 VALUES(v_boost.user_id,v_boost.target_type,v_boost.target_id,
  v_boost.objective,v_boost.boost_power,v_boost.audience_scope,
  v_boost.target_university,v_boost.duration_days,v_boost.coin_budget,
  v_boost.estimated_reach_low,v_boost.estimated_reach_high,
  now(),now()+make_interval(days=>v_boost.duration_days))
 RETURNING id INTO v_campaign;
 UPDATE public.blink_paystack_boost_orders SET campaign_id=v_campaign
 WHERE payment_id=p_payment_id;
 RETURN jsonb_build_object('campaign_id',v_campaign,'already_fulfilled',false);
END
$paid_boost$;
REVOKE ALL ON FUNCTION public.fulfill_blink_paystack_boost(uuid)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.fulfill_blink_paystack_boost(uuid) TO service_role;

-- Retain existing atomic service-only payment reconciliation (no second grant).
CREATE OR REPLACE FUNCTION public.fulfill_blink_paystack_order(
  p_reference text,p_transaction_id text,p_amount_kobo bigint,p_currency text
) RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path=''
AS $paid_order$
DECLARE
  v_order public.blink_paystack_payments%rowtype;
  v_prior_badge text; v_prior_expiration timestamptz;
  v_new_badge text; v_new_expiration timestamptz;
  v_result jsonb;
BEGIN
 SELECT * INTO v_order FROM public.blink_paystack_payments
 WHERE reference=p_reference FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'PAYMENT_ORDER_NOT_FOUND'; END IF;
 IF v_order.status='fulfilled' THEN
  IF v_order.paystack_transaction_id IS DISTINCT FROM p_transaction_id THEN
   RAISE EXCEPTION 'PAYMENT_TRANSACTION_MISMATCH'; END IF;
  RETURN jsonb_build_object('status','fulfilled','already_fulfilled',true);
 END IF;
 IF v_order.status<>'pending' OR nullif(trim(coalesce(p_transaction_id,'')),'') IS NULL
   OR p_amount_kobo<>v_order.amount_ngn::bigint*100 OR p_currency<>'NGN' THEN
    RAISE EXCEPTION 'PAYMENT_VERIFICATION_MISMATCH'; END IF;
 IF v_order.purchase_kind='coin_pack' THEN
  v_result:=public.fulfill_blink_coin_purchase_order(
    v_order.coin_order_id,v_order.reference);
 ELSIF v_order.purchase_kind='boost' THEN
  v_result:=public.fulfill_blink_paystack_boost(v_order.id);
 ELSE
  SELECT upper(coalesce(verification_badge,'NONE')),verification_expires_at
   INTO v_prior_badge,v_prior_expiration
   FROM public.profiles WHERE id=v_order.user_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'PROFILE_NOT_FOUND'; END IF;
  v_new_badge:=CASE WHEN v_order.item_code='BLUE' AND v_prior_badge='GOLD'
   AND (v_prior_expiration IS NULL OR v_prior_expiration>now())
   THEN 'GOLD' ELSE v_order.item_code END;
  v_new_expiration:=CASE
   WHEN v_order.item_code='BLUE' AND v_prior_badge='GOLD'
    AND (v_prior_expiration IS NULL OR v_prior_expiration>now())
     THEN v_prior_expiration
   WHEN v_prior_badge=v_order.item_code AND v_prior_expiration IS NULL
     THEN NULL
   ELSE greatest(coalesce(v_prior_expiration,now()),now()) +
     (CASE WHEN v_order.duration_months IS NOT NULL
       THEN make_interval(months=>v_order.duration_months)
       ELSE make_interval(days=>v_order.duration_days) END) END;
  UPDATE public.profiles SET verification_badge=v_new_badge,
    verification_tier=(CASE WHEN v_new_badge='GOLD' THEN 'Gold'
       ELSE 'Standard' END)::public.verification_tier_enum,
    is_verified=true,verified_at=coalesce(verified_at,now()),
    verification_expires_at=v_new_expiration,updated_at=now()
    WHERE id=v_order.user_id;
 END IF;
 UPDATE public.blink_paystack_payments SET
  status='fulfilled',paystack_transaction_id=p_transaction_id,
  paid_at=now(),updated_at=now() WHERE id=v_order.id;
 RETURN jsonb_build_object('status','fulfilled','already_fulfilled',false,
    'kind',v_order.purchase_kind,'item_code',v_order.item_code,'result',v_result);
END
$paid_order$;
REVOKE ALL ON FUNCTION public.fulfill_blink_paystack_order(text,text,bigint,text)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.fulfill_blink_paystack_order(text,text,bigint,text)
 TO service_role;
