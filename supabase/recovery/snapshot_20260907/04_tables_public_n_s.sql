-- Blink Supabase recovery snapshot: public tables N-S
-- Current-state definitions only; no production rows.

CREATE TABLE IF NOT EXISTS public.notifications (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  actor_id uuid,
  type notification_type_enum NOT NULL,
  post_id uuid,
  text text,
  sub_text text,
  is_read boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  actor_is_vip boolean DEFAULT false NOT NULL,
  vip_priority boolean DEFAULT false NOT NULL
);

CREATE TABLE IF NOT EXISTS public.point_transactions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  action_type text NOT NULL,
  points_delta integer NOT NULL,
  reference_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.poll_options (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  poll_id uuid NOT NULL,
  option_text text NOT NULL,
  "position" integer DEFAULT 0 NOT NULL,
  vote_count integer DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.poll_votes (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  poll_id uuid NOT NULL,
  option_id uuid NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.polls (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  post_id uuid NOT NULL,
  question text NOT NULL,
  allows_multiple boolean DEFAULT false NOT NULL,
  expires_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.post_ads (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  post_id uuid NOT NULL,
  advertiser_id uuid NOT NULL,
  status text DEFAULT 'pending'::text NOT NULL,
  budget numeric DEFAULT 0 NOT NULL,
  spent numeric DEFAULT 0 NOT NULL,
  starts_at timestamp with time zone DEFAULT now() NOT NULL,
  ends_at timestamp with time zone,
  target jsonb DEFAULT '{}'::jsonb NOT NULL,
  impressions bigint DEFAULT 0 NOT NULL,
  clicks bigint DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.post_bookmarks (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  post_id uuid NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.post_likes (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  post_id uuid NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.post_reposts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  post_id uuid NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.post_shares (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  post_id uuid NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  share_type text DEFAULT 'share'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.post_views (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  post_id uuid NOT NULL,
  viewer_id uuid NOT NULL,
  view_weight integer DEFAULT 1 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  impression_count integer DEFAULT 1 NOT NULL,
  weighted_view_count integer DEFAULT 1 NOT NULL,
  last_viewed_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.profile_posts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  username text,
  author_username text,
  author_full_name text,
  author_avatar text,
  kind text DEFAULT 'text'::text,
  text text,
  images text[] DEFAULT '{}'::text[],
  created_at timestamp with time zone DEFAULT now(),
  like_count integer DEFAULT 0,
  comment_count integer DEFAULT 0,
  repost_count integer DEFAULT 0,
  view_count integer DEFAULT 0
);

CREATE TABLE IF NOT EXISTS public.profile_skills (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  skill_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.profile_views (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  profile_id uuid NOT NULL,
  viewer_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.profiles (
  id uuid NOT NULL,
  email text,
  username text NOT NULL,
  full_name text DEFAULT 'Blink User'::text NOT NULL,
  avatar_url text,
  cover_photo text,
  pronouns text,
  university text,
  faculty text,
  department text,
  course_of_study text,
  academic_level text,
  graduation_year integer,
  professional_headline text,
  current_job_title text,
  phone text,
  whatsapp text,
  country_of_origin text,
  current_city_state text,
  campus_hostel_location text,
  bio text,
  favorite_quote text,
  custom_status text,
  availability text DEFAULT 'None'::text NOT NULL,
  website text,
  linkedin text,
  twitter text,
  instagram text,
  featured_link text,
  featured_link_label text,
  core_skills text[] DEFAULT '{}'::text[] NOT NULL,
  hobbies text[] DEFAULT '{}'::text[] NOT NULL,
  languages text[] DEFAULT '{}'::text[] NOT NULL,
  skill_endorsements jsonb DEFAULT '[]'::jsonb NOT NULL,
  badges jsonb DEFAULT '[]'::jsonb NOT NULL,
  is_seller_active boolean DEFAULT false NOT NULL,
  seller_store_name text,
  verification_badge text DEFAULT 'NONE'::text NOT NULL,
  verification_tier verification_tier_enum DEFAULT 'None'::verification_tier_enum NOT NULL,
  is_verified boolean DEFAULT false NOT NULL,
  verified_at timestamp with time zone,
  relationship_status relationship_status_enum DEFAULT 'Single'::relationship_status_enum NOT NULL,
  gender gender_enum,
  current_wallet_balance integer DEFAULT 0 NOT NULL,
  is_online boolean DEFAULT false NOT NULL,
  last_seen timestamp with time zone DEFAULT now() NOT NULL,
  onboarding_completed boolean DEFAULT false NOT NULL,
  posts_count integer DEFAULT 0 NOT NULL,
  follower_count integer DEFAULT 0 NOT NULL,
  following_count integer DEFAULT 0 NOT NULL,
  profile_views_this_week integer DEFAULT 0 NOT NULL,
  name text GENERATED ALWAYS AS (full_name) STORED,
  handle text GENERATED ALWAYS AS (username) STORED,
  world_rank integer,
  campus_rank integer,
  fcm_token text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  cover_photo_url text,
  profile_views_count integer DEFAULT 0 NOT NULL,
  daily_streak integer DEFAULT 0 NOT NULL,
  online_now boolean DEFAULT false NOT NULL,
  last_seen_at timestamp with time zone DEFAULT now() NOT NULL,
  points integer DEFAULT 0 NOT NULL,
  verification_expires_at timestamp with time zone,
  blink_vip_until timestamp with time zone
);

CREATE TABLE IF NOT EXISTS public.reading_mate_profiles (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  courses text[] DEFAULT '{}'::text[] NOT NULL,
  study_style text,
  preferred_times text[] DEFAULT '{}'::text[] NOT NULL,
  preferred_location text,
  description text,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.reading_mate_requests (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  reading_profile_id uuid NOT NULL,
  requester_id uuid DEFAULT auth.uid() NOT NULL,
  message text,
  status text DEFAULT 'pending'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.recommendation_events (
  id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  user_id uuid NOT NULL,
  surface text NOT NULL,
  target_type text NOT NULL,
  target_key text NOT NULL,
  event_type text NOT NULL,
  dwell_ms integer DEFAULT 0 NOT NULL,
  session_id uuid,
  metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.reports (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  reporter_id uuid NOT NULL,
  reported_user_id uuid,
  reported_post_id uuid,
  reason text NOT NULL,
  status text DEFAULT 'pending'::text,
  created_at timestamp with time zone DEFAULT now(),
  reported_comment_id uuid
);

CREATE TABLE IF NOT EXISTS public.repost_point_credits (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  post_id uuid NOT NULL,
  reposter_id uuid NOT NULL,
  actor_id uuid NOT NULL,
  action_type text NOT NULL,
  points_each integer NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.roommate_applications (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  roommate_profile_id uuid NOT NULL,
  applicant_id uuid DEFAULT auth.uid() NOT NULL,
  message text,
  status text DEFAULT 'pending'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.roommate_profiles (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  title text NOT NULL,
  description text,
  location text,
  budget_min numeric,
  budget_max numeric,
  move_in_date date,
  gender_preference text,
  room_type text,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.scheduled_feed_posts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  payload jsonb NOT NULL,
  scheduled_for timestamp with time zone NOT NULL,
  status text DEFAULT 'pending'::text NOT NULL,
  published_post_id uuid,
  error_message text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  published_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS public.skill_endorsements (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  skill_id uuid NOT NULL,
  profile_user_id uuid NOT NULL,
  endorser_user_id uuid DEFAULT auth.uid() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.skills (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  name text NOT NULL,
  normalized_name text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.statuses (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  media_url text,
  caption text,
  is_active boolean DEFAULT true NOT NULL,
  views_count integer DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  expires_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS public.stories (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  active boolean DEFAULT true,
  views_count integer DEFAULT 0,
  created_at timestamp with time zone DEFAULT now(),
  media_url text,
  media_type text DEFAULT 'image'::text,
  caption text,
  text text,
  image_url text,
  video_url text,
  background text,
  audio_title text,
  likes_count integer DEFAULT 0 NOT NULL
);

CREATE TABLE IF NOT EXISTS public.story_interactions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  status_id uuid NOT NULL,
  user_id uuid NOT NULL,
  type text NOT NULL,
  reply_text text,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.story_likes (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  story_id uuid NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.story_reactions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  story_id uuid NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  reaction_type text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.story_replies (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  story_id uuid NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  content text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.story_views (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  story_id uuid NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.study_circle_members (
  circle_id uuid NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  role text DEFAULT 'member'::text NOT NULL,
  joined_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.study_circle_requests (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  circle_id uuid NOT NULL,
  user_id uuid DEFAULT auth.uid() NOT NULL,
  status text DEFAULT 'pending'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.study_circles (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  owner_id uuid DEFAULT auth.uid() NOT NULL,
  name text NOT NULL,
  description text,
  faculty text,
  course text,
  max_members integer DEFAULT 50 NOT NULL,
  is_private boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);
