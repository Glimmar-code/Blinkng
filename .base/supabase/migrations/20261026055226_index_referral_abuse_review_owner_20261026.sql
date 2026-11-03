-- Reconciles live production migration 20261026055226.
begin;
set local lock_timeout='5s';
set local statement_timeout='20s';
create index if not exists blink_referral_risk_reviews_referrer_idx
  on private.blink_referral_risk_reviews(referrer_user_id);
do $verify$
begin
 if not exists(select 1 from pg_indexes
   where schemaname='private'
     and tablename='blink_referral_risk_reviews'
     and indexname='blink_referral_risk_reviews_referrer_idx') then
   raise exception 'BLINK_REFERRAL_RISK_FK_INDEX_MISSING';
 end if;
end;
$verify$;
commit;