-- Payment and renewal emails: at-most-once Brevo dispatch (never expose client writes).
-- No email worker enabled without a separately configured internal secret.
ALTER TABLE private.blink_paystack_notification_outbox
 ADD COLUMN IF NOT EXISTS attempts integer NOT NULL DEFAULT 0,
 ADD COLUMN IF NOT EXISTS available_at timestamptz NOT NULL DEFAULT now(),
 ADD COLUMN IF NOT EXISTS last_error text,
 ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now(),
 ADD COLUMN IF NOT EXISTS subscription_id uuid REFERENCES private.blink_paystack_renewals(id);
ALTER TABLE private.blink_paystack_notification_outbox
 ALTER COLUMN payment_id DROP NOT NULL;
ALTER TABLE private.blink_paystack_notification_outbox
 DROP CONSTRAINT IF EXISTS blink_paystack_notification_outbox_kind_check;
ALTER TABLE private.blink_paystack_notification_outbox
 ADD CONSTRAINT blink_paystack_notification_outbox_kind_check
 CHECK(kind IN('payment_success','renewal_failed','renewal_reminder','renewal_cancelled'));
ALTER TABLE private.blink_paystack_notification_outbox
 DROP CONSTRAINT IF EXISTS blink_paystack_notification_outbox_status_check;
ALTER TABLE private.blink_paystack_notification_outbox
 ADD CONSTRAINT blink_paystack_notification_outbox_status_check
 CHECK(status IN('pending','processing','sent','failed'));
ALTER TABLE private.blink_paystack_notification_outbox
 ADD CONSTRAINT blink_paystack_notice_has_subject
 CHECK(payment_id IS NOT NULL OR subscription_id IS NOT NULL);
REVOKE ALL ON private.blink_paystack_notification_outbox FROM PUBLIC,anon,authenticated;
GRANT SELECT,INSERT,UPDATE ON private.blink_paystack_notification_outbox TO service_role;

CREATE OR REPLACE FUNCTION public.queue_blink_paystack_renewal_notice(
 p_subscription_code text,p_kind text,p_event_key text
) RETURNS boolean LANGUAGE plpgsql SECURITY INVOKER SET search_path=''
AS $enqueue_payment$
DECLARE v_sub private.blink_paystack_renewals%rowtype;
BEGIN
 IF p_kind NOT IN ('renewal_failed','renewal_reminder','renewal_cancelled')
   OR length(coalesce(p_event_key,''))<8 OR length(p_event_key)>160
 THEN RAISE EXCEPTION 'INVALID_NOTICE'; END IF;
 SELECT * INTO v_sub FROM private.blink_paystack_renewals
 WHERE subscription_code=p_subscription_code;
 IF NOT FOUND THEN RAISE EXCEPTION 'UNKNOWN_SUBSCRIPTION'; END IF;
 INSERT INTO private.blink_paystack_notification_outbox(
   payment_id,subscription_id,user_id,event_key,kind)
 VALUES(null,v_sub.id,v_sub.user_id,p_event_key,p_kind)
 ON CONFLICT(event_key) DO NOTHING;
 RETURN true;
END
$enqueue_payment$;
REVOKE ALL ON FUNCTION public.queue_blink_paystack_renewal_notice(text,text,text)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.queue_blink_paystack_renewal_notice(text,text,text)
 TO service_role;

CREATE OR REPLACE FUNCTION public.claim_blink_paystack_payment_notices(p_batch integer DEFAULT 10)
RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path=''
AS $claim_payment$
DECLARE v_result jsonb;
BEGIN
 WITH picked AS (
 SELECT id FROM private.blink_paystack_notification_outbox
 WHERE status='pending' AND available_at<=now()
 ORDER BY created_at LIMIT least(greatest(coalesce(p_batch,10),1),25)
 FOR UPDATE SKIP LOCKED
 ), claimed AS (
  UPDATE private.blink_paystack_notification_outbox n
  SET status='processing', attempts=attempts+1,updated_at=now()
  FROM picked WHERE n.id=picked.id
  RETURNING n.*
 )
 SELECT coalesce(jsonb_agg(jsonb_build_object(
  'id',c.id,'user_id',c.user_id,'kind',c.kind,'attempts',c.attempts,
  'reference',p.reference,'item_code',p.item_code,
  'amount_ngn',coalesce(p.amount_ngn,s.amount_ngn),
  'months',coalesce(p.duration_months,s.months),
  'tier',coalesce(s.tier,p.item_code))), '[]'::jsonb)
 INTO v_result
 FROM claimed c
 LEFT JOIN public.blink_paystack_payments p ON p.id=c.payment_id
 LEFT JOIN private.blink_paystack_renewals s ON s.id=c.subscription_id;
 RETURN v_result;
END
$claim_payment$;
REVOKE ALL ON FUNCTION public.claim_blink_paystack_payment_notices(integer)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.claim_blink_paystack_payment_notices(integer)
 TO service_role;

CREATE OR REPLACE FUNCTION public.complete_blink_paystack_payment_notice(
 p_id uuid,p_status text,p_error text DEFAULT null
) RETURNS boolean LANGUAGE plpgsql SECURITY INVOKER SET search_path=''
AS $complete_payment$
BEGIN
 IF p_status NOT IN('sent','pending','failed') THEN
   RAISE EXCEPTION 'INVALID_NOTICE_STATUS'; END IF;
 UPDATE private.blink_paystack_notification_outbox
 SET status=p_status,updated_at=now(),
  sent_at=CASE WHEN p_status='sent' THEN now() ELSE sent_at END,
  last_error=left(p_error,120),
  available_at=CASE WHEN p_status='pending'
    THEN now()+make_interval(mins=>greatest(10,attempts*15))
    ELSE available_at END
 WHERE id=p_id AND status='processing' AND attempts<=3;
 RETURN FOUND;
END
$complete_payment$;
REVOKE ALL ON FUNCTION public.complete_blink_paystack_payment_notice(uuid,text,text)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.complete_blink_paystack_payment_notice(uuid,text,text)
 TO service_role;
