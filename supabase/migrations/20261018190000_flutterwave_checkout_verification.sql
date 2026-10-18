-- BLINK Flutterwave phases 1-2: secure cash verification ledger and atomic fulfilment.
-- Staged only. Deploy to Testlab Supabase before enabling cash_checkout_enabled.
-- No payment credentials or production activation belong in this migration.

create table if not exists public.blink_flutterwave_payments (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  tx_ref text not null unique,
  tier text not null check (tier in ('BLUE','GOLD')),
  amount_ngn integer not null check (amount_ngn > 0),
  currency text not null default 'NGN' check (currency = 'NGN'),
  duration_days integer not null default 30 check (duration_days between 1 and 365),
  provider_transaction_id text,
  status text not null default 'pending' check (status in ('pending','fulfilled','failed')),
  created_at timestamptz not null default now(),
  verified_at timestamptz,
  updated_at timestamptz not null default now()
);

create unique index if not exists blink_flutterwave_payments_transaction_uidx
  on public.blink_flutterwave_payments(provider_transaction_id)
  where provider_transaction_id is not null;
create index if not exists blink_flutterwave_payments_user_created_idx
  on public.blink_flutterwave_payments(user_id, created_at desc);

alter table public.blink_flutterwave_payments enable row level security;
revoke all on public.blink_flutterwave_payments from public, anon, authenticated;
grant select on public.blink_flutterwave_payments to authenticated;
drop policy if exists blink_flutterwave_payment_own_read on public.blink_flutterwave_payments;
create policy blink_flutterwave_payment_own_read
  on public.blink_flutterwave_payments for select to authenticated
  using (user_id = (select auth.uid()));

-- Only the signed-in customer creates orders. Amounts always come from private server configuration.
create or replace function public.create_blink_flutterwave_verification_order(p_tier text)
returns jsonb language plpgsql security definer set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_tier text := upper(trim(coalesce(p_tier,'')));
  v_cash_enabled boolean;
  v_amount integer;
  v_days integer := 30;
  v_follower_count integer := 0;
  v_badge text;
  v_expires timestamptz;
  v_ref text;
  v_recent integer;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  if v_tier not in ('BLUE','GOLD') then raise exception 'INVALID_VERIFICATION_TIER'; end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text)::bigint);
  select coalesce(bool_value,false) into v_cash_enabled
    from private.blink_economy_config where key = 'cash_checkout_enabled';
  if not coalesce(v_cash_enabled,false) then raise exception 'CASH_CHECKOUT_DISABLED'; end if;

  select upper(coalesce(verification_badge,'NONE')), verification_expires_at
    into v_badge,v_expires
    from public.profiles where id=v_user;
  if not found then raise exception 'PROFILE_NOT_FOUND'; end if;

  -- Count real follow relationships, not a cached or client-editable profile counter.
  if v_tier = 'GOLD' then
    select count(*)::integer into v_follower_count
    from public.follows where following_id=v_user;
  end if;
  if v_tier = 'GOLD' and v_follower_count < 1000 then
    raise exception 'GOLD_FOLLOWER_REQUIREMENT_NOT_MET';
  end if;
  if v_tier = 'BLUE' and v_badge = 'GOLD'
     and (v_expires is null or v_expires > now()) then
    raise exception 'ALREADY_GOLD_VERIFIED';
  end if;

  select count(*) into v_recent from public.blink_flutterwave_payments
    where user_id=v_user and created_at > now()-interval '30 minutes';
  if v_recent >= 5 then raise exception 'PAYMENT_RATE_LIMIT'; end if;

  if v_tier = 'BLUE' then
    select coalesce(int_value,800)::integer into v_amount
      from private.blink_economy_config where key='blue_verification_cash_ngn';
  else
    select coalesce(int_value,2000)::integer into v_amount
      from private.blink_economy_config where key='gold_verification_cash_ngn';
  end if;
  if v_amount is null or v_amount <= 0 then raise exception 'PAYMENT_PRICE_UNAVAILABLE'; end if;

  v_ref := 'blink-flw-' || replace(gen_random_uuid()::text,'-','');
  insert into public.blink_flutterwave_payments(user_id,tx_ref,tier,amount_ngn,duration_days)
  values(v_user,v_ref,v_tier,v_amount,v_days);

  return jsonb_build_object('tx_ref',v_ref,'tier',v_tier,'amount_ngn',v_amount,
    'duration_days',v_days,'currency','NGN','status','pending');
end;
$$;
revoke all on function public.create_blink_flutterwave_verification_order(text) from public, anon;
grant execute on function public.create_blink_flutterwave_verification_order(text) to authenticated;

-- Called ONLY by trusted backend AFTER retrieving a successful transaction from Flutterwave's API.
-- SERIALIZED and idempotent so webhook + manual confirmation cannot double-extend a badge.
create or replace function public.fulfill_blink_flutterwave_verification(
  p_tx_ref text, p_provider_transaction_id text, p_amount_ngn integer, p_currency text
)
returns jsonb language plpgsql security invoker set search_path = ''
as $$
declare
  v_order public.blink_flutterwave_payments%rowtype;
  v_current_badge text;
  v_current_expires timestamptz;
  v_badge text;
  v_until timestamptz;
begin
  if current_user <> 'service_role' then
    raise exception 'SERVICE_ROLE_REQUIRED' using errcode='42501';
  end if;
  select * into v_order from public.blink_flutterwave_payments
    where tx_ref=p_tx_ref for update;
  if not found then raise exception 'PAYMENT_ORDER_NOT_FOUND'; end if;

  if v_order.status='fulfilled' then
    if v_order.provider_transaction_id is distinct from p_provider_transaction_id then
      raise exception 'TRANSACTION_REFERENCE_CONFLICT';
    end if;
    return jsonb_build_object('success',true,'already_fulfilled',true,'badge',v_order.tier);
  end if;

  if v_order.status <> 'pending'
     or nullif(trim(coalesce(p_provider_transaction_id,'')),'') is null
     or v_order.amount_ngn <> p_amount_ngn
     or v_order.currency <> p_currency then
    raise exception 'VERIFIED_PAYMENT_MISMATCH';
  end if;

  select upper(coalesce(verification_badge,'NONE')), verification_expires_at
    into v_current_badge,v_current_expires
    from public.profiles where id=v_order.user_id for update;
  if not found then raise exception 'PROFILE_NOT_FOUND'; end if;

  -- A pre-existing active Gold badge must never be downgraded by a Blue checkout.
  v_badge := case
    when v_current_badge='GOLD' and (v_current_expires is null or v_current_expires>now())
      then 'GOLD'
    else v_order.tier
  end;
  -- Do not shorten legacy permanent verification when a cash order is fulfilled.
  v_until := case
    when v_current_badge in ('BLUE','GOLD') and v_current_expires is null
      then null
    else greatest(coalesce(v_current_expires,now()),now()) +
      make_interval(days=>v_order.duration_days)
  end;

  update public.profiles
    set verification_badge=v_badge,
        verification_tier=(case when v_badge='GOLD' then 'Gold' else 'Standard' end)::public.verification_tier_enum,
        is_verified=true,
        verified_at=coalesce(verified_at,now()),
        verification_expires_at=v_until,
        updated_at=now()
    where id=v_order.user_id;

  update public.blink_flutterwave_payments
    set status='fulfilled',provider_transaction_id=p_provider_transaction_id,
        verified_at=now(),updated_at=now()
    where id=v_order.id;

  return jsonb_build_object('success',true,'already_fulfilled',false,
    'badge',v_badge,'expires_at',v_until,'user_id',v_order.user_id);
end;
$$;
revoke all on function public.fulfill_blink_flutterwave_verification(text,text,integer,text)
  from public, anon, authenticated;
grant execute on function public.fulfill_blink_flutterwave_verification(text,text,integer,text)
  to service_role;

-- Checkout stays switched off until staging integration tests pass and credentials are configured.
insert into private.blink_economy_config(key,int_value)
values ('gold_verification_cash_ngn',2000)
on conflict (key) do nothing;
