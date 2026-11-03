-- Count genuine repeat visits to Market listings while preserving the daily unique-view
-- table used by existing analytics. Event IDs make retries idempotent; server-side dwell
-- and daily caps prevent client refreshes from manufacturing unlimited impressions.
BEGIN;

CREATE TABLE IF NOT EXISTS private.marketplace_view_events (
  event_id uuid PRIMARY KEY,
  viewer_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  item_id uuid NOT NULL REFERENCES public.market_items(id) ON DELETE CASCADE,
  viewed_on date NOT NULL DEFAULT CURRENT_DATE,
  view_weight integer NOT NULL CHECK (view_weight IN (1, 3, 6)),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS marketplace_view_events_viewer_item_day_idx
  ON private.marketplace_view_events(viewer_id, item_id, viewed_on, created_at DESC);
ALTER TABLE private.marketplace_view_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON private.marketplace_view_events FROM PUBLIC, anon, authenticated;

-- Drop the old one-argument implementation. The replacement keeps its one-argument
-- call compatibility through a generated event-id default for older clients.
DROP FUNCTION IF EXISTS public.record_marketplace_view(uuid);

CREATE OR REPLACE FUNCTION public.record_marketplace_view(
  p_item_id uuid,
  p_event_id uuid DEFAULT gen_random_uuid()
)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,private,pg_temp
AS $market_repeat_views$
DECLARE
  v_uid uuid := auth.uid();
  v_weight integer := 1;
  v_count bigint := 0;
  v_existing boolean := false;
  v_recent boolean := false;
  v_daily_count integer := 0;
  v_daily_weight integer := 0;
  v_seller uuid;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  IF p_item_id IS NULL OR p_event_id IS NULL THEN RAISE EXCEPTION 'INVALID_VIEW_EVENT'; END IF;

  -- Lock the listing row so concurrent visits cannot race the rate/cap checks.
  SELECT seller_id, COALESCE(views_count,0)
    INTO v_seller, v_count
    FROM public.market_items
   WHERE id=p_item_id
     AND COALESCE(status,'active') NOT IN ('removed','suspended')
     AND NOT COALESCE(is_sold,false)
   FOR UPDATE;
  IF NOT FOUND THEN RETURN 0; END IF;

  IF v_seller = v_uid THEN RETURN v_count; END IF;

  SELECT EXISTS(
    SELECT 1 FROM private.marketplace_view_events
     WHERE event_id=p_event_id AND viewer_id=v_uid AND item_id=p_item_id
  ) INTO v_existing;
  IF v_existing THEN RETURN v_count; END IF;

  SELECT EXISTS(
    SELECT 1 FROM private.marketplace_view_events
     WHERE viewer_id=v_uid AND item_id=p_item_id
       AND created_at > now() - interval '2 seconds'
  ) INTO v_recent;
  IF v_recent THEN RETURN v_count; END IF;

  SELECT count(*)::integer, COALESCE(sum(view_weight),0)::integer
    INTO v_daily_count, v_daily_weight
    FROM private.marketplace_view_events
   WHERE viewer_id=v_uid AND item_id=p_item_id
     AND viewed_on=CURRENT_DATE;
  IF v_daily_count >= 100 THEN RETURN v_count; END IF;

  -- Verification is resolved from the protected profile row, never client input.
  SELECT CASE
    WHEN COALESCE(p.is_verified,false)
      AND (p.verification_expires_at IS NULL OR p.verification_expires_at > now())
      AND (upper(COALESCE(p.verification_badge::text,''))='GOLD'
        OR upper(COALESCE(p.verification_tier::text,''))='GOLD') THEN 6
    WHEN COALESCE(p.is_verified,false)
      AND (p.verification_expires_at IS NULL OR p.verification_expires_at > now()) THEN 3
    ELSE 1
  END
  INTO v_weight
  FROM public.profiles p
  WHERE p.id=v_uid;
  v_weight := COALESCE(v_weight,1);

  IF v_daily_weight + v_weight > 600 THEN RETURN v_count; END IF;

  INSERT INTO private.marketplace_view_events(
    event_id,viewer_id,item_id,viewed_on,view_weight
  ) VALUES(p_event_id,v_uid,p_item_id,CURRENT_DATE,v_weight)
  ON CONFLICT(event_id) DO NOTHING;
  IF NOT FOUND THEN RETURN v_count; END IF;

  -- Retain the legacy daily unique visitor row for analytics; it is no longer
  -- the listing-counter dedupe key.
  INSERT INTO public.marketplace_views(user_id,item_id)
  VALUES(v_uid,p_item_id)
  ON CONFLICT DO NOTHING;

  UPDATE public.market_items
     SET views_count=COALESCE(views_count,0)+v_weight,
         updated_at=now()
   WHERE id=p_item_id
  RETURNING views_count INTO v_count;

  RETURN COALESCE(v_count,0);
END;
$market_repeat_views$;

REVOKE ALL ON FUNCTION public.record_marketplace_view(uuid,uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.record_marketplace_view(uuid,uuid) TO authenticated;

COMMIT;
