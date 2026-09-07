-- Exact live RLS policy definitions: public tables A-F.
-- Captured 2026-09-07. Recovery baseline; apply only in an isolated rebuild.

DROP POLICY IF EXISTS activities_select_own ON public.activities;
CREATE POLICY activities_select_own ON public.activities AS PERMISSIVE FOR SELECT TO authenticated
USING ((( SELECT auth.uid() AS uid) = recipient_id));

DROP POLICY IF EXISTS activities_update_own ON public.activities;
CREATE POLICY activities_update_own ON public.activities AS PERMISSIVE FOR UPDATE TO authenticated
USING ((( SELECT auth.uid() AS uid) = recipient_id))
WITH CHECK ((( SELECT auth.uid() AS uid) = recipient_id));

DROP POLICY IF EXISTS blink_boost_touches_owner_read ON public.blink_boost_touches;
CREATE POLICY blink_boost_touches_owner_read ON public.blink_boost_touches AS PERMISSIVE FOR SELECT TO authenticated
USING ((EXISTS ( SELECT 1
   FROM blink_boosts b
  WHERE ((b.id = blink_boost_touches.boost_id) AND (b.user_id = ( SELECT auth.uid() AS uid))))));

DROP POLICY IF EXISTS blink_boosts_own_read ON public.blink_boosts;
CREATE POLICY blink_boosts_own_read ON public.blink_boosts AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS blink_coin_gifts_select_involved ON public.blink_coin_gifts;
CREATE POLICY blink_coin_gifts_select_involved ON public.blink_coin_gifts AS PERMISSIVE FOR SELECT TO authenticated
USING (((( SELECT auth.uid() AS uid) = sender_id) OR (( SELECT auth.uid() AS uid) = receiver_id)));

DROP POLICY IF EXISTS blink_transactions_own_read ON public.blink_coin_transactions;
CREATE POLICY blink_transactions_own_read ON public.blink_coin_transactions AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS blink_digital_gifts_participant_read ON public.blink_digital_gifts;
CREATE POLICY blink_digital_gifts_participant_read ON public.blink_digital_gifts AS PERMISSIVE FOR SELECT TO authenticated
USING (((( SELECT auth.uid() AS uid) = sender_id) OR (( SELECT auth.uid() AS uid) = recipient_id)));

DROP POLICY IF EXISTS blink_equipped_own_read ON public.blink_equipped_items;
CREATE POLICY blink_equipped_own_read ON public.blink_equipped_items AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS blink_inventory_own_read ON public.blink_inventory;
CREATE POLICY blink_inventory_own_read ON public.blink_inventory AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS blink_activations_own_read ON public.blink_item_activations;
CREATE POLICY blink_activations_own_read ON public.blink_item_activations AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS blink_store_catalog_read ON public.blink_store_catalog;
CREATE POLICY blink_store_catalog_read ON public.blink_store_catalog AS PERMISSIVE FOR SELECT TO authenticated
USING ((is_active = true));

DROP POLICY IF EXISTS blink_vip_benefits_own_read ON public.blink_vip_benefit_balances;
CREATE POLICY blink_vip_benefits_own_read ON public.blink_vip_benefit_balances AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS blink_vip_claims_own_read ON public.blink_vip_claims;
CREATE POLICY blink_vip_claims_own_read ON public.blink_vip_claims AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS blink_vip_passes_own_read ON public.blink_vip_passes;
CREATE POLICY blink_vip_passes_own_read ON public.blink_vip_passes AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS blocks_delete_own ON public.blocks;
CREATE POLICY blocks_delete_own ON public.blocks AS PERMISSIVE FOR DELETE TO authenticated
USING ((blocker_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS blocks_insert_own ON public.blocks;
CREATE POLICY blocks_insert_own ON public.blocks AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((blocker_id = ( SELECT auth.uid() AS uid)) AND (blocked_id <> ( SELECT auth.uid() AS uid))));

DROP POLICY IF EXISTS blocks_select_own ON public.blocks;
CREATE POLICY blocks_select_own ON public.blocks AS PERMISSIVE FOR SELECT TO authenticated
USING ((blocker_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS "participants can read call signals" ON public.call_signals;
CREATE POLICY "participants can read call signals" ON public.call_signals AS PERMISSIVE FOR SELECT TO authenticated
USING ((EXISTS ( SELECT 1
   FROM calls c
  WHERE ((c.id = call_signals.call_id) AND ((( SELECT auth.uid() AS uid) = c.caller_id) OR (( SELECT auth.uid() AS uid) = c.callee_id))))));

DROP POLICY IF EXISTS "participants can send own call signals" ON public.call_signals;
CREATE POLICY "participants can send own call signals" ON public.call_signals AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((sender_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM calls c
  WHERE ((c.id = call_signals.call_id) AND (c.status = ANY (ARRAY['ringing'::text, 'connecting'::text, 'connected'::text])) AND ((( SELECT auth.uid() AS uid) = c.caller_id) OR (( SELECT auth.uid() AS uid) = c.callee_id)))))));

DROP POLICY IF EXISTS "participants can read calls" ON public.calls;
CREATE POLICY "participants can read calls" ON public.calls AS PERMISSIVE FOR SELECT TO authenticated
USING (((( SELECT auth.uid() AS uid) = caller_id) OR (( SELECT auth.uid() AS uid) = callee_id)));

DROP POLICY IF EXISTS comment_likes_delete_own ON public.comment_likes;
CREATE POLICY comment_likes_delete_own ON public.comment_likes AS PERMISSIVE FOR DELETE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS comment_likes_insert_own ON public.comment_likes;
CREATE POLICY comment_likes_insert_own ON public.comment_likes AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS comment_likes_select_all ON public.comment_likes;
CREATE POLICY comment_likes_select_all ON public.comment_likes AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS comment_mentions_no_client_access ON public.comment_mentions;
CREATE POLICY comment_mentions_no_client_access ON public.comment_mentions AS PERMISSIVE FOR ALL TO anon, authenticated
USING (false)
WITH CHECK (false);

DROP POLICY IF EXISTS comment_replies_delete_own ON public.comment_replies;
CREATE POLICY comment_replies_delete_own ON public.comment_replies AS PERMISSIVE FOR DELETE TO authenticated
USING ((auth.uid() = author_id));

DROP POLICY IF EXISTS comment_replies_insert_own ON public.comment_replies;
CREATE POLICY comment_replies_insert_own ON public.comment_replies AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((auth.uid() = author_id));

DROP POLICY IF EXISTS comment_replies_select_all ON public.comment_replies;
CREATE POLICY comment_replies_select_all ON public.comment_replies AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS comment_replies_update_own ON public.comment_replies;
CREATE POLICY comment_replies_update_own ON public.comment_replies AS PERMISSIVE FOR UPDATE TO authenticated
USING ((auth.uid() = author_id))
WITH CHECK ((auth.uid() = author_id));

DROP POLICY IF EXISTS comments_delete_own ON public.comments;
CREATE POLICY comments_delete_own ON public.comments AS PERMISSIVE FOR DELETE TO authenticated
USING ((author_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS comments_insert_own ON public.comments;
CREATE POLICY comments_insert_own ON public.comments AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((author_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS comments_select_all ON public.comments;
CREATE POLICY comments_select_all ON public.comments AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS comments_update_own ON public.comments;
CREATE POLICY comments_update_own ON public.comments AS PERMISSIVE FOR UPDATE TO authenticated
USING ((author_id = ( SELECT auth.uid() AS uid)))
WITH CHECK ((author_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS connect_applications_delete_own ON public.connect_applications;
CREATE POLICY connect_applications_delete_own ON public.connect_applications AS PERMISSIVE FOR DELETE TO authenticated
USING ((applicant_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS connect_applications_insert_own ON public.connect_applications;
CREATE POLICY connect_applications_insert_own ON public.connect_applications AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((applicant_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM connect_listings l
  WHERE ((l.id = connect_applications.listing_id) AND (l.user_id <> ( SELECT auth.uid() AS uid)) AND l.is_active)))));

DROP POLICY IF EXISTS connect_applications_select_involved ON public.connect_applications;
CREATE POLICY connect_applications_select_involved ON public.connect_applications AS PERMISSIVE FOR SELECT TO authenticated
USING (((applicant_id = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
   FROM connect_listings l
  WHERE ((l.id = connect_applications.listing_id) AND (l.user_id = ( SELECT auth.uid() AS uid)))))));

DROP POLICY IF EXISTS connect_applications_update_involved ON public.connect_applications;
CREATE POLICY connect_applications_update_involved ON public.connect_applications AS PERMISSIVE FOR UPDATE TO authenticated
USING (((applicant_id = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
   FROM connect_listings l
  WHERE ((l.id = connect_applications.listing_id) AND (l.user_id = ( SELECT auth.uid() AS uid)))))))
WITH CHECK (((applicant_id = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
   FROM connect_listings l
  WHERE ((l.id = connect_applications.listing_id) AND (l.user_id = ( SELECT auth.uid() AS uid)))))));

DROP POLICY IF EXISTS connect_category_catalog_read ON public.connect_category_catalog;
CREATE POLICY connect_category_catalog_read ON public.connect_category_catalog AS PERMISSIVE FOR SELECT TO authenticated
USING ((is_active = true));

DROP POLICY IF EXISTS connect_listings_delete_own ON public.connect_listings;
CREATE POLICY connect_listings_delete_own ON public.connect_listings AS PERMISSIVE FOR DELETE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS connect_listings_insert_own ON public.connect_listings;
CREATE POLICY connect_listings_insert_own ON public.connect_listings AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS connect_listings_select ON public.connect_listings;
CREATE POLICY connect_listings_select ON public.connect_listings AS PERMISSIVE FOR SELECT TO authenticated
USING ((is_active OR (user_id = ( SELECT auth.uid() AS uid))));

DROP POLICY IF EXISTS connect_listings_update_own ON public.connect_listings;
CREATE POLICY connect_listings_update_own ON public.connect_listings AS PERMISSIVE FOR UPDATE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)))
WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS conn_req_delete_sender ON public.connection_requests;
CREATE POLICY conn_req_delete_sender ON public.connection_requests AS PERMISSIVE FOR DELETE TO authenticated
USING ((auth.uid() = sender_id));

DROP POLICY IF EXISTS conn_req_insert_sender ON public.connection_requests;
CREATE POLICY conn_req_insert_sender ON public.connection_requests AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((auth.uid() = sender_id));

DROP POLICY IF EXISTS conn_req_select_involved ON public.connection_requests;
CREATE POLICY conn_req_select_involved ON public.connection_requests AS PERMISSIVE FOR SELECT TO authenticated
USING (((auth.uid() = sender_id) OR (auth.uid() = receiver_id)));

DROP POLICY IF EXISTS conn_req_update_involved ON public.connection_requests;
CREATE POLICY conn_req_update_involved ON public.connection_requests AS PERMISSIVE FOR UPDATE TO authenticated
USING (((auth.uid() = sender_id) OR (auth.uid() = receiver_id)))
WITH CHECK (((auth.uid() = sender_id) OR (auth.uid() = receiver_id)));

DROP POLICY IF EXISTS cp_insert_own ON public.conversation_participants;
CREATE POLICY cp_insert_own ON public.conversation_participants AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS cp_select_own ON public.conversation_participants;
CREATE POLICY cp_select_own ON public.conversation_participants AS PERMISSIVE FOR SELECT TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS cp_update_own ON public.conversation_participants;
CREATE POLICY cp_update_own ON public.conversation_participants AS PERMISSIVE FOR UPDATE TO authenticated
USING ((auth.uid() = user_id))
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS conversation_reports_insert_self ON public.conversation_reports;
CREATE POLICY conversation_reports_insert_self ON public.conversation_reports AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((reporter_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM conversation_participants cp
  WHERE ((cp.conversation_id = conversation_reports.conversation_id) AND (cp.user_id = ( SELECT auth.uid() AS uid)))))));

DROP POLICY IF EXISTS conversation_reports_select_self ON public.conversation_reports;
CREATE POLICY conversation_reports_select_self ON public.conversation_reports AS PERMISSIVE FOR SELECT TO authenticated
USING ((reporter_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS conversation_settings_delete_own ON public.conversation_settings;
CREATE POLICY conversation_settings_delete_own ON public.conversation_settings AS PERMISSIVE FOR DELETE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS conversation_settings_insert_own ON public.conversation_settings;
CREATE POLICY conversation_settings_insert_own ON public.conversation_settings AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((user_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM conversation_participants cp
  WHERE ((cp.conversation_id = conversation_settings.conversation_id) AND (cp.user_id = ( SELECT auth.uid() AS uid)))))));

DROP POLICY IF EXISTS conversation_settings_select_own ON public.conversation_settings;
CREATE POLICY conversation_settings_select_own ON public.conversation_settings AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS conversation_settings_update_own ON public.conversation_settings;
CREATE POLICY conversation_settings_update_own ON public.conversation_settings AS PERMISSIVE FOR UPDATE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)))
WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS conversation_user_state_delete_self ON public.conversation_user_state;
CREATE POLICY conversation_user_state_delete_self ON public.conversation_user_state AS PERMISSIVE FOR DELETE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS conversation_user_state_insert_self ON public.conversation_user_state;
CREATE POLICY conversation_user_state_insert_self ON public.conversation_user_state AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((user_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM conversation_participants cp
  WHERE ((cp.conversation_id = conversation_user_state.conversation_id) AND (cp.user_id = ( SELECT auth.uid() AS uid)))))));

DROP POLICY IF EXISTS conversation_user_state_select_self ON public.conversation_user_state;
CREATE POLICY conversation_user_state_select_self ON public.conversation_user_state AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS conversation_user_state_update_self ON public.conversation_user_state;
CREATE POLICY conversation_user_state_update_self ON public.conversation_user_state AS PERMISSIVE FOR UPDATE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)))
WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS conv_insert_own ON public.conversations;
CREATE POLICY conv_insert_own ON public.conversations AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((created_by = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS conv_select_member ON public.conversations;
CREATE POLICY conv_select_member ON public.conversations AS PERMISSIVE FOR SELECT TO authenticated
USING ((EXISTS ( SELECT 1
   FROM conversation_participants cp
  WHERE ((cp.conversation_id = conversations.id) AND (cp.user_id = ( SELECT auth.uid() AS uid))))));

DROP POLICY IF EXISTS conv_update_member ON public.conversations;
CREATE POLICY conv_update_member ON public.conversations AS PERMISSIVE FOR UPDATE TO authenticated
USING ((EXISTS ( SELECT 1
   FROM conversation_participants cp
  WHERE ((cp.conversation_id = conversations.id) AND (cp.user_id = ( SELECT auth.uid() AS uid))))))
WITH CHECK ((EXISTS ( SELECT 1
   FROM conversation_participants cp
  WHERE ((cp.conversation_id = conversations.id) AND (cp.user_id = ( SELECT auth.uid() AS uid))))));

DROP POLICY IF EXISTS fcm_tokens_delete_own ON public.fcm_tokens;
CREATE POLICY fcm_tokens_delete_own ON public.fcm_tokens AS PERMISSIVE FOR ALL TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS fcm_tokens_insert_own ON public.fcm_tokens;
CREATE POLICY fcm_tokens_insert_own ON public.fcm_tokens AS PERMISSIVE FOR ALL TO authenticated
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS fcm_tokens_select_own ON public.fcm_tokens;
CREATE POLICY fcm_tokens_select_own ON public.fcm_tokens AS PERMISSIVE FOR ALL TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS fcm_tokens_update_own ON public.fcm_tokens;
CREATE POLICY fcm_tokens_update_own ON public.fcm_tokens AS PERMISSIVE FOR ALL TO authenticated
USING ((auth.uid() = user_id))
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS feed_posts_delete_own ON public.feed_posts;
CREATE POLICY feed_posts_delete_own ON public.feed_posts AS PERMISSIVE FOR DELETE TO authenticated
USING ((( SELECT auth.uid() AS uid) = user_id));

DROP POLICY IF EXISTS feed_posts_insert_own ON public.feed_posts;
CREATE POLICY feed_posts_insert_own ON public.feed_posts AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((( SELECT auth.uid() AS uid) = user_id));

DROP POLICY IF EXISTS feed_posts_select_active ON public.feed_posts;
CREATE POLICY feed_posts_select_active ON public.feed_posts AS PERMISSIVE FOR SELECT TO authenticated
USING (((is_active = true) OR (( SELECT auth.uid() AS uid) = user_id)));

DROP POLICY IF EXISTS feed_posts_update_own ON public.feed_posts;
CREATE POLICY feed_posts_update_own ON public.feed_posts AS PERMISSIVE FOR UPDATE TO authenticated
USING ((( SELECT auth.uid() AS uid) = user_id))
WITH CHECK ((( SELECT auth.uid() AS uid) = user_id));

DROP POLICY IF EXISTS feed_preferences_delete_own ON public.feed_preferences;
CREATE POLICY feed_preferences_delete_own ON public.feed_preferences AS PERMISSIVE FOR DELETE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS feed_preferences_insert_own ON public.feed_preferences;
CREATE POLICY feed_preferences_insert_own ON public.feed_preferences AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS feed_preferences_select_own ON public.feed_preferences;
CREATE POLICY feed_preferences_select_own ON public.feed_preferences AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS follows_delete_own ON public.follows;
CREATE POLICY follows_delete_own ON public.follows AS PERMISSIVE FOR DELETE TO authenticated
USING ((follower_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS follows_insert_own ON public.follows;
CREATE POLICY follows_insert_own ON public.follows AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((follower_id = ( SELECT auth.uid() AS uid)) AND (following_id <> ( SELECT auth.uid() AS uid)) AND (NOT (EXISTS ( SELECT 1
   FROM blocks b
  WHERE (((b.blocker_id = ( SELECT auth.uid() AS uid)) AND (b.blocked_id = follows.following_id)) OR ((b.blocker_id = follows.following_id) AND (b.blocked_id = ( SELECT auth.uid() AS uid)))))))));

DROP POLICY IF EXISTS follows_select_all ON public.follows;
CREATE POLICY follows_select_all ON public.follows AS PERMISSIVE FOR SELECT TO authenticated
USING (true);
