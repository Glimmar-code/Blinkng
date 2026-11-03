begin;

create schema if not exists private;

create table if not exists public.game_questions (
  id text primary key,
  game_type text not null check (game_type in ('brain_mix','math_sprint','logic','memory','word_power','general_knowledge')),
  category text not null,
  prompt text not null,
  options text[] not null check (cardinality(options) between 2 and 6),
  correct_index integer not null check (correct_index >= 0),
  explanation text not null default '',
  difficulty smallint not null default 1 check (difficulty between 1 and 3),
  time_limit_seconds integer check (time_limit_seconds is null or time_limit_seconds between 5 and 120),
  stimulus text,
  is_daily boolean not null default false,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (correct_index < cardinality(options))
);

create index if not exists game_questions_type_active_idx
  on public.game_questions(game_type, is_active, difficulty);
create index if not exists game_questions_daily_idx
  on public.game_questions(is_daily, is_active) where is_daily = true;

create table if not exists public.game_rounds (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade default auth.uid(),
  game_type text not null check (game_type in ('brain_mix','math_sprint','logic','memory','word_power','general_knowledge')),
  question_ids text[] not null,
  question_count integer not null check (question_count between 1 and 10),
  score integer not null default 0 check (score >= 0),
  coins_earned integer not null default 0 check (coins_earned >= 0),
  correct_count integer not null default 0 check (correct_count >= 0),
  status text not null default 'active' check (status in ('active','completed','abandoned','expired')),
  challenge_id uuid references public.game_challenges(id) on delete set null,
  is_daily boolean not null default false,
  started_at timestamptz not null default now(),
  completed_at timestamptz,
  expires_at timestamptz not null default (now() + interval '2 hours')
);

create index if not exists game_rounds_user_active_idx
  on public.game_rounds(user_id, status, started_at desc);
create index if not exists game_rounds_user_completed_idx
  on public.game_rounds(user_id, completed_at desc) where status = 'completed';

create table if not exists public.game_attempts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade default auth.uid(),
  round_id uuid not null references public.game_rounds(id) on delete cascade,
  question_id text not null references public.game_questions(id) on delete restrict,
  selected_index integer not null check (selected_index >= -1),
  correct boolean not null,
  score_awarded integer not null default 0 check (score_awarded >= 0),
  coins_awarded integer not null default 0 check (coins_awarded >= 0),
  response_ms integer check (response_ms is null or response_ms between 0 and 120000),
  created_at timestamptz not null default now(),
  unique(user_id, round_id, question_id)
);

create index if not exists game_attempts_user_created_idx
  on public.game_attempts(user_id, created_at desc);
create index if not exists game_attempts_question_user_idx
  on public.game_attempts(question_id, user_id, created_at desc);

create table if not exists public.game_saved_questions (
  user_id uuid not null references auth.users(id) on delete cascade default auth.uid(),
  question_id text not null references public.game_questions(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(user_id, question_id)
);

create table if not exists public.game_question_reports (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade default auth.uid(),
  question_id text not null references public.game_questions(id) on delete cascade,
  reason text not null check (char_length(reason) between 2 and 80),
  details text not null default '' check (char_length(details) <= 500),
  status text not null default 'open' check (status in ('open','reviewing','resolved','dismissed')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(user_id, question_id)
);

create table if not exists public.game_coin_ledger (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade default auth.uid(),
  round_id uuid references public.game_rounds(id) on delete set null,
  question_id text references public.game_questions(id) on delete set null,
  delta integer not null check (delta <> 0),
  reason text not null,
  created_at timestamptz not null default now()
);

create index if not exists game_coin_ledger_user_created_idx
  on public.game_coin_ledger(user_id, created_at desc);

alter table public.game_sessions
  add column if not exists round_id uuid references public.game_rounds(id) on delete set null;
create unique index if not exists game_sessions_round_uidx
  on public.game_sessions(round_id) where round_id is not null;

alter table public.game_questions enable row level security;
alter table public.game_rounds enable row level security;
alter table public.game_attempts enable row level security;
alter table public.game_saved_questions enable row level security;
alter table public.game_question_reports enable row level security;
alter table public.game_coin_ledger enable row level security;

revoke all on public.game_questions from anon, authenticated;
revoke all on public.game_rounds from anon, authenticated;
revoke all on public.game_attempts from anon, authenticated;
revoke all on public.game_saved_questions from anon, authenticated;
revoke all on public.game_question_reports from anon, authenticated;
revoke all on public.game_coin_ledger from anon, authenticated;

insert into public.game_questions
(id, game_type, category, prompt, options, correct_index, explanation, difficulty, time_limit_seconds, stimulus, is_daily)
values
('gk_001','general_knowledge','Academics','What does JAMB stand for?',array['Joint Admissions and Matriculation Board','Junior Academic Management Board','Joint Association of Matriculated Brethren','Judicial Academic Monitoring Bureau'],0,'JAMB means Joint Admissions and Matriculation Board.',1,20,null,true),
('gk_002','general_knowledge','Nigeria','Which city is the capital of Nigeria?',array['Lagos','Abuja','Kano','Ibadan'],1,'Abuja is the capital of Nigeria.',1,20,null,false),
('gk_003','general_knowledge','Science','Which planet is closest to the Sun?',array['Venus','Earth','Mercury','Mars'],2,'Mercury is the closest planet to the Sun.',1,20,null,false),
('gk_004','general_knowledge','Technology','What does CPU stand for?',array['Central Processing Unit','Computer Power Utility','Core Program User','Central Program Upload'],0,'CPU stands for Central Processing Unit.',1,20,null,false),
('gk_005','general_knowledge','Academics','On a common 5.0 university grading scale, which classification typically begins at 4.50?',array['Second Class Upper','First Class','Pass','Third Class'],1,'Many Nigerian universities use 4.50–5.00 for First Class.',2,20,null,false),
('math_001','math_sprint','Arithmetic','18 × 7 = ?',array['116','126','136','146'],1,'18 × 7 = 126.',1,15,null,true),
('math_002','math_sprint','Arithmetic','144 ÷ 12 = ?',array['10','11','12','14'],2,'144 ÷ 12 = 12.',1,15,null,false),
('math_003','math_sprint','Percentages','15% of 200 = ?',array['20','25','30','35'],2,'0.15 × 200 = 30.',1,15,null,false),
('math_004','math_sprint','Algebra','If x + 9 = 23, what is x?',array['12','13','14','15'],2,'23 − 9 = 14.',1,15,null,false),
('math_005','math_sprint','Numbers','√225 = ?',array['12','13','14','15'],3,'15 × 15 = 225.',2,15,null,false),
('logic_001','logic','Sequences','What comes next: 2, 6, 12, 20, 30, ?',array['36','40','42','44'],2,'The differences are +4, +6, +8, +10, then +12, so the answer is 42.',2,25,null,true),
('logic_002','logic','Deduction','All Zips are Nors. Some Nors are Veks. Which statement is guaranteed?',array['Some Zips are Veks','All Nors are Zips','All Zips are Nors','No Veks are Zips'],2,'Only the original statement that all Zips are Nors is guaranteed.',2,25,null,false),
('logic_003','logic','Patterns','Which number is the odd one out?',array['16','25','45','49'],2,'16, 25 and 49 are perfect squares; 45 is not.',1,25,null,false),
('logic_004','logic','Spatial','At exactly 3:00, what is the smaller angle between the clock hands?',array['30°','60°','90°','120°'],2,'At 3:00 the hands are 90 degrees apart.',2,25,null,false),
('logic_005','logic','Codes','If CAT becomes DBU by shifting each letter forward by one, DOG becomes?',array['EPH','EOG','FPH','DPI'],0,'D→E, O→P and G→H, giving EPH.',2,25,null,false),
('memory_001','memory','Memory Flash','Which number appeared in the sequence?',array['5','6','7','8'],2,'The sequence contained the number 7.',1,12,'PURPLE • 7 • STAR',true),
('memory_002','memory','Memory Flash','Which item appeared second?',array['Book','Lamp','Tree','Pen'],1,'Lamp was the second item.',1,12,'BOOK • LAMP • TREE',false),
('memory_003','memory','Memory Flash','Which number came immediately after 9?',array['4','2','6','9'],1,'2 followed 9.',1,12,'4 • 9 • 2 • 6',false),
('memory_004','memory','Memory Flash','Which colour appeared last?',array['Red','Blue','Gold','Green'],2,'Gold was the final colour.',1,12,'RED • BLUE • GOLD',false),
('memory_005','memory','Memory Flash','Which number was paired with B?',array['1','3','8','9'],2,'B was paired with 8.',2,12,'A3 • B8 • C1',false),
('brain_001','brain_mix','Pattern Sprint','What comes next: 3, 6, 12, 24, ?',array['30','36','42','48'],3,'Each number doubles, so 24 becomes 48.',1,10,null,true),
('brain_002','brain_mix','Word Power','Which word is closest in meaning to concise?',array['Brief','Noisy','Ancient','Hidden'],0,'Concise means brief and clear.',1,10,null,false),
('brain_003','brain_mix','Quick Logic','If all labs are rooms and this place is a lab, what must be true?',array['It is a room','It is outdoors','It is empty','It is a library'],0,'Under the stated rule, every lab must be a room.',1,10,null,false),
('brain_004','brain_mix','Memory Mix','Which colour appeared?',array['Gold','Green','Blue','Red'],2,'Blue was the colour shown.',1,10,'8 • BLUE • K',false),
('brain_005','brain_mix','Math Sprint','27 + 16 = ?',array['41','42','43','44'],2,'27 + 16 = 43.',1,10,null,false),
('word_001','word_power','Spelling','Choose the correctly spelled word.',array['Accomodate','Acommodate','Accommodate','Acomodate'],2,'Accommodate has two c letters and two m letters.',1,20,null,true),
('word_002','word_power','Vocabulary','What is the opposite of scarce?',array['Rare','Abundant','Small','Costly'],1,'Abundant means available in large quantities.',1,20,null,false),
('word_003','word_power','Analogy','Book is to read as song is to ___?',array['Listen','Write','Draw','Count'],0,'A book is read and a song is listened to.',1,20,null,false),
('word_004','word_power','Grammar','Which word is a noun?',array['Quickly','Create','Curious','Knowledge'],3,'Knowledge names an idea, so it is a noun.',1,20,null,false),
('word_005','word_power','Anagram','Which word can be made by rearranging the letters L I S T E N?',array['Silent','Tinsel','Enlist','All three'],3,'Silent, tinsel and enlist use the same six letters.',2,20,null,false)
on conflict (id) do update set
  game_type = excluded.game_type,
  category = excluded.category,
  prompt = excluded.prompt,
  options = excluded.options,
  correct_index = excluded.correct_index,
  explanation = excluded.explanation,
  difficulty = excluded.difficulty,
  time_limit_seconds = excluded.time_limit_seconds,
  stimulus = excluded.stimulus,
  is_daily = excluded.is_daily,
  is_active = true,
  updated_at = now();

create or replace function private.start_game_round_internal(
  p_game_type text,
  p_question_count integer default 5,
  p_challenge_id uuid default null,
  p_daily boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_type text := case lower(btrim(coalesce(p_game_type,'')))
    when 'trivia' then 'general_knowledge'
    when 'math' then 'math_sprint'
    when 'speed' then 'brain_mix'
    else lower(btrim(coalesce(p_game_type,'')))
  end;
  v_count integer := greatest(3, least(coalesce(p_question_count,5),10));
  v_difficulty integer := 1;
  v_accuracy numeric;
  v_ids text[];
  v_round public.game_rounds%rowtype;
  v_questions jsonb;
  v_answered jsonb;
  v_resumed boolean := false;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if v_type not in ('brain_mix','math_sprint','logic','memory','word_power','general_knowledge') then
    raise exception 'INVALID_GAME_TYPE';
  end if;

  if p_challenge_id is not null and not exists (
    select 1 from public.game_challenges c
    where c.id = p_challenge_id
      and c.status in ('accepted','in_progress')
      and v_uid in (c.challenger_id, c.opponent_id)
  ) then raise exception 'CHALLENGE_NOT_AVAILABLE'; end if;

  update public.game_rounds
  set status = 'expired'
  where user_id = v_uid and status = 'active' and expires_at <= now();

  select * into v_round
  from public.game_rounds r
  where r.user_id = v_uid
    and r.status = 'active'
    and r.game_type = v_type
    and r.is_daily = p_daily
    and r.expires_at > now()
    and ((p_challenge_id is null and r.challenge_id is null) or r.challenge_id = p_challenge_id)
  order by r.started_at desc
  limit 1;

  if found then
    v_resumed := true;
  else
    select avg(case when a.correct then 1.0 else 0.0 end)
      into v_accuracy
    from (
      select correct
      from public.game_attempts
      where user_id = v_uid
      order by created_at desc
      limit 50
    ) a;

    v_difficulty := case
      when coalesce(v_accuracy,0) >= 0.82 then 3
      when coalesce(v_accuracy,0) >= 0.58 then 2
      else 1
    end;

    select array_agg(x.id order by x.sort_recent, x.rnd)
      into v_ids
    from (
      select q.id,
        case when exists (
          select 1 from public.game_attempts ga
          where ga.user_id = v_uid
            and ga.question_id = q.id
            and ga.created_at > now() - interval '7 days'
        ) then 1 else 0 end as sort_recent,
        random() as rnd
      from public.game_questions q
      where q.is_active = true
        and (case when p_daily then q.is_daily else q.game_type = v_type end)
        and (p_daily or q.difficulty <= greatest(1, v_difficulty))
      order by sort_recent, rnd
      limit v_count
    ) x;

    if coalesce(cardinality(v_ids),0) = 0 then raise exception 'NO_GAME_QUESTIONS_AVAILABLE'; end if;

    insert into public.game_rounds(
      user_id, game_type, question_ids, question_count, challenge_id, is_daily
    ) values (
      v_uid, v_type, v_ids, cardinality(v_ids), p_challenge_id, p_daily
    ) returning * into v_round;
  end if;

  select coalesce(jsonb_agg(
    jsonb_build_object(
      'id', q.id,
      'gameType', q.game_type,
      'category', q.category,
      'prompt', q.prompt,
      'options', to_jsonb(q.options),
      'difficulty', q.difficulty,
      'timeLimitSeconds', q.time_limit_seconds,
      'stimulus', q.stimulus,
      'saved', exists(
        select 1 from public.game_saved_questions s
        where s.user_id = v_uid and s.question_id = q.id
      )
    ) order by u.ord
  ), '[]'::jsonb)
  into v_questions
  from unnest(v_round.question_ids) with ordinality as u(question_id, ord)
  join public.game_questions q on q.id = u.question_id;

  select coalesce(jsonb_agg(a.question_id order by a.created_at), '[]'::jsonb)
    into v_answered
  from public.game_attempts a
  where a.user_id = v_uid and a.round_id = v_round.id;

  return jsonb_build_object(
    'roundId', v_round.id,
    'gameType', v_round.game_type,
    'questions', v_questions,
    'answeredQuestionIds', v_answered,
    'score', v_round.score,
    'coinsEarned', v_round.coins_earned,
    'correctCount', v_round.correct_count,
    'isDaily', v_round.is_daily,
    'resumed', v_resumed
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
  select private.start_game_round_internal(p_game_type,p_question_count,p_challenge_id,p_daily);
$$;

create or replace function private.submit_game_answer_internal(
  p_round_id uuid,
  p_question_id text,
  p_selected_index integer,
  p_response_ms integer default 0
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_round public.game_rounds%rowtype;
  v_question public.game_questions%rowtype;
  v_existing public.game_attempts%rowtype;
  v_correct boolean;
  v_score integer := 0;
  v_coins integer := 0;
  v_speed_bonus integer := 0;
  v_answered integer := 0;
  v_completed boolean := false;
  v_total_score bigint := 0;
  v_total_coins bigint := 0;
  v_streak integer := 0;
  v_best integer := 0;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_selected_index is null or p_selected_index < -1 then raise exception 'INVALID_ANSWER_INDEX'; end if;

  perform pg_advisory_xact_lock(hashtext(p_round_id::text));

  select * into v_round
  from public.game_rounds
  where id = p_round_id and user_id = v_uid
  for update;

  if not found then raise exception 'GAME_ROUND_NOT_FOUND'; end if;
  if v_round.status <> 'active' then raise exception 'GAME_ROUND_NOT_ACTIVE'; end if;
  if v_round.expires_at <= now() then
    update public.game_rounds set status='expired' where id=v_round.id;
    raise exception 'GAME_ROUND_EXPIRED';
  end if;
  if array_position(v_round.question_ids, p_question_id) is null then raise exception 'QUESTION_NOT_IN_ROUND'; end if;

  select * into v_question from public.game_questions where id = p_question_id and is_active = true;
  if not found then raise exception 'QUESTION_NOT_AVAILABLE'; end if;
  if p_selected_index >= cardinality(v_question.options) then raise exception 'INVALID_ANSWER_INDEX'; end if;

  select * into v_existing
  from public.game_attempts
  where user_id = v_uid and round_id = v_round.id and question_id = p_question_id;

  if found then
    select gp.score, gp.coins, gp.streak, gp.best_streak
      into v_total_score, v_total_coins, v_streak, v_best
    from public.game_profiles gp where gp.user_id = v_uid;
    return jsonb_build_object(
      'correct', v_existing.correct,
      'correctIndex', v_question.correct_index,
      'explanation', v_question.explanation,
      'awardedScore', v_existing.score_awarded,
      'awardedCoins', v_existing.coins_awarded,
      'streak', coalesce(v_streak,0),
      'bestStreak', coalesce(v_best,0),
      'totalScore', coalesce(v_total_score,0),
      'totalCoins', coalesce(v_total_coins,0),
      'roundScore', v_round.score,
      'roundCoins', v_round.coins_earned,
      'correctCount', v_round.correct_count,
      'completed', v_round.status='completed',
      'duplicate', true
    );
  end if;

  v_correct := p_selected_index = v_question.correct_index;
  if v_correct then
    if v_question.time_limit_seconds is not null then
      v_speed_bonus := least(20, greatest(0,
        ((v_question.time_limit_seconds * 1000 - greatest(0, least(coalesce(p_response_ms,0),120000))) / 1000) * 2
      ));
    end if;
    v_score := (v_question.difficulty * 40) + v_speed_bonus;
    v_coins := least(5, greatest(1, v_score / 25));
  end if;

  insert into public.game_attempts(
    user_id, round_id, question_id, selected_index, correct,
    score_awarded, coins_awarded, response_ms
  ) values (
    v_uid, v_round.id, p_question_id, p_selected_index, v_correct,
    v_score, v_coins, greatest(0, least(coalesce(p_response_ms,0),120000))
  );

  update public.game_rounds
  set score = score + v_score,
      coins_earned = coins_earned + v_coins,
      correct_count = correct_count + case when v_correct then 1 else 0 end
  where id = v_round.id
  returning * into v_round;

  insert into public.game_profiles(user_id,score,coins,streak,best_streak,updated_at)
  values(
    v_uid, v_score, v_coins,
    case when v_correct then 1 else 0 end,
    case when v_correct then 1 else 0 end,
    now()
  )
  on conflict(user_id) do update set
    score = public.game_profiles.score + excluded.score,
    coins = public.game_profiles.coins + excluded.coins,
    streak = case when v_correct then public.game_profiles.streak + 1 else 0 end,
    best_streak = greatest(
      public.game_profiles.best_streak,
      case when v_correct then public.game_profiles.streak + 1 else 0 end
    ),
    updated_at = now()
  returning score, coins, streak, best_streak
    into v_total_score, v_total_coins, v_streak, v_best;

  if v_coins > 0 then
    insert into public.game_coin_ledger(user_id,round_id,question_id,delta,reason)
    values(v_uid,v_round.id,p_question_id,v_coins,'correct_answer');
  end if;

  select count(*) into v_answered
  from public.game_attempts
  where user_id = v_uid and round_id = v_round.id;

  if v_answered >= v_round.question_count then
    update public.game_rounds
    set status='completed', completed_at=now()
    where id=v_round.id
    returning * into v_round;
    v_completed := true;

    insert into public.game_sessions(user_id,game_type,score,coins_earned,completed_at,round_id)
    values(v_uid,v_round.game_type,v_round.score,v_round.coins_earned,now(),v_round.id)
    on conflict (round_id) where round_id is not null do nothing;
  end if;

  return jsonb_build_object(
    'correct', v_correct,
    'correctIndex', v_question.correct_index,
    'explanation', v_question.explanation,
    'awardedScore', v_score,
    'awardedCoins', v_coins,
    'streak', coalesce(v_streak,0),
    'bestStreak', coalesce(v_best,0),
    'totalScore', coalesce(v_total_score,0),
    'totalCoins', coalesce(v_total_coins,0),
    'roundScore', v_round.score,
    'roundCoins', v_round.coins_earned,
    'correctCount', v_round.correct_count,
    'completed', v_completed,
    'duplicate', false
  );
end;
$$;

create or replace function public.submit_game_answer(
  p_round_id uuid,
  p_question_id text,
  p_selected_index integer,
  p_response_ms integer default 0
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$
  select private.submit_game_answer_internal(p_round_id,p_question_id,p_selected_index,p_response_ms);
$$;

create or replace function private.get_game_dashboard_internal()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_score bigint := 0;
  v_coins bigint := 0;
  v_streak integer := 0;
  v_best integer := 0;
  v_rank integer := 0;
  v_today integer := 0;
  v_today_correct integer := 0;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select coalesce(score,0),coalesce(coins,0),coalesce(streak,0),coalesce(best_streak,0)
    into v_score,v_coins,v_streak,v_best
  from public.game_profiles where user_id=v_uid;
  select coalesce(world_rank,0)::integer into v_rank
  from public.game_rankings where user_id=v_uid;
  select count(*)::integer, count(*) filter (where correct)::integer
    into v_today,v_today_correct
  from public.game_attempts
  where user_id=v_uid and created_at >= date_trunc('day',now());
  return jsonb_build_object(
    'score',coalesce(v_score,0),'coins',coalesce(v_coins,0),
    'streak',coalesce(v_streak,0),'bestStreak',coalesce(v_best,0),
    'worldRank',coalesce(v_rank,0),'todayAnswers',coalesce(v_today,0),
    'todayCorrect',coalesce(v_today_correct,0),'dailyGoal',15
  );
end;
$$;

create or replace function public.get_game_dashboard()
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select private.get_game_dashboard_internal(); $$;

create or replace function private.get_game_history_internal(p_limit integer default 12)
returns jsonb
language sql
security definer
set search_path = ''
stable
as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'id',x.id,'gameType',x.game_type,'score',x.score,
    'coinsEarned',x.coins_earned,'correctCount',x.correct_count,
    'questionCount',x.question_count,'completedAt',x.completed_at
  ) order by x.completed_at desc),'[]'::jsonb)
  from (
    select r.* from public.game_rounds r
    where r.user_id=auth.uid() and r.status='completed'
    order by r.completed_at desc
    limit greatest(1,least(coalesce(p_limit,12),50))
  ) x;
$$;

create or replace function public.get_game_history(p_limit integer default 12)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select private.get_game_history_internal(p_limit); $$;

create or replace function private.toggle_saved_game_question_internal(p_question_id text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_saved boolean;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if not exists(select 1 from public.game_questions where id=p_question_id and is_active=true) then
    raise exception 'QUESTION_NOT_AVAILABLE';
  end if;
  if exists(select 1 from public.game_saved_questions where user_id=v_uid and question_id=p_question_id) then
    delete from public.game_saved_questions where user_id=v_uid and question_id=p_question_id;
    v_saved := false;
  else
    insert into public.game_saved_questions(user_id,question_id) values(v_uid,p_question_id);
    v_saved := true;
  end if;
  return jsonb_build_object('saved',v_saved);
end;
$$;

create or replace function public.toggle_saved_game_question(p_question_id text)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select private.toggle_saved_game_question_internal(p_question_id); $$;

create or replace function private.report_game_question_internal(
  p_question_id text,
  p_reason text,
  p_details text default ''
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare v_uid uuid := auth.uid(); begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if not exists(select 1 from public.game_questions where id=p_question_id) then raise exception 'QUESTION_NOT_FOUND'; end if;
  if char_length(btrim(coalesce(p_reason,''))) < 2 then raise exception 'REPORT_REASON_REQUIRED'; end if;
  insert into public.game_question_reports(user_id,question_id,reason,details,status,updated_at)
  values(v_uid,p_question_id,left(btrim(p_reason),80),left(coalesce(p_details,''),500),'open',now())
  on conflict(user_id,question_id) do update set
    reason=excluded.reason,details=excluded.details,status='open',updated_at=now();
  return true;
end; $$;

create or replace function public.report_game_question(
  p_question_id text,
  p_reason text,
  p_details text default ''
)
returns boolean
language sql
security invoker
set search_path = ''
as $$ select private.report_game_question_internal(p_question_id,p_reason,p_details); $$;

create or replace function private.get_game_leaderboard_internal(
  p_period text default 'all_time',
  p_scope text default 'global',
  p_limit integer default 20
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_period text := lower(coalesce(p_period,'all_time'));
  v_scope text := lower(coalesce(p_scope,'global'));
  v_since timestamptz;
  v_result jsonb;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if v_period not in ('daily','weekly','monthly','all_time') then v_period := 'all_time'; end if;
  if v_scope not in ('global','university','faculty','department','friends') then v_scope := 'global'; end if;
  v_since := case v_period
    when 'daily' then date_trunc('day',now())
    when 'weekly' then date_trunc('week',now())
    when 'monthly' then date_trunc('month',now())
    else null end;

  with me as (
    select id,university,faculty,department from public.profiles where id=v_uid
  ), scores as (
    select gp.user_id,
      case when v_since is null then gp.score::bigint
           else coalesce(sum(ga.score_awarded) filter(where ga.created_at>=v_since),0)::bigint end as score,
      gp.streak
    from public.game_profiles gp
    left join public.game_attempts ga on ga.user_id=gp.user_id
    group by gp.user_id,gp.score,gp.streak
  ), eligible as (
    select p.id,p.username,
      coalesce(nullif(p.name,''),nullif(p.full_name,''),p.username) as display_name,
      coalesce(p.avatar_url,'') as avatar_url,
      coalesce(p.university,'') as university,
      coalesce(p.faculty,'') as faculty,
      coalesce(p.department,'') as department,
      coalesce(p.verification_badge,'NONE') as verification_badge,
      s.score,coalesce(s.streak,0) as streak
    from scores s
    join public.profiles p on p.id=s.user_id
    cross join me
    where s.score > 0 and nullif(trim(p.username),'') is not null
      and (
        v_scope='global'
        or (v_scope='university' and nullif(me.university,'') is not null and lower(p.university)=lower(me.university))
        or (v_scope='faculty' and nullif(me.faculty,'') is not null and lower(p.faculty)=lower(me.faculty))
        or (v_scope='department' and nullif(me.department,'') is not null and lower(p.department)=lower(me.department))
        or (v_scope='friends' and (p.id=v_uid or exists(
          select 1 from public.follows f where f.follower_id=v_uid and f.following_id=p.id
        )))
      )
  ), ranked as (
    select e.*, row_number() over(order by e.score desc,e.username asc)::integer as rank
    from eligible e
  ), limited as (
    select * from ranked order by rank limit greatest(3,least(coalesce(p_limit,20),50))
  )
  select coalesce(jsonb_agg(jsonb_build_object(
    'rank',l.rank,'userId',l.id,'name',l.display_name,'username',l.username,
    'avatarUrl',l.avatar_url,'university',l.university,'faculty',l.faculty,
    'department',l.department,'score',l.score,'streak',l.streak,
    'verificationBadge',l.verification_badge,'isMe',l.id=v_uid
  ) order by l.rank),'[]'::jsonb) into v_result from limited l;
  return coalesce(v_result,'[]'::jsonb);
end;
$$;

create or replace function public.get_game_leaderboard(
  p_period text default 'all_time',
  p_scope text default 'global',
  p_limit integer default 20
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select private.get_game_leaderboard_internal(p_period,p_scope,p_limit); $$;

revoke all on function private.start_game_round_internal(text,integer,uuid,boolean) from public;
revoke all on function private.submit_game_answer_internal(uuid,text,integer,integer) from public;
revoke all on function private.get_game_dashboard_internal() from public;
revoke all on function private.get_game_history_internal(integer) from public;
revoke all on function private.toggle_saved_game_question_internal(text) from public;
revoke all on function private.report_game_question_internal(text,text,text) from public;
revoke all on function private.get_game_leaderboard_internal(text,text,integer) from public;

grant usage on schema private to authenticated;
grant execute on function private.start_game_round_internal(text,integer,uuid,boolean) to authenticated;
grant execute on function private.submit_game_answer_internal(uuid,text,integer,integer) to authenticated;
grant execute on function private.get_game_dashboard_internal() to authenticated;
grant execute on function private.get_game_history_internal(integer) to authenticated;
grant execute on function private.toggle_saved_game_question_internal(text) to authenticated;
grant execute on function private.report_game_question_internal(text,text,text) to authenticated;
grant execute on function private.get_game_leaderboard_internal(text,text,integer) to authenticated;

revoke all on function public.start_game_round(text,integer,uuid,boolean) from public,anon;
revoke all on function public.submit_game_answer(uuid,text,integer,integer) from public,anon;
revoke all on function public.get_game_dashboard() from public,anon;
revoke all on function public.get_game_history(integer) from public,anon;
revoke all on function public.toggle_saved_game_question(text) from public,anon;
revoke all on function public.report_game_question(text,text,text) from public,anon;
revoke all on function public.get_game_leaderboard(text,text,integer) from public,anon;

grant execute on function public.start_game_round(text,integer,uuid,boolean) to authenticated;
grant execute on function public.submit_game_answer(uuid,text,integer,integer) to authenticated;
grant execute on function public.get_game_dashboard() to authenticated;
grant execute on function public.get_game_history(integer) to authenticated;
grant execute on function public.toggle_saved_game_question(text) to authenticated;
grant execute on function public.report_game_question(text,text,text) to authenticated;
grant execute on function public.get_game_leaderboard(text,text,integer) to authenticated;

notify pgrst, 'reload schema';
commit;
