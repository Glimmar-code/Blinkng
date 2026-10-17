-- BLINK LIFE dedicated game staging ledger, DRAFT / NOT DEPLOYED.
-- Review with staging Supabase operator before applying. NEVER merge into
-- supabase/migrations or run on production as an automatic database change.
-- Fictional, nonredeemable Game Cash ONLY; no BLINK Coins or fiat transfers.
BEGIN;

CREATE SCHEMA IF NOT EXISTS blink_life_private;
REVOKE ALL ON SCHEMA blink_life_private FROM PUBLIC, anon, authenticated;
GRANT USAGE ON SCHEMA blink_life_private TO service_role;

-- Stable, account-linked virtual plot addresses. Sequence gaps after a
-- transaction rollback are harmless; an issued slot is never reassigned.
CREATE SEQUENCE IF NOT EXISTS blink_life_private.home_plot_seq
  MINVALUE 0 MAXVALUE 9999 START WITH 0 NO CYCLE;

CREATE TABLE IF NOT EXISTS blink_life_private.profiles (
  account_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  game_cash integer NOT NULL DEFAULT 500 CHECK (game_cash BETWEEN 0 AND 1000000),
  home_owned boolean NOT NULL DEFAULT false,
  home_slot integer UNIQUE CHECK (home_slot BETWEEN 0 AND 9999),
  furniture_count smallint NOT NULL DEFAULT 0
    CHECK (furniture_count BETWEEN 0 AND 12),
  revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT furniture_requires_home CHECK (home_owned OR furniture_count = 0),
  CONSTRAINT owned_home_has_plot CHECK (home_owned = (home_slot IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS blink_life_private.applied_actions (
  event_id uuid PRIMARY KEY,
  account_id uuid NOT NULL REFERENCES blink_life_private.profiles(account_id)
    ON DELETE CASCADE,
  action_kind text NOT NULL CHECK (action_kind IN ('buy_home', 'furnish', 'work')),
  applied_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS life_action_history_account_idx
  ON blink_life_private.applied_actions(account_id, applied_at DESC);

-- Tables are NOT exposed as PostgREST public or authenticated resources.
REVOKE ALL ON ALL TABLES IN SCHEMA blink_life_private FROM PUBLIC, anon, authenticated;
REVOKE ALL ON SEQUENCE blink_life_private.home_plot_seq FROM PUBLIC, anon, authenticated;
ALTER TABLE blink_life_private.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE blink_life_private.applied_actions ENABLE ROW LEVEL SECURITY;
-- Intentionally zero policies. Only the narrowly granted server function
-- below can transact, and only when called with the staging service role.

CREATE OR REPLACE FUNCTION blink_life_private.apply_game_action(
  p_account_id uuid, p_event_id uuid, p_action text
) RETURNS TABLE (
  game_cash integer, home_owned boolean, home_slot integer,
  furniture_count smallint, revision bigint, was_duplicate boolean
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  current_profile blink_life_private.profiles%ROWTYPE;
  prior_action blink_life_private.applied_actions%ROWTYPE;
  duplicate boolean := false;
BEGIN
  IF p_account_id IS NULL OR p_event_id IS NULL OR
     p_action NOT IN ('buy_home', 'furnish', 'work') THEN
    RAISE EXCEPTION 'invalid_game_action';
  END IF;
  INSERT INTO blink_life_private.profiles(account_id)
    VALUES(p_account_id) ON CONFLICT (account_id) DO NOTHING;
  SELECT * INTO current_profile
    FROM blink_life_private.profiles
    WHERE account_id = p_account_id FOR UPDATE;

  SELECT * INTO prior_action FROM blink_life_private.applied_actions
    WHERE event_id = p_event_id;
  IF FOUND THEN
    IF prior_action.account_id <> p_account_id OR prior_action.action_kind <> p_action THEN
      RAISE EXCEPTION 'event_id_reused_with_different_action';
    END IF;
    duplicate := true;
  ELSE
    IF p_action = 'buy_home' THEN
      IF current_profile.home_owned OR current_profile.game_cash < 400 THEN
        RAISE EXCEPTION 'cannot_buy_home';
      END IF;
      current_profile.game_cash := current_profile.game_cash - 400;
      current_profile.home_owned := true;
      current_profile.home_slot := nextval(
        'blink_life_private.home_plot_seq'::regclass)::integer;
    ELSIF p_action = 'furnish' THEN
      IF NOT current_profile.home_owned OR current_profile.furniture_count >= 12
        OR current_profile.game_cash < 150 THEN
        RAISE EXCEPTION 'cannot_purchase_furniture';
      END IF;
      current_profile.game_cash := current_profile.game_cash - 150;
      current_profile.furniture_count := current_profile.furniture_count + 1;
    ELSIF p_action = 'work' THEN
      -- The dedicated game host MUST verify job completion and cooldown;
      -- no player-controlled HTTP path may invoke this function.
      current_profile.game_cash := LEAST(current_profile.game_cash + 80, 1000000);
    END IF;
    UPDATE blink_life_private.profiles AS profile SET
      game_cash = current_profile.game_cash,
      home_owned = current_profile.home_owned,
      home_slot = current_profile.home_slot,
      furniture_count = current_profile.furniture_count,
      revision = profile.revision + 1,
      updated_at = now()
    WHERE profile.account_id = p_account_id;
    INSERT INTO blink_life_private.applied_actions(event_id, account_id, action_kind)
      VALUES(p_event_id, p_account_id, p_action);
  END IF;

  RETURN QUERY SELECT p.game_cash, p.home_owned, p.home_slot,
    p.furniture_count, p.revision, duplicate
    FROM blink_life_private.profiles AS p WHERE p.account_id = p_account_id;
END;
$$;

REVOKE ALL ON FUNCTION blink_life_private.apply_game_action(uuid, uuid, text)
  FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION blink_life_private.apply_game_action(uuid, uuid, text)
  TO service_role;

COMMENT ON SCHEMA blink_life_private IS
  'Staging only: dedicated-host nonredeemable game data. Not BLINK wallets.';
COMMIT;
