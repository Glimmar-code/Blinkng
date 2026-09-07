revoke all on function public.get_admin_message_detail(uuid) from public;
revoke all on function public.get_admin_message_detail(uuid) from anon;
grant execute on function public.get_admin_message_detail(uuid) to authenticated;
