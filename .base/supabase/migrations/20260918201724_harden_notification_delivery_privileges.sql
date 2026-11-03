-- Production hardening follow-up for notification delivery.
-- Keep Data API access minimal while preserving RLS ownership checks.

revoke all on table public.notification_preferences from anon, authenticated;
grant select, insert, update on table public.notification_preferences to authenticated;
grant all on table public.notification_preferences to service_role;

-- Trigger functions should not be directly callable through the API.
revoke all on function public.dispatch_notification_push_after_insert() from public, anon, authenticated;
revoke all on function public.notify_post_repost_created() from public, anon, authenticated;
revoke all on function public.notify_comment_like_created() from public, anon, authenticated;
revoke all on function public.notify_story_like_created() from public, anon, authenticated;
