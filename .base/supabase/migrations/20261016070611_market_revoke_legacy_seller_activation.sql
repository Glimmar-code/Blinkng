-- BLINK Market follow-up: disable pre-payment seller activation bypasses.
-- Rollback: GRANT EXECUTE ON FUNCTION public.activate_marketplace_profile(text)
--          TO authenticated; only if the legacy flow is deliberately restored.
-- Trigger functions remain attached; revoking public EXECUTE does not stop
-- their automatic execution through authorized database events.
BEGIN;
REVOKE ALL ON FUNCTION public.activate_marketplace_profile(text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.recalc_marketplace_saves() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.activity_from_marketplace_order() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.activity_from_marketplace_order_status() FROM PUBLIC, anon, authenticated;
COMMIT;
