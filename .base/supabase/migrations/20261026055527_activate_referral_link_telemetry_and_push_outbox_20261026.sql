-- Reconciles externally applied hosted migration 20261026055527.
-- 20261025153000 has the underlying link-telemetry and outbox DDL.
do $check$
begin
  if to_regclass('public.blink_referral_link_opens') is null
     or to_regclass('private.blink_referral_push_outbox') is null then
    raise exception 'BLINK_REFERRAL_LINK_OR_OUTBOX_MISSING';
  end if;
  if to_regprocedure('public.blink_record_referral_link_open(text,uuid)') is null
     or to_regprocedure('public.blink_claim_referral_pushes(integer)') is null
     or to_regprocedure('public.blink_finish_referral_push(uuid,boolean,text,integer)') is null then
    raise exception 'BLINK_REFERRAL_PUSH_RPCS_MISSING';
  end if;
  if has_function_privilege('authenticated',
       'public.blink_claim_referral_pushes(integer)','EXECUTE')
    or has_function_privilege('anon',
       'public.blink_finish_referral_push(uuid,boolean,text,integer)','EXECUTE')
    or has_table_privilege('authenticated',
       'private.blink_referral_push_outbox','SELECT') then
     raise exception 'BLINK_REFERRAL_OUTBOX_PUBLIC_LEAK';
  end if;
  if not exists(select 1 from pg_class where oid='private.blink_referral_push_outbox'::regclass and relrowsecurity) then
    raise exception 'BLINK_REFERRAL_OUTBOX_RLS_REQUIRED';
  end if;
end;
$check$;
