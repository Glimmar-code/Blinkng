-- BLINK Paystack checkout ledger, production-safe default (feature flag OFF).
-- Additive migration; deployment must follow Testlab/staging verification.
-- Verification badge means paid BLINK membership, not real-world identity verification.

insert into private.blink_economy_config(key,int_value)
values ('gold_verification_cash_ngn',2000)
on conflict (key) do nothing;

create table if not exists public.blink_paystack_payments (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  reference text not null unique,
  purchase_kind text not null check(purchase_kind in ('verification','coin_pack')),
  item_code text not null,
  coin_order_id uuid references public.blink_coin_purchase_orders(id),
  amount_ngn integer not null check(amount_ngn > 0),
  coin_amount integer check(coin_amount > 0),
  duration_days integer check(duration_days between 1 and 366),
  currency text not null default 'NGN' check(currency = 'NGN'),
  paystack_transaction_id text unique,
  status text not null default 'pending'
    check(status in ('pending','fulfilled','failed','refunded')),
  created_at timestamptz not null default now(),
  paid_at timestamptz,
  updated_at timestamptz not null default now(),
  constraint paystack_order_shape check (
    (purchase_kind='verification' and item_code in ('BLUE','GOLD')
       and coin_order_id is null and coin_amount is null and duration_days is not null)
    or
    (purchase_kind='coin_pack' and coin_order_id is not null and coin_amount is not null and duration_days is null)
  )
);
create index if not exists blink_paystack_payments_user_idx
  on public.blink_paystack_payments(user_id,created_at desc);
alter table public.blink_paystack_payments enable row level security;
revoke all on public.blink_paystack_payments from public, anon, authenticated;
grant select on public.blink_paystack_payments to authenticated;
drop policy if exists blink_paystack_payments_own_read on public.blink_paystack_payments;
create policy blink_paystack_payments_own_read on public.blink_paystack_payments
  for select to authenticated using(user_id=(select auth.uid()));

-- No order amount or reward may be supplied by the Android client.
create or replace function public.create_blink_paystack_order(
  p_kind text, p_item_code text
) returns jsonb language plpgsql security definer set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_kind text := lower(trim(coalesce(p_kind,'')));
  v_item text := upper(trim(coalesce(p_item_code,'')));
  v_enabled boolean;
  v_price integer;
  v_coins integer;
  v_order_id uuid;
  v_ref text;
  v_pack jsonb;
  v_packs jsonb;
  v_followers integer := 0;
  v_badge text;
  v_expires timestamptz;
  v_recent integer;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  if v_kind not in ('verification','coin_pack') then raise exception 'INVALID_PURCHASE_KIND'; end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text)::bigint);

  select coalesce(bool_value,false) into v_enabled from private.blink_economy_config
   where key='cash_checkout_enabled';
  if not coalesce(v_enabled,false) then raise exception 'CASH_CHECKOUT_DISABLED'; end if;

  -- Guard excessive abandoned orders and API abuse.
  select count(*) into v_recent from public.blink_paystack_payments
   where user_id=v_user and created_at>now()-interval '30 minutes';
  if v_recent >= 8 then raise exception 'PAYMENT_RATE_LIMIT'; end if;

  if v_kind='verification' then
    if v_item not in ('BLUE','GOLD') then raise exception 'INVALID_TIER'; end if;
    select upper(coalesce(verification_badge,'NONE')),verification_expires_at
      into v_badge,v_expires from public.profiles where id=v_user;
    if not found then raise exception 'PROFILE_NOT_FOUND'; end if;
    if v_item='GOLD' then
      select count(*)::integer into v_followers from public.follows where following_id=v_user;
      if v_followers < 1000 then raise exception 'GOLD_FOLLOWER_REQUIREMENT_NOT_MET'; end if;
    elsif v_badge='GOLD' and (v_expires is null or v_expires>now()) then
      raise exception 'ALREADY_GOLD_VERIFIED';
    end if;
    if v_item='BLUE' then
      select coalesce(int_value,800)::integer into v_price
        from private.blink_economy_config where key='blue_verification_cash_ngn';
    else
      select coalesce(int_value,2000)::integer into v_price
        from private.blink_economy_config where key='gold_verification_cash_ngn';
    end if;
  else
    select json_value into v_packs from private.blink_economy_config where key='coin_packs';
    select value into v_pack from jsonb_array_elements(coalesce(v_packs,'[]'::jsonb))
      where upper(value->>'id')=v_item limit 1;
    if v_pack is null then raise exception 'INVALID_COIN_PACK'; end if;
    v_item := v_pack->>'id';
    v_price := (v_pack->>'price_ngn')::integer;
    v_coins := (v_pack->>'coins')::integer;
    if v_coins < 1 then raise exception 'INVALID_COIN_PACK'; end if;
    insert into public.blink_coin_purchase_orders
      (user_id,pack_id,provider,amount_ngn,coin_amount,bonus_coins)
      values (v_user,v_item,'paystack',v_price,v_coins,greatest(0,v_coins-v_price))
      returning id into v_order_id;
  end if;

  if coalesce(v_price,0) <= 0 then raise exception 'INVALID_PAYMENT_AMOUNT'; end if;
  v_ref := 'blink-ps-' || replace(gen_random_uuid()::text,'-','');
  insert into public.blink_paystack_payments
    (user_id,reference,purchase_kind,item_code,coin_order_id,amount_ngn,coin_amount,duration_days)
    values(v_user,v_ref,v_kind,v_item,v_order_id,v_price,v_coins,
      case when v_kind='verification' then 30 else null end);

  return jsonb_build_object('reference',v_ref,'kind',v_kind,'item_code',v_item,
    'amount_ngn',v_price,'currency','NGN','coins',v_coins,'status','pending');
end;
$$;
revoke all on function public.create_blink_paystack_order(text,text) from public,anon;
grant execute on function public.create_blink_paystack_order(text,text) to authenticated;

-- Invoker privileges: ONLY server service_role gets EXECUTE.
-- Paystack verification happens in the Edge Function before this RPC.
-- The database transaction atomically credits coins or activates a badge.
create or replace function public.fulfill_blink_paystack_order(
  p_reference text, p_transaction_id text, p_amount_kobo bigint, p_currency text
) returns jsonb language plpgsql security invoker set search_path = ''
as $$
declare
  v_order public.blink_paystack_payments%rowtype;
  v_prior_badge text;
  v_prior_expiration timestamptz;
  v_new_badge text;
  v_new_expiration timestamptz;
  v_fulfilment jsonb;
begin
  select * into v_order from public.blink_paystack_payments
    where reference=p_reference for update;
  if not found then raise exception 'PAYMENT_ORDER_NOT_FOUND'; end if;
  if v_order.status='fulfilled' then
    if v_order.paystack_transaction_id is distinct from p_transaction_id then
      raise exception 'PAYMENT_TRANSACTION_MISMATCH';
    end if;
    return jsonb_build_object('status','fulfilled','already_fulfilled',true);
  end if;
  if v_order.status <> 'pending'
    or nullif(trim(coalesce(p_transaction_id,'')),'') is null
    or p_amount_kobo <> v_order.amount_ngn::bigint*100
    or p_currency <> 'NGN' then
      raise exception 'PAYMENT_VERIFICATION_MISMATCH';
  end if;

  if v_order.purchase_kind='coin_pack' then
    -- Existing coin fulfilment RPC is service_role-only and idempotent.
    v_fulfilment := public.fulfill_blink_coin_purchase_order(
      v_order.coin_order_id,v_order.reference);
  else
    select upper(coalesce(verification_badge,'NONE')),verification_expires_at
      into v_prior_badge,v_prior_expiration
      from public.profiles where id=v_order.user_id for update;
    if not found then raise exception 'PROFILE_NOT_FOUND'; end if;

    -- An outstanding Blue order must never downgrade an active Gold badge.
    v_new_badge := case
      when v_prior_badge='GOLD' and
        (v_prior_expiration is null or v_prior_expiration>now()) then 'GOLD'
      else v_order.item_code end;
    v_new_expiration := case
      when v_prior_badge=v_order.item_code and v_prior_expiration is null
      then null -- preserve only an existing permanent badge of the same tier
      else greatest(coalesce(v_prior_expiration,now()),now()) +
        make_interval(days=>v_order.duration_days)
      end;
    update public.profiles set
      verification_badge=v_new_badge,
      verification_tier=(case when v_new_badge='GOLD' then 'Gold' else 'Standard' end)::public.verification_tier_enum,
      is_verified=true, verified_at=coalesce(verified_at,now()),
      verification_expires_at=v_new_expiration, updated_at=now()
      where id=v_order.user_id;
  end if;

  update public.blink_paystack_payments set
    status='fulfilled',paystack_transaction_id=p_transaction_id,
    paid_at=now(),updated_at=now() where id=v_order.id;
  return jsonb_build_object('status','fulfilled','already_fulfilled',false,
    'kind',v_order.purchase_kind,'item_code',v_order.item_code,
    'coins_result',v_fulfilment);
end;
$$;
revoke all on function public.fulfill_blink_paystack_order(text,text,bigint,text)
  from public,anon,authenticated;
grant execute on function public.fulfill_blink_paystack_order(text,text,bigint,text)
  to service_role;
