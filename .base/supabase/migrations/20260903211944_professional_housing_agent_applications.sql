
BEGIN;

CREATE TABLE IF NOT EXISTS public.housing_request_applications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  housing_request_id uuid NOT NULL REFERENCES public.housing_requests(id) ON DELETE CASCADE,
  agent_profile_id uuid NOT NULL REFERENCES public.housing_agent_profiles(id) ON DELETE CASCADE,
  message text,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','declined','cancelled')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS housing_request_applications_pending_uidx
ON public.housing_request_applications(housing_request_id, agent_profile_id)
WHERE status='pending';

CREATE INDEX IF NOT EXISTS housing_request_applications_request_idx
ON public.housing_request_applications(housing_request_id, created_at DESC);

CREATE INDEX IF NOT EXISTS housing_request_applications_agent_idx
ON public.housing_request_applications(agent_profile_id, created_at DESC);

ALTER TABLE public.housing_request_applications ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.housing_request_applications FROM anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.housing_request_applications TO authenticated;

DROP POLICY IF EXISTS housing_applications_read_participants ON public.housing_request_applications;
CREATE POLICY housing_applications_read_participants
ON public.housing_request_applications
FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.housing_requests hr
    WHERE hr.id = housing_request_id
      AND hr.student_id = (select auth.uid())
  )
  OR EXISTS (
    SELECT 1 FROM public.housing_agent_profiles ha
    WHERE ha.id = agent_profile_id
      AND ha.user_id = (select auth.uid())
  )
);

DROP POLICY IF EXISTS housing_applications_insert_verified_agent ON public.housing_request_applications;
CREATE POLICY housing_applications_insert_verified_agent
ON public.housing_request_applications
FOR INSERT TO authenticated
WITH CHECK (
  EXISTS (
    SELECT 1 FROM public.housing_agent_profiles ha
    WHERE ha.id = agent_profile_id
      AND ha.user_id = (select auth.uid())
      AND ha.is_verified
      AND ha.is_active
  )
  AND EXISTS (
    SELECT 1 FROM public.housing_requests hr
    WHERE hr.id = housing_request_id
      AND hr.student_id <> (select auth.uid())
      AND hr.status='open'
  )
);

DROP POLICY IF EXISTS housing_applications_update_participants ON public.housing_request_applications;
CREATE POLICY housing_applications_update_participants
ON public.housing_request_applications
FOR UPDATE TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.housing_requests hr
    WHERE hr.id = housing_request_id
      AND hr.student_id = (select auth.uid())
  )
  OR EXISTS (
    SELECT 1 FROM public.housing_agent_profiles ha
    WHERE ha.id = agent_profile_id
      AND ha.user_id = (select auth.uid())
  )
)
WITH CHECK (
  EXISTS (
    SELECT 1 FROM public.housing_requests hr
    WHERE hr.id = housing_request_id
      AND hr.student_id = (select auth.uid())
  )
  OR EXISTS (
    SELECT 1 FROM public.housing_agent_profiles ha
    WHERE ha.id = agent_profile_id
      AND ha.user_id = (select auth.uid())
  )
);

CREATE OR REPLACE FUNCTION public.apply_to_housing_request(
  p_housing_request_id uuid,
  p_message text DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path=public,pg_temp
AS $$
DECLARE
  v_agent uuid;
  v_id uuid;
BEGIN
  SELECT id INTO v_agent
  FROM public.housing_agent_profiles
  WHERE user_id=(select auth.uid())
    AND is_verified=true
    AND is_active=true
  LIMIT 1;

  IF v_agent IS NULL THEN
    RAISE EXCEPTION 'VERIFIED_AGENT_REQUIRED';
  END IF;

  INSERT INTO public.housing_request_applications(
    housing_request_id, agent_profile_id, message
  )
  VALUES(p_housing_request_id,v_agent,nullif(trim(coalesce(p_message,'')),''))
  ON CONFLICT (housing_request_id,agent_profile_id) WHERE status='pending'
  DO UPDATE SET message=excluded.message,updated_at=now()
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION public.apply_to_housing_request(uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.apply_to_housing_request(uuid,text) TO authenticated;

CREATE OR REPLACE FUNCTION public.protect_housing_application_updates()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path=public,pg_temp
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_student uuid;
  v_agent_user uuid;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;

  IF NEW.housing_request_id IS DISTINCT FROM OLD.housing_request_id
     OR NEW.agent_profile_id IS DISTINCT FROM OLD.agent_profile_id THEN
    RAISE EXCEPTION 'REQUEST_OWNERSHIP_IMMUTABLE';
  END IF;

  SELECT student_id INTO v_student
  FROM public.housing_requests WHERE id=OLD.housing_request_id;
  SELECT user_id INTO v_agent_user
  FROM public.housing_agent_profiles WHERE id=OLD.agent_profile_id;

  IF v_uid=v_agent_user THEN
    IF NEW.status NOT IN (OLD.status,'cancelled') THEN
      RAISE EXCEPTION 'AGENT_CAN_ONLY_CANCEL';
    END IF;
  ELSIF v_uid=v_student THEN
    IF NEW.status NOT IN (OLD.status,'accepted','declined') THEN
      RAISE EXCEPTION 'INVALID_HOUSING_APPLICATION_TRANSITION';
    END IF;
  ELSE
    RAISE EXCEPTION 'NOT_AUTHORIZED';
  END IF;

  NEW.updated_at=now();
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_housing_application_updates
ON public.housing_request_applications;
CREATE TRIGGER trg_protect_housing_application_updates
BEFORE UPDATE ON public.housing_request_applications
FOR EACH ROW EXECUTE FUNCTION public.protect_housing_application_updates();

REVOKE ALL ON FUNCTION public.protect_housing_application_updates() FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.respond_housing_application(
  p_application_id uuid,
  p_accept boolean
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path=public,pg_temp
AS $$
DECLARE
  v_request uuid;
  v_agent uuid;
BEGIN
  SELECT a.housing_request_id,a.agent_profile_id
  INTO v_request,v_agent
  FROM public.housing_request_applications a
  JOIN public.housing_requests hr ON hr.id=a.housing_request_id
  WHERE a.id=p_application_id
    AND hr.student_id=(select auth.uid())
    AND a.status='pending'
  FOR UPDATE OF a;

  IF v_request IS NULL THEN
    RAISE EXCEPTION 'APPLICATION_NOT_FOUND_OR_NOT_ALLOWED';
  END IF;

  UPDATE public.housing_request_applications
  SET status=CASE WHEN p_accept THEN 'accepted' ELSE 'declined' END,
      updated_at=now()
  WHERE id=p_application_id;

  IF p_accept THEN
    UPDATE public.housing_requests
    SET agent_id=v_agent,status='matched',updated_at=now()
    WHERE id=v_request;

    UPDATE public.housing_request_applications
    SET status='declined',updated_at=now()
    WHERE housing_request_id=v_request
      AND id<>p_application_id
      AND status='pending';
  END IF;

  RETURN true;
END;
$$;

REVOKE ALL ON FUNCTION public.respond_housing_application(uuid,boolean) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.respond_housing_application(uuid,boolean) TO authenticated;

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
SET search_path=public,pg_temp
AS $$
  SELECT * FROM (
    SELECT 'roommate'::text,ra.id,
      CASE WHEN ra.applicant_id=(select auth.uid()) THEN 'outgoing' ELSE 'incoming' END,
      ra.status,ra.roommate_profile_id,
      CASE WHEN ra.applicant_id=(select auth.uid()) THEN rp.user_id ELSE ra.applicant_id END,
      ra.created_at,coalesce(rp.title,'Roommate request')
    FROM public.roommate_applications ra
    JOIN public.roommate_profiles rp ON rp.id=ra.roommate_profile_id
    WHERE ra.applicant_id=(select auth.uid()) OR rp.user_id=(select auth.uid())

    UNION ALL
    SELECT 'mentor',mr.id,
      CASE WHEN mr.requester_id=(select auth.uid()) THEN 'outgoing' ELSE 'incoming' END,
      mr.status,mr.mentor_profile_id,
      CASE WHEN mr.requester_id=(select auth.uid()) THEN mp.user_id ELSE mr.requester_id END,
      mr.created_at,coalesce(mp.headline,'Mentor request')
    FROM public.mentor_requests mr
    JOIN public.mentor_profiles mp ON mp.id=mr.mentor_profile_id
    WHERE mr.requester_id=(select auth.uid()) OR mp.user_id=(select auth.uid())

    UNION ALL
    SELECT 'reading',rr.id,
      CASE WHEN rr.requester_id=(select auth.uid()) THEN 'outgoing' ELSE 'incoming' END,
      rr.status,rr.reading_profile_id,
      CASE WHEN rr.requester_id=(select auth.uid()) THEN rp.user_id ELSE rr.requester_id END,
      rr.created_at,'Reading mate request'
    FROM public.reading_mate_requests rr
    JOIN public.reading_mate_profiles rp ON rp.id=rr.reading_profile_id
    WHERE rr.requester_id=(select auth.uid()) OR rp.user_id=(select auth.uid())

    UNION ALL
    SELECT 'housing',ha.id,
      CASE WHEN agent.user_id=(select auth.uid()) THEN 'outgoing' ELSE 'incoming' END,
      ha.status,ha.housing_request_id,
      CASE WHEN agent.user_id=(select auth.uid()) THEN hr.student_id ELSE agent.user_id END,
      ha.created_at,coalesce(hr.title,'Housing request')
    FROM public.housing_request_applications ha
    JOIN public.housing_requests hr ON hr.id=ha.housing_request_id
    JOIN public.housing_agent_profiles agent ON agent.id=ha.agent_profile_id
    WHERE agent.user_id=(select auth.uid()) OR hr.student_id=(select auth.uid())

    UNION ALL
    SELECT 'game',gc.id,
      CASE WHEN gc.challenger_id=(select auth.uid()) THEN 'outgoing' ELSE 'incoming' END,
      gc.status,NULL::uuid,
      CASE WHEN gc.challenger_id=(select auth.uid()) THEN gc.challenged_id ELSE gc.challenger_id END,
      gc.created_at,initcap(gc.game_type)||' challenge'
    FROM public.game_challenges gc
    WHERE gc.challenger_id=(select auth.uid()) OR gc.challenged_id=(select auth.uid())
  ) q
  ORDER BY created_at DESC
  LIMIT greatest(1,least(coalesce(p_limit,100),200));
$$;

REVOKE ALL ON FUNCTION public.get_connect_request_inbox(integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.get_connect_request_inbox(integer) TO authenticated;

CREATE OR REPLACE FUNCTION public.respond_connect_request(
  p_kind text,
  p_request_id uuid,
  p_accept boolean
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path=public,pg_temp
AS $$
DECLARE v_kind text := lower(trim(coalesce(p_kind,'')));
BEGIN
  IF (select auth.uid()) IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;

  IF v_kind='roommate' THEN
    UPDATE public.roommate_applications ra
    SET status=CASE WHEN p_accept THEN 'accepted' ELSE 'declined' END,updated_at=now()
    WHERE ra.id=p_request_id
      AND EXISTS(SELECT 1 FROM public.roommate_profiles rp WHERE rp.id=ra.roommate_profile_id AND rp.user_id=(select auth.uid()))
      AND ra.status='pending';
  ELSIF v_kind='mentor' THEN
    UPDATE public.mentor_requests mr
    SET status=CASE WHEN p_accept THEN 'accepted' ELSE 'declined' END,updated_at=now()
    WHERE mr.id=p_request_id
      AND EXISTS(SELECT 1 FROM public.mentor_profiles mp WHERE mp.id=mr.mentor_profile_id AND mp.user_id=(select auth.uid()))
      AND mr.status='pending';
  ELSIF v_kind='reading' THEN
    UPDATE public.reading_mate_requests rr
    SET status=CASE WHEN p_accept THEN 'accepted' ELSE 'declined' END,updated_at=now()
    WHERE rr.id=p_request_id
      AND EXISTS(SELECT 1 FROM public.reading_mate_profiles rp WHERE rp.id=rr.reading_profile_id AND rp.user_id=(select auth.uid()))
      AND rr.status='pending';
  ELSIF v_kind='housing' THEN
    RETURN public.respond_housing_application(p_request_id,p_accept);
  ELSIF v_kind='game' THEN
    RETURN public.respond_game_challenge(p_request_id,p_accept);
  ELSE
    RAISE EXCEPTION 'UNSUPPORTED_REQUEST_KIND';
  END IF;

  IF NOT FOUND THEN RAISE EXCEPTION 'REQUEST_NOT_FOUND_OR_NOT_ALLOWED'; END IF;
  RETURN true;
END;
$$;

REVOKE ALL ON FUNCTION public.respond_connect_request(text,uuid,boolean) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.respond_connect_request(text,uuid,boolean) TO authenticated;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname='supabase_realtime'
      AND schemaname='public'
      AND tablename='housing_request_applications'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.housing_request_applications;
  END IF;
END $$;

COMMIT;
