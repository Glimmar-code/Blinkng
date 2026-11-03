-- The project has explicit role grants in addition to PostgreSQL PUBLIC defaults.
-- Keep these client RPCs unavailable to anonymous sessions.
revoke execute on function public.send_message_v2(text, text) from anon;
revoke execute on function public.ack_pending_message_deliveries() from anon;
grant execute on function public.send_message_v2(text, text) to authenticated;
grant execute on function public.ack_pending_message_deliveries() to authenticated;
