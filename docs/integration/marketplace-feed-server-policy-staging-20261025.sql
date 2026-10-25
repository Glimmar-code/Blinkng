-- BLINK MARKET vs FEED — STAGING CANDIDATE ONLY.
-- This file deliberately resides outside supabase/migrations until the
-- versioned migration is created with Supabase CLI and successfully validated
-- in a free disposable LOCAL/staging Postgres instance.
-- DO NOT apply it directly to hosted production Supabase.
--
-- Existing production table inspected read-only on 2026-10-25:
-- public.feed_posts(category text,is_sponsored boolean,ad_label text,...).
-- Client checks alone are insufficient: authenticated PostgREST clients can
-- otherwise submit "Marketplace" directly to public.feed_posts.
--
-- Approved ads are trusted SERVER writes performed through privileged roles;
-- normal authenticated users cannot impersonate this exception.
begin;

create or replace function public.blink_validate_feed_market_category()
returns trigger
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_category text := lower(btrim(coalesce(new.category, '')));
  v_server_ad boolean :=
      current_user in ('postgres', 'service_role')
      and new.is_sponsored is true
      and length(btrim(coalesce(new.ad_label,''))) > 0;
begin
  -- Sponsored flags are server-owned. A regular authenticated client MUST NOT
  -- be able to claim feed-ad status simply by choosing a benign category.
  if new.is_sponsored is true and current_user not in ('postgres', 'service_role') then
    raise exception 'BLINK_AD_REQUIRES_SERVER_APPROVAL'
      using errcode = '42501',
            hint = 'Paid feed promotion must be created by BLINK payment-verified server logic.';
  end if;
  if v_category in ('marketplace', 'market', 'for sale', 'selling') and not v_server_ad then
    raise exception 'BLINK_MARKET_LISTING_ONLY'
      using errcode = '23514',
            hint = 'Create a dedicated BLINK Market listing. Only approved server-side ads can promote listings in Feed.';
  end if;
  return new;
end;
$$;

drop trigger if exists blink_feed_market_category_guard on public.feed_posts;
create trigger blink_feed_market_category_guard
before insert or update of category, is_sponsored, ad_label
on public.feed_posts
for each row
execute function public.blink_validate_feed_market_category();

commit;

-- MANDATORY FREE STAGING TEST MATRIX BEFORE MIGRATION/PRODUCTION:
-- 1. Existing legacy feed rows are neither reclassified nor deleted.
-- 2. authenticated INSERT category='Marketplace' is rejected, even when
--    is_sponsored=true and ad_label is set by a malicious client.
-- 3. authenticated UPDATE of an ordinary post to category='Market' is rejected.
-- 3a. authenticated INSERT with category='Campus Life', is_sponsored=true
--     must ALSO be rejected; never trust a client-supplied sponsored flag.
-- 3b. authenticated UPDATE of is_sponsored=true or ad_label on an existing
--     post must be rejected unless performed by the verified server role.
-- 4. authenticated ordinary social posts (including discussions of an item)
--    remain accepted; never classify text with unreliable banned keywords.
-- 5. Trusted approved service_role sponsored placement with clear label is
--    accepted and is tied to separate server-authoritative paid-ad checks.
-- 6. Existing tests for server-created ads/boosts, RLS, real Market listings,
--    media posts and legacy release clients continue to pass.
-- 7. Rollback (staging): drop trigger if exists
--    blink_feed_market_category_guard on public.feed_posts;
--    drop function if exists public.blink_validate_feed_market_category();
