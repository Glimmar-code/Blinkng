-- Remaining deep-audit security + reliability fixes, 2026-09-03.

drop policy if exists activities_insert_actor on public.activities;
create policy activities_insert_actor
on public.activities
for insert
to authenticated
with check (
  (select auth.uid()) = actor_id
  and recipient_id is not null
  and recipient_id <> (select auth.uid())
);

revoke execute on function public.record_game_session(text, integer, integer)
from public, anon, authenticated;

revoke execute on function public.record_trivia_result(text, boolean)
from public, anon, authenticated;
drop function if exists public.record_trivia_result(text, boolean);

create or replace function public.record_trivia_result(
  p_question_id text,
  p_selected_index integer
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
  v_question text := lower(btrim(coalesce(p_question_id, '')));
  v_correct_index integer;
  v_correct boolean;
  v_score integer;
  v_coins integer;
  v_streak integer;
  v_best integer;
begin
  if v_uid is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  v_correct_index := case v_question
    when 'q1' then 1
    when 'q2' then 0
    when 'q3' then 2
    when 'q4' then 1
    when 'q5' then 1
    else null
  end;

  if v_correct_index is null then
    raise exception 'VALIDATION_ERROR: invalid question';
  end if;

  if p_selected_index is null or p_selected_index < 0 or p_selected_index > 3 then
    raise exception 'VALIDATION_ERROR: invalid answer index';
  end if;

  perform pg_advisory_xact_lock(
    hashtext(v_uid::text || ':trivia:' || v_question || ':' || current_date::text)
  );

  if exists (
    select 1
    from public.game_sessions
    where user_id = v_uid
      and game_type = 'trivia:' || v_question
      and started_at >= date_trunc('day', now())
  ) then
    raise exception 'ALREADY_ANSWERED_TODAY';
  end if;

  v_correct := p_selected_index = v_correct_index;
  v_score := case when v_correct then 50 else 0 end;
  v_coins := case when v_correct then 15 else 0 end;

  insert into public.game_sessions(
    user_id, game_type, score, coins_earned, completed_at
  )
  values (
    v_uid, 'trivia:' || v_question, v_score, v_coins, now()
  );

  insert into public.game_profiles(
    user_id, score, coins, streak, best_streak, updated_at
  )
  values (
    v_uid,
    v_score,
    v_coins,
    case when v_correct then 1 else 0 end,
    case when v_correct then 1 else 0 end,
    now()
  )
  on conflict (user_id) do update
  set score = public.game_profiles.score + excluded.score,
      coins = public.game_profiles.coins + excluded.coins,
      streak = case
        when v_correct then public.game_profiles.streak + 1
        else 0
      end,
      best_streak = greatest(
        public.game_profiles.best_streak,
        case
          when v_correct then public.game_profiles.streak + 1
          else 0
        end
      ),
      updated_at = now()
  returning streak, best_streak into v_streak, v_best;

  if v_score > 0 then
    update public.profiles
    set points = coalesce(points, 0) + v_score,
        updated_at = now()
    where id = v_uid;
  end if;

  return jsonb_build_object(
    'correct', v_correct,
    'awardedScore', v_score,
    'awardedCoins', v_coins,
    'streak', coalesce(v_streak, 0),
    'bestStreak', coalesce(v_best, 0)
  );
end;
$$;

revoke all on function public.record_trivia_result(text, integer) from public, anon;
grant execute on function public.record_trivia_result(text, integer) to authenticated;

create or replace function public.ensure_profile_username()
returns trigger
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_base text;
begin
  if new.username is null
     or btrim(new.username) = ''
     or lower(btrim(new.username)) = 'null' then
    v_base := lower(
      regexp_replace(
        coalesce(nullif(split_part(coalesce(new.email, ''), '@', 1), ''), 'user'),
        '[^a-z0-9._-]+',
        '_',
        'g'
      )
    );
    v_base := btrim(v_base, '_');
    if v_base = '' then
      v_base := 'user';
    end if;

    new.username :=
      left(v_base, 20) || '_' || substr(replace(new.id::text, '-', ''), 1, 6);
  else
    new.username := lower(btrim(new.username));
  end if;

  return new;
end;
$$;

drop trigger if exists trg_ensure_profile_username on public.profiles;
create trigger trg_ensure_profile_username
before insert or update of username, email
on public.profiles
for each row
execute function public.ensure_profile_username();

update public.profiles
set username = null
where username is null
   or btrim(username) = ''
   or lower(btrim(username)) = 'null';

alter table public.profiles
  alter column username set not null;

alter table public.profiles
  drop constraint if exists valid_futa_faculty;
