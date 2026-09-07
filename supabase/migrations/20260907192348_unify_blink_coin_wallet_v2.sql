-- Blink Coin wallet repair.
-- Canonical spendable wallet: public.user_balances.spendable_coin_balance.
-- Legacy game/admin writes are bridged by DELTA so older callers remain compatible.

-- Preserve previously stranded legacy coins. A non-zero canonical wallet wins.
insert into public.user_balances (user_id, spendable_coin_balance, updated_at)
select gp.user_id, greatest(gp.coins, 0), now()
from public.game_profiles gp
on conflict (user_id) do update
set spendable_coin_balance = case
      when public.user_balances.spendable_coin_balance = 0
        then greatest(excluded.spendable_coin_balance, 0)
      else public.user_balances.spendable_coin_balance
    end,
    updated_at = now();

-- Make current legacy rows agree with the canonical value before enabling the bridge.
update public.game_profiles gp
set coins = floor(ub.spendable_coin_balance)::bigint,
    updated_at = now()
from public.user_balances ub
where ub.user_id = gp.user_id
  and gp.coins is distinct from floor(ub.spendable_coin_balance)::bigint;

create or replace function private.sync_legacy_game_coin_delta_to_wallet()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_old bigint := case when tg_op = 'INSERT' then 0 else coalesce(old.coins, 0) end;
  v_delta bigint := coalesce(new.coins, 0) - v_old;
  v_before bigint;
  v_after bigint;
begin
  -- Canonical writers can deliberately mirror a value to game_profiles without
  -- applying it to the wallet a second time.
  if coalesce(current_setting('blink.coin_sync_bypass', true), '0') = '1' then
    return new;
  end if;

  insert into public.user_balances (user_id, spendable_coin_balance, updated_at)
  values (new.user_id, 0, now())
  on conflict (user_id) do nothing;

  select floor(ub.spendable_coin_balance)::bigint
  into v_before
  from public.user_balances ub
  where ub.user_id = new.user_id
  for update;

  v_after := coalesce(v_before, 0) + v_delta;
  if v_after < 0 then
    raise exception 'INSUFFICIENT_BLINK_COINS' using errcode = '22003';
  end if;

  if v_delta <> 0 then
    update public.user_balances
    set spendable_coin_balance = v_after,
        updated_at = now()
    where user_id = new.user_id;
  end if;

  -- Whenever legacy code touches coins, refresh its cached value to reality.
  new.coins := v_after;
  new.updated_at := now();
  return new;
end;
$$;

revoke all on function private.sync_legacy_game_coin_delta_to_wallet() from public;

drop trigger if exists trg_game_profiles_coin_wallet_bridge on public.game_profiles;
create trigger trg_game_profiles_coin_wallet_bridge
before insert or update of coins on public.game_profiles
for each row
execute function private.sync_legacy_game_coin_delta_to_wallet();

-- Admin coin changes now mutate the canonical spendable wallet while preserving
-- the existing hourly safety limit and RPC return type.
create or replace function private.admin_change_coins(
  p_actor uuid,
  p_target uuid,
  p_amount bigint,
  p_type text,
  p_reason text default null
)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_before bigint;
  v_balance bigint;
  v_used bigint := 0;
begin
  perform private.admin_ensure_control(p_actor,p_target);
  if p_amount=0 then raise exception 'COIN_AMOUNT_CANNOT_BE_ZERO'; end if;

  if p_amount > 0 and not private.is_blink_owner_id(p_actor) then
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(p_actor::text)::bigint);
    select coalesce(sum(amount),0)
      into v_used
      from private.admin_coin_transactions
     where actor_id=p_actor
       and amount>0
       and created_at > now() - interval '1 hour';

    if v_used + p_amount > 50 then
      raise exception 'NORMAL_ADMIN_HOURLY_COIN_LIMIT_EXCEEDED: % coins remaining in the current 60-minute window', greatest(50-v_used,0)
        using errcode='42501';
    end if;
  end if;

  insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
  values(p_target,0,now())
  on conflict(user_id) do nothing;

  select floor(ub.spendable_coin_balance)::bigint
    into v_before
    from public.user_balances ub
   where ub.user_id=p_target
   for update;

  v_balance := greatest(0,coalesce(v_before,0)+p_amount);

  update public.user_balances
     set spendable_coin_balance=v_balance,updated_at=now()
   where user_id=p_target;

  -- Maintain the legacy cache for old admin/game screens without double-crediting.
  perform set_config('blink.coin_sync_bypass','1',true);
  insert into public.game_profiles(user_id,coins,updated_at)
  values(p_target,v_balance,now())
  on conflict(user_id) do update set coins=excluded.coins,updated_at=now();
  perform set_config('blink.coin_sync_bypass','0',true);

  insert into private.admin_coin_transactions(actor_id,user_id,amount,transaction_type,reason)
  values(p_actor,p_target,p_amount,left(coalesce(p_type,'adjustment'),80),left(p_reason,500));

  return v_balance;
end;
$$;

-- Game UI reads exactly the same spendable balance as Profile, Store and Connect.
create or replace function private.get_game_dashboard_internal()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid:=auth.uid();
  v_score bigint:=0;
  v_coins bigint:=0;
  v_streak integer:=0;
  v_best integer:=0;
  v_rank integer:=0;
  v_today integer:=0;
  v_today_correct integer:=0;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;

  select coalesce(gp.score,0),coalesce(gp.streak,0),coalesce(gp.best_streak,0)
    into v_score,v_streak,v_best
    from public.game_profiles gp
   where gp.user_id=v_uid;

  select coalesce(floor(ub.spendable_coin_balance),0)::bigint
    into v_coins
    from public.user_balances ub
   where ub.user_id=v_uid;

  select coalesce(gr.world_rank,0)::integer
    into v_rank
    from public.game_rankings gr
   where gr.user_id=v_uid;

  select count(*)::integer,count(*) filter(where ga.correct)::integer
    into v_today,v_today_correct
    from public.game_attempts ga
   where ga.user_id=v_uid and ga.created_at>=date_trunc('day',now());

  return jsonb_build_object(
    'score',coalesce(v_score,0),
    'coins',coalesce(v_coins,0),
    'streak',coalesce(v_streak,0),
    'bestStreak',coalesce(v_best,0),
    'worldRank',coalesce(v_rank,0),
    'todayAnswers',coalesce(v_today,0),
    'todayCorrect',coalesce(v_today_correct,0),
    'dailyGoal',15
  );
end;
$$;

-- Coin gifting now spends/credits the canonical wallet, so purchased/admin coins work here too.
create or replace function public.gift_blink_coins(p_receiver_id uuid, p_amount bigint default 10)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_sender uuid := auth.uid();
  v_balance bigint;
  v_receiver_balance bigint;
begin
  if v_sender is null then raise exception 'AUTH_REQUIRED' using errcode='42501'; end if;
  if p_receiver_id is null or p_receiver_id=v_sender then raise exception 'INVALID_RECEIVER' using errcode='22023'; end if;
  if p_amount is null or p_amount<1 or p_amount>1000 then raise exception 'INVALID_AMOUNT' using errcode='22023'; end if;
  if not exists(select 1 from public.profiles p where p.id=p_receiver_id) then raise exception 'PROFILE_NOT_FOUND' using errcode='P0002'; end if;

  insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
  values(v_sender,0,now()),(p_receiver_id,0,now())
  on conflict(user_id) do nothing;

  perform 1
    from public.user_balances ub
   where ub.user_id in (v_sender,p_receiver_id)
   order by ub.user_id
   for update;

  select floor(ub.spendable_coin_balance)::bigint into v_balance
    from public.user_balances ub where ub.user_id=v_sender;
  select floor(ub.spendable_coin_balance)::bigint into v_receiver_balance
    from public.user_balances ub where ub.user_id=p_receiver_id;

  if coalesce(v_balance,0)<p_amount then raise exception 'INSUFFICIENT_COINS' using errcode='22003'; end if;

  v_balance := v_balance-p_amount;
  v_receiver_balance := coalesce(v_receiver_balance,0)+p_amount;

  update public.user_balances set spendable_coin_balance=v_balance,updated_at=now() where user_id=v_sender;
  update public.user_balances set spendable_coin_balance=v_receiver_balance,updated_at=now() where user_id=p_receiver_id;

  -- Refresh legacy cache values without reapplying the transfer.
  perform set_config('blink.coin_sync_bypass','1',true);
  insert into public.game_profiles(user_id,coins,updated_at)
  values(v_sender,v_balance,now()),(p_receiver_id,v_receiver_balance,now())
  on conflict(user_id) do update set coins=excluded.coins,updated_at=now();
  perform set_config('blink.coin_sync_bypass','0',true);

  insert into public.blink_coin_gifts(sender_id,receiver_id,amount)
  values(v_sender,p_receiver_id,p_amount);

  return v_balance;
end;
$$;
