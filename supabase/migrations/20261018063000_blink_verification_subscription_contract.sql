-- Feature 7: pricing + subscription ledger staging contract.
-- No charging, badge activation or automatic renewal is enabled by this migration.
begin;

create table if not exists public.blink_verification_subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  tier text not null check (tier in ('BLUE','GOLD')),
  months integer not null check (months in (1,3,6,12)),
  amount_kobo bigint not null check (amount_kobo > 0),
  currency text not null default 'NGN' check (currency = 'NGN'),
  status text not null default 'pending' check (status in ('pending','active','paused','cancelled','expired','payment_failed')),
  starts_at timestamptz,
  ends_at timestamptz,
  auto_renew boolean not null default false,
  recurring_consent_at timestamptz,
  recurring_consent_version text,
  provider text,
  provider_reference text unique,
  next_renewal_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint blink_subscription_recurring_consent check (
    not auto_renew or (recurring_consent_at is not null and provider is not null)
  )
);

create index if not exists idx_blink_verification_subscription_user
  on public.blink_verification_subscriptions(user_id, created_at desc);

create table if not exists public.blink_verification_subscription_events (
  id uuid primary key default gen_random_uuid(),
  subscription_id uuid not null references public.blink_verification_subscriptions(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  event_type text not null,
  amount_kobo bigint,
  occurred_at timestamptz not null default now(),
  provider_reference text,
  unique(subscription_id, event_type, provider_reference)
);

alter table public.blink_verification_subscriptions enable row level security;
alter table public.blink_verification_subscription_events enable row level security;

-- Service-only writes. Clients cannot create a fake paid ledger record or
-- grant themselves badges by editing subscription status.
revoke all on public.blink_verification_subscriptions from public, anon, authenticated;
revoke all on public.blink_verification_subscription_events from public, anon, authenticated;

create or replace function public.blink_verification_plan_quote(
  p_tier text, p_months integer
) returns jsonb language plpgsql stable security invoker
set search_path = public, pg_temp as $$
declare
  v_monthly integer;
  v_discount integer;
  v_subtotal integer;
  v_total integer;
begin
  v_monthly := case upper(coalesce(p_tier,'')) when 'FREE' then 0
     when 'BLUE' then 800 when 'GOLD' then 2000 else null end;
  v_discount := case p_months when 1 then 0 when 3 then 10
     when 6 then 20 when 12 then 30 else null end;
  if v_monthly is null or v_discount is null then
    raise exception 'INVALID_VERIFICATION_PLAN';
  end if;
  v_subtotal := v_monthly * p_months;
  v_total := v_subtotal - (v_subtotal * v_discount / 100);
  return jsonb_build_object(
    'tier', upper(p_tier), 'months', p_months, 'currency', 'NGN',
    'total_ngn', v_total, 'total_kobo', v_total * 100,
    'savings_ngn', v_subtotal - v_total,
    'discount_percent', v_discount,
    'cash_checkout_ready', false, 'auto_renew_available', false
  );
end; $$;

create or replace function public.my_blink_verification_subscription_status()
returns jsonb language plpgsql stable security definer
set search_path = public, pg_temp as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  return coalesce((
    select jsonb_build_object(
      'id', s.id, 'tier', s.tier, 'months', s.months,
      'status', s.status, 'starts_at', s.starts_at, 'ends_at', s.ends_at,
      'auto_renew', s.auto_renew, 'next_renewal_at', s.next_renewal_at,
      'amount_ngn', s.amount_kobo / 100
    )
    from public.blink_verification_subscriptions s
    where s.user_id = v_uid order by s.created_at desc limit 1
  ), '{"status":"none","auto_renew":false}'::jsonb);
end; $$;

-- OFF is always immediately available. This cannot be used to enable billing:
-- enabling requires a separately consented provider mandate and signed webhook.
create or replace function public.cancel_my_blink_verification_auto_renew()
returns boolean language plpgsql security definer
set search_path = public, pg_temp as $$
declare v_uid uuid := auth.uid(); v_count integer;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  update public.blink_verification_subscriptions
    set auto_renew = false, next_renewal_at = null, updated_at = now()
  where user_id = v_uid and auto_renew = true;
  get diagnostics v_count = row_count;
  return v_count > 0;
end; $$;

revoke all on function public.blink_verification_plan_quote(text, integer) from public, anon;
revoke all on function public.my_blink_verification_subscription_status() from public, anon;
revoke all on function public.cancel_my_blink_verification_auto_renew() from public, anon;
grant execute on function public.blink_verification_plan_quote(text, integer) to authenticated;
grant execute on function public.my_blink_verification_subscription_status() to authenticated;
grant execute on function public.cancel_my_blink_verification_auto_renew() to authenticated;

commit;
