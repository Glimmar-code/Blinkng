revoke execute on function public.record_post_view(uuid) from anon;
revoke execute on function public.record_post_view(uuid, text) from anon;
grant execute on function public.record_post_view(uuid) to authenticated;
grant execute on function public.record_post_view(uuid, text) to authenticated;
