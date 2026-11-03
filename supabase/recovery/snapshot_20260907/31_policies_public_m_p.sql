-- Exact live RLS policy definitions: public tables M-P.
-- Captured 2026-09-07. Recovery baseline; apply only in an isolated rebuild.

DROP POLICY IF EXISTS market_items_delete_own ON public.market_items;
CREATE POLICY market_items_delete_own ON public.market_items AS PERMISSIVE FOR DELETE TO authenticated
USING ((seller_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS market_items_insert_own ON public.market_items;
CREATE POLICY market_items_insert_own ON public.market_items AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((seller_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS market_items_select_visible ON public.market_items;
CREATE POLICY market_items_select_visible ON public.market_items AS PERMISSIVE FOR SELECT TO authenticated
USING (((seller_id = ( SELECT auth.uid() AS uid)) OR ((COALESCE(is_sold, false) = false) AND (COALESCE(status, 'active'::text) = 'active'::text))));

DROP POLICY IF EXISTS market_items_update_own ON public.market_items;
CREATE POLICY market_items_update_own ON public.market_items AS PERMISSIVE FOR UPDATE TO authenticated
USING ((seller_id = ( SELECT auth.uid() AS uid)))
WITH CHECK ((seller_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS mp_inq_insert_own ON public.marketplace_inquiries;
CREATE POLICY mp_inq_insert_own ON public.marketplace_inquiries AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((auth.uid() = buyer_id));

DROP POLICY IF EXISTS mp_inq_select ON public.marketplace_inquiries;
CREATE POLICY mp_inq_select ON public.marketplace_inquiries AS PERMISSIVE FOR SELECT TO authenticated
USING (((auth.uid() = buyer_id) OR (auth.uid() = seller_id)));

DROP POLICY IF EXISTS mp_inq_update ON public.marketplace_inquiries;
CREATE POLICY mp_inq_update ON public.marketplace_inquiries AS PERMISSIVE FOR UPDATE TO authenticated
USING (((auth.uid() = buyer_id) OR (auth.uid() = seller_id)))
WITH CHECK (((auth.uid() = buyer_id) OR (auth.uid() = seller_id)));

DROP POLICY IF EXISTS mp_order_delete_own ON public.marketplace_orders;
CREATE POLICY mp_order_delete_own ON public.marketplace_orders AS PERMISSIVE FOR DELETE TO authenticated
USING (((buyer_id = ( SELECT auth.uid() AS uid)) AND (status = 'pending'::text)));

DROP POLICY IF EXISTS mp_order_select ON public.marketplace_orders;
CREATE POLICY mp_order_select ON public.marketplace_orders AS PERMISSIVE FOR SELECT TO authenticated
USING (((buyer_id = ( SELECT auth.uid() AS uid)) OR (seller_id = ( SELECT auth.uid() AS uid))));

DROP POLICY IF EXISTS mp_profile_delete_own ON public.marketplace_profiles;
CREATE POLICY mp_profile_delete_own ON public.marketplace_profiles AS PERMISSIVE FOR DELETE TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS mp_profile_insert_own ON public.marketplace_profiles;
CREATE POLICY mp_profile_insert_own ON public.marketplace_profiles AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS mp_profile_select_all ON public.marketplace_profiles;
CREATE POLICY mp_profile_select_all ON public.marketplace_profiles AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS mp_profile_update_own ON public.marketplace_profiles;
CREATE POLICY mp_profile_update_own ON public.marketplace_profiles AS PERMISSIVE FOR UPDATE TO authenticated
USING ((auth.uid() = user_id))
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS marketplace_reviews_delete_own ON public.marketplace_reviews;
CREATE POLICY marketplace_reviews_delete_own ON public.marketplace_reviews AS PERMISSIVE FOR DELETE TO authenticated
USING ((reviewer_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS marketplace_reviews_insert_completed_order ON public.marketplace_reviews;
CREATE POLICY marketplace_reviews_insert_completed_order ON public.marketplace_reviews AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((reviewer_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM marketplace_orders o
  WHERE ((o.id = marketplace_reviews.order_id) AND (o.status = 'completed'::text) AND (((o.buyer_id = ( SELECT auth.uid() AS uid)) AND (marketplace_reviews.reviewee_id = o.seller_id)) OR ((o.seller_id = ( SELECT auth.uid() AS uid)) AND (marketplace_reviews.reviewee_id = o.buyer_id))))))));

DROP POLICY IF EXISTS marketplace_reviews_read ON public.marketplace_reviews;
CREATE POLICY marketplace_reviews_read ON public.marketplace_reviews AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS marketplace_wishlist_delete_own ON public.marketplace_wishlist;
CREATE POLICY marketplace_wishlist_delete_own ON public.marketplace_wishlist AS PERMISSIVE FOR DELETE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS marketplace_wishlist_insert_own ON public.marketplace_wishlist;
CREATE POLICY marketplace_wishlist_insert_own ON public.marketplace_wishlist AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS marketplace_wishlist_select_own ON public.marketplace_wishlist;
CREATE POLICY marketplace_wishlist_select_own ON public.marketplace_wishlist AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS mentor_profiles_delete_own ON public.mentor_profiles;
CREATE POLICY mentor_profiles_delete_own ON public.mentor_profiles AS PERMISSIVE FOR DELETE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS mentor_profiles_insert_own ON public.mentor_profiles;
CREATE POLICY mentor_profiles_insert_own ON public.mentor_profiles AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS mentor_profiles_read ON public.mentor_profiles;
CREATE POLICY mentor_profiles_read ON public.mentor_profiles AS PERMISSIVE FOR SELECT TO authenticated
USING ((is_active OR (user_id = ( SELECT auth.uid() AS uid))));

DROP POLICY IF EXISTS mentor_profiles_update_own ON public.mentor_profiles;
CREATE POLICY mentor_profiles_update_own ON public.mentor_profiles AS PERMISSIVE FOR UPDATE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)))
WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS mentor_requests_insert_own ON public.mentor_requests;
CREATE POLICY mentor_requests_insert_own ON public.mentor_requests AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((requester_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM mentor_profiles mp
  WHERE ((mp.id = mentor_requests.mentor_profile_id) AND (mp.user_id <> ( SELECT auth.uid() AS uid)) AND mp.is_active)))));

DROP POLICY IF EXISTS mentor_requests_read_participants ON public.mentor_requests;
CREATE POLICY mentor_requests_read_participants ON public.mentor_requests AS PERMISSIVE FOR SELECT TO authenticated
USING (((requester_id = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
   FROM mentor_profiles mp
  WHERE ((mp.id = mentor_requests.mentor_profile_id) AND (mp.user_id = ( SELECT auth.uid() AS uid)))))));

DROP POLICY IF EXISTS mentor_requests_update_participants ON public.mentor_requests;
CREATE POLICY mentor_requests_update_participants ON public.mentor_requests AS PERMISSIVE FOR UPDATE TO authenticated
USING (((requester_id = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
   FROM mentor_profiles mp
  WHERE ((mp.id = mentor_requests.mentor_profile_id) AND (mp.user_id = ( SELECT auth.uid() AS uid)))))))
WITH CHECK (((requester_id = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
   FROM mentor_profiles mp
  WHERE ((mp.id = mentor_requests.mentor_profile_id) AND (mp.user_id = ( SELECT auth.uid() AS uid)))))));

DROP POLICY IF EXISTS message_pins_delete_participant ON public.message_pins;
CREATE POLICY message_pins_delete_participant ON public.message_pins AS PERMISSIVE FOR DELETE TO authenticated
USING ((EXISTS ( SELECT 1
   FROM conversation_participants cp
  WHERE ((cp.conversation_id = message_pins.conversation_id) AND (cp.user_id = ( SELECT auth.uid() AS uid))))));

DROP POLICY IF EXISTS message_pins_insert_participant ON public.message_pins;
CREATE POLICY message_pins_insert_participant ON public.message_pins AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((pinned_by = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM conversation_participants cp
  WHERE ((cp.conversation_id = message_pins.conversation_id) AND (cp.user_id = ( SELECT auth.uid() AS uid))))) AND (EXISTS ( SELECT 1
   FROM messages m
  WHERE ((m.id = message_pins.message_id) AND (m.conversation_id = message_pins.conversation_id))))));

DROP POLICY IF EXISTS message_pins_select_participant ON public.message_pins;
CREATE POLICY message_pins_select_participant ON public.message_pins AS PERMISSIVE FOR SELECT TO authenticated
USING ((EXISTS ( SELECT 1
   FROM conversation_participants cp
  WHERE ((cp.conversation_id = message_pins.conversation_id) AND (cp.user_id = ( SELECT auth.uid() AS uid))))));

DROP POLICY IF EXISTS deny_client_message_push_dispatch_access ON public.message_push_dispatches;
CREATE POLICY deny_client_message_push_dispatch_access ON public.message_push_dispatches AS PERMISSIVE FOR ALL TO anon, authenticated
USING (false)
WITH CHECK (false);

DROP POLICY IF EXISTS message_reactions_delete_self ON public.message_reactions;
CREATE POLICY message_reactions_delete_self ON public.message_reactions AS PERMISSIVE FOR DELETE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS message_reactions_insert_self ON public.message_reactions;
CREATE POLICY message_reactions_insert_self ON public.message_reactions AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((user_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM (messages m
     JOIN conversation_participants cp ON ((cp.conversation_id = m.conversation_id)))
  WHERE ((m.id = message_reactions.message_id) AND (cp.user_id = ( SELECT auth.uid() AS uid)))))));

DROP POLICY IF EXISTS message_reactions_select_participant ON public.message_reactions;
CREATE POLICY message_reactions_select_participant ON public.message_reactions AS PERMISSIVE FOR SELECT TO authenticated
USING ((EXISTS ( SELECT 1
   FROM (messages m
     JOIN conversation_participants cp ON ((cp.conversation_id = m.conversation_id)))
  WHERE ((m.id = message_reactions.message_id) AND (cp.user_id = ( SELECT auth.uid() AS uid))))));

DROP POLICY IF EXISTS message_reports_insert_self ON public.message_reports;
CREATE POLICY message_reports_insert_self ON public.message_reports AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((reporter_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM (messages m
     JOIN conversation_participants cp ON ((cp.conversation_id = m.conversation_id)))
  WHERE ((m.id = message_reports.message_id) AND (cp.user_id = ( SELECT auth.uid() AS uid)))))));

DROP POLICY IF EXISTS message_reports_select_self ON public.message_reports;
CREATE POLICY message_reports_select_self ON public.message_reports AS PERMISSIVE FOR SELECT TO authenticated
USING ((reporter_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS message_user_state_delete_self ON public.message_user_state;
CREATE POLICY message_user_state_delete_self ON public.message_user_state AS PERMISSIVE FOR DELETE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS message_user_state_insert_self ON public.message_user_state;
CREATE POLICY message_user_state_insert_self ON public.message_user_state AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS message_user_state_select_self ON public.message_user_state;
CREATE POLICY message_user_state_select_self ON public.message_user_state AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS message_user_state_update_self ON public.message_user_state;
CREATE POLICY message_user_state_update_self ON public.message_user_state AS PERMISSIVE FOR UPDATE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)))
WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS messages_insert_member ON public.messages;
CREATE POLICY messages_insert_member ON public.messages AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((sender_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM conversation_participants cp
  WHERE ((cp.conversation_id = messages.conversation_id) AND (cp.user_id = ( SELECT auth.uid() AS uid)))))));

DROP POLICY IF EXISTS messages_select_member ON public.messages;
CREATE POLICY messages_select_member ON public.messages AS PERMISSIVE FOR SELECT TO authenticated
USING ((EXISTS ( SELECT 1
   FROM conversation_participants cp
  WHERE ((cp.conversation_id = messages.conversation_id) AND (cp.user_id = ( SELECT auth.uid() AS uid))))));

DROP POLICY IF EXISTS messages_update_own ON public.messages;
CREATE POLICY messages_update_own ON public.messages AS PERMISSIVE FOR UPDATE TO authenticated
USING ((sender_id = ( SELECT auth.uid() AS uid)))
WITH CHECK ((sender_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS messages_compat_delete_own ON public.messages_compat;
CREATE POLICY messages_compat_delete_own ON public.messages_compat AS PERMISSIVE FOR ALL TO authenticated
USING ((EXISTS ( SELECT 1
   FROM profiles p
  WHERE ((p.id = auth.uid()) AND (p.username = messages_compat.sender_username)))));

DROP POLICY IF EXISTS messages_compat_insert_own ON public.messages_compat;
CREATE POLICY messages_compat_insert_own ON public.messages_compat AS PERMISSIVE FOR ALL TO authenticated
WITH CHECK ((EXISTS ( SELECT 1
   FROM profiles p
  WHERE ((p.id = auth.uid()) AND (p.username = messages_compat.sender_username)))));

DROP POLICY IF EXISTS messages_compat_select_own ON public.messages_compat;
CREATE POLICY messages_compat_select_own ON public.messages_compat AS PERMISSIVE FOR ALL TO authenticated
USING (true);

DROP POLICY IF EXISTS muted_delete_own ON public.muted_users;
CREATE POLICY muted_delete_own ON public.muted_users AS PERMISSIVE FOR DELETE TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS muted_insert_own ON public.muted_users;
CREATE POLICY muted_insert_own ON public.muted_users AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (((user_id = ( SELECT auth.uid() AS uid)) AND (muted_id <> ( SELECT auth.uid() AS uid))));

DROP POLICY IF EXISTS muted_select_own ON public.muted_users;
CREATE POLICY muted_select_own ON public.muted_users AS PERMISSIVE FOR SELECT TO authenticated
USING ((user_id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS notifications_delete_own ON public.notifications;
CREATE POLICY notifications_delete_own ON public.notifications AS PERMISSIVE FOR ALL TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS notifications_select_own ON public.notifications;
CREATE POLICY notifications_select_own ON public.notifications AS PERMISSIVE FOR ALL TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS notifications_update_own ON public.notifications;
CREATE POLICY notifications_update_own ON public.notifications AS PERMISSIVE FOR ALL TO authenticated
USING ((auth.uid() = user_id))
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS point_transactions_insert_own ON public.point_transactions;
CREATE POLICY point_transactions_insert_own ON public.point_transactions AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS point_transactions_select_own ON public.point_transactions;
CREATE POLICY point_transactions_select_own ON public.point_transactions AS PERMISSIVE FOR SELECT TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS poll_options_delete_own ON public.poll_options;
CREATE POLICY poll_options_delete_own ON public.poll_options AS PERMISSIVE FOR DELETE TO authenticated
USING ((EXISTS ( SELECT 1
   FROM (polls p
     JOIN feed_posts fp ON ((fp.id = p.post_id)))
  WHERE ((p.id = poll_options.poll_id) AND (fp.user_id = auth.uid())))));

DROP POLICY IF EXISTS poll_options_insert_own ON public.poll_options;
CREATE POLICY poll_options_insert_own ON public.poll_options AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((EXISTS ( SELECT 1
   FROM (polls p
     JOIN feed_posts fp ON ((fp.id = p.post_id)))
  WHERE ((p.id = poll_options.poll_id) AND (fp.user_id = ( SELECT auth.uid() AS uid))))));

DROP POLICY IF EXISTS poll_options_select_all ON public.poll_options;
CREATE POLICY poll_options_select_all ON public.poll_options AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS poll_votes_delete_own ON public.poll_votes;
CREATE POLICY poll_votes_delete_own ON public.poll_votes AS PERMISSIVE FOR DELETE TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS poll_votes_insert_own ON public.poll_votes;
CREATE POLICY poll_votes_insert_own ON public.poll_votes AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS poll_votes_select_all ON public.poll_votes;
CREATE POLICY poll_votes_select_all ON public.poll_votes AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS polls_delete_own ON public.polls;
CREATE POLICY polls_delete_own ON public.polls AS PERMISSIVE FOR DELETE TO authenticated
USING ((EXISTS ( SELECT 1
   FROM feed_posts fp
  WHERE ((fp.id = polls.post_id) AND (fp.user_id = auth.uid())))));

DROP POLICY IF EXISTS polls_insert_own ON public.polls;
CREATE POLICY polls_insert_own ON public.polls AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((EXISTS ( SELECT 1
   FROM feed_posts fp
  WHERE ((fp.id = polls.post_id) AND (fp.user_id = auth.uid())))));

DROP POLICY IF EXISTS polls_select_all ON public.polls;
CREATE POLICY polls_select_all ON public.polls AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS post_ads_delete_own ON public.post_ads;
CREATE POLICY post_ads_delete_own ON public.post_ads AS PERMISSIVE FOR DELETE TO authenticated
USING ((advertiser_id = auth.uid()));

DROP POLICY IF EXISTS post_ads_insert_own ON public.post_ads;
CREATE POLICY post_ads_insert_own ON public.post_ads AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((advertiser_id = auth.uid()));

DROP POLICY IF EXISTS post_ads_select_active ON public.post_ads;
CREATE POLICY post_ads_select_active ON public.post_ads AS PERMISSIVE FOR SELECT TO authenticated
USING ((((status = 'active'::text) AND (starts_at <= now()) AND ((ends_at IS NULL) OR (ends_at > now()))) OR (advertiser_id = auth.uid())));

DROP POLICY IF EXISTS post_ads_update_own ON public.post_ads;
CREATE POLICY post_ads_update_own ON public.post_ads AS PERMISSIVE FOR UPDATE TO authenticated
USING ((advertiser_id = auth.uid()))
WITH CHECK ((advertiser_id = auth.uid()));

DROP POLICY IF EXISTS bookmarks_delete_own ON public.post_bookmarks;
CREATE POLICY bookmarks_delete_own ON public.post_bookmarks AS PERMISSIVE FOR DELETE TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS bookmarks_insert_own ON public.post_bookmarks;
CREATE POLICY bookmarks_insert_own ON public.post_bookmarks AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS bookmarks_select_own ON public.post_bookmarks;
CREATE POLICY bookmarks_select_own ON public.post_bookmarks AS PERMISSIVE FOR SELECT TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS post_likes_delete_own ON public.post_likes;
CREATE POLICY post_likes_delete_own ON public.post_likes AS PERMISSIVE FOR DELETE TO authenticated
USING ((( SELECT auth.uid() AS uid) = user_id));

DROP POLICY IF EXISTS post_likes_insert_own ON public.post_likes;
CREATE POLICY post_likes_insert_own ON public.post_likes AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((( SELECT auth.uid() AS uid) = user_id));

DROP POLICY IF EXISTS post_likes_select_all ON public.post_likes;
CREATE POLICY post_likes_select_all ON public.post_likes AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS post_reposts_read_authenticated ON public.post_reposts;
CREATE POLICY post_reposts_read_authenticated ON public.post_reposts AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS shares_insert_own ON public.post_shares;
CREATE POLICY shares_insert_own ON public.post_shares AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS shares_select_all ON public.post_shares;
CREATE POLICY shares_select_all ON public.post_shares AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS post_views_select_all ON public.post_views;
CREATE POLICY post_views_select_all ON public.post_views AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS "profile_posts: authenticated can create" ON public.profile_posts;
CREATE POLICY "profile_posts: authenticated can create" ON public.profile_posts AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK (true);

DROP POLICY IF EXISTS "profile_posts: authenticated can read" ON public.profile_posts;
CREATE POLICY "profile_posts: authenticated can read" ON public.profile_posts AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS "profile_posts: author can delete" ON public.profile_posts;
CREATE POLICY "profile_posts: author can delete" ON public.profile_posts AS PERMISSIVE FOR DELETE TO authenticated
USING (true);

DROP POLICY IF EXISTS "profile_posts: author can update" ON public.profile_posts;
CREATE POLICY "profile_posts: author can update" ON public.profile_posts AS PERMISSIVE FOR UPDATE TO authenticated
USING (true)
WITH CHECK (true);

DROP POLICY IF EXISTS profile_skills_delete_own ON public.profile_skills;
CREATE POLICY profile_skills_delete_own ON public.profile_skills AS PERMISSIVE FOR DELETE TO authenticated
USING ((auth.uid() = user_id));

DROP POLICY IF EXISTS profile_skills_insert_own ON public.profile_skills;
CREATE POLICY profile_skills_insert_own ON public.profile_skills AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((auth.uid() = user_id));

DROP POLICY IF EXISTS profile_skills_select_all ON public.profile_skills;
CREATE POLICY profile_skills_select_all ON public.profile_skills AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS profile_views_insert_own ON public.profile_views;
CREATE POLICY profile_views_insert_own ON public.profile_views AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((auth.uid() = viewer_id));

DROP POLICY IF EXISTS profile_views_select_own_profile ON public.profile_views;
CREATE POLICY profile_views_select_own_profile ON public.profile_views AS PERMISSIVE FOR SELECT TO authenticated
USING (((auth.uid() = profile_id) OR (auth.uid() = viewer_id)));

DROP POLICY IF EXISTS profiles_delete_own ON public.profiles;
CREATE POLICY profiles_delete_own ON public.profiles AS PERMISSIVE FOR DELETE TO authenticated
USING ((id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS profiles_insert_own ON public.profiles;
CREATE POLICY profiles_insert_own ON public.profiles AS PERMISSIVE FOR INSERT TO authenticated
WITH CHECK ((id = ( SELECT auth.uid() AS uid)));

DROP POLICY IF EXISTS profiles_select_authenticated ON public.profiles;
CREATE POLICY profiles_select_authenticated ON public.profiles AS PERMISSIVE FOR SELECT TO authenticated
USING (true);

DROP POLICY IF EXISTS profiles_update_own ON public.profiles;
CREATE POLICY profiles_update_own ON public.profiles AS PERMISSIVE FOR UPDATE TO authenticated
USING ((id = ( SELECT auth.uid() AS uid)))
WITH CHECK ((id = ( SELECT auth.uid() AS uid)));
