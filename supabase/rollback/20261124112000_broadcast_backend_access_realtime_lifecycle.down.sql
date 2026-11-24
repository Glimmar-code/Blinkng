-- Safe rollback for policies/grants/cron/Realtime.
-- Retains Broadcast tables, records, bucket, and storage files.
DO $$ DECLARE j record; BEGIN
 FOR j IN SELECT jobid FROM cron.job WHERE jobname IN ('blink_broadcast_lifecycle_minutely','blink_broadcast_purge_daily') LOOP PERFORM cron.unschedule(j.jobid); END LOOP;
END $$;
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='broadcasts_changed') THEN ALTER PUBLICATION supabase_realtime DROP TABLE public.broadcasts_changed; END IF;
  IF EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='broadcast_reads') THEN ALTER PUBLICATION supabase_realtime DROP TABLE public.broadcast_reads; END IF;
  IF EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='broadcast_dismissals') THEN ALTER PUBLICATION supabase_realtime DROP TABLE public.broadcast_dismissals; END IF;
  IF EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='campaign_participants') THEN ALTER PUBLICATION supabase_realtime DROP TABLE public.campaign_participants; END IF;
END $$;
DROP POLICY IF EXISTS admin_config_all ON public.broadcast_app_config;
DROP POLICY IF EXISTS admin_audit_read ON public.broadcast_audit_log;
DROP POLICY IF EXISTS admin_analytics_clicks_all ON public.broadcast_clicks;
DROP POLICY IF EXISTS admin_rollups_all ON public.broadcast_daily_rollups;
DROP POLICY IF EXISTS admin_deliveries_all ON public.broadcast_deliveries;
DROP POLICY IF EXISTS dismiss_insert_own ON public.broadcast_dismissals;
DROP POLICY IF EXISTS dismiss_own ON public.broadcast_dismissals;
DROP POLICY IF EXISTS admin_analytics_all ON public.broadcast_impressions;
DROP POLICY IF EXISTS admin_rate_limits_all ON public.broadcast_rate_limits;
DROP POLICY IF EXISTS reads_insert_own ON public.broadcast_reads;
DROP POLICY IF EXISTS reads_own ON public.broadcast_reads;
DROP POLICY IF EXISTS broadcast_admin_all ON public.broadcasts;
DROP POLICY IF EXISTS broadcast_user_live_read ON public.broadcasts;
DROP POLICY IF EXISTS broadcast_signal_read ON public.broadcasts_changed;
DROP POLICY IF EXISTS admin_participants_all ON public.campaign_participants;
DROP POLICY IF EXISTS own_participants_select ON public.campaign_participants;
DROP POLICY IF EXISTS admin_events_all ON public.campaign_progress_events;
DROP POLICY IF EXISTS broadcast_admin_rules_campaign ON public.campaign_rules;
DROP POLICY IF EXISTS own_device_tokens_all ON public.device_tokens;
DROP POLICY IF EXISTS own_prefs_all ON public.notification_prefs;
DROP POLICY IF EXISTS admin_claim_all ON public.promo_claims;
DROP POLICY IF EXISTS own_claim_select ON public.promo_claims;
DROP POLICY IF EXISTS broadcast_admin_rules_promo ON public.promo_rules;
DROP POLICY IF EXISTS broadcast_images_admin_delete ON storage.objects;
DROP POLICY IF EXISTS broadcast_images_admin_update ON storage.objects;
DROP POLICY IF EXISTS broadcast_images_admin_upload ON storage.objects;
DROP POLICY IF EXISTS broadcast_images_public_read ON storage.objects;
REVOKE ALL ON public.broadcast_app_config, public.broadcast_audit_log, public.broadcast_clicks, public.broadcast_daily_rollups, public.broadcast_deliveries, public.broadcast_dismissals, public.broadcast_impressions, public.broadcast_rate_limits, public.broadcast_reads, public.broadcasts, public.broadcasts_changed, public.campaign_participants, public.campaign_progress_events, public.campaign_rules, public.device_tokens, public.notification_prefs, public.promo_claims, public.promo_rules FROM anon,authenticated;
