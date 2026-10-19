-- Safe Testlab-only Paystack phase 2: discounted one-time plans, owner receipts,
-- service-only reporting and a transactional notification outbox.
-- This does NOT enable charging or automatic recurring card debits.
-- Run after 20261018225000_paystack_checkout.sql. Keep live checkout OFF.

CREATE OR REPLACE FUNCTION public.create_blink_paystack_order_v2(
  p_kind text, p_item_code text, p_months integer
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $paystack_plans$
DECLARE
  v_user uuid := auth.uid();
  v_kind text := lower(trim(coalesce(p_kind,'')));
  v_item text := upper(trim(coalesce(p_item_code,'')));
  v_badge text;
  v_expiry timestamptz;
  v_followers integer;
  v_enabled boolean;
  v_recent integer;
  v_monthly integer;
  v_discount integer;
  v_total integer;
  v_reference text;
BEGIN
  IF v_user IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
  IF p_months IS NULL OR p_months NOT IN (1,3,6,12) THEN
    RAISE EXCEPTION 'INVALID_DURATION';
  END IF;
  IF v_kind = 'coin_pack' THEN
    IF p_months <> 1 THEN RAISE EXCEPTION 'COIN_PACK_CANNOT_RECUR'; END IF;
    RETURN public.create_blink_paystack_order(p_kind,p_item_code);
  END IF;
  IF v_kind <> 'verification' OR v_item NOT IN ('BLUE','GOLD') THEN
    RAISE EXCEPTION 'INVALID_PURCHASE_KIND';
  END IF;
  IF p_months = 1 THEN
    RETURN public.create_blink_paystack_order(p_kind,p_item_code)
      || jsonb_build_object('months',1,'discount_percent',0,'auto_renew',false);
  END IF;
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text)::bigint);
  SELECT coalesce(bool_value,false) INTO v_enabled
    FROM private.blink_economy_config WHERE key='cash_checkout_enabled';
  IF NOT coalesce(v_enabled,false) THEN RAISE EXCEPTION 'CASH_CHECKOUT_DISABLED'; END IF;
  SELECT count(*) INTO v_recent FROM public.blink_paystack_payments
    WHERE user_id=v_user AND created_at > now()-interval '30 minutes';
  IF v_recent >= 8 THEN RAISE EXCEPTION 'PAYMENT_RATE_LIMIT'; END IF;
  SELECT upper(coalesce(verification_badge,'NONE')), verification_expires_at
    INTO v_badge,v_expiry FROM public.profiles WHERE id=v_user;
  IF NOT FOUND THEN RAISE EXCEPTION 'PROFILE_NOT_FOUND'; END IF;
  IF v_item='BLUE' AND v_badge='GOLD' AND
    (v_expiry IS NULL OR v_expiry>now()) THEN
      RAISE EXCEPTION 'ALREADY_GOLD_VERIFIED';
  END IF;
  IF v_item='GOLD' THEN
    SELECT count(*) INTO v_followers FROM public.follows WHERE following_id=v_user;
    IF v_followers < 1000 THEN RAISE EXCEPTION 'GOLD_FOLLOWER_REQUIREMENT_NOT_MET'; END IF;
  END IF;
  SELECT int_value::integer INTO v_monthly FROM private.blink_economy_config
    WHERE key=CASE WHEN v_item='BLUE' THEN
      'blue_verification_cash_ngn' ELSE 'gold_verification_cash_ngn' END;
  IF coalesce(v_monthly,0)<=0 THEN RAISE EXCEPTION 'INVALID_PAYMENT_AMOUNT'; END IF;
  v_discount:=CASE p_months WHEN 3 THEN 10 WHEN 6 THEN 20 WHEN 12 THEN 30 END;
  v_total:=(v_monthly*p_months*(100-v_discount))/100;
  IF v_total<=0 THEN RAISE EXCEPTION 'INVALID_PAYMENT_AMOUNT'; END IF;
  v_reference:='blink-ps-'||replace(gen_random_uuid()::text,'-','');
  INSERT INTO public.blink_paystack_payments
    (user_id,reference,purchase_kind,item_code,amount_ngn,duration_days)
    VALUES (v_user,v_reference,'verification',v_item,v_total,p_months*30);
  RETURN jsonb_build_object('reference',v_reference,'kind','verification',
    'item_code',v_item,'amount_ngn',v_total,'currency','NGN',
    'months',p_months,'discount_percent',v_discount,
    'auto_renew',false,'status','pending');
END
$paystack_plans$;
REVOKE ALL ON FUNCTION public.create_blink_paystack_order_v2(text,text,integer)
  FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.create_blink_paystack_order_v2(text,text,integer)
  TO authenticated;

-- RLS owner-only payment receipts, no payment card or banking details.
CREATE OR REPLACE FUNCTION public.get_my_blink_paystack_receipts(p_limit integer DEFAULT 30)
RETURNS jsonb LANGUAGE sql STABLE SECURITY INVOKER SET search_path = ''
AS $receipts$
  SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY r.created_at DESC),'[]'::jsonb)
  FROM (
    SELECT id,reference,purchase_kind,item_code,amount_ngn,currency,
      duration_days,status,created_at,paid_at
    FROM public.blink_paystack_payments
    WHERE user_id=auth.uid()
    ORDER BY created_at DESC
    LIMIT least(greatest(coalesce(p_limit,30),1),100)
  ) r
$receipts$;
REVOKE ALL ON FUNCTION public.get_my_blink_paystack_receipts(integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.get_my_blink_paystack_receipts(integer) TO authenticated;

-- Service-only financial report; NEVER accessible to every signed-in user.
CREATE OR REPLACE FUNCTION public.blink_paystack_financial_report(
  p_start timestamptz,p_end timestamptz
) RETURNS jsonb LANGUAGE sql STABLE SECURITY INVOKER SET search_path=''
AS $report$
 SELECT jsonb_build_object(
   'start',p_start,'end',p_end,
   'total_orders',count(*),
   'paid_orders',count(*) FILTER (WHERE status='fulfilled'),
   'revenue_ngn',coalesce(sum(amount_ngn) FILTER (WHERE status='fulfilled'),0),
   'pending_orders',count(*) FILTER (WHERE status='pending'),
   'failed_orders',count(*) FILTER (WHERE status='failed'),
   'refund_flagged_orders',count(*) FILTER (WHERE status='refunded')
 )
 FROM public.blink_paystack_payments
 WHERE created_at>=p_start AND created_at<p_end
$report$;
REVOKE ALL ON FUNCTION public.blink_paystack_financial_report(timestamptz,timestamptz)
  FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.blink_paystack_financial_report(timestamptz,timestamptz)
  TO service_role;

-- Payment email/push notifications require a sender worker. The outbox is
-- internal-only and inserted atomically on first successful fulfilment.
CREATE TABLE IF NOT EXISTS private.blink_paystack_notification_outbox (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_id uuid NOT NULL REFERENCES public.blink_paystack_payments(id),
  user_id uuid NOT NULL REFERENCES public.profiles(id),
  event_key text NOT NULL UNIQUE,
  kind text NOT NULL CHECK (kind IN ('payment_success')),
  status text NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending','sent','failed')),
  created_at timestamptz NOT NULL DEFAULT now(),
  sent_at timestamptz
);
REVOKE ALL ON private.blink_paystack_notification_outbox FROM PUBLIC,anon,authenticated;
GRANT SELECT,INSERT,UPDATE ON private.blink_paystack_notification_outbox TO service_role;
ALTER TABLE private.blink_paystack_notification_outbox ENABLE ROW LEVEL SECURITY;
CREATE OR REPLACE FUNCTION private.queue_blink_paystack_success()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=''
AS $notify$
BEGIN
  IF NEW.status='fulfilled' AND OLD.status IS DISTINCT FROM 'fulfilled' THEN
    INSERT INTO private.blink_paystack_notification_outbox
      (payment_id,user_id,event_key,kind)
      VALUES(NEW.id,NEW.user_id,'paid:'||NEW.reference,'payment_success')
      ON CONFLICT(event_key) DO NOTHING;
  END IF;
  RETURN NEW;
END
$notify$;
REVOKE ALL ON FUNCTION private.queue_blink_paystack_success()
  FROM PUBLIC,anon,authenticated;
DROP TRIGGER IF EXISTS blink_paystack_paid_notice ON public.blink_paystack_payments;
CREATE TRIGGER blink_paystack_paid_notice
AFTER UPDATE OF status ON public.blink_paystack_payments
FOR EACH ROW EXECUTE FUNCTION private.queue_blink_paystack_success();

-- Legal or provider-required reversals cannot be switched off by a
-- merchant preference. This work does not offer voluntary refunds.
