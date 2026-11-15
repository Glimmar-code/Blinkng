-- BLINK contests: one start, one end, one results time, automatic close/finalization.
-- Registration columns remain for older clients but are kept as aliases of voting
-- start/end until all released clients have moved to the three-time contract.
begin;

alter table public.blink_contests
  add column if not exists finalized_at timestamptz,
  add column if not exists results_notified_at timestamptz,
  add column if not exists pending_vote_reviews_at_finalization integer not null default 0;

alter table public.blink_contests
  drop constraint if exists blink_contest_dates;
alter table public.blink_contests
  drop constraint if exists blink_contests_dates_check;
alter table public.blink_contests
  drop constraint if exists blink_contest_three_times_check;

-- Existing contests retain their voting window; old application-only windows
-- are removed so the entry window is open through voting end.
update public.blink_contests
   set registration_opens_at = voting_opens_at,
       registration_closes_at = voting_closes_at
 where registration_opens_at is distinct from voting_opens_at
    or registration_closes_at is distinct from voting_closes_at;

alter table public.blink_contests
  add constraint blink_contest_three_times_check check (
    registration_opens_at = voting_opens_at
    and registration_closes_at = voting_closes_at
    and voting_opens_at < voting_closes_at
    and voting_closes_at <= results_at
  );

-- Legacy contests already finalized before this migration must not be
-- re-finalized or re-notified by the new scheduler.
update public.blink_contests
   set finalized_at = coalesce(finalized_at, updated_at, created_at),
       results_notified_at = coalesce(results_notified_at, updated_at, created_at)
 where status = 'completed';

create table if not exists private.blink_contest_notice_deliveries (
  contest_id uuid not null references public.blink_contests(id) on delete cascade,
  phase text not null check (phase in ('closed','results')),
  user_id uuid not null references auth.users(id) on delete cascade,
  delivered_at timestamptz not null default now(),
  primary key (contest_id, phase, user_id)
);
revoke all on private.blink_contest_notice_deliveries from public, anon, authenticated;

create table if not exists private.blink_contest_finalization_overrides (
  id uuid primary key default gen_random_uuid(),
  contest_id uuid not null references public.blink_contests(id) on delete cascade,
  override_number integer not null,
  admin_id uuid not null references auth.users(id),
  reason text not null check (length(reason) between 10 and 500),
  previous_winners jsonb not null,
  replacement_winners jsonb not null,
  created_at timestamptz not null default now(),
  unique (contest_id, override_number)
);
revoke all on private.blink_contest_finalization_overrides from public, anon, authenticated;

create table if not exists private.blink_contest_finalization_snapshots (
  contest_id uuid primary key references public.blink_contests(id) on delete cascade,
  finalized_at timestamptz not null,
  pending_vote_reviews integer not null check (pending_vote_reviews>=0),
  ranked_entries jsonb not null,
  original_winners jsonb not null
);
revoke all on private.blink_contest_finalization_snapshots from public, anon, authenticated;

-- Three timestamps are the canonical client contract. Legacy timestamps are
-- accepted only as input aliases; participation dates are no longer separate.
create or replace function public.admin_save_blink_contest(p_id uuid,p_data jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  u uuid := private.require_blink_admin();
  c public.blink_contests%rowtype;
  v_start timestamptz; v_end timestamptz; v_results timestamptz;
  f integer; s integer; t integer; v_title text; v_topic uuid; v_scope text;
begin
  if p_data is null or jsonb_typeof(p_data)<>'object' or octet_length(p_data::text)>10000 then
    raise exception 'INVALID_CONTEST';
  end if;
  v_title := trim(coalesce(p_data->>'title',''));
  v_start := coalesce(nullif(p_data->>'start_at',''),nullif(p_data->>'voting_opens_at',''),
                      nullif(p_data->>'registration_opens_at',''))::timestamptz;
  v_end := coalesce(nullif(p_data->>'end_at',''),nullif(p_data->>'voting_closes_at',''),
                    nullif(p_data->>'registration_closes_at',''))::timestamptz;
  v_results := nullif(p_data->>'results_at','')::timestamptz;
  f := coalesce((p_data->>'prize_first')::integer,0);
  s := coalesce((p_data->>'prize_second')::integer,0);
  t := coalesce((p_data->>'prize_third')::integer,0);
  v_topic := nullif(p_data->>'topic_id','')::uuid;
  v_scope := coalesce(nullif(p_data->>'competition_scope',''),'campus');

  if v_topic is not null and not exists(
    select 1 from public.blink_contest_topics where id=v_topic and is_active
  ) then raise exception 'INVALID_CONTEST_TOPIC'; end if;
  if v_topic is null then raise exception 'CONTEST_TOPIC_REQUIRED'; end if;
  if v_scope not in ('campus','nationwide') then raise exception 'INVALID_COMPETITION_SCOPE'; end if;
  if v_scope='campus' and nullif(trim(p_data->>'university'),'') is null then
    raise exception 'CAMPUS_COMPETITION_REQUIRES_UNIVERSITY';
  end if;
  if length(v_title) not between 4 and 100 or f<0 or s<0 or t<0 or
     v_start is null or v_end is null or v_results is null or
     not (v_start < v_end and v_end <= v_results) then
    raise exception 'INVALID_CONTEST_CONFIGURATION';
  end if;

  if p_id is not null then
    select * into c from public.blink_contests where id=p_id for update;
    if not found or c.status<>'draft' then raise exception 'ONLY_DRAFTS_EDITABLE'; end if;
    update public.blink_contests set
      title=v_title,description=coalesce(p_data->>'description',''),
      rules=coalesce(p_data->>'rules',''),category=coalesce(p_data->>'category','General'),
      topic_id=v_topic,competition_scope=v_scope,season=nullif(trim(p_data->>'season'),''),
      banner_url=nullif(p_data->>'banner_url',''),university=nullif(p_data->>'university',''),
      registration_opens_at=v_start,registration_closes_at=v_end,
      voting_opens_at=v_start,voting_closes_at=v_end,results_at=v_results,
      prize_first=f,prize_second=s,prize_third=t,
      max_entries=nullif(p_data->>'max_entries','')::integer,
      auto_approve=coalesce((p_data->>'auto_approve')::boolean,false),updated_at=now()
    where id=p_id returning * into c;
  else
    insert into public.blink_contests(
      title,description,rules,category,topic_id,competition_scope,season,banner_url,
      university,registration_opens_at,registration_closes_at,voting_opens_at,
      voting_closes_at,results_at,prize_first,prize_second,prize_third,
      max_entries,auto_approve,created_by
    ) values (
      v_title,coalesce(p_data->>'description',''),coalesce(p_data->>'rules',''),
      coalesce(p_data->>'category','General'),v_topic,v_scope,
      nullif(trim(p_data->>'season'),''),nullif(p_data->>'banner_url',''),
      nullif(p_data->>'university',''),v_start,v_end,v_start,v_end,v_results,
      f,s,t,nullif(p_data->>'max_entries','')::integer,
      coalesce((p_data->>'auto_approve')::boolean,false),u
    ) returning * into c;
  end if;
  return to_jsonb(c) || jsonb_build_object('start_at',c.voting_opens_at,'end_at',c.voting_closes_at);
end;
$$;

create or replace function public.list_blink_contests()
returns jsonb language sql stable security definer set search_path='' as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'id',c.id,'title',c.title,'description',c.description,'rules',c.rules,
    'category',c.category,'topic_id',c.topic_id,'competition_scope',c.competition_scope,
    'season',c.season,'banner_url',c.banner_url,'university',c.university,
    'prize_first',c.prize_first,'prize_second',c.prize_second,'prize_third',c.prize_third,
    'start_at',c.voting_opens_at,'end_at',c.voting_closes_at,
    'registration_opens_at',c.voting_opens_at,'registration_closes_at',c.voting_closes_at,
    'voting_opens_at',c.voting_opens_at,'voting_closes_at',c.voting_closes_at,
    'results_at',c.results_at,'finalized_at',c.finalized_at,
    'status',case
      when c.status='completed' and now()<c.results_at then 'review'
      when c.status='completed' then 'completed'
      when c.status='cancelled' then 'cancelled'
      when c.status='paused' then 'paused'
      when now()<c.voting_opens_at then 'upcoming'
      when now()<c.voting_closes_at then 'voting'
      else 'review' end,
    'contestants',(select count(*) from public.blink_contest_entries e
      where e.contest_id=c.id and e.status in ('approved','pending'))
  ) order by c.voting_opens_at desc),'[]'::jsonb)
  from public.blink_contests c
  where c.status in ('published','paused','completed')
     or (c.status='cancelled' and c.has_been_published);
$$;

create or replace function public.get_blink_contest(p_contest_id uuid)
returns jsonb language plpgsql stable security definer set search_path='' as $$
declare c public.blink_contests%rowtype; v_top jsonb; v_myrank integer;
  v_entry uuid; v_vote uuid; v_total integer; v_winners jsonb;
begin
  select * into c from public.blink_contests where id=p_contest_id
    and status in ('published','paused','completed','cancelled')
    and (status<>'cancelled' or has_been_published);
  if not found then raise exception 'CONTEST_NOT_AVAILABLE'; end if;
  with scores as (
    select e.id,e.user_id,e.statement,e.media_url,e.submitted_at,
      count(v.id) filter(where v.review_status='valid')::integer votes
    from public.blink_contest_entries e
    left join public.blink_contest_votes v on v.entry_id=e.id
    where e.contest_id=p_contest_id and e.status='approved'
    group by e.id
  ), ranking as (
    select s.*,row_number() over(order by votes desc,submitted_at asc,id asc)::integer rank
    from scores s
  )
  select coalesce((select jsonb_agg(jsonb_build_object(
      'id',r.id,'user_id',r.user_id,'username',p.username,'full_name',p.full_name,
      'avatar_url',p.avatar_url,'statement',r.statement,'media_url',r.media_url,
      'votes',r.votes,'rank',r.rank,
      'rank_delta',(select b.rank-r.rank from public.blink_contest_rank_baselines b
        where b.contest_id=p_contest_id and b.entry_id=r.id)
    ) order by r.rank) from ranking r left join public.profiles p on p.id=r.user_id
      where r.rank<=20),'[]'::jsonb),
    (select rank from ranking where user_id=auth.uid()),
    (select count(*)::integer from ranking)
  into v_top,v_myrank,v_total;

  select id into v_entry from public.blink_contest_entries
    where contest_id=p_contest_id and user_id=auth.uid();
  select entry_id into v_vote from public.blink_contest_votes
    where contest_id=p_contest_id and voter_id=auth.uid();
  if c.status='completed' and now()>=c.results_at then
    select coalesce(jsonb_agg(jsonb_build_object(
      'place',w.place,'user_id',w.user_id,'username',p.username,
      'avatar_url',p.avatar_url,'prize_amount',w.amount_ngn,
      'payment_status',w.payment_status,'announced_at',w.announced_at
    ) order by w.place),'[]'::jsonb) into v_winners
    from public.blink_contest_prizes w left join public.profiles p on p.id=w.user_id
    where w.contest_id=p_contest_id;
  else
    v_winners := '[]'::jsonb;
  end if;
  return jsonb_build_object(
    'contest',(select value from jsonb_array_elements(public.list_blink_contests()) j(value)
      where value->>'id'=p_contest_id::text limit 1),
    'leaderboard',v_top,'my_rank',v_myrank,
    'my_rank_delta',(select b.rank-v_myrank from public.blink_contest_rank_baselines b
      where b.contest_id=p_contest_id and b.entry_id=v_entry),
    'my_entry_id',v_entry,'my_vote_entry_id',v_vote,'total_ranked',v_total,
    'my_entry_status',(select status from public.blink_contest_entries where id=v_entry),
    'winners',v_winners
  );
end;
$$;

create or replace function private.blink_contest_send_notice(p_contest_id uuid,p_phase text)
returns integer language plpgsql security definer set search_path='' as $$
declare c public.blink_contests%rowtype; r record; n integer:=0; v_text text; v_sub text; v_kind text; v_place integer; v_prize integer;
begin
  if p_phase not in ('closed','results') then raise exception 'INVALID_CONTEST_NOTICE_PHASE'; end if;
  select * into c from public.blink_contests where id=p_contest_id;
  if not found then return 0; end if;
  for r in
    select distinct recipients.user_id from (
      select e.user_id from public.blink_contest_entries e where e.contest_id=p_contest_id
      union
      select v.voter_id from public.blink_contest_votes v where v.contest_id=p_contest_id
    ) recipients where recipients.user_id is not null
  loop
    insert into private.blink_contest_notice_deliveries(contest_id,phase,user_id)
      values(p_contest_id,p_phase,r.user_id) on conflict do nothing;
    if not found then continue; end if;
    v_place := null; v_prize := null;
    if p_phase='closed' then
      v_text := left('Voting has ended for '||c.title,180);
      v_sub := 'Results are scheduled for '||to_char(c.results_at at time zone 'UTC','YYYY-MM-DD HH24:MI')||' UTC.';
      v_kind := 'blink_contest_closed';
    else
      select p.place,p.amount_ngn into v_place,v_prize
      from public.blink_contest_prizes p
      where p.contest_id=p_contest_id and p.user_id=r.user_id limit 1;
      if found then
        v_text := 'BLINK Contest results: you placed #'||v_place;
        v_sub := left(c.title||' · Prize ₦'||v_prize,240);
        v_kind := 'blink_contest_winner';
      else
        v_text := 'BLINK Contest results are now available.';
        v_sub := left(c.title,240);
        v_kind := 'blink_contest_results';
      end if;
    end if;
    insert into public.notifications(user_id,actor_id,type,target_type,target_id,text,sub_text,is_read,metadata)
    values(r.user_id,c.created_by,'system'::public.notification_type_enum,
      'contest',p_contest_id,left(v_text,180),left(v_sub,240),false,
      jsonb_build_object('kind',v_kind,'contest_id',p_contest_id,'phase',p_phase,
        'place',v_place,'prize_amount',v_prize));
    n:=n+1;
  end loop;
  return n;
end;
$$;
revoke all on function private.blink_contest_send_notice(uuid,text) from public,anon,authenticated;

create or replace function private.blink_contest_finalize_internal(p_contest_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare c public.blink_contests%rowtype; v_pending integer; v_total integer;
  v_ranked jsonb; v_winners jsonb; v_finalized_at timestamptz:=now();
begin
  select * into c from public.blink_contests where id=p_contest_id for update;
  if not found then raise exception 'CONTEST_NOT_FOUND'; end if;
  if c.finalized_at is not null or c.status='completed' then
    return jsonb_build_object('status','completed','already_finalized',true,
      'pending_vote_reviews',coalesce(c.pending_vote_reviews_at_finalization,0));
  end if;
  if c.status not in ('published','paused') or now()<c.voting_closes_at then
    raise exception 'RESULTS_NOT_READY';
  end if;
  select count(*)::integer into v_pending from public.blink_contest_votes
    where contest_id=p_contest_id and review_status='under_review';
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
  select coalesce(jsonb_agg(jsonb_build_object(
    'entry_id',id,'user_id',user_id,'place',place,'valid_votes',score
  ) order by place),'[]'::jsonb) into v_ranked from ranking;
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
    case place when 1 then c.prize_first when 2 then c.prize_second else c.prize_third end
  from ranking where place<=3 and
    (case place when 1 then c.prize_first when 2 then c.prize_second else c.prize_third end)>0
  on conflict (contest_id,place) do nothing;
  get diagnostics v_total=row_count;
  select coalesce(jsonb_agg(jsonb_build_object(
    'place',place,'entry_id',entry_id,'user_id',user_id,'amount_ngn',amount_ngn
  ) order by place),'[]'::jsonb) into v_winners
  from public.blink_contest_prizes where contest_id=p_contest_id;
  insert into private.blink_contest_finalization_snapshots(
    contest_id,finalized_at,pending_vote_reviews,ranked_entries,original_winners
  ) values(p_contest_id,v_finalized_at,v_pending,v_ranked,v_winners);
  update public.blink_contests set status='completed',finalized_at=v_finalized_at,
    pending_vote_reviews_at_finalization=v_pending,updated_at=now()
  where id=p_contest_id;
  perform private.blink_contest_send_notice(p_contest_id,'closed');
  return jsonb_build_object('status','completed','winner_count',v_total,
    'pending_vote_reviews',v_pending,'snapshot_recorded',true,'results_at',c.results_at);
end;
$$;
revoke all on function private.blink_contest_finalize_internal(uuid) from public,anon,authenticated;

create or replace function private.process_blink_contest_schedule(p_limit integer default 100)
returns jsonb language plpgsql security definer set search_path='' as $$
declare r record; v_finalized integer:=0; v_announced integer:=0; v_limit integer;
begin
  v_limit:=least(greatest(coalesce(p_limit,100),1),500);
  for r in
    select id from public.blink_contests
    where status in ('published','paused') and finalized_at is null and voting_closes_at<=now()
    order by voting_closes_at,id limit v_limit for update skip locked
  loop
    perform private.blink_contest_finalize_internal(r.id);
    v_finalized:=v_finalized+1;
  end loop;
  for r in
    select id from public.blink_contests
    where status='completed' and results_notified_at is null and results_at<=now()
    order by results_at,id limit v_limit for update skip locked
  loop
    perform private.blink_contest_send_notice(r.id,'results');
    update public.blink_contests set results_notified_at=now(),updated_at=now() where id=r.id;
    v_announced:=v_announced+1;
  end loop;
  return jsonb_build_object('finalized',v_finalized,'results_announced',v_announced);
end;
$$;
revoke all on function private.process_blink_contest_schedule(integer) from public,anon,authenticated;

create or replace function public.admin_finalize_blink_contest(p_contest_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_result jsonb; v_results_at timestamptz; v_notified timestamptz;
begin
  perform private.require_blink_admin();
  v_result := private.blink_contest_finalize_internal(p_contest_id);
  select results_at,results_notified_at into v_results_at,v_notified
    from public.blink_contests where id=p_contest_id;
  if v_results_at<=now() and v_notified is null then
    perform private.blink_contest_send_notice(p_contest_id,'results');
    update public.blink_contests set results_notified_at=now(),updated_at=now()
      where id=p_contest_id and results_notified_at is null;
  end if;
  return v_result;
end;
$$;
revoke all on function public.admin_finalize_blink_contest(uuid) from public,anon,authenticated;
grant execute on function public.admin_finalize_blink_contest(uuid) to authenticated;

create or replace function public.admin_override_blink_contest_winners(
  p_contest_id uuid,p_winners jsonb,p_reason text
) returns jsonb language plpgsql security definer set search_path='' as $$
declare
  v_admin uuid:=private.require_blink_admin(); c public.blink_contests%rowtype;
  v_before jsonb; v_after jsonb; v_n integer; v_id uuid; v_row jsonb;
  v_place integer; v_entry uuid; v_amount integer; v_override integer;
begin
  if length(trim(coalesce(p_reason,''))) not between 10 and 500 or
     p_winners is null or jsonb_typeof(p_winners) is distinct from 'array' or
     jsonb_array_length(p_winners) not between 1 and 3 then
    raise exception 'INVALID_CONTEST_OVERRIDE';
  end if;
  select * into c from public.blink_contests where id=p_contest_id for update;
  if not found or c.status<>'completed' or c.finalized_at is null then
    raise exception 'CONTEST_NOT_FINALIZED';
  end if;
  if exists(select 1 from public.blink_contest_prizes where contest_id=p_contest_id
    and payment_status in ('processing','paid')) then
    raise exception 'PRIZE_PAYMENT_LOCKED';
  end if;
  if exists(
    select 1 from jsonb_array_elements(p_winners) x
    where coalesce((x->>'place')::integer,0) not between 1 and 3
       or nullif(x->>'entry_id','') is null
  ) or (select count(distinct (x->>'place')) from jsonb_array_elements(p_winners) x)<>jsonb_array_length(p_winners)
    or (select count(distinct (x->>'entry_id')) from jsonb_array_elements(p_winners) x)<>jsonb_array_length(p_winners) then
    raise exception 'INVALID_CONTEST_OVERRIDE';
  end if;
  if exists(
    select 1 from jsonb_array_elements(p_winners) x
    where not exists(select 1 from public.blink_contest_entries e
      where e.id=(x->>'entry_id')::uuid and e.contest_id=p_contest_id and e.status='approved')
  ) then raise exception 'INVALID_CONTEST_WINNER'; end if;
  if exists(
    select 1 from jsonb_array_elements(p_winners) x
    where case (x->>'place')::integer
      when 1 then c.prize_first when 2 then c.prize_second else c.prize_third end <= 0
  ) then raise exception 'INVALID_CONTEST_PRIZE'; end if;

  select coalesce(jsonb_agg(jsonb_build_object('place',place,'entry_id',entry_id,
    'user_id',user_id,'amount_ngn',amount_ngn,'payment_status',payment_status) order by place),'[]'::jsonb)
    into v_before from public.blink_contest_prizes where contest_id=p_contest_id;
  select coalesce(max(override_number),0)+1 into v_override
    from private.blink_contest_finalization_overrides where contest_id=p_contest_id;
  delete from public.blink_contest_prizes where contest_id=p_contest_id;
  for v_row in select value from jsonb_array_elements(p_winners)
  loop
    v_place:=(v_row->>'place')::integer;
    v_entry:=(v_row->>'entry_id')::uuid;
    v_amount:=case v_place when 1 then c.prize_first when 2 then c.prize_second else c.prize_third end;
    insert into public.blink_contest_prizes(contest_id,entry_id,user_id,place,amount_ngn)
      select p_contest_id,e.id,e.user_id,v_place,v_amount
      from public.blink_contest_entries e where e.id=v_entry;
  end loop;
  select coalesce(jsonb_agg(jsonb_build_object('place',place,'entry_id',entry_id,
    'user_id',user_id,'amount_ngn',amount_ngn,'payment_status',payment_status) order by place),'[]'::jsonb)
    into v_after from public.blink_contest_prizes where contest_id=p_contest_id;
  insert into private.blink_contest_finalization_overrides(
    contest_id,override_number,admin_id,reason,previous_winners,replacement_winners
  ) values(p_contest_id,v_override,v_admin,trim(p_reason),v_before,v_after) returning id into v_id;

  -- Overrides after a result notification was sent must notify all prior and
  -- replacement winners about the correction; the audit row is the dedupe key.
  if c.results_notified_at is not null then
    insert into public.notifications(user_id,actor_id,type,target_type,target_id,text,sub_text,is_read,metadata)
    select distinct affected.user_id,v_admin,'system'::public.notification_type_enum,
      'contest',p_contest_id,'BLINK Contest results were corrected',left(c.title||' · '||trim(p_reason),240),false,
      jsonb_build_object('kind','blink_contest_results_corrected','contest_id',p_contest_id,
        'override_id',v_id)
    from (
      select (x->>'user_id')::uuid user_id from jsonb_array_elements(v_before) x
      union
      select (x->>'user_id')::uuid user_id from jsonb_array_elements(v_after) x
    ) affected where affected.user_id is not null;
  end if;
  return jsonb_build_object('status','completed','override_id',v_id,
    'override_number',v_override,'winners',v_after);
end;
$$;
revoke all on function public.admin_override_blink_contest_winners(uuid,jsonb,text) from public,anon,authenticated;
grant execute on function public.admin_override_blink_contest_winners(uuid,jsonb,text) to authenticated;

-- pg_cron is required for automatic close and result delivery. Fail the migration
-- rather than silently deploying an unscheduled automatic-finalization feature.
do $$
declare j record;
begin
  if to_regclass('cron.job') is null or to_regprocedure('cron.schedule(text,text,text)') is null then
    raise exception 'BLINK_CONTEST_PG_CRON_REQUIRED';
  end if;
  for j in execute 'select jobid from cron.job where jobname=$1' using 'blink-contest-auto-finalize'
  loop
    perform cron.unschedule(j.jobid);
  end loop;
  perform cron.schedule('blink-contest-auto-finalize','* * * * *',
    'select private.process_blink_contest_schedule(100);');
end;
$$;

revoke all on function public.list_blink_contests() from public,anon;
grant execute on function public.list_blink_contests() to authenticated;
revoke all on function public.get_blink_contest(uuid) from public,anon,authenticated;
grant execute on function public.get_blink_contest(uuid) to authenticated;

notify pgrst,'reload schema';
commit;
