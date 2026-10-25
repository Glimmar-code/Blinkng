-- Staged-only referral reliability. Requires the base referral migration.
-- Link opens are UNTRUSTED engagement estimates; NEVER award coins or visibility for them.
-- Email-confirmed, one-account-one-referral events remain the source of truth.

create table if not exists public.blink_referral_link_opens (
  referrer_id uuid not null references public.profiles(id) on delete cascade,
  visitor_key uuid not null,
  opened_day date not null default (now() at time zone 'utc')::date,
  created_at timestamptz not null default now(),
  primary key (referrer_id, visitor_key, opened_day)
);
create index if not exists blink_referral_link_opens_owner_day
  on public.blink_referral_link_opens(referrer_id, opened_day desc);
alter table public.blink_referral_link_opens enable row level security;
revoke all on public.blink_referral_link_opens from public, anon, authenticated, service_role;
grant select on public.blink_referral_link_opens to service_role;
-- No client can directly insert rows or read visitor keys.

create or replace function public.blink_record_referral_link_open(
  p_username text, p_visitor uuid
) returns boolean language plpgsql security definer set search_path = ''
as $$
declare
  target uuid;
  clean text := lower(trim(leading '@' from coalesce(p_username,'')));
begin
  if p_visitor is null or length(clean) < 2 or length(clean) > 30
     or clean !~ '^[a-z0-9][a-z0-9._-]+$' then
    return false;
  end if;
  select p.id into target
    from public.profiles p
   where lower(p.username) = clean and p.onboarding_completed is true
     and not exists (
       select 1 from public.user_settings s
       where s.user_id = p.id and s.private_account is true
     )
   limit 1;
  if target is null or target = (select auth.uid()) then return false; end if;
  -- At most 30 distinct referrer links per anonymous visitor per UTC day.
  -- This is only a rough count; an attacker can clear their browser storage.
  if (select count(*) from public.blink_referral_link_opens e
       where e.visitor_key=p_visitor and e.opened_day=(now() at time zone 'utc')::date) >= 30
  then return false; end if;
  insert into public.blink_referral_link_opens(referrer_id,visitor_key)
  values(target,p_visitor) on conflict do nothing;
  return found;
end $$;
revoke all on function public.blink_record_referral_link_open(text,uuid) from public,anon,authenticated;
grant execute on function public.blink_record_referral_link_open(text,uuid) to anon,authenticated;

create or replace function public.blink_referral_link_stats()
returns table(unique_link_opens_30d bigint, confirmed_referrals_30d bigint)
language sql stable security definer set search_path=''
as $$
  select (select count(*)::bigint from public.blink_referral_link_opens e
          where e.referrer_id = (select auth.uid())
            and e.opened_day >= ((now() at time zone 'utc')::date-29)),
         (select count(*)::bigint from public.blink_referrals r
          where r.referrer_user_id = (select auth.uid()) and r.status='confirmed'
            and r.confirmed_at > now()-interval '30 days')
   where (select auth.uid()) is not null;
$$;
revoke all on function public.blink_referral_link_stats() from public,anon,authenticated;
grant execute on function public.blink_referral_link_stats() to authenticated;

-- Referral notification outbox: independent of the referred member reopening BLINK.
-- A trusted server worker must claim, send, and acknowledge batches.
create table if not exists private.blink_referral_push_outbox (
  notification_id uuid primary key references public.notifications(id) on delete cascade,
  state text not null default 'pending'
    check(state in ('pending','sending','sent','failed')),
  attempts integer not null default 0 check(attempts between 0 and 12),
  next_attempt_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  last_error text
);
alter table private.blink_referral_push_outbox enable row level security;
revoke all on private.blink_referral_push_outbox from public,anon,authenticated,service_role;
grant select,insert,update on private.blink_referral_push_outbox to service_role;

create or replace function private.blink_enqueue_referral_push()
returns trigger language plpgsql security definer set search_path=''
as $$
begin
  if new.metadata->>'kind'='blink_referral_confirmed' and new.actor_id is not null
     and exists (
       select 1 from public.blink_referrals r
       where r.referred_user_id=new.actor_id
         and r.referrer_user_id=new.user_id
         and r.status='confirmed'
     ) then
    insert into private.blink_referral_push_outbox(notification_id)
    values(new.id) on conflict do nothing;
  end if;
  return new;
end $$;
revoke all on function private.blink_enqueue_referral_push() from public,anon,authenticated;
drop trigger if exists zz_blink_referral_push_outbox on public.notifications;
create trigger zz_blink_referral_push_outbox after insert on public.notifications
for each row execute function private.blink_enqueue_referral_push();
-- Queue notifications already written by earlier trigger activity.
insert into private.blink_referral_push_outbox(notification_id)
select n.id from public.notifications n join public.blink_referrals r
  on r.referred_user_id=n.actor_id and r.referrer_user_id=n.user_id
where n.metadata->>'kind'='blink_referral_confirmed' and r.status='confirmed'
on conflict do nothing;

create or replace function public.blink_claim_referral_pushes(p_limit integer default 10)
returns table(notification_id uuid,attempt_count integer)
language sql volatile security definer set search_path=''
as $$
  with picked as (
    select o.notification_id from private.blink_referral_push_outbox o
     where (o.state in ('pending','failed') or
           (o.state='sending' and o.updated_at < now()-interval '3 minutes'))
       and o.attempts<8 and o.next_attempt_at<=now()
     order by o.next_attempt_at,o.notification_id
     limit greatest(1,least(coalesce(p_limit,10),20))
     for update skip locked
  ), claimed as (
    update private.blink_referral_push_outbox o
      set state='sending', attempts=o.attempts+1,updated_at=now()
      from picked p where o.notification_id=p.notification_id
      returning o.notification_id,o.attempts
  )
  select notification_id,attempts as attempt_count from claimed;
$$;
revoke all on function public.blink_claim_referral_pushes(integer) from public,anon,authenticated;
grant execute on function public.blink_claim_referral_pushes(integer) to service_role;

create or replace function public.blink_finish_referral_push(
  p_notification_id uuid,p_succeeded boolean,p_error text default null
) returns boolean language plpgsql security definer set search_path=''
as $$
begin
  update private.blink_referral_push_outbox o
    set state=case when p_succeeded then 'sent' else 'failed' end,
        updated_at=now(),
        next_attempt_at=case when p_succeeded then now()
          else now() + make_interval(secs => least(3600,30*power(2,least(o.attempts,7)))::integer) end,
        last_error=case when p_succeeded then null else left(coalesce(p_error,'Push failed'),400) end
  where o.notification_id=p_notification_id and o.state='sending';
  return found;
end $$;
revoke all on function public.blink_finish_referral_push(uuid,boolean,text) from public,anon,authenticated;
grant execute on function public.blink_finish_referral_push(uuid,boolean,text) to service_role;

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
