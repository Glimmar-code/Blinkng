-- BLINK Contest: one accountable appeal per rejected/disqualified application.
-- All participant and administrator reads/writes go through narrowly scoped RPCs.
begin;

create table public.blink_contest_entry_appeals (
  id uuid primary key default gen_random_uuid(),
  contest_id uuid not null references public.blink_contests(id) on delete restrict,
  entry_id uuid not null references public.blink_contest_entries(id) on delete restrict,
  user_id uuid not null references auth.users(id) on delete restrict,
  statement text not null check (length(btrim(statement)) between 20 and 1000),
  status text not null default 'pending'
    check (status in ('pending','accepted','denied')),
  created_at timestamptz not null default now(),
  reviewed_at timestamptz,
  reviewed_by uuid references auth.users(id),
  decision_reason text check (decision_reason is null or length(decision_reason) between 8 and 300),
  unique(entry_id),
  constraint appeal_review_integrity check (
    (status='pending' and reviewed_at is null and reviewed_by is null and decision_reason is null)
    or (status<>'pending' and reviewed_at is not null and reviewed_by is not null and decision_reason is not null)
  )
);
create index blink_contest_appeals_pending_idx
  on public.blink_contest_entry_appeals(contest_id,created_at desc) where status='pending';
alter table public.blink_contest_entry_appeals enable row level security;
revoke all on public.blink_contest_entry_appeals from public,anon,authenticated;
grant select,insert,update on public.blink_contest_entry_appeals to service_role;

create function public.submit_blink_contest_entry_appeal(p_entry_id uuid,p_statement text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  v_user uuid := auth.uid();
  v_entry public.blink_contest_entries%rowtype;
  v_contest public.blink_contests%rowtype;
  v_appeal public.blink_contest_entry_appeals%rowtype;
begin
  if v_user is null or coalesce(auth.jwt()->>'is_anonymous','false')='true' then
    raise exception 'SIGN_IN_REQUIRED' using errcode='42501'; end if;
  if length(btrim(coalesce(p_statement,''))) not between 20 and 1000 then
    raise exception 'APPEAL_REASON_LENGTH' using errcode='22023'; end if;

  -- Serialize with contest transitions and application moderation.
  select * into v_entry from public.blink_contest_entries
    where id=p_entry_id and user_id=v_user for update;
  if not found then raise exception 'ENTRY_NOT_AVAILABLE' using errcode='22023'; end if;
  select * into v_contest from public.blink_contests
    where id=v_entry.contest_id;
  if v_entry.status not in ('rejected','disqualified') or
     v_contest.status<>'published' or now()>=v_contest.voting_closes_at then
    raise exception 'APPEAL_NOT_AVAILABLE' using errcode='22023';
  end if;
  insert into public.blink_contest_entry_appeals(contest_id,entry_id,user_id,statement)
    values (v_entry.contest_id,v_entry.id,v_user,btrim(p_statement))
    returning * into v_appeal;

  return jsonb_build_object('id',v_appeal.id,'entry_id',v_appeal.entry_id,
    'status',v_appeal.status,'statement',v_appeal.statement,'created_at',v_appeal.created_at);
end; $$;

create function public.my_blink_contest_entry_appeal(p_entry_id uuid)
returns jsonb language plpgsql stable security definer set search_path='' as $$
declare v_user uuid := auth.uid();
begin
  if v_user is null or coalesce(auth.jwt()->>'is_anonymous','false')='true' then
    raise exception 'SIGN_IN_REQUIRED' using errcode='42501'; end if;
  -- The entry must belong to this user, even if the appeal does not exist.
  if not exists(select 1 from public.blink_contest_entries
    where id=p_entry_id and user_id=v_user) then
    raise exception 'ENTRY_NOT_AVAILABLE' using errcode='22023'; end if;
  return coalesce((select jsonb_build_object(
    'id',a.id,'status',a.status,'statement',a.statement,
    'created_at',a.created_at,'reviewed_at',a.reviewed_at,
    'decision_reason',a.decision_reason)
    from public.blink_contest_entry_appeals a
    where a.entry_id=p_entry_id and a.user_id=v_user),'null'::jsonb);
end; $$;

create function public.admin_list_blink_contest_entry_appeals(
  p_contest_id uuid, p_limit integer default 100
) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare v_limit int:=least(greatest(coalesce(p_limit,100),1),100);
begin
  perform private.require_blink_admin();
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id',a.id,'entry_id',a.entry_id,'user_id',a.user_id,
      'username',p.username,'statement',a.statement,'status',a.status,
      'created_at',a.created_at,'decision_reason',a.decision_reason,
      'reviewed_at',a.reviewed_at
    ) order by a.created_at asc,a.id asc)
    from (
      select * from public.blink_contest_entry_appeals
      where contest_id=p_contest_id and status='pending'
      order by created_at asc,id asc limit v_limit
    ) a left join public.profiles p on p.id=a.user_id
  ),'[]'::jsonb);
end; $$;

create function public.admin_decide_blink_contest_entry_appeal(
  p_appeal_id uuid,p_action text,p_reason text
) returns jsonb language plpgsql security definer set search_path='' as $$
declare
  v_admin uuid := private.require_blink_admin();
  v_appeal public.blink_contest_entry_appeals%rowtype;
  v_entry public.blink_contest_entries%rowtype;
  v_contest public.blink_contests%rowtype;
  v_reason text := btrim(coalesce(p_reason,''));
  v_status text;
begin
  if p_action not in ('accept','deny') or length(v_reason) not between 8 and 300 then
    raise exception 'INVALID_APPEAL_DECISION' using errcode='22023'; end if;
  select * into v_appeal from public.blink_contest_entry_appeals
    where id=p_appeal_id for update;
  if not found or v_appeal.status<>'pending' then
    raise exception 'APPEAL_NOT_PENDING' using errcode='22023'; end if;
  select * into v_entry from public.blink_contest_entries
    where id=v_appeal.entry_id for update;
  select * into v_contest from public.blink_contests where id=v_appeal.contest_id;
  if v_contest.status<>'published' or now()>=v_contest.voting_closes_at
     or v_entry.status not in ('rejected','disqualified') then
    raise exception 'APPEAL_REVIEW_CLOSED' using errcode='22023'; end if;
  if p_action='accept' then
    -- Reinstatement uses the existing audited moderation RPC and sends its
    -- standard approval notice within this same transaction.
    perform public.admin_review_blink_contest_entry(
      v_entry.id,'approve',v_reason);
  end if;
  v_status := case p_action when 'accept' then 'accepted' else 'denied' end;
  update public.blink_contest_entry_appeals
    set status=v_status,reviewed_at=now(),reviewed_by=v_admin,
        decision_reason=v_reason where id=v_appeal.id;

  insert into public.notifications(
    user_id,actor_id,type,target_type,target_id,text,sub_text,is_read,metadata
  ) values(
    v_appeal.user_id,v_admin,'system'::public.notification_type_enum,
    'contest',v_appeal.contest_id,
    left('BLINK Contest appeal '||v_status,180),left(v_reason,240),false,
    jsonb_build_object('kind','blink_contest_appeal_decision',
      'contest_id',v_appeal.contest_id,'entry_id',v_appeal.entry_id,
      'appeal_id',v_appeal.id,'status',v_status)
  );
  return jsonb_build_object('id',v_appeal.id,'entry_id',v_appeal.entry_id,
    'status',v_status,'decision_reason',v_reason);
end; $$;

revoke all on function public.submit_blink_contest_entry_appeal(uuid,text) from public,anon,authenticated;
revoke all on function public.my_blink_contest_entry_appeal(uuid) from public,anon,authenticated;
revoke all on function public.admin_list_blink_contest_entry_appeals(uuid,integer) from public,anon,authenticated;
revoke all on function public.admin_decide_blink_contest_entry_appeal(uuid,text,text) from public,anon,authenticated;
grant execute on function public.submit_blink_contest_entry_appeal(uuid,text),
  public.my_blink_contest_entry_appeal(uuid),
  public.admin_list_blink_contest_entry_appeals(uuid,integer),
  public.admin_decide_blink_contest_entry_appeal(uuid,text,text) to authenticated;
notify pgrst,'reload schema';
commit;
