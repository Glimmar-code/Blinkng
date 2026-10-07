-- Optimize the exact RLS policies flagged by Supabase's auth_rls_initplan advisor.
-- Policy commands, roles and predicates remain unchanged; only auth helper evaluation is cached per statement.

alter policy "blink_coin_purchase_orders_own_read" on "public"."blink_coin_purchase_orders"
using ((user_id = (select auth.uid())));

alter policy "comment_replies_delete_own" on "public"."comment_replies"
using (((select auth.uid()) = author_id));

alter policy "comment_replies_insert_own" on "public"."comment_replies"
with check (((select auth.uid()) = author_id));

alter policy "comment_replies_update_own" on "public"."comment_replies"
using (((select auth.uid()) = author_id))
with check (((select auth.uid()) = author_id));

alter policy "conn_req_delete_sender" on "public"."connection_requests"
using (((select auth.uid()) = sender_id));

alter policy "conn_req_insert_sender" on "public"."connection_requests"
with check (((select auth.uid()) = sender_id));

alter policy "conn_req_select_involved" on "public"."connection_requests"
using ((((select auth.uid()) = sender_id) OR ((select auth.uid()) = receiver_id)));

alter policy "conn_req_update_involved" on "public"."connection_requests"
using ((((select auth.uid()) = sender_id) OR ((select auth.uid()) = receiver_id)))
with check ((((select auth.uid()) = sender_id) OR ((select auth.uid()) = receiver_id)));

alter policy "cp_insert_own" on "public"."conversation_participants"
with check (((select auth.uid()) = user_id));

alter policy "cp_select_own" on "public"."conversation_participants"
using (((select auth.uid()) = user_id));

alter policy "cp_update_own" on "public"."conversation_participants"
using (((select auth.uid()) = user_id))
with check (((select auth.uid()) = user_id));

alter policy "game_rewards_select_own" on "public"."game_rewards"
using (((select auth.uid()) = user_id));

alter policy "game_sessions_insert_own" on "public"."game_sessions"
with check (((select auth.uid()) = user_id));

alter policy "game_sessions_select_own" on "public"."game_sessions"
using (((select auth.uid()) = user_id));

alter policy "mp_inq_insert_own" on "public"."marketplace_inquiries"
with check (((select auth.uid()) = buyer_id));

alter policy "mp_inq_select" on "public"."marketplace_inquiries"
using ((((select auth.uid()) = buyer_id) OR ((select auth.uid()) = seller_id)));

alter policy "mp_inq_update" on "public"."marketplace_inquiries"
using ((((select auth.uid()) = buyer_id) OR ((select auth.uid()) = seller_id)))
with check ((((select auth.uid()) = buyer_id) OR ((select auth.uid()) = seller_id)));

alter policy "mp_profile_delete_own" on "public"."marketplace_profiles"
using (((select auth.uid()) = user_id));

alter policy "mp_profile_insert_own" on "public"."marketplace_profiles"
with check (((select auth.uid()) = user_id));

alter policy "mp_profile_update_own" on "public"."marketplace_profiles"
using (((select auth.uid()) = user_id))
with check (((select auth.uid()) = user_id));

alter policy "point_transactions_insert_own" on "public"."point_transactions"
with check (((select auth.uid()) = user_id));

alter policy "point_transactions_select_own" on "public"."point_transactions"
using (((select auth.uid()) = user_id));

alter policy "poll_options_delete_own" on "public"."poll_options"
using ((EXISTS ( SELECT 1
   FROM (polls p
     JOIN feed_posts fp ON ((fp.id = p.post_id)))
  WHERE ((p.id = poll_options.poll_id) AND (fp.user_id = (select auth.uid()))))));

alter policy "poll_votes_delete_own" on "public"."poll_votes"
using (((select auth.uid()) = user_id));

alter policy "poll_votes_insert_own" on "public"."poll_votes"
with check (((select auth.uid()) = user_id));

alter policy "polls_delete_own" on "public"."polls"
using ((EXISTS ( SELECT 1
   FROM feed_posts fp
  WHERE ((fp.id = polls.post_id) AND (fp.user_id = (select auth.uid()))))));

alter policy "polls_insert_own" on "public"."polls"
with check ((EXISTS ( SELECT 1
   FROM feed_posts fp
  WHERE ((fp.id = polls.post_id) AND (fp.user_id = (select auth.uid()))))));

alter policy "post_ads_delete_own" on "public"."post_ads"
using ((advertiser_id = (select auth.uid())));

alter policy "post_ads_insert_own" on "public"."post_ads"
with check ((advertiser_id = (select auth.uid())));

alter policy "post_ads_select_active" on "public"."post_ads"
using ((((status = 'active'::text) AND (starts_at <= now()) AND ((ends_at IS NULL) OR (ends_at > now()))) OR (advertiser_id = (select auth.uid()))));

alter policy "post_ads_update_own" on "public"."post_ads"
using ((advertiser_id = (select auth.uid())))
with check ((advertiser_id = (select auth.uid())));

alter policy "profile_skills_delete_own" on "public"."profile_skills"
using (((select auth.uid()) = user_id));

alter policy "profile_skills_insert_own" on "public"."profile_skills"
with check (((select auth.uid()) = user_id));

alter policy "profile_views_insert_own" on "public"."profile_views"
with check (((select auth.uid()) = viewer_id));

alter policy "profile_views_select_own_profile" on "public"."profile_views"
using ((((select auth.uid()) = profile_id) OR ((select auth.uid()) = viewer_id)));

alter policy "skill_endorse_delete_own" on "public"."skill_endorsements"
using (((select auth.uid()) = endorser_user_id));

alter policy "skill_endorse_insert_own" on "public"."skill_endorsements"
with check (((select auth.uid()) = endorser_user_id));

alter policy "skills_insert_own" on "public"."skills"
with check (((select auth.uid()) IS NOT NULL));

alter policy "delete_stories" on "public"."stories"
using (((select auth.uid()) = user_id));

alter policy "insert_stories" on "public"."stories"
with check (((select auth.uid()) = user_id));

alter policy "update_stories" on "public"."stories"
using (((select auth.uid()) = user_id))
with check (((select auth.uid()) = user_id));

alter policy "story_likes_delete_own" on "public"."story_likes"
using (((select auth.uid()) = user_id));

alter policy "story_likes_insert_own" on "public"."story_likes"
with check (((select auth.uid()) = user_id));

alter policy "story_likes_select" on "public"."story_likes"
using ((((select auth.uid()) = user_id) OR (EXISTS ( SELECT 1
   FROM stories s
  WHERE ((s.id = story_likes.story_id) AND (s.user_id = (select auth.uid())))))));

alter policy "story_reactions_delete_own" on "public"."story_reactions"
using (((select auth.uid()) = user_id));

alter policy "story_reactions_insert_own" on "public"."story_reactions"
with check (((select auth.uid()) = user_id));

alter policy "story_reactions_select" on "public"."story_reactions"
using ((((select auth.uid()) = user_id) OR (EXISTS ( SELECT 1
   FROM stories s
  WHERE ((s.id = story_reactions.story_id) AND (s.user_id = (select auth.uid())))))));

alter policy "story_replies_delete_own" on "public"."story_replies"
using (((select auth.uid()) = user_id));

alter policy "story_replies_insert_own" on "public"."story_replies"
with check (((select auth.uid()) = user_id));

alter policy "story_replies_select" on "public"."story_replies"
using ((((select auth.uid()) = user_id) OR (EXISTS ( SELECT 1
   FROM stories s
  WHERE ((s.id = story_replies.story_id) AND (s.user_id = (select auth.uid())))))));

alter policy "story_views_insert_own" on "public"."story_views"
with check (((select auth.uid()) = user_id));

alter policy "story_views_select" on "public"."story_views"
using ((((select auth.uid()) = user_id) OR (EXISTS ( SELECT 1
   FROM stories s
  WHERE ((s.id = story_views.story_id) AND (s.user_id = (select auth.uid())))))));

alter policy "token_transactions_select_own" on "public"."token_transactions"
using ((((select auth.uid()) = recipient_id) OR ((select auth.uid()) = sender_id)));

alter policy "verif_pay_insert_own" on "public"."verification_payments"
with check (((select auth.uid()) = user_id));

alter policy "verif_pay_select_own" on "public"."verification_payments"
using (((select auth.uid()) = user_id));

alter policy "verif_delete_own" on "public"."verification_requests"
using ((((select auth.uid()) = user_id) AND (status = 'pending'::text)));

alter policy "verif_insert_own" on "public"."verification_requests"
with check ((((select auth.uid()) = user_id) AND (status = 'pending'::text)));

alter policy "verif_select_own" on "public"."verification_requests"
using (((select auth.uid()) = user_id));

alter policy "verifications_select_own" on "public"."verifications"
using (((select auth.uid()) = user_id));

