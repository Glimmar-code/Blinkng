-- Read-only, bounded moderation timeline. No direct client table grants.
-- Only a verified Contest administrator may see actor identities and reasons.
begin;

create function public.admin_get_blink_contest_entry_review_history(
  p_entry_id uuid,
  p_limit integer default 30
) returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit,30),1),50);
  v_count integer;
  v_rows jsonb;
begin
  perform private.require_blink_admin();
  if not exists (select 1 from public.blink_contest_entries
                 where id=p_entry_id) then
    raise exception 'ENTRY_NOT_FOUND' using errcode='22023';
  end if;

  select count(*)::integer into v_count
    from public.blink_contest_entry_review_audit a
    where a.entry_id=p_entry_id;

  select coalesce(jsonb_agg(jsonb_build_object(
    'id',a.id,
    'previous_status',a.previous_status,
    'next_status',a.next_status,
    'reason',a.reason,
    'actor_id',a.actor_id,
    'actor_username',coalesce(nullif(p.username,''),'Admin'),
    'created_at',a.created_at
  ) order by a.created_at desc,a.id desc),'[]'::jsonb)
  into v_rows
  from (
    select id,previous_status,next_status,reason,actor_id,created_at
    from public.blink_contest_entry_review_audit
    where entry_id=p_entry_id
    order by created_at desc,id desc
    limit v_limit
  ) a
  left join public.profiles p on p.id=a.actor_id;

  return jsonb_build_object(
    'entry_id',p_entry_id,'total',v_count,'limit',v_limit,'history',v_rows
  );
end; $$;

revoke all on function public.admin_get_blink_contest_entry_review_history(uuid,integer)
  from public,anon,authenticated;
grant execute on function public.admin_get_blink_contest_entry_review_history(uuid,integer)
  to authenticated;

notify pgrst,'reload schema';
commit;
