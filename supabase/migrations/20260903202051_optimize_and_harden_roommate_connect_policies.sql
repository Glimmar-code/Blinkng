BEGIN;

CREATE INDEX IF NOT EXISTS game_challenges_winner_idx
ON public.game_challenges(winner_id);

CREATE INDEX IF NOT EXISTS reading_mate_requests_requester_idx
ON public.reading_mate_requests(requester_id, created_at DESC);

DROP POLICY IF EXISTS roommate_select_all ON public.roommate_profiles;
CREATE POLICY roommate_select_all ON public.roommate_profiles
FOR SELECT TO authenticated
USING (is_active = true OR user_id = (select auth.uid()));

DROP POLICY IF EXISTS roommate_insert_own ON public.roommate_profiles;
CREATE POLICY roommate_insert_own ON public.roommate_profiles
FOR INSERT TO authenticated
WITH CHECK (user_id = (select auth.uid()));

DROP POLICY IF EXISTS roommate_update_own ON public.roommate_profiles;
CREATE POLICY roommate_update_own ON public.roommate_profiles
FOR UPDATE TO authenticated
USING (user_id = (select auth.uid()))
WITH CHECK (user_id = (select auth.uid()));

DROP POLICY IF EXISTS roommate_delete_own ON public.roommate_profiles;
CREATE POLICY roommate_delete_own ON public.roommate_profiles
FOR DELETE TO authenticated
USING (user_id = (select auth.uid()));

DROP POLICY IF EXISTS roommate_app_select ON public.roommate_applications;
CREATE POLICY roommate_app_select ON public.roommate_applications
FOR SELECT TO authenticated
USING (
  applicant_id = (select auth.uid())
  OR EXISTS (
    SELECT 1 FROM public.roommate_profiles rp
    WHERE rp.id = roommate_profile_id
      AND rp.user_id = (select auth.uid())
  )
);

DROP POLICY IF EXISTS roommate_app_insert_own ON public.roommate_applications;
CREATE POLICY roommate_app_insert_own ON public.roommate_applications
FOR INSERT TO authenticated
WITH CHECK (
  applicant_id = (select auth.uid())
  AND EXISTS (
    SELECT 1 FROM public.roommate_profiles rp
    WHERE rp.id = roommate_profile_id
      AND rp.user_id <> (select auth.uid())
      AND rp.is_active
  )
);

DROP POLICY IF EXISTS roommate_app_update_owner ON public.roommate_applications;
CREATE POLICY roommate_app_update_owner ON public.roommate_applications
FOR UPDATE TO authenticated
USING (
  applicant_id = (select auth.uid())
  OR EXISTS (
    SELECT 1 FROM public.roommate_profiles rp
    WHERE rp.id = roommate_profile_id
      AND rp.user_id = (select auth.uid())
  )
)
WITH CHECK (
  applicant_id = (select auth.uid())
  OR EXISTS (
    SELECT 1 FROM public.roommate_profiles rp
    WHERE rp.id = roommate_profile_id
      AND rp.user_id = (select auth.uid())
  )
);

DROP POLICY IF EXISTS roommate_app_delete_own ON public.roommate_applications;
CREATE POLICY roommate_app_delete_own ON public.roommate_applications
FOR DELETE TO authenticated
USING (applicant_id = (select auth.uid()));

CREATE OR REPLACE FUNCTION public.protect_roommate_application_updates()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_uid uuid := auth.uid();
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'AUTHENTICATION_REQUIRED';
  END IF;

  IF NEW.applicant_id IS DISTINCT FROM OLD.applicant_id
     OR NEW.roommate_profile_id IS DISTINCT FROM OLD.roommate_profile_id THEN
    RAISE EXCEPTION 'REQUEST_OWNERSHIP_IMMUTABLE';
  END IF;

  IF OLD.applicant_id = v_uid THEN
    IF NEW.status NOT IN (OLD.status, 'cancelled') THEN
      RAISE EXCEPTION 'APPLICANT_CAN_ONLY_CANCEL';
    END IF;
  ELSIF EXISTS (
    SELECT 1 FROM public.roommate_profiles rp
    WHERE rp.id = OLD.roommate_profile_id
      AND rp.user_id = v_uid
  ) THEN
    IF NEW.status NOT IN (OLD.status, 'accepted', 'declined') THEN
      RAISE EXCEPTION 'INVALID_ROOMMATE_APPLICATION_TRANSITION';
    END IF;
  ELSE
    RAISE EXCEPTION 'NOT_AUTHORIZED';
  END IF;

  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_roommate_application_updates
ON public.roommate_applications;

CREATE TRIGGER trg_protect_roommate_application_updates
BEFORE UPDATE ON public.roommate_applications
FOR EACH ROW EXECUTE FUNCTION public.protect_roommate_application_updates();

REVOKE ALL ON FUNCTION public.protect_roommate_application_updates()
FROM PUBLIC, anon, authenticated;

COMMIT;
