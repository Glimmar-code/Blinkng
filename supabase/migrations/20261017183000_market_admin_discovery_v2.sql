-- BLINK Market moderation desk + server-recommended discovery.
-- Rollback: revoke new RPC EXECUTE and remove moderation audit table after export.
BEGIN;
CREATE TABLE IF NOT EXISTS private.market_admin_action_log(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 actor_id uuid NOT NULL,
 target_kind text NOT NULL,
 target_id uuid NOT NULL,
 decision text NOT NULL,
 reason text NOT NULL DEFAULT '',
 created_at timestamptz NOT NULL DEFAULT now()
);
REVOKE ALL ON private.market_admin_action_log FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.admin_market_queue(p_limit integer DEFAULT 40)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,private,pg_temp AS $market_queue$
DECLARE v_actor uuid;
BEGIN
 v_actor:=private.require_blink_admin();
 RETURN jsonb_build_object(
  'sellers',(SELECT coalesce(jsonb_agg(to_jsonb(s)),'[]'::jsonb)
     FROM(SELECT a.user_id,p.seller_store_name,p.university,a.decision,
       a.created_at FROM private.market_seller_approvals a
       JOIN public.profiles p ON p.id=a.user_id
       WHERE a.decision='pending' ORDER BY a.created_at LIMIT least(greatest(p_limit,1),100)) s),
  'reports',(SELECT coalesce(jsonb_agg(to_jsonb(s)),'[]'::jsonb)
     FROM(SELECT r.id,r.item_id,r.reason,r.created_at
          FROM public.marketplace_reports r WHERE r.status='pending'
          ORDER BY r.created_at LIMIT least(greatest(p_limit,1),100)) s),
  'disputes',(SELECT coalesce(jsonb_agg(to_jsonb(s)),'[]'::jsonb)
     FROM(SELECT d.id,d.order_id,d.reason,d.status,d.created_at
       FROM public.market_disputes d WHERE d.status IN ('open','reviewing')
       ORDER BY d.created_at LIMIT least(greatest(p_limit,1),100)) s),
  'appeals',(SELECT coalesce(jsonb_agg(to_jsonb(s)),'[]'::jsonb)
     FROM(SELECT a.id,a.item_id,a.post_id,a.reason,a.created_at
       FROM public.market_moderation_appeals a WHERE a.status='pending'
       ORDER BY a.created_at LIMIT least(greatest(p_limit,1),100)) s)
 );
END;
$market_queue$;
REVOKE ALL ON FUNCTION public.admin_market_queue(integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.admin_market_queue(integer) TO authenticated;

CREATE OR REPLACE FUNCTION public.admin_market_review(
 p_kind text,p_id uuid,p_action text,p_reason text DEFAULT ''
) RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,private,pg_temp AS $market_review$
DECLARE v_actor uuid; v_recipient uuid; v_item uuid;v_new text;
BEGIN
 v_actor:=private.require_blink_admin();
 IF length(trim(coalesce(p_reason,'')))>1000 THEN RAISE EXCEPTION 'INVALID_REASON'; END IF;
 CASE p_kind
 WHEN 'seller' THEN
  IF p_action NOT IN('approved','rejected','revoked') THEN RAISE EXCEPTION 'INVALID_DECISION'; END IF;
  UPDATE private.market_seller_approvals SET decision=p_action,reason=trim(p_reason),
    reviewed_by=v_actor,reviewed_at=now() WHERE user_id=p_id RETURNING user_id INTO v_recipient;
  IF p_action='revoked' THEN
    UPDATE public.profiles SET seller_status='suspended',is_seller_active=false WHERE id=p_id;
  END IF;
 WHEN 'report' THEN
  IF p_action NOT IN('actioned','dismissed') THEN RAISE EXCEPTION 'INVALID_DECISION'; END IF;
  UPDATE public.marketplace_reports SET status=p_action WHERE id=p_id RETURNING item_id INTO v_item;
  IF p_action='actioned' THEN
    UPDATE public.market_items SET status='paused',moderation_state='flagged',updated_at=now() WHERE id=v_item;
  END IF;
  SELECT seller_id INTO v_recipient FROM public.market_items WHERE id=v_item;
 WHEN 'appeal' THEN
  IF p_action NOT IN('approved','rejected') THEN RAISE EXCEPTION 'INVALID_DECISION'; END IF;
  UPDATE public.market_moderation_appeals SET status=p_action,
    moderator_note=trim(p_reason),reviewed_by=v_actor,updated_at=now()
    WHERE id=p_id RETURNING user_id,item_id INTO v_recipient,v_item;
  IF p_action='approved' AND v_item IS NOT NULL THEN
    UPDATE public.market_items SET moderation_state='approved',status='paused',
      updated_at=now() WHERE id=v_item;
    -- Owner must explicitly reconfirm availability before reactivation.
  END IF;
 WHEN 'dispute' THEN
  IF p_action NOT IN('resolved','dismissed') THEN RAISE EXCEPTION 'INVALID_DECISION'; END IF;
  UPDATE public.market_disputes SET status=p_action,resolution=trim(p_reason),
      resolved_by=v_actor,updated_at=now() WHERE id=p_id RETURNING opened_by INTO v_recipient;
 ELSE RAISE EXCEPTION 'INVALID_REVIEW_KIND'; END CASE;
 IF v_recipient IS NULL THEN RAISE EXCEPTION 'MODERATION_ITEM_NOT_FOUND'; END IF;
 INSERT INTO private.market_admin_action_log(actor_id,target_kind,target_id,decision,reason)
   VALUES(v_actor,p_kind,p_id,p_action,trim(p_reason));
 INSERT INTO public.activities(recipient_id,actor_id,activity_type,entity_type,message,is_read)
 VALUES(v_recipient,NULL,'MARKET_MODERATION','market',
   'A Market '||p_kind||' review was updated: '||p_action,false);
 RETURN true;
END;
$market_review$;
REVOKE ALL ON FUNCTION public.admin_market_review(text,uuid,text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.admin_market_review(text,uuid,text,text) TO authenticated;

-- Ranking uses public, verifiable and capped inputs. This is a candidate
-- formula pending marketplace A/B validation, not a paid placement auction.
CREATE OR REPLACE FUNCTION public.discover_market_items_v2(
 p_query text DEFAULT '',p_university text DEFAULT NULL,p_category text DEFAULT NULL,
 p_limit integer DEFAULT 40,p_offset integer DEFAULT 0,
 p_min_price bigint DEFAULT NULL,p_max_price bigint DEFAULT NULL
) RETURNS SETOF public.market_items
LANGUAGE sql STABLE SECURITY INVOKER SET search_path=public,pg_temp AS $market_discovery$
 WITH scored AS (
  SELECT m.id,m.seller_id,
    (CASE WHEN p_university IS NOT NULL AND m.university=p_university THEN 30.0
          WHEN p_university IS NULL THEN 15.0 ELSE 0.0 END)
    + (CASE WHEN coalesce(array_length(m.image_urls,1),0)>=3 THEN 9.0
            WHEN m.image_url IS NOT NULL THEN 5.0 ELSE 0.0 END
       + least(10.0,length(coalesce(m.description,''))::numeric/60.0)
       + CASE WHEN m.condition IS NOT NULL AND m.price>0 AND length(m.title)>=8
              THEN 6.0 ELSE 0.0 END)
    + (least(20.0,
        least(coalesce(m.views_count,0),200)::numeric/30.0
        + least(coalesce(m.saves_count,0),100)::numeric/10.0))
    + greatest(0.0,15.0 - extract(epoch FROM (now()-m.created_at))/86400.0)
    + least(10.0,greatest(0.0,coalesce(m.seller_rating,0)::numeric*2.0))
    AS market_score
  FROM public.market_items m
  JOIN public.profiles p ON p.id=m.seller_id
  WHERE m.status='active' AND NOT coalesce(m.is_sold,false)
    AND m.moderation_state='approved'
    AND p.is_seller_active AND p.seller_status='active' AND p.is_verified
    AND upper(p.verification_badge) IN('BLUE','GOLD')
    AND (p.verification_expires_at>now()
         OR (upper(p.verification_badge)='GOLD' AND p.verification_expires_at IS NULL))
    AND (p_university IS NULL OR m.university=p_university)
    AND (p_category IS NULL OR m.category=p_category)
    AND (p_min_price IS NULL OR m.price>=p_min_price)
    AND (p_max_price IS NULL OR m.price<=p_max_price)
    AND (coalesce(trim(p_query),'')='' OR
         m.title ILIKE '%'||left(trim(p_query),80)||'%'
         OR m.description ILIKE '%'||left(trim(p_query),80)||'%'
         OR m.category ILIKE '%'||left(trim(p_query),80)||'%')
 ), fair AS (
  SELECT id,market_score,
     row_number() OVER(PARTITION BY seller_id ORDER BY market_score DESC,id DESC) AS seller_position
  FROM scored
 )
 SELECT m.* FROM fair f JOIN public.market_items m USING(id)
 WHERE f.seller_position<=3
 ORDER BY f.market_score DESC,m.created_at DESC,m.id DESC
 LIMIT greatest(1,least(p_limit,60)) OFFSET greatest(0,p_offset);
$market_discovery$;
REVOKE ALL ON FUNCTION public.discover_market_items_v2(text,text,text,integer,integer,bigint,bigint) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.discover_market_items_v2(text,text,text,integer,integer,bigint,bigint) TO authenticated;

-- Prevent client-forged trust rating on the very first insert.
CREATE OR REPLACE FUNCTION public.market_set_review_rating()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path=public,pg_temp AS $rating_guard$
DECLARE v_n integer;v_avg numeric;
BEGIN
 IF TG_OP='INSERT' THEN
  SELECT count(*)::integer,coalesce(avg(rating),0) INTO v_n,v_avg
   FROM public.marketplace_reviews WHERE reviewee_id=NEW.seller_id;
  NEW.seller_review_count:=v_n;
  NEW.seller_rating:=v_avg;
 END IF;
 RETURN NEW;
END;
$rating_guard$;
DROP TRIGGER IF EXISTS market_set_review_rating_trigger ON public.market_items;
CREATE TRIGGER market_set_review_rating_trigger BEFORE INSERT ON public.market_items
FOR EACH ROW EXECUTE FUNCTION public.market_set_review_rating();
COMMIT;
