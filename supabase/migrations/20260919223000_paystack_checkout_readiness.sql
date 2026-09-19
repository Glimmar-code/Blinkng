-- BLINK Paystack checkout readiness.
-- Additive only: production checkout stays disabled until Paystack credentials are configured and validated.
-- rollback-plan: disable cash checkout, revoke/drop the new verification-order/status RPCs,
-- drop public.blink_verification_purchase_orders, and remove the two readiness config keys.

insert into private.blink_economy_config(key, int_value)
values ('blue_verification_valid_days', 30)
on conflict(key) do update
set int_value = excluded.int_value, updated_at = now();

insert into private.blink_economy_config(key, json_value)
values (
  'paystack_checkout',
  '{"provider":"paystack","currency":"NGN","hosted_checkout":true,"webhook_required":true}'::jsonb
)
on conflict(key) do update
set json_value = excluded.json_value, updated_at = now();

create table if not exists public.blink_verification_purchase_orders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  provider text not null default 'paystack' check (provider = 'paystack'),
  amount_ngn integer not null check (amount_ngn > 0),
  currency text not null default 'NGN' check (currency = 'NGN'),
  valid_days integer not null default 30 check (valid_days between 1 and 366),
  status text not null default 'pending'
    check (status in ('pending','paid','fulfilled','failed','cancelled')),
  provider_reference text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  paid_at timestamptz,
  fulfilled_at timestamptz,
  metadata jsonb not null default '{}'::jsonb
);

create index if not exists blink_verification_purchase_orders_user_created_idx
  on public.blink_verification_purchase_orders(user_id, created_at desc);

create unique index if not exists blink_verification_purchase_orders_provider_reference_uidx
  on public.blink_verification_purchase_orders(provider_reference)
  where provider_reference is not null;

alter table public.blink_verification_purchase_orders enable row level security;
revoke all on public.blink_verification_purchase_orders from public, anon, authenticated;
grant select on public.blink_verification_purchase_orders to authenticated;

drop policy if exists blink_verification_purchase_orders_own_read
  on public.blink_verification_purchase_orders;
create policy blink_verification_purchase_orders_own_read
on public.blink_verification_purchase_orders
for select
to authenticated
using ((select auth.uid()) = user_id);

create or replace function public.create_blink_verification_purchase_order()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_price integer := 800;
  v_valid_days integer := 30;
  v_enabled boolean := false;
  v_badge text;
  v_id uuid;
begin
  if v_user is null then
    raise exception 'AUTH_REQUIRED';
  end if;

  select upper(coalesce(verification_badge, 'NONE'))
    into v_badge
    from public.profiles
   where id = v_user;

  if not found then
    raise exception 'PROFILE_NOT_FOUND';
  end if;

  if v_badge = 'GOLD' then
    raise exception 'GOLD_VERIFICATION_NOT_PURCHASABLE';
  end if;

  select coalesce(int_value, 800)::integer
    into v_price
    from private.blink_economy_config
   where key = 'blue_verification_cash_ngn';

  select coalesce(int_value, 30)::integer
    into v_valid_days
    from private.blink_economy_config
   where key = 'blue_verification_valid_days';

  select coalesce(bool_value, false)
    into v_enabled
    from private.blink_economy_config
   where key = 'cash_checkout_enabled';

  if not coalesce(v_enabled, false) then
    raise exception 'CASH_CHECKOUT_DISABLED';
  end if;

  insert into public.blink_verification_purchase_orders(
    user_id, amount_ngn, valid_days, metadata
  )
  values (
    v_user,
    v_price,
    v_valid_days,
    jsonb_build_object('product', 'BLINK_VERIFIED', 'badge', 'BLUE')
  )
  returning id into v_id;

  return jsonb_build_object(
    'success', true,
    'order_id', v_id,
    'amount_ngn', v_price,
    'currency', 'NGN',
    'valid_days', v_valid_days,
    'status', 'pending'
  );
end
$$;

revoke all on function public.create_blink_verification_purchase_order() from public, anon;
grant execute on function public.create_blink_verification_purchase_order() to authenticated;

create or replace function public.fulfill_blink_verification_purchase_order(
  p_order_id uuid,
  p_provider_reference text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_order public.blink_verification_purchase_orders%rowtype;
  v_badge text;
  v_expires_at timestamptz;
begin
  if coalesce(trim(p_provider_reference), '') = '' then
    raise exception 'PAYMENT_REFERENCE_REQUIRED';
  end if;

  select *
    into v_order
    from public.blink_verification_purchase_orders
   where id = p_order_id
   for update;

  if not found then
    raise exception 'VERIFICATION_PURCHASE_ORDER_NOT_FOUND';
  end if;

  if v_order.status = 'fulfilled' then
    select verification_expires_at
      into v_expires_at
      from public.profiles
     where id = v_order.user_id;

    return jsonb_build_object(
      'success', true,
      'already_fulfilled', true,
      'expires_at', v_expires_at
    );
  end if;

  if v_order.provider_reference is not null
     and v_order.provider_reference <> trim(p_provider_reference) then
    raise exception 'PAYMENT_REFERENCE_MISMATCH';
  end if;

  select upper(coalesce(verification_badge, 'NONE'))
    into v_badge
    from public.profiles
   where id = v_order.user_id
   for update;

  if not found then
    raise exception 'PROFILE_NOT_FOUND';
  end if;

  if v_badge = 'GOLD' then
    v_expires_at := null;
  else
    update public.profiles
       set verification_badge = 'BLUE',
           verification_tier = 'Standard'::public.verification_tier_enum,
           is_verified = true,
           verified_at = coalesce(verified_at, now()),
           verification_expires_at =
             greatest(coalesce(verification_expires_at, now()), now())
             + make_interval(days => v_order.valid_days),
           updated_at = now()
     where id = v_order.user_id
     returning verification_expires_at into v_expires_at;
  end if;

  update public.blink_verification_purchase_orders
     set status = 'fulfilled',
         provider_reference = trim(p_provider_reference),
         paid_at = coalesce(paid_at, now()),
         fulfilled_at = now(),
         updated_at = now(),
         metadata = coalesce(metadata, '{}'::jsonb)
           || jsonb_build_object(
             'fulfilled_badge', v_badge,
             'verification_expires_at', v_expires_at
           )
   where id = v_order.id;

  return jsonb_build_object(
    'success', true,
    'already_fulfilled', false,
    'badge', case when v_badge = 'GOLD' then 'GOLD' else 'BLUE' end,
    'expires_at', v_expires_at
  );
end
$$;

revoke all on function public.fulfill_blink_verification_purchase_order(uuid, text)
  from public, anon, authenticated;
grant execute on function public.fulfill_blink_verification_purchase_order(uuid, text)
  to service_role;

create or replace function public.get_blink_cash_order_status(p_order_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_coin public.blink_coin_purchase_orders%rowtype;
  v_verification public.blink_verification_purchase_orders%rowtype;
begin
  if v_user is null then
    raise exception 'AUTH_REQUIRED';
  end if;

  select *
    into v_coin
    from public.blink_coin_purchase_orders
   where id = p_order_id
     and user_id = v_user;

  if found then
    return jsonb_build_object(
      'order_id', v_coin.id,
      'kind', 'COIN_PACK',
      'status', v_coin.status,
      'amount_ngn', v_coin.amount_ngn,
      'currency', 'NGN',
      'provider', v_coin.provider,
      'provider_reference', v_coin.provider_reference,
      'created_at', v_coin.created_at,
      'paid_at', v_coin.paid_at,
      'fulfilled_at', v_coin.fulfilled_at
    );
  end if;

  select *
    into v_verification
    from public.blink_verification_purchase_orders
   where id = p_order_id
     and user_id = v_user;

  if found then
    return jsonb_build_object(
      'order_id', v_verification.id,
      'kind', 'BLUE_VERIFICATION',
      'status', v_verification.status,
      'amount_ngn', v_verification.amount_ngn,
      'currency', v_verification.currency,
      'provider', v_verification.provider,
      'provider_reference', v_verification.provider_reference,
      'created_at', v_verification.created_at,
      'paid_at', v_verification.paid_at,
      'fulfilled_at', v_verification.fulfilled_at
    );
  end if;

  raise exception 'CASH_ORDER_NOT_FOUND';
end
$$;

revoke all on function public.get_blink_cash_order_status(uuid) from public, anon;
grant execute on function public.get_blink_cash_order_status(uuid) to authenticated;
