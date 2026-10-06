-- BLINK Market professional hardening.
-- Additive migration: strengthens seller trust, listing media, marketplace safety and lifecycle.
BEGIN;

ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS is_seller_active boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS seller_store_name text NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS seller_status text NOT NULL DEFAULT 'inactive',
  ADD COLUMN IF NOT EXISTS seller_activated_at timestamptz,
  ADD COLUMN IF NOT EXISTS seller_payment_reference text;

DO $$
BEGIN
  ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_seller_status_check
    CHECK (seller_status IN ('inactive','pending','active','suspended','rejected'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

UPDATE public.profiles
SET seller_status = 'active',
    seller_activated_at = COALESCE(seller_activated_at, now())
WHERE COALESCE(is_seller_active,false) = true
  AND seller_status = 'inactive';

ALTER TABLE public.market_items
  ADD COLUMN IF NOT EXISTS quantity integer NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS currency text NOT NULL DEFAULT 'NGN',
  ADD COLUMN IF NOT EXISTS status text NOT NULL DEFAULT 'active',
  ADD COLUMN IF NOT EXISTS negotiable boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS delivery_method text NOT NULL DEFAULT 'meetup',
  ADD COLUMN IF NOT EXISTS pickup_location text NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS views_count bigint NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS saves_count bigint NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

DO $$
BEGIN
  ALTER TABLE public.market_items
    ADD CONSTRAINT market_items_quantity_check CHECK (quantity BETWEEN 0 AND 9999);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
  ALTER TABLE public.market_items
    ADD CONSTRAINT market_items_currency_check CHECK (currency = 'NGN');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
  ALTER TABLE public.market_items
    ADD CONSTRAINT market_items_status_check CHECK (status IN ('active','paused','sold','removed','suspended'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS market_items_status_created_idx
  ON public.market_items(status, created_at DESC);
CREATE INDEX IF NOT EXISTS market_items_category_created_idx
  ON public.market_items(category, created_at DESC);
CREATE INDEX IF NOT EXISTS market_items_university_created_idx
  ON public.market_items(university, created_at DESC);
CREATE INDEX IF NOT EXISTS market_items_price_idx
  ON public.market_items(price);

-- Dedicated public bucket for listing photos. Writes stay owner-scoped.
INSERT INTO storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
VALUES(
  'market-media',
  'market-media',
  true,
  10485760,
  ARRAY['image/jpeg','image/png','image/webp','image/heic','image/heif']
)
ON CONFLICT (id) DO UPDATE
SET public = true,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS market_media_insert_own ON storage.objects;
DROP POLICY IF EXISTS market_media_update_own ON storage.objects;
DROP POLICY IF EXISTS market_media_delete_own ON storage.objects;
CREATE POLICY market_media_insert_own ON storage.objects
FOR INSERT TO authenticated
WITH CHECK (
  bucket_id='market-media'
  AND (storage.foldername(name))[1]='users'
  AND (storage.foldername(name))[2]=(select auth.uid())::text
);
CREATE POLICY market_media_update_own ON storage.objects
FOR UPDATE TO authenticated
USING (
  bucket_id='market-media'
  AND (storage.foldername(name))[1]='users'
  AND (storage.foldername(name))[2]=(select auth.uid())::text
)
WITH CHECK (
  bucket_id='market-media'
  AND (storage.foldername(name))[1]='users'
  AND (storage.foldername(name))[2]=(select auth.uid())::text
);
CREATE POLICY market_media_delete_own ON storage.objects
FOR DELETE TO authenticated
USING (
  bucket_id='market-media'
  AND (storage.foldername(name))[1]='users'
  AND (storage.foldername(name))[2]=(select auth.uid())::text
);

-- Only activated sellers can create new public listings.
DROP POLICY IF EXISTS market_items_insert_own ON public.market_items;
CREATE POLICY market_items_insert_own ON public.market_items
FOR INSERT TO authenticated
WITH CHECK (
  seller_id=(select auth.uid())
  AND EXISTS(
    SELECT 1
    FROM public.profiles p
    WHERE p.id=(select auth.uid())
      AND COALESCE(p.is_seller_active,false)=true
      AND COALESCE(p.seller_status,'inactive')='active'
  )
);

-- Existing owner update/delete policy remains ownership based so a seller can
-- still remove or pause their content after suspension.
DROP POLICY IF EXISTS market_items_update_own ON public.market_items;
CREATE POLICY market_items_update_own ON public.market_items
FOR UPDATE TO authenticated
USING (seller_id=(select auth.uid()))
WITH CHECK (seller_id=(select auth.uid()));

DROP POLICY IF EXISTS market_items_delete_own ON public.market_items;
CREATE POLICY market_items_delete_own ON public.market_items
FOR DELETE TO authenticated
USING (seller_id=(select auth.uid()));

CREATE TABLE IF NOT EXISTS public.marketplace_views (
  user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE DEFAULT auth.uid(),
  item_id uuid NOT NULL REFERENCES public.market_items(id) ON DELETE CASCADE,
  viewed_on date NOT NULL DEFAULT CURRENT_DATE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,item_id,viewed_on)
);
ALTER TABLE public.marketplace_views ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.marketplace_views FROM anon;
GRANT SELECT,INSERT ON public.marketplace_views TO authenticated;
DROP POLICY IF EXISTS marketplace_views_select_own ON public.marketplace_views;
DROP POLICY IF EXISTS marketplace_views_insert_own ON public.marketplace_views;
CREATE POLICY marketplace_views_select_own ON public.marketplace_views
FOR SELECT TO authenticated USING (user_id=(select auth.uid()));
CREATE POLICY marketplace_views_insert_own ON public.marketplace_views
FOR INSERT TO authenticated WITH CHECK (user_id=(select auth.uid()));

CREATE TABLE IF NOT EXISTS public.marketplace_reports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  reporter_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE DEFAULT auth.uid(),
  item_id uuid NOT NULL REFERENCES public.market_items(id) ON DELETE CASCADE,
  reason text NOT NULL,
  details text,
  status text NOT NULL DEFAULT 'pending',
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS marketplace_reports_pending_idx
  ON public.marketplace_reports(status,created_at DESC);
ALTER TABLE public.marketplace_reports ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.marketplace_reports FROM anon;
GRANT SELECT,INSERT ON public.marketplace_reports TO authenticated;
DROP POLICY IF EXISTS marketplace_reports_select_own ON public.marketplace_reports;
DROP POLICY IF EXISTS marketplace_reports_insert_own ON public.marketplace_reports;
CREATE POLICY marketplace_reports_select_own ON public.marketplace_reports
FOR SELECT TO authenticated USING (reporter_id=(select auth.uid()));
CREATE POLICY marketplace_reports_insert_own ON public.marketplace_reports
FOR INSERT TO authenticated WITH CHECK (reporter_id=(select auth.uid()));

-- Keep saves_count authoritative even when wishlist rows are modified elsewhere.
CREATE OR REPLACE FUNCTION public.recalc_marketplace_saves()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE v_item uuid := COALESCE(NEW.item_id,OLD.item_id);
BEGIN
  UPDATE public.market_items
  SET saves_count=(SELECT count(*) FROM public.marketplace_wishlist w WHERE w.item_id=v_item),
      updated_at=now()
  WHERE id=v_item;
  RETURN COALESCE(NEW,OLD);
END;
$$;
DROP TRIGGER IF EXISTS trg_recalc_marketplace_saves ON public.marketplace_wishlist;
CREATE TRIGGER trg_recalc_marketplace_saves
AFTER INSERT OR DELETE ON public.marketplace_wishlist
FOR EACH ROW EXECUTE FUNCTION public.recalc_marketplace_saves();

CREATE OR REPLACE FUNCTION public.toggle_marketplace_wishlist(p_item_id uuid)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE v_uid uuid := auth.uid();
DECLARE v_saved boolean;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  IF NOT EXISTS(
    SELECT 1 FROM public.market_items
    WHERE id=p_item_id AND COALESCE(status,'active') NOT IN ('removed','suspended')
  ) THEN
    RAISE EXCEPTION 'ITEM_NOT_FOUND';
  END IF;

  DELETE FROM public.marketplace_wishlist
  WHERE user_id=v_uid AND item_id=p_item_id;

  IF FOUND THEN
    v_saved:=false;
  ELSE
    INSERT INTO public.marketplace_wishlist(user_id,item_id)
    VALUES(v_uid,p_item_id)
    ON CONFLICT DO NOTHING;
    v_saved:=true;
  END IF;

  RETURN v_saved;
END;
$$;
REVOKE ALL ON FUNCTION public.toggle_marketplace_wishlist(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.toggle_marketplace_wishlist(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.record_marketplace_view(p_item_id uuid)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE v_uid uuid := auth.uid();
DECLARE inserted_count integer := 0;
DECLARE total_views bigint := 0;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;

  INSERT INTO public.marketplace_views(user_id,item_id)
  SELECT v_uid,p_item_id
  WHERE EXISTS(
    SELECT 1 FROM public.market_items
    WHERE id=p_item_id AND COALESCE(status,'active') NOT IN ('removed','suspended')
  )
  ON CONFLICT DO NOTHING;

  GET DIAGNOSTICS inserted_count = ROW_COUNT;
  IF inserted_count > 0 THEN
    UPDATE public.market_items
    SET views_count=views_count+1,updated_at=now()
    WHERE id=p_item_id
    RETURNING views_count INTO total_views;
  ELSE
    SELECT views_count INTO total_views FROM public.market_items WHERE id=p_item_id;
  END IF;

  RETURN COALESCE(total_views,0);
END;
$$;
REVOKE ALL ON FUNCTION public.record_marketplace_view(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.record_marketplace_view(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.report_marketplace_item(
  p_item_id uuid,
  p_reason text,
  p_details text DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE v_uid uuid := auth.uid();
DECLARE v_id uuid;
DECLARE clean_reason text := left(trim(coalesce(p_reason,'')),80);
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  IF length(clean_reason)<3 THEN RAISE EXCEPTION 'REASON_REQUIRED'; END IF;
  IF NOT EXISTS(SELECT 1 FROM public.market_items WHERE id=p_item_id) THEN
    RAISE EXCEPTION 'ITEM_NOT_FOUND';
  END IF;
  IF (
    SELECT count(*) FROM public.marketplace_reports
    WHERE reporter_id=v_uid AND created_at>now()-interval '1 hour'
  ) >= 10 THEN
    RAISE EXCEPTION 'REPORT_RATE_LIMITED';
  END IF;

  INSERT INTO public.marketplace_reports(reporter_id,item_id,reason,details)
  VALUES(v_uid,p_item_id,clean_reason,left(trim(coalesce(p_details,'')),1000))
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;
REVOKE ALL ON FUNCTION public.report_marketplace_item(uuid,text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.report_marketplace_item(uuid,text,text) TO authenticated;

CREATE OR REPLACE FUNCTION public.update_marketplace_listing_status(
  p_item_id uuid,
  p_status text
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE v_uid uuid := auth.uid();
DECLARE v_next text := lower(trim(coalesce(p_status,'')));
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  IF v_next NOT IN ('active','paused','sold') THEN RAISE EXCEPTION 'INVALID_STATUS'; END IF;

  IF v_next='active' AND NOT EXISTS(
    SELECT 1 FROM public.profiles p
    WHERE p.id=v_uid
      AND COALESCE(p.is_seller_active,false)=true
      AND COALESCE(p.seller_status,'inactive')='active'
  ) THEN
    RAISE EXCEPTION 'SELLER_NOT_ACTIVE';
  END IF;

  UPDATE public.market_items
  SET status=v_next,
      is_sold=(v_next='sold'),
      updated_at=now()
  WHERE id=p_item_id AND seller_id=v_uid;

  IF NOT FOUND THEN RAISE EXCEPTION 'ITEM_NOT_FOUND_OR_NOT_OWNER'; END IF;
  RETURN true;
END;
$$;
REVOKE ALL ON FUNCTION public.update_marketplace_listing_status(uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.update_marketplace_listing_status(uuid,text) TO authenticated;

CREATE OR REPLACE FUNCTION public.delete_marketplace_listing(p_item_id uuid)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE v_uid uuid := auth.uid();
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;

  UPDATE public.market_items
  SET status='removed',is_sold=true,updated_at=now()
  WHERE id=p_item_id AND seller_id=v_uid;

  IF NOT FOUND THEN RAISE EXCEPTION 'ITEM_NOT_FOUND_OR_NOT_OWNER'; END IF;
  RETURN true;
END;
$$;
REVOKE ALL ON FUNCTION public.delete_marketplace_listing(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.delete_marketplace_listing(uuid) TO authenticated;

-- Seller activation is a server-priced Paystack product.
CREATE TABLE IF NOT EXISTS public.market_seller_activation_orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE DEFAULT auth.uid(),
  store_name text NOT NULL,
  amount_ngn integer NOT NULL DEFAULT 5000 CHECK (amount_ngn=5000),
  currency text NOT NULL DEFAULT 'NGN' CHECK (currency='NGN'),
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','failed','fulfilled','cancelled')),
  provider_reference text UNIQUE,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  fulfilled_at timestamptz
);
CREATE INDEX IF NOT EXISTS market_seller_activation_orders_user_created_idx
  ON public.market_seller_activation_orders(user_id,created_at DESC);
ALTER TABLE public.market_seller_activation_orders ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.market_seller_activation_orders FROM anon;
GRANT SELECT ON public.market_seller_activation_orders TO authenticated;
DROP POLICY IF EXISTS market_seller_activation_orders_select_own ON public.market_seller_activation_orders;
CREATE POLICY market_seller_activation_orders_select_own
ON public.market_seller_activation_orders
FOR SELECT TO authenticated
USING (user_id=(select auth.uid()));

CREATE OR REPLACE FUNCTION public.create_market_seller_activation_order(p_store_name text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE v_uid uuid := auth.uid();
DECLARE v_store text := left(trim(coalesce(p_store_name,'')),80);
DECLARE v_order public.market_seller_activation_orders%ROWTYPE;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  IF length(v_store)<2 THEN RAISE EXCEPTION 'STORE_NAME_REQUIRED'; END IF;
  IF EXISTS(
    SELECT 1 FROM public.profiles
    WHERE id=v_uid AND COALESCE(is_seller_active,false)=true
  ) THEN
    RAISE EXCEPTION 'SELLER_ALREADY_ACTIVE';
  END IF;

  SELECT * INTO v_order
  FROM public.market_seller_activation_orders
  WHERE user_id=v_uid
    AND store_name=v_store
    AND status='pending'
    AND provider_reference IS NULL
    AND created_at>now()-interval '30 minutes'
  ORDER BY created_at DESC
  LIMIT 1;

  IF v_order.id IS NULL THEN
    INSERT INTO public.market_seller_activation_orders(user_id,store_name)
    VALUES(v_uid,v_store)
    RETURNING * INTO v_order;
  END IF;

  UPDATE public.profiles
  SET seller_store_name=v_store,
      seller_status='pending'
  WHERE id=v_uid;

  RETURN jsonb_build_object(
    'order_id',v_order.id,
    'amount_ngn',v_order.amount_ngn,
    'currency',v_order.currency,
    'store_name',v_order.store_name
  );
END;
$$;
REVOKE ALL ON FUNCTION public.create_market_seller_activation_order(text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.create_market_seller_activation_order(text) TO authenticated;

CREATE OR REPLACE FUNCTION public.fulfill_market_seller_activation_order(
  p_order_id uuid,
  p_provider_reference text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE v_order public.market_seller_activation_orders%ROWTYPE;
BEGIN
  SELECT * INTO v_order
  FROM public.market_seller_activation_orders
  WHERE id=p_order_id
  FOR UPDATE;

  IF NOT FOUND THEN RAISE EXCEPTION 'SELLER_ACTIVATION_ORDER_NOT_FOUND'; END IF;
  IF v_order.status='fulfilled' THEN
    RETURN jsonb_build_object('success',true,'already_fulfilled',true,'user_id',v_order.user_id);
  END IF;
  IF nullif(trim(coalesce(p_provider_reference,'')),'') IS NULL THEN
    RAISE EXCEPTION 'PAYMENT_REFERENCE_REQUIRED';
  END IF;
  IF v_order.provider_reference IS DISTINCT FROM p_provider_reference THEN
    RAISE EXCEPTION 'PAYMENT_REFERENCE_MISMATCH';
  END IF;

  UPDATE public.market_seller_activation_orders
  SET status='fulfilled',fulfilled_at=now(),updated_at=now()
  WHERE id=v_order.id;

  UPDATE public.profiles
  SET is_seller_active=true,
      seller_store_name=v_order.store_name,
      seller_status='active',
      seller_activated_at=now(),
      seller_payment_reference=p_provider_reference
  WHERE id=v_order.user_id;

  RETURN jsonb_build_object(
    'success',true,
    'user_id',v_order.user_id,
    'store_name',v_order.store_name
  );
END;
$$;
REVOKE ALL ON FUNCTION public.fulfill_market_seller_activation_order(uuid,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.fulfill_market_seller_activation_order(uuid,text) TO service_role;

-- Market activity notifications.
CREATE OR REPLACE FUNCTION public.activity_from_marketplace_order()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE v_title text;
BEGIN
  SELECT title INTO v_title FROM public.market_items WHERE id=NEW.item_id;
  INSERT INTO public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message,is_read)
  VALUES(
    NEW.seller_id,
    NEW.buyer_id,
    'MARKET_ORDER',
    'market',
    NEW.item_id,
    'sent a purchase request for '||COALESCE(v_title,'your listing'),
    false
  );
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_activity_marketplace_order ON public.marketplace_orders;
CREATE TRIGGER trg_activity_marketplace_order
AFTER INSERT ON public.marketplace_orders
FOR EACH ROW EXECUTE FUNCTION public.activity_from_marketplace_order();

CREATE OR REPLACE FUNCTION public.activity_from_marketplace_order_status()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE v_recipient uuid;
DECLARE v_actor uuid;
DECLARE v_title text;
BEGIN
  IF NEW.status IS NOT DISTINCT FROM OLD.status THEN RETURN NEW; END IF;
  SELECT title INTO v_title FROM public.market_items WHERE id=NEW.item_id;

  IF NEW.status IN ('accepted','declined') THEN
    v_recipient:=NEW.buyer_id; v_actor:=NEW.seller_id;
  ELSE
    v_recipient:=CASE WHEN auth.uid()=NEW.buyer_id THEN NEW.seller_id ELSE NEW.buyer_id END;
    v_actor:=CASE WHEN v_recipient=NEW.buyer_id THEN NEW.seller_id ELSE NEW.buyer_id END;
  END IF;

  INSERT INTO public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message,is_read)
  VALUES(
    v_recipient,
    v_actor,
    'MARKET_ORDER',
    'market',
    NEW.item_id,
    'updated '||COALESCE(v_title,'a market order')||' to '||NEW.status,
    false
  );
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_activity_marketplace_order_status ON public.marketplace_orders;
CREATE TRIGGER trg_activity_marketplace_order_status
AFTER UPDATE OF status ON public.marketplace_orders
FOR EACH ROW EXECUTE FUNCTION public.activity_from_marketplace_order_status();

COMMIT;
