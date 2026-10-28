-- BLINK essential transactional email queue. TESTLAB / staging migration.
-- This migration DOES NOT configure or enable signup confirmation emails.
-- Never enqueue emails directly from an untrusted Android/desktop/web client.

CREATE SCHEMA IF NOT EXISTS private;

CREATE TABLE IF NOT EXISTS public.blink_essential_email_outbox (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    kind text NOT NULL CHECK (kind IN ('welcome', 'new_device', 'suspicious_activity')),
    event_key text NOT NULL CHECK (char_length(event_key) BETWEEN 8 AND 160),
    status text NOT NULL DEFAULT 'pending'
      CHECK (status IN ('pending','sending','sent','failed')),
    priority smallint NOT NULL DEFAULT 10 CHECK (priority BETWEEN 1 AND 10),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 10),
    available_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz,
    last_error text,
    UNIQUE(user_id, kind, event_key)
);

CREATE INDEX IF NOT EXISTS blink_essential_email_due_idx
 ON public.blink_essential_email_outbox(status, priority, available_at, created_at)
 WHERE status = 'pending';

-- No client can read or write this table, including other users' email states.
ALTER TABLE public.blink_essential_email_outbox ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.blink_essential_email_outbox FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.blink_essential_email_outbox TO service_role;

-- In-band signup event: only verified addresses receive a welcome email.
-- This respects the *existing* Supabase verification policy; it does not
-- activate signup verification or generate any confirmation message.
CREATE OR REPLACE FUNCTION private.blink_queue_welcome_email()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF NEW.email_confirmed_at IS NOT NULL
     AND (TG_OP = 'INSERT' OR OLD.email_confirmed_at IS NULL) THEN
    INSERT INTO public.blink_essential_email_outbox (user_id, kind, event_key, priority)
    VALUES (NEW.id, 'welcome', 'welcome:' || NEW.id::text, 10)
    ON CONFLICT (user_id, kind, event_key) DO NOTHING;
  END IF;
  RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION private.blink_queue_welcome_email() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS blink_queue_welcome_email_on_auth_user ON auth.users;
CREATE TRIGGER blink_queue_welcome_email_on_auth_user
AFTER INSERT OR UPDATE OF email_confirmed_at ON auth.users
FOR EACH ROW EXECUTE FUNCTION private.blink_queue_welcome_email();

COMMENT ON TABLE public.blink_essential_email_outbox IS
  'Service-only transactional email queue; no signup confirmation, no client access. New-device and suspicious events require authenticated server-side event detection before insertion.';
