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
  v_copy text := concat_ws(' ', coalesce(new.text,''), coalesce(new.caption,''), coalesce(new.alt_text,''));
  v_sales_intent boolean;
  v_has_price boolean;
  v_server_ad boolean :=
      current_user in ('postgres', 'service_role')
      and new.is_sponsored is true
      and length(btrim(coalesce(new.ad_label,''))) > 0;
begin
  -- Sponsored flags are server-owned. A regular authenticated client MUST NOT
  -- be able to claim feed-ad status simply by choosing a benign category.
  if (new.is_sponsored is true
      or length(btrim(coalesce(new.ad_label,''))) > 0
      or length(btrim(coalesce(new.ad_cta,''))) > 0)
     and current_user not in ('postgres', 'service_role') then
    raise exception 'BLINK_AD_REQUIRES_SERVER_APPROVAL'
      using errcode = '42501',
            hint = 'Paid feed promotion must be created by BLINK payment-verified server logic.';
  end if;
  v_sales_intent := v_copy ~* '(^|[^[:alpha:]])(for sale|selling (this|my|a|an)|available for (purchase|sale)|dm to (buy|order)|buy now|order now|price is)([^[:alpha:]]|$)';
  v_has_price := v_copy ~* '(₦|NGN[[:space:]]*)[[:space:]]*[0-9]';
  -- Restrict the combination of explicit sales language and a price.
  -- A review or discussion ABOUT an item must remain publishable.
  if (v_category in ('marketplace', 'market', 'buy & sell', 'for sale', 'selling')
      or (v_sales_intent and v_has_price)) and not v_server_ad then
    raise exception 'BLINK_MARKET_LISTING_ONLY'
      using errcode = '23514',
            hint = 'Create a dedicated BLINK Market listing. Only approved server-side ads can promote listings in Feed.';
  end if;
  return new;
end;
$$;

-- Replaces the narrower old text-only trigger atomically. Keeping both would
-- block even a trusted, payment-approved Sponsored Market placement.
-- Keep the old function for a reversible rollback; do NOT deploy untested.
drop trigger if exists feed_route_product_ads_trigger on public.feed_posts;
drop trigger if exists blink_feed_market_category_guard on public.feed_posts;
create trigger blink_feed_market_category_guard
before insert or update of category, text, caption, alt_text, is_sponsored, ad_label, ad_cta
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
-- 3b. authenticated UPDATE of is_sponsored=true, ad_label or ad_cta on an existing
--     post must be rejected unless performed by the verified server role.
-- 4. Authenticated ordinary social posts (including discussion of prices) stay accepted.
-- 4a. Explicit sales intent + naira price in text/caption/alt_text is rejected.
-- 4b. Updating caption or alt_text to add that combination is rejected.
-- 4c. An image containing sales words without any accompanying text or alt
--     description is NOT reliably detectable by this text trigger: image moderation
--     and user reporting must remain separate until server media analysis exists.
-- 5. Trusted approved service_role sponsored placement with clear label is
--    accepted and is tied to separate server-authoritative paid-ad checks.
-- 6. Existing tests for server-created ads/boosts, RLS, real Market listings,
--    media posts and legacy release clients continue to pass.
-- 7. SQL is also exercised by staged-market-community-contract-tests.mjs.
-- 8. Rollback (staging): drop trigger if exists
--    blink_feed_market_category_guard on public.feed_posts;
--    drop function if exists public.blink_validate_feed_market_category();
--    Recreate the former text-only trigger ONLY IF this known legacy function
--    still exists, using BEFORE INSERT OR UPDATE OF text:
--    public.route_product_ads_to_market(). Verify the full previous definition
--    before relying on this rollback in a deployed environment.
