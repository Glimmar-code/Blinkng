-- Opt-in Paystack recurring billing: production disabled independently by env.
-- Must follow the verified Paystack checkout and notification migrations.
-- No customers are automatically enrolled. No credit card data is stored.
CREATE TABLE IF NOT EXISTS private.blink_paystack_renewals (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
 original_payment_id uuid NOT NULL REFERENCES public.blink_paystack_payments(id),
 tier text NOT NULL CHECK(tier IN ('BLUE','GOLD')),
 months integer NOT NULL CHECK(months IN(1,3,6,12)),
 amount_ngn integer NOT NULL CHECK(amount_ngn>0),
 plan_code text NOT NULL,
 customer_code text NOT NULL,
 subscription_code text UNIQUE,
 email_token text,
 consent_at timestamptz NOT NULL DEFAULT now(),
 consent_version text NOT NULL,
 status text NOT NULL DEFAULT 'creating'
   CHECK(status IN ('creating','active','cancel_pending','cancelled','error')),
 last_renewed_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(original_payment_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS blink_paystack_one_open_renewal_per_user
 ON private.blink_paystack_renewals(user_id)
 WHERE status IN('creating','active','cancel_pending');
ALTER TABLE private.blink_paystack_renewals ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON private.blink_paystack_renewals FROM PUBLIC,anon,authenticated;
GRANT SELECT,INSERT,UPDATE ON private.blink_paystack_renewals TO service_role;

CREATE OR REPLACE FUNCTION public.prepare_blink_paystack_renewal(
 p_user uuid,p_reference text,p_plan_code text,p_customer_code text,p_consent_version text
) RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path=''
AS $prepare_renewal$
DECLARE v_payment public.blink_paystack_payments%rowtype;
 v_expiry timestamptz; v_id uuid;
BEGIN
 IF p_user IS NULL OR p_consent_version <> 'v1.2026-10'
    OR p_plan_code !~ '^PLN_[a-zA-Z0-9]+$'
    OR p_customer_code !~ '^CUS_[a-zA-Z0-9]+$'
 THEN RAISE EXCEPTION 'RECURRING_CONSENT_INVALID'; END IF;
 SELECT * INTO v_payment FROM public.blink_paystack_payments
  WHERE reference=p_reference AND user_id=p_user AND status='fulfilled'
   AND purchase_kind='verification' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'PAID_VERIFICATION_REQUIRED'; END IF;
 SELECT verification_expires_at INTO v_expiry FROM public.profiles WHERE id=p_user;
 IF v_expiry IS NULL OR v_expiry<=now() THEN
   RAISE EXCEPTION 'EXPIRING_PAID_MEMBERSHIP_REQUIRED'; END IF;
 IF EXISTS(SELECT 1 FROM private.blink_paystack_renewals
           WHERE user_id=p_user AND status IN ('creating','active','cancel_pending'))
 THEN RAISE EXCEPTION 'RENEWAL_ALREADY_ENROLLED'; END IF;
 INSERT INTO private.blink_paystack_renewals(
   user_id,original_payment_id,tier,months,amount_ngn,
   plan_code,customer_code,consent_version)
 VALUES(p_user,v_payment.id,v_payment.item_code,
   coalesce(v_payment.duration_months,1),v_payment.amount_ngn,
   p_plan_code,p_customer_code,p_consent_version)
 RETURNING id INTO v_id;
 RETURN jsonb_build_object('id',v_id,'valid_until',v_expiry);
END
$prepare_renewal$;
REVOKE ALL ON FUNCTION public.prepare_blink_paystack_renewal(
 uuid,text,text,text,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.prepare_blink_paystack_renewal(
 uuid,text,text,text,text) TO service_role;

CREATE OR REPLACE FUNCTION public.finish_blink_paystack_renewal(
 p_id uuid,p_subscription_code text,p_email_token text
) RETURNS void LANGUAGE plpgsql SECURITY INVOKER SET search_path=''
AS $finish_renewal$
BEGIN
 IF p_subscription_code !~ '^SUB_[a-zA-Z0-9]+$'
  OR coalesce(length(p_email_token),0)<8 THEN
  RAISE EXCEPTION 'PROVIDER_SUBSCRIPTION_INVALID'; END IF;
 UPDATE private.blink_paystack_renewals SET subscription_code=p_subscription_code,
  email_token=p_email_token,status='active',updated_at=now()
 WHERE id=p_id AND status='creating';
 IF NOT FOUND THEN RAISE EXCEPTION 'ENROLLMENT_STATE_CHANGED'; END IF;
END
$finish_renewal$;
REVOKE ALL ON FUNCTION public.finish_blink_paystack_renewal(uuid,text,text)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.finish_blink_paystack_renewal(uuid,text,text)
 TO service_role;

CREATE OR REPLACE FUNCTION public.my_blink_paystack_renewal()
RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path=''
AS $renewal_status$
 SELECT coalesce((
   SELECT jsonb_build_object('id',r.id,'tier',r.tier,'months',r.months,
    'price_ngn',r.amount_ngn,'status',r.status,
    'auto_renew',r.status='active','consent_at',r.consent_at)
   FROM private.blink_paystack_renewals r
   WHERE r.user_id=auth.uid() ORDER BY r.created_at DESC LIMIT 1
 ),'{"status":"none","auto_renew":false}'::jsonb)
$renewal_status$;
REVOKE ALL ON FUNCTION public.my_blink_paystack_renewal() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.my_blink_paystack_renewal() TO authenticated;

CREATE OR REPLACE FUNCTION public.stop_blink_paystack_renewal(
 p_id uuid,p_user uuid
) RETURNS boolean LANGUAGE plpgsql SECURITY INVOKER SET search_path=''
AS $stop_renewal$
BEGIN
 UPDATE private.blink_paystack_renewals SET status='cancelled',updated_at=now()
 WHERE id=p_id AND user_id=p_user AND status IN('active','cancel_pending');
 RETURN FOUND;
END
$stop_renewal$;
REVOKE ALL ON FUNCTION public.stop_blink_paystack_renewal(uuid,uuid)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.stop_blink_paystack_renewal(uuid,uuid)
 TO service_role;

-- Confirmed provider payment only; callable from trusted server after HTTPS
-- verification of subscription_code, customer_code, plan_code and amount.
CREATE OR REPLACE FUNCTION public.fulfill_blink_paystack_renewal(
 p_code text,p_reference text,p_transaction_id text,p_amount_kobo bigint,p_currency text
) RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path=''
AS $fulfill_renewal$
DECLARE v_sub private.blink_paystack_renewals%rowtype;
 v_existing public.blink_paystack_payments%rowtype;
 v_prior_tier text;v_prior_expiry timestamptz;
 v_new_expiry timestamptz;v_ref text;v_payment_id uuid;
BEGIN
 SELECT * INTO v_sub FROM private.blink_paystack_renewals
 WHERE subscription_code=p_code FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'UNKNOWN_SUBSCRIPTION'; END IF;
 IF v_sub.status NOT IN('active','cancel_pending') THEN
   RAISE EXCEPTION 'SUBSCRIPTION_NOT_ACTIVE'; END IF;
 IF p_reference IS NULL OR p_reference='' OR length(p_reference)>140 OR
  nullif(p_transaction_id,'') IS NULL OR p_currency <> 'NGN'
  OR p_amount_kobo <> v_sub.amount_ngn::bigint*100 THEN
   RAISE EXCEPTION 'RENEWAL_PAYMENT_MISMATCH'; END IF;
 SELECT * INTO v_existing FROM public.blink_paystack_payments
 WHERE paystack_transaction_id=p_transaction_id;
 IF FOUND THEN
   IF v_existing.user_id<>v_sub.user_id OR v_existing.amount_ngn<>v_sub.amount_ngn
   THEN RAISE EXCEPTION 'REUSED_PROVIDER_TRANSACTION'; END IF;
   RETURN jsonb_build_object('status','fulfilled','already_fulfilled',true);
 END IF;
 SELECT upper(coalesce(verification_badge,'NONE')),verification_expires_at
 INTO v_prior_tier,v_prior_expiry
 FROM public.profiles WHERE id=v_sub.user_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'PROFILE_NOT_FOUND'; END IF;
 v_new_expiry:=CASE
  WHEN v_sub.tier='BLUE' AND v_prior_tier='GOLD'
    AND (v_prior_expiry IS NULL OR v_prior_expiry>now())
    THEN v_prior_expiry
  WHEN v_sub.tier=v_prior_tier AND v_prior_expiry IS NULL
    THEN NULL
  ELSE greatest(coalesce(v_prior_expiry,now()),now())
       +make_interval(months=>v_sub.months) END;
 UPDATE public.profiles SET
  verification_badge=CASE WHEN v_sub.tier='BLUE' AND v_prior_tier='GOLD'
   AND (v_prior_expiry IS NULL OR v_prior_expiry>now()) THEN 'GOLD'
   ELSE v_sub.tier END,
  verification_tier=(CASE WHEN v_sub.tier='BLUE' AND v_prior_tier='GOLD'
    AND (v_prior_expiry IS NULL OR v_prior_expiry>now()) THEN 'Gold'
    WHEN v_sub.tier='GOLD' THEN 'Gold' ELSE 'Standard' END)::public.verification_tier_enum,
  is_verified=true,verified_at=coalesce(verified_at,now()),
  verification_expires_at=v_new_expiry,updated_at=now()
 WHERE id=v_sub.user_id;
 v_ref:='blink-ps-'||replace(gen_random_uuid()::text,'-','');
 INSERT INTO public.blink_paystack_payments(
  user_id,reference,purchase_kind,item_code,amount_ngn,
  duration_days,duration_months,paystack_transaction_id,status,paid_at)
 VALUES(v_sub.user_id,v_ref,'verification',v_sub.tier,v_sub.amount_ngn,
  CASE WHEN v_sub.months=12 THEN 365 ELSE v_sub.months*30 END,
  v_sub.months,p_transaction_id,'pending',now())
 RETURNING id INTO v_payment_id;
 UPDATE public.blink_paystack_payments SET status='fulfilled',updated_at=now()
 WHERE id=v_payment_id;
 UPDATE private.blink_paystack_renewals SET last_renewed_at=now(),
  updated_at=now() WHERE id=v_sub.id;
 RETURN jsonb_build_object('status','fulfilled','already_fulfilled',false,
    'payment_id',v_payment_id);
END
$fulfill_renewal$;
REVOKE ALL ON FUNCTION public.fulfill_blink_paystack_renewal(
 text,text,text,bigint,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.fulfill_blink_paystack_renewal(
 text,text,text,bigint,text) TO service_role;
