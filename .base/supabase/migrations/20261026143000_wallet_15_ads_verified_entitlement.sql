-- BLINK final wallet verification eligibility — original economy and HP/XP algorithms unchanged.
-- Verified accounts can view rewarded ads 11–15; unverified accounts stop at 10.
-- Retroactive Coin payouts and valid past transfers remain unaffected.
-- destructive-change-reviewed
-- rollback-plan: restore begin_blink_rewarded_ad_claim,
-- complete_blink_rewarded_ad_claim and get_blink_economy_status
-- from migration 20260919190000_blink_coin_economy_v1.sql and
-- 20260918193000_rewarded_ad_blink_coins.sql;
-- drop helper only after restored functions no longer reference it.
-- Preserve all reward credits, XP/HP progression and Coin gift transfers.
-- The 15-ad tier is for actively verified users as requested.
-- This read-only entitlement helper has no balance-changing side effects.
create or replace function private.blink_active_rewarded_ad_limit(p_user uuid,p_configured_limit integer)
returns integer language sql stable security definer set search_path=''
as $$
  select case when exists (
    select 1 from public.profiles p where p.id=p_user
      and (
        (upper(coalesce(p.verification_badge,''))='BLUE'
           and p.verification_expires_at > now())
        or (upper(coalesce(p.verification_badge,''))='GOLD'
           and (p.verification_expires_at > now() or p.verification_expires_at is null))
      )
  ) then greatest(1,least(coalesce(p_configured_limit,15),15))
    else greatest(1,least(coalesce(p_configured_limit,15),10)) end;
$$;
revoke all on function private.blink_active_rewarded_ad_limit(uuid,integer) from public,anon,authenticated;

CREATE OR REPLACE FUNCTION public.begin_blink_rewarded_ad_claim()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_user uuid := auth.uid();
  v_claim uuid;
  v_limit integer;
  v_ads_today integer;
begin
  if v_user is null then
    raise exception 'AUTH_REQUIRED';
  end if;

  select coalesce(int_value,15)::integer into v_limit
    from private.blink_economy_config where key='rewarded_ad_daily_limit';
  v_limit := private.blink_active_rewarded_ad_limit(v_user, v_limit);
  -- Serialize admission with the existing server-side completion per-user lock.
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text)::bigint);
  select count(*)::integer into v_ads_today
    from public.blink_rewarded_ad_claims
   where user_id=v_user
     and rewarded_at >= date_trunc('day',now() at time zone 'UTC') at time zone 'UTC';
  if v_ads_today >= v_limit then raise exception 'REWARDED_AD_DAILY_LIMIT'; end if;

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
$function$;

CREATE OR REPLACE FUNCTION public.complete_blink_rewarded_ad_claim(p_claim_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
  v_limit := private.blink_active_rewarded_ad_limit(v_user, v_limit);
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
$function$;

CREATE OR REPLACE FUNCTION public.get_blink_economy_status()
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
  v_limit := private.blink_active_rewarded_ad_limit(v_user, v_limit);
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
$function$;

