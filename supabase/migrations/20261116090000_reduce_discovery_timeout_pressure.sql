-- Reduce discovery-feed scoring and personalization lookup pressure.
-- Safe additive indexes for the viewer/creator predicates used by the current
-- ranking function. Preserve all eligibility, privacy, scoring and monetization
-- behavior. This migration intentionally does not change candidate caps/weights.
begin;
set local lock_timeout = '5s';
set local statement_timeout = '90s';

create index if not exists blocks_blocked_blocker_idx
  on public.blocks (blocked_id, blocker_id);

create index if not exists post_views_viewer_post_last_viewed_idx
  on public.post_views (viewer_id, post_id, last_viewed_at desc);

create index if not exists post_likes_user_created_post_idx
  on public.post_likes (user_id, created_at desc, post_id);

create index if not exists post_bookmarks_user_created_post_idx
  on public.post_bookmarks (user_id, created_at desc, post_id);

create index if not exists comments_author_created_post_idx
  on public.comments (author_id, created_at desc, post_id);

-- The discovery RPC repeatedly selects the highest-ranked remaining candidate
-- for each position. Index its temporary candidate set once, then retain the
-- existing priority lanes and diversity rules. Guard the source rewrite so an
-- unexpected future function revision aborts rather than being overwritten.
do $migration$
declare
  v_oid regprocedure :=
    'private_ranking.get_discovery_feed(integer,integer,timestamptz,numeric,numeric,numeric,numeric,numeric)'::regprocedure;
  v_source text := pg_get_functiondef(v_oid);
  v_marker text := E'  for v_position in 1..v_target_count loop';
  v_rewrite text := E'  -- BLINK_TIMEOUT_RANKED_TEMP_INDEXES\n'
    || E'  create index discovery_candidates_score_idx\n'
    || E'    on pg_temp.discovery_candidates(feed_score desc, created_at desc, post_id desc);\n'
    || E'  create index discovery_candidates_lane_idx\n'
    || E'    on pg_temp.discovery_candidates(is_reel, creator_tier, feed_score desc, created_at desc, post_id desc);\n'
    || E'  analyze pg_temp.discovery_candidates;\n\n'
    || v_marker;
begin
  if v_source is null then
    raise exception 'DISCOVERY_FEED_FUNCTION_MISSING';
  end if;

  if position('BLINK_TIMEOUT_RANKED_TEMP_INDEXES' in v_source) > 0 then
    if position('discovery_candidates_score_idx' in v_source) = 0
       or position('discovery_candidates_lane_idx' in v_source) = 0 then
      raise exception 'DISCOVERY_TEMP_INDEX_MARKER_INCOMPLETE';
    end if;
    return;
  end if;

  if position(v_marker in v_source) = 0
     or length(v_source) - length(replace(v_source, v_marker, '')) <> length(v_marker) then
    raise exception 'DISCOVERY_FEED_SELECTION_SOURCE_DRIFT';
  end if;

  v_source := replace(v_source, v_marker, v_rewrite);
  execute v_source;
end;
$migration$;
commit;
