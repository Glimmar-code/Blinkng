-- Security rollback is intentionally fail-closed. Do not restore the unsafe direct-execution grants.
-- This script disables Broadcast client RPCs; data, function definitions, tables and wallet remain.
REVOKE EXECUTE ON FUNCTION public.admin_broadcast_analytics(uuid),public.admin_get_broadcast_audit(uuid,integer),
  public.admin_estimate_broadcast_audience(jsonb,text),public.admin_create_broadcast(jsonb),public.admin_update_broadcast(uuid,integer,jsonb),
  public.admin_end_broadcast(uuid),public.admin_duplicate_broadcast(uuid),public.admin_delete_broadcast(uuid),
  public.admin_update_broadcast_config(jsonb),public.get_active_broadcasts(text,text),public.dismiss_broadcast(uuid),
  public.mark_read(uuid),public.mark_all_read(),public.track_broadcast_impression(uuid,text),public.track_broadcast_click(uuid,text),
  public.join_campaign(uuid),public.get_campaign_progress(uuid),public.get_campaign_leaderboard(uuid),public.claim_promo(uuid,text,numeric),
  public.get_broadcast_inbox(text,timestamptz,integer),public.get_broadcast_inbox_keyset(text,timestamptz,uuid,integer),
  public.get_broadcast_prefs(),public.update_broadcast_prefs(jsonb),public.register_broadcast_device_token(text,text,text),
  public.get_broadcast_blocking_state(),public.get_website_broadcasts() FROM PUBLIC,anon,authenticated;
