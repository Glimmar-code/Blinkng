-- Stage-only. Verify idempotency, permissions, RLS and notification in a disposable staging DB.
-- This endpoint is not activated by the Android UI until the migration is verified.
-- It reuses the production gift ledger (primary-key UUID) as an exactly-once receipt.
create or replace function public.gift_blink_coins_once(
  p_receiver_id uuid, p_amount bigint, p_request_id uuid
) returns bigint
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_sender uuid := auth.uid();
  v_sender_balance bigint;
  v_receiver_balance bigint;
  v_existing record;
begin
  if v_sender is null then raise exception 'AUTH_REQUIRED' using errcode='42501'; end if;
  if p_receiver_id is null or p_receiver_id = v_sender then
    raise exception 'INVALID_RECEIVER' using errcode='22023';
  end if;
  if p_request_id is null or p_amount not in (20,50,100) then
    raise exception 'INVALID_GIFT_REQUEST' using errcode='22023';
  end if;
  if not exists (select 1 from public.profiles where id=p_receiver_id) then
    raise exception 'PROFILE_NOT_FOUND' using errcode='P0002';
  end if;

  -- Unique receipt row serializes concurrent/replayed calls for the same UUID.
  insert into public.blink_coin_gifts(id,sender_id,receiver_id,amount)
  values (p_request_id,v_sender,p_receiver_id,p_amount)
  on conflict (id) do nothing;

  if not found then
    select sender_id,receiver_id,amount into v_existing
      from public.blink_coin_gifts where id=p_request_id;
    if v_existing.sender_id is distinct from v_sender
       or v_existing.receiver_id is distinct from p_receiver_id
       or v_existing.amount is distinct from p_amount then
      raise exception 'GIFT_RECEIPT_CONFLICT' using errcode='23505';
    end if;
    return coalesce((
      select floor(spendable_coin_balance)::bigint
      from public.user_balances where user_id=v_sender
    ),0);
  end if;

  insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
  values(v_sender,0,now()),(p_receiver_id,0,now())
  on conflict(user_id) do nothing;

  -- Lock both rows in deterministic order to prevent transfer deadlocks.
  perform 1 from public.user_balances
  where user_id in (v_sender,p_receiver_id)
  order by user_id for update;
  select floor(spendable_coin_balance)::bigint into v_sender_balance
    from public.user_balances where user_id=v_sender;
  select floor(spendable_coin_balance)::bigint into v_receiver_balance
    from public.user_balances where user_id=p_receiver_id;
  if coalesce(v_sender_balance,0)<p_amount then
    raise exception 'INSUFFICIENT_COINS' using errcode='22003';
  end if;
  v_sender_balance := v_sender_balance - p_amount;
  v_receiver_balance := coalesce(v_receiver_balance,0) + p_amount;
  update public.user_balances set spendable_coin_balance=v_sender_balance,updated_at=now()
    where user_id=v_sender;
  update public.user_balances set spendable_coin_balance=v_receiver_balance,updated_at=now()
    where user_id=p_receiver_id;

  perform set_config('blink.coin_sync_bypass','1',true);
  insert into public.game_profiles(user_id,coins,updated_at)
    values(v_sender,v_sender_balance,now()),(p_receiver_id,v_receiver_balance,now())
  on conflict(user_id) do update set coins=excluded.coins,updated_at=now();
  perform set_config('blink.coin_sync_bypass','0',true);

  insert into public.notifications(user_id,actor_id,type,text,sub_text,target_type,target_id,metadata)
  values(
    p_receiver_id,v_sender,'tip',
    'You received ' || p_amount || ' BLINK Coins!',
    'A user sent you BLINK Coins.',
    'coin_gift',p_request_id,
    jsonb_build_object('gift_id',p_request_id,'amount',p_amount,'sender_id',v_sender)
  );
  return v_sender_balance;
end;
$$;

revoke all on function public.gift_blink_coins_once(uuid,bigint,uuid) from public,anon;
grant execute on function public.gift_blink_coins_once(uuid,bigint,uuid) to authenticated,service_role;
