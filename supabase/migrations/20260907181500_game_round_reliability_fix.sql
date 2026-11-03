begin;

create or replace function private.get_existing_game_answer_internal(
  p_round_id uuid,
  p_question_id text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_attempt public.game_attempts%rowtype;
  v_round public.game_rounds%rowtype;
  v_question public.game_questions%rowtype;
  v_total_score bigint := 0;
  v_total_coins bigint := 0;
  v_streak integer := 0;
  v_best integer := 0;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;

  select * into v_round
  from public.game_rounds
  where id = p_round_id and user_id = v_uid;

  if not found then return null; end if;

  select * into v_attempt
  from public.game_attempts
  where user_id = v_uid
    and round_id = p_round_id
    and question_id = p_question_id;

  if not found then return null; end if;

  select * into v_question
  from public.game_questions
  where id = p_question_id;

  select coalesce(score, 0), coalesce(coins, 0), coalesce(streak, 0), coalesce(best_streak, 0)
    into v_total_score, v_total_coins, v_streak, v_best
  from public.game_profiles
  where user_id = v_uid;

  return jsonb_build_object(
    'correct', v_attempt.correct,
    'correctIndex', coalesce(v_question.correct_index, -1),
    'explanation', coalesce(v_question.explanation, ''),
    'awardedScore', v_attempt.score_awarded,
    'awardedCoins', v_attempt.coins_awarded,
    'streak', coalesce(v_streak, 0),
    'bestStreak', coalesce(v_best, 0),
    'totalScore', coalesce(v_total_score, 0),
    'totalCoins', coalesce(v_total_coins, 0),
    'roundScore', v_round.score,
    'roundCoins', v_round.coins_earned,
    'correctCount', v_round.correct_count,
    'completed', v_round.status = 'completed',
    'duplicate', true
  );
end;
$$;

create or replace function public.start_game_round(
  p_game_type text,
  p_question_count integer default 5,
  p_challenge_id uuid default null,
  p_daily boolean default false
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$
  select private.start_game_round_internal(
    p_game_type,
    p_question_count,
    case when p_daily then null else p_challenge_id end,
    p_daily
  );
$$;

create or replace function public.submit_game_answer(
  p_round_id uuid,
  p_question_id text,
  p_selected_index integer,
  p_response_ms integer default 0
)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_existing jsonb;
begin
  v_existing := private.get_existing_game_answer_internal(p_round_id, p_question_id);
  if v_existing is not null then
    return v_existing;
  end if;

  return private.submit_game_answer_internal(
    p_round_id,
    p_question_id,
    p_selected_index,
    p_response_ms
  );
end;
$$;

revoke all on function private.get_existing_game_answer_internal(uuid, text) from public;
grant execute on function private.get_existing_game_answer_internal(uuid, text) to authenticated;

revoke all on function public.start_game_round(text, integer, uuid, boolean) from public, anon;
revoke all on function public.submit_game_answer(uuid, text, integer, integer) from public, anon;
grant execute on function public.start_game_round(text, integer, uuid, boolean) to authenticated;
grant execute on function public.submit_game_answer(uuid, text, integer, integer) to authenticated;

notify pgrst, 'reload schema';
commit;
