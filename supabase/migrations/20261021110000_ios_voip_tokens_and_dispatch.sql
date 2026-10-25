-- BLINK iOS Phase 9: STAGING-ONLY migration, do not deploy to prod
-- without Apple signing + APNs secrets + physical-device tests.
-- Purposefully isolate sensitive push tokens from authenticated/anon API.
create table if not exists public.ios_voip_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  device_id uuid not null,
  bundle_id text not null,
  environment text not null check (environment in ('sandbox','production')),
  token text not null check (token ~ '^[0-9a-f]{64,512}$'),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  last_registered_at timestamptz not null default now(),
  revoked_at timestamptz,
  constraint ios_voip_tokens_bundle_format check (
    bundle_id ~ '^[A-Za-z0-9][A-Za-z0-9.-]{2,120}$'
  ),
  constraint ios_voip_tokens_device_unique
    unique(device_id,bundle_id,environment),
  constraint ios_voip_tokens_raw_token_unique unique(bundle_id,environment,token)
);
create index if not exists ios_voip_tokens_user_active_idx
  on public.ios_voip_tokens(user_id, is_active, environment, updated_at desc);
create index if not exists ios_voip_tokens_expiry_idx
  on public.ios_voip_tokens(last_registered_at) where is_active;

alter table public.ios_voip_tokens enable row level security;
revoke all on public.ios_voip_tokens from PUBLIC, anon, authenticated;
grant select, insert, update, delete on public.ios_voip_tokens to service_role;

-- Idempotency is PER CALL AND DEVICE, not per caller. Never use this log to
-- infer that APNs was actually displayed or answered by the user.
create table if not exists public.ios_voip_dispatches (
  call_id uuid not null references public.calls(id) on delete cascade,
  token_id uuid not null references public.ios_voip_tokens(id) on delete cascade,
  status text not null check (status in ('claimed','sent','failed')),
  attempts integer not null default 1 check (attempts between 0 and 100),
  last_error_code text,
  updated_at timestamptz not null default now(),
  primary key (call_id, token_id)
);
create index if not exists ios_voip_dispatches_status_idx
  on public.ios_voip_dispatches(status, updated_at desc);
alter table public.ios_voip_dispatches enable row level security;
revoke all on public.ios_voip_dispatches from PUBLIC, anon, authenticated;
grant select, insert, update, delete on public.ios_voip_dispatches to service_role;

-- No client RLS policies: only server Edge Functions can read or write
-- unredacted device tokens. RLS is enabled in exposed public schema.
comment on table public.ios_voip_tokens is
  'Server-only APNs VoIP device registrations. Do not expose to Data API user roles.';
comment on table public.ios_voip_dispatches is
  'Server-only dedupe for real ringing calls sent through Apple APNs.';
