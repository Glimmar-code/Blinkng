begin;

create index if not exists game_attempts_round_idx
  on public.game_attempts(round_id);

create index if not exists game_coin_ledger_round_idx
  on public.game_coin_ledger(round_id);

create index if not exists game_coin_ledger_question_idx
  on public.game_coin_ledger(question_id);

create index if not exists game_question_reports_question_idx
  on public.game_question_reports(question_id);

create index if not exists game_rounds_challenge_idx
  on public.game_rounds(challenge_id)
  where challenge_id is not null;

create index if not exists game_saved_questions_question_idx
  on public.game_saved_questions(question_id);

commit;
