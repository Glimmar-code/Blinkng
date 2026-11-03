-- BLINK Market-only Feed server guard, reviewed against the isolated PGlite
-- staging role/legacy-trigger fixture on 2026-10-26.
-- Preserve the former trigger function for explicit rollback if needed.
-- This applies to organic Feed posts; trusted sponsored inventory is server-owned.
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

-- Rollback plan (use only after reviewing affected writes):
-- DROP TRIGGER IF EXISTS blink_feed_market_category_guard ON public.feed_posts;
-- DROP FUNCTION IF EXISTS public.blink_validate_feed_market_category();
-- CREATE TRIGGER feed_route_product_ads_trigger
-- BEFORE INSERT OR UPDATE OF text ON public.feed_posts FOR EACH ROW
-- EXECUTE FUNCTION public.route_product_ads_to_market();
