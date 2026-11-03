-- BLINK For You incorrectly offered only 4 posts because the ranking function
-- excludes all content older than seven days. Preserve ranking, moderation,
-- audience checks, boosts, and privacy; change ONLY the recency candidate window.
-- Weighted-decay scoring still ranks fresh content above older relevant posts.
-- Guard against silently editing an unexpected future revision of this function.
do $migration$
declare
  v_function regprocedure := 'private_ranking.get_discovery_feed(integer,integer,timestamp with time zone,numeric,numeric,numeric,numeric,numeric)'::regprocedure;
  v_source text := pg_get_functiondef(v_function);
  v_from text := ' > p_as_of - interval ''7 days''';
  v_to text := ' > p_as_of - interval ''90 days''';
begin
  if v_source is null or position(v_from in v_source) = 0 then
    raise exception 'DISCOVERY_WINDOW_SOURCE_CHANGED: inspect get_discovery_feed before updating it';
  end if;
  if position(v_to in v_source) > 0 then
    raise exception 'DISCOVERY_WINDOW_ALREADY_EXPANDED';
  end if;
  execute replace(v_source, v_from, v_to);
end;
$migration$;

-- The ranking RPC evaluates exclusions per candidate. Existing indexes start
-- on target_type so they cannot efficiently serve target_id + action lookups.
-- Concurrently updated posts are handled by the application's timeout fallback.
create index if not exists blink_drop_incentivized_target_action_idx
  on public.blink_drop_incentivized_interactions(target_id, action);
