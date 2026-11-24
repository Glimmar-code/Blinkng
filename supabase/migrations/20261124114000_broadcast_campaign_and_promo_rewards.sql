-- Broadcast rewards, campaign progress automation, and idempotent reward handlers.
-- Reuses existing BLINK wallets, coin ledger, inventory, boosts, marketplace, referrals and contests.
-- Testlab-first. This migration requires the Broadcast schema/function/access migrations.

CREATE TABLE IF NOT EXISTS public.broadcast_campaign_reward_claims (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  broadcast_id uuid NOT NULL REFERENCES public.broadcasts(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  reward_tier integer NOT NULL CHECK(reward_tier>0),
  reward jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(broadcast_id,user_id,reward_tier)
);
CREATE INDEX IF NOT EXISTS broadcast_campaign_reward_claims_user_idx ON public.broadcast_campaign_reward_claims(user_id,broadcast_id);
ALTER TABLE public.broadcast_campaign_reward_claims ENABLE ROW LEVEL SECURITY;
CREATE POLICY broadcast_campaign_reward_claims_own_select ON public.broadcast_campaign_reward_claims FOR SELECT TO authenticated USING(user_id=auth.uid());
CREATE POLICY broadcast_campaign_reward_claims_admin_all ON public.broadcast_campaign_reward_claims FOR ALL TO authenticated USING(public.broadcast_admin_check()) WITH CHECK(public.broadcast_admin_check());
GRANT SELECT ON public.broadcast_campaign_reward_claims TO authenticated;
GRANT ALL ON public.broadcast_campaign_reward_claims TO service_role;

CREATE TABLE IF NOT EXISTS public.broadcast_free_spin_credits (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  broadcast_id uuid NOT NULL REFERENCES public.broadcasts(id) ON DELETE CASCADE,
  claim_id uuid NOT NULL UNIQUE,
  credits_granted integer NOT NULL CHECK(credits_granted BETWEEN 1 AND 100),
  credits_remaining integer NOT NULL CHECK(credits_remaining BETWEEN 0 AND 100),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS broadcast_free_spin_credits_user_idx ON public.broadcast_free_spin_credits(user_id,created_at) WHERE credits_remaining>0;
ALTER TABLE public.broadcast_free_spin_credits ENABLE ROW LEVEL SECURITY;
CREATE POLICY broadcast_free_spin_credits_own_select ON public.broadcast_free_spin_credits FOR SELECT TO authenticated USING(user_id=auth.uid());
GRANT SELECT ON public.broadcast_free_spin_credits TO authenticated;
GRANT ALL ON public.broadcast_free_spin_credits TO service_role;

CREATE TABLE IF NOT EXISTS public.broadcast_fee_vouchers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  broadcast_id uuid NOT NULL REFERENCES public.broadcasts(id) ON DELETE CASCADE,
  claim_id uuid NOT NULL UNIQUE,
  discount_percent numeric NOT NULL CHECK(discount_percent BETWEEN 0.01 AND 100),
  minimum_fee numeric NOT NULL DEFAULT 0 CHECK(minimum_fee>=0),
  expires_at timestamptz NOT NULL,
  redeemed_at timestamptz,
  redeemed_for text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS broadcast_fee_vouchers_user_active_idx ON public.broadcast_fee_vouchers(user_id,expires_at) WHERE redeemed_at IS NULL;
ALTER TABLE public.broadcast_fee_vouchers ENABLE ROW LEVEL SECURITY;
CREATE POLICY broadcast_fee_vouchers_own_select ON public.broadcast_fee_vouchers FOR SELECT TO authenticated USING(user_id=auth.uid());
GRANT SELECT ON public.broadcast_fee_vouchers TO authenticated;
GRANT ALL ON public.broadcast_fee_vouchers TO service_role;

-- Hidden item reference; the store does not expose it for purchase.
INSERT INTO public.blink_store_catalog(id,name,description,icon_key,category,price,item_type,target_type,duration_seconds,stackable,vip_only,boost_multipliers,is_active,sort_order,metadata)
VALUES('broadcast_free_spin','Broadcast Free Spin','Hidden internal reward token','spin','Broadcast Rewards',0,'CONSUMABLE','PROFILE',null,true,false,'{}',false,9999,jsonb_build_object('hidden_from_store',true,'source','broadcast_reward'))
ON CONFLICT(id) DO NOTHING;

ALTER TABLE public.connect_match_spins DROP CONSTRAINT IF EXISTS connect_match_spins_coins_spent_check;
ALTER TABLE public.connect_match_spins ADD CONSTRAINT connect_match_spins_coins_spent_check CHECK(coins_spent IN (0,10));

CREATE OR REPLACE FUNCTION public.broadcast_apply_reward(p_user uuid,p_broadcast uuid,p_claim uuid,p_reward_type public.broadcast_reward_type,p_amount numeric,p_reward_text text DEFAULT NULL)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_balance numeric; v_inventory uuid; v_spin uuid; v_voucher uuid; v_amount bigint;
BEGIN
  IF p_user IS NULL OR p_claim IS NULL OR p_amount IS NULL OR p_amount<=0 OR p_amount>1000000 THEN RAISE EXCEPTION 'INVALID_REWARD'; END IF;
  IF p_reward_type='coins' THEN
    IF p_amount<>floor(p_amount) THEN RAISE EXCEPTION 'COIN_REWARD_MUST_BE_INTEGER'; END IF;
    v_amount:=p_amount::bigint;
    INSERT INTO public.user_balances(user_id,spendable_coin_balance,updated_at) VALUES(p_user,0,now()) ON CONFLICT(user_id) DO NOTHING;
    UPDATE public.user_balances SET spendable_coin_balance=spendable_coin_balance+v_amount,updated_at=now() WHERE user_id=p_user RETURNING spendable_coin_balance INTO v_balance;
    INSERT INTO public.blink_coin_transactions(user_id,kind,item_name,amount,balance_after,metadata)
      VALUES(p_user,'broadcast_reward',coalesce(nullif(p_reward_text,''),'BLINK Broadcast reward'),v_amount,floor(v_balance)::bigint,jsonb_build_object('broadcast_id',p_broadcast,'claim_id',p_claim,'reward_type','coins'));
    RETURN jsonb_build_object('type','coins','amount',v_amount,'balance',floor(v_balance)::bigint);
  ELSIF p_reward_type='free_spin' THEN
    IF p_amount<>floor(p_amount) OR p_amount>100 THEN RAISE EXCEPTION 'INVALID_FREE_SPIN_COUNT'; END IF;
    INSERT INTO public.broadcast_free_spin_credits(user_id,broadcast_id,claim_id,credits_granted,credits_remaining)
      VALUES(p_user,p_broadcast,p_claim,p_amount::integer,p_amount::integer) ON CONFLICT(claim_id) DO NOTHING RETURNING id INTO v_spin;
    RETURN jsonb_build_object('type','free_spin','credits',p_amount::integer,'credit_id',v_spin);
  ELSIF p_reward_type='boost' THEN
    IF p_amount NOT IN (1,2,3,5) THEN RAISE EXCEPTION 'INVALID_BOOST_MULTIPLIER'; END IF;
    INSERT INTO public.blink_inventory(user_id,catalog_id,quantity,status,boost_multiplier,metadata)
      VALUES(p_user,'post_boost',1,'AVAILABLE',p_amount::integer,jsonb_build_object('source','broadcast_reward','broadcast_id',p_broadcast,'claim_id',p_claim))
      RETURNING id INTO v_inventory;
    RETURN jsonb_build_object('type','boost','inventory_id',v_inventory,'multiplier',p_amount,'target_type','POST_OR_REEL');
  ELSIF p_reward_type='fee_discount' THEN
    IF p_amount<=0 OR p_amount>100 THEN RAISE EXCEPTION 'INVALID_FEE_DISCOUNT'; END IF;
    INSERT INTO public.broadcast_fee_vouchers(user_id,broadcast_id,claim_id,discount_percent,expires_at)
      VALUES(p_user,p_broadcast,p_claim,p_amount,now()+interval '30 days') ON CONFLICT(claim_id) DO NOTHING RETURNING id INTO v_voucher;
    RETURN jsonb_build_object('type','fee_discount','voucher_id',v_voucher,'percent',p_amount,'expires_at',now()+interval '30 days');
  END IF;
  RAISE EXCEPTION 'UNSUPPORTED_REWARD_TYPE';
END
$$;

CREATE OR REPLACE FUNCTION public.broadcast_campaign_reward(p_broadcast_id uuid,p_user uuid,p_progress integer)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE participant public.campaign_participants%rowtype; rule public.campaign_rules%rowtype; tier jsonb; claim_id uuid; v_reward_result jsonb; threshold integer; reward_type public.broadcast_reward_type; amount numeric; max_tier integer:=0; awarded boolean:=false;
BEGIN
  SELECT * INTO participant FROM public.campaign_participants WHERE broadcast_id=p_broadcast_id AND user_id=p_user FOR UPDATE;
  IF NOT FOUND THEN RETURN false; END IF;
  SELECT * INTO rule FROM public.campaign_rules WHERE broadcast_id=p_broadcast_id;
  IF NOT FOUND THEN RETURN false; END IF;
  FOR tier IN SELECT value FROM jsonb_array_elements(rule.tiers) ORDER BY (value->>'threshold')::integer LOOP
    threshold:=coalesce((tier->>'threshold')::integer,0);
    IF threshold<=0 OR threshold>p_progress THEN CONTINUE; END IF;
    reward_type:=(tier->>'reward_type')::public.broadcast_reward_type;
    amount:=coalesce((tier->>'amount')::numeric,0);
    IF amount<=0 OR amount>1000000 THEN CONTINUE; END IF;
    INSERT INTO public.broadcast_campaign_reward_claims(broadcast_id,user_id,reward_tier,reward)
      VALUES(p_broadcast_id,p_user,threshold,jsonb_build_object('type',reward_type,'amount',amount))
      ON CONFLICT(broadcast_id,user_id,reward_tier) DO NOTHING RETURNING id INTO claim_id;
    IF claim_id IS NOT NULL THEN
      v_reward_result:=public.broadcast_apply_reward(p_user,p_broadcast_id,claim_id,reward_type,amount,'Broadcast campaign tier reward');
      UPDATE public.broadcast_campaign_reward_claims rc SET reward=rc.reward||jsonb_build_object('result',v_reward_result) WHERE rc.id=claim_id;
      INSERT INTO public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message,is_read)
        VALUES(p_user,null,'broadcast_campaign_reward','broadcast',p_broadcast_id,'You earned a BLINK Broadcast campaign reward.',false);
      INSERT INTO public.notifications(user_id,actor_id,type,text,sub_text,is_read,target_type,target_id,metadata)
        VALUES(p_user,null,'system'::public.notification_type_enum,'Campaign reward earned','Your BLINK Broadcast campaign reward is ready.',false,'broadcast',p_broadcast_id,jsonb_build_object('broadcast_id',p_broadcast_id,'reward_tier',threshold,'reward',v_reward_result));
      awarded:=true;
    END IF;
    max_tier:=greatest(max_tier,threshold); claim_id:=null;
  END LOOP;
  UPDATE public.campaign_participants SET reward_tier=max_tier,
    reward_claimed_at=case when p_progress>=rule.target then coalesce(reward_claimed_at,now()) else reward_claimed_at end,
    completed_at=case when p_progress>=rule.target then coalesce(completed_at,now()) else completed_at end
  WHERE broadcast_id=p_broadcast_id AND user_id=p_user;
  RETURN awarded;
END
$$;

CREATE OR REPLACE FUNCTION public.record_broadcast_campaign_event(p_goal public.broadcast_goal_type,p_user uuid,p_source text,p_source_id text,p_delta integer,p_event_at timestamptz)
RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE c record; inserted_id uuid; total integer;
BEGIN
  IF p_user IS NULL OR p_source_id IS NULL OR p_delta<=0 OR p_delta>10000 OR p_event_at IS NULL THEN RETURN 0; END IF;
  FOR c IN SELECT cp.broadcast_id,cp.joined_at,cr.target,b.starts_at,b.ends_at
    FROM public.campaign_participants cp JOIN public.campaign_rules cr ON cr.broadcast_id=cp.broadcast_id JOIN public.broadcasts b ON b.id=cp.broadcast_id
    WHERE cp.user_id=p_user AND cr.goal_type=p_goal AND cp.completed_at IS NULL AND b.status IN ('live','ended')
      AND cp.joined_at<=p_event_at AND (b.starts_at IS NULL OR b.starts_at<=p_event_at) AND (b.ends_at IS NULL OR b.ends_at>=p_event_at)
  LOOP
    INSERT INTO public.campaign_progress_events(broadcast_id,user_id,source_type,source_id,delta,created_at)
      VALUES(c.broadcast_id,p_user,p_source,p_source_id,p_delta,p_event_at)
      ON CONFLICT(broadcast_id,source_type,source_id) DO NOTHING RETURNING id INTO inserted_id;
    IF inserted_id IS NOT NULL THEN
      UPDATE public.campaign_participants SET progress=progress+p_delta,
        completed_at=case when progress+p_delta>=c.target then coalesce(completed_at,now()) else completed_at end
      WHERE broadcast_id=c.broadcast_id AND user_id=p_user RETURNING progress INTO total;
      PERFORM public.broadcast_campaign_reward(c.broadcast_id,p_user,total);
    END IF;
    inserted_id:=null;
  END LOOP;
  RETURN 1;
END
$$;

CREATE OR REPLACE FUNCTION public.broadcast_coin_purchase_campaign_trigger()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_event_at timestamptz;
BEGIN
  IF NEW.status IN ('paid','fulfilled') AND (TG_OP='INSERT' OR OLD.status IS DISTINCT FROM NEW.status) THEN
    v_event_at:=coalesce(NEW.paid_at,NEW.fulfilled_at,NEW.updated_at,NEW.created_at);
    PERFORM public.record_broadcast_campaign_event('purchases',NEW.user_id,'coin_purchase',NEW.id::text,1,v_event_at);
  END IF;
  RETURN NEW;
END
$$;
DROP TRIGGER IF EXISTS broadcast_coin_purchase_campaign_progress ON public.blink_coin_purchase_orders;
CREATE TRIGGER broadcast_coin_purchase_campaign_progress AFTER INSERT OR UPDATE ON public.blink_coin_purchase_orders
FOR EACH ROW EXECUTE FUNCTION public.broadcast_coin_purchase_campaign_trigger();

CREATE OR REPLACE FUNCTION public.broadcast_order_campaign_trigger()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_event_at timestamptz;
BEGIN
  IF NEW.status IN ('paid','completed','delivered') AND (TG_OP='INSERT' OR OLD.status IS DISTINCT FROM NEW.status) THEN
    v_event_at:=coalesce(NEW.updated_at,NEW.created_at);
    PERFORM public.record_broadcast_campaign_event('orders',NEW.buyer_id,'marketplace_order',NEW.id::text,1,v_event_at);
    PERFORM public.record_broadcast_campaign_event('purchases',NEW.buyer_id,'marketplace_order',NEW.id::text,1,v_event_at);
  END IF;
  RETURN NEW;
END
$$;
CREATE OR REPLACE FUNCTION public.broadcast_referral_campaign_trigger()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
  IF NEW.status='confirmed' AND NEW.confirmed_at IS NOT NULL AND (TG_OP='INSERT' OR OLD.status IS DISTINCT FROM NEW.status) THEN
    PERFORM public.record_broadcast_campaign_event('invites',NEW.referrer_user_id,'referral',NEW.referred_user_id::text,1,NEW.confirmed_at);
  END IF;
  RETURN NEW;
END
$$;
CREATE OR REPLACE FUNCTION public.broadcast_vote_campaign_trigger()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
  IF NEW.review_status='valid' AND (TG_OP='INSERT' OR OLD.review_status IS DISTINCT FROM NEW.review_status) THEN
    PERFORM public.record_broadcast_campaign_event('votes',NEW.voter_id,'contest_vote',NEW.id::text,1,NEW.created_at);
  END IF;
  RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION public.broadcast_backfill_campaign_progress()
RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE n bigint:=0; r record;
BEGIN
  IF auth.uid() IS NOT NULL THEN PERFORM private.require_blink_admin();
  ELSIF coalesce(current_setting('request.jwt.claims',true)::jsonb->>'role','')<>'service_role' THEN RAISE EXCEPTION 'ADMIN_REQUIRED'; END IF;
  FOR r IN SELECT cp.user_id,cp.joined_at,b.starts_at,b.ends_at,b.id broadcast_id,cr.goal_type
    FROM public.campaign_participants cp JOIN public.broadcasts b ON b.id=cp.broadcast_id JOIN public.campaign_rules cr ON cr.broadcast_id=b.id
    WHERE b.status IN ('live','ended')
  LOOP
    IF r.goal_type='invites' THEN
      PERFORM public.record_broadcast_campaign_event('invites',r.user_id,'referral',x.referred_user_id::text,1,x.confirmed_at)
      FROM public.blink_referrals x WHERE x.referrer_user_id=r.user_id AND x.status='confirmed' AND x.confirmed_at IS NOT NULL
        AND x.confirmed_at>=r.joined_at AND (r.starts_at IS NULL OR x.confirmed_at>=r.starts_at) AND (r.ends_at IS NULL OR x.confirmed_at<=r.ends_at);
    ELSIF r.goal_type='purchases' THEN
      PERFORM public.record_broadcast_campaign_event('purchases',r.user_id,'coin_purchase',x.id::text,1,coalesce(x.paid_at,x.fulfilled_at,x.updated_at,x.created_at))
      FROM public.blink_coin_purchase_orders x WHERE x.user_id=r.user_id AND x.status IN ('paid','fulfilled')
        AND coalesce(x.paid_at,x.fulfilled_at,x.updated_at,x.created_at)>=r.joined_at
        AND (r.starts_at IS NULL OR coalesce(x.paid_at,x.fulfilled_at,x.updated_at,x.created_at)>=r.starts_at)
        AND (r.ends_at IS NULL OR coalesce(x.paid_at,x.fulfilled_at,x.updated_at,x.created_at)<=r.ends_at);
      PERFORM public.record_broadcast_campaign_event('purchases',r.user_id,'marketplace_order',x.id::text,1,coalesce(x.updated_at,x.created_at))
      FROM public.marketplace_orders x WHERE x.buyer_id=r.user_id AND x.status IN ('paid','completed','delivered')
        AND coalesce(x.updated_at,x.created_at)>=r.joined_at AND (r.starts_at IS NULL OR coalesce(x.updated_at,x.created_at)>=r.starts_at)
        AND (r.ends_at IS NULL OR coalesce(x.updated_at,x.created_at)<=r.ends_at);
    ELSIF r.goal_type='orders' THEN
      PERFORM public.record_broadcast_campaign_event('orders',r.user_id,'marketplace_order',x.id::text,1,coalesce(x.updated_at,x.created_at))
      FROM public.marketplace_orders x WHERE x.buyer_id=r.user_id AND x.status IN ('paid','completed','delivered')
        AND coalesce(x.updated_at,x.created_at)>=r.joined_at AND (r.starts_at IS NULL OR coalesce(x.updated_at,x.created_at)>=r.starts_at)
        AND (r.ends_at IS NULL OR coalesce(x.updated_at,x.created_at)<=r.ends_at);
    ELSIF r.goal_type='votes' THEN
      PERFORM public.record_broadcast_campaign_event('votes',r.user_id,'contest_vote',x.id::text,1,x.created_at)
      FROM public.blink_contest_votes x WHERE x.voter_id=r.user_id AND x.review_status='valid' AND x.created_at>=r.joined_at
        AND (r.starts_at IS NULL OR x.created_at>=r.starts_at) AND (r.ends_at IS NULL OR x.created_at<=r.ends_at);
    END IF;
    n:=n+1;
  END LOOP;
  RETURN n;
END
$$;

CREATE OR REPLACE FUNCTION public.claim_promo(p_broadcast_id uuid,p_idempotency_key text,p_purchase_amount numeric DEFAULT 0)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE uid uuid:=auth.uid(); pr public.promo_rules%rowtype; b public.broadcasts%rowtype; existing public.promo_claims%rowtype;
  claim_id uuid; reward_json jsonb; reward_result jsonb; budget_units numeric; purchase_ok boolean; result_status public.broadcast_claim_status;
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
  PERFORM public.broadcast_require_current_app();
  IF p_idempotency_key IS NULL OR length(trim(p_idempotency_key))<8 OR length(p_idempotency_key)>128 THEN RAISE EXCEPTION 'INVALID_IDEMPOTENCY_KEY'; END IF;
  IF NOT public.broadcast_rate_limit('claim',10,60) THEN RETURN jsonb_build_object('status','NOT_ELIGIBLE','reason','RATE_LIMITED'); END IF;
  SELECT * INTO existing FROM public.promo_claims WHERE broadcast_id=p_broadcast_id AND user_id=uid AND idempotency_key=p_idempotency_key;
  IF FOUND THEN RETURN jsonb_build_object('status',upper(existing.status::text),'claim_id',existing.id,'reward',existing.reward,'idempotent',true); END IF;
  SELECT * INTO pr FROM public.promo_rules WHERE broadcast_id=p_broadcast_id FOR UPDATE;
  IF NOT FOUND THEN RETURN jsonb_build_object('status','EXPIRED'); END IF;
  SELECT * INTO existing FROM public.promo_claims WHERE broadcast_id=p_broadcast_id AND user_id=uid AND idempotency_key=p_idempotency_key;
  IF FOUND THEN RETURN jsonb_build_object('status',upper(existing.status::text),'claim_id',existing.id,'reward',existing.reward,'idempotent',true); END IF;
  SELECT * INTO b FROM public.broadcasts WHERE id=p_broadcast_id;
  IF NOT FOUND OR b.deleted_at IS NOT NULL OR b.status<>'live' OR (b.starts_at IS NOT NULL AND b.starts_at>now()) OR (b.ends_at IS NOT NULL AND b.ends_at<=now()) THEN
    result_status:='expired';
  ELSIF NOT public.broadcast_audience_match(uid,b.audience,nullif((current_setting('request.headers',true)::jsonb)->>'x-blink-app-version','')) THEN
    result_status:='not_eligible';
  ELSIF (SELECT count(*) FROM public.promo_claims pc WHERE pc.broadcast_id=p_broadcast_id AND pc.user_id=uid AND pc.status='claimed')>=pr.per_user_limit THEN
    result_status:='limit_reached';
  ELSIF pr.min_purchase>0 THEN
    SELECT EXISTS(
      SELECT 1 FROM public.marketplace_orders mo WHERE mo.buyer_id=uid AND mo.status IN ('paid','completed','delivered')
        AND mo.total_price>=pr.min_purchase AND mo.created_at>=coalesce(b.starts_at,'-infinity'::timestamptz)
      UNION ALL
      SELECT 1 FROM public.blink_coin_purchase_orders co WHERE co.user_id=uid AND co.status='fulfilled'
        AND co.amount_ngn>=pr.min_purchase AND co.created_at>=coalesce(b.starts_at,'-infinity'::timestamptz)
    ) INTO purchase_ok;
    IF NOT coalesce(purchase_ok,false) THEN result_status:='not_eligible'; ELSE result_status:='claimed'; END IF;
  ELSE result_status:='claimed';
  END IF;
  IF result_status<>'claimed' THEN RETURN jsonb_build_object('status',upper(result_status::text),'claim_id',null,'reward',null); END IF;
  IF pr.reward_type='coins' THEN
    IF pr.reward_amount<>floor(pr.reward_amount) OR pr.reward_amount>1000000 THEN RAISE EXCEPTION 'INVALID_COIN_REWARD_AMOUNT'; END IF;
    budget_units:=pr.reward_amount;
  ELSIF pr.reward_type='free_spin' THEN
    IF pr.reward_amount<>floor(pr.reward_amount) OR pr.reward_amount>100 THEN RAISE EXCEPTION 'INVALID_FREE_SPIN_COUNT'; END IF;
    budget_units:=pr.reward_amount;
  ELSIF pr.reward_type='boost' THEN
    IF pr.reward_amount NOT IN (1,2,3,5) THEN RAISE EXCEPTION 'INVALID_BOOST_MULTIPLIER'; END IF;
    budget_units:=1;
  ELSIF pr.reward_type='fee_discount' THEN
    IF pr.reward_amount<=0 OR pr.reward_amount>100 THEN RAISE EXCEPTION 'INVALID_FEE_DISCOUNT'; END IF;
    budget_units:=1;
  ELSE RAISE EXCEPTION 'UNSUPPORTED_REWARD_TYPE';
  END IF;
  IF pr.total_budget_cap IS NOT NULL AND pr.budget_used+budget_units>pr.total_budget_cap THEN RETURN jsonb_build_object('status','SOLD_OUT','claim_id',null,'reward',null); END IF;
  reward_json:=jsonb_build_object('type',pr.reward_type,'amount',pr.reward_amount,'text',pr.reward_text);
  INSERT INTO public.promo_claims(broadcast_id,user_id,status,reward,idempotency_key) VALUES(p_broadcast_id,uid,'claimed',reward_json,p_idempotency_key) RETURNING id INTO claim_id;
  UPDATE public.promo_rules SET budget_used=budget_used+budget_units,updated_at=now() WHERE broadcast_id=p_broadcast_id;
  reward_result:=public.broadcast_apply_reward(uid,p_broadcast_id,claim_id,pr.reward_type,pr.reward_amount,pr.reward_text);
  UPDATE public.promo_claims SET reward=reward_json||jsonb_build_object('result',reward_result) WHERE id=claim_id;
  INSERT INTO public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message,is_read)
    VALUES(uid,null,'broadcast_promo_claim','broadcast',p_broadcast_id,'You claimed a BLINK Broadcast promo reward.',false);
  INSERT INTO public.notifications(user_id,actor_id,type,text,sub_text,is_read,target_type,target_id,metadata)
    VALUES(uid,null,'system'::public.notification_type_enum,'Promo reward claimed','Your BLINK Broadcast reward is ready.',false,'broadcast',p_broadcast_id,jsonb_build_object('claim_id',claim_id,'reward',reward_json||jsonb_build_object('result',reward_result)));
  RETURN jsonb_build_object('status','CLAIMED','claim_id',claim_id,'reward',reward_json||jsonb_build_object('result',reward_result),'idempotent',false);
END
$$;

CREATE OR REPLACE FUNCTION public.redeem_broadcast_fee_voucher(p_voucher_id uuid,p_fee_amount numeric,p_order_reference text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v public.broadcast_fee_vouchers%rowtype; v_discount numeric;
BEGIN
  IF p_fee_amount IS NULL OR p_fee_amount<0 OR p_fee_amount>1000000000 OR p_order_reference IS NULL OR length(p_order_reference)>128 THEN RAISE EXCEPTION 'INVALID_FEE_CONTEXT'; END IF;
  SELECT * INTO v FROM public.broadcast_fee_vouchers WHERE id=p_voucher_id FOR UPDATE;
  IF NOT FOUND OR v.redeemed_at IS NOT NULL OR v.expires_at<=now() THEN RETURN jsonb_build_object('ok',false,'reason','VOUCHER_EXPIRED_OR_USED'); END IF;
  IF p_fee_amount<v.minimum_fee THEN RETURN jsonb_build_object('ok',false,'reason','MINIMUM_FEE_NOT_MET'); END IF;
  v_discount:=round(p_fee_amount*v.discount_percent/100,2);
  UPDATE public.broadcast_fee_vouchers SET redeemed_at=now(),redeemed_for=p_order_reference WHERE id=p_voucher_id AND redeemed_at IS NULL;
  IF NOT FOUND THEN RETURN jsonb_build_object('ok',false,'reason','VOUCHER_USED'); END IF;
  RETURN jsonb_build_object('ok',true,'discount',v_discount,'fee_after_discount',greatest(0,p_fee_amount-v_discount),'percent',v.discount_percent);
END
$$;

CREATE OR REPLACE FUNCTION public.spin_connect_match(p_university text DEFAULT NULL::text, p_faculty text DEFAULT NULL::text, p_department text DEFAULT NULL::text, p_academic_level text DEFAULT NULL::text, p_relationship_status text DEFAULT NULL::text, p_type_prompt text DEFAULT NULL::text, p_online_only boolean DEFAULT false)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_user_id uuid := (select auth.uid());
  v_balance numeric;
  v_free_credit_id uuid;
  v_free_spin boolean := false;
  v_prompt text := left(nullif(btrim(p_type_prompt), ''), 200);
  v_match record;
begin
  if v_user_id is null then
    raise exception 'Please sign in again.';
  end if;

  insert into public.user_balances (
    user_id,
    lifetime_engagement_score,
    spendable_coin_balance,
    updated_at
  ) values (
    v_user_id,
    0,
    0,
    now()
  )
  on conflict (user_id) do nothing;

  select ub.spendable_coin_balance
    into v_balance
  from public.user_balances ub
  where ub.user_id = v_user_id
  for update;

  SELECT id INTO v_free_credit_id
    FROM public.broadcast_free_spin_credits
   WHERE user_id=v_user_id AND credits_remaining>0
   ORDER BY created_at,id
   LIMIT 1 FOR UPDATE;
  v_free_spin := v_free_credit_id IS NOT NULL;
  IF NOT v_free_spin AND floor(coalesce(v_balance,0))<10 THEN
    RAISE EXCEPTION 'You need at least 10 Blink Coins or a free-spin credit.';
  END IF;

  with filtered as (
    select
      m.*,
      p.bio,
      p.professional_headline,
      p.core_skills,
      p.hobbies,
      p.languages,
      coalesce(pp.public_posts, '') as public_posts
    from public.get_connect_matches(120) m
    join public.profiles p on p.id = m.id
    left join lateral (
      select string_agg(
        concat_ws(
          ' ',
          recent.text,
          recent.caption,
          array_to_string(recent.hashtags, ' '),
          array_to_string(recent.tags, ' ')
        ),
        ' '
      ) as public_posts
      from (
        select fp.text, fp.caption, fp.hashtags, fp.tags
        from public.feed_posts fp
        where fp.user_id = m.id
          and fp.is_active is true
          and coalesce(fp.is_flagged, false) is false
          and (fp.audience is null or fp.audience ilike 'Everyone')
        order by fp.created_at desc
        limit 20
      ) recent
    ) pp on true
    where
      (nullif(btrim(p_university), '') is null or lower(m.university) = lower(btrim(p_university)))
      and (nullif(btrim(p_faculty), '') is null or lower(m.faculty) = lower(btrim(p_faculty)))
      and (nullif(btrim(p_department), '') is null or lower(m.department) = lower(btrim(p_department)))
      and (nullif(btrim(p_academic_level), '') is null or lower(m.academic_level) = lower(btrim(p_academic_level)))
      and (nullif(btrim(p_relationship_status), '') is null or lower(m.relationship_status) = lower(btrim(p_relationship_status)))
      and (not coalesce(p_online_only, false) or m.online_now is true)
  ),
  enriched as (
    select
      f.*,
      lower(
        concat_ws(
          ' ',
          f.bio,
          f.professional_headline,
          array_to_string(f.core_skills, ' '),
          array_to_string(f.hobbies, ' '),
          array_to_string(f.languages, ' '),
          f.university,
          f.faculty,
          f.department,
          f.public_posts
        )
      ) as search_blob
    from filtered f
  ),
  scored as (
    select
      e.*,
      case
        when v_prompt is null then 0
        else least(
          40,
          coalesce((
            select count(*)::int * 4
            from (
              select distinct token
              from regexp_split_to_table(
                regexp_replace(lower(v_prompt), '[^a-z0-9+#]+', ' ', 'g'),
                '\s+'
              ) token
              where length(token) >= 3
                and token not in (
                  'someone', 'somebody', 'person', 'people', 'that', 'with',
                  'who', 'likes', 'like', 'into', 'and', 'the', 'for', 'from',
                  'want', 'looking', 'type', 'very', 'also'
                )
            ) prompt_tokens
            where e.search_blob like '%' || prompt_tokens.token || '%'
          ), 0)
          + case
              when lower(v_prompt) ~ '(coding|code|programming|software|developer|tech)'
               and e.search_blob ~ '(coding|code|programming|software|developer|tech)'
              then 10 else 0
            end
          + case
              when lower(v_prompt) ~ '(social|talking|talk|chat|communication|community)'
               and e.search_blob ~ '(social|talking|talk|chat|communication|community)'
              then 10 else 0
            end
          + case
              when lower(v_prompt) ~ '(cooking|cook|food|baking|bake|chef|recipe)'
               and e.search_blob ~ '(cooking|cook|food|baking|bake|chef|recipe)'
              then 10 else 0
            end
          + case
              when lower(v_prompt) ~ '(football|sport|sports|gaming|game|music|reading|books|art)'
               and e.search_blob ~ '(football|sport|sports|gaming|game|music|reading|books|art)'
              then 8 else 0
            end
        )::int
      end as semantic_score
    from enriched e
  ),
  ranked as (
    select
      s.*,
      least(100, s.compatibility_score + round(s.semantic_score * 0.60)::int) as final_score,
      exists (
        select 1
        from public.connect_match_spins cms
        where cms.user_id = v_user_id
          and cms.matched_user_id = s.id
          and cms.created_at > now() - interval '14 days'
      ) as recently_matched
    from scored s
  )
  select *
    into v_match
  from ranked r
  order by
    r.recently_matched asc,
    (r.final_score + (random() * 8)) desc
  limit 1;

  if not found then
    raise exception 'No eligible match fits those filters yet. Try widening your preferences.';
  end if;

  IF v_free_spin THEN
    UPDATE public.broadcast_free_spin_credits
       SET credits_remaining=credits_remaining-1,updated_at=now()
     WHERE id=v_free_credit_id AND credits_remaining>0;
    IF NOT FOUND THEN RAISE EXCEPTION 'FREE_SPIN_CREDIT_EXHAUSTED'; END IF;
  ELSE
    UPDATE public.user_balances
       SET spendable_coin_balance=spendable_coin_balance-10,updated_at=now()
     WHERE user_id=v_user_id;
    INSERT INTO public.blink_coin_transactions(user_id,kind,item_name,amount,balance_after,metadata)
      SELECT v_user_id,'connect_match_spin','Connect Match Spin',-10,floor(spendable_coin_balance)::bigint,
        jsonb_build_object('source','connect_match_spin')
      FROM public.user_balances WHERE user_id=v_user_id;
  END IF;

  insert into public.connect_match_spins (
    user_id,
    matched_user_id,
    filters,
    type_prompt,
    coins_spent,
    compatibility_score
  ) values (
    v_user_id,
    v_match.id,
    jsonb_build_object(
      'university', nullif(btrim(p_university), ''),
      'faculty', nullif(btrim(p_faculty), ''),
      'department', nullif(btrim(p_department), ''),
      'academic_level', nullif(btrim(p_academic_level), ''),
      'relationship_status', nullif(btrim(p_relationship_status), ''),
      'online_only', coalesce(p_online_only, false)
    ),
    v_prompt,
    case when v_free_spin then 0 else 10 end,
    v_match.final_score
  );

  return jsonb_build_object(
    'candidate', jsonb_build_object(
      'id', v_match.id,
      'username', v_match.username,
      'full_name', v_match.full_name,
      'avatar_url', v_match.avatar_url,
      'university', v_match.university,
      'faculty', v_match.faculty,
      'department', v_match.department,
      'academic_level', v_match.academic_level,
      'relationship_status', v_match.relationship_status,
      'online_now', v_match.online_now,
      'last_seen_at', v_match.last_seen_at,
      'compatibility_score', v_match.final_score,
      'common_skills', coalesce(v_match.common_skills, '{}'::text[]),
      'common_hobbies', coalesce(v_match.common_hobbies, '{}'::text[])
    ),
    'coins_spent', case when v_free_spin then 0 else 10 end,
    'remaining_coins', floor(v_balance - case when v_free_spin then 0 else 10 end)::bigint,
    'match_reasons', to_jsonb(array_remove(array[
      case
        when cardinality(coalesce(v_match.common_skills, '{}'::text[])) > 0
        then 'Shared skills'
      end,
      case
        when cardinality(coalesce(v_match.common_hobbies, '{}'::text[])) > 0
        then 'Shared interests'
      end,
      case
        when v_match.semantic_score > 0
        then 'Matches your type preference'
      end,
      case
        when v_match.online_now is true
        then 'Online now'
      end,
      case
        when nullif(btrim(p_university), '') is not null
        then 'University match'
      end
    ], null))
  );
end;
$function$


-- Idempotent backfill may be run after migration, under admin/service-role only.

-- Explicit execution grants for reward handlers and the backfill utility.
REVOKE EXECUTE ON FUNCTION public.broadcast_apply_reward(uuid,uuid,uuid,public.broadcast_reward_type,numeric,text) FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_campaign_reward(uuid,uuid,integer) FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.record_broadcast_campaign_event(public.broadcast_goal_type,uuid,text,text,integer,timestamptz) FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_coin_purchase_campaign_trigger() FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_backfill_campaign_progress() FROM PUBLIC,anon;
REVOKE EXECUTE ON FUNCTION public.redeem_broadcast_fee_voucher(uuid,numeric,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.broadcast_apply_reward(uuid,uuid,uuid,public.broadcast_reward_type,numeric,text),
  public.broadcast_campaign_reward(uuid,uuid,integer),public.record_broadcast_campaign_event(public.broadcast_goal_type,uuid,text,text,integer,timestamptz),
  public.broadcast_coin_purchase_campaign_trigger(),public.redeem_broadcast_fee_voucher(uuid,numeric,text) TO service_role;
GRANT EXECUTE ON FUNCTION public.broadcast_backfill_campaign_progress() TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.spin_connect_match(text,text,text,text,text,text,boolean) TO authenticated,service_role;
