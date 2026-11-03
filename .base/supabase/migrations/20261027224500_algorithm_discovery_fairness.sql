-- BLINK algorithm fairness: preserve existing function signatures and projections.
-- Rollback-plan: restore pg_get_functiondef backups of the two affected functions.
-- No data deletion, wallet change, or user balance migration.
begin;
set local lock_timeout = '5s';
set local statement_timeout = '60s';

-- Users with equal real points should share a rank. Created_at and UUID are
-- tie-breaking display order, not grounds for a different competitive rank.
do $leaderboard$
declare
  v_function text := pg_get_functiondef('public.blink_public_leaderboard_rows()'::regprocedure);
  v_old text := 'ORDER BY COALESCE(p.points, 0) DESC, p.created_at ASC, p.id ASC';
begin
  if v_function is null then raise exception 'LEADERBOARD_RANK_MISSING'; end if;
  if (length(v_function)-length(replace(v_function,v_old,'')))/length(v_old) = 2 then
    v_function := replace(v_function,v_old,'ORDER BY COALESCE(p.points, 0) DESC');
    execute v_function;
  elsif position('ORDER BY COALESCE(p.points, 0) DESC' in v_function)>0
    and position(v_old in v_function)=0 then
    null; -- Already migrated; safe to reapply.
  else
    raise exception 'LEADERBOARD_RANK_SOURCE_DRIFT';
  end if;
end;
$leaderboard$;

-- Discovery must not permanently favor accounts with low UUIDs, recommend
-- someone already followed, or promote someone a viewer has muted.
-- Seed the tie-break with viewer ID and UTC day for stable per-day diversity;
-- never turn this ordering into a false online presence or paid rank.
do $recommendations$
declare
  v_function text := pg_get_functiondef('public.blink_referral_recommendations(integer)'::regprocedure);
  v_filter text := 'where p.id <> v.id and p.onboarding_completed is true';
  v_seed text := 'md5(c.id::text || (select viewer.id::text from viewer) || (now() at time zone ''UTC'')::date::text)';
begin
  if v_function is null or position(v_filter in v_function)=0 then
    raise exception 'REFERRAL_RECOMMENDATIONS_SOURCE_DRIFT';
  end if;
  if position('order by id' in v_function)=0
    and position('order by md5(c.id::text ||' in v_function)>0
    and position('current_follow.follower_id=v.id' in v_function)>0 then
    return; -- Already migrated; do not overwrite changes.
  end if;
  if position('order by id' in v_function)=0
    or position('order by visibility_points desc, id' in v_function)=0 then
    raise exception 'REFERRAL_RECOMMENDATIONS_SOURCE_DRIFT';
  end if;
  v_function := replace(v_function,v_filter, v_filter || $filter$

      and not exists (select 1 from public.follows current_follow
        where current_follow.follower_id=v.id and current_follow.following_id=p.id)
      and not exists (select 1 from public.muted_users current_mute
        where current_mute.user_id=v.id and current_mute.muted_id=p.id)
$filter$);
  -- Use original candidate fields. Stable daily pseudorandom order avoids
  -- selecting the same low-UUID users on every app restart.
  v_function := replace(v_function,
    'order by id',
    'order by ' || v_seed);
  v_function := replace(v_function,
    'order by visibility_points desc, id',
    'order by visibility_points desc, id');
  execute v_function;
end;
$recommendations$;
commit;
