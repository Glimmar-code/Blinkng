-- BLINK Contest growth: in-app vote alerts, share analytics, vote-integrity review,
-- confirmed results, and manual payment-status tracking.
-- No payment execution, no paid voting, no access to IP/device fingerprints.
begin;

create table public.blink_contest_shares (
  id uuid primary key default gen_random_uuid(),
  contest_id uuid not null,
  entry_id uuid not null,
  sharer_id uuid not null references auth.users(id) on delete cascade,
  channel text not null default 'other' check (channel in ('native','copy','whatsapp','desktop','other')),
  created_at timestamptz not null default now(),
  foreign key (contest_id,entry_id) references public.blink_contest_entries(contest_id,id)
);
create index blink_contest_shares_entry_idx on public.blink_contest_shares(contest_id,entry_id,created_at desc);
create index blink_contest_shares_sharer_idx on public.blink_contest_shares(sharer_id,created_at desc);
alter table public.blink_contest_shares enable row level security;
revoke all on table public.blink_contest_shares from public,anon,authenticated;
grant select, insert, update, delete on table public.blink_contest_shares to service_role;

create table public.blink_contest_vote_audit (
  id uuid primary key default gen_random_uuid(),
  contest_id uuid not null references public.blink_contests(id) on delete restrict,
  vote_id uuid not null references public.blink_contest_votes(id) on delete restrict,
  previous_status text,
  next_status text not null,
  reason text not null,
  actor_id uuid references auth.users(id),
  created_at timestamptz not null default now()
);
create index blink_contest_vote_audit_idx on public.blink_contest_vote_audit(contest_id,created_at desc);
alter table public.blink_contest_vote_audit enable row level security;
revoke all on table public.blink_contest_vote_audit from public,anon,authenticated;
grant select, insert on table public.blink_contest_vote_audit to service_role;

-- Track real user-initiated share actions, never hypothetical impressions.
create function public.record_blink_contest_share(p_contest_id uuid,p_entry_id uuid,p_channel text default 'other')
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_user uuid:=auth.uid(); v_count integer;
begin
  if v_user is null or coalesce(auth.jwt()->>'is_anonymous','false')='true' then
    raise exception 'SIGN_IN_REQUIRED' using errcode='42501'; end if;
  if p_channel not in ('native','copy','whatsapp','desktop','other') then
    raise exception 'INVALID_CHANNEL'; end if;
  if not exists(select 1 from public.blink_contests c
    join public.blink_contest_entries e on e.contest_id=c.id
    where c.id=p_contest_id and c.status in ('published','paused','completed')
      and e.id=p_entry_id and e.status='approved') then
    raise exception 'CANDIDATE_NOT_AVAILABLE'; end if;
  select count(*) into v_count from public.blink_contest_shares
    where sharer_id=v_user and created_at>now()-interval '1 day';
  if v_count >= 40 then raise exception 'SHARE_LOG_LIMIT_REACHED'; end if;
  insert into public.blink_contest_shares(contest_id,entry_id,sharer_id,channel)
    values(p_contest_id,p_entry_id,v_user,p_channel);
  return jsonb_build_object('recorded',true);
end; $$;

-- User-initiated votes. DB unique(contest_id,voter_id) still guarantees one free
-- vote even when the same request is retried in parallel.
create or replace function public.vote_blink_contest(p_contest_id uuid,p_entry_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare c public.blink_contests%rowtype; e public.blink_contest_entries%rowtype;
  v_user uuid:=auth.uid(); v_age timestamptz; v_recent integer; v_review text:='valid';
begin
  if v_user is null or coalesce(auth.jwt()->>'is_anonymous','false')='true' then
    raise exception 'SIGN_IN_REQUIRED' using errcode='42501'; end if;
  select * into c from public.blink_contests where id=p_contest_id for update;
  if not found or c.status<>'published' or now()<c.voting_opens_at or
    now()>=c.voting_closes_at then raise exception 'VOTING_CLOSED'; end if;
  select * into e from public.blink_contest_entries
    where id=p_entry_id and contest_id=p_contest_id and status='approved';
  if not found then raise exception 'ENTRY_NOT_ELIGIBLE'; end if;
  if e.user_id=v_user then raise exception 'SELF_VOTE_NOT_ALLOWED'; end if;

  select created_at into v_age from auth.users where id=v_user;
  select count(*) into v_recent from public.blink_contest_votes
    where entry_id=p_entry_id and created_at>now()-interval '10 minutes';
  -- A newly opened account plus a sharp concentrated burst triggers manual
  -- review; no fingerprinting, account blocking, or silent discarding.
  if v_age>now()-interval '10 minutes' and v_recent>=15 then v_review:='under_review'; end if;
  insert into public.blink_contest_votes(contest_id,entry_id,voter_id,review_status,
    review_reason) values (p_contest_id,p_entry_id,v_user,v_review,
    case when v_review='under_review' then 'Recent-account concentrated voting burst' else null end);

  if v_review='valid' and not exists(
    select 1 from public.notifications n
    where n.user_id=e.user_id and n.target_id=p_contest_id
      and n.metadata->>'kind'='blink_contest_vote'
      and n.created_at>now()-interval '1 hour'
  ) then
    insert into public.notifications(
      user_id,actor_id,type,target_type,target_id,text,sub_text,is_read,metadata)
    values(e.user_id,v_user,'system'::public.notification_type_enum,
      'contest',p_contest_id,left('Your contest entry received a vote',180),
      left(c.title,240),false,
      jsonb_build_object('kind','blink_contest_vote','contest_id',p_contest_id,
        'entry_id',p_entry_id));
  end if;
  return jsonb_build_object('success',true,'entry_id',p_entry_id,
    'vote_status',v_review,'counted',v_review='valid');
end; $$;

-- Explicit admin review is auditable, cannot edit already completed results.
create function public.admin_review_blink_contest_vote(
 p_vote_id uuid,p_action text,p_reason text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_admin uuid:=private.require_blink_admin(); v public.blink_contest_votes%rowtype;
 c public.blink_contests%rowtype; v_next text;
begin
  if p_action not in ('hold','approve','invalidate') or
     length(trim(coalesce(p_reason,''))) not between 8 and 300 then
    raise exception 'INVALID_REVIEW'; end if;
  select * into v from public.blink_contest_votes where id=p_vote_id for update;
  if not found then raise exception 'VOTE_NOT_FOUND'; end if;
  select * into c from public.blink_contests where id=v.contest_id for update;
  if c.status in ('completed','cancelled') then raise exception 'RESULTS_LOCKED'; end if;
  v_next:=case p_action when 'hold' then 'under_review'
       when 'approve' then 'valid' else 'invalidated' end;
  if v.review_status=v_next then raise exception 'NO_REVIEW_CHANGE'; end if;
  update public.blink_contest_votes set review_status=v_next,
    review_reason=trim(p_reason),reviewed_by=v_admin,reviewed_at=now() where id=v.id;
  insert into public.blink_contest_vote_audit(
    contest_id,vote_id,previous_status,next_status,reason,actor_id)
    values(v.contest_id,v.id,v.review_status,v_next,trim(p_reason),v_admin);
  return jsonb_build_object('vote_id',v.id,'status',v_next);
end; $$;

-- Aggregated metrics exclude private voter identities; the review queue is
-- available to admins only so disputed votes never leak into public profiles.
create function public.admin_blink_contest_insights(p_contest_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_blink_admin();
  if not exists(select 1 from public.blink_contests where id=p_contest_id)
    then raise exception 'CONTEST_NOT_FOUND'; end if;
  return jsonb_build_object(
    'shares',(select count(*) from public.blink_contest_shares where contest_id=p_contest_id),
    'unique_sharers',(select count(distinct sharer_id) from public.blink_contest_shares where contest_id=p_contest_id),
    'total_votes',(select count(*) from public.blink_contest_votes where contest_id=p_contest_id),
    'counted_votes',(select count(*) from public.blink_contest_votes where contest_id=p_contest_id and review_status='valid'),
    'review_votes',(select count(*) from public.blink_contest_votes where contest_id=p_contest_id and review_status='under_review'),
    'rejected_votes',(select count(*) from public.blink_contest_votes where contest_id=p_contest_id and review_status='invalidated'),
    'share_by_channel',coalesce((select jsonb_object_agg(channel,num) from
      (select channel,count(*) as num from public.blink_contest_shares
       where contest_id=p_contest_id group by channel) counts),'{}'::jsonb),
    'review_queue',coalesce((select jsonb_agg(jsonb_build_object(
        'id',v.id,'entry_id',v.entry_id,'created_at',v.created_at,
        'reason',v.review_reason,'status',v.review_status) order by v.created_at)
       from public.blink_contest_votes v where v.contest_id=p_contest_id
       and v.review_status='under_review'),'[]'::jsonb),
    'prizes',coalesce((select jsonb_agg(jsonb_build_object(
        'id',p.id,'place',p.place,'username',u.username,
        'amount_ngn',p.amount_ngn,'payment_status',p.payment_status,
        'payment_reference',p.payment_reference,'processed_at',p.processed_at)
       order by p.place) from public.blink_contest_prizes p
       join public.profiles u on u.id=p.user_id
       where p.contest_id=p_contest_id),'[]'::jsonb)
  );
end; $$;

-- Atomic winner snapshot; no arbitrary client-selected winners or amounts.
create function public.admin_finalize_blink_contest(p_contest_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_admin uuid:=private.require_blink_admin(); c public.blink_contests%rowtype;
  v_unresolved integer; v_total integer;
begin
  select * into c from public.blink_contests where id=p_contest_id for update;
  if not found or c.status not in ('published','paused')
    or now()<c.voting_closes_at or now()<c.results_at then
      raise exception 'RESULTS_NOT_READY'; end if;
  select count(*) into v_unresolved from public.blink_contest_votes
    where contest_id=p_contest_id and review_status='under_review';
  if v_unresolved>0 then raise exception 'VOTE_REVIEWS_PENDING'; end if;

  with scores as (
    select e.id,e.user_id,e.submitted_at,
      count(v.id) filter(where v.review_status='valid') as score
    from public.blink_contest_entries e
    left join public.blink_contest_votes v on v.entry_id=e.id
    where e.contest_id=p_contest_id and e.status='approved'
    group by e.id
  ), ranking as (
    select *,row_number() over(order by score desc,submitted_at asc,id asc)::integer place
    from scores
  )
  insert into public.blink_contest_prizes(contest_id,entry_id,user_id,place,amount_ngn)
  select p_contest_id,id,user_id,place,
    case place when 1 then c.prize_first when 2 then c.prize_second
      else c.prize_third end
  from ranking where place<=3 and
    (case place when 1 then c.prize_first when 2 then c.prize_second
      else c.prize_third end)>0;
  get diagnostics v_total = row_count;

  update public.blink_contests set status='completed',updated_at=now()
    where id=p_contest_id;
  insert into public.notifications(
    user_id,actor_id,type,target_type,target_id,text,sub_text,is_read,metadata)
  select p.user_id,v_admin,'system'::public.notification_type_enum,
    'contest',p_contest_id,left('BLINK Contest results: you placed #'||p.place,180),
    left(c.title||' · Prize ₦'||p.amount_ngn,240),false,
    jsonb_build_object('kind','blink_contest_winner','contest_id',p_contest_id,
      'entry_id',p.entry_id,'place',p.place)
  from public.blink_contest_prizes p where p.contest_id=p_contest_id;
  return jsonb_build_object('status','completed','winner_count',v_total);
end; $$;

-- Bookkeeping only. A paid record requires an external receipt reference,
-- and the RPC never contacts Paystack or sends money.
create function public.admin_record_blink_contest_prize(
 p_prize_id uuid,p_status text,p_reference text default null,p_note text default null
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_admin uuid:=private.require_blink_admin();
 p public.blink_contest_prizes%rowtype;
begin
  if p_status not in ('processing','failed','paid') or
     length(coalesce(p_note,''))>400 or length(coalesce(p_reference,''))>150 then
    raise exception 'INVALID_PRIZE_STATUS'; end if;
  select * into p from public.blink_contest_prizes where id=p_prize_id for update;
  if not found then raise exception 'PRIZE_NOT_FOUND'; end if;
  if p.payment_status='paid' then raise exception 'PAID_PRIZE_LOCKED'; end if;
  if p_status='paid' and (p_reference is null or
    length(trim(p_reference))<6) then raise exception 'RECEIPT_REFERENCE_REQUIRED'; end if;
  update public.blink_contest_prizes set payment_status=p_status,
    payment_reference=case when p_status='paid' then trim(p_reference)
       else nullif(trim(coalesce(p_reference,'')),'') end,
    payment_note=nullif(trim(coalesce(p_note,'')),''),
    processed_by=v_admin,processed_at=now()
    where id=p_prize_id returning * into p;
  if p_status='paid' then
    insert into public.notifications(user_id,actor_id,type,target_type,target_id,
      text,sub_text,is_read,metadata)
    values(p.user_id,v_admin,'system'::public.notification_type_enum,
      'contest',p.contest_id,'BLINK Contest prize payment recorded',
      'Please confirm receipt with the contest administrator.',false,
      jsonb_build_object('kind','blink_contest_prize_recorded',
        'contest_id',p.contest_id,'place',p.place));
  end if;
  return jsonb_build_object('id',p.id,'status',p.payment_status,
    'amount_ngn',p.amount_ngn,'payment_reference',p.payment_reference);
end; $$;

revoke all on function public.record_blink_contest_share(uuid,uuid,text) from public,anon,authenticated;
revoke all on function public.admin_blink_contest_insights(uuid) from public,anon,authenticated;
revoke all on function public.admin_review_blink_contest_vote(uuid,text,text) from public,anon,authenticated;
revoke all on function public.admin_finalize_blink_contest(uuid) from public,anon,authenticated;
revoke all on function public.admin_record_blink_contest_prize(uuid,text,text,text) from public,anon,authenticated;
grant execute on function public.record_blink_contest_share(uuid,uuid,text),
  public.admin_blink_contest_insights(uuid),
  public.admin_review_blink_contest_vote(uuid,text,text),
  public.admin_finalize_blink_contest(uuid),
  public.admin_record_blink_contest_prize(uuid,text,text,text) to authenticated;

notify pgrst,'reload schema';
commit;
