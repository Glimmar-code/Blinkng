-- Smart Discovery v3: explicit tag searches gently teach the existing feed model.
-- Requires 20261010161600_blink_smart_tags.sql. Apply in staging first.
-- A user may contribute at most one interest signal for the same tag/hour.
-- No ranking score, wallet, XP, view multiplier, or private search log is exposed.
CREATE OR REPLACE FUNCTION public.blink_record_tag_search(p_tag text)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
    v_user uuid := (SELECT auth.uid());
    v_tag text := lower(trim(leading '#' from btrim(coalesce(p_tag, ''))));
BEGIN
    IF v_user IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING errcode = '42501';
    END IF;
    IF v_tag !~ '^[a-z0-9_]{2,32}$' THEN RETURN; END IF;

    INSERT INTO public.blink_tag_search_events(user_id, tag, searched_hour)
    VALUES (v_user, v_tag, date_trunc('hour', now()))
    ON CONFLICT DO NOTHING;

    -- PostgreSQL FOUND is true only if a genuinely new event was inserted.
    -- Searching the same tag repeatedly cannot repeatedly train the model.
    IF FOUND THEN
        PERFORM private_ranking.bump_interest(v_user, 'feed', 'tag', v_tag, 0.6);
        PERFORM private_ranking.refresh_discovery_interest_cache(v_user);
    END IF;
END;
$function$;
REVOKE ALL ON FUNCTION public.blink_record_tag_search(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.blink_record_tag_search(text) TO authenticated;
