create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table if not exists private.user_streak_state (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  last_active_date date,
  current_streak integer not null default 0 check (current_streak >= 0),
  updated_at timestamptz not null default now()
);
alter table private.user_streak_state enable row level security;

insert into public.user_balances (user_id)
select p.id from public.profiles p
on conflict (user_id) do nothing;

create or replace function private.ensure_user_balance_row()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.user_balances (user_id)
  values (new.id)
  on conflict (user_id) do nothing;
  return new;
end;
$$;

revoke all on function private.ensure_user_balance_row() from public, anon, authenticated;

drop trigger if exists trg_profiles_ensure_user_balance on public.profiles;
create trigger trg_profiles_ensure_user_balance
after insert on public.profiles
for each row execute function private.ensure_user_balance_row();

revoke insert, update, delete, truncate, references, trigger on public.user_balances from authenticated, anon;
grant select on public.user_balances to authenticated;
revoke all on public.user_balances from anon;

drop policy if exists user_balances_select_own on public.user_balances;
create policy user_balances_select_own
on public.user_balances
for select
to authenticated
using ((select auth.uid()) = user_id);

create or replace function public.get_my_blink_coin_balance()
returns bigint
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
  select coalesce(floor(spendable_coin_balance), 0)::bigint
  from public.user_balances
  where user_id = (select auth.uid())
$$;

revoke all on function public.get_my_blink_coin_balance() from public, anon;
grant execute on function public.get_my_blink_coin_balance() to authenticated;

create or replace function public.touch_daily_streak()
returns integer
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
  v_today date := (now() at time zone 'UTC')::date;
  v_last date;
  v_streak integer;
begin
  if v_uid is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  if not exists (select 1 from public.profiles where id = v_uid) then
    raise exception 'PROFILE_NOT_FOUND';
  end if;

  insert into private.user_streak_state (user_id, last_active_date, current_streak)
  values (v_uid, null, 0)
  on conflict (user_id) do nothing;

  select last_active_date, current_streak
  into v_last, v_streak
  from private.user_streak_state
  where user_id = v_uid
  for update;

  if v_last is null then
    v_streak := 1;
  elsif v_last = v_today then
    v_streak := greatest(v_streak, 1);
  elsif v_last = v_today - 1 then
    v_streak := greatest(v_streak, 0) + 1;
  else
    v_streak := 1;
  end if;

  update private.user_streak_state
  set last_active_date = v_today,
      current_streak = v_streak,
      updated_at = now()
  where user_id = v_uid;

  update public.profiles
  set daily_streak = v_streak,
      updated_at = now()
  where id = v_uid;

  return v_streak;
end;
$$;

revoke all on function public.touch_daily_streak() from public, anon;
grant execute on function public.touch_daily_streak() to authenticated;
