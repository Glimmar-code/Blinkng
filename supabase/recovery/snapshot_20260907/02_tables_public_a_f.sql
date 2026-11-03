-- Blink Supabase recovery snapshot: public tables A-F
-- Current-state definitions only; no production rows.

CREATE TABLE IF NOT EXISTS public.activities (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  recipient_id uuid NOT NULL,
  actor_id uuid,
  activity_type text NOT NULL,
  entity_type text,
  entity_id uuid,
  message text,
  is_read boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.blink_boost_touches (
  boost_id uuid NOT NULL,
  viewer_id uuid NOT NULL,
  last_touched_at timestamp with time zone DEFAULT now() NOT NULL,
  profile_visit_at timestamp with time zone,
  follow_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS public.blink_boosts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  inventory_id uuid,
  content_id uuid NOT NULL,
  content_type text NOT NULL,
  multiplier integer NOT NULL,
  starts_at timestamp with time zone DEFAULT now() NOT NULL,
  ends_at timestamp with time zone NOT NULL,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  extra_impressions bigint DEFAULT 0 NOT NULL,
  profile_visits bigint DEFAULT 0 NOT NULL,
  followers_attributed bigint DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.blink_coin_gifts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  sender_id uuid NOT NULL,
  receiver_id uuid NOT NULL,
  amount bigint NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.blink_coin_transactions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  kind text NOT NULL,
  catalog_id text,
  item_name text NOT NULL,
  amount bigint NOT NULL,
  balance_after bigint NOT NULL,
  metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.blink_digital_gifts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  sender_id uuid NOT NULL,
  recipient_id uuid NOT NULL,
  inventory_id uuid,
  message text,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.blink_equipped_items (
  user_id uuid NOT NULL,
  slot text NOT NULL,
  inventory_id uuid NOT NULL,
  catalog_id text NOT NULL,
  equipped_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.blink_inventory (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  catalog_id text NOT NULL,
  quantity integer DEFAULT 1 NOT NULL,
  status text DEFAULT 'AVAILABLE'::text NOT NULL,
  purchased_at timestamp with time zone DEFAULT now() NOT NULL,
  activated_at timestamp with time zone,
  expires_at timestamp with time zone,
  target_type text,
  target_id uuid,
  boost_multiplier integer,
  gifted_by uuid,
  metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.blink_item_activations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  inventory_id uuid,
  catalog_id text NOT NULL,
  target_type text,
  target_id uuid,
  started_at timestamp with time zone DEFAULT now() NOT NULL,
  expires_at timestamp with time zone,
  ended_at timestamp with time zone,
  metadata jsonb DEFAULT '{}'::jsonb NOT NULL
);

CREATE TABLE IF NOT EXISTS public.blink_store_catalog (
  id text NOT NULL,
  name text NOT NULL,
  description text NOT NULL,
  icon_key text NOT NULL,
  category text NOT NULL,
  price integer NOT NULL,
  item_type text NOT NULL,
  target_type text DEFAULT 'NONE'::text NOT NULL,
  duration_seconds bigint,
  stackable boolean DEFAULT false NOT NULL,
  vip_only boolean DEFAULT false NOT NULL,
  boost_multipliers integer[] DEFAULT '{}'::integer[] NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  sort_order integer DEFAULT 0 NOT NULL,
  metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.blink_vip_benefit_balances (
  pass_id uuid NOT NULL,
  user_id uuid NOT NULL,
  post_boosts_2x integer DEFAULT 2 NOT NULL,
  reel_boosts_2x integer DEFAULT 2 NOT NULL,
  profile_spotlights integer DEFAULT 1 NOT NULL,
  post_spotlights integer DEFAULT 1 NOT NULL,
  reel_spotlights integer DEFAULT 1 NOT NULL,
  marketplace_highlights integer DEFAULT 1 NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.blink_vip_claims (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  pass_id uuid,
  claim_key text NOT NULL,
  claim_date date,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.blink_vip_passes (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  inventory_id uuid,
  starts_at timestamp with time zone NOT NULL,
  expires_at timestamp with time zone NOT NULL,
  auto_renew boolean DEFAULT false NOT NULL,
  expiry_reminder_sent boolean DEFAULT false NOT NULL,
  gifted_by uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.blocks (
  blocker_id uuid NOT NULL,
  blocked_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.call_push_dispatches (
  call_id uuid NOT NULL,
  event text NOT NULL,
  target_user_id uuid NOT NULL,
  status text DEFAULT 'sending'::text NOT NULL,
  attempts integer DEFAULT 1 NOT NULL,
  last_error text,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.call_signals (
  id bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  call_id uuid NOT NULL,
  sender_id uuid NOT NULL,
  kind text NOT NULL,
  payload jsonb DEFAULT '{}'::jsonb NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.calls (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  conversation_id uuid NOT NULL,
  caller_id uuid NOT NULL,
  callee_id uuid NOT NULL,
  call_type text NOT NULL,
  status text DEFAULT 'ringing'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  ringing_at timestamp with time zone DEFAULT now() NOT NULL,
  answered_at timestamp with time zone,
  connected_at timestamp with time zone,
  ended_at timestamp with time zone,
  timeout_at timestamp with time zone DEFAULT (now() + '00:00:45'::interval) NOT NULL,
  ended_by uuid,
  end_reason text,
  duration_seconds integer DEFAULT 0 NOT NULL
);

CREATE TABLE IF NOT EXISTS public.comment_likes (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  comment_id uuid NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.comment_mentions (
  comment_id uuid NOT NULL,
  mentioned_user_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.comment_replies (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  comment_id uuid NOT NULL,
  author_id uuid NOT NULL,
  content text DEFAULT ''::text NOT NULL,
  likes_count integer DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.comments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  post_id uuid NOT NULL,
  author_id uuid DEFAULT auth.uid() NOT NULL,
  content text NOT NULL,
  parent_comment_id uuid,
  likes_count integer DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.connect_applications (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  listing_id uuid NOT NULL,
  applicant_id uuid DEFAULT auth.uid() NOT NULL,
  message text DEFAULT ''::text NOT NULL,
  status text DEFAULT 'pending'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.connect_category_catalog (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  slug text NOT NULL,
  title text NOT NULL,
  description text DEFAULT ''::text NOT NULL,
  route_kind text NOT NULL,
  icon_key text DEFAULT 'people'::text NOT NULL,
  display_order smallint NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.connect_listings (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  listing_type text NOT NULL,
  title text NOT NULL,
  description text DEFAULT ''::text NOT NULL,
  university text,
  department text,
  academic_level text,
  location text,
  budget_min numeric,
  budget_max numeric,
  subjects text[] DEFAULT '{}'::text[] NOT NULL,
  tags text[] DEFAULT '{}'::text[] NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.connect_match_spins (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  matched_user_id uuid NOT NULL,
  filters jsonb DEFAULT '{}'::jsonb NOT NULL,
  type_prompt text,
  coins_spent integer DEFAULT 10 NOT NULL,
  compatibility_score integer DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.connection_requests (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  sender_id uuid DEFAULT auth.uid() NOT NULL,
  receiver_id uuid NOT NULL,
  status text DEFAULT 'pending'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.conversation_participants (
  conversation_id uuid NOT NULL,
  user_id uuid NOT NULL,
  last_read_at timestamp with time zone DEFAULT now() NOT NULL,
  is_admin boolean DEFAULT false NOT NULL,
  joined_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.conversation_reports (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  conversation_id uuid NOT NULL,
  reporter_id uuid NOT NULL,
  reason text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.conversation_settings (
  conversation_id uuid NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  archived boolean DEFAULT false NOT NULL,
  muted_until timestamp with time zone,
  disappearing_seconds integer DEFAULT 0 NOT NULL,
  theme text DEFAULT 'default'::text NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.conversation_user_state (
  conversation_id uuid NOT NULL,
  user_id uuid NOT NULL,
  cleared_at timestamp with time zone,
  is_muted boolean DEFAULT false NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.conversations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  is_group boolean DEFAULT false NOT NULL,
  created_by uuid,
  last_message_at timestamp with time zone DEFAULT now(),
  title text,
  avatar_url text,
  description text
);

CREATE TABLE IF NOT EXISTS public.fcm_tokens (
  id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  user_id uuid NOT NULL,
  token text NOT NULL,
  device_id text,
  platform text DEFAULT 'android'::text NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.feed_posts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  type text DEFAULT 'text'::text NOT NULL,
  faculty text,
  text text,
  caption text,
  image_url text,
  video_url text,
  gradient jsonb,
  hashtags text[] DEFAULT '{}'::text[] NOT NULL,
  like_count integer DEFAULT 0 NOT NULL,
  comment_count integer DEFAULT 0 NOT NULL,
  share_count integer DEFAULT 0 NOT NULL,
  view_count integer DEFAULT 0 NOT NULL,
  is_reel boolean DEFAULT false NOT NULL,
  is_original boolean DEFAULT true NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  is_flagged boolean DEFAULT false NOT NULL,
  report_count integer DEFAULT 0 NOT NULL,
  engagement_score numeric DEFAULT 0 NOT NULL,
  visibility_weight numeric DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  tags text[] DEFAULT '{}'::text[] NOT NULL,
  mentions uuid[] DEFAULT '{}'::uuid[] NOT NULL,
  poll_id uuid,
  audience text DEFAULT 'Everyone'::text NOT NULL,
  category text DEFAULT 'Campus Life'::text NOT NULL,
  location text,
  link_url text,
  allow_comments boolean DEFAULT true NOT NULL,
  hide_likes boolean DEFAULT false NOT NULL,
  is_pinned boolean DEFAULT false NOT NULL,
  is_disappearing boolean DEFAULT false NOT NULL,
  audio_title text,
  alt_text text,
  images text[] DEFAULT '{}'::text[] NOT NULL,
  expires_at timestamp with time zone,
  is_sponsored boolean DEFAULT false NOT NULL,
  ad_label text,
  ad_cta text,
  creator_post_number integer NOT NULL,
  repost_count integer DEFAULT 0 NOT NULL
);

CREATE TABLE IF NOT EXISTS public.feed_preferences (
  user_id uuid DEFAULT auth.uid() NOT NULL,
  post_id uuid NOT NULL,
  preference text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.follows (
  follower_id uuid NOT NULL,
  following_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);
