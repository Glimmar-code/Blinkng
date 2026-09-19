-- BLINK Coin Economy v1: server-controlled rewarded milestones, coin packs and coin verification.
-- destructive-change-reviewed: additive config/order schema plus a backward-compatible replacement of the rewarded-ad completion RPC.
-- rollback-plan: restore complete_blink_rewarded_ad_claim from 20260918193000, revoke/drop the new economy RPCs,
-- drop blink_coin_purchase_orders, drop the two rewarded-claim accounting columns, then drop private.blink_economy_config.
-- Cash purchase orders never credit coins by themselves. Only the service-role fulfilment RPC can credit a paid order.

create table if not exists private.blink_economy_config (
  key text primary key,
  int_value bigint,
  bool_value boolean,
  json_value jsonb,
  updated_at timestamptz not null default now()
);
revoke all on private.blink_economy_config from public, anon, authenticated;

insert into private.blink_economy_config(key,int_value) values
  ('rewarded_ad_base_coins',10),
  ('rewarded_ad_daily_limit',15),
  ('blue_verification_cash_ngn',800),
  ('blue_verification_coin_cost',3000)
on conflict(key) do update set int_value=excluded.int_value,updated_at=now();

insert into private.blink_economy_config(key,bool_value) values
  ('cash_checkout_enabled',false)
on conflict(key) do update set bool_value=excluded.bool_value,updated_at=now();

insert into private.blink_economy_config(key,json_value) values
  ('rewarded_milestones','[
    {"ads":5,"total_coins":60},
    {"ads":10,"total_coins":130},
    {"ads":15,"total_coins":210}
  ]'::jsonb),
  ('coin_packs','[
    {"id":"coins_100","price_ngn":100,"coins":100},
    {"id":"coins_500","price_ngn":500,"coins":550},
    {"id":"coins_1000","price_ngn":1000,"coins":1200},
    {"id":"coins_2000","price_ngn":2000,"coins":2600},
    {"id":"coins_5000","price_ngn":5000,"coins":7000}
  ]'::jsonb)
on conflict(key) do update set json_value=excluded.json_value,updated_at=now();

alter table public.blink_rewarded_ad_claims
  add column if not exists credited_amount integer not null default 0 check (credited_amount >= 0),
  add column if not exists milestone_bonus integer not null default 0 check (milestone_bonus >= 0);

-- Existing completed claims were already paid under the original 10-coin rule.
-- Backfill their accounting so today's earned total and idempotent retries stay correct.
update public.blink_rewarded_ad_claims
set credited_amount = reward_amount
where rewarded_at is not null
  and credited_amount = 0;

create table if not exists public.blink_coin_purchase_orders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  pack_id text not null,
  provider text not null default 'paystack',
  amount_ngn integer not null check(amount_ngn > 0),
  coin_amount integer not null check(coin_amount > 0),
  bonus_coins integer not null default 0 check(bonus_coins >= 0),
  status text not null default 'pending' check(status in ('pending','paid','fulfilled','failed','cancelled')),
  provider_reference text,
  created_at timestamptz not null default now(),
  paid_at timestamptz,
  fulfilled_at timestamptz,
  metadata jsonb not null default '{}'::jsonb
);

create index if not exists blink_coin_purchase_orders_user_created_idx
  on public.blink_coin_purchase_orders(user_id,created_at desc);
create unique index if not exists blink_coin_purchase_orders_provider_reference_uidx
  on public.blink_coin_purchase_orders(provider_reference)
  where provider_reference is not null;

alter table public.blink_coin_purchase_orders enable row level security;
revoke all on public.blink_coin_purchase_orders from anon, authenticated;
grant select on public.blink_coin_purchase_orders to authenticated;
drop policy if exists blink_coin_purchase_orders_own_read on public.blink_coin_purchase_orders;
create policy blink_coin_purchase_orders_own_read
on public.blink_coin_purchase_orders for select to authenticated
using(user_id=auth.uid());

create or replace function public.get_blink_economy_status()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_base integer;
  v_limit integer;
  v_cash integer;
  v_verify_coins integer;
  v_cash_enabled boolean;
  v_milestones jsonb;
  v_packs jsonb;
  v_ads_today integer;
  v_earned_today bigint;
  v_balance bigint;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;

  select coalesce(int_value,10)::integer into v_base
    from private.blink_economy_config where key='rewarded_ad_base_coins';
  select coalesce(int_value,15)::integer into v_limit
    from private.blink_economy_config where key='rewarded_ad_daily_limit';
  select coalesce(int_value,800)::integer into v_cash
    from private.blink_economy_config where key='blue_verification_cash_ngn';
  select coalesce(int_value,3000)::integer into v_verify_coins
    from private.blink_economy_config where key='blue_verification_coin_cost';
  select coalesce(bool_value,false) into v_cash_enabled
    from private.blink_economy_config where key='cash_checkout_enabled';
  select coalesce(json_value,'[]'::jsonb) into v_milestones
    from private.blink_economy_config where key='rewarded_milestones';
  select coalesce(json_value,'[]'::jsonb) into v_packs
    from private.blink_economy_config where key='coin_packs';

  select count(*)::integer,coalesce(sum(credited_amount),0)::bigint
    into v_ads_today,v_earned_today
    from public.blink_rewarded_ad_claims
   where user_id=v_user
     and rewarded_at >= date_trunc('day',now() at time zone 'UTC') at time zone 'UTC';

  select coalesce(floor(spendable_coin_balance),0)::bigint into v_balance
    from public.user_balances where user_id=v_user;

  return jsonb_build_object(
    'rewarded_ad_base_coins',coalesce(v_base,10),
    'rewarded_ad_daily_limit',coalesce(v_limit,15),
    'rewarded_milestones',coalesce(v_milestones,'[]'::jsonb),
    'blue_verification_cash_ngn',coalesce(v_cash,800),
    'blue_verification_coin_cost',coalesce(v_verify_coins,3000),
    'coin_packs',coalesce(v_packs,'[]'::jsonb),
    'cash_checkout_enabled',coalesce(v_cash_enabled,false),
    'ads_today',coalesce(v_ads_today,0),
    'coins_earned_from_ads_today',coalesce(v_earned_today,0),
    'balance',coalesce(v_balance,0)
  );
end
$$;
revoke all on function public.get_blink_economy_status() from public, anon;
grant execute on function public.get_blink_economy_status() to authenticated;

create or replace function public.complete_blink_rewarded_ad_claim(p_claim_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_claim public.blink_rewarded_ad_claims%rowtype;
  v_balance bigint;
  v_base integer := 10;
  v_limit integer := 15;
  v_ads_before integer := 0;
  v_ad_number integer;
  v_previous_total integer;
  v_target_total integer;
  v_credit integer;
  v_bonus integer;
  v_milestones jsonb := '[]'::jsonb;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;

  select * into v_claim
    from public.blink_rewarded_ad_claims
   where id=p_claim_id and user_id=v_user
   for update;

  if not found then raise exception 'REWARDED_AD_CLAIM_EXPIRED'; end if;

  select coalesce(int_value,10)::integer into v_base
    from private.blink_economy_config where key='rewarded_ad_base_coins';
  select coalesce(int_value,15)::integer into v_limit
    from private.blink_economy_config where key='rewarded_ad_daily_limit';
  select coalesce(json_value,'[]'::jsonb) into v_milestones
    from private.blink_economy_config where key='rewarded_milestones';

  if v_claim.rewarded_at is not null then
    select coalesce(floor(spendable_coin_balance),0)::bigint into v_balance
      from public.user_balances where user_id=v_user;
    select count(*)::integer into v_ad_number
      from public.blink_rewarded_ad_claims
     where user_id=v_user
       and rewarded_at >= date_trunc('day',v_claim.rewarded_at at time zone 'UTC') at time zone 'UTC'
       and rewarded_at <= v_claim.rewarded_at;
    return jsonb_build_object(
      'success',true,'already_credited',true,
      'reward_amount',coalesce(v_claim.credited_amount,v_claim.reward_amount),
      'admob_reward_amount',v_claim.reward_amount,
      'milestone_bonus',coalesce(v_claim.milestone_bonus,0),
      'ads_today',coalesce(v_ad_number,0),'daily_limit',v_limit,
      'balance',coalesce(v_balance,0)
    );
  end if;

  if v_claim.started_at < now()-interval '30 minutes' then raise exception 'REWARDED_AD_CLAIM_EXPIRED'; end if;
  if v_claim.started_at > now()-interval '5 seconds' then raise exception 'REWARDED_AD_TOO_SOON'; end if;

  -- Serialize rewards per user so concurrent callbacks cannot duplicate a milestone
  -- or race past the daily cap.
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text)::bigint);

  select count(*)::integer into v_ads_before
    from public.blink_rewarded_ad_claims
   where user_id=v_user
     and rewarded_at >= date_trunc('day',now() at time zone 'UTC') at time zone 'UTC';

  if v_ads_before >= v_limit then raise exception 'REWARDED_AD_DAILY_LIMIT'; end if;

  v_ad_number := v_ads_before + 1;
  v_previous_total := (v_ad_number-1)*v_base;
  v_target_total := v_ad_number*v_base;

  select greatest(v_previous_total,coalesce(max((m->>'total_coins')::integer),v_previous_total))
    into v_previous_total
    from jsonb_array_elements(v_milestones) m
   where (m->>'ads')::integer <= v_ad_number-1;

  select greatest(v_target_total,coalesce(max((m->>'total_coins')::integer),v_target_total))
    into v_target_total
    from jsonb_array_elements(v_milestones) m
   where (m->>'ads')::integer <= v_ad_number;

  v_credit := greatest(v_base,v_target_total-v_previous_total);
  v_bonus := greatest(0,v_credit-v_base);

  insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
  values(v_user,0,now()) on conflict(user_id) do nothing;

  update public.user_balances
     set spendable_coin_balance=spendable_coin_balance+v_credit,updated_at=now()
   where user_id=v_user
  returning floor(spendable_coin_balance)::bigint into v_balance;

  perform set_config('blink.coin_sync_bypass','1',true);
  insert into public.game_profiles(user_id,coins,updated_at)
  values(v_user,v_balance,now())
  on conflict(user_id) do update set coins=excluded.coins,updated_at=now();
  perform set_config('blink.coin_sync_bypass','0',true);

  update public.blink_rewarded_ad_claims
     set rewarded_at=now(),credited_amount=v_credit,milestone_bonus=v_bonus,
         metadata=coalesce(metadata,'{}'::jsonb)||jsonb_build_object('ad_number',v_ad_number,'daily_limit',v_limit)
   where id=v_claim.id;

  insert into public.blink_coin_transactions(user_id,kind,item_name,amount,balance_after,metadata)
  values(v_user,'REWARDED_AD','Rewarded ad',v_credit,v_balance,
    jsonb_build_object('claim_id',v_claim.id,'ad_number',v_ad_number,'base_coins',v_base,'milestone_bonus',v_bonus));

  return jsonb_build_object(
    'success',true,'already_credited',false,
    'reward_amount',v_credit,'admob_reward_amount',v_claim.reward_amount,
    'milestone_bonus',v_bonus,'ads_today',v_ad_number,'daily_limit',v_limit,'balance',v_balance
  );
end
$$;
revoke all on function public.complete_blink_rewarded_ad_claim(uuid) from public, anon;
grant execute on function public.complete_blink_rewarded_ad_claim(uuid) to authenticated;

create or replace function public.purchase_blink_blue_verification_with_coins()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_cost integer := 3000;
  v_balance bigint;
  v_badge text;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;

  -- Keep verification purchase atomic with other per-user economy mutations.
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text)::bigint);

  select upper(coalesce(verification_badge,'NONE')) into v_badge
    from public.profiles where id=v_user for update;
  if not found then raise exception 'PROFILE_NOT_FOUND'; end if;

  if v_badge in ('BLUE','GOLD') then
    select coalesce(floor(spendable_coin_balance),0)::bigint into v_balance
      from public.user_balances where user_id=v_user;
    return jsonb_build_object('success',true,'already_verified',true,'cost',0,'balance',coalesce(v_balance,0),'badge',v_badge);
  end if;

  select coalesce(int_value,3000)::integer into v_cost
    from private.blink_economy_config where key='blue_verification_coin_cost';

  insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
  values(v_user,0,now()) on conflict(user_id) do nothing;

  select floor(spendable_coin_balance)::bigint into v_balance
    from public.user_balances where user_id=v_user for update;

  if coalesce(v_balance,0) < v_cost then raise exception 'INSUFFICIENT_BLINK_COINS'; end if;

  update public.user_balances
     set spendable_coin_balance=spendable_coin_balance-v_cost,updated_at=now()
   where user_id=v_user
  returning floor(spendable_coin_balance)::bigint into v_balance;

  perform set_config('blink.coin_sync_bypass','1',true);
  insert into public.game_profiles(user_id,coins,updated_at)
  values(v_user,v_balance,now())
  on conflict(user_id) do update set coins=excluded.coins,updated_at=now();
  perform set_config('blink.coin_sync_bypass','0',true);

  update public.profiles
     set verification_badge='BLUE',
         verification_tier='Standard'::public.verification_tier_enum,
         is_verified=true,
         verified_at=coalesce(verified_at,now()),
         verification_expires_at=null,
         updated_at=now()
   where id=v_user;

  insert into public.blink_coin_transactions(user_id,kind,item_name,amount,balance_after,metadata)
  values(v_user,'VERIFICATION_PURCHASE','BLINK Verified',-v_cost,v_balance,
    jsonb_build_object('badge','BLUE','purchase_method','blink_coins'));

  return jsonb_build_object('success',true,'already_verified',false,'cost',v_cost,'balance',v_balance,'badge','BLUE');
end
$$;
revoke all on function public.purchase_blink_blue_verification_with_coins() from public, anon;
grant execute on function public.purchase_blink_blue_verification_with_coins() to authenticated;

create or replace function public.create_blink_coin_purchase_order(p_pack_id text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_packs jsonb := '[]'::jsonb;
  v_pack jsonb;
  v_id uuid;
  v_price integer;
  v_coins integer;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  select coalesce(json_value,'[]'::jsonb) into v_packs
    from private.blink_economy_config where key='coin_packs';
  select value into v_pack
    from jsonb_array_elements(v_packs)
   where value->>'id'=trim(coalesce(p_pack_id,''))
   limit 1;
  if v_pack is null then raise exception 'INVALID_COIN_PACK'; end if;

  v_price := (v_pack->>'price_ngn')::integer;
  v_coins := (v_pack->>'coins')::integer;

  insert into public.blink_coin_purchase_orders(user_id,pack_id,amount_ngn,coin_amount,bonus_coins)
  values(v_user,v_pack->>'id',v_price,v_coins,greatest(0,v_coins-v_price))
  returning id into v_id;

  return jsonb_build_object(
    'success',true,'order_id',v_id,'pack_id',v_pack->>'id',
    'amount_ngn',v_price,'coins',v_coins,'bonus_coins',greatest(0,v_coins-v_price),
    'status','pending'
  );
end
$$;
revoke all on function public.create_blink_coin_purchase_order(text) from public, anon;
grant execute on function public.create_blink_coin_purchase_order(text) to authenticated;

create or replace function public.fulfill_blink_coin_purchase_order(
  p_order_id uuid,
  p_provider_reference text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_order public.blink_coin_purchase_orders%rowtype;
  v_balance bigint;
begin
  select * into v_order
    from public.blink_coin_purchase_orders
   where id=p_order_id for update;
  if not found then raise exception 'COIN_PURCHASE_ORDER_NOT_FOUND'; end if;

  if v_order.status='fulfilled' then
    select coalesce(floor(spendable_coin_balance),0)::bigint into v_balance
      from public.user_balances where user_id=v_order.user_id;
    return jsonb_build_object('success',true,'already_fulfilled',true,'balance',coalesce(v_balance,0));
  end if;
  if coalesce(trim(p_provider_reference),'')='' then raise exception 'PAYMENT_REFERENCE_REQUIRED'; end if;

  insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
  values(v_order.user_id,0,now()) on conflict(user_id) do nothing;

  update public.user_balances
     set spendable_coin_balance=spendable_coin_balance+v_order.coin_amount,updated_at=now()
   where user_id=v_order.user_id
  returning floor(spendable_coin_balance)::bigint into v_balance;

  perform set_config('blink.coin_sync_bypass','1',true);
  insert into public.game_profiles(user_id,coins,updated_at)
  values(v_order.user_id,v_balance,now())
  on conflict(user_id) do update set coins=excluded.coins,updated_at=now();
  perform set_config('blink.coin_sync_bypass','0',true);

  update public.blink_coin_purchase_orders
     set status='fulfilled',provider_reference=trim(p_provider_reference),
         paid_at=coalesce(paid_at,now()),fulfilled_at=now()
   where id=v_order.id;

  insert into public.blink_coin_transactions(user_id,kind,item_name,amount,balance_after,metadata)
  values(v_order.user_id,'COIN_PURCHASE','Blink Coin pack',v_order.coin_amount,v_balance,
    jsonb_build_object('order_id',v_order.id,'pack_id',v_order.pack_id,'amount_ngn',v_order.amount_ngn,'provider',v_order.provider,'provider_reference',trim(p_provider_reference)));

  return jsonb_build_object('success',true,'already_fulfilled',false,'coins',v_order.coin_amount,'balance',v_balance);
end
$$;
revoke all on function public.fulfill_blink_coin_purchase_order(uuid,text) from public, anon, authenticated;
grant execute on function public.fulfill_blink_coin_purchase_order(uuid,text) to service_role;
