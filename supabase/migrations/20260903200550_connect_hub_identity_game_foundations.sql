BEGIN;

CREATE UNIQUE INDEX IF NOT EXISTS profiles_username_normalized_uidx
ON public.profiles ((lower(trim(username))))
WHERE trim(username) <> '';

CREATE OR REPLACE FUNCTION public.normalize_profile_username()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
BEGIN
  NEW.username := lower(trim(NEW.username));
  IF NEW.username = '' THEN
    RAISE EXCEPTION 'USERNAME_REQUIRED';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_normalize_profile_username ON public.profiles;
CREATE TRIGGER trg_normalize_profile_username
BEFORE INSERT OR UPDATE OF username ON public.profiles
FOR EACH ROW EXECUTE FUNCTION public.normalize_profile_username();

REVOKE ALL ON FUNCTION public.normalize_profile_username() FROM PUBLIC, anon, authenticated;

CREATE TABLE IF NOT EXISTS public.mentor_profiles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL UNIQUE REFERENCES public.profiles(id) ON DELETE CASCADE DEFAULT auth.uid(),
  mode text NOT NULL DEFAULT 'mentor' CHECK (mode IN ('mentor','mentee','both')),
  subjects text[] NOT NULL DEFAULT '{}',
  headline text,
  description text,
  preferred_level text,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.mentor_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  mentor_profile_id uuid NOT NULL REFERENCES public.mentor_profiles(id) ON DELETE CASCADE,
  requester_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE DEFAULT auth.uid(),
  message text,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','declined','cancelled')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS mentor_requests_pending_uidx
ON public.mentor_requests(mentor_profile_id, requester_id)
WHERE status = 'pending';
CREATE INDEX IF NOT EXISTS mentor_profiles_active_idx ON public.mentor_profiles(is_active, created_at DESC);
CREATE INDEX IF NOT EXISTS mentor_requests_requester_idx ON public.mentor_requests(requester_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.reading_mate_profiles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL UNIQUE REFERENCES public.profiles(id) ON DELETE CASCADE DEFAULT auth.uid(),
  courses text[] NOT NULL DEFAULT '{}',
  study_style text,
  preferred_times text[] NOT NULL DEFAULT '{}',
  preferred_location text,
  description text,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.reading_mate_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  reading_profile_id uuid NOT NULL REFERENCES public.reading_mate_profiles(id) ON DELETE CASCADE,
  requester_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE DEFAULT auth.uid(),
  message text,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','declined','cancelled')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS reading_mate_requests_pending_uidx
ON public.reading_mate_requests(reading_profile_id, requester_id)
WHERE status = 'pending';
CREATE INDEX IF NOT EXISTS reading_mate_profiles_active_idx ON public.reading_mate_profiles(is_active, created_at DESC);

CREATE TABLE IF NOT EXISTS public.housing_agent_profiles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL UNIQUE REFERENCES public.profiles(id) ON DELETE CASCADE DEFAULT auth.uid(),
  business_name text NOT NULL,
  service_areas text[] NOT NULL DEFAULT '{}',
  bio text,
  is_verified boolean NOT NULL DEFAULT false,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.housing_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  student_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE DEFAULT auth.uid(),
  agent_id uuid REFERENCES public.housing_agent_profiles(id) ON DELETE SET NULL,
  title text NOT NULL,
  preferred_location text,
  budget_min numeric CHECK (budget_min IS NULL OR budget_min >= 0),
  budget_max numeric CHECK (budget_max IS NULL OR budget_max >= 0),
  description text,
  status text NOT NULL DEFAULT 'open' CHECK (status IN ('open','applied','matched','closed','cancelled')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (budget_min IS NULL OR budget_max IS NULL OR budget_max >= budget_min)
);

CREATE INDEX IF NOT EXISTS housing_agent_profiles_active_idx ON public.housing_agent_profiles(is_active, is_verified, created_at DESC);
CREATE INDEX IF NOT EXISTS housing_requests_status_idx ON public.housing_requests(status, created_at DESC);
CREATE INDEX IF NOT EXISTS housing_requests_student_idx ON public.housing_requests(student_id, created_at DESC);
CREATE INDEX IF NOT EXISTS housing_requests_agent_idx ON public.housing_requests(agent_id, created_at DESC);

CREATE OR REPLACE FUNCTION public.prevent_agent_self_verification()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
BEGIN
  IF TG_OP = 'INSERT' AND NEW.is_verified THEN
    RAISE EXCEPTION 'AGENT_VERIFICATION_REQUIRES_REVIEW';
  END IF;
  IF TG_OP = 'UPDATE'
     AND NEW.is_verified IS DISTINCT FROM OLD.is_verified
     AND auth.uid() = OLD.user_id THEN
    RAISE EXCEPTION 'AGENT_VERIFICATION_REQUIRES_REVIEW';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_prevent_agent_self_verification ON public.housing_agent_profiles;
CREATE TRIGGER trg_prevent_agent_self_verification
BEFORE INSERT OR UPDATE ON public.housing_agent_profiles
FOR EACH ROW EXECUTE FUNCTION public.prevent_agent_self_verification();

REVOKE ALL ON FUNCTION public.prevent_agent_self_verification() FROM PUBLIC, anon, authenticated;

CREATE TABLE IF NOT EXISTS public.game_challenges (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  challenger_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE DEFAULT auth.uid(),
  challenged_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  game_type text NOT NULL CHECK (game_type IN ('trivia','math','logic','memory','speed')),
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','declined','completed','cancelled')),
  challenger_score integer CHECK (challenger_score IS NULL OR challenger_score >= 0),
  challenged_score integer CHECK (challenged_score IS NULL OR challenged_score >= 0),
  winner_id uuid REFERENCES public.profiles(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  accepted_at timestamptz,
  completed_at timestamptz,
  CHECK (challenger_id <> challenged_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS game_challenges_pending_uidx
ON public.game_challenges(
  least(challenger_id, challenged_id),
  greatest(challenger_id, challenged_id),
  game_type
)
WHERE status IN ('pending','accepted');
CREATE INDEX IF NOT EXISTS game_challenges_challenger_idx ON public.game_challenges(challenger_id, created_at DESC);
CREATE INDEX IF NOT EXISTS game_challenges_challenged_idx ON public.game_challenges(challenged_id, created_at DESC);

CREATE OR REPLACE FUNCTION public.get_connect_matches(p_limit integer DEFAULT 24)
RETURNS TABLE(
  id uuid,
  username text,
  full_name text,
  avatar_url text,
  university text,
  faculty text,
  department text,
  academic_level text,
  relationship_status text,
  online_now boolean,
  last_seen_at timestamptz,
  compatibility_score integer,
  common_skills text[],
  common_hobbies text[]
)
LANGUAGE sql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
  WITH me AS (
    SELECT * FROM public.profiles WHERE id = auth.uid()
  )
  SELECT
    p.id, p.username, p.full_name, p.avatar_url, p.university, p.faculty,
    p.department, p.academic_level, p.relationship_status::text,
    p.online_now, p.last_seen_at,
    least(100,
      (CASE WHEN nullif(me.university,'') IS NOT NULL AND lower(p.university)=lower(me.university) THEN 30 ELSE 0 END) +
      (CASE WHEN nullif(me.department,'') IS NOT NULL AND lower(p.department)=lower(me.department) THEN 25 ELSE 0 END) +
      (CASE WHEN nullif(me.faculty,'') IS NOT NULL AND lower(p.faculty)=lower(me.faculty) THEN 10 ELSE 0 END) +
      (CASE WHEN nullif(me.academic_level,'') IS NOT NULL AND lower(p.academic_level)=lower(me.academic_level) THEN 10 ELSE 0 END) +
      least(15, 5 * cardinality(ARRAY(SELECT unnest(coalesce(p.core_skills,'{}'::text[])) INTERSECT SELECT unnest(coalesce(me.core_skills,'{}'::text[]))))) +
      least(5, 2 * cardinality(ARRAY(SELECT unnest(coalesce(p.hobbies,'{}'::text[])) INTERSECT SELECT unnest(coalesce(me.hobbies,'{}'::text[]))))) +
      (CASE WHEN p.online_now THEN 5 ELSE 0 END)
    )::integer,
    ARRAY(SELECT unnest(coalesce(p.core_skills,'{}'::text[])) INTERSECT SELECT unnest(coalesce(me.core_skills,'{}'::text[]))),
    ARRAY(SELECT unnest(coalesce(p.hobbies,'{}'::text[])) INTERSECT SELECT unnest(coalesce(me.hobbies,'{}'::text[])))
  FROM public.profiles p
  CROSS JOIN me
  WHERE p.id <> auth.uid() AND nullif(trim(p.username),'') IS NOT NULL
  ORDER BY 12 DESC, p.online_now DESC, p.last_seen_at DESC
  LIMIT greatest(1, least(coalesce(p_limit,24), 50));
$$;

REVOKE ALL ON FUNCTION public.get_connect_matches(integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_connect_matches(integer) TO authenticated;

CREATE OR REPLACE FUNCTION public.record_game_session(
  p_game_type text,
  p_score integer,
  p_coins_earned integer DEFAULT 0
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_session_id uuid;
  v_score integer;
  v_coins integer;
  v_daily_points integer;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;
  IF p_game_type NOT IN ('trivia','math','logic','memory','speed') THEN RAISE EXCEPTION 'INVALID_GAME_TYPE'; END IF;
  IF p_score < 0 THEN RAISE EXCEPTION 'INVALID_SCORE'; END IF;

  v_score := least(p_score, 500);
  v_coins := least(30, floor(v_score / 20.0)::integer);

  SELECT coalesce(sum(score),0) INTO v_daily_points
  FROM public.game_sessions
  WHERE user_id=auth.uid() AND started_at>=date_trunc('day',now());

  IF v_daily_points + v_score > 5000 THEN RAISE EXCEPTION 'DAILY_GAME_SCORE_LIMIT_REACHED'; END IF;

  IF EXISTS (
    SELECT 1 FROM public.game_sessions
    WHERE user_id=auth.uid() AND started_at>now()-interval '3 seconds'
  ) THEN RAISE EXCEPTION 'GAME_SESSION_RATE_LIMITED'; END IF;

  INSERT INTO public.game_sessions(user_id,game_type,score,coins_earned,completed_at)
  VALUES(auth.uid(),p_game_type,v_score,v_coins,now())
  RETURNING id INTO v_session_id;

  INSERT INTO public.game_profiles(user_id,score,coins,updated_at)
  VALUES(auth.uid(),v_score,v_coins,now())
  ON CONFLICT(user_id) DO UPDATE SET
    score=public.game_profiles.score+excluded.score,
    coins=public.game_profiles.coins+excluded.coins,
    updated_at=now();

  UPDATE public.profiles
  SET points=coalesce(points,0)+v_score,updated_at=now()
  WHERE id=auth.uid();

  RETURN v_session_id;
END;
$$;

REVOKE ALL ON FUNCTION public.record_game_session(text,integer,integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.record_game_session(text,integer,integer) TO authenticated;

ALTER TABLE public.mentor_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mentor_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reading_mate_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reading_mate_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.housing_agent_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.housing_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.game_challenges ENABLE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.mentor_profiles TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.mentor_requests TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.reading_mate_profiles TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.reading_mate_requests TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.housing_agent_profiles TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.housing_requests TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.game_challenges TO authenticated;
REVOKE ALL ON public.mentor_profiles, public.mentor_requests, public.reading_mate_profiles,
  public.reading_mate_requests, public.housing_agent_profiles, public.housing_requests,
  public.game_challenges FROM anon;

DROP POLICY IF EXISTS mentor_profiles_read ON public.mentor_profiles;
CREATE POLICY mentor_profiles_read ON public.mentor_profiles FOR SELECT TO authenticated
USING (is_active OR user_id=(select auth.uid()));
DROP POLICY IF EXISTS mentor_profiles_insert_own ON public.mentor_profiles;
CREATE POLICY mentor_profiles_insert_own ON public.mentor_profiles FOR INSERT TO authenticated
WITH CHECK (user_id=(select auth.uid()));
DROP POLICY IF EXISTS mentor_profiles_update_own ON public.mentor_profiles;
CREATE POLICY mentor_profiles_update_own ON public.mentor_profiles FOR UPDATE TO authenticated
USING (user_id=(select auth.uid())) WITH CHECK (user_id=(select auth.uid()));
DROP POLICY IF EXISTS mentor_profiles_delete_own ON public.mentor_profiles;
CREATE POLICY mentor_profiles_delete_own ON public.mentor_profiles FOR DELETE TO authenticated
USING (user_id=(select auth.uid()));

DROP POLICY IF EXISTS mentor_requests_read_participants ON public.mentor_requests;
CREATE POLICY mentor_requests_read_participants ON public.mentor_requests FOR SELECT TO authenticated
USING (
  requester_id=(select auth.uid()) OR
  EXISTS (SELECT 1 FROM public.mentor_profiles mp WHERE mp.id=mentor_profile_id AND mp.user_id=(select auth.uid()))
);
DROP POLICY IF EXISTS mentor_requests_insert_own ON public.mentor_requests;
CREATE POLICY mentor_requests_insert_own ON public.mentor_requests FOR INSERT TO authenticated
WITH CHECK (
  requester_id=(select auth.uid()) AND
  EXISTS (SELECT 1 FROM public.mentor_profiles mp WHERE mp.id=mentor_profile_id AND mp.user_id<>(select auth.uid()) AND mp.is_active)
);
DROP POLICY IF EXISTS mentor_requests_update_participants ON public.mentor_requests;
CREATE POLICY mentor_requests_update_participants ON public.mentor_requests FOR UPDATE TO authenticated
USING (
  requester_id=(select auth.uid()) OR
  EXISTS (SELECT 1 FROM public.mentor_profiles mp WHERE mp.id=mentor_profile_id AND mp.user_id=(select auth.uid()))
)
WITH CHECK (
  requester_id=(select auth.uid()) OR
  EXISTS (SELECT 1 FROM public.mentor_profiles mp WHERE mp.id=mentor_profile_id AND mp.user_id=(select auth.uid()))
);

DROP POLICY IF EXISTS reading_profiles_read ON public.reading_mate_profiles;
CREATE POLICY reading_profiles_read ON public.reading_mate_profiles FOR SELECT TO authenticated
USING (is_active OR user_id=(select auth.uid()));
DROP POLICY IF EXISTS reading_profiles_insert_own ON public.reading_mate_profiles;
CREATE POLICY reading_profiles_insert_own ON public.reading_mate_profiles FOR INSERT TO authenticated
WITH CHECK (user_id=(select auth.uid()));
DROP POLICY IF EXISTS reading_profiles_update_own ON public.reading_mate_profiles;
CREATE POLICY reading_profiles_update_own ON public.reading_mate_profiles FOR UPDATE TO authenticated
USING (user_id=(select auth.uid())) WITH CHECK (user_id=(select auth.uid()));
DROP POLICY IF EXISTS reading_profiles_delete_own ON public.reading_mate_profiles;
CREATE POLICY reading_profiles_delete_own ON public.reading_mate_profiles FOR DELETE TO authenticated
USING (user_id=(select auth.uid()));

DROP POLICY IF EXISTS reading_requests_read_participants ON public.reading_mate_requests;
CREATE POLICY reading_requests_read_participants ON public.reading_mate_requests FOR SELECT TO authenticated
USING (
  requester_id=(select auth.uid()) OR
  EXISTS (SELECT 1 FROM public.reading_mate_profiles rp WHERE rp.id=reading_profile_id AND rp.user_id=(select auth.uid()))
);
DROP POLICY IF EXISTS reading_requests_insert_own ON public.reading_mate_requests;
CREATE POLICY reading_requests_insert_own ON public.reading_mate_requests FOR INSERT TO authenticated
WITH CHECK (
  requester_id=(select auth.uid()) AND
  EXISTS (SELECT 1 FROM public.reading_mate_profiles rp WHERE rp.id=reading_profile_id AND rp.user_id<>(select auth.uid()) AND rp.is_active)
);
DROP POLICY IF EXISTS reading_requests_update_participants ON public.reading_mate_requests;
CREATE POLICY reading_requests_update_participants ON public.reading_mate_requests FOR UPDATE TO authenticated
USING (
  requester_id=(select auth.uid()) OR
  EXISTS (SELECT 1 FROM public.reading_mate_profiles rp WHERE rp.id=reading_profile_id AND rp.user_id=(select auth.uid()))
)
WITH CHECK (
  requester_id=(select auth.uid()) OR
  EXISTS (SELECT 1 FROM public.reading_mate_profiles rp WHERE rp.id=reading_profile_id AND rp.user_id=(select auth.uid()))
);

DROP POLICY IF EXISTS housing_agents_read ON public.housing_agent_profiles;
CREATE POLICY housing_agents_read ON public.housing_agent_profiles FOR SELECT TO authenticated
USING ((is_active AND is_verified) OR user_id=(select auth.uid()));
DROP POLICY IF EXISTS housing_agents_insert_own ON public.housing_agent_profiles;
CREATE POLICY housing_agents_insert_own ON public.housing_agent_profiles FOR INSERT TO authenticated
WITH CHECK (user_id=(select auth.uid()) AND is_verified=false);
DROP POLICY IF EXISTS housing_agents_update_own ON public.housing_agent_profiles;
CREATE POLICY housing_agents_update_own ON public.housing_agent_profiles FOR UPDATE TO authenticated
USING (user_id=(select auth.uid())) WITH CHECK (user_id=(select auth.uid()));
DROP POLICY IF EXISTS housing_agents_delete_own ON public.housing_agent_profiles;
CREATE POLICY housing_agents_delete_own ON public.housing_agent_profiles FOR DELETE TO authenticated
USING (user_id=(select auth.uid()));

DROP POLICY IF EXISTS housing_requests_read ON public.housing_requests;
CREATE POLICY housing_requests_read ON public.housing_requests FOR SELECT TO authenticated
USING (
  status='open' OR student_id=(select auth.uid()) OR
  EXISTS (SELECT 1 FROM public.housing_agent_profiles a WHERE a.id=agent_id AND a.user_id=(select auth.uid()))
);
DROP POLICY IF EXISTS housing_requests_insert_own ON public.housing_requests;
CREATE POLICY housing_requests_insert_own ON public.housing_requests FOR INSERT TO authenticated
WITH CHECK (student_id=(select auth.uid()));
DROP POLICY IF EXISTS housing_requests_update_participants ON public.housing_requests;
CREATE POLICY housing_requests_update_participants ON public.housing_requests FOR UPDATE TO authenticated
USING (
  student_id=(select auth.uid()) OR
  EXISTS (SELECT 1 FROM public.housing_agent_profiles a WHERE a.id=agent_id AND a.user_id=(select auth.uid()) AND a.is_verified)
)
WITH CHECK (
  student_id=(select auth.uid()) OR
  EXISTS (SELECT 1 FROM public.housing_agent_profiles a WHERE a.id=agent_id AND a.user_id=(select auth.uid()) AND a.is_verified)
);

DROP POLICY IF EXISTS game_challenges_read_participants ON public.game_challenges;
CREATE POLICY game_challenges_read_participants ON public.game_challenges FOR SELECT TO authenticated
USING (challenger_id=(select auth.uid()) OR challenged_id=(select auth.uid()));
DROP POLICY IF EXISTS game_challenges_insert_own ON public.game_challenges;
CREATE POLICY game_challenges_insert_own ON public.game_challenges FOR INSERT TO authenticated
WITH CHECK (challenger_id=(select auth.uid()) AND challenged_id<>(select auth.uid()));
DROP POLICY IF EXISTS game_challenges_update_participants ON public.game_challenges;
CREATE POLICY game_challenges_update_participants ON public.game_challenges FOR UPDATE TO authenticated
USING (challenger_id=(select auth.uid()) OR challenged_id=(select auth.uid()))
WITH CHECK (challenger_id=(select auth.uid()) OR challenged_id=(select auth.uid()));
DROP POLICY IF EXISTS game_challenges_delete_own_pending ON public.game_challenges;
CREATE POLICY game_challenges_delete_own_pending ON public.game_challenges FOR DELETE TO authenticated
USING (challenger_id=(select auth.uid()) AND status='pending');

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='mentor_requests') THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.mentor_requests;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='reading_mate_requests') THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.reading_mate_requests;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='housing_requests') THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.housing_requests;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='game_challenges') THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.game_challenges;
  END IF;
END $$;

COMMIT;
