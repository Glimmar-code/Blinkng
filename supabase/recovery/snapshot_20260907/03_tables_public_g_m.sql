-- Blink Supabase recovery snapshot: public tables G-M
-- Current-state definitions only; no production rows.

CREATE TABLE IF NOT EXISTS public.game_attempts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  round_id uuid NOT NULL,
  question_id text NOT NULL,
  selected_index integer NOT NULL,
  correct boolean NOT NULL,
  score_awarded integer DEFAULT 0 NOT NULL,
  coins_awarded integer DEFAULT 0 NOT NULL,
  response_ms integer,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.game_challenges (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  challenger_id uuid DEFAULT auth.uid() NOT NULL,
  opponent_id uuid NOT NULL,
  game_type text NOT NULL,
  status text DEFAULT 'pending'::text NOT NULL,
  challenger_score integer,
  opponent_score integer,
  winner_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  accepted_at timestamp with time zone,
  completed_at timestamp with time zone,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.game_coin_ledger (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  round_id uuid,
  question_id text,
  delta integer NOT NULL,
  reason text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.game_profiles (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  score bigint DEFAULT 0 NOT NULL,
  coins bigint DEFAULT 0 NOT NULL,
  streak integer DEFAULT 0 NOT NULL,
  best_streak integer DEFAULT 0 NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.game_question_reports (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  question_id text NOT NULL,
  reason text NOT NULL,
  details text DEFAULT ''::text NOT NULL,
  status text DEFAULT 'open'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.game_questions (
  id text NOT NULL,
  game_type text NOT NULL,
  category text NOT NULL,
  prompt text NOT NULL,
  options text[] NOT NULL,
  correct_index integer NOT NULL,
  explanation text DEFAULT ''::text NOT NULL,
  difficulty smallint DEFAULT 1 NOT NULL,
  time_limit_seconds integer,
  stimulus text,
  is_daily boolean DEFAULT false NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.game_rewards (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  reward_type text NOT NULL,
  amount integer DEFAULT 0 NOT NULL,
  source text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.game_rounds (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  game_type text NOT NULL,
  question_ids text[] NOT NULL,
  question_count integer NOT NULL,
  score integer DEFAULT 0 NOT NULL,
  coins_earned integer DEFAULT 0 NOT NULL,
  correct_count integer DEFAULT 0 NOT NULL,
  status text DEFAULT 'active'::text NOT NULL,
  challenge_id uuid,
  is_daily boolean DEFAULT false NOT NULL,
  started_at timestamp with time zone DEFAULT now() NOT NULL,
  completed_at timestamp with time zone,
  expires_at timestamp with time zone DEFAULT (now() + '02:00:00'::interval) NOT NULL
);

CREATE TABLE IF NOT EXISTS public.game_saved_questions (
  user_id uuid DEFAULT auth.uid() NOT NULL,
  question_id text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.game_sessions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  game_type text NOT NULL,
  score integer DEFAULT 0 NOT NULL,
  coins_earned integer DEFAULT 0 NOT NULL,
  started_at timestamp with time zone DEFAULT now() NOT NULL,
  completed_at timestamp with time zone,
  round_id uuid
);

CREATE TABLE IF NOT EXISTS public.housing_agent_profiles (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  business_name text NOT NULL,
  service_areas text[] DEFAULT '{}'::text[] NOT NULL,
  bio text,
  is_verified boolean DEFAULT false NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.housing_request_applications (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  housing_request_id uuid NOT NULL,
  agent_profile_id uuid NOT NULL,
  message text,
  status text DEFAULT 'pending'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.housing_requests (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  student_id uuid DEFAULT auth.uid() NOT NULL,
  agent_id uuid,
  title text NOT NULL,
  preferred_location text,
  budget_min numeric,
  budget_max numeric,
  description text,
  status text DEFAULT 'open'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.interactions (
  user_id uuid NOT NULL,
  post_id uuid NOT NULL,
  type interaction_type_enum NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.leaderboard_snapshots (
  id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  user_id uuid NOT NULL,
  name text NOT NULL,
  handle text NOT NULL,
  university text,
  avatar_url text,
  verification_tier verification_tier_enum DEFAULT 'None'::verification_tier_enum NOT NULL,
  world_score bigint DEFAULT 0 NOT NULL,
  world_rank integer,
  campus_score bigint DEFAULT 0 NOT NULL,
  campus_rank integer,
  snapshot_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.market_items (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  title text DEFAULT 'Campus Item'::text NOT NULL,
  price bigint DEFAULT 0 NOT NULL,
  category text DEFAULT 'Electronics'::text NOT NULL,
  condition text DEFAULT 'Like New'::text NOT NULL,
  description text DEFAULT ''::text NOT NULL,
  image_url text,
  seller_username text DEFAULT 'campus_seller'::text NOT NULL,
  seller_name text DEFAULT 'Campus Seller'::text NOT NULL,
  seller_avatar text,
  seller_phone text DEFAULT '+234 812 345 6789'::text NOT NULL,
  seller_whatsapp text DEFAULT '+2348123456789'::text NOT NULL,
  seller_is_verified boolean DEFAULT false,
  seller_rating double precision DEFAULT 4.8,
  seller_review_count integer DEFAULT 0,
  university text DEFAULT 'University of Lagos'::text NOT NULL,
  location text DEFAULT 'Akoka Campus, Lagos'::text NOT NULL,
  is_featured boolean DEFAULT false,
  is_sold boolean DEFAULT false,
  created_at timestamp with time zone DEFAULT now(),
  seller_id uuid,
  currency text DEFAULT 'NGN'::text NOT NULL,
  image_urls text[] DEFAULT '{}'::text[] NOT NULL,
  quantity integer DEFAULT 1 NOT NULL,
  status text DEFAULT 'active'::text NOT NULL
);

CREATE TABLE IF NOT EXISTS public.marketplace_inquiries (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  item_id uuid NOT NULL,
  buyer_id uuid DEFAULT auth.uid() NOT NULL,
  seller_id uuid NOT NULL,
  message text,
  status text DEFAULT 'pending'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.marketplace_orders (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  item_id uuid NOT NULL,
  buyer_id uuid DEFAULT auth.uid() NOT NULL,
  seller_id uuid NOT NULL,
  quantity integer DEFAULT 1 NOT NULL,
  unit_price numeric NOT NULL,
  total_price numeric NOT NULL,
  currency text DEFAULT 'NGN'::text NOT NULL,
  status text DEFAULT 'pending'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.marketplace_profiles (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  store_name text NOT NULL,
  bio text,
  is_active boolean DEFAULT true NOT NULL,
  verified_seller boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.marketplace_reviews (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  order_id uuid NOT NULL,
  reviewer_id uuid DEFAULT auth.uid() NOT NULL,
  reviewee_id uuid NOT NULL,
  rating integer NOT NULL,
  comment text,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.marketplace_wishlist (
  user_id uuid DEFAULT auth.uid() NOT NULL,
  item_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.mentor_profiles (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  mode text DEFAULT 'mentor'::text NOT NULL,
  subjects text[] DEFAULT '{}'::text[] NOT NULL,
  headline text,
  description text,
  preferred_level text,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.mentor_requests (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  mentor_profile_id uuid NOT NULL,
  requester_id uuid DEFAULT auth.uid() NOT NULL,
  message text,
  status text DEFAULT 'pending'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.message_pins (
  message_id uuid NOT NULL,
  conversation_id uuid NOT NULL,
  pinned_by uuid NOT NULL,
  pinned_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.message_push_dispatches (
  message_id uuid NOT NULL,
  status text DEFAULT 'sending'::text NOT NULL,
  attempts integer DEFAULT 1 NOT NULL,
  last_error text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.message_reactions (
  message_id uuid NOT NULL,
  user_id uuid NOT NULL,
  emoji text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.message_reports (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  message_id uuid NOT NULL,
  reporter_id uuid NOT NULL,
  reason text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.message_user_state (
  message_id uuid NOT NULL,
  user_id uuid NOT NULL,
  is_starred boolean DEFAULT false NOT NULL,
  is_hidden boolean DEFAULT false NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.messages (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  conversation_id uuid NOT NULL,
  sender_id uuid NOT NULL,
  content text DEFAULT ''::text NOT NULL,
  media_url text,
  is_read boolean DEFAULT false NOT NULL,
  reply_to uuid,
  deleted_for_everyone boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  message_type text DEFAULT 'text'::text NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  reply_to_message_id uuid,
  delivered_at timestamp with time zone,
  read_at timestamp with time zone,
  edited_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS public.messages_compat (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  sender_username text NOT NULL,
  receiver_username text NOT NULL,
  text text DEFAULT ''::text NOT NULL,
  created_at timestamp with time zone DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.muted_users (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  muted_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);
