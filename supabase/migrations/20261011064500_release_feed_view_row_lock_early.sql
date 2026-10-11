-- Reduce PostgreSQL statement timeouts during simultaneous feed views/reposts.
-- Before: record_content_view held an exclusive feed_posts row lock while
-- doing unrelated rewards and repost-distribution lookups. A concurrent
-- post_reposts FK check (FOR KEY SHARE) could wait until statement_timeout.
-- Keep a FOR KEY SHARE read to protect against deletes but defer the atomic
-- (non-key) view_count UPDATE until after expensive reward bookkeeping.
-- The post_views UPSERT still enforces each viewer's contribution cap.
-- Uses guarded source rewriting so unexpected function changes abort safely.
do $migration$
declare
  v_oid regprocedure := 'public.record_content_view(uuid,uuid)'::regprocedure;
  v_source text := pg_get_functiondef(v_oid);
  v_lock_before text := $part$
    select coalesce(fp.view_count, 0), coalesce(fp.is_reel, false)
      into v_view_count, v_is_reel
      from public.feed_posts fp
     where fp.id = p_post_id
       and (fp.is_active = true or fp.user_id = v_viewer_id)
     for update;$part$;
  v_lock_after text := $part$
    select coalesce(fp.view_count, 0), coalesce(fp.is_reel, false)
      into v_view_count, v_is_reel
      from public.feed_posts fp
     where fp.id = p_post_id
       and (fp.is_active = true or fp.user_id = v_viewer_id)
     for key share;$part$;
  v_counter_update text := $part$
    update public.feed_posts
       set view_count = coalesce(view_count, 0) + v_view_weight,
           updated_at = now()
     where id = p_post_id
    returning view_count into v_view_count;$part$;
  v_award_end text := $part$
    begin
        perform public.award_repost_distribution_points(p_post_id, v_viewer_id, 'view');
    exception when others then
        null;
    end;$part$;
begin
  if v_source is null
    or position(v_lock_before in v_source) = 0
    or position(v_counter_update in v_source) = 0
    or position(v_award_end in v_source) = 0 then
    raise exception 'RECORD_CONTENT_VIEW_SOURCE_CHANGED: review lock-order migration';
  end if;
  -- Do not silently double-apply the view counter update.
  if length(v_source) - length(replace(v_source, v_counter_update, '')) <> length(v_counter_update) then
    raise exception 'RECORD_CONTENT_VIEW_UPDATE_OCCURRENCE_CHANGED';
  end if;

  v_source := replace(v_source, v_lock_before, v_lock_after);
  v_source := replace(v_source, v_counter_update, '');
  v_source := replace(v_source, v_award_end, v_award_end || E'\n\n' || v_counter_update);
  execute v_source;
end;
$migration$;
