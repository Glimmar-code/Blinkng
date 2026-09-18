-- Blink rewarded ads: one authenticated claim earns exactly 10 Blink Coins.
-- The Android client opens a claim before showing an AdMob rewarded ad and completes it only
-- from the Google Mobile Ads OnUserEarnedReward callback. The claim id is also attached as
-- AdMob SSV custom data so server-side verification can be layered on without changing the app flow.

create table if not exists public.blink_rewarded_ad_claims (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  started_at timestamptz not null default now(),
  rewarded_at timestamptz,
  reward_amount integer not null default 10 check (reward_amount = 10),
  metadata jsonb not null default '{}'::jsonb
);

create index if not exists blink_rewarded_ad_claims_user_started_idx
  on public.blink_rewarded_ad_claims(user_id, started_at desc);

alter table public.blink_rewarded_ad_claims enable row level security;

-- Claims are mutated only through security-definer RPCs. Users never get direct table write access.
revoke all on public.blink_rewarded_ad_claims from anon, authenticated;

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
set search_path = public, pg_temp
as $$
declare
  v_user uuid := auth.uid();
  v_claim public.blink_rewarded_ad_claims%rowtype;
  v_balance bigint;
begin
  if v_user is null then
    raise exception 'AUTH_REQUIRED';
  end if;

  select *
    into v_claim
    from public.blink_rewarded_ad_claims
   where id = p_claim_id
     and user_id = v_user
   for update;

  if not found then
    raise exception 'REWARDED_AD_CLAIM_EXPIRED';
  end if;

  if v_claim.rewarded_at is not null then
    select coalesce(spendable_coin_balance, 0)
      into v_balance
      from public.user_balances
     where user_id = v_user;

    return jsonb_build_object(
      'success', true,
      'already_credited', true,
      'reward_amount', 10,
      'balance', coalesce(v_balance, 0)
    );
  end if;

  if v_claim.started_at < now() - interval '30 minutes' then
    raise exception 'REWARDED_AD_CLAIM_EXPIRED';
  end if;

  -- Prevent instant/manual claim calls while preserving normal rewarded-ad completion.
  if v_claim.started_at > now() - interval '5 seconds' then
    raise exception 'REWARDED_AD_TOO_SOON';
  end if;

  insert into public.user_balances(user_id, spendable_coin_balance)
  values(v_user, 0)
  on conflict(user_id) do nothing;

  update public.user_balances
     set spendable_coin_balance = spendable_coin_balance + 10,
         updated_at = now()
   where user_id = v_user
  returning spendable_coin_balance into v_balance;

  update public.blink_rewarded_ad_claims
     set rewarded_at = now()
   where id = v_claim.id;

  insert into public.blink_coin_transactions(
    user_id,
    kind,
    item_name,
    amount,
    balance_after,
    metadata
  )
  values(
    v_user,
    'REWARDED_AD',
    'Rewarded ad',
    10,
    v_balance,
    jsonb_build_object('claim_id', v_claim.id)
  );

  return jsonb_build_object(
    'success', true,
    'already_credited', false,
    'reward_amount', 10,
    'balance', v_balance
  );
end
$$;

revoke all on function public.begin_blink_rewarded_ad_claim() from public, anon;
revoke all on function public.complete_blink_rewarded_ad_claim(uuid) from public, anon;
grant execute on function public.begin_blink_rewarded_ad_claim() to authenticated;
grant execute on function public.complete_blink_rewarded_ad_claim(uuid) to authenticated;
