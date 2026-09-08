do $$
declare
  v_sql text;
  v_next text;
begin
  select pg_get_functiondef(p.oid)
  into v_sql
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'private_ranking'
    and p.proname = 'get_discovery_feed'
    and pg_get_function_identity_arguments(p.oid) = 'p_limit integer, p_offset integer, p_as_of timestamp with time zone, p_gravity numeric, p_w1 numeric, p_w2 numeric, p_w3 numeric, p_w4 numeric';

  if v_sql is null then
    raise exception 'private_ranking.get_discovery_feed signature not found';
  end if;

  v_next := replace(
    v_sql,
    ') * case when r.is_reel and r.completion_rate > 0.80 then 3 else 1 end as virality_component',
    ') as virality_component'
  );

  if v_next = v_sql then
    raise exception 'expected reel completion ranking multiplier was not found';
  end if;

  v_next := replace(
    v_next,
    '''reel_completion_boost'',case when c.is_reel and c.completion_rate > 0.80 then 3 else 1 end',
    '''reel_completion_boost'',1'
  );

  execute v_next;

  select pg_get_functiondef(p.oid)
  into v_sql
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname = 'get_ranked_feed_page'
    and pg_get_function_identity_arguments(p.oid) = 'p_surface text, p_limit integer, p_cursor_score numeric, p_cursor_created_at timestamp with time zone, p_cursor_id uuid, p_as_of timestamp with time zone';

  if v_sql is not null then
    v_next := replace(
      v_sql,
      '/ case when c.item_surface=''reels'' then 48.0 else 96.0 end',
      '/ 96.0'
    );
    if v_next <> v_sql then
      execute v_next;
    end if;
  end if;
end
$$;

create or replace function public.record_qualified_post_view(
  p_post_id uuid,
  p_viewer_username text default null,
  p_viewed_for_seconds integer default 0
)
returns integer
language plpgsql
security invoker
set search_path = 'public', 'pg_temp'
as $$
begin
  if auth.uid() is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  if coalesce(p_viewed_for_seconds, 0) < 60 then
    return coalesce((select fp.view_count from public.feed_posts fp where fp.id = p_post_id), 0);
  end if;

  return public.record_post_view(p_post_id);
end;
$$;

revoke all on function public.record_qualified_post_view(uuid, text, integer) from public, anon;
grant execute on function public.record_qualified_post_view(uuid, text, integer) to authenticated;
