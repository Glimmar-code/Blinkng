-- BLINK Smart Discovery v3: stop truncating For You when creators are concentrated.
-- Testlab/preview FIRST. This migration does not modify posts, visibility or points.
--
-- Existing algorithm prevents >2 consecutive posts from the same creator.
-- Once other creators run out, its final "if not found then exit" terminates
-- despite other eligible content existing. The last-resort branch only relaxes
-- this diversity preference when all stricter alternatives have failed.
--
-- The live database already has a 90-day discovery window. A fresh/older
-- environment may still have the original 7-day window, so promote it safely
-- without replacing the rest of the ranking function or resetting its grants.
DO $blink_discovery_v3$
DECLARE
    v_function regprocedure := 'private_ranking.get_discovery_feed(integer,integer,timestamptz,numeric,numeric,numeric,numeric,numeric)'::regprocedure;
    v_source text;
    v_old text := 'if not found then exit; end if;';
    v_marker text := '-- BLINK_V3_EXHAUSTED_CREATOR_FALLBACK';
    v_replacement text := $fix$
    -- BLINK_V3_EXHAUSTED_CREATOR_FALLBACK
    if not found then
      -- Every strict choice exhausted: fill from other eligible candidates.
      -- This does NOT bypass candidate visibility, blocks, flags or audience.
      select c.* into v_pick
      from pg_temp.discovery_candidates c
      where not exists (
        select 1 from pg_temp.discovery_selected s where s.post_id = c.post_id
      )
      order by c.feed_score desc, c.created_at desc, c.post_id desc
      limit 1;
    end if;
    if not found then exit; end if;$fix$;
    v_window_old text := 'interval ''7 days''';
    v_window_new text := 'interval ''90 days''';
BEGIN
    SELECT pg_get_functiondef(v_function) INTO v_source;
    IF v_source IS NULL THEN RAISE EXCEPTION 'Discovery function missing'; END IF;

    -- Idempotent: avoid installing a second fallback.
    IF strpos(v_source, v_marker) > 0 THEN RETURN; END IF;

    IF length(v_source) - length(replace(v_source, v_old, '')) <> length(v_old) THEN
        RAISE EXCEPTION 'Discovery algorithm changed: exhaustion guard not uniquely found';
    END IF;
    v_source := replace(v_source, v_old, v_replacement);

    -- Older environments: extend candidate eligibility, not freshness score.
    -- Existing decay still prioritizes new content and interests.
    IF strpos(v_source, v_window_old) > 0 AND strpos(v_source, v_window_new) = 0 THEN
        IF length(v_source) - length(replace(v_source, v_window_old, '')) <> length(v_window_old) THEN
            RAISE EXCEPTION 'Unexpected discovery history window: manual review required';
        END IF;
        v_source := replace(v_source, v_window_old, v_window_new);
    END IF;

    -- Use the existing, reviewed function body. The only modification is its
    -- exhaustion fallback (and, for older environments, its history window).
    EXECUTE v_source;

    IF strpos(pg_get_functiondef(v_function), v_marker) = 0 THEN
        RAISE EXCEPTION 'Discovery backfill did not install';
    END IF;
END
$blink_discovery_v3$;
