-- Safe rollback disables new authoring guards without weakening reward security.
DROP TRIGGER IF EXISTS broadcast_validate_row_guard ON public.broadcasts;
DROP TRIGGER IF EXISTS broadcast_campaign_reward_config_guard ON public.campaign_rules;
DROP TRIGGER IF EXISTS broadcast_promo_reward_config_guard ON public.promo_rules;
REVOKE EXECUTE ON FUNCTION public.broadcast_validate_row(),public.broadcast_reward_config_guard() FROM PUBLIC,anon,authenticated;
