-- Administrator-only inspection of stale, unreferenced Contest uploads.
-- Deliberately READ ONLY: deleting storage.objects via SQL can strand R2/Storage
-- bytes; a later privileged Storage API cleanup must revalidate each candidate.
begin;

create function public.admin_list_blink_contest_orphan_media(
  p_min_age_hours integer default 72,
  p_limit integer default 50
) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare
  v_hours integer := coalesce(p_min_age_hours,72);
  v_limit integer := least(greatest(coalesce(p_limit,50),1),100);
  v_candidates jsonb;
begin
  perform private.require_blink_admin();
  if v_hours < 72 or v_hours > 24*365 then
    raise exception 'INVALID_MEDIA_RETENTION' using errcode='22023';
  end if;
  select coalesce(jsonb_agg(jsonb_build_object(
    'path',s.name,'created_at',s.created_at
  ) order by s.created_at asc,s.name asc),'[]'::jsonb)
  into v_candidates from (
    select o.name,o.created_at from storage.objects o
    where o.bucket_id='blink-contest-private'
      and o.name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}[.](jpg|jpeg|png|webp|mp4)$'
      and o.created_at < now() - make_interval(hours=>v_hours)
      and not exists (
        select 1 from public.blink_contest_entries e
        where e.media_url like '%/functions/v1/blink-contest-private-media/' || o.name
      )
    order by o.created_at asc,o.name asc limit v_limit
  ) s;
  return jsonb_build_object(
    'bucket','blink-contest-private',
    'min_age_hours',v_hours,'limit',v_limit,
    'dry_run',true,'objects',v_candidates
  );
end; $$;

revoke all on function public.admin_list_blink_contest_orphan_media(integer,integer)
 from public,anon,authenticated;
grant execute on function public.admin_list_blink_contest_orphan_media(integer,integer)
 to authenticated;

notify pgrst,'reload schema';
commit;
