-- BLINK For You: preserve weighted-decay ranking while removing the hard
-- seven-day discovery cutoff. Keep all visibility, block/mute, expiry and
-- personalization predicates exactly as they were.
--
-- Creator diversity is preferred, but a single prolific author must not make
-- discovery pagination stop when other eligible posts still remain.
--
-- Transform the existing production function instead of replacing its full
-- implementation: downstream rank/boost/view changes remain intact.
do $migration$
declare
  v_sql text;
  v_next text;
  v_old_cutoff constant text :=
    'and greatest(fp.created_at, coalesce(rr.created_at, fp.created_at)) > p_as_of - interval ''7 days''';
  v_old_end constant text := 'if not found then exit; end if;';
  v_fallback constant text := $fallback$
    -- BLINK_FOR_YOU_CREATOR_FALLBACK_V1: only relax the consecutive-creator
    -- preference when every diversity-preserving choice has been exhausted.
    if not found then
      select c.* into v_pick
      from pg_temp.discovery_candidates c
      where not exists(
        select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id
      )
      order by c.feed_score desc,c.created_at desc,c.post_id desc
      limit 1;
    end if;

    if not found then exit; end if;$fallback$;
begin
  select pg_get_functiondef(p.oid)
  into v_sql
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'private_ranking'
    and p.proname = 'get_discovery_feed'
    and pg_get_function_identity_arguments(p.oid) =
      'p_limit integer, p_offset integer, p_as_of timestamp with time zone, p_gravity numeric, p_w1 numeric, p_w2 numeric, p_w3 numeric, p_w4 numeric';

  if v_sql is null then
    raise exception 'For You migration: ranked discovery function signature not found';
  end if;

  v_next := v_sql;
  if position('BLINK_FOR_YOU_NO_AGE_CUTOFF_V1' in v_next) = 0 then
    if position(v_old_cutoff in v_next) = 0 then
      raise exception 'For You migration: expected seven-day eligibility predicate not found';
    end if;
    v_next := replace(
      v_next,
      v_old_cutoff,
      '-- BLINK_FOR_YOU_NO_AGE_CUTOFF_V1: ranking already decays with post age.'
    );
  end if;

  if position('BLINK_FOR_YOU_CREATOR_FALLBACK_V1' in v_next) = 0 then
    if position(v_old_end in v_next) = 0 then
      raise exception 'For You migration: creator diversity loop end not found';
    end if;
    v_next := replace(v_next, v_old_end, v_fallback);
  end if;

  if v_next <> v_sql then
    execute v_next;
  end if;
end;
$migration$;
