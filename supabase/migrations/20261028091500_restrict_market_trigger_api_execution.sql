-- BLINK: remove public RPC execution of seller-approval trigger functions.
-- These functions return TRIGGER and are only meant to run from the
-- existing market_items / marketplace_orders triggers. No user data changes.
-- Rollback (only if unavoidable): GRANT EXECUTE on each function to the
-- specific role(s) requiring it; do not blanket-grant to PUBLIC.
begin;

revoke all on function public.market_require_approved_seller()
  from public, anon, authenticated;

revoke all on function public.market_require_approved_seller_for_order()
  from public, anon, authenticated;

-- Preserve the service-side execution contract; do not expose an RPC
-- entry point to the unauthenticated or normal authenticated API roles.
grant execute on function public.market_require_approved_seller() to service_role;
grant execute on function public.market_require_approved_seller_for_order() to service_role;

commit;
