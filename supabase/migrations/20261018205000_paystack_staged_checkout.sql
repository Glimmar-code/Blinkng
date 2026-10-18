-- BLINK Paystack checkout. Deploy only to an isolated staging database first.
-- Payment enable switches default off. No live payment is enabled by this migration.
-- Payment references / benefits are determined server-side, never by Android.

insert into private.blink_economy_config(key,int_value)
values ('gold_verification_cash_ngn',2000)
on conflict(key) do nothing;

create table if not exists public.blink_paystack_orders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  reference text not null unique,
  kind text not null check(kind in ('VERIFICATION','COINS')),
  verification_tier text check(verification_tier in ('BLUE','GOLD')),
  coin_order_id uuid references public.blink_coin_purchase_orders(id),
  customer_email text not null,
  amount_kobo bigint not null check(amount_kobo between 100 and 1000000000),
  currency text not null default 'NGN' check(currency = 'NGN'),
  duration_days integer not null default 30 check(duration_days between 1 and 365),
  transaction_id text unique,
  status text not null default 'pending'
    check(status in ('pending','fulfilled','failed','cancelled')),
  paid_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check ((kind='VERIFICATION' and verification_tier is not null and coin_order_id is null)
       or (kind='COINS' and coin_order_id is not null and verification_tier is null))
);
create unique index if not exists blink_paystack_coin_order_once
  on public.blink_paystack_orders(coin_order_id) where coin_order_id is not null;
create index if not exists blink_paystack_orders_user_created
  on public.blink_paystack_orders(user_id,created_at desc);

alter table public.blink_paystack_orders enable row level security;
revoke all on public.blink_paystack_orders from public,anon,authenticated;
grant select on public.blink_paystack_orders to authenticated;
create policy blink_paystack_order_owner_select
  on public.blink_paystack_orders for select to authenticated
  using(user_id=(select auth.uid()));

create or replace function public.create_blink_paystack_order(
  p_kind text, p_tier text default null, p_pack_id text default null
)
returns jsonb language plpgsql security definer set search_path=''
as $$
declare
  v_user uuid:=auth.uid();
  v_kind text:=upper(trim(coalesce(p_kind,'')));
  v_tier text:=upper(trim(coalesce(p_tier,'')));
  v_enabled boolean;
  v_amount integer;
  v_count integer;
  v_ref text;
  v_email text;
  v_coin_response jsonb;
  v_coin_order uuid;
  v_coin_amount integer;
  v_badge text;
  v_expires timestamptz;
  v_order_id uuid;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  if v_kind not in ('VERIFICATION','COINS') then raise exception 'INVALID_PAYMENT_KIND'; end if;
  if v_kind='VERIFICATION' and v_tier not in ('BLUE','GOLD') then
    raise exception 'INVALID_VERIFICATION_TIER';
  end if;
  if v_kind='COINS' and (coalesce(trim(p_pack_id),'')='' or coalesce(trim(p_tier),'')<>'') then
    raise exception 'INVALID_COIN_PACK';
  end if;
  select coalesce(bool_value,false) into v_enabled
    from private.blink_economy_config where key='cash_checkout_enabled';
  if not coalesce(v_enabled,false) then raise exception 'CASH_CHECKOUT_DISABLED'; end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text)::bigint);
  select upper(coalesce(verification_badge,'NONE')),verification_expires_at
    into v_badge,v_expires
    from public.profiles where id=v_user;
  if not found then raise exception 'PROFILE_NOT_FOUND'; end if;
  -- Never use a user-editable profile email for payment identity.
  select lower(trim(coalesce(email,''))) into v_email
    from auth.users where id=v_user;
  if coalesce(v_email,'')='' then raise exception 'ACCOUNT_EMAIL_REQUIRED'; end if;

  select count(*)::integer into v_count from public.blink_paystack_orders
    where user_id=v_user and created_at>now()-interval '30 minutes';
  if v_count>=5 then raise exception 'PAYMENT_RATE_LIMIT'; end if;

  if v_kind='VERIFICATION' then
    if v_tier='BLUE' and v_badge='GOLD'
       and (v_expires is null or v_expires>now()) then
      raise exception 'ALREADY_GOLD_VERIFIED';
    end if;
    if v_tier='GOLD' then
      select count(*)::integer into v_count from public.follows
        where following_id=v_user;
      if v_count<1000 then raise exception 'GOLD_REQUIRES_1000_FOLLOWERS'; end if;
      select coalesce(int_value,2000)::integer into v_amount
        from private.blink_economy_config where key='gold_verification_cash_ngn';
    else
      select coalesce(int_value,800)::integer into v_amount
        from private.blink_economy_config where key='blue_verification_cash_ngn';
    end if;
  else
    -- Reuse the existing server-priced Blink Coin ordering policy.
    v_coin_response:=public.create_blink_coin_purchase_order(p_pack_id);
    v_coin_order:=(v_coin_response->>'order_id')::uuid;
    v_amount:=(v_coin_response->>'amount_ngn')::integer;
    v_coin_amount:=(v_coin_response->>'coins')::integer;
    update public.blink_coin_purchase_orders
       set provider='paystack' where id=v_coin_order and user_id=v_user;
  end if;
  if v_amount is null or v_amount<=0 then raise exception 'INVALID_PAYMENT_AMOUNT'; end if;
  v_ref:='blink-ps-'||replace(gen_random_uuid()::text,'-','');
  insert into public.blink_paystack_orders
    (user_id,reference,kind,verification_tier,coin_order_id,customer_email,amount_kobo)
  values(v_user,v_ref,v_kind,
         case when v_kind='VERIFICATION' then v_tier else null end,
         v_coin_order,lower(trim(v_email)),v_amount::bigint*100)
  returning id into v_order_id;

  return jsonb_build_object('id',v_order_id,'reference',v_ref,'kind',v_kind,
    'tier',case when v_kind='VERIFICATION' then v_tier else null end,
    'pack_id',p_pack_id,'coin_amount',v_coin_amount,
    'amount_kobo',v_amount::bigint*100,'currency','NGN','status','pending');
end;
$$;
revoke all on function public.create_blink_paystack_order(text,text,text)
  from public,anon;
grant execute on function public.create_blink_paystack_order(text,text,text)
  to authenticated;

-- Invoker privileges: only Supabase service_role may reconcile Paystack's verified receipt.
-- This is one database transaction: coin credit / badge grant and payment state commit together.
create or replace function public.fulfill_blink_paystack_order(
  p_reference text, p_transaction_id text, p_amount_kobo bigint,
  p_currency text, p_customer_email text
)
returns jsonb language plpgsql security invoker set search_path=''
as $$
declare
  v_order public.blink_paystack_orders%rowtype;
  v_badge text;
  v_old_expiry timestamptz;
  v_target_badge text;
  v_new_expiry timestamptz;
  v_coin_response jsonb;
begin
  if current_user <> 'service_role' then
    raise exception 'SERVER_ONLY' using errcode='42501';
  end if;
  select * into v_order from public.blink_paystack_orders
    where reference=p_reference for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if v_order.status='fulfilled' then
    if v_order.transaction_id is distinct from p_transaction_id then
      raise exception 'REFERENCE_CONFLICT';
    end if;
    return jsonb_build_object('success',true,'already_fulfilled',true,
      'kind',v_order.kind);
  end if;
  if v_order.status<>'pending'
    or nullif(trim(coalesce(p_transaction_id,'')),'') is null
    or p_transaction_id !~ '^[0-9]+$'
    or v_order.amount_kobo<>p_amount_kobo
    or v_order.currency<>p_currency
    or v_order.customer_email<>lower(trim(coalesce(p_customer_email,''))) then
    raise exception 'PAYMENT_MISMATCH';
  end if;

  if v_order.kind='COINS' then
    v_coin_response:=public.fulfill_blink_coin_purchase_order(
      v_order.coin_order_id,p_reference
    );
    if coalesce((v_coin_response->>'success')::boolean,false)=false then
      raise exception 'COIN_CREDIT_FAILED';
    end if;
  else
    select upper(coalesce(verification_badge,'NONE')),verification_expires_at
    into v_badge,v_old_expiry from public.profiles
    where id=v_order.user_id for update;
    if not found then raise exception 'PROFILE_NOT_FOUND'; end if;
    -- Never downgrade an active Gold badge; never shorten a permanent badge.
    v_target_badge:=case
      when v_badge='GOLD' and (v_old_expiry is null or v_old_expiry>now())
      then 'GOLD' else v_order.verification_tier end;
    v_new_expiry:=case
      when v_badge in ('BLUE','GOLD') and v_old_expiry is null
      then null
      when v_old_expiry>now() then v_old_expiry+make_interval(days=>v_order.duration_days)
      else now()+make_interval(days=>v_order.duration_days)
      end;
    update public.profiles set verification_badge=v_target_badge,
      verification_tier=(case when v_target_badge='GOLD' then 'Gold'
        else 'Standard' end)::public.verification_tier_enum,
      is_verified=true,verified_at=coalesce(verified_at,now()),
      verification_expires_at=v_new_expiry,updated_at=now()
      where id=v_order.user_id;
  end if;

  update public.blink_paystack_orders set
    transaction_id=p_transaction_id,status='fulfilled',paid_at=now(),updated_at=now()
  where id=v_order.id;
  return jsonb_build_object('success',true,'already_fulfilled',false,
    'kind',v_order.kind,'tier',v_order.verification_tier,
    'user_id',v_order.user_id,'paid_at',now());
end;
$$;
revoke all on function public.fulfill_blink_paystack_order(text,text,bigint,text,text)
  from public,anon,authenticated;
grant execute on function public.fulfill_blink_paystack_order(text,text,bigint,text,text)
  to service_role;

-- Intentionally leave private.blink_economy_config.cash_checkout_enabled false.
