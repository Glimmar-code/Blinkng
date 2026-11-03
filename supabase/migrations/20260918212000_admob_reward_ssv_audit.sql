-- AdMob rewarded SSV audit metadata.
-- destructive-change-reviewed: additive-only columns/index/function; no existing data is removed.
-- rollback-plan: revoke/drop record_blink_rewarded_ad_ssv, drop the SSV unique index, then drop the added SSV columns.

alter table public.blink_rewarded_ad_claims
  add column if not exists ssv_verified_at timestamptz,
  add column if not exists ssv_transaction_id text,
  add column if not exists ssv_ad_unit text,
  add column if not exists ssv_reward_amount integer,
  add column if not exists ssv_key_id text;

create unique index if not exists blink_rewarded_ad_claims_ssv_transaction_uidx
  on public.blink_rewarded_ad_claims(ssv_transaction_id)
  where ssv_transaction_id is not null;

create or replace function public.record_blink_rewarded_ad_ssv(
  p_claim_id uuid,
  p_user_id uuid,
  p_transaction_id text,
  p_ad_unit text,
  p_reward_amount integer,
  p_key_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_claim public.blink_rewarded_ad_claims%rowtype;
begin
  if p_claim_id is null
     or p_user_id is null
     or coalesce(trim(p_transaction_id), '') = ''
     or coalesce(trim(p_ad_unit), '') = ''
     or p_reward_amount is distinct from 10 then
    raise exception 'INVALID_ADMOB_SSV_PAYLOAD';
  end if;

  -- AdMob currently reports either the numeric ad-unit suffix or the full ad-unit id.
  if p_ad_unit not in (
    '4343111201',
    'ca-app-pub-9152580730716304/4343111201'
  ) then
    raise exception 'INVALID_ADMOB_AD_UNIT';
  end if;

  select *
    into v_claim
    from public.blink_rewarded_ad_claims
   where id = p_claim_id
     and user_id = p_user_id
   for update;

  if not found then
    raise exception 'REWARDED_AD_CLAIM_NOT_FOUND';
  end if;

  if v_claim.ssv_transaction_id is not null then
    if v_claim.ssv_transaction_id = p_transaction_id then
      return jsonb_build_object(
        'success', true,
        'already_verified', true,
        'claim_id', v_claim.id
      );
    end if;
    raise exception 'REWARDED_AD_CLAIM_ALREADY_VERIFIED';
  end if;

  if exists (
    select 1
      from public.blink_rewarded_ad_claims
     where ssv_transaction_id = p_transaction_id
       and id <> p_claim_id
  ) then
    raise exception 'REWARDED_AD_TRANSACTION_REPLAY';
  end if;

  update public.blink_rewarded_ad_claims
     set ssv_verified_at = now(),
         ssv_transaction_id = p_transaction_id,
         ssv_ad_unit = p_ad_unit,
         ssv_reward_amount = p_reward_amount,
         ssv_key_id = nullif(trim(p_key_id), ''),
         metadata = coalesce(metadata, '{}'::jsonb) ||
           jsonb_build_object(
             'ssv_verified', true,
             'ssv_verified_at', now()
           )
   where id = p_claim_id;

  return jsonb_build_object(
    'success', true,
    'already_verified', false,
    'claim_id', p_claim_id
  );
end
$$;

revoke all on function public.record_blink_rewarded_ad_ssv(uuid, uuid, text, text, integer, text)
  from public, anon, authenticated;
grant execute on function public.record_blink_rewarded_ad_ssv(uuid, uuid, text, text, integer, text)
  to service_role;
