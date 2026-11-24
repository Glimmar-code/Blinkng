-- Broadcast backend policies, grants, Storage, Realtime and lifecycle cron.
-- Run after 20261124111000_broadcast_backend_rpc_recovery.sql.
CREATE EXTENSION IF NOT EXISTS pg_cron;

GRANT ALL ON public.broadcast_app_config, public.broadcast_audit_log, public.broadcast_clicks, public.broadcast_daily_rollups, public.broadcast_deliveries, public.broadcast_dismissals, public.broadcast_impressions, public.broadcast_rate_limits, public.broadcast_reads, public.broadcasts, public.broadcasts_changed, public.campaign_participants, public.campaign_progress_events, public.campaign_rules, public.device_tokens, public.notification_prefs, public.promo_claims, public.promo_rules TO service_role;
GRANT SELECT ON public.broadcasts,public.broadcasts_changed,public.broadcast_dismissals,public.broadcast_reads,public.campaign_participants,public.promo_claims,public.notification_prefs,public.device_tokens TO authenticated;
GRANT INSERT ON public.broadcast_dismissals,public.broadcast_reads TO authenticated;
GRANT INSERT,UPDATE ON public.notification_prefs TO authenticated;
GRANT INSERT,UPDATE,DELETE ON public.device_tokens TO authenticated;
GRANT SELECT ON public.broadcast_app_config,public.broadcast_audit_log,public.broadcast_clicks,public.broadcast_daily_rollups,public.broadcast_deliveries,public.broadcast_impressions,public.broadcast_rate_limits,public.campaign_progress_events,public.campaign_rules,public.promo_rules TO authenticated;

ALTER TABLE public.broadcast_app_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.broadcast_audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.broadcast_clicks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.broadcast_daily_rollups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.broadcast_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.broadcast_dismissals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.broadcast_impressions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.broadcast_rate_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.broadcast_reads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.broadcasts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.broadcasts_changed ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.campaign_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.campaign_progress_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.campaign_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notification_prefs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.promo_claims ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.promo_rules ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='broadcast_app_config' AND policyname='admin_config_all') THEN CREATE POLICY admin_config_all ON public.broadcast_app_config FOR ALL TO authenticated USING (broadcast_admin_check()); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='broadcast_audit_log' AND policyname='admin_audit_read') THEN CREATE POLICY admin_audit_read ON public.broadcast_audit_log FOR SELECT TO authenticated USING (broadcast_admin_check()); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='broadcast_clicks' AND policyname='admin_analytics_clicks_all') THEN CREATE POLICY admin_analytics_clicks_all ON public.broadcast_clicks FOR ALL TO authenticated USING (broadcast_admin_check()); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='broadcast_daily_rollups' AND policyname='admin_rollups_all') THEN CREATE POLICY admin_rollups_all ON public.broadcast_daily_rollups FOR ALL TO authenticated USING (broadcast_admin_check()); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='broadcast_deliveries' AND policyname='admin_deliveries_all') THEN CREATE POLICY admin_deliveries_all ON public.broadcast_deliveries FOR ALL TO authenticated USING (broadcast_admin_check()); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='broadcast_dismissals' AND policyname='dismiss_insert_own') THEN CREATE POLICY dismiss_insert_own ON public.broadcast_dismissals FOR INSERT TO authenticated WITH CHECK ((user_id = auth.uid())); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='broadcast_dismissals' AND policyname='dismiss_own') THEN CREATE POLICY dismiss_own ON public.broadcast_dismissals FOR SELECT TO authenticated USING ((user_id = auth.uid())); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='broadcast_impressions' AND policyname='admin_analytics_all') THEN CREATE POLICY admin_analytics_all ON public.broadcast_impressions FOR ALL TO authenticated USING (broadcast_admin_check()); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='broadcast_rate_limits' AND policyname='admin_rate_limits_all') THEN CREATE POLICY admin_rate_limits_all ON public.broadcast_rate_limits FOR ALL TO authenticated USING (broadcast_admin_check()); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='broadcast_reads' AND policyname='reads_insert_own') THEN CREATE POLICY reads_insert_own ON public.broadcast_reads FOR INSERT TO authenticated WITH CHECK ((user_id = auth.uid())); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='broadcast_reads' AND policyname='reads_own') THEN CREATE POLICY reads_own ON public.broadcast_reads FOR SELECT TO authenticated USING ((user_id = auth.uid())); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='broadcasts' AND policyname='broadcast_admin_all') THEN CREATE POLICY broadcast_admin_all ON public.broadcasts FOR ALL TO authenticated USING (broadcast_admin_check()) WITH CHECK (broadcast_admin_check()); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='broadcasts' AND policyname='broadcast_user_live_read') THEN CREATE POLICY broadcast_user_live_read ON public.broadcasts FOR SELECT TO authenticated USING (((status = 'live'::broadcast_status) AND (deleted_at IS NULL) AND ((starts_at IS NULL) OR (starts_at <= now())) AND ((ends_at IS NULL) OR (ends_at > now())) AND broadcast_audience_match_self(audience))); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='broadcasts_changed' AND policyname='broadcast_signal_read') THEN CREATE POLICY broadcast_signal_read ON public.broadcasts_changed FOR SELECT TO authenticated USING (true); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='campaign_participants' AND policyname='admin_participants_all') THEN CREATE POLICY admin_participants_all ON public.campaign_participants FOR ALL TO authenticated USING (broadcast_admin_check()) WITH CHECK (broadcast_admin_check()); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='campaign_participants' AND policyname='own_participants_select') THEN CREATE POLICY own_participants_select ON public.campaign_participants FOR SELECT TO authenticated USING ((user_id = auth.uid())); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='campaign_progress_events' AND policyname='admin_events_all') THEN CREATE POLICY admin_events_all ON public.campaign_progress_events FOR ALL TO authenticated USING (broadcast_admin_check()) WITH CHECK (broadcast_admin_check()); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='campaign_rules' AND policyname='broadcast_admin_rules_campaign') THEN CREATE POLICY broadcast_admin_rules_campaign ON public.campaign_rules FOR ALL TO authenticated USING (broadcast_admin_check()) WITH CHECK (broadcast_admin_check()); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='device_tokens' AND policyname='own_device_tokens_all') THEN CREATE POLICY own_device_tokens_all ON public.device_tokens FOR ALL TO authenticated USING ((user_id = auth.uid())) WITH CHECK ((user_id = auth.uid())); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='notification_prefs' AND policyname='own_prefs_all') THEN CREATE POLICY own_prefs_all ON public.notification_prefs FOR ALL TO authenticated USING ((user_id = auth.uid())) WITH CHECK ((user_id = auth.uid())); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='promo_claims' AND policyname='admin_claim_all') THEN CREATE POLICY admin_claim_all ON public.promo_claims FOR ALL TO authenticated USING (broadcast_admin_check()) WITH CHECK (broadcast_admin_check()); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='promo_claims' AND policyname='own_claim_select') THEN CREATE POLICY own_claim_select ON public.promo_claims FOR SELECT TO authenticated USING ((user_id = auth.uid())); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='promo_rules' AND policyname='broadcast_admin_rules_promo') THEN CREATE POLICY broadcast_admin_rules_promo ON public.promo_rules FOR ALL TO authenticated USING (broadcast_admin_check()) WITH CHECK (broadcast_admin_check()); END IF; END $$;

-- Storage bucket is public-read, admin-write, image-only and capped at 8 MiB.
INSERT INTO storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
VALUES('broadcast-images','broadcast-images',true,8388608,ARRAY['image/jpeg','image/png','image/webp','image/gif'])
ON CONFLICT(id) DO NOTHING;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='storage' AND tablename='objects' AND policyname='broadcast_images_admin_delete') THEN CREATE POLICY broadcast_images_admin_delete ON storage.objects FOR DELETE TO authenticated USING (((bucket_id = 'broadcast-images'::text) AND broadcast_admin_check())); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='storage' AND tablename='objects' AND policyname='broadcast_images_admin_update') THEN CREATE POLICY broadcast_images_admin_update ON storage.objects FOR UPDATE TO authenticated USING (((bucket_id = 'broadcast-images'::text) AND broadcast_admin_check())) WITH CHECK (((bucket_id = 'broadcast-images'::text) AND broadcast_admin_check() AND (name ~ '^broadcasts/[0-9a-fA-F-]{36}/[^/]+$'::text))); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='storage' AND tablename='objects' AND policyname='broadcast_images_admin_upload') THEN CREATE POLICY broadcast_images_admin_upload ON storage.objects FOR INSERT TO authenticated WITH CHECK (((bucket_id = 'broadcast-images'::text) AND broadcast_admin_check() AND (name ~ '^broadcasts/[0-9a-fA-F-]{36}/[^/]+$'::text))); END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='storage' AND tablename='objects' AND policyname='broadcast_images_public_read') THEN CREATE POLICY broadcast_images_public_read ON storage.objects FOR SELECT TO anon, authenticated USING ((bucket_id = 'broadcast-images'::text)); END IF; END $$;

-- Only lightweight signals and user-owned rows are in Realtime publication.
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='broadcasts_changed') THEN ALTER PUBLICATION supabase_realtime ADD TABLE public.broadcasts_changed; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='broadcast_reads') THEN ALTER PUBLICATION supabase_realtime ADD TABLE public.broadcast_reads; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='broadcast_dismissals') THEN ALTER PUBLICATION supabase_realtime ADD TABLE public.broadcast_dismissals; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='campaign_participants') THEN ALTER PUBLICATION supabase_realtime ADD TABLE public.campaign_participants; END IF;
END $$;

DO $$ DECLARE j record; BEGIN
 IF NOT EXISTS (SELECT 1 FROM cron.job WHERE jobname='blink_broadcast_lifecycle_minutely') THEN PERFORM cron.schedule('blink_broadcast_lifecycle_minutely','* * * * *','select public.broadcast_lifecycle_tick()'); END IF;
 IF NOT EXISTS (SELECT 1 FROM cron.job WHERE jobname='blink_broadcast_purge_daily') THEN PERFORM cron.schedule('blink_broadcast_purge_daily','15 3 * * *','select public.broadcast_purge_old_events()'); END IF;
END $$;
