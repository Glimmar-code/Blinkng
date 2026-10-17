-- BLINK Market seller trust and fair-listing controls (staging only).
-- destructive-change-reviewed
-- rollback-plan: disable new seller RPC, remove insertion and Feed routing triggers, restore previous Market RLS policies after exporting affected data; never delete existing orders.
-- Depends on 20261007005000_market_professional_hardening.sql.
-- Do not apply to production before the prerequisite migration and staging validation.
-- Rollback: revoke the new registration RPC, remove the spam trigger/function,
-- and restore the previous market_items policies. Existing seller records are retained.
BEGIN;

-- Separate free seller enrollment from discontinued seller-activation checkout.
-- Eligibility is always derived from the server-owned verification record.
CREATE OR REPLACE FUNCTION public.register_verified_market_seller(
  p_store_name text,
  p_phone text,
  p_whatsapp text,
  p_state text,
  p_city text,
  p_university text
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $market_register$
DECLARE
  v_user uuid := auth.uid();
  v_profile public.profiles%ROWTYPE;
BEGIN
  IF v_user IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  IF length(trim(coalesce(p_store_name,''))) NOT BETWEEN 2 AND 80
     OR length(trim(coalesce(p_city,''))) NOT BETWEEN 2 AND 120
     OR length(trim(coalesce(p_state,''))) NOT BETWEEN 2 AND 100
     OR length(trim(coalesce(p_university,''))) NOT BETWEEN 3 AND 140
     OR length(regexp_replace(coalesce(p_phone,''),'[^0-9]','','g')) NOT BETWEEN 10 AND 15
     OR length(regexp_replace(coalesce(p_whatsapp,''),'[^0-9]','','g')) NOT BETWEEN 10 AND 15
  THEN RAISE EXCEPTION 'INVALID_SELLER_DETAILS'; END IF;

  SELECT * INTO v_profile FROM public.profiles WHERE id = v_user FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'PROFILE_NOT_FOUND'; END IF;
  IF v_profile.seller_status IN ('suspended','rejected') THEN
    RAISE EXCEPTION 'SELLER_REQUIRES_REVIEW';
  END IF;
  IF NOT (
    coalesce(v_profile.is_verified,false)
    AND upper(coalesce(v_profile.verification_badge,'NONE')) IN ('BLUE','GOLD')
    AND (
      v_profile.verification_expires_at > now()
      OR (upper(coalesce(v_profile.verification_badge,'NONE')) = 'GOLD'
          AND v_profile.verification_expires_at IS NULL)
    )
  ) THEN RAISE EXCEPTION 'VERIFICATION_REQUIRED_OR_EXPIRED'; END IF;

  UPDATE public.profiles
  SET is_seller_active = true,
      seller_status = 'active',
      seller_store_name = trim(p_store_name),
      seller_activated_at = coalesce(seller_activated_at,now()),
      university = trim(p_university)
  WHERE id = v_user;
  RETURN true;
END;
$market_register$;
REVOKE ALL ON FUNCTION public.register_verified_market_seller(text,text,text,text,text,text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_verified_market_seller(text,text,text,text,text,text) TO authenticated;

-- Never allow self-service activation via the retired paid method.
DO $legacy_seller_activation$
BEGIN
  IF to_regprocedure('public.activate_marketplace_profile(text)') IS NOT NULL THEN
    REVOKE ALL ON FUNCTION public.activate_marketplace_profile(text) FROM PUBLIC, anon, authenticated;
  END IF;
END;
$legacy_seller_activation$;
REVOKE ALL ON FUNCTION public.create_market_seller_activation_order(text) FROM PUBLIC, anon, authenticated;
-- Reject even service-role attempts to start new paid enrollments through legacy
-- Edge Functions, while retaining previously fulfilled payment records.
CREATE OR REPLACE FUNCTION public.create_market_seller_activation_order(p_store_name text)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, pg_temp
AS $retired_seller$
BEGIN
  RAISE EXCEPTION 'SELLER_ACTIVATION_PAYMENT_RETIRED';
END;
$retired_seller$;
REVOKE ALL ON FUNCTION public.create_market_seller_activation_order(text) FROM PUBLIC, anon, authenticated, service_role;

-- Existing listings stay owned by sellers after expiry, but expired/blocked sellers
-- cannot publish or appear in public recommendations. Renewal restores eligibility.
DROP POLICY IF EXISTS market_items_insert_own ON public.market_items;
CREATE POLICY market_items_insert_own ON public.market_items
FOR INSERT TO authenticated
WITH CHECK (
  seller_id = (select auth.uid())
  AND EXISTS (
    SELECT 1 FROM public.profiles p
    WHERE p.id = (select auth.uid())
      AND coalesce(p.is_seller_active,false)
      AND p.seller_status = 'active'
      AND coalesce(p.is_verified,false)
      AND upper(coalesce(p.verification_badge,'NONE')) IN ('BLUE','GOLD')
      AND (p.verification_expires_at > now()
           OR (upper(coalesce(p.verification_badge,'NONE'))='GOLD'
               AND p.verification_expires_at IS NULL))
  )
);

DROP POLICY IF EXISTS market_items_select_visible ON public.market_items;
CREATE POLICY market_items_select_visible ON public.market_items
FOR SELECT TO authenticated
USING (
  seller_id = (select auth.uid())
  OR (
    coalesce(is_sold,false) = false
    AND status = 'active'
    AND EXISTS (
      SELECT 1 FROM public.profiles p
      WHERE p.id = seller_id
        AND coalesce(p.is_seller_active,false)
        AND p.seller_status = 'active'
        AND coalesce(p.is_verified,false)
        AND upper(coalesce(p.verification_badge,'NONE')) IN ('BLUE','GOLD')
        AND (p.verification_expires_at > now()
             OR (upper(coalesce(p.verification_badge,'NONE'))='GOLD'
                 AND p.verification_expires_at IS NULL))
    )
  )
);

-- Guard INSERT even if a legacy permissive policy or altered client bypasses
-- the screen checks. Editing a listing cannot reset its publish timestamp.
CREATE OR REPLACE FUNCTION public.market_enforce_listing_limits()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $market_limits$
DECLARE
  v_profile public.profiles%ROWTYPE;
  v_normalized text := lower(regexp_replace(trim(coalesce(NEW.title,'')),'[[:space:]]+',' ','g'));
BEGIN
  IF NEW.seller_id IS DISTINCT FROM auth.uid() THEN
    RAISE EXCEPTION 'SELLER_OWNERSHIP_REQUIRED';
  END IF;
  -- Serializes concurrent publishes by the same seller so 3/day is enforced.
  PERFORM pg_advisory_xact_lock(hashtextextended(NEW.seller_id::text,42));
  SELECT * INTO v_profile FROM public.profiles WHERE id = NEW.seller_id;
  IF NOT FOUND
    OR NOT coalesce(v_profile.is_seller_active,false)
    OR v_profile.seller_status <> 'active'
    OR NOT coalesce(v_profile.is_verified,false)
    OR upper(coalesce(v_profile.verification_badge,'NONE')) NOT IN ('BLUE','GOLD')
    OR NOT (
      v_profile.verification_expires_at > now()
      OR (upper(coalesce(v_profile.verification_badge,'NONE')) = 'GOLD'
          AND v_profile.verification_expires_at IS NULL)
    )
  THEN RAISE EXCEPTION 'SELLER_NOT_VERIFIED_OR_EXPIRED'; END IF;

  IF length(v_normalized) < 3 OR length(trim(coalesce(NEW.description,''))) < 10
     OR NEW.price IS NULL OR NEW.price <= 0
  THEN RAISE EXCEPTION 'INVALID_MARKET_LISTING'; END IF;

  IF (SELECT count(*) FROM public.market_items
      WHERE seller_id = NEW.seller_id
        AND created_at >= now() - interval '24 hours') >= 3
  THEN RAISE EXCEPTION 'DAILY_LISTING_LIMIT_REACHED'; END IF;

  IF EXISTS(
    SELECT 1 FROM public.market_items
    WHERE seller_id = NEW.seller_id
      AND lower(regexp_replace(trim(coalesce(title,'')),'[[:space:]]+',' ','g')) = v_normalized
      AND created_at >= now() - interval '30 days'
      AND status <> 'removed'
  ) THEN RAISE EXCEPTION 'DUPLICATE_MARKET_LISTING'; END IF;

  NEW.seller_is_verified := true; -- never trust the client-supplied badge bit
  NEW.university := v_profile.university; -- registration is campus-authoritative
  RETURN NEW;
END;
$market_limits$;
DROP TRIGGER IF EXISTS market_enforce_listing_limits_trigger ON public.market_items;
CREATE TRIGGER market_enforce_listing_limits_trigger
BEFORE INSERT ON public.market_items
FOR EACH ROW EXECUTE FUNCTION public.market_enforce_listing_limits();

-- Stop owner edits from resetting freshness, boosting metrics or changing seller.
-- Privileged, server-authorized RPCs may still update legitimate counters.
CREATE OR REPLACE FUNCTION public.market_guard_listing_update()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $market_update$
DECLARE
  v_title text;
BEGIN
  IF current_user IN ('authenticated','anon') THEN
    IF NEW.seller_id IS DISTINCT FROM OLD.seller_id THEN
      RAISE EXCEPTION 'CANNOT_TRANSFER_MARKET_LISTING';
    END IF;
    NEW.created_at := OLD.created_at;
    NEW.views_count := OLD.views_count;
    NEW.saves_count := OLD.saves_count;
    NEW.seller_rating := OLD.seller_rating;
    NEW.seller_review_count := OLD.seller_review_count;
    NEW.seller_is_verified := OLD.seller_is_verified;
    NEW.is_featured := OLD.is_featured;

    IF NEW.status = 'active' AND OLD.status <> 'active' AND NOT EXISTS (
      SELECT 1 FROM public.profiles p
      WHERE p.id = NEW.seller_id AND p.is_seller_active
        AND p.seller_status = 'active' AND p.is_verified
        AND upper(coalesce(p.verification_badge,'NONE')) IN ('BLUE','GOLD')
        AND (
          p.verification_expires_at > now()
          OR (upper(coalesce(p.verification_badge,'NONE')) = 'GOLD'
              AND p.verification_expires_at IS NULL)
        )
    ) THEN RAISE EXCEPTION 'VERIFICATION_REQUIRED_TO_REACTIVATE'; END IF;

    IF NEW.title IS DISTINCT FROM OLD.title THEN
      v_title := lower(regexp_replace(trim(coalesce(NEW.title,'')),'[[:space:]]+',' ','g'));
      IF EXISTS (
        SELECT 1 FROM public.market_items i
        WHERE i.seller_id = NEW.seller_id AND i.id <> OLD.id
          AND i.created_at >= now() - interval '30 days'
          AND i.status <> 'removed'
          AND lower(regexp_replace(trim(coalesce(i.title,'')),'[[:space:]]+',' ','g')) = v_title
      ) THEN RAISE EXCEPTION 'DUPLICATE_MARKET_LISTING'; END IF;
    END IF;
  END IF;
  RETURN NEW;
END;
$market_update$;
DROP TRIGGER IF EXISTS market_guard_listing_update_trigger ON public.market_items;
CREATE TRIGGER market_guard_listing_update_trigger
BEFORE UPDATE ON public.market_items
FOR EACH ROW EXECUTE FUNCTION public.market_guard_listing_update();

-- Viewer-first public sort: no seller should purchase a credibility ranking.
-- Existing saved/review/order/market reports RLS remains unchanged.
CREATE INDEX IF NOT EXISTS market_items_seller_created_rate_idx
  ON public.market_items(seller_id,created_at DESC);

-- Do not accept new purchase requests for listings whose seller has lost
-- verification. Existing orders and refund/dispute records are preserved.
CREATE OR REPLACE FUNCTION public.market_order_requires_active_seller()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $market_order$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.market_items i
    JOIN public.profiles p ON p.id = i.seller_id
    WHERE i.id = NEW.item_id
      AND i.status = 'active'
      AND not coalesce(i.is_sold,false)
      AND p.is_seller_active
      AND p.seller_status = 'active'
      AND p.is_verified
      AND upper(coalesce(p.verification_badge,'NONE')) IN ('BLUE','GOLD')
      AND (
        p.verification_expires_at > now()
        OR (upper(coalesce(p.verification_badge,'NONE')) = 'GOLD'
            AND p.verification_expires_at IS NULL)
      )
  ) THEN RAISE EXCEPTION 'SELLER_VERIFICATION_EXPIRED'; END IF;
  RETURN NEW;
END;
$market_order$;
DROP TRIGGER IF EXISTS market_order_requires_active_seller_trigger ON public.marketplace_orders;
CREATE TRIGGER market_order_requires_active_seller_trigger
BEFORE INSERT ON public.marketplace_orders
FOR EACH ROW EXECUTE FUNCTION public.market_order_requires_active_seller();

-- Ordinary feed content cannot be an unmistakable product listing.
-- Sponsored placement is handled separately from feed_posts, with a visible ad label.
CREATE OR REPLACE FUNCTION public.route_product_ads_to_market()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $market_feed$
BEGIN
  IF coalesce(NEW.text,'') ~* '(^|[^[:alpha:]])(for sale|selling this|available for purchase|dm to buy|buy now|order now|price is)([^[:alpha:]]|$)'
     AND coalesce(NEW.text,'') ~* '(₦|NGN[[:space:]]*)[[:space:]]*[0-9]'
  THEN
    RAISE EXCEPTION 'PRODUCT_ADS_MARKET_ONLY'
      USING HINT = 'Publish the listing through BLINK Market or request a review.';
  END IF;
  RETURN NEW;
END;
$market_feed$;
DROP TRIGGER IF EXISTS feed_route_product_ads_trigger ON public.feed_posts;
CREATE TRIGGER feed_route_product_ads_trigger
BEFORE INSERT OR UPDATE OF text ON public.feed_posts
FOR EACH ROW EXECUTE FUNCTION public.route_product_ads_to_market();

-- Sponsored inventory must be filtered by the same eligibility rules; do not
-- represent estimated views as guaranteed paid impressions.
COMMIT;
