-- Applied to Supabase project jhwgifrlxwspoedxjaly.
-- Keep feed media canonical and prevent Android picker content:// URIs.
CREATE OR REPLACE FUNCTION public.normalize_feed_post_media()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.type = 'text' THEN
    NEW.image_url := NULL; NEW.video_url := NULL; NEW.images := '{}'::text[]; NEW.is_reel := false;
  ELSIF NEW.type = 'reel' THEN
    NEW.is_reel := true;
    IF NEW.video_url IS NULL OR NEW.video_url = '' OR NEW.video_url LIKE 'content:%' THEN
      RAISE EXCEPTION 'Reel posts require a Supabase Storage video URL';
    END IF;
    NEW.image_url := NULL; NEW.images := '{}'::text[];
  ELSE
    IF NEW.image_url LIKE 'content:%' OR EXISTS (SELECT 1 FROM unnest(COALESCE(NEW.images,'{}'::text[])) u WHERE u LIKE 'content:%') THEN
      NEW.image_url := NULL; NEW.images := '{}'::text[];
    END IF;
    IF NEW.video_url LIKE 'content:%' THEN NEW.video_url := NULL; END IF;
  END IF;
  RETURN NEW;
END; $$;
DROP TRIGGER IF EXISTS trg_normalize_feed_post_media ON public.feed_posts;
CREATE TRIGGER trg_normalize_feed_post_media BEFORE INSERT OR UPDATE ON public.feed_posts FOR EACH ROW EXECUTE FUNCTION public.normalize_feed_post_media();

DO $$ DECLARE r record; BEGIN
  FOR r IN SELECT * FROM (VALUES
    ('post_likes','post_id','feed_posts','id','post_likes_post_id_fkey'),('post_bookmarks','post_id','feed_posts','id','post_bookmarks_post_id_fkey'),('post_shares','post_id','feed_posts','id','post_shares_post_id_fkey'),('post_views','post_id','feed_posts','id','post_views_post_id_fkey'),('comment_likes','comment_id','comments','id','comment_likes_comment_id_fkey'),('story_views','story_id','stories','id','story_views_story_id_fkey'),('story_likes','story_id','stories','id','story_likes_story_id_fkey'),('story_reactions','story_id','stories','id','story_reactions_story_id_fkey'),('story_replies','story_id','stories','id','story_replies_story_id_fkey'),('stories','user_id','profiles','id','stories_user_id_fkey'),('market_items','seller_id','profiles','id','market_items_seller_id_fkey'),('marketplace_inquiries','item_id','market_items','id','marketplace_inquiries_item_id_fkey'),('marketplace_orders','item_id','market_items','id','marketplace_orders_item_id_fkey'),('polls','post_id','feed_posts','id','polls_post_id_fkey'),('profile_views','profile_id','profiles','id','profile_views_profile_id_fkey'),('profile_views','viewer_id','profiles','id','profile_views_viewer_id_fkey'),('point_transactions','user_id','profiles','id','point_transactions_user_id_fkey'),('reports','reported_user_id','profiles','id','reports_reported_user_id_fkey'),('reports','reported_post_id','feed_posts','id','reports_reported_post_id_fkey')
  ) v(table_name,column_name,ref_table,ref_column,constraint_name) LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname=r.constraint_name) THEN
      EXECUTE format('ALTER TABLE public.%I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES public.%I(%I) ON DELETE CASCADE',r.table_name,r.constraint_name,r.column_name,r.ref_table,r.ref_column);
    END IF;
  END LOOP;
END $$;

UPDATE public.feed_posts SET image_url=NULL, images='{}'::text[], type='text', is_reel=false, updated_at=now()
WHERE image_url LIKE 'content:%' OR image_url LIKE '%content%/media/picker%';
