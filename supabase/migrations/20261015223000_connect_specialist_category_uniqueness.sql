-- Give independent specialist directories independent active-listing uniqueness.
-- Legacy Connect listing types retain their previous single-active-per-type rule.
-- A member may have one active listing in each specialist category rather than
-- collisions between unrelated categories sharing the same listing_type.
begin;

drop index if exists public.connect_listings_one_active_type_per_user;

create unique index if not exists connect_listings_one_active_type_per_user
  on public.connect_listings (user_id, listing_type)
  where is_active and category_slug is null;

create unique index if not exists connect_listings_one_active_specialist_category_per_user
  on public.connect_listings (user_id, category_slug)
  where is_active and category_slug is not null;

commit;
