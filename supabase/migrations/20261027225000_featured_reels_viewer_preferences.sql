-- Keep featured Reel accounting and the approved 1/3/6 qualified-view weights.
-- Respect muted authors and normalize audience case without changing the RPC contract.
begin;
set local lock_timeout='5s';
set local statement_timeout='45s';

do $featured$
declare
  v_fn text := pg_get_functiondef('public.blink_featured_reels_12h()'::regprocedure);
  v_anchor text := 'order by c.twelve_hour_views desc, fp.created_at desc, fp.id';
  v_filter text := $filter$and not exists (
      select 1 from public.muted_users muted
      where muted.user_id=v_viewer and muted.muted_id=fp.user_id
    )$filter$;
begin
  if v_fn is null or position(v_anchor in v_fn)=0 then
    raise exception 'FEATURED_REELS_SOURCE_DRIFT';
  end if;
  if position('muted.muted_id=fp.user_id' in v_fn)=0 then
    v_fn := replace(v_fn, v_anchor, v_filter || E'\n  ' || v_anchor);
  end if;
  if position('fp.audience = ''Everyone''' in v_fn)>0 then
    v_fn := replace(v_fn, 'fp.audience = ''Everyone''',
      'lower(coalesce(fp.audience,''everyone'')) = ''everyone''');
  end if;
  execute v_fn;
end;
$featured$;
commit;
