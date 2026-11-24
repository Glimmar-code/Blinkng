-- Safe rollback for schema recovery migration.
-- Do not drop Broadcast data, tables, types, or storage objects automatically.
-- This rollback only removes optional realtime membership and disables Broadcast cron jobs.
DO $$ DECLARE j record; BEGIN
  FOR j IN SELECT jobid FROM cron.job WHERE jobname IN ('blink_broadcast_lifecycle_minutely','blink_broadcast_purge_daily') LOOP PERFORM cron.unschedule(j.jobid); END LOOP;
END $$;
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='broadcasts_changed') THEN ALTER PUBLICATION supabase_realtime DROP TABLE public.broadcasts_changed; END IF;
  IF EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='broadcast_reads') THEN ALTER PUBLICATION supabase_realtime DROP TABLE public.broadcast_reads; END IF;
  IF EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='broadcast_dismissals') THEN ALTER PUBLICATION supabase_realtime DROP TABLE public.broadcast_dismissals; END IF;
  IF EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='campaign_participants') THEN ALTER PUBLICATION supabase_realtime DROP TABLE public.campaign_participants; END IF;
END $$;
