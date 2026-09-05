-- Keep one canonical authenticated messaging RPC. The old three-argument overload
-- duplicated sender identity from the client and could cause ambiguous/legacy call paths.
drop function if exists public.send_message(uuid, text, text);

-- Explicit RPC permissions used by the Android messaging + push-token pipeline.
grant execute on function public.send_message(text, text) to authenticated;
grant execute on function public.get_conversation_summaries(integer) to authenticated;
grant execute on function public.get_conversation_messages_page(uuid, integer, timestamptz, uuid) to authenticated;
grant execute on function public.register_my_fcm_token(text) to authenticated;
