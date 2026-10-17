-- Market selling is a reviewed privilege, not a consequence of purchasing a blue tick.
-- Rollback: reinstate 20261017181500 Market SELECT/INSERT policies and remove
-- approval triggers after auditing the affected transactions and seller queue.
BEGIN;
CREATE OR REPLACE FUNCTION public.market_require_approved_seller()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $seller_gate$
BEGIN
  IF NOT public.market_seller_has_approval(NEW.seller_id)
  THEN RAISE EXCEPTION 'MARKET_SELLER_APPROVAL_REQUIRED'; END IF;
  RETURN NEW;
END;
$seller_gate$;
DROP TRIGGER IF EXISTS market_require_approved_seller_insert ON public.market_items;
CREATE TRIGGER market_require_approved_seller_insert BEFORE INSERT ON public.market_items
FOR EACH ROW EXECUTE FUNCTION public.market_require_approved_seller();

DROP POLICY IF EXISTS market_items_insert_own ON public.market_items;
CREATE POLICY market_items_insert_own ON public.market_items
FOR INSERT TO authenticated
WITH CHECK(seller_id=(select auth.uid())
  AND public.market_seller_has_approval((select auth.uid())));

DROP POLICY IF EXISTS market_items_select_visible ON public.market_items;
CREATE POLICY market_items_select_visible ON public.market_items FOR SELECT TO authenticated
USING(
  seller_id=(select auth.uid())
  OR (
    status='active' AND NOT coalesce(is_sold,false)
    AND moderation_state='approved'
    AND public.market_seller_has_approval(seller_id)
  )
);

CREATE OR REPLACE FUNCTION public.market_require_approved_seller_for_order()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $order_approval$
BEGIN
  IF NOT public.market_seller_has_approval(NEW.seller_id)
  THEN RAISE EXCEPTION 'MARKET_SELLER_APPROVAL_REQUIRED'; END IF;
  RETURN NEW;
END;
$order_approval$;
DROP TRIGGER IF EXISTS market_require_approved_seller_for_order_trigger ON public.marketplace_orders;
CREATE TRIGGER market_require_approved_seller_for_order_trigger
BEFORE INSERT ON public.marketplace_orders FOR EACH ROW
EXECUTE FUNCTION public.market_require_approved_seller_for_order();

-- Expiry and revocation affect all advertised listings without deleting seller
-- content or order history; admins can reverse the decision through audited RPCs.
CREATE INDEX IF NOT EXISTS market_merchant_approval_idx
ON private.market_seller_approvals(decision,created_at);
COMMIT;
