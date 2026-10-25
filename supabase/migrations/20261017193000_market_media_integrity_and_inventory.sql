-- Preserve truthful inventory and require actual product imagery at publication.
-- Rollback: remove integrity triggers, retain existing storage and listing rows.
BEGIN;
CREATE OR REPLACE FUNCTION public.market_require_listing_media()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER
SET search_path=public,pg_temp AS $market_media_integrity$
DECLARE v_image text;v_count integer;
BEGIN
 v_image:=nullif(trim(coalesce(NEW.image_url,'')),'');
 v_count:=coalesce(array_length(NEW.image_urls,1),0);
 IF v_image IS NULL OR v_image !~ '^https://' OR v_count>8 THEN
   RAISE EXCEPTION 'VALID_PRODUCT_PHOTO_REQUIRED'; END IF;
 IF v_count>0 THEN
   IF NOT v_image=NEW.image_urls[1] THEN
      RAISE EXCEPTION 'PRODUCT_COVER_MUST_MATCH_FIRST_IMAGE'; END IF;
   IF EXISTS(SELECT 1 FROM unnest(NEW.image_urls) AS u(src)
     WHERE src IS NULL OR src !~ '^https://' OR length(src)>1500)
   THEN RAISE EXCEPTION 'INVALID_PRODUCT_PHOTOS'; END IF;
 END IF;
 IF coalesce(NEW.quantity,0) NOT BETWEEN 1 AND 9999 THEN
   RAISE EXCEPTION 'INVALID_PRODUCT_QUANTITY'; END IF;
 RETURN NEW;
END;
$market_media_integrity$;
DROP TRIGGER IF EXISTS market_require_listing_media_insert ON public.market_items;
CREATE TRIGGER market_require_listing_media_insert
BEFORE INSERT ON public.market_items FOR EACH ROW
EXECUTE FUNCTION public.market_require_listing_media();

CREATE OR REPLACE FUNCTION public.market_protect_inventory_freshness()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER
SET search_path=public,pg_temp AS $market_inventory_integrity$
BEGIN
 IF current_user IN ('authenticated','anon') THEN
   -- Renewal is a separate verified-seller RPC. A modified app must not turn
   -- its timestamp or photo fingerprint into an indefinitely fresh listing.
   NEW.availability_confirmed_at:=OLD.availability_confirmed_at;
   NEW.media_digest:=OLD.media_digest;
 END IF;
 RETURN NEW;
END;
$market_inventory_integrity$;
DROP TRIGGER IF EXISTS market_protect_inventory_freshness_trigger ON public.market_items;
CREATE TRIGGER market_protect_inventory_freshness_trigger
BEFORE UPDATE ON public.market_items FOR EACH ROW
EXECUTE FUNCTION public.market_protect_inventory_freshness();
COMMIT;
