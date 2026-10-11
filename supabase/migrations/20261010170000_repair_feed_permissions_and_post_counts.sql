-- Testlab-first feed permission and active-post counter repair.
-- No posts are deleted, reactivated, hidden or otherwise modified.
-- Root cause: authenticated does not have USAGE on the private schema.
-- Keep private locked: the narrowly scoped public RPC runs as its trusted owner.
-- Rollback: ALTER FUNCTION public.get_feed_page(integer,timestamptz,uuid,text,uuid)
--   SECURITY INVOKER; DROP TRIGGER trg_blink_active_post_count ON public.feed_posts;
--   DROP FUNCTION public.sync_posts_count(); Stored posts_count may be recomputed
--   without touching feed_posts. Revoke no private-schema permissions.
CREATE OR REPLACE FUNCTION public.get_feed_page(
  p_limit integer DEFAULT 30,
  p_before timestamptz DEFAULT NULL,
  p_before_id uuid DEFAULT NULL,
  p_feed_type text DEFAULT 'posts',
  p_user_id uuid DEFAULT NULL
)
RETURNS SETOF public.feed_posts
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $sql$
  SELECT fp.*
  FROM public.feed_posts fp
  WHERE (SELECT auth.uid()) IS NOT NULL
    AND fp.is_active = true
    AND (p_user_id IS NULL OR fp.user_id = p_user_id)
    AND (
      p_before IS NULL
      OR fp.created_at < p_before
      OR (fp.created_at = p_before AND (p_before_id IS NULL OR fp.id < p_before_id))
    )
    AND (
      private.is_blink_owner_id(fp.user_id)
      OR NOT EXISTS (
        SELECT 1 FROM public.blocks b
        WHERE (b.blocker_id = (SELECT auth.uid()) AND b.blocked_id = fp.user_id)
           OR (b.blocker_id = fp.user_id AND b.blocked_id = (SELECT auth.uid()))
      )
    )
    AND (
      private.is_blink_owner_id(fp.user_id)
      OR NOT EXISTS (
        SELECT 1 FROM public.muted_users mu
        WHERE mu.user_id = (SELECT auth.uid()) AND mu.muted_id = fp.user_id
      )
    )
    AND (
      private.is_blink_owner_id(fp.user_id)
      OR NOT EXISTS (
        SELECT 1 FROM public.feed_preferences pref
        WHERE pref.user_id = (SELECT auth.uid())
          AND pref.post_id = fp.id AND pref.preference = 'not_interested'
      )
    )
    AND (
      CASE lower(coalesce(p_feed_type, 'posts'))
        WHEN 'reels' THEN coalesce(fp.is_reel, false) OR nullif(fp.video_url, '') IS NOT NULL
        WHEN 'following' THEN
          NOT (coalesce(fp.is_reel, false) OR nullif(fp.video_url, '') IS NOT NULL)
          AND (
            private.is_blink_owner_id(fp.user_id)
            OR EXISTS (
              SELECT 1 FROM public.follows f
              WHERE f.follower_id = (SELECT auth.uid()) AND f.following_id = fp.user_id
            )
          )
        WHEN 'all' THEN true
        ELSE NOT (coalesce(fp.is_reel, false) OR nullif(fp.video_url, '') IS NOT NULL)
      END
    )
  ORDER BY CASE WHEN private.is_blink_owner_id(fp.user_id) THEN 0 ELSE 1 END,
           fp.created_at DESC, fp.id DESC
  LIMIT greatest(1, least(coalesce(p_limit, 30), 60));
$sql$;

-- A SECURITY DEFINER endpoint must never be callable by anonymous clients.
REVOKE ALL ON FUNCTION public.get_feed_page(integer,timestamptz,uuid,text,uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_feed_page(integer,timestamptz,uuid,text,uuid)
  TO authenticated, service_role;

-- The old sync_posts_count() function existed, but had NO feed_posts trigger.
-- Update atomically so parallel post creation cannot overwrite a recomputed count.
-- Count ACTIVE posts and reels only; inactive historical empty rows stay inactive.
CREATE OR REPLACE FUNCTION public.sync_posts_count()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $fn$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.is_active THEN
      UPDATE public.profiles SET posts_count = posts_count + 1
      WHERE id = NEW.user_id;
    END IF;
    RETURN NEW;
  ELSIF TG_OP = 'DELETE' THEN
    IF OLD.is_active THEN
      UPDATE public.profiles SET posts_count = greatest(posts_count - 1, 0)
      WHERE id = OLD.user_id;
    END IF;
    RETURN OLD;
  END IF;

  -- UPDATE OF user_id/is_active: skip no-op changes and handle owner moves.
  IF OLD.user_id = NEW.user_id THEN
    IF OLD.is_active IS DISTINCT FROM NEW.is_active THEN
      UPDATE public.profiles
      SET posts_count = greatest(posts_count + CASE WHEN NEW.is_active THEN 1 ELSE -1 END, 0)
      WHERE id = NEW.user_id;
    END IF;
  ELSE
    IF OLD.is_active THEN
      UPDATE public.profiles SET posts_count = greatest(posts_count - 1, 0)
      WHERE id = OLD.user_id;
    END IF;
    IF NEW.is_active THEN
      UPDATE public.profiles SET posts_count = posts_count + 1 WHERE id = NEW.user_id;
    END IF;
  END IF;
  RETURN NEW;
END;
$fn$;

REVOKE ALL ON FUNCTION public.sync_posts_count() FROM PUBLIC, anon, authenticated;

DO $do$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger
    WHERE tgrelid = 'public.feed_posts'::regclass
      AND tgname = 'trg_blink_active_post_count'
      AND NOT tgisinternal
  ) THEN
    CREATE TRIGGER trg_blink_active_post_count
      AFTER INSERT OR DELETE OR UPDATE OF user_id, is_active
      ON public.feed_posts FOR EACH ROW
      EXECUTE FUNCTION public.sync_posts_count();
  END IF;
END;
$do$;

-- Repair all existing profiles from authoritative, currently active rows.
-- Reapply-safe; never touches content/media/visibility or profile updated_at.
UPDATE public.profiles p
SET posts_count = (
  SELECT count(*)::integer
  FROM public.feed_posts fp
  WHERE fp.user_id = p.id AND fp.is_active = true
)
WHERE p.posts_count IS DISTINCT FROM (
  SELECT count(*)::integer
  FROM public.feed_posts fp
  WHERE fp.user_id = p.id AND fp.is_active = true
);
