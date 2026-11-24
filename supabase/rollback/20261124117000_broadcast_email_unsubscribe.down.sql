-- Rollback email unsubscribe feature safely. Keep token records for audit and do not re-enable opted-out users.
REVOKE EXECUTE ON FUNCTION public.broadcast_email_unsubscribe(text) FROM PUBLIC,anon,authenticated;
