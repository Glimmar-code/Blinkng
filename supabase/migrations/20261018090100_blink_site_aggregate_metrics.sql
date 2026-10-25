-- BLINK Phase 3 Step 5: privacy-minimised public site aggregate analytics.
-- No IP, device, visitor ID, auth user, session, cookie, search query or URL is stored.
-- Writes are disabled by the Edge Function unless production is explicitly activated.
-- Aggregates are approximate, not deduplicated unique visitors nor fraud-proof billing.

-- destructive-change-reviewed: the ONLY DELETE removes rate-limit minute buckets
-- older than 48 hours from a NEW private table; no existing BLINK records are
-- touched. Deletion is intentional expiry of transient abuse-throttle counters.
-- rollback-plan: first keep BLINK_SITE_ANALYTICS_ENABLED unset/false and the
-- website ENABLED=false, then deploy a separately reviewed migration to revoke
-- the writer and remove the two newly created functions/tables if necessary.
-- No older user data, orders, accounts or messages require restoration.


create table if not exists public.blink_site_metric_daily (
    day date not null,
    page text not null check (page in ('home', 'about', 'features', 'download', 'faq', 'privacy')),
    kind text not null check (kind in ('view', 'download')),
    total bigint not null default 0 check (total >= 0),
    primary key (day, page, kind)
);

create table if not exists private.blink_site_metric_minute_limiter (
    minute_bucket timestamptz not null,
    page text not null check (page in ('home', 'about', 'features', 'download', 'faq', 'privacy')),
    kind text not null check (kind in ('view', 'download')),
    total integer not null default 0 check (total between 0 and 120),
    primary key (minute_bucket, page, kind)
);

alter table public.blink_site_metric_daily enable row level security;
alter table private.blink_site_metric_minute_limiter enable row level security;

revoke all on public.blink_site_metric_daily from public, anon, authenticated;
revoke all on private.blink_site_metric_minute_limiter from public, anon, authenticated;
-- A trusted Edge Function is the sole writer, via a service-role-only RPC.
revoke all on public.blink_site_metric_daily from service_role;
revoke all on private.blink_site_metric_minute_limiter from service_role;

create or replace function public.record_blink_site_metric(
    p_page text,
    p_kind text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_bucket timestamptz := date_trunc('minute', now());
  v_accepted integer;
begin
  if p_page not in ('home', 'about', 'features', 'download', 'faq', 'privacy')
     or p_kind not in ('view', 'download') then
    return false;
  end if;

  -- Atomic per-surface global cap to bound unauthenticated writes.
  -- Not per-user tracking; repeated spoofed clicks still affect counts.
  insert into private.blink_site_metric_minute_limiter as limiter
    (minute_bucket, page, kind, total)
  values (v_bucket, p_page, p_kind, 1)
  on conflict (minute_bucket, page, kind)
  do update set total = limiter.total + 1
    where limiter.total < 120
  returning total into v_accepted;

  if v_accepted is null then
    return false;
  end if;

  insert into public.blink_site_metric_daily as daily
    (day, page, kind, total)
  values ((now() at time zone 'Africa/Lagos')::date, p_page, p_kind, 1)
  on conflict (day, page, kind)
  do update set total = daily.total + 1;

  -- Opportunistic bounded cleanup; no event-level records are retained.
  if random() < 0.004 then
    delete from private.blink_site_metric_minute_limiter
    where minute_bucket < now() - interval '48 hours';
  end if;

  return true;
end;
$$;

revoke all on function public.record_blink_site_metric(text,text)
  from public, anon, authenticated;
grant execute on function public.record_blink_site_metric(text,text)
  to service_role;

create or replace function public.get_blink_site_metrics(p_days integer default 30)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_actor uuid;
  v_days integer := least(90, greatest(1, coalesce(p_days, 30)));
  v_result jsonb;
begin
  v_actor := private.require_blink_admin();

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'day', d.day,
        'page', d.page,
        'kind', d.kind,
        'count', d.total
      ) order by d.day desc, d.page, d.kind
    ),
    '[]'::jsonb
  )
  into v_result
  from public.blink_site_metric_daily d
  where d.day >= (now() at time zone 'Africa/Lagos')::date - (v_days - 1);

  return jsonb_build_object(
    'timezone', 'Africa/Lagos',
    'days', v_days,
    'unique_visitors', null,
    'note', 'Approximate public-page views and APK button clicks. No unique visitor tracking.',
    'records', v_result
  );
end;
$$;

revoke all on function public.get_blink_site_metrics(integer)
  from public, anon, service_role;
grant execute on function public.get_blink_site_metrics(integer)
  to authenticated;

comment on table public.blink_site_metric_daily is
  'Daily BLINK public-page view and APK click aggregates only. No user IDs, IPs, paths, device fingerprints or raw events.';
comment on function public.get_blink_site_metrics(integer) is
  'Admin-only approximate anonymous site metrics. Caller validated via private.require_blink_admin().';
