-- Search approved contestants beyond the top-20 leaderboard without exposing
-- draft, pending, rejected or disqualified entries. Deliberately bounded pages.
begin;

create function public.search_blink_contest_candidates(
  p_contest_id uuid,
  p_query text default '',
  p_limit integer default 20,
  p_offset integer default 0
) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
  v_term text := lower(btrim(coalesce(p_query, '')));
  v_limit integer := least(greatest(coalesce(p_limit, 20), 1), 30);
  v_offset integer := least(greatest(coalesce(p_offset, 0), 0), 10000);
  v_result jsonb;
begin
  if length(v_term) > 60 or length(v_term) = 1 then
    raise exception 'CANDIDATE_SEARCH_TERM_INVALID' using errcode = '22023';
  end if;
  if not exists(
    select 1 from public.blink_contests
    where id = p_contest_id and status in ('published','paused','completed')
  ) then
    raise exception 'CONTEST_NOT_AVAILABLE' using errcode = '22023';
  end if;

  with scores as (
    select e.id, e.contest_id, e.user_id, e.statement, e.media_url,
           e.submitted_at,
           count(v.id) filter (where v.review_status='valid')::integer as votes
    from public.blink_contest_entries e
    left join public.blink_contest_votes v on v.entry_id=e.id
    where e.contest_id=p_contest_id and e.status='approved'
    group by e.id
  ), ranked as (
    select s.*, row_number() over (
      order by s.votes desc,s.submitted_at asc,s.id asc
    )::integer as rank
    from scores s
  ), filtered as (
    select r.*, p.username,p.full_name,p.avatar_url
    from ranked r
    join public.profiles p on p.id=r.user_id
    where v_term='' or
       strpos(lower(coalesce(p.username,'')),v_term)>0 or
       strpos(lower(coalesce(p.full_name,'')),v_term)>0
  ), page as (
    select * from filtered order by rank limit v_limit offset v_offset
  )
  select jsonb_build_object(
    'total',(select count(*)::integer from filtered),
    'offset',v_offset,
    'limit',v_limit,
    'entries',coalesce((select jsonb_agg(jsonb_build_object(
      'id',r.id,'contest_id',r.contest_id,'user_id',r.user_id,
      'username',r.username,'name',coalesce(nullif(r.full_name,''),r.username),
      'avatar_url',r.avatar_url,'statement',r.statement,'media_url',r.media_url,
      'votes',r.votes,'rank',r.rank
    ) order by r.rank) from page r),'[]'::jsonb)
  ) into v_result;
  return v_result;
end; $$;

revoke all on function public.search_blink_contest_candidates(uuid,text,integer,integer)
  from public,anon,authenticated;
grant execute on function public.search_blink_contest_candidates(uuid,text,integer,integer)
  to anon,authenticated;

notify pgrst,'reload schema';
commit;
