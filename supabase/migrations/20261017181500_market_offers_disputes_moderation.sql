-- BLINK Market: buyer/seller negotiation, dispute safety, moderation and appeal.
-- Rollback: revoke RPC access, disable new triggers, export new workflow tables
-- before dropping them. No payment, escrow or buyer refunds are automated here.
BEGIN;

CREATE TABLE IF NOT EXISTS public.market_offers(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 item_id uuid NOT NULL REFERENCES public.market_items(id),
 buyer_id uuid NOT NULL REFERENCES public.profiles(id),
 seller_id uuid NOT NULL REFERENCES public.profiles(id),
 asking_price bigint NOT NULL CHECK(asking_price>0),
 proposed_price bigint NOT NULL CHECK(proposed_price>0),
 status text NOT NULL DEFAULT 'offered'
   CHECK(status IN ('offered','countered','accepted','declined','cancelled')),
 message text NOT NULL DEFAULT '',
 updated_at timestamptz NOT NULL DEFAULT now(),
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS market_offers_seller_status_idx ON public.market_offers(seller_id,status,created_at DESC);
CREATE INDEX IF NOT EXISTS market_offers_buyer_idx ON public.market_offers(buyer_id,created_at DESC);
ALTER TABLE public.market_offers ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.market_offers FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.market_offers TO authenticated;
DROP POLICY IF EXISTS market_offers_participant_read ON public.market_offers;
CREATE POLICY market_offers_participant_read ON public.market_offers FOR SELECT TO authenticated
 USING (buyer_id=(select auth.uid()) OR seller_id=(select auth.uid()));

CREATE TABLE IF NOT EXISTS public.market_offer_events(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 offer_id uuid NOT NULL REFERENCES public.market_offers(id) ON DELETE CASCADE,
 actor_id uuid NOT NULL REFERENCES public.profiles(id),
 event_kind text NOT NULL CHECK(event_kind IN ('offered','countered','accepted','declined','cancelled')),
 amount bigint NOT NULL CHECK(amount>0),
 note text NOT NULL DEFAULT '',
 created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.market_offer_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.market_offer_events FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.market_offer_events TO authenticated;
DROP POLICY IF EXISTS market_offer_events_read ON public.market_offer_events;
CREATE POLICY market_offer_events_read ON public.market_offer_events FOR SELECT TO authenticated
 USING(EXISTS(SELECT 1 FROM public.market_offers o WHERE o.id=offer_id
      AND (o.buyer_id=(select auth.uid()) OR o.seller_id=(select auth.uid()))));

CREATE OR REPLACE FUNCTION public.make_market_offer(p_item uuid,p_amount bigint,p_note text DEFAULT '')
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $offer_create$
DECLARE v_user uuid:=auth.uid();v_listing public.market_items%ROWTYPE;v_id uuid;
BEGIN
 IF v_user IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
 SELECT * INTO v_listing FROM public.market_items WHERE id=p_item AND status='active' AND NOT is_sold;
 IF NOT FOUND THEN RAISE EXCEPTION 'LISTING_NOT_AVAILABLE'; END IF;
 IF v_listing.seller_id=v_user THEN RAISE EXCEPTION 'CANNOT_OFFER_OWN_PRODUCT'; END IF;
 IF p_amount IS NULL OR p_amount<1 OR p_amount>v_listing.price*3
    OR length(trim(coalesce(p_note,'')))>500 THEN RAISE EXCEPTION 'INVALID_OFFER'; END IF;
 IF (SELECT count(*) FROM public.market_offers WHERE buyer_id=v_user
        AND created_at>now()-interval '24 hours')>=10
 THEN RAISE EXCEPTION 'TOO_MANY_OFFERS'; END IF;
 IF EXISTS (SELECT 1 FROM public.market_offers WHERE buyer_id=v_user
    AND item_id=p_item AND status IN ('offered','countered')) THEN
    RAISE EXCEPTION 'OFFER_ALREADY_OPEN';
 END IF;
 INSERT INTO public.market_offers(item_id,buyer_id,seller_id,asking_price,proposed_price,message)
 VALUES(p_item,v_user,v_listing.seller_id,v_listing.price,p_amount,trim(coalesce(p_note,'')))
 RETURNING id INTO v_id;
 INSERT INTO public.market_offer_events(offer_id,actor_id,event_kind,amount,note)
 VALUES(v_id,v_user,'offered',p_amount,trim(coalesce(p_note,'')));
 INSERT INTO public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message,is_read)
 VALUES(v_listing.seller_id,v_user,'MARKET_OFFER','market',p_item,'sent an offer for '||v_listing.title,false);
 RETURN v_id;
END;
$offer_create$;
REVOKE ALL ON FUNCTION public.make_market_offer(uuid,bigint,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.make_market_offer(uuid,bigint,text) TO authenticated;

CREATE OR REPLACE FUNCTION public.respond_market_offer(p_offer uuid,p_action text,
  p_counter_amount bigint DEFAULT NULL,p_note text DEFAULT '')
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $offer_response$
DECLARE v_user uuid:=auth.uid();v_offer public.market_offers%ROWTYPE;v_status text;
DECLARE v_new_amount bigint;v_recipient uuid;
BEGIN
 IF v_user IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
 SELECT * INTO v_offer FROM public.market_offers WHERE id=p_offer FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'OFFER_NOT_FOUND'; END IF;
 v_status:=lower(trim(coalesce(p_action,'')));
 IF v_status NOT IN ('countered','accepted','declined','cancelled')
    OR length(trim(coalesce(p_note,'')))>500 THEN RAISE EXCEPTION 'INVALID_OFFER_RESPONSE'; END IF;
 IF v_offer.status NOT IN ('offered','countered') THEN RAISE EXCEPTION 'OFFER_CLOSED'; END IF;
 IF v_user=v_offer.seller_id THEN
   IF v_status='cancelled' THEN RAISE EXCEPTION 'SELLER_CANNOT_CANCEL'; END IF;
   v_recipient:=v_offer.buyer_id;
 ELSIF v_user=v_offer.buyer_id THEN
   IF NOT (v_status='cancelled' OR
      (v_status='accepted' AND v_offer.status='countered'))
   THEN RAISE EXCEPTION 'BUYER_MAY_ONLY_CANCEL_OR_ACCEPT_COUNTER'; END IF;
   v_recipient:=v_offer.seller_id;
 ELSE RAISE EXCEPTION 'NOT_A_PARTICIPANT'; END IF;
 v_new_amount:=CASE WHEN v_status='countered' THEN p_counter_amount ELSE v_offer.proposed_price END;
 IF v_new_amount IS NULL OR v_new_amount<1 OR v_new_amount>v_offer.asking_price*3
 THEN RAISE EXCEPTION 'INVALID_COUNTER_AMOUNT'; END IF;
 UPDATE public.market_offers SET status=v_status,proposed_price=v_new_amount,
   updated_at=now() WHERE id=p_offer;
 INSERT INTO public.market_offer_events(offer_id,actor_id,event_kind,amount,note)
 VALUES(p_offer,v_user,v_status,v_new_amount,trim(coalesce(p_note,'')));
 INSERT INTO public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message,is_read)
 VALUES(v_recipient,v_user,'MARKET_OFFER','market',v_offer.item_id,
        'updated a Market offer: '||v_status,false);
 RETURN true;
END;
$offer_response$;
REVOKE ALL ON FUNCTION public.respond_market_offer(uuid,text,bigint,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.respond_market_offer(uuid,text,bigint,text) TO authenticated;

CREATE TABLE IF NOT EXISTS public.market_disputes(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 order_id uuid NOT NULL REFERENCES public.marketplace_orders(id),
 opened_by uuid NOT NULL REFERENCES public.profiles(id),
 seller_id uuid NOT NULL REFERENCES public.profiles(id),
 buyer_id uuid NOT NULL REFERENCES public.profiles(id),
 reason text NOT NULL CHECK(length(reason) BETWEEN 8 AND 200),
 details text NOT NULL DEFAULT '',
 status text NOT NULL DEFAULT 'open'
   CHECK(status IN ('open','reviewing','resolved','dismissed')),
 resolution text,
 resolved_by uuid REFERENCES public.profiles(id),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(order_id,opened_by)
);
ALTER TABLE public.market_disputes ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.market_disputes FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.market_disputes TO authenticated;
DROP POLICY IF EXISTS market_disputes_participant_read ON public.market_disputes;
CREATE POLICY market_disputes_participant_read ON public.market_disputes FOR SELECT TO authenticated
 USING(buyer_id=(select auth.uid()) OR seller_id=(select auth.uid()));

CREATE OR REPLACE FUNCTION public.open_market_dispute(
 p_order uuid,p_reason text,p_details text DEFAULT ''
) RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $dispute_create$
DECLARE v_uid uuid:=auth.uid();v_order public.marketplace_orders%ROWTYPE;v_id uuid;
BEGIN
 IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
 SELECT * INTO v_order FROM public.marketplace_orders WHERE id=p_order;
 IF NOT FOUND OR v_uid NOT IN (v_order.buyer_id,v_order.seller_id)
   THEN RAISE EXCEPTION 'ORDER_NOT_FOUND_OR_NOT_PARTICIPANT'; END IF;
 IF length(trim(coalesce(p_reason,''))) NOT BETWEEN 8 AND 200
    OR length(trim(coalesce(p_details,'')))>2000 THEN RAISE EXCEPTION 'INVALID_DISPUTE'; END IF;
 INSERT INTO public.market_disputes(order_id,opened_by,seller_id,buyer_id,reason,details)
 VALUES(p_order,v_uid,v_order.seller_id,v_order.buyer_id,trim(p_reason),trim(p_details))
 ON CONFLICT(order_id,opened_by) DO UPDATE SET updated_at=public.market_disputes.updated_at
 RETURNING id INTO v_id;
 RETURN v_id;
END;
$dispute_create$;
REVOKE ALL ON FUNCTION public.open_market_dispute(uuid,text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.open_market_dispute(uuid,text,text) TO authenticated;

CREATE TABLE IF NOT EXISTS public.market_moderation_appeals(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES public.profiles(id),
 item_id uuid REFERENCES public.market_items(id),
 post_id uuid REFERENCES public.feed_posts(id),
 reason text NOT NULL,
 status text NOT NULL DEFAULT 'pending'
   CHECK(status IN ('pending','reviewing','approved','rejected')),
 moderator_note text,
 reviewed_by uuid REFERENCES public.profiles(id),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK((item_id IS NOT NULL) <> (post_id IS NOT NULL))
);
ALTER TABLE public.market_moderation_appeals ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.market_moderation_appeals FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.market_moderation_appeals TO authenticated;
DROP POLICY IF EXISTS market_appeals_owner_read ON public.market_moderation_appeals;
CREATE POLICY market_appeals_owner_read ON public.market_moderation_appeals
 FOR SELECT TO authenticated USING(user_id=(select auth.uid()));

CREATE OR REPLACE FUNCTION public.request_market_content_review(
 p_item uuid DEFAULT NULL,p_post uuid DEFAULT NULL,p_reason text DEFAULT ''
) RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $appeal$
DECLARE v_user uuid:=auth.uid();v_id uuid;
BEGIN
 IF v_user IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
 IF (p_item IS NULL)=(p_post IS NULL)
    OR length(trim(coalesce(p_reason,''))) NOT BETWEEN 15 AND 1000
 THEN RAISE EXCEPTION 'INVALID_APPEAL'; END IF;
 IF p_item IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.market_items
        WHERE id=p_item AND seller_id=v_user) THEN
   RAISE EXCEPTION 'NOT_OWN_ITEM'; END IF;
 IF p_post IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.feed_posts
        WHERE id=p_post AND user_id=v_user) THEN
   RAISE EXCEPTION 'NOT_OWN_POST'; END IF;
 IF (SELECT count(*) FROM public.market_moderation_appeals WHERE user_id=v_user
     AND created_at>now()-interval '24 hours')>=5 THEN RAISE EXCEPTION 'APPEAL_LIMIT'; END IF;
 INSERT INTO public.market_moderation_appeals(user_id,item_id,post_id,reason)
 VALUES(v_user,p_item,p_post,trim(p_reason)) RETURNING id INTO v_id;
 RETURN v_id;
END;
$appeal$;
REVOKE ALL ON FUNCTION public.request_market_content_review(uuid,uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.request_market_content_review(uuid,uuid,text) TO authenticated;

-- A separate moderation state distinguishes flagged items from ordinary seller
-- pauses. Only moderators can restore flagged content to recommendations.
ALTER TABLE public.market_items ADD COLUMN IF NOT EXISTS moderation_state text NOT NULL DEFAULT 'approved';
ALTER TABLE public.market_items ADD COLUMN IF NOT EXISTS video_url text;
ALTER TABLE public.market_items ADD COLUMN IF NOT EXISTS media_digest text;
DO $market_mod_constraints$
BEGIN
 ALTER TABLE public.market_items ADD CONSTRAINT market_moderation_state_check
  CHECK(moderation_state IN ('approved','flagged','rejected'));
EXCEPTION WHEN duplicate_object THEN NULL;
END;
$market_mod_constraints$;

CREATE OR REPLACE FUNCTION public.market_flag_unsafe_listing()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=public,pg_temp AS $market_safety$
DECLARE v_text text:=lower(coalesce(NEW.title,'')||' '||coalesce(NEW.description,''));
BEGIN
  -- High-confidence blocked content is quarantined for moderator review.
  -- This is defense in depth: additional image moderation and reporting remain necessary.
  IF v_text ~ '(illegal drug|counterfeit document|stolen account|weapon for sale|sale of ammunition|fake passport)'
  THEN NEW.moderation_state:='flagged'; NEW.status:='paused'; END IF;
  IF NEW.video_url IS NOT NULL AND (length(NEW.video_url)>600 OR NEW.video_url !~ '^https://')
  THEN RAISE EXCEPTION 'INVALID_MARKET_VIDEO'; END IF;
  -- Exact digest can be client-supplied and is not sufficient evidence for bans;
  -- similarity escalation requires a trusted server-computed media fingerprint.
  IF TG_OP='INSERT' AND NEW.media_digest IS NOT NULL AND
     NEW.media_digest ~ '^[0-9a-f]{64}$' AND EXISTS(
      SELECT 1 FROM public.market_items i
      WHERE i.seller_id=NEW.seller_id AND i.media_digest=NEW.media_digest
        AND i.created_at>now()-interval '30 days' AND i.status<>'removed'
     )
  THEN RAISE EXCEPTION 'DUPLICATE_MARKET_IMAGE'; END IF;
  RETURN NEW;
END;
$market_safety$;
DROP TRIGGER IF EXISTS market_flag_unsafe_listing_trigger ON public.market_items;
CREATE TRIGGER market_flag_unsafe_listing_trigger
BEFORE INSERT OR UPDATE OF title,description,video_url,media_digest ON public.market_items
FOR EACH ROW EXECUTE FUNCTION public.market_flag_unsafe_listing();

-- Preserve moderation outcome against modified client attempts.
CREATE OR REPLACE FUNCTION public.market_protect_moderation_state()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=public,pg_temp AS $moderation_guard$
BEGIN
 IF current_user IN ('authenticated','anon') THEN
  IF NEW.moderation_state IS DISTINCT FROM OLD.moderation_state AND
     NOT (NEW.moderation_state='flagged' AND OLD.moderation_state='approved') THEN
    RAISE EXCEPTION 'MODERATION_REVIEW_REQUIRED';
  END IF;
  IF OLD.moderation_state<>'approved' AND NEW.status='active' THEN
    RAISE EXCEPTION 'MODERATED_LISTING_NOT_PUBLIC';
  END IF;
 END IF;
 RETURN NEW;
END;
$moderation_guard$;
DROP TRIGGER IF EXISTS market_protect_moderation_state_trigger ON public.market_items;
CREATE TRIGGER market_protect_moderation_state_trigger
BEFORE UPDATE ON public.market_items
FOR EACH ROW EXECUTE FUNCTION public.market_protect_moderation_state();

-- Require moderation approval on every public discovery surface.
DROP POLICY IF EXISTS market_items_select_visible ON public.market_items;
CREATE POLICY market_items_select_visible ON public.market_items
FOR SELECT TO authenticated USING(
 seller_id=(select auth.uid())
 OR (
   coalesce(is_sold,false)=false AND status='active' AND moderation_state='approved'
   AND EXISTS(SELECT 1 FROM public.profiles p WHERE p.id=seller_id
       AND p.is_seller_active AND p.seller_status='active' AND p.is_verified
       AND upper(p.verification_badge) IN ('BLUE','GOLD')
       AND (p.verification_expires_at>now()
           OR (upper(p.verification_badge)='GOLD' AND p.verification_expires_at IS NULL)))
 ));

-- Only authenticated seller-owned videos, not user-editable full external URLs.
INSERT INTO storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
VALUES('market-video','market-video',true,52428800,ARRAY['video/mp4','video/webm'])
ON CONFLICT(id) DO UPDATE SET public=true,file_size_limit=excluded.file_size_limit,
  allowed_mime_types=excluded.allowed_mime_types;
DROP POLICY IF EXISTS market_video_insert_own ON storage.objects;
CREATE POLICY market_video_insert_own ON storage.objects FOR INSERT TO authenticated
 WITH CHECK(bucket_id='market-video' AND (storage.foldername(name))[1]='users'
   AND (storage.foldername(name))[2]=(select auth.uid())::text);
DROP POLICY IF EXISTS market_video_select_own ON storage.objects;
CREATE POLICY market_video_select_own ON storage.objects FOR SELECT TO authenticated
 USING(bucket_id='market-video' AND (storage.foldername(name))[1]='users'
   AND (storage.foldername(name))[2]=(select auth.uid())::text);
DROP POLICY IF EXISTS market_video_delete_own ON storage.objects;
CREATE POLICY market_video_delete_own ON storage.objects FOR DELETE TO authenticated
 USING(bucket_id='market-video' AND (storage.foldername(name))[1]='users'
   AND (storage.foldername(name))[2]=(select auth.uid())::text);
COMMIT;
