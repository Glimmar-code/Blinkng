-- BLINK Market: professional seller identity, reputation, and lifecycle notifications.
-- Rollback: disable scheduled market maintenance, revoke public RPCs, and archive
-- new tables for recovery. Preserve seller listings, existing orders and reports.
BEGIN;

CREATE TABLE IF NOT EXISTS public.market_seller_profiles (
    user_id uuid PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    seller_kind text NOT NULL DEFAULT 'individual'
        CHECK (seller_kind IN ('individual','business')),
    headline text NOT NULL DEFAULT '',
    description text NOT NULL DEFAULT '',
    product_categories text[] NOT NULL DEFAULT '{}',
    offers_delivery boolean NOT NULL DEFAULT false,
    offers_campus_pickup boolean NOT NULL DEFAULT true,
    logo_url text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.market_seller_profiles ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.market_seller_profiles FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.market_seller_profiles TO authenticated;
DROP POLICY IF EXISTS market_seller_profile_owner_read ON public.market_seller_profiles;
CREATE POLICY market_seller_profile_owner_read ON public.market_seller_profiles
  FOR SELECT TO authenticated USING (user_id=(select auth.uid()));

CREATE SCHEMA IF NOT EXISTS private;
CREATE TABLE IF NOT EXISTS private.market_seller_approvals (
    user_id uuid PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    decision text NOT NULL DEFAULT 'pending' CHECK (decision IN ('pending','approved','rejected','revoked')),
    reason text NOT NULL DEFAULT '',
    reviewed_by uuid REFERENCES public.profiles(id),
    reviewed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
REVOKE ALL ON private.market_seller_approvals FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.complete_market_seller_profile(
  p_kind text, p_description text, p_categories text[],
  p_delivery boolean, p_pickup boolean, p_logo_url text DEFAULT NULL
) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $seller_complete$
DECLARE v_user uuid := auth.uid();
BEGIN
  IF v_user IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  IF NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=v_user
      AND is_seller_active=true AND seller_status='active')
  THEN RAISE EXCEPTION 'SELLER_REGISTRATION_REQUIRED'; END IF;
  IF p_kind NOT IN ('individual','business')
     OR length(trim(coalesce(p_description,''))) NOT BETWEEN 20 AND 1000
     OR coalesce(array_length(p_categories,1),0) NOT BETWEEN 1 AND 6
     OR (NOT coalesce(p_delivery,false) AND NOT coalesce(p_pickup,false))
     OR (p_logo_url IS NOT NULL AND
         (length(p_logo_url)>500 OR p_logo_url !~ '^https://'))
  THEN RAISE EXCEPTION 'INVALID_SELLER_PROFILE'; END IF;
  INSERT INTO public.market_seller_profiles
    (user_id,seller_kind,description,product_categories,offers_delivery,offers_campus_pickup,logo_url)
  VALUES (v_user,p_kind,trim(p_description),p_categories,p_delivery,p_pickup,p_logo_url)
  ON CONFLICT (user_id) DO UPDATE SET seller_kind=excluded.seller_kind,
    description=excluded.description,product_categories=excluded.product_categories,
    offers_delivery=excluded.offers_delivery,offers_campus_pickup=excluded.offers_campus_pickup,
    logo_url=excluded.logo_url,updated_at=now();
  INSERT INTO private.market_seller_approvals(user_id) VALUES(v_user)
    ON CONFLICT(user_id) DO NOTHING;
  RETURN true;
END;
$seller_complete$;
REVOKE ALL ON FUNCTION public.complete_market_seller_profile(text,text,text[],boolean,boolean,text)
  FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.complete_market_seller_profile(text,text,text[],boolean,boolean,text)
  TO authenticated;

-- A blue tick is NOT a seller safety guarantee. The separate merchant badge is
-- awarded only after explicit marketplace moderation approval.
CREATE OR REPLACE FUNCTION public.get_market_public_seller_card(p_user uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,private,pg_temp AS $seller_card$
DECLARE v_profile public.profiles%ROWTYPE;
DECLARE v_review_avg numeric; v_review_count integer; v_completed integer;
DECLARE v_report_actions integer; v_score integer; v_approved boolean;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  SELECT * INTO v_profile FROM public.profiles WHERE id=p_user;
  IF NOT FOUND THEN RETURN jsonb_build_object('found',false); END IF;
  SELECT coalesce(avg(rating),0),count(*)::integer INTO v_review_avg,v_review_count
    FROM public.marketplace_reviews WHERE reviewee_id=p_user;
  SELECT count(*)::integer INTO v_completed FROM public.marketplace_orders
    WHERE seller_id=p_user AND status IN ('completed','delivered');
  SELECT count(*)::integer INTO v_report_actions FROM public.marketplace_reports r
    JOIN public.market_items i ON i.id=r.item_id
    WHERE i.seller_id=p_user AND r.status IN ('actioned','upheld');
  v_score:=greatest(0,least(100,round(
    70 * (coalesce(v_review_avg,0)*v_review_count + 3.5*5)/(5*(v_review_count+5))
    + 20 * (v_completed::numeric/(v_completed+5))
    + 10 - least(30,v_report_actions*10)
  )::integer));
  SELECT (decision='approved') INTO v_approved
    FROM private.market_seller_approvals WHERE user_id=p_user;
  RETURN jsonb_build_object(
    'found',true,'seller_name',v_profile.seller_store_name,
    'university',v_profile.university,
    'verified_seller',coalesce(v_approved,false) AND v_profile.is_seller_active
      AND v_profile.seller_status='active'
      AND v_profile.is_verified
      AND upper(coalesce(v_profile.verification_badge,'NONE')) IN ('BLUE','GOLD')
      AND (v_profile.verification_expires_at>now()
           OR (upper(coalesce(v_profile.verification_badge,'NONE'))='GOLD'
               AND v_profile.verification_expires_at IS NULL)),
    'rating',round(v_review_avg,1),'review_count',v_review_count,
    'completed_orders',v_completed,'reliability_score',v_score
  );
END;
$seller_card$;
REVOKE ALL ON FUNCTION public.get_market_public_seller_card(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.get_market_public_seller_card(uuid) TO authenticated;

-- Owner-only dashboard. No buyer contact information or review narratives.
CREATE OR REPLACE FUNCTION public.get_my_market_dashboard()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $market_dashboard$
DECLARE v_uid uuid:=auth.uid();
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  RETURN jsonb_build_object(
    'products',(SELECT count(*) FROM public.market_items WHERE seller_id=v_uid AND status='active'),
    'sold',(SELECT count(*) FROM public.market_items WHERE seller_id=v_uid AND status='sold'),
    'views',(SELECT coalesce(sum(views_count),0) FROM public.market_items WHERE seller_id=v_uid),
    'saves',(SELECT coalesce(sum(saves_count),0) FROM public.market_items WHERE seller_id=v_uid),
    'inquiries',(SELECT count(*) FROM public.marketplace_orders WHERE seller_id=v_uid),
    'completed_orders',(SELECT count(*) FROM public.marketplace_orders WHERE seller_id=v_uid AND status IN ('completed','delivered')),
    'reviews',(SELECT count(*) FROM public.marketplace_reviews WHERE reviewee_id=v_uid),
    'boosts',(SELECT count(*) FROM public.blink_boost_campaigns_v2 WHERE user_id=v_uid AND target_type='LISTING'),
    'seller_card',public.get_market_public_seller_card(v_uid)
  );
END;
$market_dashboard$;
REVOKE ALL ON FUNCTION public.get_my_market_dashboard() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.get_my_market_dashboard() TO authenticated;

-- Listing freshness means availability confirmation, NOT manipulating created_at.
ALTER TABLE public.market_items ADD COLUMN IF NOT EXISTS availability_confirmed_at timestamptz;
UPDATE public.market_items SET availability_confirmed_at=coalesce(availability_confirmed_at,created_at,now())
 WHERE availability_confirmed_at IS NULL;
ALTER TABLE public.market_items ALTER COLUMN availability_confirmed_at SET DEFAULT now();
CREATE INDEX IF NOT EXISTS market_listing_expiry_idx
 ON public.market_items(status,availability_confirmed_at) WHERE status='active';

CREATE OR REPLACE FUNCTION public.confirm_market_listing_availability(p_item uuid)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $confirm_market$
DECLARE v_uid uuid:=auth.uid();
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  UPDATE public.market_items SET availability_confirmed_at=now(),
    status=CASE WHEN status='paused' THEN 'active' ELSE status END,
    updated_at=now()
  WHERE id=p_item AND seller_id=v_uid AND status IN ('active','paused')
    AND EXISTS(SELECT 1 FROM public.profiles p WHERE p.id=v_uid AND p.is_seller_active
      AND p.seller_status='active' AND p.is_verified
      AND upper(p.verification_badge) IN ('BLUE','GOLD')
      AND (p.verification_expires_at>now()
           OR (upper(p.verification_badge)='GOLD' AND p.verification_expires_at IS NULL)));
  RETURN FOUND;
END;
$confirm_market$;
REVOKE ALL ON FUNCTION public.confirm_market_listing_availability(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.confirm_market_listing_availability(uuid) TO authenticated;

CREATE TABLE IF NOT EXISTS private.market_notification_log(
  user_id uuid NOT NULL,kind text NOT NULL,marker text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,kind,marker)
);
REVOKE ALL ON private.market_notification_log FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION private.market_daily_maintenance()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,private,pg_temp AS $market_maint$
DECLARE v_num integer:=0; v_paused integer:=0;
BEGIN
  -- Transactionally deduplicate the day-7, day-3, and expired notifications.
  WITH events AS (
    SELECT p.id AS user_id,
      CASE WHEN p.verification_expires_at<=now() THEN 'expired'
           WHEN p.verification_expires_at<=now()+interval '3 days' THEN '3d'
           WHEN p.verification_expires_at<=now()+interval '7 days' THEN '7d'
           ELSE NULL END kind,
      p.verification_expires_at::date::text AS marker
    FROM public.profiles p WHERE p.is_seller_active
      AND p.seller_status='active' AND p.verification_expires_at IS NOT NULL
      AND p.verification_expires_at<=now()+interval '7 days'
  ), inserted AS (
    INSERT INTO private.market_notification_log(user_id,kind,marker)
    SELECT user_id,kind,marker FROM events WHERE kind IS NOT NULL
    ON CONFLICT DO NOTHING RETURNING user_id,kind
  )
  INSERT INTO public.activities(recipient_id,actor_id,activity_type,entity_type,message,is_read)
  SELECT user_id,NULL,'MARKET_VERIFICATION','market',
    CASE kind WHEN '7d' THEN 'Your seller verification expires within 7 days. Renew to keep your listings visible.'
    WHEN '3d' THEN 'Your seller verification expires within 3 days. Renew to keep selling.'
    ELSE 'Your seller verification expired. New listings and recommendations are paused until renewal.' END,
    false
  FROM inserted;
  GET DIAGNOSTICS v_num=ROW_COUNT;

  UPDATE public.market_items SET status='paused',updated_at=now()
  WHERE status='active' AND coalesce(availability_confirmed_at,created_at,now())
      < now()-interval '30 days';
  GET DIAGNOSTICS v_paused=ROW_COUNT;
  RETURN jsonb_build_object('notified',v_num,'paused_stale_listings',v_paused);
END;
$market_maint$;
REVOKE ALL ON FUNCTION private.market_daily_maintenance() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION private.market_daily_maintenance() TO service_role;

-- Use an existing no-fee pg_cron installation, but never automatically install
-- paid extensions or create a Supabase hosted preview branch.
DO $schedule_market$
BEGIN
  IF to_regprocedure('cron.schedule(text,text,text)') IS NOT NULL THEN
    PERFORM cron.schedule('blink-market-lifecycle-daily','30 4 * * *',
      'select private.market_daily_maintenance()');
  END IF;
EXCEPTION WHEN insufficient_privilege OR undefined_function THEN
  RAISE NOTICE 'Market maintenance needs a privileged scheduler setup';
END;
$schedule_market$;
COMMIT;
