-- Blink Supabase recovery snapshot: extensions, schemas, enums
-- Generated from live project jhwgifrlxwspoedxjaly on 2026-09-07.
-- Recovery-only: do not auto-run against production.

-- Extensions
CREATE EXTENSION IF NOT EXISTS pg_cron WITH SCHEMA pg_catalog;
CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS pg_stat_statements WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS supabase_vault WITH SCHEMA vault;
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA extensions;

-- Schemas
CREATE SCHEMA IF NOT EXISTS private;
CREATE SCHEMA IF NOT EXISTS private_ranking;

-- Enums
DO $ddl$ BEGIN CREATE TYPE public.discovery_verification_tier_enum AS ENUM ('standard', 'blue', 'gold'); EXCEPTION WHEN duplicate_object THEN NULL; END $ddl$;
DO $ddl$ BEGIN CREATE TYPE public.dm_privacy_enum AS ENUM ('everyone', 'following', 'nobody'); EXCEPTION WHEN duplicate_object THEN NULL; END $ddl$;
DO $ddl$ BEGIN CREATE TYPE public.gender_enum AS ENUM ('Male', 'Female', 'Prefer not to say'); EXCEPTION WHEN duplicate_object THEN NULL; END $ddl$;
DO $ddl$ BEGIN CREATE TYPE public.interaction_type_enum AS ENUM ('like', 'repost', 'save'); EXCEPTION WHEN duplicate_object THEN NULL; END $ddl$;
DO $ddl$ BEGIN CREATE TYPE public.notification_type_enum AS ENUM ('like', 'comment', 'repost', 'follow', 'mention', 'tip', 'verification', 'leaderboard', 'system'); EXCEPTION WHEN duplicate_object THEN NULL; END $ddl$;
DO $ddl$ BEGIN CREATE TYPE public.relationship_status_enum AS ENUM ('Single', 'Taken', 'Private', 'Married', 'It''s complicated', 'Prefer not to say'); EXCEPTION WHEN duplicate_object THEN NULL; END $ddl$;
DO $ddl$ BEGIN CREATE TYPE public.theme_enum AS ENUM ('system', 'light', 'dark'); EXCEPTION WHEN duplicate_object THEN NULL; END $ddl$;
DO $ddl$ BEGIN CREATE TYPE public.transaction_type_enum AS ENUM ('like_reward', 'comment_reward', 'repost_reward', 'view_reward', 'tip', 'verification_purchase', 'ad_reward', 'welcome_bonus', 'leaderboard_reward', 'withdrawal', 'penalty'); EXCEPTION WHEN duplicate_object THEN NULL; END $ddl$;
DO $ddl$ BEGIN CREATE TYPE public.verification_tier_enum AS ENUM ('None', 'Standard', 'Gold'); EXCEPTION WHEN duplicate_object THEN NULL; END $ddl$;
