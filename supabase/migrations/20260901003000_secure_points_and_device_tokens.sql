begin;

-- Point awarding is server-side only. Client roles must never be able to mint points.
revoke execute on function public.award_points(uuid,text,uuid) from anon, authenticated;
revoke execute on function public.award_interaction_points() from anon, authenticated;

-- One canonical multi-device token index. profiles.fcm_token remains only for backward compatibility.
create unique index if not exists fcm_tokens_user_token_uidx on public.fcm_tokens(user_id, token);
create index if not exists fcm_tokens_user_updated_idx on public.fcm_tokens(user_id, updated_at desc);

-- The leaderboard is intentionally recreated as an invoker view so normal RLS applies.
drop view if exists public.game_leaderboard;
create view public.game_leaderboard with (security_invoker = true) as
select
  p.id as user_id,
  coalesce(p.points,0)::bigint as score,
  coalesce(gp.coins,0::bigint) as coins,
  coalesce(p.daily_streak,0) as streak,
  coalesce(gp.best_streak,0) as best_streak,
  coalesce(p.name,p.full_name,''::text) as name,
  p.username,
  p.avatar_url,
  p.university,
  rank() over(order by coalesce(p.points,0) desc,p.created_at) as world_rank
from public.profiles p
left join public.game_profiles gp on gp.user_id=p.id
order by coalesce(p.points,0) desc,p.created_at;
grant select on public.game_leaderboard to authenticated;

commit;
