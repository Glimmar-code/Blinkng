-- Only anonymous browser clients and the service role may execute the public web API.
-- Authenticated Android clients continue using their existing app RPCs.
revoke all on function public.get_public_web_profile(text) from public, authenticated;
revoke all on function public.get_public_web_content(uuid,text) from public, authenticated;
revoke all on function public.record_public_web_view(uuid,text) from public, authenticated;

grant execute on function public.get_public_web_profile(text) to anon, service_role;
grant execute on function public.get_public_web_content(uuid,text) to anon, service_role;
grant execute on function public.record_public_web_view(uuid,text) to anon, service_role;
