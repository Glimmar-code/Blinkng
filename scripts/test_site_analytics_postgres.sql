-- BLINK site analytics: deterministic, disposable PostgreSQL integration test.
-- Only synthetic records and fixture roles; NEVER execute against production.
\set ON_ERROR_STOP on
create role anon nologin;
create role authenticated nologin;
create role service_role nologin;
create schema if not exists private;

-- Local stand-in only. Production uses actual private.require_blink_admin(),
-- which checks auth.uid() and private.active_blink_admin_role().
create or replace function private.require_blink_admin()
returns uuid language plpgsql stable security definer
set search_path=''
as $$
begin
  if current_setting('request.jwt.claim.admin_fixture',true) is distinct from 'yes' then
    raise exception 'ADMIN_REQUIRED' using errcode='42501';
  end if;
  return '00000000-0000-4000-8000-000000000001'::uuid;
end;
$$;

\i supabase/migrations/20261018090100_blink_site_aggregate_metrics.sql

-- RLS is enabled and public reads are forbidden.
select case when
  (select relrowsecurity from pg_class where oid='public.blink_site_metric_daily'::regclass)
  and (select relrowsecurity from pg_class where oid='private.blink_site_metric_minute_limiter'::regclass)
then 'PASS RLS enabled' else (1/0)::text end;

set role anon;
do $$
begin
  perform public.record_blink_site_metric('home','view');
  raise exception 'ANON_WRITER_EXPOSED';
exception when insufficient_privilege then null;
end $$;
do $$
begin
  perform count(*) from public.blink_site_metric_daily;
  raise exception 'ANON_RAW_READER_EXPOSED';
exception when insufficient_privilege then null;
end $$;
do $$
begin
  perform public.get_blink_site_metrics(30);
  raise exception 'ANON_ADMIN_READER_EXPOSED';
exception when insufficient_privilege then null;
end $$;
reset role;

set role authenticated;
do $$
begin
  perform public.record_blink_site_metric('home','download');
  raise exception 'AUTHENTICATED_WRITER_EXPOSED';
exception when insufficient_privilege then null;
end $$;
do $$
begin
  perform count(*) from private.blink_site_metric_minute_limiter;
  raise exception 'AUTHENTICATED_LIMITER_READ_EXPOSED';
exception when insufficient_privilege then null;
end $$;
do $$
begin
  perform public.get_blink_site_metrics(30);
  raise exception 'NON_ADMIN_READER_EXPOSED';
exception when insufficient_privilege then null;
end $$;
reset role;

-- Only the trusted service-role RPC may write. The function's Postgres
-- definer enforces the private minute cap without exposing raw table rights.
set role service_role;
do $$
declare
  n integer;
begin
  for n in 1..120 loop
    if not public.record_blink_site_metric('home','view') then
      raise exception 'WRITER_REJECTED_VALID_EVENT at %',n;
    end if;
  end loop;
  if public.record_blink_site_metric('home','view') then
    raise exception 'GLOBAL_RATE_CAP_MISSING';
  end if;
  if public.record_blink_site_metric('invalid-page','view') then
    raise exception 'INVALID_PAGE_ACCEPTED';
  end if;
  if public.record_blink_site_metric('home','invalid-kind') then
    raise exception 'INVALID_KIND_ACCEPTED';
  end if;
  if not public.record_blink_site_metric('download','download') then
    raise exception 'INDEPENDENT_PAGE_KIND_RATE_CAP_BROKEN';
  end if;
end $$;
do $$
begin
  perform count(*) from public.blink_site_metric_daily;
  raise exception 'SERVICE_ROLE_RAW_READER_EXPOSED';
exception when insufficient_privilege then null;
end $$;
reset role;

select case when
  (select total from public.blink_site_metric_daily where page='home' and kind='view')=120
  and (select total from public.blink_site_metric_daily where page='download' and kind='download')=1
then 'PASS server rate cap and daily aggregates' else (1/0)::text end;

-- Admin RPC only uses fixture admin bit here; production identity is
-- validated by private.require_blink_admin() and active_blink_admin_role().
set role authenticated;
select set_config('request.jwt.claim.admin_fixture','yes',false);
select case when
  (public.get_blink_site_metrics(30)->'records') @> '[{"page":"home","kind":"view","count":120},{"page":"download","kind":"download","count":1}]'::jsonb
then 'PASS authorized admin-only aggregate RPC' else (1/0)::text end;
reset role;

select 'PASS disposable PostgreSQL analytics roles, RLS, caps, data schema and admin RPC';
