-- Channels: one-minute due-update and push-retry reconciliation.
-- No credentials or JWTs in this job definition: the private function
-- reads its one-time random token directly from restricted PostgreSQL storage.
-- pg_cron & pg_net are enabled on the BLINK Supabase project.
select cron.schedule(
  'blink-channel-notification-delivery',
  '* * * * *',
  'select private.blink_channel_dispatch_due();'
);
