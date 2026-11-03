-- Expose only aggregate save counts for active, publicly viewable posts.
-- No user IDs or individual saved-post records are returned. Authenticated only.
-- Staged with BLINK Home redesign; deploy to production only after QA passes.
create or replace function public.get_visible_post_save_counts(p_post_ids uuid[])
returns table(post_id uuid, save_count bigint)
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if coalesce(cardinality(p_post_ids), 0) > 60 then
    raise exception 'Maximum 60 posts per request' using errcode = '22023';
  end if;

  return query
  select b.post_id, count(*)::bigint
  from public.post_bookmarks b
  join public.feed_posts p on p.id = b.post_id
  where b.post_id = any(p_post_ids)
    and p.is_active = true
    and coalesce(p.audience, 'Everyone') in ('Everyone', 'Public')
    and (p.expires_at is null or p.expires_at > now())
  group by b.post_id;
end;
$$;
revoke all on function public.get_visible_post_save_counts(uuid[]) from public, anon;
grant execute on function public.get_visible_post_save_counts(uuid[]) to authenticated;
