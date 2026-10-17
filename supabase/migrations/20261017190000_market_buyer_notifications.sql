-- BLINK Market buyer notifications: price reductions, order status and stale inventory.
-- Rollback: remove event triggers and scheduled job, preserve notification log/history.
BEGIN;

CREATE OR REPLACE FUNCTION private.market_notify_saved_price_drop()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
SET search_path=public,private,pg_temp AS $price_drop$
BEGIN
 IF NEW.price>=OLD.price*0.95 OR NEW.price<=0 OR
    NEW.status<>'active' OR NEW.moderation_state<>'approved'
 THEN RETURN NEW; END IF;
 WITH eligible AS (
  SELECT w.user_id
  FROM public.marketplace_wishlist w
  JOIN public.profiles p ON p.id=NEW.seller_id
  WHERE w.item_id=NEW.id AND w.user_id<>NEW.seller_id
    AND p.is_seller_active AND p.seller_status='active'
    AND p.is_verified
    AND upper(p.verification_badge) IN ('BLUE','GOLD')
    AND (p.verification_expires_at>now()
       OR (upper(p.verification_badge)='GOLD' AND p.verification_expires_at IS NULL))
 ), logged AS (
   INSERT INTO private.market_notification_log(user_id,kind,marker)
   SELECT user_id,'price_drop',NEW.id::text||':'||current_date::text
   FROM eligible ON CONFLICT DO NOTHING RETURNING user_id
 )
 INSERT INTO public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message,is_read)
 SELECT user_id,NEW.seller_id,'MARKET_PRICE_DROP','market',NEW.id,
   left(NEW.title,120)||' has a new lower price. Check BLINK Market.',false
 FROM logged;
 RETURN NEW;
END;
$price_drop$;
DROP TRIGGER IF EXISTS market_saved_price_drop_trigger ON public.market_items;
CREATE TRIGGER market_saved_price_drop_trigger
AFTER UPDATE OF price ON public.market_items
FOR EACH ROW EXECUTE FUNCTION private.market_notify_saved_price_drop();

CREATE OR REPLACE FUNCTION private.market_inactive_listing_reminders()
RETURNS integer LANGUAGE plpgsql SECURITY DEFINER
SET search_path=public,private,pg_temp AS $listing_reminder$
DECLARE v_count integer:=0;
BEGIN
 WITH should_remind AS (
  SELECT m.id,m.seller_id,m.title,
   CASE WHEN m.availability_confirmed_at<now()-interval '30 days'
        THEN 'expired' ELSE '25d' END notice
  FROM public.market_items m WHERE m.status IN ('active','paused')
    AND m.availability_confirmed_at<=now()-interval '25 days'
    AND m.availability_confirmed_at>=now()-interval '120 days'
 ), logged AS (
  INSERT INTO private.market_notification_log(user_id,kind,marker)
  SELECT seller_id,'listing_'||notice,id::text||':'||availability_confirmed_at::date::text
  FROM should_remind
  ON CONFLICT DO NOTHING RETURNING user_id,kind,marker
 )
 INSERT INTO public.activities(recipient_id,actor_id,activity_type,entity_type,message,is_read)
 SELECT user_id,NULL,'MARKET_LISTING','market',
   CASE WHEN kind='listing_25d'
     THEN 'Your Market listing needs availability confirmation within 5 days.'
     ELSE 'A Market listing was paused because its availability was not confirmed for 30 days.' END,
   false
 FROM logged;
 GET DIAGNOSTICS v_count=ROW_COUNT;
 RETURN v_count;
END;
$listing_reminder$;
REVOKE ALL ON FUNCTION private.market_inactive_listing_reminders() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION private.market_inactive_listing_reminders() TO service_role;

DO $market_notices_cron$
BEGIN
 IF to_regprocedure('cron.schedule(text,text,text)') IS NOT NULL
    AND to_regclass('cron.job') IS NOT NULL THEN
   IF NOT EXISTS(SELECT 1 FROM cron.job WHERE jobname='blink-market-seller-listing-reminders') THEN
     PERFORM cron.schedule('blink-market-seller-listing-reminders','25 4 * * *',
       'select private.market_inactive_listing_reminders()');
   END IF;
 END IF;
EXCEPTION WHEN undefined_table OR undefined_function OR insufficient_privilege THEN
 RAISE NOTICE 'Market seller inventory reminders require scheduling';
END;
$market_notices_cron$;
COMMIT;
