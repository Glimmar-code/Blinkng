-- The public profile table uses owner-only RLS. A security-invoker view
-- directly over profiles sees only the caller, even though the ranking
-- algorithm produces ranks for every account. This small, read-only and
-- authenticated-only SECURITY DEFINER exposes *only* leaderboard fields.
-- Never expose profile contact info, emails, DOBs, wallets or private fields.
CREATE OR REPLACE FUNCTION public.blink_public_leaderboard_rows()
RETURNS TABLE (
  user_id uuid,
  score bigint,
  coins bigint,
  streak integer,
  best_streak integer,
  name text,
  username text,
  avatar_url text,
  university text,
  world_rank bigint,
  faculty text,
  academic_level text,
  verification_badge text,
  campus_rank bigint
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $function$
  WITH ranked AS (
    SELECT
      p.id AS user_id,
      COALESCE(p.points, 0)::bigint AS score,
      COALESCE(gp.coins, 0)::bigint AS coins,
      COALESCE(gp.streak, 0)::integer AS streak,
      COALESCE(gp.best_streak, 0)::integer AS best_streak,
      COALESCE(NULLIF(p.name, ''), NULLIF(p.full_name, ''), p.username)::text AS name,
      p.username::text AS username,
      NULLIF(p.avatar_url, '')::text AS avatar_url,
      NULLIF(NULLIF(BTRIM(p.university), ''), 'null')::text AS raw_university,
      NULLIF(REGEXP_REPLACE(LOWER(COALESCE(NULLIF(NULLIF(BTRIM(p.university), ''), 'null'), '')), '[^a-z0-9]+', '', 'g'), '') AS campus_key,
      RANK() OVER (
        ORDER BY COALESCE(p.points, 0) DESC, p.created_at ASC, p.id ASC
      )::bigint AS world_rank,
      p.faculty::text AS faculty,
      p.academic_level::text AS academic_level,
      COALESCE(NULLIF(p.verification_badge, ''), 'NONE')::text AS verification_badge,
      RANK() OVER (
        PARTITION BY NULLIF(REGEXP_REPLACE(LOWER(COALESCE(NULLIF(NULLIF(BTRIM(p.university), ''), 'null'), '')), '[^a-z0-9]+', '', 'g'), '')
        ORDER BY COALESCE(p.points, 0) DESC, p.created_at ASC, p.id ASC
      )::bigint AS campus_rank
    FROM public.profiles AS p
    LEFT JOIN public.game_profiles AS gp ON gp.user_id = p.id
    WHERE (SELECT auth.uid()) IS NOT NULL
      AND NULLIF(BTRIM(p.username), '') IS NOT NULL
  )
  SELECT
    ranked.user_id, ranked.score, ranked.coins,
    ranked.streak, ranked.best_streak, ranked.name,
    ranked.username, ranked.avatar_url,
    CASE
      WHEN ranked.campus_key = 'federaluniversityoftechnologyakure'
        THEN 'Federal University of Technology Akure'
      ELSE ranked.raw_university
    END AS university,
    ranked.world_rank, ranked.faculty, ranked.academic_level,
    ranked.verification_badge,
    CASE WHEN ranked.campus_key IS NULL THEN NULL::bigint
         ELSE ranked.campus_rank END AS campus_rank
  FROM ranked;
$function$;

-- Functions are executable by PUBLIC by default; deny anonymous access,
-- and never expand the existing owner-only profiles SELECT RLS policy.
REVOKE ALL ON FUNCTION public.blink_public_leaderboard_rows() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.blink_public_leaderboard_rows() TO authenticated;

-- Keep the existing view's first 12 columns and types in their original
-- order so already-published Android versions work without an APK update.
-- Appended fields allow verified badges and campus ranks on newer clients.
CREATE OR REPLACE VIEW public.game_leaderboard
WITH (security_invoker = true)
AS
SELECT
  user_id, score, coins, streak, best_streak,
  name, username, avatar_url, university, world_rank,
  faculty, academic_level, verification_badge, campus_rank
FROM public.blink_public_leaderboard_rows();

REVOKE ALL ON public.game_leaderboard FROM PUBLIC, anon;
GRANT SELECT ON public.game_leaderboard TO authenticated;

COMMENT ON FUNCTION public.blink_public_leaderboard_rows()
IS 'Auth-only public ranking projection; intentionally bypasses private profiles RLS solely for safe leaderboard columns. No private contact fields.';
