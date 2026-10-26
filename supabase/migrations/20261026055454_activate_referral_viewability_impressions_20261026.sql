-- Reconciles externally applied hosted migration 20261026055454.
-- The original 20261025201000 staged source created viewability tracking.
do $check$
begin
  if to_regclass('public.blink_referral_discovery_impressions') is null then
    raise exception 'BLINK_REFERRAL_IMPRESSION_TABLE_MISSING';
  end if;
  if not exists(select 1 from pg_class where oid='public.blink_referral_discovery_impressions'::regclass and relrowsecurity) then
    raise exception 'BLINK_REFERRAL_IMPRESSION_RLS_REQUIRED';
  end if;
  if to_regprocedure('public.blink_record_referral_impression(uuid,text)') is null
    or to_regprocedure('public.blink_referral_impression_stats()') is null then
    raise exception 'BLINK_REFERRAL_IMPRESSION_RPCS_MISSING';
  end if;
  if has_table_privilege('authenticated',
     'public.blink_referral_discovery_impressions','INSERT') then
    raise exception 'BLINK_REFERRAL_IMPRESSIONS_DIRECT_WRITE_FORBIDDEN';
  end if;
end;
$check$;
