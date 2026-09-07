-- Mirror production migration 20260907173819.
-- Store/VIP SECURITY DEFINER RPCs are intentionally callable only by signed-in users.

revoke execute on function public.activate_blink_item(uuid, uuid) from public, anon;
revoke execute on function public.blink_is_vip(uuid) from public, anon;
revoke execute on function public.claim_blink_vip_benefit(text) from public, anon;
revoke execute on function public.get_blink_store_state() from public, anon;
revoke execute on function public.get_blink_vip_statuses(uuid[]) from public, anon;
revoke execute on function public.get_my_blink_boostable_content() from public, anon;
revoke execute on function public.gift_blink_vip(text) from public, anon;
revoke execute on function public.purchase_blink_item(text, integer, integer) from public, anon;
revoke execute on function public.renew_blink_vip() from public, anon;
revoke execute on function public.set_blink_item_equipped(uuid, text, boolean) from public, anon;
revoke execute on function public.set_blink_vip_auto_renew(boolean) from public, anon;
revoke execute on function public.send_blink_digital_gift(uuid, text, text) from public, anon;
revoke execute on function public.get_blink_vip_status_by_username(text) from public, anon;

grant execute on function public.activate_blink_item(uuid, uuid) to authenticated;
grant execute on function public.blink_is_vip(uuid) to authenticated;
grant execute on function public.claim_blink_vip_benefit(text) to authenticated;
grant execute on function public.get_blink_store_state() to authenticated;
grant execute on function public.get_blink_vip_statuses(uuid[]) to authenticated;
grant execute on function public.get_my_blink_boostable_content() to authenticated;
grant execute on function public.gift_blink_vip(text) to authenticated;
grant execute on function public.purchase_blink_item(text, integer, integer) to authenticated;
grant execute on function public.renew_blink_vip() to authenticated;
grant execute on function public.set_blink_item_equipped(uuid, text, boolean) to authenticated;
grant execute on function public.set_blink_vip_auto_renew(boolean) to authenticated;
grant execute on function public.send_blink_digital_gift(uuid, text, text) to authenticated;
grant execute on function public.get_blink_vip_status_by_username(text) to authenticated;
