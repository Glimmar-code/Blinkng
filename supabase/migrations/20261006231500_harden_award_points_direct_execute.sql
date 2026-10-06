-- BLINK security hardening: award_points is an internal points primitive.
-- Client features earn points through validated writes/RPCs whose SECURITY DEFINER
-- trigger/service functions call award_points. No Android/Windows client calls this
-- function directly, so direct Data API execution is unnecessary and abusable.

revoke all on function public.award_points(uuid, text, uuid) from public;
revoke execute on function public.award_points(uuid, text, uuid) from anon;
revoke execute on function public.award_points(uuid, text, uuid) from authenticated;

-- Preserve trusted server-side maintenance/integration access. Function owner calls
-- (including existing SECURITY DEFINER trigger functions) are unaffected by revokes.
grant execute on function public.award_points(uuid, text, uuid) to service_role;
