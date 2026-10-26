begin;
set local lock_timeout='5s';
set local statement_timeout='45s';
do $before$
begin
 if to_regclass('public.blink_referrals') is null then
   raise exception 'BLINK_REFERRAL_SOURCE_MISSING'; end if;
end;
$before$;
-- Risk flags are server-only audit signals. Not used to falsely inflate or erase referrals.
create table if not exists private.blink_referral_risk_reviews (
  referred_user_id uuid primary key references auth.users(id) on delete cascade,
  referrer_user_id uuid not null references auth.users(id) on delete cascade,
  reason text not null,
  created_at timestamptz not null default now(),
  resolved_at timestamptz
);
alter table private.blink_referral_risk_reviews enable row level security;
revoke all on private.blink_referral_risk_reviews from public,anon,authenticated,service_role;
grant select on private.blink_referral_risk_reviews to service_role;

create or replace function private.blink_flag_referral_velocity()
returns trigger language plpgsql security definer set search_path=''
as $$
declare
  burst integer;
begin
  if new.status='confirmed' and (tg_op='INSERT' or old.status is distinct from 'confirmed') then
    select count(*) into burst from public.blink_referrals r
     where r.referrer_user_id=new.referrer_user_id
       and r.status='confirmed' and r.confirmed_at>now()-interval '1 hour';
    if burst >= 5 then
      insert into private.blink_referral_risk_reviews(referred_user_id,referrer_user_id,reason)
      values(new.referred_user_id,new.referrer_user_id,'unusual_referral_velocity')
      on conflict(referred_user_id) do nothing;
    end if;
  end if;
  return new;
end $$;
revoke all on function private.blink_flag_referral_velocity() from public,anon,authenticated;
drop trigger if exists zz_blink_review_referral_velocity on public.blink_referrals;
create trigger zz_blink_review_referral_velocity
  after insert or update of status on public.blink_referrals
  for each row execute function private.blink_flag_referral_velocity();

do $after$
begin
 if not exists(select 1 from pg_trigger
     where tgrelid='public.blink_referrals'::regclass
       and tgname='zz_blink_review_referral_velocity'
       and tgenabled<>'D') then
   raise exception 'BLINK_REFERRAL_VELOCITY_GUARD_NOT_ACTIVE'; end if;
 if has_table_privilege('anon','private.blink_referral_risk_reviews','SELECT')
    or has_table_privilege('authenticated','private.blink_referral_risk_reviews','INSERT')
    or has_function_privilege('authenticated','private.blink_flag_referral_velocity()','EXECUTE') then
   raise exception 'BLINK_REFERRAL_RISK_REVIEW_LEAK'; end if;
end;
$after$;
commit;