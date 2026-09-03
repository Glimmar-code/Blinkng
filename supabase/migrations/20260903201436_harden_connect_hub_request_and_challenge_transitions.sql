BEGIN;

CREATE OR REPLACE FUNCTION public.protect_connect_hub_request_updates()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_uid uuid := auth.uid();
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;

  IF TG_TABLE_NAME = 'mentor_requests' THEN
    IF NEW.requester_id IS DISTINCT FROM OLD.requester_id
       OR NEW.mentor_profile_id IS DISTINCT FROM OLD.mentor_profile_id THEN
      RAISE EXCEPTION 'REQUEST_OWNERSHIP_IMMUTABLE';
    END IF;

    IF OLD.requester_id = v_uid THEN
      IF NEW.status NOT IN (OLD.status, 'cancelled') THEN RAISE EXCEPTION 'REQUESTER_CAN_ONLY_CANCEL'; END IF;
    ELSIF EXISTS (
      SELECT 1 FROM public.mentor_profiles mp
      WHERE mp.id=OLD.mentor_profile_id AND mp.user_id=v_uid
    ) THEN
      IF NEW.status NOT IN (OLD.status, 'accepted', 'declined') THEN
        RAISE EXCEPTION 'INVALID_MENTOR_REQUEST_TRANSITION';
      END IF;
    ELSE
      RAISE EXCEPTION 'NOT_AUTHORIZED';
    END IF;

  ELSIF TG_TABLE_NAME = 'reading_mate_requests' THEN
    IF NEW.requester_id IS DISTINCT FROM OLD.requester_id
       OR NEW.reading_profile_id IS DISTINCT FROM OLD.reading_profile_id THEN
      RAISE EXCEPTION 'REQUEST_OWNERSHIP_IMMUTABLE';
    END IF;

    IF OLD.requester_id = v_uid THEN
      IF NEW.status NOT IN (OLD.status, 'cancelled') THEN RAISE EXCEPTION 'REQUESTER_CAN_ONLY_CANCEL'; END IF;
    ELSIF EXISTS (
      SELECT 1 FROM public.reading_mate_profiles rp
      WHERE rp.id=OLD.reading_profile_id AND rp.user_id=v_uid
    ) THEN
      IF NEW.status NOT IN (OLD.status, 'accepted', 'declined') THEN
        RAISE EXCEPTION 'INVALID_READING_REQUEST_TRANSITION';
      END IF;
    ELSE
      RAISE EXCEPTION 'NOT_AUTHORIZED';
    END IF;

  ELSIF TG_TABLE_NAME = 'housing_requests' THEN
    IF NEW.student_id IS DISTINCT FROM OLD.student_id THEN
      RAISE EXCEPTION 'REQUEST_OWNERSHIP_IMMUTABLE';
    END IF;
  END IF;

  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_mentor_request_updates ON public.mentor_requests;
CREATE TRIGGER trg_protect_mentor_request_updates
BEFORE UPDATE ON public.mentor_requests
FOR EACH ROW EXECUTE FUNCTION public.protect_connect_hub_request_updates();

DROP TRIGGER IF EXISTS trg_protect_reading_request_updates ON public.reading_mate_requests;
CREATE TRIGGER trg_protect_reading_request_updates
BEFORE UPDATE ON public.reading_mate_requests
FOR EACH ROW EXECUTE FUNCTION public.protect_connect_hub_request_updates();

DROP TRIGGER IF EXISTS trg_protect_housing_request_updates ON public.housing_requests;
CREATE TRIGGER trg_protect_housing_request_updates
BEFORE UPDATE ON public.housing_requests
FOR EACH ROW EXECUTE FUNCTION public.protect_connect_hub_request_updates();

REVOKE ALL ON FUNCTION public.protect_connect_hub_request_updates() FROM PUBLIC, anon, authenticated;

REVOKE UPDATE ON public.game_challenges FROM authenticated;

CREATE OR REPLACE FUNCTION public.respond_game_challenge(
  p_challenge_id uuid,
  p_accept boolean
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_uid uuid := auth.uid();
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;

  UPDATE public.game_challenges
  SET status=CASE WHEN p_accept THEN 'accepted' ELSE 'declined' END,
      accepted_at=CASE WHEN p_accept THEN now() ELSE accepted_at END
  WHERE id=p_challenge_id
    AND challenged_id=v_uid
    AND status='pending';

  IF NOT FOUND THEN RAISE EXCEPTION 'CHALLENGE_NOT_FOUND_OR_NOT_ALLOWED'; END IF;
  RETURN true;
END;
$$;

REVOKE ALL ON FUNCTION public.respond_game_challenge(uuid,boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.respond_game_challenge(uuid,boolean) TO authenticated;

CREATE OR REPLACE FUNCTION public.submit_game_challenge_score(
  p_challenge_id uuid,
  p_score integer
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_row public.game_challenges%rowtype;
  v_score integer := least(greatest(coalesce(p_score,0),0),500);
  v_winner uuid;
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'; END IF;

  SELECT * INTO v_row
  FROM public.game_challenges
  WHERE id=p_challenge_id
  FOR UPDATE;

  IF NOT FOUND OR v_row.status <> 'accepted' THEN RAISE EXCEPTION 'CHALLENGE_NOT_ACTIVE'; END IF;

  IF v_uid=v_row.challenger_id THEN
    UPDATE public.game_challenges SET challenger_score=v_score WHERE id=p_challenge_id;
  ELSIF v_uid=v_row.challenged_id THEN
    UPDATE public.game_challenges SET challenged_score=v_score WHERE id=p_challenge_id;
  ELSE
    RAISE EXCEPTION 'NOT_AUTHORIZED';
  END IF;

  SELECT * INTO v_row FROM public.game_challenges WHERE id=p_challenge_id;

  IF v_row.challenger_score IS NOT NULL AND v_row.challenged_score IS NOT NULL THEN
    v_winner := CASE
      WHEN v_row.challenger_score > v_row.challenged_score THEN v_row.challenger_id
      WHEN v_row.challenged_score > v_row.challenger_score THEN v_row.challenged_id
      ELSE NULL
    END;

    UPDATE public.game_challenges
    SET status='completed', winner_id=v_winner, completed_at=now()
    WHERE id=p_challenge_id;
  END IF;

  RETURN jsonb_build_object(
    'score',v_score,
    'completed',v_row.challenger_score IS NOT NULL AND v_row.challenged_score IS NOT NULL,
    'winnerId',v_winner
  );
END;
$$;

REVOKE ALL ON FUNCTION public.submit_game_challenge_score(uuid,integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.submit_game_challenge_score(uuid,integer) TO authenticated;

COMMIT;
