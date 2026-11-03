-- BLINK Contest: free entry, one free vote per account, server-authoritative rankings.
-- Staging first. Do not apply to the live Supabase project before verifying
-- the SQL, authenticated RPCs, admin ACLs, concurrency and prize eligibility.
begin;

create table public.blink_contests (
  id uuid primary key default gen_random_uuid(),
  title text not null check (length(trim(title)) between 4 and 100),
  description text not null default '' check (length(description) <= 2000),
  rules text not null default '' check (length(rules) <= 4000),
  category text not null default 'General' check (length(category) <= 60),
  banner_url text check (banner_url is null or (length(banner_url) <= 600 and banner_url ~ '^https://')),
  university text check (university is null or length(university) <= 120),
  prize_first integer not null default 0 check (prize_first >= 0),
  prize_second integer not null default 0 check (prize_second >= 0),
  prize_third integer not null default 0 check (prize_third >= 0),
  registration_opens_at timestamptz not null,
  registration_closes_at timestamptz not null,
  voting_opens_at timestamptz not null,
  voting_closes_at timestamptz not null,
  results_at timestamptz not null,
  max_entries integer check (max_entries is null or max_entries between 2 and 100000),
  auto_approve boolean not null default false,
  status text not null default 'draft'
    check (status in ('draft','published','paused','completed','cancelled')),
  created_by uuid not null references auth.users(id),
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  constraint blink_contest_dates check (
    registration_opens_at < registration_closes_at
    and registration_closes_at <= voting_opens_at
    and voting_opens_at < voting_closes_at
    and voting_closes_at <= results_at
  )
);

create table public.blink_contest_entries (
  id uuid primary key default gen_random_uuid(),
  contest_id uuid not null references public.blink_contests(id) on delete restrict,
  user_id uuid not null references auth.users(id) on delete cascade,
  statement text not null check (length(trim(statement)) between 10 and 500),
  media_url text check (media_url is null or (length(media_url) <= 800 and media_url ~ '^https://')),
  status text not null default 'pending'
    check (status in ('pending','approved','rejected','disqualified')),
  submitted_at timestamptz not null default now(),
  reviewed_at timestamptz,
  unique (contest_id, user_id),
  unique (contest_id, id)
);
create index blink_contest_entries_status_idx on public.blink_contest_entries(contest_id,status,submitted_at);

create table public.blink_contest_votes (
  id uuid primary key default gen_random_uuid(),
  contest_id uuid not null,
  entry_id uuid not null,
  voter_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  foreign key (contest_id, entry_id) references public.blink_contest_entries(contest_id,id) on delete restrict,
  unique (contest_id,voter_id)
);
create index blink_contest_votes_entry_idx on public.blink_contest_votes(entry_id);
alter table public.blink_contest_votes
  add column review_status text not null default 'valid'
    check (review_status in ('valid','under_review','invalidated')),
  add column review_reason text,
  add column reviewed_by uuid references auth.users(id),
  add column reviewed_at timestamptz;
create index blink_contest_votes_review_idx
  on public.blink_contest_votes(contest_id,review_status,created_at);


create table public.blink_contest_prizes (
  id uuid primary key default gen_random_uuid(),
  contest_id uuid not null references public.blink_contests(id) on delete restrict,
  entry_id uuid not null,
  user_id uuid not null references auth.users(id),
  place integer not null check (place between 1 and 3),
  amount_ngn integer not null check (amount_ngn >= 0),
  payment_status text not null default 'pending'
    check (payment_status in ('pending','processing','failed','paid')),
  payment_reference text,
  payment_note text,
  announced_at timestamptz not null default now(),
  processed_at timestamptz,
  processed_by uuid references auth.users(id),
  foreign key (contest_id,entry_id) references public.blink_contest_entries(contest_id,id),
  unique(contest_id,place),
  unique(contest_id,entry_id)
);
-- Direct table writes cannot bypass server-side time, identity, and approval rules.
alter table public.blink_contests enable row level security;
alter table public.blink_contest_entries enable row level security;
alter table public.blink_contest_votes enable row level security;
alter table public.blink_contest_prizes enable row level security;
revoke all on table public.blink_contests from public, anon, authenticated;
revoke all on table public.blink_contest_entries from public, anon, authenticated;
revoke all on table public.blink_contest_votes from public, anon, authenticated;
revoke all on table public.blink_contest_prizes from public, anon, authenticated;
grant select, insert, update, delete on table public.blink_contests to service_role;
grant select, insert, update, delete on table public.blink_contest_entries to service_role;
grant select, insert, update, delete on table public.blink_contest_votes to service_role;
grant select, insert, update, delete on table public.blink_contest_prizes to service_role;

-- Historical leaderboard baseline: at most 50 rank rows refreshed once per
-- 30-minute activity window. This is not a fabricated rank-change indicator.
create table public.blink_contest_rank_baselines (
  contest_id uuid not null references public.blink_contests(id) on delete cascade,
  entry_id uuid not null references public.blink_contest_entries(id) on delete cascade,
  rank integer not null check (rank between 1 and 50),
  votes integer not null check (votes >= 0),
  captured_at timestamptz not null default now(),
  primary key(contest_id,entry_id)
);
create index blink_contest_baseline_fresh_idx
  on public.blink_contest_rank_baselines(contest_id,captured_at desc);
alter table public.blink_contest_rank_baselines enable row level security;
revoke all on public.blink_contest_rank_baselines from public,anon,authenticated;
grant select,insert,update,delete on public.blink_contest_rank_baselines to service_role;

create function private.blink_capture_rank_baseline(p_contest uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  -- Prevent simultaneous writers from multiplying expensive rankings.
  if not pg_try_advisory_xact_lock(hashtextextended(p_contest::text, 90210)) then
    return;
  end if;
  if exists(select 1 from public.blink_contest_rank_baselines
    where contest_id=p_contest and captured_at > now()-interval '30 minutes') then
    return;
  end if;
  -- Preserve earlier baseline rows and update only the candidate's own snapshot.
  insert into public.blink_contest_rank_baselines(contest_id,entry_id,rank,votes)
  select p_contest,r.id,r.rank,r.votes
  from (
    select e.id, row_number() over (
      order by count(v.id) filter (where v.review_status='valid') desc,
        e.submitted_at asc, e.id asc)::integer rank,
      count(v.id) filter (where v.review_status='valid')::integer votes
    from public.blink_contest_entries e
    left join public.blink_contest_votes v on v.entry_id=e.id
    where e.contest_id=p_contest and e.status='approved'
    group by e.id
  ) r where r.rank<=50
  on conflict(contest_id,entry_id) do update
    set rank=excluded.rank,votes=excluded.votes,captured_at=now();
end;
$$;
revoke all on function private.blink_capture_rank_baseline(uuid)
  from public,anon,authenticated;
grant execute on function private.blink_capture_rank_baseline(uuid) to service_role;

create function private.blink_rank_baseline_vote_trigger()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform private.blink_capture_rank_baseline(new.contest_id);
  return new;
end;
$$;
revoke all on function private.blink_rank_baseline_vote_trigger()
  from public,anon,authenticated;
-- Snapshot BEFORE a vote changes a rank, not after the result is known.
-- No baseline is fabricated for ranks whose historical position was never captured.
create trigger blink_contest_rank_baseline_vote
before insert or update of review_status on public.blink_contest_votes
for each row execute function private.blink_rank_baseline_vote_trigger();

create function public.list_blink_contests()
returns jsonb language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'id',c.id,'title',c.title,'description',c.description,'rules',c.rules,
    'category',c.category,'banner_url',c.banner_url,'university',c.university,
    'prize_first',c.prize_first,'prize_second',c.prize_second,'prize_third',c.prize_third,
    'registration_opens_at',c.registration_opens_at,
    'registration_closes_at',c.registration_closes_at,
    'voting_opens_at',c.voting_opens_at,'voting_closes_at',c.voting_closes_at,
    'results_at',c.results_at,'status',
      case when c.status = 'completed' then 'completed'
           when c.status = 'cancelled' then 'cancelled'
           when c.status = 'paused' then 'paused'
           when now() < c.registration_opens_at then 'upcoming'
           when now() < c.registration_closes_at then 'registration'
           when now() < c.voting_opens_at then 'upcoming'
           when now() < c.voting_closes_at then 'voting'
           else 'review' end,
    'contestants',(select count(*) from public.blink_contest_entries e
        where e.contest_id=c.id and e.status in ('approved','pending'))
  ) order by c.voting_opens_at desc),'[]'::jsonb)
  from public.blink_contests c where c.status in ('published','paused','completed');
$$;

create function public.get_blink_contest(p_contest_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare c public.blink_contests%rowtype; v_top jsonb; v_myrank integer;
  v_entry uuid; v_vote uuid; v_total integer;
begin
  select * into c from public.blink_contests where id=p_contest_id
    and status in ('published','paused','completed');
  if not found then raise exception 'CONTEST_NOT_AVAILABLE'; end if;
  with scores as (
    select e.id, e.user_id, e.statement, e.media_url, e.submitted_at,
           count(v.id) filter (where v.review_status='valid')::integer votes
    from public.blink_contest_entries e
    left join public.blink_contest_votes v on v.entry_id=e.id
    where e.contest_id=p_contest_id and e.status='approved'
    group by e.id
  ), ranking as (
    select s.*,row_number() over (order by votes desc,submitted_at asc,id asc)::integer rank
    from scores s
  )
  select
    coalesce((select jsonb_agg(jsonb_build_object(
      'id',r.id,'user_id',r.user_id,'username',p.username,
      'avatar_url',p.avatar_url,'statement',r.statement,'media_url',r.media_url,
      'votes',r.votes,'rank',r.rank,
      'rank_delta',(select b.rank-r.rank from public.blink_contest_rank_baselines b
         where b.contest_id=p_contest_id and b.entry_id=r.id)) order by r.rank)
      from ranking r left join public.profiles p on p.id=r.user_id
      where r.rank<=20),'[]'::jsonb),
    (select rank from ranking where user_id=auth.uid()),
    (select count(*)::integer from ranking)
  into v_top,v_myrank,v_total;

  select id into v_entry from public.blink_contest_entries
    where contest_id=p_contest_id and user_id=auth.uid();
  select entry_id into v_vote from public.blink_contest_votes
    where contest_id=p_contest_id and voter_id=auth.uid();

  return jsonb_build_object(
    'contest',(select value from jsonb_array_elements(public.list_blink_contests()) as j(value)
       where value->>'id'=p_contest_id::text limit 1),
    'leaderboard',v_top,'my_rank',v_myrank,
    'my_rank_delta',(select b.rank-v_myrank from public.blink_contest_rank_baselines b
        where b.contest_id=p_contest_id and b.entry_id=v_entry),
    'my_entry_id',v_entry,
    'my_vote_entry_id',v_vote,'total_ranked',v_total,
    'my_entry_status',(select status from public.blink_contest_entries
       where id=v_entry),
    'winners',coalesce((select jsonb_agg(jsonb_build_object(
      'place',w.place,'user_id',w.user_id,'username',p.username,
      'avatar_url',p.avatar_url,
      'prize_amount',w.amount_ngn,'payment_status',w.payment_status,
      'announced_at',w.announced_at) order by w.place)
      from public.blink_contest_prizes w
      left join public.profiles p on p.id=w.user_id
      where w.contest_id=p_contest_id),'[]'::jsonb)
  );
end; $$;

create function public.apply_blink_contest(p_contest_id uuid,p_statement text,p_media_url text default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare c public.blink_contests%rowtype; u uuid:=auth.uid(); v_id uuid; v_uni text;
begin
  if u is null or coalesce(auth.jwt()->>'is_anonymous','false')='true' then
    raise exception 'SIGN_IN_REQUIRED' using errcode='42501'; end if;
  if p_statement is null or length(trim(p_statement)) not between 10 and 500
    then raise exception 'INVALID_ENTRY'; end if;
  if p_media_url is not null and
     (length(p_media_url)>800 or p_media_url !~ '^https://')
     then raise exception 'INVALID_MEDIA_URL'; end if;
  select * into c from public.blink_contests where id=p_contest_id for update;
  if not found or c.status<>'published' or now()<c.registration_opens_at
     or now()>=c.registration_closes_at then raise exception 'REGISTRATION_CLOSED'; end if;
  if c.university is not null then
    select p.university into v_uni from public.profiles p where p.id=u;
    if lower(trim(coalesce(v_uni,'')))<>lower(trim(c.university)) then
      raise exception 'CAMPUS_NOT_ELIGIBLE'; end if;
  end if;
  if c.max_entries is not null and
     (select count(*) from public.blink_contest_entries where contest_id=c.id)>=c.max_entries
     then raise exception 'CONTEST_FULL'; end if;
  insert into public.blink_contest_entries(contest_id,user_id,statement,media_url,status)
  values (p_contest_id,u,trim(p_statement),nullif(trim(p_media_url),''),
    case when c.auto_approve then 'approved' else 'pending' end)
  returning id into v_id;
  return jsonb_build_object('entry_id',v_id,
    'status',case when c.auto_approve then 'approved' else 'pending' end);
end; $$;

create function public.vote_blink_contest(p_contest_id uuid,p_entry_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare c public.blink_contests%rowtype; e public.blink_contest_entries%rowtype;
  u uuid:=auth.uid();
begin
  if u is null then raise exception 'SIGN_IN_REQUIRED' using errcode='42501'; end if;
  -- Serialize with admin pause/cancel and end-time checks; unique constraint
  -- prevents double-voting under concurrent requests or client retries.
  select * into c from public.blink_contests where id=p_contest_id for update;
  if not found or c.status<>'published' or now()<c.voting_opens_at
     or now()>=c.voting_closes_at then raise exception 'VOTING_CLOSED'; end if;
  select * into e from public.blink_contest_entries where id=p_entry_id
    and contest_id=p_contest_id and status='approved';
  if not found then raise exception 'ENTRY_NOT_ELIGIBLE'; end if;
  if e.user_id=u then raise exception 'SELF_VOTE_NOT_ALLOWED'; end if;
  insert into public.blink_contest_votes(contest_id,entry_id,voter_id)
    values(p_contest_id,p_entry_id,u);
  return jsonb_build_object('success',true,'entry_id',p_entry_id);
end; $$;

create function public.admin_list_blink_contests()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_blink_admin();
  return coalesce((select jsonb_agg(to_jsonb(c) order by c.created_at desc)
     from public.blink_contests c),'[]'::jsonb);
end; $$;

create function public.admin_save_blink_contest(p_id uuid,p_data jsonb)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare u uuid:=private.require_blink_admin(); c public.blink_contests%rowtype;
  ro timestamptz; rc timestamptz; vo timestamptz; vc timestamptz; ra timestamptz;
  f int; s int; t int; v_title text;
begin
  if p_data is null or jsonb_typeof(p_data)<>'object'
    or octet_length(p_data::text)>10000 then raise exception 'INVALID_CONTEST'; end if;
  v_title:=trim(coalesce(p_data->>'title',''));
  ro:=(p_data->>'registration_opens_at')::timestamptz;
  rc:=(p_data->>'registration_closes_at')::timestamptz;
  vo:=(p_data->>'voting_opens_at')::timestamptz;
  vc:=(p_data->>'voting_closes_at')::timestamptz;
  ra:=(p_data->>'results_at')::timestamptz;
  f:=coalesce((p_data->>'prize_first')::int,0);
  s:=coalesce((p_data->>'prize_second')::int,0);
  t:=coalesce((p_data->>'prize_third')::int,0);
  if length(v_title) not between 4 and 100 or f<0 or s<0 or t<0
     or ro is null or rc is null or vo is null or vc is null or ra is null
     or not (ro<rc and rc<=vo and vo<vc and vc<=ra)
     then raise exception 'INVALID_CONTEST_CONFIGURATION'; end if;
  if p_id is not null then
    select * into c from public.blink_contests where id=p_id for update;
    if not found or c.status<>'draft' then raise exception 'ONLY_DRAFTS_EDITABLE'; end if;
    update public.blink_contests set
      title=v_title, description=coalesce(p_data->>'description',''),
      rules=coalesce(p_data->>'rules',''),category=coalesce(p_data->>'category','General'),
      banner_url=nullif(p_data->>'banner_url',''),
      university=nullif(p_data->>'university',''),
      registration_opens_at=ro, registration_closes_at=rc,
      voting_opens_at=vo, voting_closes_at=vc, results_at=ra,
      prize_first=f,prize_second=s,prize_third=t,
      max_entries=nullif(p_data->>'max_entries','')::integer,
      auto_approve=coalesce((p_data->>'auto_approve')::boolean,false),
      updated_at=now() where id=p_id returning * into c;
  else
    insert into public.blink_contests(title,description,rules,category,banner_url,
      university,registration_opens_at,registration_closes_at,voting_opens_at,
      voting_closes_at,results_at,prize_first,prize_second,prize_third,
      max_entries,auto_approve,created_by)
    values(v_title,coalesce(p_data->>'description',''),coalesce(p_data->>'rules',''),
      coalesce(p_data->>'category','General'),nullif(p_data->>'banner_url',''),
      nullif(p_data->>'university',''),ro,rc,vo,vc,ra,f,s,t,
      nullif(p_data->>'max_entries','')::integer,
      coalesce((p_data->>'auto_approve')::boolean,false),u)
    returning * into c;
  end if;
  return to_jsonb(c);
end; $$;

create function public.admin_transition_blink_contest(p_contest_id uuid,p_action text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare c public.blink_contests%rowtype;
begin
  perform private.require_blink_admin();
  select * into c from public.blink_contests where id=p_contest_id for update;
  if not found then raise exception 'CONTEST_NOT_FOUND'; end if;
  if not ((p_action='publish' and c.status='draft' and c.voting_closes_at>now())
     or (p_action='pause' and c.status='published')
     or (p_action='resume' and c.status='paused' and c.voting_closes_at>now())
     or (p_action='cancel' and c.status in ('draft','published','paused')))
     then raise exception 'INVALID_CONTEST_TRANSITION'; end if;
  update public.blink_contests set status=case p_action
      when 'publish' then 'published' when 'resume' then 'published'
      when 'pause' then 'paused' when 'complete' then 'completed'
      else 'cancelled' end,updated_at=now()
    where id=p_contest_id returning * into c;
  return to_jsonb(c);
end; $$;

create function public.admin_review_blink_contest_entry(p_entry_id uuid,p_action text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare e public.blink_contest_entries%rowtype; c public.blink_contests%rowtype;
begin
  perform private.require_blink_admin();
  select * into e from public.blink_contest_entries where id=p_entry_id for update;
  if not found then raise exception 'ENTRY_NOT_FOUND'; end if;
  select * into c from public.blink_contests where id=e.contest_id;
  if c.status in ('completed','cancelled') or now()>=c.voting_closes_at then
    raise exception 'REVIEW_LOCKED_AFTER_VOTING'; end if;
  if p_action not in ('approve','reject','disqualify') then
    raise exception 'INVALID_REVIEW_ACTION'; end if;
  update public.blink_contest_entries set status=case p_action
      when 'approve' then 'approved' when 'reject' then 'rejected'
      else 'disqualified' end, reviewed_at=now()
    where id=p_entry_id returning * into e;
  return to_jsonb(e);
end; $$;


create function public.admin_list_blink_contest_entries(p_contest_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_blink_admin();
  return coalesce((select jsonb_agg(jsonb_build_object(
     'id',e.id,'contest_id',e.contest_id,'user_id',e.user_id,
     'username',p.username,'statement',e.statement,'media_url',e.media_url,
     'status',e.status,'submitted_at',e.submitted_at)
     order by e.submitted_at desc)
   from public.blink_contest_entries e
   left join public.profiles p on p.id=e.user_id
   where e.contest_id=p_contest_id),'[]'::jsonb);
end; $$;


-- Public, approved-only contestant lookup permits a share link to target users
-- outside the top 20 without revealing pending or rejected applications.
create function public.get_blink_contest_candidate(
  p_contest_id uuid, p_entry_id uuid
) returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare v_candidate jsonb;
begin
  with ranked as (
    select e.id, e.contest_id, e.user_id, e.statement, e.media_url,
           e.submitted_at, count(v.id) filter (where v.review_status='valid')::integer as votes,
           row_number() over (
             order by count(v.id) filter (where v.review_status='valid') desc, e.submitted_at asc, e.id asc
           )::integer as rank
    from public.blink_contest_entries e
    left join public.blink_contest_votes v on v.entry_id=e.id
    join public.blink_contests c on c.id=e.contest_id
    where c.id=p_contest_id and c.status in ('published','paused','completed')
      and e.status='approved'
    group by e.id
  )
  select jsonb_build_object(
    'id',r.id,'contest_id',r.contest_id,'user_id',r.user_id,
    'username',p.username,'name',coalesce(nullif(p.full_name,''),p.username),
    'avatar_url',p.avatar_url,'statement',r.statement,'media_url',r.media_url,
    'votes',r.votes,'rank',r.rank,
    'rank_delta',(select b.rank-r.rank from public.blink_contest_rank_baselines b
      where b.contest_id=p_contest_id and b.entry_id=r.id),
    'contest_title',c.title,
    'contest_status',case when c.status='paused' then 'paused'
       when c.status='completed' then 'completed'
       when now()<c.voting_opens_at then 'upcoming'
       when now()>=c.voting_closes_at then 'review'
       else 'voting' end,
    'voting_closes_at',c.voting_closes_at
  ) into v_candidate
  from ranked r
  join public.profiles p on p.id=r.user_id
  join public.blink_contests c on c.id=r.contest_id
  where r.id=p_entry_id;
  if v_candidate is null then raise exception 'CANDIDATE_NOT_AVAILABLE'; end if;
  return v_candidate;
end; $$;

-- PUBLIC function EXECUTE is implicit on PostgreSQL; explicitly restrict it.
revoke all on function public.get_blink_contest_candidate(uuid,uuid) from public,anon,authenticated;
revoke all on function public.list_blink_contests() from public,anon,authenticated;
revoke all on function public.get_blink_contest(uuid) from public,anon,authenticated;
revoke all on function public.apply_blink_contest(uuid,text,text) from public,anon,authenticated;
revoke all on function public.vote_blink_contest(uuid,uuid) from public,anon,authenticated;
revoke all on function public.admin_list_blink_contests() from public,anon,authenticated;
revoke all on function public.admin_list_blink_contest_entries(uuid) from public,anon,authenticated;
revoke all on function public.admin_save_blink_contest(uuid,jsonb) from public,anon,authenticated;
revoke all on function public.admin_transition_blink_contest(uuid,text) from public,anon,authenticated;
revoke all on function public.admin_review_blink_contest_entry(uuid,text) from public,anon,authenticated;
grant execute on function public.list_blink_contests(),public.get_blink_contest(uuid),
  public.get_blink_contest_candidate(uuid,uuid) to anon,authenticated;
grant execute on function public.apply_blink_contest(uuid,text,text),
  public.vote_blink_contest(uuid,uuid),
  public.admin_list_blink_contests(),
  public.admin_list_blink_contest_entries(uuid),
  public.admin_save_blink_contest(uuid,jsonb),
  public.admin_transition_blink_contest(uuid,text),
  public.admin_review_blink_contest_entry(uuid,text) to authenticated;
notify pgrst,'reload schema';
commit;
