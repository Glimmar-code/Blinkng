create or replace function private.guard_profile_daily_streak()
returns trigger
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
declare
  v_canonical integer;
begin
  if new.daily_streak is distinct from old.daily_streak then
    select current_streak into v_canonical
    from private.user_streak_state
    where user_id = old.id;

    if new.daily_streak is distinct from coalesce(v_canonical, old.daily_streak) then
      new.daily_streak := old.daily_streak;
    end if;
  end if;
  return new;
end;
$$;

revoke all on function private.guard_profile_daily_streak() from public, anon, authenticated;

drop trigger if exists trg_guard_profile_daily_streak on public.profiles;
create trigger trg_guard_profile_daily_streak
before update of daily_streak on public.profiles
for each row execute function private.guard_profile_daily_streak();
