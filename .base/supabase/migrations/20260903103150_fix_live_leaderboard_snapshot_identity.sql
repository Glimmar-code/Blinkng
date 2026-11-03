create or replace function public.refresh_leaderboards()
returns integer
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_inserted integer := 0;
begin
  delete from public.leaderboard_snapshots
  where snapshot_at < now() - interval '30 days';

  insert into public.leaderboard_snapshots (
    user_id,
    name,
    handle,
    university,
    avatar_url,
    verification_tier,
    world_score,
    world_rank,
    campus_score,
    campus_rank,
    snapshot_at
  )
  select
    p.id,
    coalesce(nullif(p.name, ''), nullif(p.full_name, ''), p.username),
    p.username,
    nullif(nullif(p.university, ''), 'null'),
    nullif(p.avatar_url, ''),
    case upper(coalesce(p.verification_badge::text, ''))
      when 'GOLD' then 'Gold'::public.verification_tier_enum
      when 'BLUE' then 'Standard'::public.verification_tier_enum
      else 'None'::public.verification_tier_enum
    end,
    coalesce(p.points, 0)::bigint,
    rank() over (order by coalesce(p.points, 0) desc, p.created_at asc)::integer,
    coalesce(p.points, 0)::bigint,
    rank() over (
      partition by nullif(nullif(p.university, ''), 'null')
      order by coalesce(p.points, 0) desc, p.created_at asc
    )::integer,
    now()
  from public.profiles p
  where coalesce(trim(p.username), '') <> '';

  get diagnostics v_inserted = row_count;
  return v_inserted;
end;
$$;

revoke execute on function public.refresh_leaderboards() from public, anon, authenticated;
