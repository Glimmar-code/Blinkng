-- Preserve the Completed tab for contests that were publicly launched and later cancelled.
-- A cancelled *draft* remains invisible so unpublished prize/entry plans stay private.
begin;

alter table public.blink_contests add column has_been_published boolean not null default false;
-- Existing active/finalized contests were necessarily launched. The historical
-- state of already cancelled contests cannot be inferred; keep those hidden.
update public.blink_contests set has_been_published=true
  where status in ('published','paused','completed');

create or replace function public.list_blink_contests()
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
  from public.blink_contests c where c.status in ('published','paused','completed')
    or (c.status='cancelled' and c.has_been_published);
$$;

create or replace function public.get_blink_contest(p_contest_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare c public.blink_contests%rowtype; v_top jsonb; v_myrank integer;
  v_entry uuid; v_vote uuid; v_total integer;
begin
  select * into c from public.blink_contests where id=p_contest_id
    and (status in ('published','paused','completed')
      or (status='cancelled' and has_been_published));
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

create or replace function public.admin_transition_blink_contest(p_contest_id uuid,p_action text)
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
      else 'cancelled' end,
      has_been_published=case when p_action in ('publish','resume') then true
        else c.has_been_published end,updated_at=now()
    where id=p_contest_id returning * into c;
  return to_jsonb(c);
end; $$;

notify pgrst,'reload schema';
commit;
