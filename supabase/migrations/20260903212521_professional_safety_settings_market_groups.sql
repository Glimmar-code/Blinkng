
BEGIN;

ALTER TABLE public.user_settings
  ADD COLUMN IF NOT EXISTS private_account boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS show_online_status boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS read_receipts boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS autoplay_videos boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS data_saver boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS reduce_motion boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS public.feed_preferences (
  user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE DEFAULT auth.uid(),
  post_id uuid NOT NULL REFERENCES public.feed_posts(id) ON DELETE CASCADE,
  preference text NOT NULL CHECK (preference IN ('not_interested','hide_author')),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,post_id,preference)
);

CREATE INDEX IF NOT EXISTS feed_preferences_user_created_idx
ON public.feed_preferences(user_id,created_at DESC);

ALTER TABLE public.feed_preferences ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.feed_preferences FROM anon;
GRANT SELECT,INSERT,DELETE ON public.feed_preferences TO authenticated;

DROP POLICY IF EXISTS feed_preferences_select_own ON public.feed_preferences;
CREATE POLICY feed_preferences_select_own ON public.feed_preferences
FOR SELECT TO authenticated
USING (user_id=(select auth.uid()));

DROP POLICY IF EXISTS feed_preferences_insert_own ON public.feed_preferences;
CREATE POLICY feed_preferences_insert_own ON public.feed_preferences
FOR INSERT TO authenticated
WITH CHECK (user_id=(select auth.uid()));

DROP POLICY IF EXISTS feed_preferences_delete_own ON public.feed_preferences;
CREATE POLICY feed_preferences_delete_own ON public.feed_preferences
FOR DELETE TO authenticated
USING (user_id=(select auth.uid()));

CREATE TABLE IF NOT EXISTS public.marketplace_wishlist (
  user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE DEFAULT auth.uid(),
  item_id uuid NOT NULL REFERENCES public.market_items(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,item_id)
);

ALTER TABLE public.marketplace_wishlist ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.marketplace_wishlist FROM anon;
GRANT SELECT,INSERT,DELETE ON public.marketplace_wishlist TO authenticated;

CREATE POLICY marketplace_wishlist_select_own ON public.marketplace_wishlist
FOR SELECT TO authenticated USING (user_id=(select auth.uid()));
CREATE POLICY marketplace_wishlist_insert_own ON public.marketplace_wishlist
FOR INSERT TO authenticated WITH CHECK (user_id=(select auth.uid()));
CREATE POLICY marketplace_wishlist_delete_own ON public.marketplace_wishlist
FOR DELETE TO authenticated USING (user_id=(select auth.uid()));

CREATE TABLE IF NOT EXISTS public.marketplace_reviews (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL UNIQUE REFERENCES public.marketplace_orders(id) ON DELETE CASCADE,
  reviewer_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE DEFAULT auth.uid(),
  reviewee_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  rating integer NOT NULL CHECK (rating BETWEEN 1 AND 5),
  comment text,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (reviewer_id<>reviewee_id)
);

CREATE INDEX IF NOT EXISTS marketplace_reviews_reviewee_idx
ON public.marketplace_reviews(reviewee_id,created_at DESC);

ALTER TABLE public.marketplace_reviews ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.marketplace_reviews FROM anon;
GRANT SELECT,INSERT,DELETE ON public.marketplace_reviews TO authenticated;

CREATE POLICY marketplace_reviews_read ON public.marketplace_reviews
FOR SELECT TO authenticated USING (true);
CREATE POLICY marketplace_reviews_insert_completed_order ON public.marketplace_reviews
FOR INSERT TO authenticated
WITH CHECK (
  reviewer_id=(select auth.uid())
  AND EXISTS(
    SELECT 1 FROM public.marketplace_orders o
    WHERE o.id=order_id
      AND o.status='completed'
      AND (
        (o.buyer_id=(select auth.uid()) AND reviewee_id=o.seller_id)
        OR (o.seller_id=(select auth.uid()) AND reviewee_id=o.buyer_id)
      )
  )
);
CREATE POLICY marketplace_reviews_delete_own ON public.marketplace_reviews
FOR DELETE TO authenticated USING (reviewer_id=(select auth.uid()));

ALTER TABLE public.conversations
  ADD COLUMN IF NOT EXISTS title text,
  ADD COLUMN IF NOT EXISTS avatar_url text,
  ADD COLUMN IF NOT EXISTS description text;

CREATE TABLE IF NOT EXISTS public.conversation_settings (
  conversation_id uuid NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE DEFAULT auth.uid(),
  archived boolean NOT NULL DEFAULT false,
  muted_until timestamptz,
  disappearing_seconds integer NOT NULL DEFAULT 0 CHECK (disappearing_seconds BETWEEN 0 AND 2592000),
  theme text NOT NULL DEFAULT 'default',
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(conversation_id,user_id)
);

ALTER TABLE public.conversation_settings ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.conversation_settings FROM anon;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.conversation_settings TO authenticated;
CREATE POLICY conversation_settings_select_own ON public.conversation_settings
FOR SELECT TO authenticated USING (user_id=(select auth.uid()));
CREATE POLICY conversation_settings_insert_own ON public.conversation_settings
FOR INSERT TO authenticated WITH CHECK (
  user_id=(select auth.uid())
  AND EXISTS(
    SELECT 1 FROM public.conversation_participants cp
    WHERE cp.conversation_id=conversation_settings.conversation_id
      AND cp.user_id=(select auth.uid())
  )
);
CREATE POLICY conversation_settings_update_own ON public.conversation_settings
FOR UPDATE TO authenticated
USING (user_id=(select auth.uid()))
WITH CHECK (user_id=(select auth.uid()));
CREATE POLICY conversation_settings_delete_own ON public.conversation_settings
FOR DELETE TO authenticated USING (user_id=(select auth.uid()));

-- Consolidate policies with precise commands and init-plan-friendly auth.uid().
DROP POLICY IF EXISTS "follows_delete_own" ON public.follows;
DROP POLICY IF EXISTS "follows_insert_own" ON public.follows;
DROP POLICY IF EXISTS "follows_select_all" ON public.follows;
CREATE POLICY follows_select_all ON public.follows
FOR SELECT TO authenticated USING (true);
CREATE POLICY follows_insert_own ON public.follows
FOR INSERT TO authenticated WITH CHECK (
  follower_id=(select auth.uid())
  AND following_id<>(select auth.uid())
  AND NOT EXISTS(
    SELECT 1 FROM public.blocks b
    WHERE (b.blocker_id=(select auth.uid()) AND b.blocked_id=following_id)
       OR (b.blocker_id=following_id AND b.blocked_id=(select auth.uid()))
  )
);
CREATE POLICY follows_delete_own ON public.follows
FOR DELETE TO authenticated USING (follower_id=(select auth.uid()));

DROP POLICY IF EXISTS "blocks: user manage own" ON public.blocks;
DROP POLICY IF EXISTS "blocks_delete_own" ON public.blocks;
DROP POLICY IF EXISTS "blocks_insert_own" ON public.blocks;
DROP POLICY IF EXISTS "blocks_select_own" ON public.blocks;
CREATE POLICY blocks_select_own ON public.blocks
FOR SELECT TO authenticated USING (blocker_id=(select auth.uid()));
CREATE POLICY blocks_insert_own ON public.blocks
FOR INSERT TO authenticated WITH CHECK (
  blocker_id=(select auth.uid()) AND blocked_id<>(select auth.uid())
);
CREATE POLICY blocks_delete_own ON public.blocks
FOR DELETE TO authenticated USING (blocker_id=(select auth.uid()));

DROP POLICY IF EXISTS "muted_delete_own" ON public.muted_users;
DROP POLICY IF EXISTS "muted_insert_own" ON public.muted_users;
DROP POLICY IF EXISTS "muted_select_own" ON public.muted_users;
CREATE POLICY muted_select_own ON public.muted_users
FOR SELECT TO authenticated USING (user_id=(select auth.uid()));
CREATE POLICY muted_insert_own ON public.muted_users
FOR INSERT TO authenticated WITH CHECK (
  user_id=(select auth.uid()) AND muted_id<>(select auth.uid())
);
CREATE POLICY muted_delete_own ON public.muted_users
FOR DELETE TO authenticated USING (user_id=(select auth.uid()));

DROP POLICY IF EXISTS "reports: user can insert" ON public.reports;
DROP POLICY IF EXISTS "reports_insert_own" ON public.reports;
DROP POLICY IF EXISTS "reports_select_own" ON public.reports;
CREATE POLICY reports_insert_own ON public.reports
FOR INSERT TO authenticated WITH CHECK (reporter_id=(select auth.uid()));
CREATE POLICY reports_select_own ON public.reports
FOR SELECT TO authenticated USING (reporter_id=(select auth.uid()));

DROP POLICY IF EXISTS "settings: owner read update" ON public.user_settings;
DROP POLICY IF EXISTS "user_settings_insert_own" ON public.user_settings;
DROP POLICY IF EXISTS "user_settings_select_own" ON public.user_settings;
DROP POLICY IF EXISTS "user_settings_update_own" ON public.user_settings;
CREATE POLICY user_settings_select_own ON public.user_settings
FOR SELECT TO authenticated USING (user_id=(select auth.uid()));
CREATE POLICY user_settings_insert_own ON public.user_settings
FOR INSERT TO authenticated WITH CHECK (user_id=(select auth.uid()));
CREATE POLICY user_settings_update_own ON public.user_settings
FOR UPDATE TO authenticated
USING (user_id=(select auth.uid()))
WITH CHECK (user_id=(select auth.uid()));
CREATE POLICY user_settings_delete_own ON public.user_settings
FOR DELETE TO authenticated USING (user_id=(select auth.uid()));

DROP POLICY IF EXISTS market_items_select_all ON public.market_items;
DROP POLICY IF EXISTS market_items_insert_own ON public.market_items;
DROP POLICY IF EXISTS market_items_update_own ON public.market_items;
DROP POLICY IF EXISTS market_items_delete_own ON public.market_items;
CREATE POLICY market_items_select_visible ON public.market_items
FOR SELECT TO authenticated
USING (
  seller_id=(select auth.uid())
  OR (coalesce(is_sold,false)=false AND coalesce(status,'active')='active')
);
CREATE POLICY market_items_insert_own ON public.market_items
FOR INSERT TO authenticated WITH CHECK (seller_id=(select auth.uid()));
CREATE POLICY market_items_update_own ON public.market_items
FOR UPDATE TO authenticated
USING (seller_id=(select auth.uid()))
WITH CHECK (seller_id=(select auth.uid()));
CREATE POLICY market_items_delete_own ON public.market_items
FOR DELETE TO authenticated USING (seller_id=(select auth.uid()));

-- Orders must be created/transitioned through validated RPCs.
REVOKE INSERT,UPDATE ON public.marketplace_orders FROM authenticated;
GRANT SELECT,DELETE ON public.marketplace_orders TO authenticated;
DROP POLICY IF EXISTS mp_order_insert_own ON public.marketplace_orders;
DROP POLICY IF EXISTS mp_order_select ON public.marketplace_orders;
DROP POLICY IF EXISTS mp_order_delete_own ON public.marketplace_orders;
CREATE POLICY mp_order_select ON public.marketplace_orders
FOR SELECT TO authenticated
USING (buyer_id=(select auth.uid()) OR seller_id=(select auth.uid()));
CREATE POLICY mp_order_delete_own ON public.marketplace_orders
FOR DELETE TO authenticated
USING (buyer_id=(select auth.uid()) AND status='pending');

CREATE OR REPLACE FUNCTION public.set_post_preference(
  p_post_id uuid,
  p_preference text
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path=public,pg_temp
AS $$
BEGIN
  IF p_preference NOT IN ('not_interested','hide_author') THEN
    RAISE EXCEPTION 'INVALID_PREFERENCE';
  END IF;
  INSERT INTO public.feed_preferences(user_id,post_id,preference)
  VALUES((select auth.uid()),p_post_id,p_preference)
  ON CONFLICT DO NOTHING;
  RETURN true;
END;
$$;
REVOKE ALL ON FUNCTION public.set_post_preference(uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.set_post_preference(uuid,text) TO authenticated;

CREATE OR REPLACE FUNCTION public.block_user(p_target_id uuid)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE v_uid uuid := auth.uid();
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  IF p_target_id=v_uid THEN RAISE EXCEPTION 'CANNOT_BLOCK_SELF'; END IF;
  IF NOT EXISTS(SELECT 1 FROM public.profiles WHERE id=p_target_id) THEN
    RAISE EXCEPTION 'USER_NOT_FOUND';
  END IF;

  INSERT INTO public.blocks(blocker_id,blocked_id)
  VALUES(v_uid,p_target_id)
  ON CONFLICT DO NOTHING;

  DELETE FROM public.follows
  WHERE (follower_id=v_uid AND following_id=p_target_id)
     OR (follower_id=p_target_id AND following_id=v_uid);

  DELETE FROM public.connection_requests
  WHERE (sender_id=v_uid AND receiver_id=p_target_id)
     OR (sender_id=p_target_id AND receiver_id=v_uid);

  RETURN true;
END;
$$;
REVOKE ALL ON FUNCTION public.block_user(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.block_user(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.unblock_user(p_target_id uuid)
RETURNS boolean
LANGUAGE sql
SECURITY INVOKER
SET search_path=public,pg_temp
AS $$
  WITH d AS (
    DELETE FROM public.blocks
    WHERE blocker_id=(select auth.uid()) AND blocked_id=p_target_id
    RETURNING 1
  )
  SELECT true;
$$;
REVOKE ALL ON FUNCTION public.unblock_user(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.unblock_user(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.report_user(
  p_user_id uuid,
  p_reason text
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path=public,pg_temp
AS $$
DECLARE v_id uuid;
BEGIN
  IF (select auth.uid()) IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  IF p_user_id=(select auth.uid()) THEN RAISE EXCEPTION 'CANNOT_REPORT_SELF'; END IF;
  IF length(trim(coalesce(p_reason,'')))<3 THEN RAISE EXCEPTION 'REASON_REQUIRED'; END IF;
  IF (
    SELECT count(*) FROM public.reports
    WHERE reporter_id=(select auth.uid()) AND created_at>now()-interval '1 hour'
  ) >= 10 THEN RAISE EXCEPTION 'REPORT_RATE_LIMITED'; END IF;

  INSERT INTO public.reports(reporter_id,reported_user_id,reason,status)
  VALUES((select auth.uid()),p_user_id,left(trim(p_reason),500),'pending')
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;
REVOKE ALL ON FUNCTION public.report_user(uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.report_user(uuid,text) TO authenticated;

CREATE OR REPLACE FUNCTION public.create_marketplace_order(
  p_item_id uuid,
  p_quantity integer DEFAULT 1
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE
  v_item public.market_items%ROWTYPE;
  v_order_id uuid;
  v_total numeric;
  v_uid uuid := auth.uid();
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  IF p_quantity<=0 OR p_quantity>20 THEN RAISE EXCEPTION 'INVALID_QUANTITY'; END IF;

  SELECT * INTO v_item
  FROM public.market_items
  WHERE id=p_item_id
    AND coalesce(is_sold,false)=false
    AND coalesce(status,'active')='active'
  FOR UPDATE;

  IF NOT FOUND THEN RAISE EXCEPTION 'ITEM_NOT_AVAILABLE'; END IF;
  IF v_item.seller_id IS NULL OR v_item.seller_id=v_uid THEN
    RAISE EXCEPTION 'INVALID_SELLER';
  END IF;
  IF p_quantity>coalesce(v_item.quantity,1) THEN RAISE EXCEPTION 'INSUFFICIENT_QUANTITY'; END IF;
  IF EXISTS(
    SELECT 1 FROM public.blocks b
    WHERE (b.blocker_id=v_uid AND b.blocked_id=v_item.seller_id)
       OR (b.blocker_id=v_item.seller_id AND b.blocked_id=v_uid)
  ) THEN RAISE EXCEPTION 'USER_BLOCKED'; END IF;
  IF (
    SELECT count(*) FROM public.marketplace_orders
    WHERE buyer_id=v_uid AND created_at>now()-interval '1 minute'
  )>=8 THEN RAISE EXCEPTION 'ORDER_RATE_LIMITED'; END IF;

  v_total:=v_item.price*p_quantity;
  INSERT INTO public.marketplace_orders(
    item_id,buyer_id,seller_id,quantity,unit_price,total_price,currency,status
  )
  VALUES(
    p_item_id,v_uid,v_item.seller_id,p_quantity,v_item.price,v_total,
    coalesce(v_item.currency,'NGN'),'pending'
  )
  RETURNING id INTO v_order_id;

  RETURN v_order_id;
END;
$$;
REVOKE ALL ON FUNCTION public.create_marketplace_order(uuid,integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.create_marketplace_order(uuid,integer) TO authenticated;

CREATE OR REPLACE FUNCTION public.update_marketplace_order_status(
  p_order_id uuid,
  p_status text
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE
  v_order public.marketplace_orders%ROWTYPE;
  v_uid uuid := auth.uid();
  v_next text := lower(trim(coalesce(p_status,'')));
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  SELECT * INTO v_order FROM public.marketplace_orders WHERE id=p_order_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'ORDER_NOT_FOUND'; END IF;

  IF v_uid=v_order.seller_id THEN
    IF NOT (
      (v_order.status='pending' AND v_next IN ('accepted','declined'))
      OR (v_order.status='accepted' AND v_next IN ('completed','cancelled'))
    ) THEN RAISE EXCEPTION 'INVALID_SELLER_TRANSITION'; END IF;
  ELSIF v_uid=v_order.buyer_id THEN
    IF NOT (
      (v_order.status='pending' AND v_next='cancelled')
      OR (v_order.status='accepted' AND v_next='completed')
    ) THEN RAISE EXCEPTION 'INVALID_BUYER_TRANSITION'; END IF;
  ELSE
    RAISE EXCEPTION 'NOT_AUTHORIZED';
  END IF;

  UPDATE public.marketplace_orders SET status=v_next,updated_at=now() WHERE id=p_order_id;
  IF v_next='completed' THEN
    UPDATE public.market_items
    SET quantity=greatest(0,quantity-v_order.quantity),
        is_sold=(quantity-v_order.quantity)<=0,
        status=CASE WHEN (quantity-v_order.quantity)<=0 THEN 'sold' ELSE status END
    WHERE id=v_order.item_id;
  END IF;
  RETURN true;
END;
$$;
REVOKE ALL ON FUNCTION public.update_marketplace_order_status(uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.update_marketplace_order_status(uuid,text) TO authenticated;

CREATE OR REPLACE FUNCTION public.create_group_conversation(
  p_title text,
  p_member_ids uuid[]
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_conv uuid;
  v_members uuid[];
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  IF length(trim(coalesce(p_title,'')))<2 THEN RAISE EXCEPTION 'GROUP_TITLE_REQUIRED'; END IF;

  SELECT array_agg(DISTINCT x)
  INTO v_members
  FROM unnest(array_append(coalesce(p_member_ids,'{}'::uuid[]),v_uid)) x
  WHERE x IS NOT NULL;

  IF cardinality(v_members)<3 OR cardinality(v_members)>50 THEN
    RAISE EXCEPTION 'GROUP_MEMBER_LIMIT';
  END IF;
  IF EXISTS(
    SELECT 1 FROM unnest(v_members) x
    WHERE NOT EXISTS(SELECT 1 FROM public.profiles p WHERE p.id=x)
  ) THEN RAISE EXCEPTION 'GROUP_MEMBER_NOT_FOUND'; END IF;
  IF EXISTS(
    SELECT 1 FROM public.blocks b
    WHERE b.blocker_id=ANY(v_members)
      AND b.blocked_id=ANY(v_members)
  ) THEN RAISE EXCEPTION 'GROUP_CONTAINS_BLOCKED_USERS'; END IF;

  INSERT INTO public.conversations(is_group,created_by,title,last_message_at)
  VALUES(true,v_uid,left(trim(p_title),80),now())
  RETURNING id INTO v_conv;

  INSERT INTO public.conversation_participants(conversation_id,user_id,is_admin)
  SELECT v_conv,x,(x=v_uid)
  FROM unnest(v_members) x;

  RETURN v_conv;
END;
$$;
REVOKE ALL ON FUNCTION public.create_group_conversation(text,uuid[]) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.create_group_conversation(text,uuid[]) TO authenticated;

CREATE OR REPLACE FUNCTION public.send_group_message(
  p_conversation_id uuid,
  p_content text
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path=public,pg_temp
AS $$
DECLARE v_id uuid;
BEGIN
  IF nullif(trim(coalesce(p_content,'')),'') IS NULL THEN RAISE EXCEPTION 'MESSAGE_REQUIRED'; END IF;
  IF NOT EXISTS(
    SELECT 1
    FROM public.conversations c
    JOIN public.conversation_participants cp ON cp.conversation_id=c.id
    WHERE c.id=p_conversation_id
      AND c.is_group
      AND cp.user_id=(select auth.uid())
  ) THEN RAISE EXCEPTION 'NOT_A_GROUP_MEMBER'; END IF;
  IF (
    SELECT count(*) FROM public.messages
    WHERE sender_id=(select auth.uid()) AND created_at>now()-interval '1 minute'
  )>=60 THEN RAISE EXCEPTION 'MESSAGE_RATE_LIMITED'; END IF;

  INSERT INTO public.messages(conversation_id,sender_id,content,message_type)
  VALUES(p_conversation_id,(select auth.uid()),left(trim(p_content),5000),'text')
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;
REVOKE ALL ON FUNCTION public.send_group_message(uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.send_group_message(uuid,text) TO authenticated;

-- Keep the modern 2-argument direct-message RPC; remove normal-client access to the legacy sender-id overload.
REVOKE ALL ON FUNCTION public.send_message(uuid,text,text) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.send_message(
  p_receiver_username text,
  p_content text
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,pg_temp
AS $$
DECLARE
  v_sender uuid := auth.uid();
  v_receiver uuid;
  v_conversation uuid;
  v_message uuid;
  v_privacy text;
BEGIN
  IF v_sender IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  IF nullif(trim(p_receiver_username),'') IS NULL THEN RAISE EXCEPTION 'RECIPIENT_REQUIRED'; END IF;
  IF nullif(trim(p_content),'') IS NULL THEN RAISE EXCEPTION 'MESSAGE_REQUIRED'; END IF;
  IF length(p_content)>5000 THEN RAISE EXCEPTION 'MESSAGE_TOO_LONG'; END IF;

  SELECT id INTO v_receiver
  FROM public.profiles
  WHERE lower(username)=lower(trim(p_receiver_username))
  LIMIT 1;

  IF v_receiver IS NULL THEN RAISE EXCEPTION 'RECIPIENT_NOT_FOUND'; END IF;
  IF v_receiver=v_sender THEN RAISE EXCEPTION 'CANNOT_MESSAGE_SELF'; END IF;

  IF EXISTS(
    SELECT 1 FROM public.blocks b
    WHERE (b.blocker_id=v_sender AND b.blocked_id=v_receiver)
       OR (b.blocker_id=v_receiver AND b.blocked_id=v_sender)
  ) THEN RAISE EXCEPTION 'USER_BLOCKED'; END IF;

  SELECT coalesce(us.dm_privacy::text,'everyone') INTO v_privacy
  FROM public.user_settings us WHERE us.user_id=v_receiver;
  v_privacy:=coalesce(v_privacy,'everyone');

  IF v_privacy='nobody' THEN RAISE EXCEPTION 'DM_PRIVACY_RESTRICTED'; END IF;
  IF v_privacy='following' AND NOT EXISTS(
    SELECT 1 FROM public.follows f
    WHERE f.follower_id=v_receiver AND f.following_id=v_sender
  ) THEN RAISE EXCEPTION 'DM_PRIVACY_FOLLOWING_ONLY'; END IF;

  IF (
    SELECT count(*) FROM public.messages
    WHERE sender_id=v_sender AND created_at>now()-interval '1 minute'
  )>=60 THEN RAISE EXCEPTION 'MESSAGE_RATE_LIMITED'; END IF;

  SELECT c.id INTO v_conversation
  FROM public.conversations c
  WHERE c.is_group=false
    AND EXISTS(SELECT 1 FROM public.conversation_participants cp WHERE cp.conversation_id=c.id AND cp.user_id=v_sender)
    AND EXISTS(SELECT 1 FROM public.conversation_participants cp WHERE cp.conversation_id=c.id AND cp.user_id=v_receiver)
  LIMIT 1;

  IF v_conversation IS NULL THEN
    INSERT INTO public.conversations(created_by,is_group,last_message_at)
    VALUES(v_sender,false,now()) RETURNING id INTO v_conversation;
    INSERT INTO public.conversation_participants(conversation_id,user_id)
    VALUES(v_conversation,v_sender),(v_conversation,v_receiver)
    ON CONFLICT DO NOTHING;
  END IF;

  INSERT INTO public.messages(conversation_id,sender_id,content,message_type)
  VALUES(v_conversation,v_sender,trim(p_content),'text')
  RETURNING id INTO v_message;

  UPDATE public.conversations
  SET last_message_at=now(),updated_at=now()
  WHERE id=v_conversation;

  RETURN v_message;
END;
$$;
REVOKE ALL ON FUNCTION public.send_message(text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.send_message(text,text) TO authenticated;

CREATE OR REPLACE FUNCTION public.export_my_account_data()
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path=public,pg_temp
AS $$
  SELECT jsonb_build_object(
    'profile',(SELECT to_jsonb(p) FROM public.profiles p WHERE p.id=(select auth.uid())),
    'settings',(SELECT to_jsonb(s) FROM public.user_settings s WHERE s.user_id=(select auth.uid())),
    'posts',coalesce((SELECT jsonb_agg(to_jsonb(fp) ORDER BY fp.created_at DESC) FROM public.feed_posts fp WHERE fp.user_id=(select auth.uid())),'[]'::jsonb),
    'marketItems',coalesce((SELECT jsonb_agg(to_jsonb(mi) ORDER BY mi.created_at DESC) FROM public.market_items mi WHERE mi.seller_id=(select auth.uid())),'[]'::jsonb),
    'orders',coalesce((SELECT jsonb_agg(to_jsonb(o) ORDER BY o.created_at DESC) FROM public.marketplace_orders o WHERE o.buyer_id=(select auth.uid()) OR o.seller_id=(select auth.uid())),'[]'::jsonb),
    'messages',coalesce((
      SELECT jsonb_agg(to_jsonb(m) ORDER BY m.created_at ASC)
      FROM public.messages m
      WHERE EXISTS(
        SELECT 1 FROM public.conversation_participants cp
        WHERE cp.conversation_id=m.conversation_id AND cp.user_id=(select auth.uid())
      )
    ),'[]'::jsonb)
  );
$$;
REVOKE ALL ON FUNCTION public.export_my_account_data() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.export_my_account_data() TO authenticated;

CREATE OR REPLACE FUNCTION public.delete_my_account(p_confirmation text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=public,auth,pg_temp
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_username text;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  SELECT username INTO v_username FROM public.profiles WHERE id=v_uid;
  IF lower(trim(coalesce(p_confirmation,'')))<>lower(trim(coalesce(v_username,''))) THEN
    RAISE EXCEPTION 'CONFIRMATION_MISMATCH';
  END IF;
  DELETE FROM auth.users WHERE id=v_uid;
  RETURN true;
END;
$$;
REVOKE ALL ON FUNCTION public.delete_my_account(text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.delete_my_account(text) TO authenticated;

CREATE OR REPLACE FUNCTION public.get_feed_page(
  p_limit integer DEFAULT 30,
  p_before timestamptz DEFAULT NULL,
  p_before_id uuid DEFAULT NULL,
  p_feed_type text DEFAULT 'posts'
)
RETURNS SETOF public.feed_posts
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path=public,pg_temp
AS $$
  SELECT fp.*
  FROM public.feed_posts fp
  WHERE fp.is_active=true
    AND (
      p_before IS NULL
      OR fp.created_at<p_before
      OR (fp.created_at=p_before AND (p_before_id IS NULL OR fp.id<p_before_id))
    )
    AND NOT EXISTS(
      SELECT 1 FROM public.blocks b
      WHERE (b.blocker_id=(select auth.uid()) AND b.blocked_id=fp.user_id)
         OR (b.blocker_id=fp.user_id AND b.blocked_id=(select auth.uid()))
    )
    AND NOT EXISTS(
      SELECT 1 FROM public.muted_users mu
      WHERE mu.user_id=(select auth.uid()) AND mu.muted_id=fp.user_id
    )
    AND NOT EXISTS(
      SELECT 1 FROM public.feed_preferences pref
      WHERE pref.user_id=(select auth.uid())
        AND pref.post_id=fp.id
        AND pref.preference='not_interested'
    )
    AND (
      CASE lower(coalesce(p_feed_type,'posts'))
        WHEN 'reels' THEN coalesce(fp.is_reel,false) OR nullif(fp.video_url,'') IS NOT NULL
        WHEN 'following' THEN
          NOT(coalesce(fp.is_reel,false) OR nullif(fp.video_url,'') IS NOT NULL)
          AND EXISTS(
            SELECT 1 FROM public.follows f
            WHERE f.follower_id=(select auth.uid()) AND f.following_id=fp.user_id
          )
        WHEN 'all' THEN true
        ELSE NOT(coalesce(fp.is_reel,false) OR nullif(fp.video_url,'') IS NOT NULL)
      END
    )
  ORDER BY fp.created_at DESC,fp.id DESC
  LIMIT greatest(1,least(coalesce(p_limit,30),60));
$$;

COMMIT;
