-- Blink Supabase recovery snapshot: public tables T-Z
-- Current-state definitions only; no production rows.

CREATE TABLE IF NOT EXISTS public.token_transactions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  recipient_id uuid,
  sender_id uuid,
  type transaction_type_enum NOT NULL,
  amount numeric(20,2) NOT NULL,
  post_id uuid,
  description text,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.user_balances (
  user_id uuid NOT NULL,
  lifetime_engagement_score bigint DEFAULT 0 NOT NULL,
  spendable_coin_balance numeric(20,2) DEFAULT 0 NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.user_devices (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  fcm_token text NOT NULL,
  device_platform text,
  last_active_at timestamp with time zone DEFAULT now(),
  created_at timestamp with time zone DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.user_interest_weights (
  user_id uuid NOT NULL,
  surface text NOT NULL,
  feature_type text NOT NULL,
  feature_value text NOT NULL,
  weight numeric(10,4) DEFAULT 0 NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.user_settings (
  user_id uuid NOT NULL,
  theme theme_enum DEFAULT 'system'::theme_enum,
  language text DEFAULT 'en'::text,
  push_notifs_enabled boolean DEFAULT true,
  email_notifs_enabled boolean DEFAULT true,
  dm_privacy dm_privacy_enum DEFAULT 'everyone'::dm_privacy_enum,
  updated_at timestamp with time zone DEFAULT now(),
  private_account boolean DEFAULT false NOT NULL,
  show_online_status boolean DEFAULT true NOT NULL,
  read_receipts boolean DEFAULT true NOT NULL,
  autoplay_videos boolean DEFAULT true NOT NULL,
  data_saver boolean DEFAULT false NOT NULL,
  reduce_motion boolean DEFAULT false NOT NULL
);

CREATE TABLE IF NOT EXISTS public.verification_payments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  verification_request_id uuid NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  provider text DEFAULT 'paystack'::text NOT NULL,
  provider_reference text,
  amount numeric NOT NULL,
  currency text DEFAULT 'NGN'::text NOT NULL,
  status text DEFAULT 'pending'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.verification_requests (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  verification_type text NOT NULL,
  status text DEFAULT 'pending'::text NOT NULL,
  submitted_data jsonb,
  review_notes text,
  submitted_at timestamp with time zone DEFAULT now() NOT NULL,
  reviewed_at timestamp with time zone,
  reviewed_by uuid
);

CREATE TABLE IF NOT EXISTS public.verifications (
  id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  user_id uuid NOT NULL,
  tier verification_tier_enum DEFAULT 'None'::verification_tier_enum NOT NULL,
  expires_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);
