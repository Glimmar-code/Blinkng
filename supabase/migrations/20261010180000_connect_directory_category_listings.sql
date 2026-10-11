-- Connect directory categories need their own persisted listings instead of
-- sending every specialist category to the same generic mentor page.
-- Existing listings remain untouched (NULL category_slug).
ALTER TABLE public.connect_listings
  ADD COLUMN IF NOT EXISTS category_slug text;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'public.connect_listings'::regclass
      AND conname = 'connect_listings_category_slug_fkey'
  ) THEN
    ALTER TABLE public.connect_listings
      ADD CONSTRAINT connect_listings_category_slug_fkey
      FOREIGN KEY (category_slug)
      REFERENCES public.connect_category_catalog(slug)
      ON UPDATE CASCADE;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_connect_listings_category_live_newest
  ON public.connect_listings (category_slug, created_at DESC)
  WHERE is_active = true;

-- RLS stays enabled. Own account IDs come from auth.uid() defaults.
-- The existing insert policy blocks users from posting as another account;
-- connect_applications policy blocks applying to inactive/own listings.
