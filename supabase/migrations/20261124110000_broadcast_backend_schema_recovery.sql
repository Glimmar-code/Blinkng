-- Broadcast backend schema recovery (Testlab-first)
-- Reconstructed from production catalog snapshot 2026-11-24.
-- Additive/idempotent table and type creation. Does not touch existing BLINK wallet,
-- profiles, marketplace, referrals, contests, universities, or admin-role tables.
-- Apply only after review in a disposable Supabase branch.

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public' AND t.typname='broadcast_channel') THEN CREATE TYPE public.broadcast_channel AS ENUM ('in_app','push','email','website'); END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public' AND t.typname='broadcast_delivery_status') THEN CREATE TYPE public.broadcast_delivery_status AS ENUM ('queued','sent','failed','skipped'); END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public' AND t.typname='broadcast_type') THEN CREATE TYPE public.broadcast_type AS ENUM ('ANNOUNCEMENT','CAMPAIGN','PROMO'); END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public' AND t.typname='broadcast_status') THEN CREATE TYPE public.broadcast_status AS ENUM ('draft','scheduled','live','paused','ended'); END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public' AND t.typname='broadcast_block_kind') THEN CREATE TYPE public.broadcast_block_kind AS ENUM ('maintenance','force_update'); END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public' AND t.typname='broadcast_goal_type') THEN CREATE TYPE public.broadcast_goal_type AS ENUM ('invites','purchases','orders','votes'); END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public' AND t.typname='broadcast_claim_status') THEN CREATE TYPE public.broadcast_claim_status AS ENUM ('claimed','limit_reached','expired','sold_out','not_eligible'); END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public' AND t.typname='broadcast_reward_type') THEN CREATE TYPE public.broadcast_reward_type AS ENUM ('coins','free_spin','fee_discount','boost'); END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.broadcast_app_config (
  id boolean NOT NULL DEFAULT true,
  maintenance_enabled boolean NOT NULL DEFAULT false,
  maintenance_message text,
  min_app_version text,
  store_url text,
  updated_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.broadcast_app_config ADD COLUMN IF NOT EXISTS id boolean NOT NULL DEFAULT true;

ALTER TABLE public.broadcast_app_config ADD COLUMN IF NOT EXISTS maintenance_enabled boolean NOT NULL DEFAULT false;

ALTER TABLE public.broadcast_app_config ADD COLUMN IF NOT EXISTS maintenance_message text;

ALTER TABLE public.broadcast_app_config ADD COLUMN IF NOT EXISTS min_app_version text;

ALTER TABLE public.broadcast_app_config ADD COLUMN IF NOT EXISTS store_url text;

ALTER TABLE public.broadcast_app_config ADD COLUMN IF NOT EXISTS updated_by uuid;

ALTER TABLE public.broadcast_app_config ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS public.broadcast_audit_log (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  actor_id uuid,
  action text NOT NULL,
  broadcast_id uuid,
  before jsonb,
  after jsonb,
  ip inet,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.broadcast_audit_log ADD COLUMN IF NOT EXISTS id uuid NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE public.broadcast_audit_log ADD COLUMN IF NOT EXISTS actor_id uuid;

ALTER TABLE public.broadcast_audit_log ADD COLUMN IF NOT EXISTS action text NOT NULL;

ALTER TABLE public.broadcast_audit_log ADD COLUMN IF NOT EXISTS broadcast_id uuid;

ALTER TABLE public.broadcast_audit_log ADD COLUMN IF NOT EXISTS before jsonb;

ALTER TABLE public.broadcast_audit_log ADD COLUMN IF NOT EXISTS after jsonb;

ALTER TABLE public.broadcast_audit_log ADD COLUMN IF NOT EXISTS ip inet;

ALTER TABLE public.broadcast_audit_log ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS public.broadcast_clicks (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  broadcast_id uuid NOT NULL,
  surface text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.broadcast_clicks ADD COLUMN IF NOT EXISTS id uuid NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE public.broadcast_clicks ADD COLUMN IF NOT EXISTS user_id uuid NOT NULL;

ALTER TABLE public.broadcast_clicks ADD COLUMN IF NOT EXISTS broadcast_id uuid NOT NULL;

ALTER TABLE public.broadcast_clicks ADD COLUMN IF NOT EXISTS surface text NOT NULL;

ALTER TABLE public.broadcast_clicks ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS public.broadcast_daily_rollups (
  day date NOT NULL,
  broadcast_id uuid NOT NULL,
  impressions bigint NOT NULL DEFAULT 0,
  clicks bigint NOT NULL DEFAULT 0
);

ALTER TABLE public.broadcast_daily_rollups ADD COLUMN IF NOT EXISTS day date NOT NULL;

ALTER TABLE public.broadcast_daily_rollups ADD COLUMN IF NOT EXISTS broadcast_id uuid NOT NULL;

ALTER TABLE public.broadcast_daily_rollups ADD COLUMN IF NOT EXISTS impressions bigint NOT NULL DEFAULT 0;

ALTER TABLE public.broadcast_daily_rollups ADD COLUMN IF NOT EXISTS clicks bigint NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS public.broadcast_deliveries (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  broadcast_id uuid NOT NULL,
  user_id uuid NOT NULL,
  channel broadcast_channel NOT NULL,
  status broadcast_delivery_status NOT NULL DEFAULT 'queued'::broadcast_delivery_status,
  attempts integer NOT NULL DEFAULT 0,
  last_error text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  sent_at timestamptz
);

ALTER TABLE public.broadcast_deliveries ADD COLUMN IF NOT EXISTS id uuid NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE public.broadcast_deliveries ADD COLUMN IF NOT EXISTS broadcast_id uuid NOT NULL;

ALTER TABLE public.broadcast_deliveries ADD COLUMN IF NOT EXISTS user_id uuid NOT NULL;

ALTER TABLE public.broadcast_deliveries ADD COLUMN IF NOT EXISTS channel broadcast_channel NOT NULL;

ALTER TABLE public.broadcast_deliveries ADD COLUMN IF NOT EXISTS status broadcast_delivery_status NOT NULL DEFAULT 'queued'::broadcast_delivery_status;

ALTER TABLE public.broadcast_deliveries ADD COLUMN IF NOT EXISTS attempts integer NOT NULL DEFAULT 0;

ALTER TABLE public.broadcast_deliveries ADD COLUMN IF NOT EXISTS last_error text;

ALTER TABLE public.broadcast_deliveries ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE public.broadcast_deliveries ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE public.broadcast_deliveries ADD COLUMN IF NOT EXISTS sent_at timestamptz;

CREATE TABLE IF NOT EXISTS public.broadcast_dismissals (
  user_id uuid NOT NULL,
  broadcast_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.broadcast_dismissals ADD COLUMN IF NOT EXISTS user_id uuid NOT NULL;

ALTER TABLE public.broadcast_dismissals ADD COLUMN IF NOT EXISTS broadcast_id uuid NOT NULL;

ALTER TABLE public.broadcast_dismissals ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS public.broadcast_impressions (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  broadcast_id uuid NOT NULL,
  surface text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.broadcast_impressions ADD COLUMN IF NOT EXISTS id uuid NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE public.broadcast_impressions ADD COLUMN IF NOT EXISTS user_id uuid NOT NULL;

ALTER TABLE public.broadcast_impressions ADD COLUMN IF NOT EXISTS broadcast_id uuid NOT NULL;

ALTER TABLE public.broadcast_impressions ADD COLUMN IF NOT EXISTS surface text NOT NULL;

ALTER TABLE public.broadcast_impressions ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS public.broadcast_rate_limits (
  user_id uuid NOT NULL,
  action text NOT NULL,
  window_started_at timestamptz NOT NULL,
  count integer NOT NULL DEFAULT 0
);

ALTER TABLE public.broadcast_rate_limits ADD COLUMN IF NOT EXISTS user_id uuid NOT NULL;

ALTER TABLE public.broadcast_rate_limits ADD COLUMN IF NOT EXISTS action text NOT NULL;

ALTER TABLE public.broadcast_rate_limits ADD COLUMN IF NOT EXISTS window_started_at timestamptz NOT NULL;

ALTER TABLE public.broadcast_rate_limits ADD COLUMN IF NOT EXISTS count integer NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS public.broadcast_reads (
  user_id uuid NOT NULL,
  broadcast_id uuid NOT NULL,
  read_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.broadcast_reads ADD COLUMN IF NOT EXISTS user_id uuid NOT NULL;

ALTER TABLE public.broadcast_reads ADD COLUMN IF NOT EXISTS broadcast_id uuid NOT NULL;

ALTER TABLE public.broadcast_reads ADD COLUMN IF NOT EXISTS read_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS public.broadcasts (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  type broadcast_type NOT NULL,
  status broadcast_status NOT NULL DEFAULT 'draft'::broadcast_status,
  title text NOT NULL,
  body text NOT NULL,
  image_url text,
  cta_label text,
  deep_link text,
  priority integer NOT NULL DEFAULT 0,
  starts_at timestamptz,
  ends_at timestamptz,
  timezone text NOT NULL DEFAULT 'Africa/Lagos'::text,
  dismissible boolean NOT NULL DEFAULT true,
  is_blocking boolean NOT NULL DEFAULT false,
  block_kind broadcast_block_kind,
  min_app_version text,
  store_url text,
  audience jsonb NOT NULL DEFAULT '{"kind": "everyone"}'::jsonb,
  channels jsonb NOT NULL DEFAULT '{"push": false, "email": false, "in_app": true, "website": false, "frequency_cap": 1, "quiet_hours_respect": true}'::jsonb,
  created_by uuid,
  updated_by uuid,
  version integer NOT NULL DEFAULT 1,
  deleted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS id uuid NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS type broadcast_type NOT NULL;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS status broadcast_status NOT NULL DEFAULT 'draft'::broadcast_status;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS title text NOT NULL;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS body text NOT NULL;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS image_url text;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS cta_label text;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS deep_link text;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS priority integer NOT NULL DEFAULT 0;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS starts_at timestamptz;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS ends_at timestamptz;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS timezone text NOT NULL DEFAULT 'Africa/Lagos'::text;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS dismissible boolean NOT NULL DEFAULT true;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS is_blocking boolean NOT NULL DEFAULT false;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS block_kind broadcast_block_kind;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS min_app_version text;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS store_url text;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS audience jsonb NOT NULL DEFAULT '{"kind": "everyone"}'::jsonb;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS channels jsonb NOT NULL DEFAULT '{"push": false, "email": false, "in_app": true, "website": false, "frequency_cap": 1, "quiet_hours_respect": true}'::jsonb;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS created_by uuid;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS updated_by uuid;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 1;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE public.broadcasts ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS public.broadcasts_changed (
  signal_id smallint NOT NULL DEFAULT 1,
  changed_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.broadcasts_changed ADD COLUMN IF NOT EXISTS signal_id smallint NOT NULL DEFAULT 1;

ALTER TABLE public.broadcasts_changed ADD COLUMN IF NOT EXISTS changed_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS public.campaign_participants (
  broadcast_id uuid NOT NULL,
  user_id uuid NOT NULL,
  joined_at timestamptz NOT NULL DEFAULT now(),
  progress integer NOT NULL DEFAULT 0,
  completed_at timestamptz,
  reward_claimed_at timestamptz,
  reward_tier integer
);

ALTER TABLE public.campaign_participants ADD COLUMN IF NOT EXISTS broadcast_id uuid NOT NULL;

ALTER TABLE public.campaign_participants ADD COLUMN IF NOT EXISTS user_id uuid NOT NULL;

ALTER TABLE public.campaign_participants ADD COLUMN IF NOT EXISTS joined_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE public.campaign_participants ADD COLUMN IF NOT EXISTS progress integer NOT NULL DEFAULT 0;

ALTER TABLE public.campaign_participants ADD COLUMN IF NOT EXISTS completed_at timestamptz;

ALTER TABLE public.campaign_participants ADD COLUMN IF NOT EXISTS reward_claimed_at timestamptz;

ALTER TABLE public.campaign_participants ADD COLUMN IF NOT EXISTS reward_tier integer;

CREATE TABLE IF NOT EXISTS public.campaign_progress_events (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  broadcast_id uuid NOT NULL,
  user_id uuid NOT NULL,
  source_type text NOT NULL,
  source_id text NOT NULL,
  delta integer NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.campaign_progress_events ADD COLUMN IF NOT EXISTS id uuid NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE public.campaign_progress_events ADD COLUMN IF NOT EXISTS broadcast_id uuid NOT NULL;

ALTER TABLE public.campaign_progress_events ADD COLUMN IF NOT EXISTS user_id uuid NOT NULL;

ALTER TABLE public.campaign_progress_events ADD COLUMN IF NOT EXISTS source_type text NOT NULL;

ALTER TABLE public.campaign_progress_events ADD COLUMN IF NOT EXISTS source_id text NOT NULL;

ALTER TABLE public.campaign_progress_events ADD COLUMN IF NOT EXISTS delta integer NOT NULL DEFAULT 1;

ALTER TABLE public.campaign_progress_events ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS public.campaign_rules (
  broadcast_id uuid NOT NULL,
  goal_type broadcast_goal_type NOT NULL,
  target integer NOT NULL,
  tiers jsonb NOT NULL DEFAULT '[]'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.campaign_rules ADD COLUMN IF NOT EXISTS broadcast_id uuid NOT NULL;

ALTER TABLE public.campaign_rules ADD COLUMN IF NOT EXISTS goal_type broadcast_goal_type NOT NULL;

ALTER TABLE public.campaign_rules ADD COLUMN IF NOT EXISTS target integer NOT NULL;

ALTER TABLE public.campaign_rules ADD COLUMN IF NOT EXISTS tiers jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE public.campaign_rules ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE public.campaign_rules ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS public.device_tokens (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  token text NOT NULL,
  platform text NOT NULL,
  app_version text,
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  disabled_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS id uuid NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS user_id uuid NOT NULL;

ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS token text NOT NULL;

ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS platform text NOT NULL;

ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS app_version text;

ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS last_seen_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS disabled_at timestamptz;

ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS public.notification_prefs (
  user_id uuid NOT NULL,
  push_enabled boolean NOT NULL DEFAULT true,
  announcements boolean NOT NULL DEFAULT true,
  campaigns boolean NOT NULL DEFAULT true,
  offers boolean NOT NULL DEFAULT true,
  email boolean NOT NULL DEFAULT true,
  quiet_start time,
  quiet_end time,
  timezone text NOT NULL DEFAULT 'Africa/Lagos'::text,
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.notification_prefs ADD COLUMN IF NOT EXISTS user_id uuid NOT NULL;

ALTER TABLE public.notification_prefs ADD COLUMN IF NOT EXISTS push_enabled boolean NOT NULL DEFAULT true;

ALTER TABLE public.notification_prefs ADD COLUMN IF NOT EXISTS announcements boolean NOT NULL DEFAULT true;

ALTER TABLE public.notification_prefs ADD COLUMN IF NOT EXISTS campaigns boolean NOT NULL DEFAULT true;

ALTER TABLE public.notification_prefs ADD COLUMN IF NOT EXISTS offers boolean NOT NULL DEFAULT true;

ALTER TABLE public.notification_prefs ADD COLUMN IF NOT EXISTS email boolean NOT NULL DEFAULT true;

ALTER TABLE public.notification_prefs ADD COLUMN IF NOT EXISTS quiet_start time;

ALTER TABLE public.notification_prefs ADD COLUMN IF NOT EXISTS quiet_end time;

ALTER TABLE public.notification_prefs ADD COLUMN IF NOT EXISTS timezone text NOT NULL DEFAULT 'Africa/Lagos'::text;

ALTER TABLE public.notification_prefs ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS public.promo_claims (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  broadcast_id uuid NOT NULL,
  user_id uuid NOT NULL,
  status broadcast_claim_status NOT NULL,
  reward jsonb NOT NULL DEFAULT '{}'::jsonb,
  idempotency_key text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.promo_claims ADD COLUMN IF NOT EXISTS id uuid NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE public.promo_claims ADD COLUMN IF NOT EXISTS broadcast_id uuid NOT NULL;

ALTER TABLE public.promo_claims ADD COLUMN IF NOT EXISTS user_id uuid NOT NULL;

ALTER TABLE public.promo_claims ADD COLUMN IF NOT EXISTS status broadcast_claim_status NOT NULL;

ALTER TABLE public.promo_claims ADD COLUMN IF NOT EXISTS reward jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE public.promo_claims ADD COLUMN IF NOT EXISTS idempotency_key text NOT NULL;

ALTER TABLE public.promo_claims ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS public.promo_rules (
  broadcast_id uuid NOT NULL,
  reward_type broadcast_reward_type NOT NULL,
  reward_amount numeric NOT NULL,
  per_user_limit integer NOT NULL DEFAULT 1,
  total_budget_cap numeric,
  budget_used numeric NOT NULL DEFAULT 0,
  min_purchase numeric NOT NULL DEFAULT 0,
  reward_text text,
  placement text[] NOT NULL DEFAULT '{}'::text[],
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.promo_rules ADD COLUMN IF NOT EXISTS broadcast_id uuid NOT NULL;

ALTER TABLE public.promo_rules ADD COLUMN IF NOT EXISTS reward_type broadcast_reward_type NOT NULL;

ALTER TABLE public.promo_rules ADD COLUMN IF NOT EXISTS reward_amount numeric NOT NULL;

ALTER TABLE public.promo_rules ADD COLUMN IF NOT EXISTS per_user_limit integer NOT NULL DEFAULT 1;

ALTER TABLE public.promo_rules ADD COLUMN IF NOT EXISTS total_budget_cap numeric;

ALTER TABLE public.promo_rules ADD COLUMN IF NOT EXISTS budget_used numeric NOT NULL DEFAULT 0;

ALTER TABLE public.promo_rules ADD COLUMN IF NOT EXISTS min_purchase numeric NOT NULL DEFAULT 0;

ALTER TABLE public.promo_rules ADD COLUMN IF NOT EXISTS reward_text text;

ALTER TABLE public.promo_rules ADD COLUMN IF NOT EXISTS placement text[] NOT NULL DEFAULT '{}'::text[];

ALTER TABLE public.promo_rules ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE public.promo_rules ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_app_config'::regclass AND conname='broadcast_app_config_id_check') THEN ALTER TABLE public.broadcast_app_config ADD CONSTRAINT broadcast_app_config_id_check CHECK (id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_app_config'::regclass AND conname='broadcast_app_config_pkey') THEN ALTER TABLE public.broadcast_app_config ADD CONSTRAINT broadcast_app_config_pkey PRIMARY KEY (id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_app_config'::regclass AND conname='broadcast_app_config_updated_by_fkey') THEN ALTER TABLE public.broadcast_app_config ADD CONSTRAINT broadcast_app_config_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth.users(id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_audit_log'::regclass AND conname='broadcast_audit_log_actor_id_fkey') THEN ALTER TABLE public.broadcast_audit_log ADD CONSTRAINT broadcast_audit_log_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES auth.users(id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_audit_log'::regclass AND conname='broadcast_audit_log_broadcast_id_fkey') THEN ALTER TABLE public.broadcast_audit_log ADD CONSTRAINT broadcast_audit_log_broadcast_id_fkey FOREIGN KEY (broadcast_id) REFERENCES broadcasts(id) ON DELETE SET NULL; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_audit_log'::regclass AND conname='broadcast_audit_log_pkey') THEN ALTER TABLE public.broadcast_audit_log ADD CONSTRAINT broadcast_audit_log_pkey PRIMARY KEY (id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_clicks'::regclass AND conname='broadcast_clicks_broadcast_id_fkey') THEN ALTER TABLE public.broadcast_clicks ADD CONSTRAINT broadcast_clicks_broadcast_id_fkey FOREIGN KEY (broadcast_id) REFERENCES broadcasts(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_clicks'::regclass AND conname='broadcast_clicks_pkey') THEN ALTER TABLE public.broadcast_clicks ADD CONSTRAINT broadcast_clicks_pkey PRIMARY KEY (id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_clicks'::regclass AND conname='broadcast_clicks_user_id_fkey') THEN ALTER TABLE public.broadcast_clicks ADD CONSTRAINT broadcast_clicks_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_daily_rollups'::regclass AND conname='broadcast_daily_rollups_broadcast_id_fkey') THEN ALTER TABLE public.broadcast_daily_rollups ADD CONSTRAINT broadcast_daily_rollups_broadcast_id_fkey FOREIGN KEY (broadcast_id) REFERENCES broadcasts(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_daily_rollups'::regclass AND conname='broadcast_daily_rollups_day_broadcast_id_key') THEN ALTER TABLE public.broadcast_daily_rollups ADD CONSTRAINT broadcast_daily_rollups_day_broadcast_id_key UNIQUE (day, broadcast_id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_deliveries'::regclass AND conname='broadcast_deliveries_broadcast_id_fkey') THEN ALTER TABLE public.broadcast_deliveries ADD CONSTRAINT broadcast_deliveries_broadcast_id_fkey FOREIGN KEY (broadcast_id) REFERENCES broadcasts(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_deliveries'::regclass AND conname='broadcast_deliveries_broadcast_id_user_id_channel_key') THEN ALTER TABLE public.broadcast_deliveries ADD CONSTRAINT broadcast_deliveries_broadcast_id_user_id_channel_key UNIQUE (broadcast_id, user_id, channel); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_deliveries'::regclass AND conname='broadcast_deliveries_pkey') THEN ALTER TABLE public.broadcast_deliveries ADD CONSTRAINT broadcast_deliveries_pkey PRIMARY KEY (id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_deliveries'::regclass AND conname='broadcast_deliveries_user_id_fkey') THEN ALTER TABLE public.broadcast_deliveries ADD CONSTRAINT broadcast_deliveries_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_dismissals'::regclass AND conname='broadcast_dismissals_broadcast_id_fkey') THEN ALTER TABLE public.broadcast_dismissals ADD CONSTRAINT broadcast_dismissals_broadcast_id_fkey FOREIGN KEY (broadcast_id) REFERENCES broadcasts(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_dismissals'::regclass AND conname='broadcast_dismissals_pkey') THEN ALTER TABLE public.broadcast_dismissals ADD CONSTRAINT broadcast_dismissals_pkey PRIMARY KEY (user_id, broadcast_id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_dismissals'::regclass AND conname='broadcast_dismissals_user_id_fkey') THEN ALTER TABLE public.broadcast_dismissals ADD CONSTRAINT broadcast_dismissals_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_impressions'::regclass AND conname='broadcast_impressions_broadcast_id_fkey') THEN ALTER TABLE public.broadcast_impressions ADD CONSTRAINT broadcast_impressions_broadcast_id_fkey FOREIGN KEY (broadcast_id) REFERENCES broadcasts(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_impressions'::regclass AND conname='broadcast_impressions_pkey') THEN ALTER TABLE public.broadcast_impressions ADD CONSTRAINT broadcast_impressions_pkey PRIMARY KEY (id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_impressions'::regclass AND conname='broadcast_impressions_user_id_fkey') THEN ALTER TABLE public.broadcast_impressions ADD CONSTRAINT broadcast_impressions_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_rate_limits'::regclass AND conname='broadcast_rate_limits_pkey') THEN ALTER TABLE public.broadcast_rate_limits ADD CONSTRAINT broadcast_rate_limits_pkey PRIMARY KEY (user_id, action); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_rate_limits'::regclass AND conname='broadcast_rate_limits_user_id_fkey') THEN ALTER TABLE public.broadcast_rate_limits ADD CONSTRAINT broadcast_rate_limits_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_reads'::regclass AND conname='broadcast_reads_broadcast_id_fkey') THEN ALTER TABLE public.broadcast_reads ADD CONSTRAINT broadcast_reads_broadcast_id_fkey FOREIGN KEY (broadcast_id) REFERENCES broadcasts(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_reads'::regclass AND conname='broadcast_reads_pkey') THEN ALTER TABLE public.broadcast_reads ADD CONSTRAINT broadcast_reads_pkey PRIMARY KEY (user_id, broadcast_id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcast_reads'::regclass AND conname='broadcast_reads_user_id_fkey') THEN ALTER TABLE public.broadcast_reads ADD CONSTRAINT broadcast_reads_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcasts'::regclass AND conname='broadcasts_block_check') THEN ALTER TABLE public.broadcasts ADD CONSTRAINT broadcasts_block_check CHECK ((((is_blocking = false) AND (block_kind IS NULL)) OR ((is_blocking = true) AND (block_kind IS NOT NULL)))); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcasts'::regclass AND conname='broadcasts_body_check') THEN ALTER TABLE public.broadcasts ADD CONSTRAINT broadcasts_body_check CHECK ((length(body) <= 240)); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcasts'::regclass AND conname='broadcasts_created_by_fkey') THEN ALTER TABLE public.broadcasts ADD CONSTRAINT broadcasts_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth.users(id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcasts'::regclass AND conname='broadcasts_link_check') THEN ALTER TABLE public.broadcasts ADD CONSTRAINT broadcasts_link_check CHECK (((deep_link IS NULL) OR (deep_link ~ '^blink://(spin|market|contest|campaign/[0-9a-fA-F-]{36}|promo/[0-9a-fA-F-]{36}|wallet|profile/[0-9a-fA-F-]{36})$'::text))); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcasts'::regclass AND conname='broadcasts_pkey') THEN ALTER TABLE public.broadcasts ADD CONSTRAINT broadcasts_pkey PRIMARY KEY (id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcasts'::regclass AND conname='broadcasts_store_url_check') THEN ALTER TABLE public.broadcasts ADD CONSTRAINT broadcasts_store_url_check CHECK (((store_url IS NULL) OR (store_url ~ '^https://'::text))); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcasts'::regclass AND conname='broadcasts_title_check') THEN ALTER TABLE public.broadcasts ADD CONSTRAINT broadcasts_title_check CHECK ((length(title) <= 60)); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcasts'::regclass AND conname='broadcasts_updated_by_fkey') THEN ALTER TABLE public.broadcasts ADD CONSTRAINT broadcasts_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth.users(id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcasts'::regclass AND conname='broadcasts_window') THEN ALTER TABLE public.broadcasts ADD CONSTRAINT broadcasts_window CHECK (((ends_at IS NULL) OR (starts_at IS NULL) OR (ends_at > starts_at))); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcasts_changed'::regclass AND conname='broadcasts_changed_pkey') THEN ALTER TABLE public.broadcasts_changed ADD CONSTRAINT broadcasts_changed_pkey PRIMARY KEY (signal_id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.broadcasts_changed'::regclass AND conname='broadcasts_changed_signal_id_check') THEN ALTER TABLE public.broadcasts_changed ADD CONSTRAINT broadcasts_changed_signal_id_check CHECK ((signal_id = 1)); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.campaign_participants'::regclass AND conname='campaign_participants_broadcast_id_fkey') THEN ALTER TABLE public.campaign_participants ADD CONSTRAINT campaign_participants_broadcast_id_fkey FOREIGN KEY (broadcast_id) REFERENCES broadcasts(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.campaign_participants'::regclass AND conname='campaign_participants_pkey') THEN ALTER TABLE public.campaign_participants ADD CONSTRAINT campaign_participants_pkey PRIMARY KEY (broadcast_id, user_id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.campaign_participants'::regclass AND conname='campaign_participants_progress_check') THEN ALTER TABLE public.campaign_participants ADD CONSTRAINT campaign_participants_progress_check CHECK ((progress >= 0)); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.campaign_participants'::regclass AND conname='campaign_participants_user_id_fkey') THEN ALTER TABLE public.campaign_participants ADD CONSTRAINT campaign_participants_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.campaign_progress_events'::regclass AND conname='campaign_progress_events_broadcast_id_fkey') THEN ALTER TABLE public.campaign_progress_events ADD CONSTRAINT campaign_progress_events_broadcast_id_fkey FOREIGN KEY (broadcast_id) REFERENCES broadcasts(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.campaign_progress_events'::regclass AND conname='campaign_progress_events_broadcast_id_source_type_source_id_key') THEN ALTER TABLE public.campaign_progress_events ADD CONSTRAINT campaign_progress_events_broadcast_id_source_type_source_id_key UNIQUE (broadcast_id, source_type, source_id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.campaign_progress_events'::regclass AND conname='campaign_progress_events_delta_check') THEN ALTER TABLE public.campaign_progress_events ADD CONSTRAINT campaign_progress_events_delta_check CHECK ((delta > 0)); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.campaign_progress_events'::regclass AND conname='campaign_progress_events_pkey') THEN ALTER TABLE public.campaign_progress_events ADD CONSTRAINT campaign_progress_events_pkey PRIMARY KEY (id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.campaign_progress_events'::regclass AND conname='campaign_progress_events_user_id_fkey') THEN ALTER TABLE public.campaign_progress_events ADD CONSTRAINT campaign_progress_events_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.campaign_rules'::regclass AND conname='campaign_rules_broadcast_id_fkey') THEN ALTER TABLE public.campaign_rules ADD CONSTRAINT campaign_rules_broadcast_id_fkey FOREIGN KEY (broadcast_id) REFERENCES broadcasts(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.campaign_rules'::regclass AND conname='campaign_rules_pkey') THEN ALTER TABLE public.campaign_rules ADD CONSTRAINT campaign_rules_pkey PRIMARY KEY (broadcast_id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.campaign_rules'::regclass AND conname='campaign_rules_target_check') THEN ALTER TABLE public.campaign_rules ADD CONSTRAINT campaign_rules_target_check CHECK (((target > 0) AND (target <= 10000000))); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.campaign_rules'::regclass AND conname='campaign_rules_tiers_check') THEN ALTER TABLE public.campaign_rules ADD CONSTRAINT campaign_rules_tiers_check CHECK ((jsonb_typeof(tiers) = 'array'::text)); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.device_tokens'::regclass AND conname='device_tokens_pkey') THEN ALTER TABLE public.device_tokens ADD CONSTRAINT device_tokens_pkey PRIMARY KEY (id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.device_tokens'::regclass AND conname='device_tokens_platform_check') THEN ALTER TABLE public.device_tokens ADD CONSTRAINT device_tokens_platform_check CHECK ((platform = ANY (ARRAY['android'::text, 'ios'::text, 'web'::text]))); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.device_tokens'::regclass AND conname='device_tokens_token_key') THEN ALTER TABLE public.device_tokens ADD CONSTRAINT device_tokens_token_key UNIQUE (token); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.device_tokens'::regclass AND conname='device_tokens_user_id_fkey') THEN ALTER TABLE public.device_tokens ADD CONSTRAINT device_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.notification_prefs'::regclass AND conname='notification_prefs_pkey') THEN ALTER TABLE public.notification_prefs ADD CONSTRAINT notification_prefs_pkey PRIMARY KEY (user_id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.notification_prefs'::regclass AND conname='notification_prefs_user_id_fkey') THEN ALTER TABLE public.notification_prefs ADD CONSTRAINT notification_prefs_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.promo_claims'::regclass AND conname='promo_claims_broadcast_id_fkey') THEN ALTER TABLE public.promo_claims ADD CONSTRAINT promo_claims_broadcast_id_fkey FOREIGN KEY (broadcast_id) REFERENCES broadcasts(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.promo_claims'::regclass AND conname='promo_claims_broadcast_id_user_id_idempotency_key_key') THEN ALTER TABLE public.promo_claims ADD CONSTRAINT promo_claims_broadcast_id_user_id_idempotency_key_key UNIQUE (broadcast_id, user_id, idempotency_key); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.promo_claims'::regclass AND conname='promo_claims_pkey') THEN ALTER TABLE public.promo_claims ADD CONSTRAINT promo_claims_pkey PRIMARY KEY (id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.promo_claims'::regclass AND conname='promo_claims_user_id_fkey') THEN ALTER TABLE public.promo_claims ADD CONSTRAINT promo_claims_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.promo_rules'::regclass AND conname='promo_rules_broadcast_id_fkey') THEN ALTER TABLE public.promo_rules ADD CONSTRAINT promo_rules_broadcast_id_fkey FOREIGN KEY (broadcast_id) REFERENCES broadcasts(id) ON DELETE CASCADE; END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.promo_rules'::regclass AND conname='promo_rules_budget_used_check') THEN ALTER TABLE public.promo_rules ADD CONSTRAINT promo_rules_budget_used_check CHECK ((budget_used >= (0)::numeric)); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.promo_rules'::regclass AND conname='promo_rules_min_purchase_check') THEN ALTER TABLE public.promo_rules ADD CONSTRAINT promo_rules_min_purchase_check CHECK ((min_purchase >= (0)::numeric)); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.promo_rules'::regclass AND conname='promo_rules_per_user_limit_check') THEN ALTER TABLE public.promo_rules ADD CONSTRAINT promo_rules_per_user_limit_check CHECK (((per_user_limit >= 1) AND (per_user_limit <= 1000))); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.promo_rules'::regclass AND conname='promo_rules_pkey') THEN ALTER TABLE public.promo_rules ADD CONSTRAINT promo_rules_pkey PRIMARY KEY (broadcast_id); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.promo_rules'::regclass AND conname='promo_rules_reward_amount_check') THEN ALTER TABLE public.promo_rules ADD CONSTRAINT promo_rules_reward_amount_check CHECK (((reward_amount >= (0)::numeric) AND (reward_amount <= (100000000)::numeric))); END IF; END $$;

DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.promo_rules'::regclass AND conname='promo_rules_total_budget_cap_check') THEN ALTER TABLE public.promo_rules ADD CONSTRAINT promo_rules_total_budget_cap_check CHECK (((total_budget_cap IS NULL) OR (total_budget_cap >= (0)::numeric))); END IF; END $$;

CREATE UNIQUE INDEX IF NOT EXISTS broadcast_app_config_pkey ON public.broadcast_app_config USING btree (id);

CREATE INDEX IF NOT EXISTS broadcast_audit_log_actor_idx ON public.broadcast_audit_log USING btree (actor_id, created_at DESC);

CREATE INDEX IF NOT EXISTS broadcast_audit_log_broadcast_idx ON public.broadcast_audit_log USING btree (broadcast_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS broadcast_audit_log_pkey ON public.broadcast_audit_log USING btree (id);

CREATE UNIQUE INDEX IF NOT EXISTS broadcast_clicks_pkey ON public.broadcast_clicks USING btree (id);

CREATE INDEX IF NOT EXISTS broadcast_clicks_user_broadcast_idx ON public.broadcast_clicks USING btree (user_id, broadcast_id);

CREATE UNIQUE INDEX IF NOT EXISTS broadcast_daily_rollups_day_broadcast_id_key ON public.broadcast_daily_rollups USING btree (day, broadcast_id);

CREATE UNIQUE INDEX IF NOT EXISTS broadcast_deliveries_broadcast_id_user_id_channel_key ON public.broadcast_deliveries USING btree (broadcast_id, user_id, channel);

CREATE INDEX IF NOT EXISTS broadcast_deliveries_broadcast_status_idx ON public.broadcast_deliveries USING btree (broadcast_id, status);

CREATE UNIQUE INDEX IF NOT EXISTS broadcast_deliveries_pkey ON public.broadcast_deliveries USING btree (id);

CREATE UNIQUE INDEX IF NOT EXISTS broadcast_dismissals_pkey ON public.broadcast_dismissals USING btree (user_id, broadcast_id);

CREATE UNIQUE INDEX IF NOT EXISTS broadcast_impressions_daily_dedupe ON public.broadcast_impressions USING btree (user_id, broadcast_id, date((created_at AT TIME ZONE 'UTC'::text)));

CREATE UNIQUE INDEX IF NOT EXISTS broadcast_impressions_pkey ON public.broadcast_impressions USING btree (id);

CREATE INDEX IF NOT EXISTS broadcast_impressions_user_broadcast_idx ON public.broadcast_impressions USING btree (user_id, broadcast_id);

CREATE UNIQUE INDEX IF NOT EXISTS broadcast_rate_limits_pkey ON public.broadcast_rate_limits USING btree (user_id, action);

CREATE UNIQUE INDEX IF NOT EXISTS broadcast_reads_pkey ON public.broadcast_reads USING btree (user_id, broadcast_id);

CREATE INDEX IF NOT EXISTS broadcasts_audience_gin_idx ON public.broadcasts USING gin (audience);

CREATE INDEX IF NOT EXISTS broadcasts_live_priority_idx ON public.broadcasts USING btree (priority DESC, starts_at) WHERE ((status = 'live'::broadcast_status) AND (deleted_at IS NULL));

CREATE UNIQUE INDEX IF NOT EXISTS broadcasts_pkey ON public.broadcasts USING btree (id);

CREATE INDEX IF NOT EXISTS broadcasts_status_window_idx ON public.broadcasts USING btree (status, starts_at, ends_at) WHERE (deleted_at IS NULL);

CREATE UNIQUE INDEX IF NOT EXISTS broadcasts_changed_pkey ON public.broadcasts_changed USING btree (signal_id);

CREATE UNIQUE INDEX IF NOT EXISTS campaign_participants_pkey ON public.campaign_participants USING btree (broadcast_id, user_id);

CREATE INDEX IF NOT EXISTS campaign_participants_user_idx ON public.campaign_participants USING btree (user_id, broadcast_id);

CREATE UNIQUE INDEX IF NOT EXISTS campaign_progress_events_broadcast_id_source_type_source_id_key ON public.campaign_progress_events USING btree (broadcast_id, source_type, source_id);

CREATE UNIQUE INDEX IF NOT EXISTS campaign_progress_events_pkey ON public.campaign_progress_events USING btree (id);

CREATE INDEX IF NOT EXISTS campaign_progress_events_user_idx ON public.campaign_progress_events USING btree (user_id, broadcast_id);

CREATE UNIQUE INDEX IF NOT EXISTS campaign_rules_pkey ON public.campaign_rules USING btree (broadcast_id);

CREATE UNIQUE INDEX IF NOT EXISTS device_tokens_pkey ON public.device_tokens USING btree (id);

CREATE UNIQUE INDEX IF NOT EXISTS device_tokens_token_key ON public.device_tokens USING btree (token);

CREATE INDEX IF NOT EXISTS device_tokens_user_active_idx ON public.device_tokens USING btree (user_id) WHERE (disabled_at IS NULL);

CREATE UNIQUE INDEX IF NOT EXISTS notification_prefs_pkey ON public.notification_prefs USING btree (user_id);

CREATE UNIQUE INDEX IF NOT EXISTS promo_claims_broadcast_id_user_id_idempotency_key_key ON public.promo_claims USING btree (broadcast_id, user_id, idempotency_key);

CREATE UNIQUE INDEX IF NOT EXISTS promo_claims_pkey ON public.promo_claims USING btree (id);

CREATE INDEX IF NOT EXISTS promo_claims_user_broadcast_idx ON public.promo_claims USING btree (user_id, broadcast_id);

CREATE UNIQUE INDEX IF NOT EXISTS promo_rules_pkey ON public.promo_rules USING btree (broadcast_id);

ALTER TABLE public.broadcast_app_config ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.broadcast_audit_log ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.broadcast_clicks ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.broadcast_daily_rollups ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.broadcast_deliveries ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.broadcast_dismissals ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.broadcast_impressions ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.broadcast_rate_limits ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.broadcast_reads ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.broadcasts ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.broadcasts_changed ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.campaign_participants ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.campaign_progress_events ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.campaign_rules ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.device_tokens ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.notification_prefs ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.promo_claims ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.promo_rules ENABLE ROW LEVEL SECURITY;
