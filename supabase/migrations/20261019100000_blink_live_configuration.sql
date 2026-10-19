-- BLINK live configuration: public read-only presentation settings.
-- Rollback: disable affected rows; drop this table only when old clients no longer read it.
-- Client-readable content must never contain secrets or authorization/payment policy.
begin;

create table if not exists public.blink_live_config (
    key text primary key check (key in ('announcement', 'promotion', 'display', 'feature_visibility')),
    value jsonb not null default '{}'::jsonb,
    enabled boolean not null default false,
    updated_at timestamptz not null default now(),
    constraint blink_live_config_value_object check (jsonb_typeof(value) = 'object'),
    constraint blink_live_config_value_size check (octet_length(value::text) <= 4096)
);

alter table public.blink_live_config enable row level security;

-- No registered or unregistered app user can publish configuration.
revoke all on table public.blink_live_config from public, anon, authenticated;
grant select on table public.blink_live_config to anon, authenticated;

drop policy if exists blink_live_config_public_active_read on public.blink_live_config;
create policy blink_live_config_public_active_read
on public.blink_live_config for select to anon, authenticated
using (enabled is true);

-- Safe defaults: no promotional copy is shown until explicitly published by
-- a trusted database operator in staging and then production.
insert into public.blink_live_config (key, value, enabled) values
    ('announcement', '{"title":"","message":""}'::jsonb, false),
    ('promotion', '{"title":"","message":""}'::jsonb, false),
    ('display', '{"show_announcements":true,"show_promotions":true}'::jsonb, false),
    ('feature_visibility', '{"home_promo_enabled":true,"home_campaign_enabled":true,"reels_campaign_enabled":true,"marketplace_campaign_enabled":true,"connect_campaign_enabled":true,"messages_campaign_enabled":true,"games_campaign_enabled":true}'::jsonb, false)
on conflict (key) do nothing;

comment on table public.blink_live_config is
'Read-only client presentation configuration. Only trusted backend operators may edit. Never use to authorize payments, verified status, posting or moderation.';
commit;
