-- Exact duplicate-index cleanup.
-- destructive-change-reviewed
-- rollback-plan: recreate each dropped index using the identical retained definition
-- documented below. No constraint-backed index is removed.
--
-- Keep higher/current-use equivalent:
-- activities: idx_activities_recipient_created
-- conversation_participants: conversation_participants_user_conversation_idx
-- feed_posts: idx_feed_posts_created_at_desc
-- game_sessions: idx_game_sessions_user_started
-- point_transactions: uq_point_transactions_user_action_reference
-- post_shares: idx_post_shares_post_id

drop index if exists public.activities_recipient_idx;
drop index if exists public.idx_conversation_participants_user;
drop index if exists public.feed_posts_created_idx;
drop index if exists public.game_sessions_user_idx;
drop index if exists public.point_transactions_once_per_reference_idx;
drop index if exists public.idx_shares_post;
