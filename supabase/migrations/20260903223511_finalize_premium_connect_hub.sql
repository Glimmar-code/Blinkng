begin;

create or replace function public.get_connect_request_inbox(p_limit integer default 100)
returns table(
  kind text,
  request_id uuid,
  direction text,
  status text,
  listing_id uuid,
  other_user_id uuid,
  created_at timestamptz,
  title text
)
language sql
stable
set search_path = public, pg_temp
as $$
  select * from (
    select 'roommate'::text, ra.id,
      case when ra.applicant_id = (select auth.uid()) then 'outgoing' else 'incoming' end,
      ra.status, ra.roommate_profile_id,
      case when ra.applicant_id = (select auth.uid()) then rp.user_id else ra.applicant_id end,
      ra.created_at, coalesce(rp.title, 'Roommate request')
    from public.roommate_applications ra
    join public.roommate_profiles rp on rp.id = ra.roommate_profile_id
    where ra.applicant_id = (select auth.uid()) or rp.user_id = (select auth.uid())

    union all
    select 'mentor', mr.id,
      case when mr.requester_id = (select auth.uid()) then 'outgoing' else 'incoming' end,
      mr.status, mr.mentor_profile_id,
      case when mr.requester_id = (select auth.uid()) then mp.user_id else mr.requester_id end,
      mr.created_at, coalesce(mp.headline, 'Mentor request')
    from public.mentor_requests mr
    join public.mentor_profiles mp on mp.id = mr.mentor_profile_id
    where mr.requester_id = (select auth.uid()) or mp.user_id = (select auth.uid())

    union all
    select 'reading', rr.id,
      case when rr.requester_id = (select auth.uid()) then 'outgoing' else 'incoming' end,
      rr.status, rr.reading_profile_id,
      case when rr.requester_id = (select auth.uid()) then rp.user_id else rr.requester_id end,
      rr.created_at, 'Reading mate request'
    from public.reading_mate_requests rr
    join public.reading_mate_profiles rp on rp.id = rr.reading_profile_id
    where rr.requester_id = (select auth.uid()) or rp.user_id = (select auth.uid())

    union all
    select 'housing', ha.id,
      case when agent.user_id = (select auth.uid()) then 'outgoing' else 'incoming' end,
      ha.status, ha.housing_request_id,
      case when agent.user_id = (select auth.uid()) then hr.student_id else agent.user_id end,
      ha.created_at, coalesce(hr.title, 'Housing request')
    from public.housing_request_applications ha
    join public.housing_requests hr on hr.id = ha.housing_request_id
    join public.housing_agent_profiles agent on agent.id = ha.agent_profile_id
    where agent.user_id = (select auth.uid()) or hr.student_id = (select auth.uid())

    union all
    select 'game', gc.id,
      case when gc.challenger_id = (select auth.uid()) then 'outgoing' else 'incoming' end,
      gc.status, null::uuid,
      case when gc.challenger_id = (select auth.uid()) then gc.opponent_id else gc.challenger_id end,
      gc.created_at, initcap(replace(gc.game_type, '_', ' ')) || ' challenge'
    from public.game_challenges gc
    where gc.challenger_id = (select auth.uid()) or gc.opponent_id = (select auth.uid())
  ) q
  order by created_at desc
  limit greatest(1, least(coalesce(p_limit, 100), 200));
$$;

revoke all on function public.get_connect_request_inbox(integer) from public, anon;
grant execute on function public.get_connect_request_inbox(integer) to authenticated;

create or replace function public.record_game_session(
  p_game_type text,
  p_score integer,
  p_coins_earned integer default 0
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_session_id uuid;
  v_score integer;
  v_coins integer;
  v_daily_points integer;
  v_game_type text := case lower(btrim(coalesce(p_game_type, '')))
    when 'trivia' then 'general_knowledge'
    when 'math' then 'math_sprint'
    when 'speed' then 'brain_mix'
    else lower(btrim(coalesce(p_game_type, '')))
  end;
begin
  if auth.uid() is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;
  if v_game_type not in (
    'brain_mix', 'math_sprint', 'logic', 'memory', 'word_power', 'general_knowledge'
  ) then
    raise exception 'INVALID_GAME_TYPE';
  end if;
  if p_score < 0 then
    raise exception 'INVALID_SCORE';
  end if;

  v_score := least(p_score, 500);
  v_coins := least(30, floor(v_score / 20.0)::integer);

  select coalesce(sum(score), 0)
  into v_daily_points
  from public.game_sessions
  where user_id = auth.uid()
    and started_at >= date_trunc('day', now());

  if v_daily_points + v_score > 5000 then
    raise exception 'DAILY_GAME_SCORE_LIMIT_REACHED';
  end if;

  if exists (
    select 1
    from public.game_sessions
    where user_id = auth.uid()
      and started_at > now() - interval '3 seconds'
  ) then
    raise exception 'GAME_SESSION_RATE_LIMITED';
  end if;

  insert into public.game_sessions(user_id, game_type, score, coins_earned, completed_at)
  values(auth.uid(), v_game_type, v_score, v_coins, now())
  returning id into v_session_id;

  insert into public.game_profiles(user_id, score, coins, updated_at)
  values(auth.uid(), v_score, v_coins, now())
  on conflict(user_id) do update set
    score = public.game_profiles.score + excluded.score,
    coins = public.game_profiles.coins + excluded.coins,
    updated_at = now();

  update public.profiles
  set points = coalesce(points, 0) + v_score,
      updated_at = now()
  where id = auth.uid();

  return v_session_id;
end;
$$;

revoke all on function public.record_game_session(text, integer, integer) from public, anon;
grant execute on function public.record_game_session(text, integer, integer) to authenticated;

-- Blink is designed for students, so the chance-based reward endpoint is no longer client-callable.
revoke all on function public.claim_daily_spin() from public, anon, authenticated;
grant execute on function public.claim_daily_spin() to service_role;

notify pgrst, 'reload schema';
commit;
