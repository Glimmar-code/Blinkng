-- Blink Supabase recovery snapshot: Storage buckets, Realtime publication, and cron schedules.
-- Contains configuration only. It does NOT contain uploaded object bytes or user rows.
-- Run after schemas/functions/tables are restored.

-- Storage bucket configuration (10 live buckets)
INSERT INTO storage.buckets (id,name,public,file_size_limit,allowed_mime_types) VALUES
 ('avatars','avatars',true,NULL,NULL),
 ('covers','covers',true,NULL,NULL),
 ('feed-media','feed-media',false,NULL,NULL),
 ('marketplace-media','marketplace-media',true,NULL,NULL),
 ('message-media','message-media',false,NULL,NULL),
 ('post-media','post-media',true,NULL,NULL),
 ('posts','posts',true,NULL,NULL),
 ('profile-media','profile-media',true,NULL,NULL),
 ('status-media','status-media',true,NULL,NULL),
 ('story-media','story-media',true,NULL,NULL)
ON CONFLICT (id) DO UPDATE SET
 name=EXCLUDED.name,
 public=EXCLUDED.public,
 file_size_limit=EXCLUDED.file_size_limit,
 allowed_mime_types=EXCLUDED.allowed_mime_types;

-- Realtime membership (32 live app tables). Add only when missing.
DO $realtime$
DECLARE r record;
BEGIN
  FOR r IN SELECT * FROM (VALUES
    ('public','activities'),('public','call_signals'),('public','calls'),
    ('public','comment_likes'),('public','comment_replies'),('public','comments'),
    ('public','connection_requests'),('public','conversations'),('public','feed_posts'),
    ('public','follows'),('public','game_challenges'),('public','housing_request_applications'),
    ('public','housing_requests'),('public','market_items'),('public','mentor_requests'),
    ('public','messages'),('public','notifications'),('public','poll_votes'),
    ('public','post_bookmarks'),('public','post_likes'),('public','post_views'),
    ('public','reading_mate_requests'),('public','roommate_applications'),('public','roommate_profiles'),
    ('public','skill_endorsements'),('public','stories'),('public','story_likes'),
    ('public','story_reactions'),('public','story_replies'),('public','story_views'),
    ('public','study_circle_members'),('public','study_circles')
  ) AS x(schemaname,tablename)
  LOOP
    IF NOT EXISTS (
      SELECT 1 FROM pg_publication_tables
      WHERE pubname='supabase_realtime' AND schemaname=r.schemaname AND tablename=r.tablename
    ) THEN
      EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE %I.%I',r.schemaname,r.tablename);
    END IF;
  END LOOP;
END
$realtime$;

-- Cron jobs. No tokens, URLs, passwords, or other secret values are embedded in these live commands.
DO $cron$
DECLARE n text;
BEGIN
  FOREACH n IN ARRAY ARRAY[
    'blink-refresh-leaderboards','blink-expire-statuses','publish-due-blink-posts',
    'blink-ranking-events-retention','blink-presence-lease-reconcile','blink-expire-admin-state',
    'blink-admin-notification-campaigns','blink-vip-expiry-maintenance','blink-vip-auto-renew'
  ] LOOP
    IF EXISTS (SELECT 1 FROM cron.job WHERE jobname=n) THEN
      PERFORM cron.unschedule(n);
    END IF;
  END LOOP;
END
$cron$;

SELECT cron.schedule('blink-refresh-leaderboards','0 * * * *','select public.refresh_leaderboards();');
SELECT cron.schedule('blink-expire-statuses','*/15 * * * *','select public.expire_statuses();');
SELECT cron.schedule('publish-due-blink-posts','* * * * *','select public.publish_due_scheduled_feed_posts();');
SELECT cron.schedule('blink-ranking-events-retention','23 3 * * *','select private_ranking.prune_old_events();');
SELECT cron.schedule('blink-presence-lease-reconcile','* * * * *','select public.reconcile_presence_lease();');
SELECT cron.schedule('blink-expire-admin-state','*/5 * * * *','select private.expire_blink_admin_state();');
SELECT cron.schedule('blink-admin-notification-campaigns','* * * * *','select private.dispatch_due_admin_campaigns();');
SELECT cron.schedule('blink-vip-expiry-maintenance','17 * * * *','select private.expire_blink_items_and_remind_vip();');
SELECT cron.schedule('blink-vip-auto-renew','27 * * * *','select private.process_blink_vip_auto_renewals();');
