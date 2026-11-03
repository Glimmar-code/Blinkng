create index if not exists idx_feed_posts_created_at_desc
  on public.feed_posts (created_at desc);

create index if not exists idx_profiles_username_lower
  on public.profiles (lower(username))
  where username is not null and trim(username) <> '';

create or replace view public.game_leaderboard
with (security_invoker = true) as
select
  p.id as user_id,
  coalesce(p.points, 0)::bigint as score,
  coalesce(gp.coins, 0::bigint) as coins,
  coalesce(p.daily_streak, 0)::integer as streak,
  coalesce(gp.best_streak, 0)::integer as best_streak,
  coalesce(nullif(p.name, ''), nullif(p.full_name, ''), p.username) as name,
  p.username,
  nullif(p.avatar_url, '') as avatar_url,
  nullif(nullif(p.university, ''), 'null') as university,
  rank() over (order by coalesce(p.points, 0) desc, p.created_at asc) as world_rank
from public.profiles p
left join public.game_profiles gp on gp.user_id = p.id
where nullif(trim(p.username), '') is not null
order by coalesce(p.points, 0) desc, p.created_at asc;

revoke all on public.game_leaderboard from anon;
grant select on public.game_leaderboard to authenticated;

drop policy if exists delete_own_stories on public.stories;
drop policy if exists insert_own_stories on public.stories;
drop policy if exists select_own_stories on public.stories;
drop policy if exists update_own_stories on public.stories;
