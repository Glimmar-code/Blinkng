-- BLINK opt-in, server-authoritative social emails. STAGING FIRST.
-- Strictly does NOT change Supabase Auth signup confirmation policy or SMTP.
-- Requires migration 20261016093000_blink_essential_email_outbox.sql.

-- Separate opt-in settings: none of the 5 types send by default.
CREATE TABLE IF NOT EXISTS public.blink_social_email_preferences (
 user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid(),
 new_follower_enabled boolean NOT NULL DEFAULT false,
 unread_messages_enabled boolean NOT NULL DEFAULT false,
 post_engagement_enabled boolean NOT NULL DEFAULT false,
 reel_milestone_enabled boolean NOT NULL DEFAULT false,
 leaderboard_enabled boolean NOT NULL DEFAULT false,
 new_follower_since timestamptz,
 unread_messages_since timestamptz,
 post_engagement_since timestamptz,
 reel_milestone_since timestamptz,
 leaderboard_since timestamptz,
 updated_at timestamptz NOT NULL DEFAULT now()
);

-- Ignore any user-supplied timestamps and stamp consent on the server.
CREATE OR REPLACE FUNCTION private.blink_normalize_social_email_consent()
RETURNS trigger LANGUAGE plpgsql SET search_path = '' AS $$
BEGIN
 IF TG_OP = 'INSERT' THEN
   NEW.new_follower_since := CASE WHEN NEW.new_follower_enabled THEN now() END;
   NEW.unread_messages_since := CASE WHEN NEW.unread_messages_enabled THEN now() END;
   NEW.post_engagement_since := CASE WHEN NEW.post_engagement_enabled THEN now() END;
   NEW.reel_milestone_since := CASE WHEN NEW.reel_milestone_enabled THEN now() END;
   NEW.leaderboard_since := CASE WHEN NEW.leaderboard_enabled THEN now() END;
 ELSE
   NEW.new_follower_since := CASE WHEN NOT NEW.new_follower_enabled THEN NULL WHEN OLD.new_follower_enabled THEN OLD.new_follower_since ELSE now() END;
   NEW.unread_messages_since := CASE WHEN NOT NEW.unread_messages_enabled THEN NULL WHEN OLD.unread_messages_enabled THEN OLD.unread_messages_since ELSE now() END;
   NEW.post_engagement_since := CASE WHEN NOT NEW.post_engagement_enabled THEN NULL WHEN OLD.post_engagement_enabled THEN OLD.post_engagement_since ELSE now() END;
   NEW.reel_milestone_since := CASE WHEN NOT NEW.reel_milestone_enabled THEN NULL WHEN OLD.reel_milestone_enabled THEN OLD.reel_milestone_since ELSE now() END;
   NEW.leaderboard_since := CASE WHEN NOT NEW.leaderboard_enabled THEN NULL WHEN OLD.leaderboard_enabled THEN OLD.leaderboard_since ELSE now() END;
 END IF;
 NEW.updated_at := now();
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION private.blink_normalize_social_email_consent() FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS blink_stamp_social_email_consent ON public.blink_social_email_preferences;
CREATE TRIGGER blink_stamp_social_email_consent
BEFORE INSERT OR UPDATE ON public.blink_social_email_preferences
FOR EACH ROW EXECUTE FUNCTION private.blink_normalize_social_email_consent();

ALTER TABLE public.blink_social_email_preferences ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.blink_social_email_preferences FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.blink_social_email_preferences TO authenticated;
GRANT INSERT (user_id, new_follower_enabled, unread_messages_enabled,
 post_engagement_enabled, reel_milestone_enabled, leaderboard_enabled)
 ON public.blink_social_email_preferences TO authenticated;
GRANT UPDATE (user_id, new_follower_enabled, unread_messages_enabled, post_engagement_enabled,
 reel_milestone_enabled, leaderboard_enabled) ON public.blink_social_email_preferences TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.blink_social_email_preferences TO service_role;
DROP POLICY IF EXISTS blink_email_prefs_read_own ON public.blink_social_email_preferences;
CREATE POLICY blink_email_prefs_read_own ON public.blink_social_email_preferences
 FOR SELECT TO authenticated USING ((select auth.uid()) = user_id);
DROP POLICY IF EXISTS blink_email_prefs_insert_own ON public.blink_social_email_preferences;
CREATE POLICY blink_email_prefs_insert_own ON public.blink_social_email_preferences
 FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = user_id);
DROP POLICY IF EXISTS blink_email_prefs_update_own ON public.blink_social_email_preferences;
CREATE POLICY blink_email_prefs_update_own ON public.blink_social_email_preferences
 FOR UPDATE TO authenticated USING ((select auth.uid()) = user_id)
 WITH CHECK ((select auth.uid()) = user_id);

-- Atomic one-category update: never overwrite other flags from mobile or desktop.
-- Invoker RLS confines the write to the currently signed-in account.
CREATE OR REPLACE FUNCTION public.blink_set_social_email_preference(
 p_category text, p_enabled boolean
) RETURNS boolean
LANGUAGE plpgsql SECURITY INVOKER SET search_path = ''
AS $$
DECLARE v_user uuid := auth.uid();
BEGIN
 IF v_user IS NULL OR p_enabled IS NULL
   OR p_category NOT IN (
      'new_follower','unread_messages','post_engagement','reel_milestone','leaderboard'
   ) THEN
   RAISE EXCEPTION 'Invalid social email preference' USING ERRCODE='22023';
 END IF;

 INSERT INTO public.blink_social_email_preferences(user_id)
 VALUES(v_user) ON CONFLICT(user_id) DO NOTHING;
 UPDATE public.blink_social_email_preferences
 SET new_follower_enabled = CASE WHEN p_category='new_follower' THEN p_enabled ELSE new_follower_enabled END,
     unread_messages_enabled = CASE WHEN p_category='unread_messages' THEN p_enabled ELSE unread_messages_enabled END,
     post_engagement_enabled = CASE WHEN p_category='post_engagement' THEN p_enabled ELSE post_engagement_enabled END,
     reel_milestone_enabled = CASE WHEN p_category='reel_milestone' THEN p_enabled ELSE reel_milestone_enabled END,
     leaderboard_enabled = CASE WHEN p_category='leaderboard' THEN p_enabled ELSE leaderboard_enabled END
 WHERE user_id = v_user;
 RETURN FOUND;
END;
$$;
REVOKE ALL ON FUNCTION public.blink_set_social_email_preference(text,boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.blink_set_social_email_preference(text,boolean) TO authenticated;

-- Server-only counters prevent a client resetting digest cooldown/rank comparison.
CREATE TABLE IF NOT EXISTS public.blink_social_email_progress (
 user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
 kind text NOT NULL CHECK(kind IN('new_follower','unread_messages','post_engagement','reel_milestone','leaderboard')),
 last_queued_at timestamptz,
 previous_world_rank bigint,
 PRIMARY KEY(user_id, kind)
);
ALTER TABLE public.blink_social_email_progress ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.blink_social_email_progress FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.blink_social_email_progress TO service_role;

CREATE TABLE IF NOT EXISTS public.blink_reel_email_milestones (
 user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
 post_id uuid NOT NULL REFERENCES public.feed_posts(id) ON DELETE CASCADE,
 milestone integer NOT NULL CHECK(milestone IN (100, 500, 1000, 10000)),
 created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY (user_id, post_id, milestone)
);
ALTER TABLE public.blink_reel_email_milestones ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.blink_reel_email_milestones FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.blink_reel_email_milestones TO service_role;

-- Widen only the *still-staged* queue kind validation; never alter sender logic via clients.
ALTER TABLE public.blink_essential_email_outbox
 DROP CONSTRAINT IF EXISTS blink_essential_email_outbox_kind_check;
ALTER TABLE public.blink_essential_email_outbox
 ADD CONSTRAINT blink_essential_email_outbox_kind_check
 CHECK (kind IN (
 'welcome','new_device','suspicious_activity',
 'new_follower','unread_messages','post_engagement','reel_milestone','leaderboard'
 ));
CREATE INDEX IF NOT EXISTS blink_email_outbox_user_kind_recent_idx
 ON public.blink_essential_email_outbox(user_id, kind, created_at DESC);

-- Round-robin scans ensure accounts beyond the first page are eventually processed.
CREATE TABLE IF NOT EXISTS private.blink_social_email_scan_cursor (
 id smallint PRIMARY KEY DEFAULT 1 CHECK(id=1),
 last_user_id uuid
);
INSERT INTO private.blink_social_email_scan_cursor (id, last_user_id)
 VALUES(1,NULL) ON CONFLICT(id) DO NOTHING;
REVOKE ALL ON private.blink_social_email_scan_cursor FROM PUBLIC, anon, authenticated;
GRANT SELECT, UPDATE ON private.blink_social_email_scan_cursor TO service_role;

-- Called only from an INTERNAL, server-owned scheduling job. It is not
-- executable by app users; never enqueue based on client assertions.
CREATE OR REPLACE FUNCTION public.blink_queue_social_email_digests(p_limit integer DEFAULT 100)
RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $$
DECLARE
 r record;
 v_kind text;
 v_since timestamptz;
 v_last timestamptz;
 v_rank bigint;
 v_current_rank bigint;
 v_cursor uuid;
 v_final uuid;
 v_seen integer := 0;
 v_count bigint;
 v_milestone integer;
 v_reel record;
 v_queued integer := 0;
 v_key text;
 v_interval interval;
BEGIN
 IF auth.role() <> 'service_role' THEN
   RAISE EXCEPTION 'server role required' USING ERRCODE = '42501';
 END IF;
 SELECT last_user_id INTO v_cursor
 FROM private.blink_social_email_scan_cursor WHERE id=1 FOR UPDATE;
 FOR r IN
  SELECT p.*, u.email_notifs_enabled
  FROM public.blink_social_email_preferences p
  LEFT JOIN public.user_settings u ON u.user_id = p.user_id
  WHERE (u.email_notifs_enabled IS DISTINCT FROM false)
  AND (p.new_follower_enabled OR p.unread_messages_enabled OR p.post_engagement_enabled
       OR p.reel_milestone_enabled OR p.leaderboard_enabled)
  AND (v_cursor IS NULL OR p.user_id > v_cursor)
  ORDER BY p.user_id
  LIMIT least(greatest(p_limit, 0), 200)
 LOOP
  v_seen := v_seen + 1;
  v_final := r.user_id;
  FOREACH v_kind IN ARRAY ARRAY['new_follower','unread_messages','post_engagement','reel_milestone','leaderboard'] LOOP
   v_since := CASE v_kind
    WHEN 'new_follower' THEN CASE WHEN r.new_follower_enabled THEN r.new_follower_since END
    WHEN 'unread_messages' THEN CASE WHEN r.unread_messages_enabled THEN r.unread_messages_since END
    WHEN 'post_engagement' THEN CASE WHEN r.post_engagement_enabled THEN r.post_engagement_since END
    WHEN 'reel_milestone' THEN CASE WHEN r.reel_milestone_enabled THEN r.reel_milestone_since END
    WHEN 'leaderboard' THEN CASE WHEN r.leaderboard_enabled THEN r.leaderboard_since END END;
   IF v_since IS NULL THEN CONTINUE; END IF;
   v_interval := CASE WHEN v_kind IN ('post_engagement','leaderboard') THEN interval '7 days' ELSE interval '24 hours' END;
   SELECT last_queued_at, previous_world_rank INTO v_last, v_rank
   FROM public.blink_social_email_progress
   WHERE user_id = r.user_id AND kind = v_kind;

   -- After a new opt-in (or opt-out + opt-in), old progress is irrelevant.
   IF v_last IS NOT NULL AND v_last < v_since THEN
     v_last := NULL;
     v_rank := NULL;
   END IF;

   -- The queue only delivers important, new, opt-in events. Never replay old data.
   IF v_last IS NOT NULL AND now() - v_last < v_interval THEN CONTINUE; END IF;
   v_count := 0;
   v_current_rank := NULL;

   IF v_kind = 'new_follower' THEN
    SELECT count(*) INTO v_count
    FROM public.follows f
    WHERE f.following_id = r.user_id AND f.created_at >= greatest(v_since, coalesce(v_last,v_since))
      AND f.follower_id <> r.user_id;

   ELSIF v_kind = 'unread_messages' THEN
    SELECT count(DISTINCT cp.conversation_id) INTO v_count
    FROM public.conversation_participants cp
    JOIN public.messages m ON m.conversation_id = cp.conversation_id
    WHERE cp.user_id = r.user_id
      AND m.sender_id <> r.user_id AND coalesce(m.deleted_for_everyone,false) = false
      AND m.created_at >= v_since
      AND m.created_at > coalesce(cp.last_read_at, '-infinity'::timestamptz)
      AND m.created_at <= now() - interval '6 hours';

   ELSIF v_kind = 'post_engagement' THEN
    SELECT (
      (SELECT count(*) FROM public.post_likes l JOIN public.feed_posts fp ON fp.id=l.post_id
       WHERE fp.user_id=r.user_id AND fp.is_active=true AND coalesce(fp.is_flagged,false)=false
         AND l.user_id<>r.user_id AND l.created_at >= greatest(v_since,coalesce(v_last,v_since)))
      +
      (SELECT count(*) FROM public.comments c JOIN public.feed_posts fp ON fp.id=c.post_id
       WHERE fp.user_id=r.user_id AND fp.is_active=true AND coalesce(fp.is_flagged,false)=false
         AND c.author_id<>r.user_id AND c.created_at >= greatest(v_since,coalesce(v_last,v_since)))
    ) INTO v_count;
    IF v_count < 10 THEN v_count := 0; END IF;

   ELSIF v_kind = 'reel_milestone' THEN
    -- Server-recorded distinct viewers, not weighted/display views; only reels
    -- created *after* the user enabled milestone emails are considered.
    FOR v_reel IN
      SELECT fp.id AS post_id, count(DISTINCT pv.viewer_id) AS unique_viewers
      FROM public.feed_posts fp
      JOIN public.post_views pv ON pv.post_id = fp.id
      WHERE fp.user_id=r.user_id AND fp.is_active=true
        AND coalesce(fp.is_flagged,false)=false AND fp.is_reel=true
        AND fp.created_at >= v_since
        AND pv.viewer_id<>r.user_id
      GROUP BY fp.id
      HAVING count(DISTINCT pv.viewer_id) >= 100
      ORDER BY count(DISTINCT pv.viewer_id) DESC
      LIMIT 10
    LOOP
      v_milestone := CASE WHEN v_reel.unique_viewers>=10000 THEN 10000
                      WHEN v_reel.unique_viewers>=1000 THEN 1000
                      WHEN v_reel.unique_viewers>=500 THEN 500 ELSE 100 END;
      INSERT INTO public.blink_reel_email_milestones(user_id,post_id,milestone)
      VALUES (r.user_id,v_reel.post_id,v_milestone)
      ON CONFLICT DO NOTHING;
      IF FOUND THEN v_count := v_count+1; END IF;
    END LOOP;

   ELSIF v_kind = 'leaderboard' THEN
    SELECT ls.world_rank INTO v_current_rank
    FROM public.leaderboard_snapshots ls
    WHERE ls.user_id=r.user_id LIMIT 1;
    IF v_current_rank IS NOT NULL AND v_rank IS NOT NULL
       AND v_current_rank <> v_rank THEN
      v_count := 1;
    END IF;
   END IF;

   -- For a leaderboard first opt-in, record baseline but do not email.
   IF v_kind = 'leaderboard' THEN
     INSERT INTO public.blink_social_email_progress(user_id,kind,previous_world_rank)
     VALUES(r.user_id,v_kind,v_current_rank)
     ON CONFLICT(user_id,kind) DO UPDATE
      SET previous_world_rank=excluded.previous_world_rank;
   END IF;

   IF v_count > 0 THEN
     -- Every category has a stable per-window event key; no mass mail on retries.
     v_key := v_kind || ':' || to_char(now() AT TIME ZONE 'UTC',
       CASE WHEN v_interval >= interval '7 days' THEN 'IYYY-IW' ELSE 'YYYY-MM-DD' END);
     INSERT INTO public.blink_essential_email_outbox(user_id,kind,event_key,priority)
      VALUES(r.user_id,v_kind,v_key,CASE WHEN v_kind='unread_messages' THEN 8 ELSE 9 END)
      ON CONFLICT(user_id,kind,event_key) DO NOTHING;
     IF FOUND THEN
       v_queued := v_queued + 1;
       INSERT INTO public.blink_social_email_progress(user_id,kind,last_queued_at)
       VALUES(r.user_id,v_kind,now())
       ON CONFLICT(user_id,kind) DO UPDATE
       SET last_queued_at=excluded.last_queued_at;
     END IF;
   END IF;
  END LOOP;
 END LOOP;
 IF v_seen = 0 THEN
   -- Reached the end of the current sweep. The next run starts again at the
   -- first user; this is not a failure or an invitation to send duplicates.
   UPDATE private.blink_social_email_scan_cursor SET last_user_id=NULL WHERE id=1;
 ELSE
   UPDATE private.blink_social_email_scan_cursor SET last_user_id=v_final WHERE id=1;
 END IF;
 RETURN v_queued;
END;
$$;
REVOKE ALL ON FUNCTION public.blink_queue_social_email_digests(integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.blink_queue_social_email_digests(integer) TO service_role;

COMMENT ON TABLE public.blink_social_email_preferences IS
 'Separate opt-in flags, default OFF, for five BLINK social email digests. Never used for signup verification.';
