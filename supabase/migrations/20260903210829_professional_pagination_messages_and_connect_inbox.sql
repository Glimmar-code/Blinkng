
BEGIN;

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA extensions;

CREATE INDEX IF NOT EXISTS feed_posts_page_idx
ON public.feed_posts (created_at DESC, id DESC)
WHERE is_active = true;

CREATE INDEX IF NOT EXISTS feed_posts_user_page_idx
ON public.feed_posts (user_id, created_at DESC, id DESC)
WHERE is_active = true;

CREATE INDEX IF NOT EXISTS messages_conversation_page_idx
ON public.messages (conversation_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS conversation_participants_user_conversation_idx
ON public.conversation_participants (user_id, conversation_id);

CREATE INDEX IF NOT EXISTS profiles_username_trgm_idx
ON public.profiles USING gin (lower(username) extensions.gin_trgm_ops);

CREATE INDEX IF NOT EXISTS profiles_full_name_trgm_idx
ON public.profiles USING gin (lower(full_name) extensions.gin_trgm_ops);

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
SET search_path = public, pg_temp
AS $$
  SELECT fp.*
  FROM public.feed_posts fp
  WHERE fp.is_active = true
    AND (
      p_before IS NULL
      OR fp.created_at < p_before
      OR (fp.created_at = p_before AND (p_before_id IS NULL OR fp.id < p_before_id))
    )
    AND (
      CASE lower(coalesce(p_feed_type,'posts'))
        WHEN 'reels' THEN coalesce(fp.is_reel,false) OR nullif(fp.video_url,'') IS NOT NULL
        WHEN 'following' THEN
          NOT (coalesce(fp.is_reel,false) OR nullif(fp.video_url,'') IS NOT NULL)
          AND EXISTS (
            SELECT 1
            FROM public.follows f
            WHERE f.follower_id = (select auth.uid())
              AND f.following_id = fp.user_id
          )
        WHEN 'all' THEN true
        ELSE NOT (coalesce(fp.is_reel,false) OR nullif(fp.video_url,'') IS NOT NULL)
      END
    )
  ORDER BY fp.created_at DESC, fp.id DESC
  LIMIT greatest(1, least(coalesce(p_limit,30), 60));
$$;

REVOKE ALL ON FUNCTION public.get_feed_page(integer,timestamptz,uuid,text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_feed_page(integer,timestamptz,uuid,text) TO authenticated;

CREATE OR REPLACE FUNCTION public.search_profiles_page(
  p_query text,
  p_limit integer DEFAULT 30,
  p_after_username text DEFAULT NULL,
  p_after_id uuid DEFAULT NULL
)
RETURNS SETOF public.profiles
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
  WITH q AS (
    SELECT lower(trim(coalesce(p_query,''))) AS term
  )
  SELECT p.*
  FROM public.profiles p, q
  WHERE q.term <> ''
    AND nullif(trim(p.username),'') IS NOT NULL
    AND (
      lower(coalesce(p.username,'')) LIKE '%' || q.term || '%'
      OR lower(coalesce(p.full_name,'')) LIKE '%' || q.term || '%'
      OR lower(coalesce(p.university,'')) LIKE '%' || q.term || '%'
      OR lower(coalesce(p.faculty,'')) LIKE '%' || q.term || '%'
      OR lower(coalesce(p.department,'')) LIKE '%' || q.term || '%'
    )
    AND (
      p_after_username IS NULL
      OR lower(p.username) > lower(p_after_username)
      OR (lower(p.username) = lower(p_after_username) AND (p_after_id IS NULL OR p.id > p_after_id))
    )
  ORDER BY lower(p.username), p.id
  LIMIT greatest(1, least(coalesce(p_limit,30), 60));
$$;

REVOKE ALL ON FUNCTION public.search_profiles_page(text,integer,text,uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.search_profiles_page(text,integer,text,uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.search_feed_page(
  p_query text,
  p_limit integer DEFAULT 30,
  p_before timestamptz DEFAULT NULL,
  p_before_id uuid DEFAULT NULL
)
RETURNS SETOF public.feed_posts
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
  WITH q AS (
    SELECT lower(trim(coalesce(p_query,''))) AS term
  )
  SELECT fp.*
  FROM public.feed_posts fp, q
  WHERE fp.is_active = true
    AND q.term <> ''
    AND (
      lower(coalesce(fp.text,'')) LIKE '%' || q.term || '%'
      OR lower(coalesce(fp.caption,'')) LIKE '%' || q.term || '%'
      OR lower(coalesce(fp.category,'')) LIKE '%' || q.term || '%'
      OR EXISTS (
        SELECT 1 FROM unnest(coalesce(fp.tags,'{}'::text[])) t
        WHERE lower(t) LIKE '%' || q.term || '%'
      )
    )
    AND (
      p_before IS NULL
      OR fp.created_at < p_before
      OR (fp.created_at = p_before AND (p_before_id IS NULL OR fp.id < p_before_id))
    )
  ORDER BY fp.created_at DESC, fp.id DESC
  LIMIT greatest(1, least(coalesce(p_limit,30), 60));
$$;

REVOKE ALL ON FUNCTION public.search_feed_page(text,integer,timestamptz,uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.search_feed_page(text,integer,timestamptz,uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.get_conversation_summaries(p_limit integer DEFAULT 50)
RETURNS TABLE(
  conversation_id uuid,
  partner_id uuid,
  partner_username text,
  partner_name text,
  partner_avatar text,
  partner_online boolean,
  partner_last_seen timestamptz,
  last_message text,
  last_message_at timestamptz,
  unread_count bigint
)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
  SELECT
    c.id,
    other.user_id,
    p.username,
    p.full_name,
    p.avatar_url,
    coalesce(p.online_now,p.is_online,false),
    coalesce(p.last_seen_at,p.last_seen),
    lm.content,
    lm.created_at,
    coalesce(uc.unread_count,0)
  FROM public.conversation_participants mine
  JOIN public.conversations c ON c.id = mine.conversation_id
  JOIN public.conversation_participants other
    ON other.conversation_id = c.id
   AND other.user_id <> (select auth.uid())
  LEFT JOIN public.profiles p ON p.id = other.user_id
  LEFT JOIN LATERAL (
    SELECT m.content, m.created_at
    FROM public.messages m
    WHERE m.conversation_id = c.id
      AND coalesce(m.deleted_for_everyone,false) = false
    ORDER BY m.created_at DESC, m.id DESC
    LIMIT 1
  ) lm ON true
  LEFT JOIN LATERAL (
    SELECT count(*) AS unread_count
    FROM public.messages m
    WHERE m.conversation_id = c.id
      AND m.sender_id <> (select auth.uid())
      AND coalesce(m.deleted_for_everyone,false) = false
      AND m.created_at > coalesce(mine.last_read_at,'epoch'::timestamptz)
  ) uc ON true
  WHERE mine.user_id = (select auth.uid())
  ORDER BY coalesce(lm.created_at,c.updated_at,c.created_at) DESC
  LIMIT greatest(1, least(coalesce(p_limit,50),100));
$$;

REVOKE ALL ON FUNCTION public.get_conversation_summaries(integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_conversation_summaries(integer) TO authenticated;

CREATE OR REPLACE FUNCTION public.get_conversation_messages_page(
  p_conversation_id uuid,
  p_limit integer DEFAULT 40,
  p_before timestamptz DEFAULT NULL,
  p_before_id uuid DEFAULT NULL
)
RETURNS SETOF public.messages
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
  SELECT m.*
  FROM public.messages m
  WHERE m.conversation_id = p_conversation_id
    AND coalesce(m.deleted_for_everyone,false) = false
    AND EXISTS (
      SELECT 1
      FROM public.conversation_participants cp
      WHERE cp.conversation_id = p_conversation_id
        AND cp.user_id = (select auth.uid())
    )
    AND (
      p_before IS NULL
      OR m.created_at < p_before
      OR (m.created_at = p_before AND (p_before_id IS NULL OR m.id < p_before_id))
    )
  ORDER BY m.created_at DESC, m.id DESC
  LIMIT greatest(1, least(coalesce(p_limit,40),100));
$$;

REVOKE ALL ON FUNCTION public.get_conversation_messages_page(uuid,integer,timestamptz,uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_conversation_messages_page(uuid,integer,timestamptz,uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.get_connect_request_inbox(p_limit integer DEFAULT 100)
RETURNS TABLE(
  kind text,
  request_id uuid,
  direction text,
  status text,
  listing_id uuid,
  other_user_id uuid,
  created_at timestamptz,
  title text
)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
  SELECT * FROM (
    SELECT
      'roommate'::text,
      ra.id,
      CASE WHEN ra.applicant_id=(select auth.uid()) THEN 'outgoing' ELSE 'incoming' END,
      ra.status,
      ra.roommate_profile_id,
      CASE WHEN ra.applicant_id=(select auth.uid()) THEN rp.user_id ELSE ra.applicant_id END,
      ra.created_at,
      coalesce(rp.title,'Roommate request')
    FROM public.roommate_applications ra
    JOIN public.roommate_profiles rp ON rp.id=ra.roommate_profile_id
    WHERE ra.applicant_id=(select auth.uid()) OR rp.user_id=(select auth.uid())

    UNION ALL

    SELECT
      'mentor',
      mr.id,
      CASE WHEN mr.requester_id=(select auth.uid()) THEN 'outgoing' ELSE 'incoming' END,
      mr.status,
      mr.mentor_profile_id,
      CASE WHEN mr.requester_id=(select auth.uid()) THEN mp.user_id ELSE mr.requester_id END,
      mr.created_at,
      coalesce(mp.headline,'Mentor request')
    FROM public.mentor_requests mr
    JOIN public.mentor_profiles mp ON mp.id=mr.mentor_profile_id
    WHERE mr.requester_id=(select auth.uid()) OR mp.user_id=(select auth.uid())

    UNION ALL

    SELECT
      'reading',
      rr.id,
      CASE WHEN rr.requester_id=(select auth.uid()) THEN 'outgoing' ELSE 'incoming' END,
      rr.status,
      rr.reading_profile_id,
      CASE WHEN rr.requester_id=(select auth.uid()) THEN rp.user_id ELSE rr.requester_id END,
      rr.created_at,
      'Reading mate request'
    FROM public.reading_mate_requests rr
    JOIN public.reading_mate_profiles rp ON rp.id=rr.reading_profile_id
    WHERE rr.requester_id=(select auth.uid()) OR rp.user_id=(select auth.uid())

    UNION ALL

    SELECT
      'game',
      gc.id,
      CASE WHEN gc.challenger_id=(select auth.uid()) THEN 'outgoing' ELSE 'incoming' END,
      gc.status,
      NULL::uuid,
      CASE WHEN gc.challenger_id=(select auth.uid()) THEN gc.challenged_id ELSE gc.challenger_id END,
      gc.created_at,
      initcap(gc.game_type) || ' challenge'
    FROM public.game_challenges gc
    WHERE gc.challenger_id=(select auth.uid()) OR gc.challenged_id=(select auth.uid())
  ) q
  ORDER BY created_at DESC
  LIMIT greatest(1, least(coalesce(p_limit,100),200));
$$;

REVOKE ALL ON FUNCTION public.get_connect_request_inbox(integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_connect_request_inbox(integer) TO authenticated;

CREATE OR REPLACE FUNCTION public.respond_connect_request(
  p_kind text,
  p_request_id uuid,
  p_accept boolean
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_kind text := lower(trim(coalesce(p_kind,'')));
BEGIN
  IF (select auth.uid()) IS NULL THEN
    RAISE EXCEPTION 'AUTHENTICATION_REQUIRED';
  END IF;

  IF v_kind='roommate' THEN
    UPDATE public.roommate_applications ra
    SET status=CASE WHEN p_accept THEN 'accepted' ELSE 'declined' END,
        updated_at=now()
    WHERE ra.id=p_request_id
      AND EXISTS (
        SELECT 1 FROM public.roommate_profiles rp
        WHERE rp.id=ra.roommate_profile_id
          AND rp.user_id=(select auth.uid())
      )
      AND ra.status='pending';
  ELSIF v_kind='mentor' THEN
    UPDATE public.mentor_requests mr
    SET status=CASE WHEN p_accept THEN 'accepted' ELSE 'declined' END,
        updated_at=now()
    WHERE mr.id=p_request_id
      AND EXISTS (
        SELECT 1 FROM public.mentor_profiles mp
        WHERE mp.id=mr.mentor_profile_id
          AND mp.user_id=(select auth.uid())
      )
      AND mr.status='pending';
  ELSIF v_kind='reading' THEN
    UPDATE public.reading_mate_requests rr
    SET status=CASE WHEN p_accept THEN 'accepted' ELSE 'declined' END,
        updated_at=now()
    WHERE rr.id=p_request_id
      AND EXISTS (
        SELECT 1 FROM public.reading_mate_profiles rp
        WHERE rp.id=rr.reading_profile_id
          AND rp.user_id=(select auth.uid())
      )
      AND rr.status='pending';
  ELSIF v_kind='game' THEN
    RETURN public.respond_game_challenge(p_request_id,p_accept);
  ELSE
    RAISE EXCEPTION 'UNSUPPORTED_REQUEST_KIND';
  END IF;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'REQUEST_NOT_FOUND_OR_NOT_ALLOWED';
  END IF;
  RETURN true;
END;
$$;

REVOKE ALL ON FUNCTION public.respond_connect_request(text,uuid,boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.respond_connect_request(text,uuid,boolean) TO authenticated;

COMMIT;
