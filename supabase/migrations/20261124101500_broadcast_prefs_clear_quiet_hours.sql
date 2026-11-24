-- Allow a user to explicitly clear quiet-hour times in Broadcast preferences.
-- Testlab-first recovery migration; review and validate before any production deployment.
CREATE OR REPLACE FUNCTION public.update_broadcast_prefs(p_patch jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  uid uuid := auth.uid();
  res jsonb;
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;

  INSERT INTO notification_prefs(user_id) VALUES (uid)
    ON CONFLICT DO NOTHING;

  UPDATE notification_prefs SET
    push_enabled = coalesce((p_patch->>'push_enabled')::boolean, push_enabled),
    announcements = coalesce((p_patch->>'announcements')::boolean, announcements),
    campaigns = coalesce((p_patch->>'campaigns')::boolean, campaigns),
    offers = coalesce((p_patch->>'offers')::boolean, offers),
    email = coalesce((p_patch->>'email')::boolean, email),
    quiet_start = CASE WHEN p_patch ? 'quiet_start'
      THEN nullif(p_patch->>'quiet_start', '')::time ELSE quiet_start END,
    quiet_end = CASE WHEN p_patch ? 'quiet_end'
      THEN nullif(p_patch->>'quiet_end', '')::time ELSE quiet_end END,
    timezone = coalesce(p_patch->>'timezone', timezone),
    updated_at = now()
  WHERE user_id = uid
  RETURNING to_jsonb(notification_prefs.*) INTO res;

  RETURN res;
END
$function$;
