-- BLINK Paystack Market and Gift checkout; seller-payment and refunds staging.
-- Safe defaults: no cash checkout activation, no automatic seller transfers/refunds.
-- Rollout requires approved adult merchant, provider terms, staging tests and owner review.

INSERT INTO private.blink_economy_config(key,bool_value) VALUES
 ('paystack_market_checkout_enabled',false),
 ('paystack_gifts_enabled',false),
 ('paystack_refunds_enabled',false),
 ('paystack_payouts_enabled',false)
ON CONFLICT(key) DO NOTHING;

ALTER TABLE public.blink_paystack_payments
 DROP CONSTRAINT IF EXISTS blink_paystack_payments_purchase_kind_check;
ALTER TABLE public.blink_paystack_payments
 ADD CONSTRAINT blink_paystack_payments_purchase_kind_check
 CHECK(purchase_kind IN('verification','coin_pack','boost','marketplace','gift'));
ALTER TABLE public.blink_paystack_payments DROP CONSTRAINT IF EXISTS paystack_order_shape;
ALTER TABLE public.blink_paystack_payments ADD CONSTRAINT paystack_order_shape CHECK(
  (purchase_kind='verification' AND item_code IN('BLUE','GOLD')
    AND coin_order_id IS NULL AND coin_amount IS NULL AND duration_days IS NOT NULL)
  OR (purchase_kind='coin_pack' AND coin_order_id IS NOT NULL AND coin_amount IS NOT NULL
    AND duration_days IS NULL)
  OR (purchase_kind='gift' AND item_code='GIFT_COINS' AND coin_order_id IS NOT NULL
    AND coin_amount IS NOT NULL AND duration_days IS NULL AND duration_months IS NULL)
  OR (purchase_kind IN('boost','marketplace') AND item_code IN('BOOST','MARKET_ITEM')
    AND coin_order_id IS NULL AND coin_amount IS NULL AND duration_days IS NULL
    AND duration_months IS NULL)
);

CREATE TABLE IF NOT EXISTS public.blink_paystack_market_orders (
 payment_id uuid PRIMARY KEY REFERENCES public.blink_paystack_payments(id),
 marketplace_order_id uuid NOT NULL UNIQUE REFERENCES public.marketplace_orders(id),
 listing_id uuid NOT NULL REFERENCES public.market_items(id),
 buyer_id uuid NOT NULL REFERENCES public.profiles(id),
 seller_id uuid NOT NULL REFERENCES public.profiles(id),
 quantity integer NOT NULL DEFAULT 1 CHECK(quantity=1),
 amount_ngn integer NOT NULL CHECK(amount_ngn>0),
 fulfilment_status text NOT NULL DEFAULT 'awaiting_payment'
   CHECK(fulfilment_status IN('awaiting_payment','paid_hold','delivered','disputed','refunding','refunded','settled','review_required')),
 paid_at timestamptz,
 delivered_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.blink_paystack_market_orders ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.blink_paystack_market_orders FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.blink_paystack_market_orders TO authenticated;
GRANT SELECT,INSERT,UPDATE ON public.blink_paystack_market_orders TO service_role;
DROP POLICY IF EXISTS blink_paystack_market_participants_read ON public.blink_paystack_market_orders;
CREATE POLICY blink_paystack_market_participants_read
 ON public.blink_paystack_market_orders FOR SELECT TO authenticated
 USING(buyer_id=(SELECT auth.uid()) OR seller_id=(SELECT auth.uid()));

CREATE TABLE IF NOT EXISTS public.blink_paystack_gifts (
 payment_id uuid PRIMARY KEY REFERENCES public.blink_paystack_payments(id),
 sender_id uuid NOT NULL REFERENCES public.profiles(id),
 recipient_id uuid NOT NULL REFERENCES public.profiles(id),
 coin_amount integer NOT NULL CHECK(coin_amount>0),
 message text CHECK(message IS NULL OR length(message)<=160),
 created_at timestamptz NOT NULL DEFAULT now(),
 CHECK(sender_id<>recipient_id)
);
ALTER TABLE public.blink_paystack_gifts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.blink_paystack_gifts FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.blink_paystack_gifts TO authenticated;
GRANT SELECT,INSERT,UPDATE ON public.blink_paystack_gifts TO service_role;
DROP POLICY IF EXISTS blink_paystack_gift_participants_read ON public.blink_paystack_gifts;
CREATE POLICY blink_paystack_gift_participants_read
 ON public.blink_paystack_gifts FOR SELECT TO authenticated
 USING(sender_id=(SELECT auth.uid()) OR recipient_id=(SELECT auth.uid()));

CREATE TABLE IF NOT EXISTS public.blink_paystack_refunds (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 payment_id uuid NOT NULL REFERENCES public.blink_paystack_payments(id),
 requested_by uuid NOT NULL REFERENCES public.profiles(id),
 reason text NOT NULL CHECK(length(reason) BETWEEN 10 AND 1000),
 amount_kobo bigint NOT NULL CHECK(amount_kobo>0),
 provider_refund_id text UNIQUE,
 status text NOT NULL DEFAULT 'requested'
   CHECK(status IN('requested','approved','submitted','processing','completed','rejected','failed')),
 reviewed_by uuid REFERENCES public.profiles(id),
 reviewed_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS paystack_active_refund_per_payment
 ON public.blink_paystack_refunds(payment_id)
 WHERE status IN('requested','approved','submitted','processing','completed');
ALTER TABLE public.blink_paystack_refunds ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.blink_paystack_refunds FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.blink_paystack_refunds TO authenticated;
GRANT SELECT,INSERT,UPDATE ON public.blink_paystack_refunds TO service_role;
DROP POLICY IF EXISTS blink_paystack_refund_customer_read ON public.blink_paystack_refunds;
CREATE POLICY blink_paystack_refund_customer_read
 ON public.blink_paystack_refunds FOR SELECT TO authenticated
 USING(requested_by=(SELECT auth.uid()));

-- Finance features must not leak bank account numbers to the app or an unapproved seller.
CREATE TABLE IF NOT EXISTS private.blink_paystack_seller_destinations (
 seller_id uuid PRIMARY KEY REFERENCES public.profiles(id),
 provider_recipient_code text NOT NULL UNIQUE CHECK(provider_recipient_code ~ '^RCP_[A-Za-z0-9]+$'),
 verified_by uuid NOT NULL REFERENCES public.profiles(id),
 verified_at timestamptz NOT NULL DEFAULT now()
);
REVOKE ALL ON private.blink_paystack_seller_destinations FROM PUBLIC,anon,authenticated;
CREATE TABLE IF NOT EXISTS public.blink_paystack_seller_payouts (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 payment_id uuid NOT NULL UNIQUE REFERENCES public.blink_paystack_payments(id),
 seller_id uuid NOT NULL REFERENCES public.profiles(id),
 amount_kobo bigint NOT NULL CHECK(amount_kobo>0),
 provider_transfer_ref text UNIQUE,
 provider_transfer_id text,
 status text NOT NULL DEFAULT 'blocked'
   CHECK(status IN('blocked','eligible','approved','submitted','processing','completed','failed','cancelled')),
 approved_by uuid REFERENCES public.profiles(id),
 approved_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.blink_paystack_seller_payouts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.blink_paystack_seller_payouts FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.blink_paystack_seller_payouts TO authenticated;
GRANT SELECT,INSERT,UPDATE ON public.blink_paystack_seller_payouts TO service_role;
DROP POLICY IF EXISTS blink_paystack_seller_payout_own_read ON public.blink_paystack_seller_payouts;
CREATE POLICY blink_paystack_seller_payout_own_read
 ON public.blink_paystack_seller_payouts FOR SELECT TO authenticated
 USING(seller_id=(SELECT auth.uid()));

-- Called with auth.uid() inside an authenticated Supabase RPC; amount and item
-- are always read from server-owned listings, never from the Android request.
CREATE OR REPLACE FUNCTION public.create_blink_paystack_market_order(p_listing uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $market_checkout$
DECLARE v_uid uuid:=auth.uid();v_listing public.market_items%rowtype;
 v_ref text;v_payment uuid;v_market_order uuid;v_enabled boolean;v_price integer;
BEGIN
 IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
 IF p_listing IS NULL THEN RAISE EXCEPTION 'LISTING_REQUIRED'; END IF;
 SELECT coalesce(bool_value,false) INTO v_enabled FROM private.blink_economy_config
   WHERE key='cash_checkout_enabled';
 IF NOT coalesce(v_enabled,false) THEN RAISE EXCEPTION 'CASH_CHECKOUT_DISABLED'; END IF;
 SELECT coalesce(bool_value,false) INTO v_enabled FROM private.blink_economy_config
   WHERE key='paystack_market_checkout_enabled';
 IF NOT coalesce(v_enabled,false) THEN RAISE EXCEPTION 'MARKET_CHECKOUT_DISABLED'; END IF;
 PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(p_listing::text)::bigint);
 SELECT * INTO v_listing FROM public.market_items WHERE id=p_listing FOR UPDATE;
 IF NOT FOUND OR v_listing.status<>'active' OR coalesce(v_listing.is_sold,false)
     OR v_listing.moderation_state<>'approved' OR v_listing.quantity<1
     OR v_listing.currency<>'NGN' OR v_listing.seller_id=v_uid
     OR NOT public.market_seller_has_approval(v_listing.seller_id)
 THEN RAISE EXCEPTION 'LISTING_NOT_AVAILABLE'; END IF;
 IF v_listing.price<100 OR v_listing.price>1000000
 THEN RAISE EXCEPTION 'MARKET_PRICE_UNAVAILABLE'; END IF;
 IF EXISTS (
   SELECT 1 FROM public.blink_paystack_market_orders m
   JOIN public.blink_paystack_payments p ON p.id=m.payment_id
   WHERE m.listing_id=p_listing AND
   (m.fulfilment_status IN('paid_hold','delivered','disputed','refunding','settled','review_required')
      OR (p.status='pending' AND p.created_at>now()-interval '30 minutes'))
 ) THEN RAISE EXCEPTION 'LISTING_CHECKOUT_BUSY'; END IF;
 v_price:=v_listing.price::integer;
 v_ref:='blink-ps-'||replace(gen_random_uuid()::text,'-','');
 INSERT INTO public.blink_paystack_payments
  (user_id,reference,purchase_kind,item_code,amount_ngn)
  VALUES(v_uid,v_ref,'marketplace','MARKET_ITEM',v_price)
  RETURNING id INTO v_payment;
 INSERT INTO public.marketplace_orders
   (item_id,buyer_id,seller_id,quantity,unit_price,total_price,currency,status)
 VALUES(p_listing,v_uid,v_listing.seller_id,1,v_price,v_price,'NGN','pending')
 RETURNING id INTO v_market_order;
 INSERT INTO public.blink_paystack_market_orders
  (payment_id,marketplace_order_id,listing_id,buyer_id,seller_id,amount_ngn)
 VALUES(v_payment,v_market_order,p_listing,v_uid,v_listing.seller_id,v_price);
 RETURN jsonb_build_object('reference',v_ref,'kind','marketplace','item_code','MARKET_ITEM',
 'amount_ngn',v_price,'currency','NGN','status','pending');
END;
$market_checkout$;
REVOKE ALL ON FUNCTION public.create_blink_paystack_market_order(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.create_blink_paystack_market_order(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.create_blink_paystack_gift_order(
 p_username text,p_pack_id text,p_message text DEFAULT NULL
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $gift_checkout$
DECLARE v_uid uuid:=auth.uid();v_recipient uuid;v_ref text;v_payment uuid;v_coin_order uuid;
 v_packs jsonb;v_pack jsonb;v_price integer;v_coins integer;v_enabled boolean;
BEGIN
 IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
 SELECT coalesce(bool_value,false) INTO v_enabled FROM private.blink_economy_config
   WHERE key='cash_checkout_enabled';
 IF NOT coalesce(v_enabled,false) THEN RAISE EXCEPTION 'CASH_CHECKOUT_DISABLED'; END IF;
 SELECT coalesce(bool_value,false) INTO v_enabled FROM private.blink_economy_config
   WHERE key='paystack_gifts_enabled';
 IF NOT coalesce(v_enabled,false) THEN RAISE EXCEPTION 'GIFT_PURCHASE_DISABLED'; END IF;
 IF p_message IS NOT NULL AND length(p_message)>160 THEN
   RAISE EXCEPTION 'GIFT_MESSAGE_TOO_LONG'; END IF;
 SELECT id INTO v_recipient FROM public.profiles
 WHERE lower(username)=lower(trim(p_username)) LIMIT 1;
 IF v_recipient IS NULL OR v_recipient=v_uid THEN RAISE EXCEPTION 'GIFT_RECIPIENT_INVALID'; END IF;
 SELECT json_value INTO v_packs FROM private.blink_economy_config WHERE key='coin_packs';
 SELECT value INTO v_pack FROM jsonb_array_elements(coalesce(v_packs,'[]'::jsonb))
 WHERE value->>'id'=p_pack_id LIMIT 1;
 IF v_pack IS NULL THEN RAISE EXCEPTION 'GIFT_PACK_UNAVAILABLE'; END IF;
 v_price:=(v_pack->>'price_ngn')::integer;
 v_coins:=(v_pack->>'coins')::integer;
 IF v_price<50 OR v_coins<1 THEN RAISE EXCEPTION 'GIFT_PACK_UNAVAILABLE'; END IF;
 PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_uid::text)::bigint);
 IF (SELECT count(*) FROM public.blink_paystack_payments
     WHERE user_id=v_uid AND created_at>now()-interval '30 minutes')>=8
 THEN RAISE EXCEPTION 'PAYMENT_RATE_LIMIT'; END IF;
 INSERT INTO public.blink_coin_purchase_orders
  (user_id,pack_id,provider,amount_ngn,coin_amount,bonus_coins)
 VALUES(v_recipient,p_pack_id,'paystack',v_price,v_coins,greatest(v_coins-v_price,0))
 RETURNING id INTO v_coin_order;
 v_ref:='blink-ps-'||replace(gen_random_uuid()::text,'-','');
 INSERT INTO public.blink_paystack_payments
  (user_id,reference,purchase_kind,item_code,coin_order_id,amount_ngn,coin_amount)
 VALUES(v_uid,v_ref,'gift','GIFT_COINS',v_coin_order,v_price,v_coins)
 RETURNING id INTO v_payment;
 INSERT INTO public.blink_paystack_gifts
  (payment_id,sender_id,recipient_id,coin_amount,message)
 VALUES(v_payment,v_uid,v_recipient,v_coins,p_message);
 RETURN jsonb_build_object('reference',v_ref,'kind','gift','item_code','GIFT_COINS',
   'amount_ngn',v_price,'currency','NGN','status','pending');
END;
$gift_checkout$;
REVOKE ALL ON FUNCTION public.create_blink_paystack_gift_order(text,text,text)
 FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.create_blink_paystack_gift_order(text,text,text) TO authenticated;

-- Provider-verified payment fulfilment happens atomically inside existing payment
-- RPC. Marketplace funds remain on BLINK until delivery, dispute review and
-- seller settlement permission; Paystack is NOT an escrow service.
CREATE OR REPLACE FUNCTION public.fulfill_blink_paystack_market_order(p_payment uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path=''
AS $market_fulfill$
DECLARE v_market public.blink_paystack_market_orders%rowtype;
 v_listing public.market_items%rowtype;
BEGIN
 SELECT * INTO v_market FROM public.blink_paystack_market_orders
 WHERE payment_id=p_payment FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'MARKET_ORDER_MISSING'; END IF;
 IF v_market.fulfilment_status='paid_hold'
 THEN RETURN jsonb_build_object('status','paid_hold','already_fulfilled',true); END IF;
 IF v_market.fulfilment_status<>'awaiting_payment'
 THEN RAISE EXCEPTION 'MARKET_PAYMENT_IN_REVIEW'; END IF;
 SELECT * INTO v_listing FROM public.market_items
 WHERE id=v_market.listing_id FOR UPDATE;
 IF NOT FOUND OR v_listing.quantity<1 OR v_listing.status<>'active'
   OR NOT public.market_seller_has_approval(v_market.seller_id)
 THEN
   UPDATE public.blink_paystack_market_orders SET fulfilment_status='review_required'
   WHERE payment_id=p_payment;
   -- The paid order stays recorded and cannot silently be delivered.
   RETURN jsonb_build_object('status','review_required','needs_refund_review',true);
 END IF;
 UPDATE public.market_items SET quantity=quantity-1,
   is_sold=(quantity=1),
   status=CASE WHEN quantity=1 THEN 'sold' ELSE status END,
   updated_at=now() WHERE id=v_market.listing_id;
 UPDATE public.marketplace_orders SET status='paid',updated_at=now()
 WHERE id=v_market.marketplace_order_id;
 UPDATE public.blink_paystack_market_orders
 SET fulfilment_status='paid_hold',paid_at=now() WHERE payment_id=p_payment;
 INSERT INTO public.blink_paystack_seller_payouts
   (payment_id,seller_id,amount_kobo)
 VALUES(p_payment,v_market.seller_id,v_market.amount_ngn::bigint*100)
 ON CONFLICT(payment_id) DO NOTHING;
 RETURN jsonb_build_object('status','paid_hold','already_fulfilled',false);
END;
$market_fulfill$;
REVOKE ALL ON FUNCTION public.fulfill_blink_paystack_market_order(uuid)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.fulfill_blink_paystack_market_order(uuid) TO service_role;

-- Use a new overloaded name for fee-free app gifts and marketplace payments.
-- This function supersedes the prior version, retaining the existing signature.
CREATE OR REPLACE FUNCTION public.fulfill_blink_paystack_order(
 p_reference text,p_transaction_id text,p_amount_kobo bigint,p_currency text
) RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path=''
AS $fulfil_all$
DECLARE v_order public.blink_paystack_payments%rowtype;
 v_badge text;v_expires timestamptz;v_new_badge text;v_new_expires timestamptz;
 v_result jsonb;
BEGIN
 SELECT * INTO v_order FROM public.blink_paystack_payments
 WHERE reference=p_reference FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'PAYMENT_ORDER_NOT_FOUND'; END IF;
 IF v_order.status='fulfilled' THEN
   IF v_order.paystack_transaction_id IS DISTINCT FROM p_transaction_id
   THEN RAISE EXCEPTION 'PAYMENT_TRANSACTION_MISMATCH'; END IF;
   RETURN jsonb_build_object('status','fulfilled','already_fulfilled',true);
 END IF;
 IF v_order.status<>'pending' OR nullif(trim(coalesce(p_transaction_id,'')),'') IS NULL
   OR p_amount_kobo<>v_order.amount_ngn::bigint*100 OR p_currency<>'NGN'
 THEN RAISE EXCEPTION 'PAYMENT_VERIFICATION_MISMATCH'; END IF;
 IF v_order.purchase_kind IN('coin_pack','gift') THEN
   v_result:=public.fulfill_blink_coin_purchase_order(v_order.coin_order_id,v_order.reference);
 ELSIF v_order.purchase_kind='boost' THEN
   v_result:=public.fulfill_blink_paystack_boost(v_order.id);
 ELSIF v_order.purchase_kind='marketplace' THEN
   v_result:=public.fulfill_blink_paystack_market_order(v_order.id);
 ELSE
   SELECT upper(coalesce(verification_badge,'NONE')),verification_expires_at
   INTO v_badge,v_expires FROM public.profiles WHERE id=v_order.user_id FOR UPDATE;
   IF NOT FOUND THEN RAISE EXCEPTION 'PROFILE_NOT_FOUND'; END IF;
   v_new_badge:=CASE WHEN v_badge='GOLD' AND (v_expires IS NULL OR v_expires>now())
     THEN 'GOLD' ELSE v_order.item_code END;
   v_new_expires:=CASE WHEN v_badge=v_order.item_code AND v_expires IS NULL THEN NULL
     ELSE greatest(coalesce(v_expires,now()),now()) +
       make_interval(days=>coalesce(v_order.duration_days,30)) END;
   UPDATE public.profiles SET verification_badge=v_new_badge,
     verification_tier=(CASE WHEN v_new_badge='GOLD' THEN 'Gold' ELSE 'Standard' END)::public.verification_tier_enum,
     is_verified=true,verified_at=coalesce(verified_at,now()),
     verification_expires_at=v_new_expires,updated_at=now()
    WHERE id=v_order.user_id;
 END IF;
 UPDATE public.blink_paystack_payments SET status='fulfilled',
   paystack_transaction_id=p_transaction_id,paid_at=now(),updated_at=now()
 WHERE id=v_order.id;
 RETURN jsonb_build_object('status','fulfilled','already_fulfilled',false,
   'kind',v_order.purchase_kind,'item_code',v_order.item_code,'result',v_result);
END;
$fulfil_all$;
REVOKE ALL ON FUNCTION public.fulfill_blink_paystack_order(text,text,bigint,text)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.fulfill_blink_paystack_order(text,text,bigint,text)
 TO service_role;

-- Refund requests create a review queue. Money is moved only via an explicitly
-- enabled, independently authenticated server-to-provider refund call.
CREATE OR REPLACE FUNCTION public.request_blink_paystack_refund(
 p_reference text,p_reason text
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $refund_request$
DECLARE v_uid uuid:=auth.uid();v_payment public.blink_paystack_payments%rowtype;
 v_id uuid;
BEGIN
 IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
 IF length(trim(coalesce(p_reason,''))) NOT BETWEEN 10 AND 1000
 THEN RAISE EXCEPTION 'INVALID_REFUND_REASON'; END IF;
 SELECT * INTO v_payment FROM public.blink_paystack_payments
 WHERE reference=p_reference AND user_id=v_uid AND status='fulfilled' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'REFUND_NOT_ELIGIBLE'; END IF;
 INSERT INTO public.blink_paystack_refunds(payment_id,requested_by,reason,amount_kobo)
 VALUES(v_payment.id,v_uid,trim(p_reason),v_payment.amount_ngn::bigint*100)
 ON CONFLICT DO NOTHING RETURNING id INTO v_id;
 IF v_id IS NULL THEN
   SELECT id INTO v_id FROM public.blink_paystack_refunds
   WHERE payment_id=v_payment.id AND
   status IN('requested','approved','submitted','processing','completed') LIMIT 1;
 END IF;
 RETURN jsonb_build_object('request_id',v_id,'status','review_required');
END;
$refund_request$;
REVOKE ALL ON FUNCTION public.request_blink_paystack_refund(text,text)
 FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.request_blink_paystack_refund(text,text) TO authenticated;
