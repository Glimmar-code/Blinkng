-- Reposts are distribution references to the canonical feed post. They do not clone
-- likes/comments/views, so every repost keeps the original post's engagement totals.
-- Engagement arriving through a followed reposter awards an equal distribution
-- credit to the original author and the reposter, with a dedupe ledger to prevent
-- repeated farming by the same actor/action pair.

alter table public.feed_posts
  add column if not exists repost_count integer not null default 0;

alter table public.feed_posts
  drop constraint if exists feed_posts_repost_count_nonnegative;
alter table public.feed_posts
  add constraint feed_posts_repost_count_nonnegative check (repost_count >= 0);

create table if not exists public.post_reposts (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.feed_posts(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade default auth.uid(),
  created_at timestamptz not null default now(),
  unique(post_id, user_id)
);

create index if not exists post_reposts_post_created_idx
  on public.post_reposts(post_id, created_at desc);
create index if not exists post_reposts_user_created_idx
  on public.post_reposts(user_id, created_at desc);

create table if not exists public.repost_point_credits (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.feed_posts(id) on delete cascade,
  reposter_id uuid not null references public.profiles(id) on delete cascade,
  actor_id uuid not null references public.profiles(id) on delete cascade,
  action_type text not null check (action_type in ('repost','view','like','comment','save','share')),
  points_each integer not null check (points_each > 0),
  created_at timestamptz not null default now(),
  unique(post_id, reposter_id, actor_id, action_type)
);

create index if not exists repost_point_credits_post_idx
  on public.repost_point_credits(post_id, created_at desc);
create index if not exists repost_point_credits_reposter_idx
  on public.repost_point_credits(reposter_id, created_at desc);
create index if not exists repost_point_credits_actor_idx
  on public.repost_point_credits(actor_id, created_at desc);

alter table public.post_reposts enable row level security;
alter table public.repost_point_credits enable row level security;

revoke all on public.post_reposts from anon;
revoke all on public.repost_point_credits from anon, authenticated;
grant select on public.post_reposts to authenticated;
revoke insert, update, delete on public.post_reposts from authenticated;

drop policy if exists post_reposts_read_authenticated on public.post_reposts;
create policy post_reposts_read_authenticated
on public.post_reposts
for select
to authenticated
using (true);

create or replace function public.sync_post_repost_count()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_post_id uuid := coalesce(new.post_id, old.post_id);
begin
  update public.feed_posts fp
  set repost_count = (
        select count(*)::integer
        from public.post_reposts r
        where r.post_id = v_post_id
      ),
      updated_at = now()
  where fp.id = v_post_id;
  return coalesce(new, old);
end;
$$;

revoke all on function public.sync_post_repost_count() from public, anon, authenticated;

drop trigger if exists trg_sync_post_repost_count_insert on public.post_reposts;
create trigger trg_sync_post_repost_count_insert
after insert on public.post_reposts
for each row execute function public.sync_post_repost_count();

drop trigger if exists trg_sync_post_repost_count_delete on public.post_reposts;
create trigger trg_sync_post_repost_count_delete
after delete on public.post_reposts
for each row execute function public.sync_post_repost_count();

create or replace function public.award_repost_distribution_points(
  p_post_id uuid,
  p_actor_id uuid,
  p_action_type text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_author_id uuid;
  v_reposter_id uuid;
  v_points integer;
  v_credit_id uuid;
  v_action text := lower(trim(coalesce(p_action_type, '')));
begin
  if p_post_id is null or p_actor_id is null then return; end if;
  if v_action not in ('repost','view','like','comment','save','share') then return; end if;

  select fp.user_id
    into v_author_id
  from public.feed_posts fp
  where fp.id = p_post_id and fp.is_active = true;

  if v_author_id is null then return; end if;

  if v_action = 'repost' then
    v_reposter_id := p_actor_id;
    if not exists (
      select 1 from public.post_reposts r
      where r.post_id = p_post_id and r.user_id = p_actor_id
    ) then return; end if;
  else
    if p_actor_id = v_author_id then return; end if;

    select r.user_id
      into v_reposter_id
    from public.post_reposts r
    where r.post_id = p_post_id
      and r.user_id <> p_actor_id
      and exists (
        select 1 from public.follows f
        where f.follower_id = p_actor_id
          and f.following_id = r.user_id
      )
      and not exists (
        select 1 from public.blocks b
        where (b.blocker_id = p_actor_id and b.blocked_id = r.user_id)
           or (b.blocker_id = r.user_id and b.blocked_id = p_actor_id)
      )
    order by r.created_at desc
    limit 1;
  end if;

  if v_reposter_id is null or v_reposter_id = v_author_id then return; end if;

  v_points := case v_action
    when 'comment' then 2
    when 'save' then 2
    when 'share' then 2
    else 1
  end;

  insert into public.repost_point_credits(
    post_id, reposter_id, actor_id, action_type, points_each
  ) values (
    p_post_id, v_reposter_id, p_actor_id, v_action, v_points
  )
  on conflict (post_id, reposter_id, actor_id, action_type) do nothing
  returning id into v_credit_id;

  if v_credit_id is null then return; end if;

  update public.profiles
  set points = coalesce(points, 0) + v_points,
      updated_at = now()
  where id in (v_author_id, v_reposter_id);

  insert into public.point_transactions(user_id, action_type, points_delta, reference_id)
  values
    (v_author_id, 'repost_origin_' || v_action, v_points, p_post_id),
    (v_reposter_id, 'repost_distribution_' || v_action, v_points, p_post_id);
end;
$$;

revoke all on function public.award_repost_distribution_points(uuid, uuid, text)
from public, anon, authenticated;

create or replace function public.award_repost_points_from_interaction()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if tg_table_name = 'post_likes' then
    perform public.award_repost_distribution_points(new.post_id, new.user_id, 'like');
  elsif tg_table_name = 'comments' then
    perform public.award_repost_distribution_points(new.post_id, new.author_id, 'comment');
  elsif tg_table_name = 'post_bookmarks' then
    perform public.award_repost_distribution_points(new.post_id, new.user_id, 'save');
  elsif tg_table_name = 'post_shares' then
    perform public.award_repost_distribution_points(new.post_id, new.user_id, 'share');
  end if;
  return new;
end;
$$;

revoke all on function public.award_repost_points_from_interaction()
from public, anon, authenticated;

drop trigger if exists trg_repost_credit_post_like on public.post_likes;
create trigger trg_repost_credit_post_like
after insert on public.post_likes
for each row execute function public.award_repost_points_from_interaction();

drop trigger if exists trg_repost_credit_comment on public.comments;
create trigger trg_repost_credit_comment
after insert on public.comments
for each row execute function public.award_repost_points_from_interaction();

drop trigger if exists trg_repost_credit_bookmark on public.post_bookmarks;
create trigger trg_repost_credit_bookmark
after insert on public.post_bookmarks
for each row execute function public.award_repost_points_from_interaction();

drop trigger if exists trg_repost_credit_share on public.post_shares;
create trigger trg_repost_credit_share
after insert on public.post_shares
for each row execute function public.award_repost_points_from_interaction();

create or replace function public.award_repost_points_on_create()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  perform public.award_repost_distribution_points(new.post_id, new.user_id, 'repost');
  return new;
end;
$$;

revoke all on function public.award_repost_points_on_create()
from public, anon, authenticated;

drop trigger if exists trg_repost_credit_create on public.post_reposts;
create trigger trg_repost_credit_create
after insert on public.post_reposts
for each row execute function public.award_repost_points_on_create();

create or replace function public.toggle_post_repost(p_post_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
  v_author_id uuid;
  v_existing_id uuid;
  v_repost_id uuid;
  v_reposted boolean;
  v_count integer;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;

  select fp.user_id into v_author_id
  from public.feed_posts fp
  where fp.id = p_post_id and fp.is_active = true;

  if v_author_id is null then raise exception 'POST_NOT_FOUND'; end if;
  if v_author_id = v_uid then raise exception 'CANNOT_REPOST_OWN_POST'; end if;

  select r.id into v_existing_id
  from public.post_reposts r
  where r.post_id = p_post_id and r.user_id = v_uid
  for update;

  if v_existing_id is not null then
    delete from public.post_reposts where id = v_existing_id;
    v_reposted := false;
    v_repost_id := null;
  else
    insert into public.post_reposts(post_id, user_id)
    values (p_post_id, v_uid)
    returning id into v_repost_id;
    v_reposted := true;
  end if;

  select coalesce(fp.repost_count, 0) into v_count
  from public.feed_posts fp where fp.id = p_post_id;

  return jsonb_build_object(
    'reposted', v_reposted,
    'repostId', v_repost_id,
    'repostCount', coalesce(v_count, 0)
  );
end;
$$;

revoke all on function public.toggle_post_repost(uuid) from public, anon;
grant execute on function public.toggle_post_repost(uuid) to authenticated;

-- Keep the existing weighted view semantics, but also grant one deduped
-- distribution credit when the viewer reached the post through a followed reposter.
create or replace function public.record_post_view(p_post_id uuid)
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_viewer_id uuid := auth.uid();
    v_weight integer := 1;
    v_impression_count integer;
    v_new_views integer;
begin
    if v_viewer_id is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;

    select case
        when coalesce(p.is_verified, false)
             and upper(coalesce(p.verification_badge, 'NONE')) = 'GOLD' then 6
        when coalesce(p.is_verified, false)
             and upper(coalesce(p.verification_badge, 'NONE')) = 'BLUE' then 3
        else 1
    end
    into v_weight
    from public.profiles p
    where p.id = v_viewer_id;

    v_weight := coalesce(v_weight, 1);

    select fp.view_count into v_new_views
    from public.feed_posts fp
    where fp.id = p_post_id and fp.is_active = true
    for update;

    if not found then return 0; end if;

    insert into public.post_views as pv (
        post_id, viewer_id, view_weight, impression_count,
        weighted_view_count, last_viewed_at
    )
    values (p_post_id, v_viewer_id, v_weight, 1, v_weight, now())
    on conflict (post_id, viewer_id) do update
    set impression_count = pv.impression_count + 1,
        weighted_view_count = pv.weighted_view_count + excluded.view_weight,
        view_weight = excluded.view_weight,
        last_viewed_at = now()
    where pv.impression_count < 100
    returning impression_count into v_impression_count;

    if not found then return coalesce(v_new_views, 0); end if;

    update public.feed_posts
    set view_count = coalesce(view_count, 0) + v_weight,
        updated_at = now()
    where id = p_post_id
    returning view_count into v_new_views;

    if v_impression_count = 1 then
        begin
            perform public.award_points(v_viewer_id, 'view_post', p_post_id);
        exception when undefined_function then null;
        end;
    end if;

    perform public.award_repost_distribution_points(p_post_id, v_viewer_id, 'view');
    return coalesce(v_new_views, 0);
end;
$$;

revoke all on function public.record_post_view(uuid) from public, anon;
grant execute on function public.record_post_view(uuid) to authenticated;

-- Game score is now independent from the social/profile leaderboard.
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
  if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if v_game_type not in ('brain_mix','math_sprint','logic','memory','word_power','general_knowledge') then
    raise exception 'INVALID_GAME_TYPE';
  end if;
  if p_score < 0 then raise exception 'INVALID_SCORE'; end if;

  v_score := least(p_score, 500);
  v_coins := least(30, floor(v_score / 20.0)::integer);

  select coalesce(sum(score), 0) into v_daily_points
  from public.game_sessions
  where user_id = auth.uid()
    and started_at >= date_trunc('day', now());

  if v_daily_points + v_score > 5000 then
    raise exception 'DAILY_GAME_SCORE_LIMIT_REACHED';
  end if;

  if exists (
    select 1 from public.game_sessions
    where user_id = auth.uid()
      and started_at > now() - interval '3 seconds'
  ) then raise exception 'GAME_SESSION_RATE_LIMITED'; end if;

  insert into public.game_sessions(user_id, game_type, score, coins_earned, completed_at)
  values(auth.uid(), v_game_type, v_score, v_coins, now())
  returning id into v_session_id;

  insert into public.game_profiles(user_id, score, coins, updated_at)
  values(auth.uid(), v_score, v_coins, now())
  on conflict(user_id) do update set
    score = public.game_profiles.score + excluded.score,
    coins = public.game_profiles.coins + excluded.coins,
    updated_at = now();

  return v_session_id;
end;
$$;

revoke all on function public.record_game_session(text, integer, integer) from public, anon;
grant execute on function public.record_game_session(text, integer, integer) to authenticated;

create or replace function public.record_trivia_result(p_question_id text, p_selected_index integer)
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
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;

  v_correct_index := case v_question
    when 'q1' then 1 when 'q2' then 0 when 'q3' then 2
    when 'q4' then 1 when 'q5' then 1 else null
  end;
  if v_correct_index is null then raise exception 'VALIDATION_ERROR: invalid question'; end if;
  if p_selected_index is null or p_selected_index < 0 or p_selected_index > 3 then
    raise exception 'VALIDATION_ERROR: invalid answer index';
  end if;

  perform pg_advisory_xact_lock(hashtext(v_uid::text || ':trivia:' || v_question || ':' || current_date::text));
  if exists (
    select 1 from public.game_sessions
    where user_id = v_uid
      and game_type = 'trivia:' || v_question
      and started_at >= date_trunc('day', now())
  ) then raise exception 'ALREADY_ANSWERED_TODAY'; end if;

  v_correct := p_selected_index = v_correct_index;
  v_score := case when v_correct then 50 else 0 end;
  v_coins := case when v_correct then 15 else 0 end;

  insert into public.game_sessions(user_id, game_type, score, coins_earned, completed_at)
  values (v_uid, 'trivia:' || v_question, v_score, v_coins, now());

  insert into public.game_profiles(user_id, score, coins, streak, best_streak, updated_at)
  values (
    v_uid, v_score, v_coins,
    case when v_correct then 1 else 0 end,
    case when v_correct then 1 else 0 end,
    now()
  )
  on conflict (user_id) do update
  set score = public.game_profiles.score + excluded.score,
      coins = public.game_profiles.coins + excluded.coins,
      streak = case when v_correct then public.game_profiles.streak + 1 else 0 end,
      best_streak = greatest(
        public.game_profiles.best_streak,
        case when v_correct then public.game_profiles.streak + 1 else 0 end
      ),
      updated_at = now()
  returning streak, best_streak into v_streak, v_best;

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

create or replace view public.game_rankings
with (security_invoker = true)
as
select
  p.id as user_id,
  coalesce(gp.score, 0)::bigint as score,
  coalesce(gp.coins, 0)::bigint as coins,
  coalesce(gp.streak, 0)::integer as streak,
  coalesce(gp.best_streak, 0)::integer as best_streak,
  coalesce(nullif(p.name, ''), nullif(p.full_name, ''), p.username) as name,
  p.username,
  nullif(p.avatar_url, '') as avatar_url,
  nullif(nullif(p.university, ''), 'null') as university,
  rank() over (
    order by coalesce(gp.score, 0) desc, gp.updated_at asc nulls last, p.created_at asc
  ) as world_rank,
  p.faculty,
  p.academic_level,
  p.verification_badge
from public.profiles p
left join public.game_profiles gp on gp.user_id = p.id
where nullif(trim(p.username), '') is not null
order by coalesce(gp.score, 0) desc, gp.updated_at asc nulls last, p.created_at asc;

revoke all on public.game_rankings from anon;
grant select on public.game_rankings to authenticated;

-- One-time detachment of historical game score from the social leaderboard.
-- The insert records the correction so it is auditable.
with game_totals as (
  select user_id, coalesce(sum(score), 0)::integer as game_points
  from public.game_sessions
  group by user_id
), adjustments as (
  select p.id as user_id, least(coalesce(p.points, 0), gt.game_points) as removed_points
  from public.profiles p
  join game_totals gt on gt.user_id = p.id
  where gt.game_points > 0 and coalesce(p.points, 0) > 0
), updated as (
  update public.profiles p
  set points = greatest(0, coalesce(p.points, 0) - a.removed_points),
      updated_at = now()
  from adjustments a
  where p.id = a.user_id
  returning p.id
)
insert into public.point_transactions(user_id, action_type, points_delta, reference_id)
select a.user_id, 'game_points_detached', -a.removed_points, null
from adjustments a
where a.removed_points > 0;
