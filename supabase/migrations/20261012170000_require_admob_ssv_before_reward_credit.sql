-- Require verified AdMob server-side verification before paying a rewarded ad.
-- STAGING GATE: DO NOT DEPLOY to production until AdMob's actual signed callbacks
-- are confirmed to reach admob-reward-ssv with reward_amount=10 and matching ad unit.
-- Existing previously credited claims remain idempotent, even without historical SSV.
-- destructive-change-reviewed: safely replaces two claim RPCs; no user balances altered.
-- rollback-plan: restore the prior begin/complete RPC definitions from
-- 20260918193000/20260919190000 (temporarily loses SSV enforcement; do not auto-rollback).

create or replace function public.begin_blink_rewarded_ad_claim()
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user uuid := auth.uid();
  v_claim uuid;
begin
  if v_user is null then
    raise exception 'AUTH_REQUIRED';
  end if;

  -- Clean up abandoned claims so retries do not leave unbounded rows.
  delete from public.blink_rewarded_ad_claims
   where user_id = v_user
     and rewarded_at is null
     and ssv_verified_at is null
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

  -- Verified SSV callbacks are cryptographically signed and replay-checked. Their
  -- credit may be reconciled after the app was closed: never expire a verified claim.
  if v_claim.started_at < now()-interval '30 minutes'
     and v_claim.ssv_verified_at is null then
    raise exception 'REWARDED_AD_CLAIM_EXPIRED';
  end if;
  if v_claim.started_at > now()-interval '5 seconds' then raise exception 'REWARDED_AD_TOO_SOON'; end if;

  -- Never mint wallet balance from an unverified client-side reward callback.
  -- The service-role-only SSV recorder sets these columns after validating
  -- Google's signature, unique transaction ID, reward amount and ad unit.
  if v_claim.ssv_verified_at is null
     or v_claim.ssv_transaction_id is null
     or v_claim.ssv_reward_amount is distinct from 10
     or v_claim.ssv_ad_unit not in (
       '4343111201',
       'ca-app-pub-9152580730716304/4343111201'
     ) then
    raise exception 'REWARDED_AD_VERIFICATION_PENDING';
  end if;

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
