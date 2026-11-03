-- BLINK Channels: explicit restricted Data API grants, following the
-- first versioned migration. Does not mutate user rows, media, or rewards.
revoke all on table public.blink_channel_updates from anon, authenticated;
grant all on table public.blink_channel_updates to service_role;
revoke all on table public.blink_channel_reactions from anon, authenticated;
grant all on table public.blink_channel_reactions to service_role;
revoke all on table public.blink_channel_poll_votes from anon, authenticated;
grant all on table public.blink_channel_poll_votes to service_role;
revoke all on table public.blink_channel_reports from anon, authenticated;
grant all on table public.blink_channel_reports to service_role;
revoke all on table public.blink_channel_views from anon, authenticated;
grant all on table public.blink_channel_views to service_role;
revoke all on table public.blink_channel_notifications from anon, authenticated;
grant select, insert, update on table public.blink_channel_notifications to authenticated;
grant all on table public.blink_channel_notifications to service_role;
