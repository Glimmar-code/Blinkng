-- destructive-change-reviewed
-- TESTLAB ONLY: BLINK Wallet five-page server contract.
-- Requires disposable PostgreSQL/Supabase staging before production.
-- rollback-plan: restore the three rewarded-ad RPCs from their original migrations;
-- revoke/drop public.gift_blink_hp_once, remove HP-only tables/rule. Do not restore
-- peer-to-peer Coin gifting unless the owner explicitly reverses that decision.
-- No balance backfill and no client-created credits.

-- Reject any new peer-to-peer BLINK Coin transfers. Existing purchases/ledger
-- history and already paid orders are kept intact.
-- Some installations have not deployed newer gift endpoints.
-- Conditional revocations keep the migration compatible and prevent new gifts.
do $coin_gifts$
begin
  if to_regprocedure('public.gift_blink_coins(uuid,bigint)') is not null then
    revoke execute on function public.gift_blink_coins(uuid,bigint)
      from public, anon, authenticated;
  end if;
  if to_regprocedure('public.gift_blink_coins_once(uuid,bigint,uuid)') is not null then
    revoke execute on function public.gift_blink_coins_once(uuid,bigint,uuid)
      from public, anon, authenticated, service_role;
  end if;
  -- Existing paid orders remain eligible for lawful fulfilment/refunds.
  if to_regprocedure('public.create_blink_paystack_gift_order(text,text,text)') is not null then
    revoke execute on function public.create_blink_paystack_gift_order(text,text,text)
      from public, anon, authenticated;
  end if;
end
$coin_gifts$;

-- Gift HP = recipient XP level progress. Never spend or transfer Coin balances.
insert into public.xp_rules(event_type,xp_amount,repeatable,daily_event_limit,enabled,description)
values('hp_gift',20,false,null,true,'Server-limited gifted HP toward recipient XP level')
on conflict (event_type) do update
set xp_amount=20,repeatable=false,daily_event_limit=null,enabled=true,
    description=excluded.description,updated_at=now();

create table if not exists public.blink_hp_gifts (
    id uuid primary key,
    sender_id uuid not null references public.profiles(id) on delete cascade,
    receiver_id uuid not null references public.profiles(id) on delete cascade,
    amount integer not null check (amount in (20,50,100)),
    created_at timestamptz not null default now(),
    check (sender_id <> receiver_id)
);
create index if not exists blink_hp_gifts_sender_day
  on public.blink_hp_gifts(sender_id,created_at desc);
create index if not exists blink_hp_gifts_receiver_day
  on public.blink_hp_gifts(receiver_id,created_at desc);
alter table public.blink_hp_gifts enable row level security;
revoke all on public.blink_hp_gifts from anon, authenticated;
grant select on public.blink_hp_gifts to authenticated;
grant select on public.blink_hp_gifts to service_role;
drop policy if exists blink_hp_gifts_my_history on public.blink_hp_gifts;
create policy blink_hp_gifts_my_history
on public.blink_hp_gifts for select to authenticated
using (sender_id = auth.uid() or receiver_id = auth.uid());

create or replace function public.gift_blink_hp_once(
    p_receiver_id uuid, p_amount integer, p_request_id uuid)
returns jsonb language plpgsql security definer set search_path = ''
as $$
declare
    v_sender uuid := auth.uid();
    v_existing public.blink_hp_gifts%rowtype;
    v_first_lock bigint;
    v_second_lock bigint;
    v_sent integer;
    v_received integer;
    v_awarded integer;
    v_level integer;
begin
    if v_sender is null then raise exception 'AUTH_REQUIRED'; end if;
    if p_receiver_id is null or p_receiver_id=v_sender or p_request_id is null
       or p_amount not in (20,50,100) then raise exception 'INVALID_HP_GIFT'; end if;

    -- Lock both participants in deterministic order to avoid reciprocal-gift deadlocks
    -- and concurrent daily-cap bypasses from multiple senders.
    v_first_lock := least(pg_catalog.hashtext(v_sender::text)::bigint,
                          pg_catalog.hashtext(p_receiver_id::text)::bigint);
    v_second_lock := greatest(pg_catalog.hashtext(v_sender::text)::bigint,
                              pg_catalog.hashtext(p_receiver_id::text)::bigint);
    perform pg_catalog.pg_advisory_xact_lock(v_first_lock);
    if v_second_lock <> v_first_lock then
        perform pg_catalog.pg_advisory_xact_lock(v_second_lock);
    end if;
    select * into v_existing from public.blink_hp_gifts where id=p_request_id;
    if found then
        if v_existing.sender_id is distinct from v_sender
           or v_existing.receiver_id is distinct from p_receiver_id
           or v_existing.amount is distinct from p_amount then
            raise exception 'HP_RECEIPT_CONFLICT';
        end if;
        return jsonb_build_object('success',true,'already_gifted',true,'awarded_hp',0);
    end if;

    if not exists (select 1 from public.profiles where id=p_receiver_id) then
        raise exception 'PROFILE_NOT_FOUND';
    end if;
    select coalesce(sum(amount),0)::integer into v_sent
    from public.blink_hp_gifts
    where sender_id=v_sender
      and created_at >= date_trunc('day',now() at time zone 'UTC') at time zone 'UTC';
    if v_sent + p_amount > 100 then raise exception 'DAILY_HP_GIFT_LIMIT'; end if;

    -- Both sender/recipient were locked above; caps remain atomic.
    select coalesce(sum(amount),0)::integer into v_received
    from public.blink_hp_gifts
    where receiver_id=p_receiver_id
      and created_at >= date_trunc('day',now() at time zone 'UTC') at time zone 'UTC';
    if v_received + p_amount > 300 then raise exception 'RECEIVER_HP_GIFT_LIMIT'; end if;

    insert into public.blink_hp_gifts(id,sender_id,receiver_id,amount)
    values(p_request_id,v_sender,p_receiver_id,p_amount);
    v_awarded := private.award_xp(p_receiver_id,'hp_gift','hp_gift:'||p_request_id::text,
                                 'profile_gift',v_sender::text,p_amount);
    if v_awarded <> p_amount then raise exception 'HP_LEVEL_CREDIT_FAILED'; end if;
    select xp_level into v_level from public.profiles where id=p_receiver_id;
    return jsonb_build_object('success',true,'already_gifted',false,
        'awarded_hp',v_awarded,'receiver_level',v_level);
end
$$;
revoke all on function public.gift_blink_hp_once(uuid,integer,uuid) from public,anon;
grant execute on function public.gift_blink_hp_once(uuid,integer,uuid) to authenticated;

-- Server entitlement checks run at ad START and again during credit, so an
-- unverified client cannot complete ad #11-15 with an old APK or raw RPC call.
create or replace function public.begin_blink_rewarded_ad_claim()
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user uuid := auth.uid();
  v_claim uuid;
  v_limit integer := 15;
  v_count integer;
begin
  if v_user is null then
    raise exception 'AUTH_REQUIRED';
  end if;

  select coalesce(int_value,15)::integer into v_limit
    from private.blink_economy_config where key='rewarded_ad_daily_limit';

  select case when p.is_verified is true
       and upper(coalesce(p.verification_badge::text,'NONE')) in ('BLUE','GOLD')
       and (p.verification_expires_at is null or p.verification_expires_at > now())
     then greatest(1,v_limit) else least(10,greatest(1,v_limit)) end
    into v_limit
    from public.profiles p where p.id=v_user;
  v_limit := coalesce(v_limit,10);

  select count(*)::integer into v_count
    from public.blink_rewarded_ad_claims
   where user_id=v_user
     and rewarded_at >= date_trunc('day',now() at time zone 'UTC') at time zone 'UTC';
  if v_count >= v_limit then raise exception 'REWARDED_AD_DAILY_LIMIT'; end if;

  -- Clean up abandoned claims so retries do not leave unbounded rows.
  delete from public.blink_rewarded_ad_claims
   where user_id = v_user
     and rewarded_at is null
     and started_at < now() - interval '30 minutes';

  insert into public.blink_rewarded_ad_claims(user_id)
  values(v_user)
  returning id into v_claim;

  return jsonb_build_object(
    'success', true,
    'claim_id', v_claim,
    'reward_amount', 10
  );
end
$$;


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
  v_valid_days integer;
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

  select case when p.is_verified is true
       and upper(coalesce(p.verification_badge::text,'NONE')) in ('BLUE','GOLD')
       and (p.verification_expires_at is null or p.verification_expires_at > now())
     then greatest(1,v_limit) else least(10,greatest(1,v_limit)) end
    into v_limit
    from public.profiles p where p.id=v_user;
  v_limit := coalesce(v_limit,10);

  select coalesce(int_value,800)::integer into v_cash
    from private.blink_economy_config where key='blue_verification_cash_ngn';
  select coalesce(int_value,3000)::integer into v_verify_coins
    from private.blink_economy_config where key='blue_verification_coin_cost';
  select coalesce(int_value,30)::integer into v_valid_days
    from private.blink_economy_config where key='blue_verification_valid_days';
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
    'blue_verification_valid_days',coalesce(v_valid_days,30),
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

  select case when p.is_verified is true
       and upper(coalesce(p.verification_badge::text,'NONE')) in ('BLUE','GOLD')
       and (p.verification_expires_at is null or p.verification_expires_at > now())
     then greatest(1,v_limit) else least(10,greatest(1,v_limit)) end
    into v_limit
    from public.profiles p where p.id=v_user;
  v_limit := coalesce(v_limit,10);

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

