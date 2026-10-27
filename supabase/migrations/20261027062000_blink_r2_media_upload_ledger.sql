-- BLINK R2 media staging contract. DO NOT apply in production until
-- Cloudflare buckets, server-only secrets and staging read/write tests are ready.
-- Keeps all existing Supabase Storage objects, RLS and URLs unchanged.
create table if not exists public.blink_r2_uploads (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  purpose text not null check (purpose in ('feed','reel','profile','market','story','channel','chat')),
  context_id uuid,
  bucket text not null check (bucket in ('blink-public-media','blink-private-media','blink-temporary-media')),
  object_key text not null unique,
  mime_type text not null,
  bytes_expected bigint not null check (bytes_expected > 0 and bytes_expected <= 83886080),
  bytes_confirmed bigint,
  state text not null default 'pending' check (state in ('pending','ready','rejected')),
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  constraint blink_r2_uploads_purpose_bucket_check check (
    (purpose in ('feed','reel','profile','market') and bucket='blink-public-media') or
    (purpose='story' and bucket='blink-temporary-media') or
    (purpose in ('chat','channel') and bucket='blink-private-media')
  )
);
create index if not exists blink_r2_uploads_owner_created_idx
  on public.blink_r2_uploads (owner_id, created_at desc);
create index if not exists blink_r2_uploads_pending_created_idx
  on public.blink_r2_uploads (created_at) where state='pending';

-- No exposed Data API policies: only a verified, service-role Edge Function
-- may issue short-lived upload/download URLs. Being signed in alone is never
-- sufficient to browse private chat attachments.
alter table public.blink_r2_uploads enable row level security;
revoke all on table public.blink_r2_uploads from public, anon, authenticated;
grant select, insert, update, delete on table public.blink_r2_uploads to service_role;
comment on table public.blink_r2_uploads is
  'Internal Cloudflare R2 upload ledger; no direct anon/authenticated Data API access.';
