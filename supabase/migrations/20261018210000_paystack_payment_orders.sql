-- Paystack cash payment foundation for BLINK Testlab.
-- Cash checkout stays OFF until an approved merchant, staging credentials and tests exist.
-- All amounts are chosen by the server (NGN). Checkout never starts for BLINK Store.
-- Marketplace listings remain free; there is no seller activation charge.

insert into private.blink_economy_config(key,int_value)
values ('gold_verification_cash_ngn',2000)
on conflict(key) do nothing;

create table if not exists public.blink_paystack_orders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  reference text not null unique,
  kind text not null check (kind in ('BLUE_VERIFICATION','GOLD_VERIFICATION','COIN_PACK')),
  tier text check (tier in ('BLUE','GOLD') or tier is null),
  coin_pack_id text,
  coin_purchase_order_id uuid references public.blink_coin_purchase_orders(id),
  amount_ngn integer not null check (amount_ngn > 0),
  currency text not null default 'NGN' check(currency='NGN'),
  days integer not null default 30 check(days between 1 and 366),
  provider_transaction_id text unique,
  status text not null default 'pending' check(status in ('pending','fulfilled','failed','cancelled')),
  created_at timestamptz not null default now(),
  fulfilled_at timestamptz,
  updated_at timestamptz not null default now()
);

create index if not exists blink_paystack_orders_user_created_idx
  on public.blink_paystack_orders(user_id,created_at desc);
alter table public.blink_paystack_orders enable row level security;
revoke all on public.blink_paystack_orders from public, anon, authenticated;
grant select on public.blink_paystack_orders to authenticated;
create policy blink_paystack_orders_own_read on public.blink_paystack_orders
  for select to authenticated using (user_id=(select auth.uid()));

create or replace function public.create_blink_paystack_order(p_kind text,p_pack_id text default null)
returns jsonb language plpgsql security definer set search_path=''
as $$
declare
  v_user uuid:=auth.uid();
  v_kind text:=upper(trim(coalesce(p_kind,'')));
  v_enabled boolean;
  v_amount integer;
  v_tier text;
  v_pack jsonb;
  v_packs jsonb;
  v_coins integer;
  v_coin_order_id uuid;
  v_order_id uuid;
  v_ref text;
  v_followers integer;
  v_badge text;
  v_expires timestamptz;
  v_recent integer;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  if v_kind not in ('BLUE_VERIFICATION','GOLD_VERIFICATION','COIN_PACK') then
    raise exception 'INVALID_PAYMENT_PRODUCT';
  end if;
  select coalesce(bool_value,false) into v_enabled
    from private.blink_economy_config where key='cash_checkout_enabled';
  if not coalesce(v_enabled,false) then raise exception 'CASH_CHECKOUT_DISABLED'; end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text)::bigint);
  select count(*) into v_recent from public.blink_paystack_orders
    where user_id=v_user and created_at > now()-interval '30 minutes';
  if v_recent>=5 then raise exception 'PAYMENT_RATE_LIMIT'; end if;

  if v_kind='BLUE_VERIFICATION' or v_kind='GOLD_VERIFICATION' then
    v_tier:=case when v_kind='GOLD_VERIFICATION' then 'GOLD' else 'BLUE' end;
    select upper(coalesce(verification_badge,'NONE')),verification_expires_at
      into v_badge,v_expires from public.profiles where id=v_user;
    if not found then raise exception 'PROFILE_NOT_FOUND'; end if;
    if v_tier='BLUE' and v_badge='GOLD' and (v_expires is null or v_expires>now()) then
      raise exception 'ALREADY_GOLD_VERIFIED';
    end if;
    if v_tier='GOLD' then
      select count(*)::integer into v_followers from public.follows where following_id=v_user;
      if v_followers<1000 then raise exception 'GOLD_FOLLOWER_REQUIREMENT_NOT_MET'; end if;
      select coalesce(int_value,2000)::integer into v_amount
        from private.blink_economy_config where key='gold_verification_cash_ngn';
    else
      select coalesce(int_value,800)::integer into v_amount
        from private.blink_economy_config where key='blue_verification_cash_ngn';
    end if;
  else
    select json_value into v_packs
      from private.blink_economy_config where key='coin_packs';
    select value into v_pack from jsonb_array_elements(coalesce(v_packs,'[]'::jsonb))
      where value->>'id'=trim(coalesce(p_pack_id,'')) limit 1;
    if v_pack is null then raise exception 'INVALID_COIN_PACK'; end if;
    v_amount:=(v_pack->>'price_ngn')::integer;
    v_coins:=(v_pack->>'coins')::integer;
    if v_coins<=0 then raise exception 'INVALID_COIN_PACK'; end if;
  end if;

  if v_amount is null or v_amount<1 or v_amount>1000000 then
    raise exception 'INVALID_SERVER_PRICE';
  end if;
  v_ref:='BLINK-PS-'||replace(gen_random_uuid()::text,'-','');
  if v_kind='COIN_PACK' then
    insert into public.blink_coin_purchase_orders
      (user_id,pack_id,provider,amount_ngn,coin_amount,bonus_coins,provider_reference)
    values
      (v_user,v_pack->>'id','paystack',v_amount,v_coins,greatest(0,v_coins-v_amount),v_ref)
    returning id into v_coin_order_id;
  end if;
  insert into public.blink_paystack_orders
    (user_id,reference,kind,tier,coin_pack_id,coin_purchase_order_id,amount_ngn)
  values
    (v_user,v_ref,v_kind,v_tier,v_pack->>'id',v_coin_order_id,v_amount)
  returning id into v_order_id;

  return jsonb_build_object('order_id',v_order_id,'reference',v_ref,'kind',v_kind,
    'amount_ngn',v_amount,'currency','NGN','days',30,'status','pending');
end;
$$;
revoke all on function public.create_blink_paystack_order(text,text) from public,anon;
grant execute on function public.create_blink_paystack_order(text,text) to authenticated;

-- Called only with the privileged server key AFTER retrieving transaction from Paystack.
-- The server passes the full verified amount in kobo, not an Android-supplied figure.
create or replace function public.fulfill_blink_paystack_order(
  p_reference text,p_provider_transaction_id text,p_paid_kobo bigint,p_currency text
)
returns jsonb language plpgsql security invoker set search_path=''
as $$
declare
  v_order public.blink_paystack_orders%rowtype;
  v_badge text;
  v_old_badge text;
  v_old_expiry timestamptz;
  v_new_expiry timestamptz;
  v_coins jsonb;
begin
  select * into v_order from public.blink_paystack_orders
    where reference=p_reference for update;
  if not found then raise exception 'PAYMENT_ORDER_NOT_FOUND'; end if;

  if v_order.status='fulfilled' then
    if v_order.provider_transaction_id is distinct from p_provider_transaction_id then
      raise exception 'PAYMENT_TRANSACTION_CONFLICT';
    end if;
    return jsonb_build_object('success',true,'already_fulfilled',true,'kind',v_order.kind);
  end if;

  if v_order.status<>'pending' or p_reference is distinct from v_order.reference
    or coalesce(trim(p_provider_transaction_id),'')=''
    or p_paid_kobo is distinct from v_order.amount_ngn::bigint*100
    or p_currency is distinct from 'NGN' then
    raise exception 'PAYMENT_VERIFICATION_MISMATCH';
  end if;

  if v_order.kind='COIN_PACK' then
    if v_order.coin_purchase_order_id is null then raise exception 'COIN_ORDER_MISSING'; end if;
    select public.fulfill_blink_coin_purchase_order(v_order.coin_purchase_order_id,v_order.reference)
      into v_coins;
  else
    select upper(coalesce(verification_badge,'NONE')), verification_expires_at
      into v_old_badge,v_old_expiry
      from public.profiles where id=v_order.user_id for update;
    if not found then raise exception 'PROFILE_NOT_FOUND'; end if;

    -- A pending Blue order must never downgrade an active Gold badge.
    v_badge:=case
      when v_old_badge='GOLD' and (v_old_expiry is null or v_old_expiry>now())
        then 'GOLD'
      else v_order.tier end;

    -- Preserve permanent status only if the purchased tier is not an upgrade.
    v_new_expiry:=case
      when v_old_badge=v_badge and v_old_expiry is null then null
      else greatest(coalesce(v_old_expiry,now()),now())
        + make_interval(days=>v_order.days) end;

    update public.profiles
      set verification_badge=v_badge,
          verification_tier=(case when v_badge='GOLD' then 'Gold' else 'Standard' end)::public.verification_tier_enum,
          is_verified=true,
          verified_at=coalesce(verified_at,now()),
          verification_expires_at=v_new_expiry,
          updated_at=now()
      where id=v_order.user_id;
  end if;

  update public.blink_paystack_orders
    set status='fulfilled',
        provider_transaction_id=p_provider_transaction_id,
        fulfilled_at=now(),updated_at=now()
    where id=v_order.id;

  return jsonb_build_object('success',true,'already_fulfilled',false,'kind',v_order.kind,
    'tier',v_badge,'coin_result',v_coins,'expires_at',v_new_expiry);
end;
$$;
revoke all on function public.fulfill_blink_paystack_order(text,text,bigint,text)
  from public,anon,authenticated;
grant execute on function public.fulfill_blink_paystack_order(text,text,bigint,text)
  to service_role;
