-- DEPLOYMENT NOTE 2026-10-28: A vetted security-invoker version of this guard
-- is now live in BLINK Supabase, migration 20261028200903.
-- See supabase/migrations/20261028200903_blink_poll_vote_expiration_guard_20261028.sql
-- for the actual deployed definition. This draft remains a historical staging reference.
-- The real-account end-to-end vote test remains pending.

-- STAGING ONLY: DO NOT RUN AGAINST PRODUCTION WITHOUT FREE ISOLATED
-- PostgreSQL role tests and latest-head Android/Windows integration results.
-- Existing public.polls already has expires_at and allows_multiple columns.
-- This trigger guards expired voting even from older mobile clients.
-- On production, plan an idempotent maintenance deployment in the protected release.
BEGIN;

CREATE OR REPLACE FUNCTION public.blink_enforce_poll_vote_deadline()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $body$
DECLARE
    deadline timestamptz;
BEGIN
    SELECT p.expires_at INTO deadline
    FROM public.polls AS p
    WHERE p.id = NEW.poll_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Poll not found' USING ERRCODE = '23503';
    END IF;

    IF deadline IS NOT NULL AND deadline <= now() THEN
        RAISE EXCEPTION 'Poll has closed' USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.poll_options po
        WHERE po.id = NEW.option_id AND po.poll_id = NEW.poll_id
    ) THEN
        RAISE EXCEPTION 'Poll option does not belong to poll'
        USING ERRCODE = '23514';
    END IF;

    IF NEW.user_id IS DISTINCT FROM auth.uid() THEN
        RAISE EXCEPTION 'Vote owner must match authenticated user'
        USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END;
$body$;

REVOKE ALL ON FUNCTION public.blink_enforce_poll_vote_deadline()
    FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS blink_poll_vote_deadline ON public.poll_votes;
CREATE TRIGGER blink_poll_vote_deadline
    BEFORE INSERT OR UPDATE OF poll_id, option_id, user_id ON public.poll_votes
    FOR EACH ROW EXECUTE FUNCTION public.blink_enforce_poll_vote_deadline();

COMMIT;

-- Isolation test plan:
-- (1) vote 1ms before expiration succeeds
-- (2) vote at/after expiration rejected, including older clients
-- (3) anon/cross-user insert denied
-- (4) mismatched option_id/poll_id denied
-- (5) duplicates rejected by existing UNIQUE (poll_id,user_id)
-- (6) edit/create on behalf of another post author denied by existing RLS
-- (7) old polls with NULL expiry remain votable, preserving compatibility
-- (8) evaluate trigger execution role in isolated authenticated JWT sessions
-- (9) verify new function is not externally callable and audit grants
-- Rollback:
-- DROP TRIGGER IF EXISTS blink_poll_vote_deadline ON public.poll_votes;
-- DROP FUNCTION IF EXISTS public.blink_enforce_poll_vote_deadline();
