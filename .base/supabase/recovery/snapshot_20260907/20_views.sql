-- Blink Supabase recovery snapshot: views
-- All views use security_invoker=true so underlying RLS remains authoritative.

CREATE OR REPLACE VIEW public.game_leaderboard WITH (security_invoker=true) AS
 SELECT p.id AS user_id,
    COALESCE(p.points, 0)::bigint AS score,
    COALESCE(gp.coins, 0::bigint) AS coins,
    COALESCE(gp.streak, 0) AS streak,
    COALESCE(gp.best_streak, 0) AS best_streak,
    COALESCE(NULLIF(p.name, ''::text), NULLIF(p.full_name, ''::text), p.username) AS name,
    p.username,
    NULLIF(p.avatar_url, ''::text) AS avatar_url,
    NULLIF(NULLIF(p.university, ''::text), 'null'::text) AS university,
    rank() OVER (ORDER BY (COALESCE(p.points, 0)) DESC, p.created_at) AS world_rank,
    p.faculty,
    p.academic_level
   FROM public.profiles p
     LEFT JOIN public.game_profiles gp ON gp.user_id = p.id
  WHERE NULLIF(TRIM(BOTH FROM p.username), ''::text) IS NOT NULL
  ORDER BY (COALESCE(p.points, 0)) DESC, p.created_at;

CREATE OR REPLACE VIEW public.game_rankings WITH (security_invoker=true) AS
 SELECT p.id AS user_id,
    COALESCE(gp.score, 0::bigint) AS score,
    COALESCE(gp.coins, 0::bigint) AS coins,
    COALESCE(gp.streak, 0) AS streak,
    COALESCE(gp.best_streak, 0) AS best_streak,
    COALESCE(NULLIF(p.name, ''::text), NULLIF(p.full_name, ''::text), p.username) AS name,
    p.username,
    NULLIF(p.avatar_url, ''::text) AS avatar_url,
    NULLIF(NULLIF(p.university, ''::text), 'null'::text) AS university,
    rank() OVER (ORDER BY (COALESCE(gp.score, 0::bigint)) DESC, gp.updated_at, p.created_at) AS world_rank,
    p.faculty,
    p.academic_level,
    p.verification_badge
   FROM public.profiles p
     LEFT JOIN public.game_profiles gp ON gp.user_id = p.id
  WHERE NULLIF(TRIM(BOTH FROM p.username), ''::text) IS NOT NULL
  ORDER BY (COALESCE(gp.score, 0::bigint)) DESC, gp.updated_at, p.created_at;

CREATE OR REPLACE VIEW public.posts WITH (security_invoker=true) AS
 SELECT id,
    user_id AS author_id,
    text AS content,
    image_url AS media_url,
    hashtags,
    like_count AS likes_count,
    comment_count AS comments_count,
    share_count AS reposts_count,
    view_count AS views_count,
    engagement_score,
    visibility_weight,
    is_original,
    is_flagged,
    is_active,
    created_at,
    updated_at
   FROM public.feed_posts fp;
