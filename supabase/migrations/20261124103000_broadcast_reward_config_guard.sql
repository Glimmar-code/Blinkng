-- Enforce the currently implemented reward contract server-side.
-- Production catalog check on 2026-11-24 found zero campaign_rules and promo_rules rows.
-- Testlab-first; do not deploy to production until reviewed and validated.
CREATE OR REPLACE FUNCTION public.broadcast_reward_config_guard()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO 'public'
AS $function$
DECLARE
  tier jsonb;
BEGIN
  IF TG_TABLE_NAME = 'campaign_rules' THEN
    IF jsonb_typeof(NEW.tiers) <> 'array' THEN
      RAISE EXCEPTION 'INVALID_CAMPAIGN_TIERS';
    END IF;
    FOR tier IN SELECT value FROM jsonb_array_elements(NEW.tiers)
    LOOP
      IF coalesce((tier->>'threshold')::integer, 0) <= 0
         OR coalesce(tier->>'reward_type', '') <> 'coins'
         OR coalesce((tier->>'amount')::numeric, 0) <= 0 THEN
        RAISE EXCEPTION 'UNSUPPORTED_CAMPAIGN_REWARD';
      END IF;
    END LOOP;
  ELSIF TG_TABLE_NAME = 'promo_rules' THEN
    IF NEW.reward_type <> 'coins'
       OR coalesce(NEW.min_purchase, 0) > 0
       OR coalesce(NEW.reward_amount, 0) <= 0
       OR coalesce(NEW.per_user_limit, 0) <= 0 THEN
      RAISE EXCEPTION 'UNSUPPORTED_PROMO_REWARD';
    END IF;
  END IF;
  RETURN NEW;
END
$function$;

DROP TRIGGER IF EXISTS broadcast_campaign_reward_config_guard ON public.campaign_rules;
CREATE TRIGGER broadcast_campaign_reward_config_guard
BEFORE INSERT OR UPDATE OF tiers ON public.campaign_rules
FOR EACH ROW EXECUTE FUNCTION public.broadcast_reward_config_guard();

DROP TRIGGER IF EXISTS broadcast_promo_reward_config_guard ON public.promo_rules;
CREATE TRIGGER broadcast_promo_reward_config_guard
BEFORE INSERT OR UPDATE OF reward_type, min_purchase, reward_amount, per_user_limit ON public.promo_rules
FOR EACH ROW EXECUTE FUNCTION public.broadcast_reward_config_guard();
