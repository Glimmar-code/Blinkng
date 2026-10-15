-- Give independent specialist directories independent active-listing uniqueness.
-- Legacy Connect listing types retain their previous single-active-per-type rule.
-- A member may have one active listing in each specialist category rather than
-- collisions between unrelated categories sharing the same listing_type.
begin;

-- Earlier directory-category migration is not yet present on every environment.
-- Keep this repair safe and complete even where that migration is still pending.
alter table public.connect_listings
  add column if not exists category_slug text;

do $
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.connect_listings'::regclass
      and conname = 'connect_listings_category_slug_fkey'
  ) then
    alter table public.connect_listings
      add constraint connect_listings_category_slug_fkey
      foreign key (category_slug)
      references public.connect_category_catalog(slug)
      on update cascade;
  end if;
end $;

create index if not exists idx_connect_listings_category_live_newest
  on public.connect_listings (category_slug, created_at desc)
  where is_active = true;

drop index if exists public.connect_listings_one_active_type_per_user;

create unique index if not exists connect_listings_one_active_type_per_user
  on public.connect_listings (user_id, listing_type)
  where is_active and category_slug is null;

create unique index if not exists connect_listings_one_active_specialist_category_per_user
  on public.connect_listings (user_id, category_slug)
  where is_active and category_slug is not null;

commit;
