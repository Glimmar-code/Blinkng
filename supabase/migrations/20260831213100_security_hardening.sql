-- Blinkng Supabase security hardening
-- Applied to project: jhwgifrlxwspoedxjaly
--
-- This migration mirrors the live security fix that removes API exposure from
-- SECURITY DEFINER helpers, keeps authenticated app RPCs available, and makes
-- the game leaderboard respect caller RLS policies.

begin;

create or replace view public.game_leaderboard
with (security_invoker = true)
as
select
    gp.user_id,
    gp.score,
    gp.coins,
    gp.streak,
    gp.best_streak,
    p.name,
    p.username,
    p.avatar_url,
    p.university,
    rank() over (order by gp.score desc) as world_rank
from public.game_profiles gp
join public.profiles p on p.id = gp.user_id
order by gp.score desc;

-- Remove default PUBLIC execution from RPCs and internal helpers.
revoke execute on function public.accept_connection_request(uuid) from public, anon;
revoke execute on function public.approve_circle_request(uuid) from public, anon;
revoke execute on function public.bookmark_post(uuid) from public, anon;
revoke execute on function public.cancel_connection_request(uuid) from public, anon;
revoke execute on function public.create_marketplace_order(uuid, integer) from public, anon;
revoke execute on function public.create_message_notification() from public, anon, authenticated;
revoke execute on function public.decline_connection_request(uuid) from public, anon;
revoke execute on function public.delete_post(uuid) from public, anon;
revoke execute on function public.follow_user(uuid) from public, anon;
revoke execute on function public.get_following_feed(integer, timestamptz) from public, anon;
revoke execute on function public.join_study_circle(uuid) from public, anon;
revoke execute on function public.leave_study_circle(uuid) from public, anon;
revoke execute on function public.like_comment(uuid) from public, anon;
revoke execute on function public.like_post(uuid) from public, anon;
revoke execute on function public.record_game_session(text, integer, integer) from public, anon;
revoke execute on function public.record_post_view(uuid) from public, anon;
revoke execute on function public.remove_circle_member(uuid, uuid) from public, anon;
revoke execute on function public.request_to_join_circle(uuid) from public, anon;
revoke execute on function public.send_connection_request(uuid) from public, anon;
revoke execute on function public.send_message(uuid, text, text) from public, anon;
revoke execute on function public.share_post(uuid, text) from public, anon;
revoke execute on function public.submit_verification_request(text, jsonb) from public, anon;
revoke execute on function public.unbookmark_post(uuid) from public, anon;
revoke execute on function public.unfollow_user(uuid) from public, anon;
revoke execute on function public.unlike_comment(uuid) from public, anon;
revoke execute on function public.unlike_post(uuid) from public, anon;
revoke execute on function public.vote_in_poll(uuid, uuid) from public, anon;

revoke execute on function public.sync_comment_like_count() from public, anon, authenticated;
revoke execute on function public.sync_post_comment_count() from public, anon, authenticated;
revoke execute on function public.sync_post_like_count() from public, anon, authenticated;
revoke execute on function public.sync_profile_follow_counts() from public, anon, authenticated;
revoke execute on function public.sync_story_like_count() from public, anon, authenticated;
revoke execute on function public.update_comment_like_count() from public, anon, authenticated;
revoke execute on function public.update_conversation_last_message() from public, anon, authenticated;
revoke execute on function public.update_post_comment_count() from public, anon, authenticated;
revoke execute on function public.update_post_like_count() from public, anon, authenticated;
revoke execute on function public.update_story_view_count() from public, anon, authenticated;

-- Rewards are privileged server-side operations only.
revoke execute on function public.award_game_reward(uuid, integer, text, text) from public, anon, authenticated;
revoke execute on function public.award_game_reward(uuid, text, integer, text) from public, anon, authenticated;
revoke execute on function public.run_rls_tests() from public, anon, authenticated;

-- Authenticated app RPCs.
grant execute on function public.accept_connection_request(uuid) to authenticated;
grant execute on function public.approve_circle_request(uuid) to authenticated;
grant execute on function public.bookmark_post(uuid) to authenticated;
grant execute on function public.cancel_connection_request(uuid) to authenticated;
grant execute on function public.create_marketplace_order(uuid, integer) to authenticated;
grant execute on function public.decline_connection_request(uuid) to authenticated;
grant execute on function public.delete_post(uuid) to authenticated;
grant execute on function public.follow_user(uuid) to authenticated;
grant execute on function public.get_following_feed(integer, timestamptz) to authenticated;
grant execute on function public.join_study_circle(uuid) to authenticated;
grant execute on function public.leave_study_circle(uuid) to authenticated;
grant execute on function public.like_comment(uuid) to authenticated;
grant execute on function public.like_post(uuid) to authenticated;
grant execute on function public.record_game_session(text, integer, integer) to authenticated;
grant execute on function public.record_post_view(uuid) to authenticated;
grant execute on function public.remove_circle_member(uuid, uuid) to authenticated;
grant execute on function public.request_to_join_circle(uuid) to authenticated;
grant execute on function public.send_connection_request(uuid) to authenticated;
grant execute on function public.send_message(uuid, text, text) to authenticated;
grant execute on function public.share_post(uuid, text) to authenticated;
grant execute on function public.submit_verification_request(text, jsonb) to authenticated;
grant execute on function public.unbookmark_post(uuid) to authenticated;
grant execute on function public.unfollow_user(uuid) to authenticated;
grant execute on function public.unlike_comment(uuid) to authenticated;
grant execute on function public.unlike_post(uuid) to authenticated;
grant execute on function public.vote_in_poll(uuid, uuid) to authenticated;

-- Service-side reward and test execution only.
grant execute on function public.award_game_reward(uuid, integer, text, text) to service_role;
grant execute on function public.award_game_reward(uuid, text, integer, text) to service_role;
grant execute on function public.run_rls_tests() to service_role;

commit;
