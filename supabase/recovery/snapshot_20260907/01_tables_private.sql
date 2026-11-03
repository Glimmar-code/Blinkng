-- Blink Supabase recovery snapshot: private/private_ranking tables
-- Current-state definitions only; no production rows.

CREATE TABLE IF NOT EXISTS private.admin_action_reversals (
  action_id uuid NOT NULL,
  reversed_by uuid NOT NULL,
  reversed_at timestamp with time zone DEFAULT now() NOT NULL,
  note text,
  result jsonb DEFAULT '{}'::jsonb NOT NULL
);

CREATE TABLE IF NOT EXISTS private.admin_announcements (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  sender_id uuid NOT NULL,
  message text NOT NULL,
  target_user_id uuid,
  target_university text,
  verification_filter text DEFAULT 'all'::text NOT NULL,
  delivered_count integer DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private.admin_audit_log (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  actor_id uuid NOT NULL,
  action text NOT NULL,
  target_user_id uuid,
  target_post_id uuid,
  details jsonb DEFAULT '{}'::jsonb NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private.admin_coin_transactions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  actor_id uuid NOT NULL,
  user_id uuid NOT NULL,
  amount bigint NOT NULL,
  transaction_type text NOT NULL,
  reason text,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private.admin_comment_state (
  comment_id uuid NOT NULL,
  original_content text,
  is_removed boolean DEFAULT false NOT NULL,
  is_hidden boolean DEFAULT false NOT NULL,
  updated_by uuid,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private.admin_feature_catalog (
  feature_id integer NOT NULL,
  title text NOT NULL,
  category text NOT NULL,
  owner_only boolean DEFAULT false NOT NULL,
  enabled boolean DEFAULT true NOT NULL
);

CREATE TABLE IF NOT EXISTS private.admin_feature_registry_v2 (
  feature_id integer NOT NULL,
  title text NOT NULL,
  category text NOT NULL,
  module text NOT NULL,
  route_key text NOT NULL,
  target_type text DEFAULT 'none'::text NOT NULL,
  input_kind text DEFAULT 'none'::text NOT NULL,
  owner_only boolean DEFAULT false NOT NULL,
  enabled boolean DEFAULT true NOT NULL,
  reversible boolean DEFAULT false NOT NULL,
  description text DEFAULT ''::text NOT NULL,
  section_key text,
  permission_key text,
  risk_level text DEFAULT 'low'::text NOT NULL,
  confirmation_kind text DEFAULT 'none'::text NOT NULL
);

CREATE TABLE IF NOT EXISTS private.admin_notes (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  actor_id uuid NOT NULL,
  target_user_id uuid,
  target_admin_id uuid,
  note text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private.admin_notification_campaigns (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  sender_id uuid NOT NULL,
  title text DEFAULT 'Blink'::text NOT NULL,
  message text DEFAULT ''::text NOT NULL,
  image_url text,
  action_label text,
  action_url text,
  link_type text,
  link_id uuid,
  audience jsonb DEFAULT '{"type": "all"}'::jsonb NOT NULL,
  scheduled_at timestamp with time zone,
  status text DEFAULT 'draft'::text NOT NULL,
  delivered_count integer DEFAULT 0 NOT NULL,
  opened_count integer DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  subtopic text
);

CREATE TABLE IF NOT EXISTS private.admin_notification_deliveries (
  campaign_id uuid NOT NULL,
  user_id uuid NOT NULL,
  notification_id uuid,
  delivered_at timestamp with time zone DEFAULT now() NOT NULL,
  opened_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS private.admin_report_state (
  report_id uuid NOT NULL,
  assigned_to uuid,
  severity text DEFAULT 'normal'::text NOT NULL,
  escalated boolean DEFAULT false NOT NULL,
  admin_notes text,
  resolved_at timestamp with time zone,
  updated_by uuid,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private.admin_role_templates (
  template_key text NOT NULL,
  label text NOT NULL,
  permissions text[] NOT NULL,
  can_manage_admins boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private.admin_roles (
  user_id uuid NOT NULL,
  role text NOT NULL,
  granted_by uuid,
  granted_at timestamp with time zone DEFAULT now() NOT NULL,
  expires_at timestamp with time zone,
  role_label text DEFAULT 'admin'::text NOT NULL,
  permissions text[] DEFAULT ARRAY['users'::text, 'coins'::text, 'verification'::text, 'content'::text, 'messages'::text, 'analytics'::text] NOT NULL,
  scope_universities text[] DEFAULT '{}'::text[] NOT NULL,
  suspended_until timestamp with time zone,
  can_manage_admins boolean DEFAULT false NOT NULL
);

CREATE TABLE IF NOT EXISTS private.admin_sections_v3 (
  section_key text NOT NULL,
  title text NOT NULL,
  description text DEFAULT ''::text NOT NULL,
  sort_order integer NOT NULL,
  show_in_sidebar boolean DEFAULT true NOT NULL,
  is_top_action boolean DEFAULT false NOT NULL,
  enabled boolean DEFAULT true NOT NULL
);

CREATE TABLE IF NOT EXISTS private.admin_system_config (
  key text NOT NULL,
  value jsonb NOT NULL,
  updated_by uuid,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private.admin_university_catalog (
  name text NOT NULL,
  source text DEFAULT 'android:NigerianUniversities'::text NOT NULL,
  active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private.admin_user_controls (
  user_id uuid NOT NULL,
  is_banned boolean DEFAULT false NOT NULL,
  banned_until timestamp with time zone,
  suspended_until timestamp with time zone,
  post_restricted_until timestamp with time zone,
  comment_restricted_until timestamp with time zone,
  message_restricted_until timestamp with time zone,
  reel_restricted_until timestamp with time zone,
  marketplace_restricted_until timestamp with time zone,
  follow_restricted_until timestamp with time zone,
  under_review boolean DEFAULT false NOT NULL,
  trusted boolean DEFAULT false NOT NULL,
  suspicious boolean DEFAULT false NOT NULL,
  account_locked_until timestamp with time zone,
  account_disabled_until timestamp with time zone,
  deletion_requested_at timestamp with time zone,
  coin_frozen boolean DEFAULT false NOT NULL,
  warning_count integer DEFAULT 0 NOT NULL,
  last_warning text,
  updated_by uuid,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private.content_view_events (
  event_id uuid NOT NULL,
  viewer_id uuid NOT NULL,
  post_id uuid NOT NULL,
  content_type text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  view_weight smallint DEFAULT 1 NOT NULL
);

CREATE TABLE IF NOT EXISTS private.user_streak_state (
  user_id uuid NOT NULL,
  last_active_date date,
  current_streak integer DEFAULT 0 NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private.web_content_views (
  post_id uuid NOT NULL,
  visitor_hash text NOT NULL,
  impression_count integer DEFAULT 0 NOT NULL,
  last_viewed_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private_ranking.discovery_interest_cache (
  user_id uuid NOT NULL,
  positive_interests text[] DEFAULT '{}'::text[] NOT NULL,
  negative_interests text[] DEFAULT '{}'::text[] NOT NULL,
  positive_creators uuid[] DEFAULT '{}'::uuid[] NOT NULL,
  negative_creators uuid[] DEFAULT '{}'::uuid[] NOT NULL,
  tag_weights jsonb DEFAULT '{}'::jsonb NOT NULL,
  creator_weights jsonb DEFAULT '{}'::jsonb NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private_ranking.discovery_reel_metrics (
  post_id uuid NOT NULL,
  completion_samples bigint DEFAULT 0 NOT NULL,
  completion_rate numeric(6,5) DEFAULT 0 NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private_ranking.discovery_user_geo (
  user_id uuid NOT NULL,
  location geography(Point,4326) NOT NULL,
  accuracy_meters numeric(10,2),
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS private_ranking.discovery_verification_multipliers (
  tier discovery_verification_tier_enum NOT NULL,
  multiplier numeric(6,3) NOT NULL
);
