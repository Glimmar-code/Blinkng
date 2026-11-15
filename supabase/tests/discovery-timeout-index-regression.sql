-- Run after all migrations in a disposable Supabase-compatible database.
do $test$
declare
  v_definition text;
  v_missing text[];
begin
  select array_agg(expected.name order by expected.name)
    into v_missing
  from (values
    ('blocks_blocked_blocker_idx'),
    ('post_views_viewer_post_last_viewed_idx'),
    ('post_likes_user_created_post_idx'),
    ('post_bookmarks_user_created_post_idx'),
    ('comments_author_created_post_idx')
  ) as expected(name)
  where not exists (
    select 1 from pg_class idx
    join pg_namespace ns on ns.oid=idx.relnamespace
    where idx.relkind='i' and idx.relname=expected.name and ns.nspname='public'
  );

  if coalesce(cardinality(v_missing),0) > 0 then
    raise exception 'MISSING_DISCOVERY_TIMEOUT_INDEXES: %', array_to_string(v_missing, ', ');
  end if;

  v_definition := pg_get_functiondef(
    'private_ranking.get_discovery_feed(integer,integer,timestamptz,numeric,numeric,numeric,numeric,numeric)'::regprocedure
  );
  if position('BLINK_TIMEOUT_RANKED_TEMP_INDEXES' in v_definition)=0
     or position('discovery_candidates_score_idx' in v_definition)=0
     or position('discovery_candidates_lane_idx' in v_definition)=0 then
    raise exception 'DISCOVERY_FEED_TEMP_INDEX_OPTIMIZATION_MISSING';
  end if;

  if has_function_privilege('anon', 'public.market_require_approved_seller()', 'EXECUTE')
     or has_function_privilege('authenticated', 'public.market_require_approved_seller()', 'EXECUTE')
     or has_function_privilege('anon', 'public.market_require_approved_seller_for_order()', 'EXECUTE')
     or has_function_privilege('authenticated', 'public.market_require_approved_seller_for_order()', 'EXECUTE') then
    raise exception 'TRIGGER_ONLY_MARKET_FUNCTION_STILL_CLIENT_EXECUTABLE';
  end if;

  if not exists (
    select 1 from pg_trigger
    where not tgisinternal
      and tgfoid='public.market_require_approved_seller()'::regprocedure
  ) or not exists (
    select 1 from pg_trigger
    where not tgisinternal
      and tgfoid='public.market_require_approved_seller_for_order()'::regprocedure
  ) then
    raise exception 'MARKET_SELLER_GUARD_TRIGGER_MISSING';
  end if;

  if not exists (
    select 1
    from pg_index i
    join pg_class t on t.oid=i.indrelid
    join pg_namespace n on n.oid=t.relnamespace
    join pg_attribute a on a.attrelid=t.oid and a.attname='post_id'
    where n.nspname='public' and t.relname='feed_preferences'
      and i.indisvalid and i.indisready and i.indnkeyatts >= 1
      and (i.indkey::smallint[])[0]=a.attnum
  ) then
    raise exception 'FOREIGN_KEY_SIDE_INDEX_MISSING';
  end if;

  -- The prior view-lock repair must remain in place: expensive reward work
  -- should not retain an exclusive feed_posts row lock.
  v_definition := pg_get_functiondef('public.record_content_view(uuid,uuid)'::regprocedure);
  if position('for key share' in lower(v_definition))=0
     or position('for update' in lower(v_definition))>0 then
    raise exception 'CONTENT_VIEW_ROW_LOCK_REGRESSION';
  end if;
end;
$test$;
