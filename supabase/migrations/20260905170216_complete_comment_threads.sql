BEGIN;

-- Comments are always owned by the authenticated author and contain usable text.
ALTER TABLE public.comments
  ALTER COLUMN author_id SET DEFAULT auth.uid();

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conrelid = 'public.comments'::regclass
      AND conname = 'comments_content_length_check'
  ) THEN
    ALTER TABLE public.comments
      ADD CONSTRAINT comments_content_length_check
      CHECK (char_length(btrim(content)) BETWEEN 1 AND 2000)
      NOT VALID;
  END IF;
END;
$$;

ALTER TABLE public.comments
  VALIDATE CONSTRAINT comments_content_length_check;

CREATE OR REPLACE FUNCTION public.validate_comment_write()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE
  v_post_owner uuid;
  v_comments_allowed boolean;
  v_parent_post_id uuid;
  v_parent_parent_id uuid;
  v_parent_author_id uuid;
BEGIN
  NEW.content := btrim(NEW.content);

  IF char_length(NEW.content) NOT BETWEEN 1 AND 2000 THEN
    RAISE EXCEPTION 'VALIDATION_ERROR: comment must be between 1 and 2000 characters';
  END IF;

  IF TG_OP = 'UPDATE' THEN
    IF NEW.post_id IS DISTINCT FROM OLD.post_id
       OR NEW.author_id IS DISTINCT FROM OLD.author_id
       OR NEW.parent_comment_id IS DISTINCT FROM OLD.parent_comment_id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
      RAISE EXCEPTION 'VALIDATION_ERROR: comment ownership and thread cannot be changed';
    END IF;
    NEW.updated_at := now();
    RETURN NEW;
  END IF;

  SELECT fp.user_id, fp.allow_comments
    INTO v_post_owner, v_comments_allowed
  FROM public.feed_posts fp
  WHERE fp.id = NEW.post_id
    AND coalesce(fp.is_active, true) = true;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'POST_NOT_FOUND';
  END IF;
  IF NOT coalesce(v_comments_allowed, true) THEN
    RAISE EXCEPTION 'COMMENTS_DISABLED';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.blocks b
    WHERE (b.blocker_id = NEW.author_id AND b.blocked_id = v_post_owner)
       OR (b.blocker_id = v_post_owner AND b.blocked_id = NEW.author_id)
  ) THEN
    RAISE EXCEPTION 'INTERACTION_NOT_ALLOWED';
  END IF;

  IF NEW.parent_comment_id IS NOT NULL THEN
    SELECT c.post_id, c.parent_comment_id, c.author_id
      INTO v_parent_post_id, v_parent_parent_id, v_parent_author_id
    FROM public.comments c
    WHERE c.id = NEW.parent_comment_id;

    IF NOT FOUND OR v_parent_post_id <> NEW.post_id THEN
      RAISE EXCEPTION 'INVALID_PARENT_COMMENT';
    END IF;
    IF v_parent_parent_id IS NOT NULL THEN
      RAISE EXCEPTION 'INVALID_PARENT_COMMENT: replies must target a top-level comment';
    END IF;
    IF EXISTS (
      SELECT 1
      FROM public.blocks b
      WHERE (b.blocker_id = NEW.author_id AND b.blocked_id = v_parent_author_id)
         OR (b.blocker_id = v_parent_author_id AND b.blocked_id = NEW.author_id)
    ) THEN
      RAISE EXCEPTION 'INTERACTION_NOT_ALLOWED';
    END IF;
  END IF;

  IF (
    SELECT count(*)
    FROM public.comments c
    WHERE c.author_id = NEW.author_id
      AND c.created_at > now() - interval '1 minute'
  ) >= 20 THEN
    RAISE EXCEPTION 'RATE_LIMITED';
  END IF;

  NEW.updated_at := now();
  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS trg_validate_comment_write ON public.comments;
CREATE TRIGGER trg_validate_comment_write
BEFORE INSERT OR UPDATE ON public.comments
FOR EACH ROW EXECUTE FUNCTION public.validate_comment_write();

REVOKE ALL ON FUNCTION public.validate_comment_write() FROM PUBLIC, anon, authenticated;

-- Replace the accidentally-created FOR ALL policies with command-specific RLS.
ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS comments_select_all ON public.comments;
DROP POLICY IF EXISTS comments_insert_own ON public.comments;
DROP POLICY IF EXISTS comments_update_own ON public.comments;
DROP POLICY IF EXISTS comments_delete_own ON public.comments;

CREATE POLICY comments_select_all
ON public.comments
FOR SELECT
TO authenticated
USING (true);

CREATE POLICY comments_insert_own
ON public.comments
FOR INSERT
TO authenticated
WITH CHECK (author_id = (SELECT auth.uid()));

CREATE POLICY comments_update_own
ON public.comments
FOR UPDATE
TO authenticated
USING (author_id = (SELECT auth.uid()))
WITH CHECK (author_id = (SELECT auth.uid()));

CREATE POLICY comments_delete_own
ON public.comments
FOR DELETE
TO authenticated
USING (author_id = (SELECT auth.uid()));

REVOKE ALL ON TABLE public.comments FROM anon;
REVOKE UPDATE ON TABLE public.comments FROM authenticated;
GRANT SELECT, INSERT, DELETE ON TABLE public.comments TO authenticated;
GRANT UPDATE (content) ON TABLE public.comments TO authenticated;

-- One authenticated RPC returns the full thread with public profile identity and like state.
CREATE OR REPLACE FUNCTION public.get_post_comments(p_post_id uuid)
RETURNS TABLE (
  id uuid,
  post_id uuid,
  parent_comment_id uuid,
  author_id uuid,
  content text,
  likes_count integer,
  created_at timestamptz,
  username text,
  display_name text,
  avatar_url text,
  verification_badge text,
  is_liked boolean
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE
  v_actor uuid := auth.uid();
BEGIN
  IF v_actor IS NULL THEN
    RAISE EXCEPTION 'AUTHENTICATION_REQUIRED';
  END IF;

  RETURN QUERY
  SELECT
    c.id,
    c.post_id,
    c.parent_comment_id,
    c.author_id,
    c.content,
    c.likes_count,
    c.created_at,
    p.username,
    coalesce(
      nullif(btrim(p.full_name), ''),
      nullif(btrim(p.name), ''),
      nullif(btrim(p.username), ''),
      'Blink user'
    ) AS display_name,
    coalesce(p.avatar_url, '') AS avatar_url,
    CASE
      WHEN upper(coalesce(p.verification_badge, '')) IN ('BLUE', 'GOLD')
        THEN upper(p.verification_badge)
      WHEN coalesce(p.is_verified, false) THEN 'BLUE'
      ELSE 'NONE'
    END AS verification_badge,
    EXISTS (
      SELECT 1
      FROM public.comment_likes cl
      WHERE cl.comment_id = c.id
        AND cl.user_id = v_actor
    ) AS is_liked
  FROM public.comments c
  JOIN public.profiles p ON p.id = c.author_id
  WHERE c.post_id = p_post_id
    AND NOT EXISTS (
      SELECT 1
      FROM public.blocks b
      WHERE (b.blocker_id = v_actor AND b.blocked_id = c.author_id)
         OR (b.blocker_id = c.author_id AND b.blocked_id = v_actor)
    )
  ORDER BY c.created_at ASC, c.id ASC;
END;
$function$;

REVOKE ALL ON FUNCTION public.get_post_comments(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_post_comments(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.create_comment(
  p_post_id uuid,
  p_content text,
  p_parent_comment_id uuid DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE
  v_actor uuid := auth.uid();
  v_comment_id uuid;
BEGIN
  IF v_actor IS NULL THEN
    RAISE EXCEPTION 'AUTHENTICATION_REQUIRED';
  END IF;

  INSERT INTO public.comments (post_id, author_id, content, parent_comment_id)
  VALUES (p_post_id, v_actor, p_content, p_parent_comment_id)
  RETURNING comments.id INTO v_comment_id;

  RETURN v_comment_id;
END;
$function$;

REVOKE ALL ON FUNCTION public.create_comment(uuid, text, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_comment(uuid, text, uuid) TO authenticated;

-- Resolve @usernames server-side and emit durable mention notifications.
CREATE TABLE IF NOT EXISTS public.comment_mentions (
  comment_id uuid NOT NULL REFERENCES public.comments(id) ON DELETE CASCADE,
  mentioned_user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (comment_id, mentioned_user_id)
);

CREATE INDEX IF NOT EXISTS comment_mentions_recipient_created_idx
  ON public.comment_mentions (mentioned_user_id, created_at DESC);

ALTER TABLE public.comment_mentions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.comment_mentions FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.sync_comment_mentions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE
  v_mentioned_ids uuid[] := ARRAY[]::uuid[];
BEGIN
  WITH extracted AS (
    SELECT DISTINCT lower(captured.parts[2]) AS username
    FROM regexp_matches(
      NEW.content,
      '(^|[^[:alnum:]_.-])@([[:alnum:]_.-]{2,30})',
      'g'
    ) AS captured(parts)
  ), limited AS (
    SELECT e.username
    FROM extracted e
    ORDER BY e.username
    LIMIT 10
  )
  SELECT coalesce(array_agg(p.id), ARRAY[]::uuid[])
    INTO v_mentioned_ids
  FROM limited l
  JOIN public.profiles p
    ON lower(btrim(p.username)) = l.username
  WHERE p.id <> NEW.author_id
    AND NOT EXISTS (
      SELECT 1
      FROM public.blocks b
      WHERE (b.blocker_id = NEW.author_id AND b.blocked_id = p.id)
         OR (b.blocker_id = p.id AND b.blocked_id = NEW.author_id)
    );

  DELETE FROM public.comment_mentions cm
  WHERE cm.comment_id = NEW.id
    AND NOT (cm.mentioned_user_id = ANY (v_mentioned_ids));

  INSERT INTO public.comment_mentions (comment_id, mentioned_user_id)
  SELECT NEW.id, mentioned_id
  FROM unnest(v_mentioned_ids) AS mentioned_id
  ON CONFLICT (comment_id, mentioned_user_id) DO NOTHING;

  RETURN NEW;
END;
$function$;

-- Backfill mention relationships without creating old notifications.
WITH extracted AS (
  SELECT DISTINCT
    c.id AS comment_id,
    c.author_id,
    lower(captured.parts[2]) AS username
  FROM public.comments c
  CROSS JOIN LATERAL regexp_matches(
    c.content,
    '(^|[^[:alnum:]_.-])@([[:alnum:]_.-]{2,30})',
    'g'
  ) AS captured(parts)
), resolved AS (
  SELECT e.comment_id, p.id AS mentioned_user_id
  FROM extracted e
  JOIN public.profiles p ON lower(btrim(p.username)) = e.username
  WHERE p.id <> e.author_id
    AND NOT EXISTS (
      SELECT 1
      FROM public.blocks b
      WHERE (b.blocker_id = e.author_id AND b.blocked_id = p.id)
         OR (b.blocker_id = p.id AND b.blocked_id = e.author_id)
    )
)
INSERT INTO public.comment_mentions (comment_id, mentioned_user_id)
SELECT r.comment_id, r.mentioned_user_id
FROM resolved r
ON CONFLICT (comment_id, mentioned_user_id) DO NOTHING;

CREATE OR REPLACE FUNCTION public.activity_from_comment_mention()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE
  v_actor uuid;
  v_post_id uuid;
  v_parent_comment_id uuid;
  v_parent_author uuid;
  v_post_owner uuid;
BEGIN
  SELECT c.author_id, c.post_id, c.parent_comment_id, parent.author_id, fp.user_id
    INTO v_actor, v_post_id, v_parent_comment_id, v_parent_author, v_post_owner
  FROM public.comments c
  JOIN public.feed_posts fp ON fp.id = c.post_id
  LEFT JOIN public.comments parent ON parent.id = c.parent_comment_id
  WHERE c.id = NEW.comment_id;

  IF v_actor IS NULL OR NEW.mentioned_user_id = v_actor THEN
    RETURN NEW;
  END IF;

  -- A post comment or reply already creates the more specific notification.
  IF (v_parent_comment_id IS NULL AND NEW.mentioned_user_id = v_post_owner)
     OR (v_parent_comment_id IS NOT NULL AND NEW.mentioned_user_id = v_parent_author) THEN
    RETURN NEW;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.blocks b
    WHERE (b.blocker_id = v_actor AND b.blocked_id = NEW.mentioned_user_id)
       OR (b.blocker_id = NEW.mentioned_user_id AND b.blocked_id = v_actor)
  ) THEN
    RETURN NEW;
  END IF;

  INSERT INTO public.activities (
    recipient_id,
    actor_id,
    activity_type,
    entity_type,
    entity_id,
    message,
    is_read
  ) VALUES (
    NEW.mentioned_user_id,
    v_actor,
    'MENTION',
    'post',
    v_post_id,
    'mentioned you in a comment',
    false
  );

  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS trg_sync_comment_mentions ON public.comments;
CREATE TRIGGER trg_sync_comment_mentions
AFTER INSERT OR UPDATE OF content ON public.comments
FOR EACH ROW EXECUTE FUNCTION public.sync_comment_mentions();

DROP TRIGGER IF EXISTS trg_activity_comment_mention ON public.comment_mentions;
CREATE TRIGGER trg_activity_comment_mention
AFTER INSERT ON public.comment_mentions
FOR EACH ROW EXECUTE FUNCTION public.activity_from_comment_mention();

REVOKE ALL ON FUNCTION public.sync_comment_mentions() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.activity_from_comment_mention() FROM PUBLIC, anon, authenticated;

-- Replies notify the parent author; top-level comments notify the post author.
CREATE OR REPLACE FUNCTION public.activity_from_post_comment()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE
  v_recipient uuid;
  v_message text;
BEGIN
  IF NEW.parent_comment_id IS NULL THEN
    SELECT fp.user_id INTO v_recipient
    FROM public.feed_posts fp
    WHERE fp.id = NEW.post_id;
    v_message := 'commented on your post';
  ELSE
    SELECT c.author_id INTO v_recipient
    FROM public.comments c
    WHERE c.id = NEW.parent_comment_id;
    v_message := 'replied to your comment';
  END IF;

  IF v_recipient IS NOT NULL
     AND v_recipient <> NEW.author_id
     AND NOT EXISTS (
       SELECT 1
       FROM public.blocks b
       WHERE (b.blocker_id = NEW.author_id AND b.blocked_id = v_recipient)
          OR (b.blocker_id = v_recipient AND b.blocked_id = NEW.author_id)
     ) THEN
    INSERT INTO public.activities (
      recipient_id,
      actor_id,
      activity_type,
      entity_type,
      entity_id,
      message,
      is_read
    ) VALUES (
      v_recipient,
      NEW.author_id,
      CASE WHEN NEW.parent_comment_id IS NULL THEN 'COMMENT' ELSE 'REPLY' END,
      'post',
      NEW.post_id,
      v_message,
      false
    );
  END IF;

  RETURN NEW;
END;
$function$;

REVOKE ALL ON FUNCTION public.activity_from_post_comment() FROM PUBLIC, anon, authenticated;

-- Comment reports use the same moderated queue as post and account reports.
ALTER TABLE public.reports
  ADD COLUMN IF NOT EXISTS reported_comment_id uuid;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conrelid = 'public.reports'::regclass
      AND conname = 'reports_reported_comment_id_fkey'
  ) THEN
    ALTER TABLE public.reports
      ADD CONSTRAINT reports_reported_comment_id_fkey
      FOREIGN KEY (reported_comment_id)
      REFERENCES public.comments(id)
      ON DELETE CASCADE;
  END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS reports_reported_comment_id_idx
  ON public.reports (reported_comment_id);

CREATE UNIQUE INDEX IF NOT EXISTS reports_one_pending_comment_per_user_idx
  ON public.reports (reporter_id, reported_comment_id)
  WHERE reported_comment_id IS NOT NULL
    AND coalesce(status, 'pending') = 'pending';

CREATE OR REPLACE FUNCTION public.report_comment(p_comment_id uuid, p_reason text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE
  v_actor uuid := auth.uid();
  v_author uuid;
  v_existing uuid;
  v_id uuid;
BEGIN
  IF v_actor IS NULL THEN
    RAISE EXCEPTION 'AUTHENTICATION_REQUIRED';
  END IF;
  IF char_length(btrim(coalesce(p_reason, ''))) NOT BETWEEN 3 AND 500 THEN
    RAISE EXCEPTION 'VALIDATION_ERROR: reason must be between 3 and 500 characters';
  END IF;

  SELECT c.author_id INTO v_author
  FROM public.comments c
  WHERE c.id = p_comment_id;

  IF v_author IS NULL THEN
    RAISE EXCEPTION 'COMMENT_NOT_FOUND';
  END IF;
  IF v_author = v_actor THEN
    RAISE EXCEPTION 'VALIDATION_ERROR: cannot report your own comment';
  END IF;

  SELECT r.id INTO v_existing
  FROM public.reports r
  WHERE r.reporter_id = v_actor
    AND r.reported_comment_id = p_comment_id
    AND coalesce(r.status, 'pending') = 'pending'
  ORDER BY r.created_at DESC
  LIMIT 1;

  IF v_existing IS NOT NULL THEN
    RETURN v_existing;
  END IF;

  IF (
    SELECT count(*)
    FROM public.reports r
    WHERE r.reporter_id = v_actor
      AND r.created_at > now() - interval '1 hour'
  ) >= 10 THEN
    RAISE EXCEPTION 'RATE_LIMITED';
  END IF;

  INSERT INTO public.reports (
    reporter_id,
    reported_user_id,
    reported_comment_id,
    reason,
    status
  ) VALUES (
    v_actor,
    v_author,
    p_comment_id,
    btrim(p_reason),
    'pending'
  )
  RETURNING reports.id INTO v_id;

  RETURN v_id;
END;
$function$;

REVOKE ALL ON FUNCTION public.report_comment(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.report_comment(uuid, text) TO authenticated;

-- Reports must enter through validated RPCs.
REVOKE INSERT, UPDATE, DELETE ON TABLE public.reports FROM anon, authenticated;
GRANT SELECT ON TABLE public.reports TO authenticated;

-- Keep exactly one authoritative counter trigger for each interaction.
CREATE OR REPLACE FUNCTION public.sync_post_comment_count()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE
  v_post_id uuid := coalesce(NEW.post_id, OLD.post_id);
BEGIN
  UPDATE public.feed_posts fp
  SET comment_count = (
        SELECT count(*)
        FROM public.comments c
        WHERE c.post_id = v_post_id
      ),
      updated_at = now()
  WHERE fp.id = v_post_id;
  RETURN coalesce(NEW, OLD);
END;
$function$;

DROP TRIGGER IF EXISTS trg_post_comment_count ON public.comments;
DROP TRIGGER IF EXISTS trg_recalc_feed_comment_count ON public.comments;
DROP TRIGGER IF EXISTS trg_sync_post_comment_count ON public.comments;
CREATE TRIGGER trg_sync_post_comment_count
AFTER INSERT OR DELETE ON public.comments
FOR EACH ROW EXECUTE FUNCTION public.sync_post_comment_count();

CREATE OR REPLACE FUNCTION public.sync_comment_like_count()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public', 'pg_temp'
AS $function$
DECLARE
  v_comment_id uuid := coalesce(NEW.comment_id, OLD.comment_id);
BEGIN
  UPDATE public.comments c
  SET likes_count = (
        SELECT count(*)
        FROM public.comment_likes cl
        WHERE cl.comment_id = v_comment_id
      ),
      updated_at = now()
  WHERE c.id = v_comment_id;
  RETURN coalesce(NEW, OLD);
END;
$function$;

DROP TRIGGER IF EXISTS trg_comment_like_count ON public.comment_likes;
DROP TRIGGER IF EXISTS trg_recalc_comment_like_count ON public.comment_likes;
DROP TRIGGER IF EXISTS trg_sync_comment_like_count ON public.comment_likes;
CREATE TRIGGER trg_sync_comment_like_count
AFTER INSERT OR DELETE ON public.comment_likes
FOR EACH ROW EXECUTE FUNCTION public.sync_comment_like_count();

REVOKE ALL ON FUNCTION public.update_post_comment_count() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.recalc_feed_comment_count() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.sync_post_comment_count() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.update_comment_like_count() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.recalc_comment_like_count() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.sync_comment_like_count() FROM PUBLIC, anon, authenticated;

COMMIT;
