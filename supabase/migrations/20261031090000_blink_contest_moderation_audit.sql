-- Require an attributable reason for Contest moderation changes.
-- Keep existing 2-argument RPC available internally to the guarded wrapper,
-- but revoke its direct authenticated access to prevent audit bypass.
begin;

create table public.blink_contest_entry_review_audit (
  id uuid primary key default gen_random_uuid(),
  contest_id uuid not null references public.blink_contests(id) on delete restrict,
  entry_id uuid not null references public.blink_contest_entries(id) on delete restrict,
  actor_id uuid not null references auth.users(id) on delete restrict,
  previous_status text not null,
  next_status text not null,
  reason text not null check (length(reason) between 8 and 300),
  created_at timestamptz not null default now()
);
create index blink_contest_entry_review_audit_entry_time_idx
  on public.blink_contest_entry_review_audit(entry_id, created_at desc);
alter table public.blink_contest_entry_review_audit enable row level security;
revoke all on table public.blink_contest_entry_review_audit from public, anon, authenticated;
grant select, insert on table public.blink_contest_entry_review_audit to service_role;

-- Calls from the mobile/Windows client MUST use this reason-bearing signature.
revoke execute on function public.admin_review_blink_contest_entry(uuid,text)
  from authenticated;

create function public.admin_review_blink_contest_entry(
  p_entry_id uuid, p_action text, p_reason text
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_actor uuid := private.require_blink_admin();
  v_entry public.blink_contest_entries%rowtype;
  v_updated jsonb;
  v_next text;
  v_reason text := btrim(coalesce(p_reason,''));
begin
  if p_action not in ('approve','reject','disqualify') then
    raise exception 'INVALID_REVIEW_ACTION' using errcode='22023';
  end if;
  if length(v_reason) not between 8 and 300 then
    raise exception 'CONTEST_REVIEW_REASON_REQUIRED' using errcode='22023';
  end if;
  select * into v_entry from public.blink_contest_entries
    where id=p_entry_id for update;
  if not found then raise exception 'ENTRY_NOT_FOUND' using errcode='22023'; end if;
  v_next := case p_action when 'approve' then 'approved'
    when 'reject' then 'rejected' else 'disqualified' end;
  if v_entry.status = v_next then
    raise exception 'ENTRY_ALREADY_IN_STATUS' using errcode='22023';
  end if;

  -- Delegates deadline/contest-status checks to the existing transactional RPC.
  v_updated := public.admin_review_blink_contest_entry(p_entry_id, p_action);

  insert into public.blink_contest_entry_review_audit(
    contest_id, entry_id, actor_id, previous_status, next_status, reason
  ) values (
    v_entry.contest_id, v_entry.id, v_actor, v_entry.status, v_next, v_reason
  );

  insert into public.notifications(
    user_id, actor_id, type, target_type, target_id,
    text, sub_text, is_read, metadata
  ) values (
    v_entry.user_id, v_actor, 'system'::public.notification_type_enum,
    'contest', v_entry.contest_id,
    left('BLINK Contest application ' || v_next,180),
    left(v_reason,240), false,
    jsonb_build_object(
      'kind','blink_contest_application_review',
      'contest_id',v_entry.contest_id,
      'entry_id',v_entry.id,
      'new_status',v_next
    )
  );
  return v_updated;
end;
$$;

revoke all on function public.admin_review_blink_contest_entry(uuid,text,text)
  from public, anon, authenticated;
grant execute on function public.admin_review_blink_contest_entry(uuid,text,text)
  to authenticated;

notify pgrst, 'reload schema';
commit;
