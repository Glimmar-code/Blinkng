-- Restore editable Broadcast administration fields omitted from the deployed update RPC.
-- Testlab-first recovery migration. Never apply directly to production before review.
CREATE OR REPLACE FUNCTION public.admin_update_broadcast(
  p_id uuid,
  p_version integer,
  p_patch jsonb
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  actor uuid := private.require_blink_admin();
  oldrow broadcasts%rowtype;
  newrow broadcasts%rowtype;
BEGIN
  SELECT * INTO oldrow FROM broadcasts WHERE id = p_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'NOT_FOUND'; END IF;
  IF oldrow.version <> p_version THEN RAISE EXCEPTION 'VERSION_CONFLICT'; END IF;

  IF coalesce(p_patch->>'title', oldrow.title) IS NULL
     OR length(coalesce(p_patch->>'title', oldrow.title)) > 60
     OR length(coalesce(p_patch->>'body', oldrow.body)) > 240 THEN
    RAISE EXCEPTION 'CONTENT_TOO_LONG';
  END IF;

  IF coalesce(p_patch->'audience', oldrow.audience)->>'kind' = 'everyone'
     AND coalesce((p_patch->>'confirm_everyone')::boolean, false) IS NOT TRUE
     AND oldrow.audience->>'kind' IS DISTINCT FROM 'everyone' THEN
    RAISE EXCEPTION 'CONFIRM_EVERYONE_REQUIRED';
  END IF;

  IF coalesce((p_patch->>'starts_at')::timestamptz, oldrow.starts_at) IS NOT NULL
     AND coalesce((p_patch->>'ends_at')::timestamptz, oldrow.ends_at) IS NOT NULL
     AND coalesce((p_patch->>'ends_at')::timestamptz, oldrow.ends_at)
         <= coalesce((p_patch->>'starts_at')::timestamptz, oldrow.starts_at) THEN
    RAISE EXCEPTION 'INVALID_WINDOW';
  END IF;

  UPDATE broadcasts SET
    title = coalesce(p_patch->>'title', title),
    body = coalesce(p_patch->>'body', body),
    status = coalesce((p_patch->>'status')::broadcast_status, status),
    image_url = coalesce(p_patch->>'image_url', image_url),
    cta_label = coalesce(p_patch->>'cta_label', cta_label),
    deep_link = coalesce(p_patch->>'deep_link', deep_link),
    priority = coalesce((p_patch->>'priority')::integer, priority),
    starts_at = coalesce((p_patch->>'starts_at')::timestamptz, starts_at),
    ends_at = coalesce((p_patch->>'ends_at')::timestamptz, ends_at),
    timezone = coalesce(p_patch->>'timezone', timezone),
    dismissible = coalesce((p_patch->>'dismissible')::boolean, dismissible),
    is_blocking = coalesce((p_patch->>'is_blocking')::boolean, is_blocking),
    block_kind = CASE WHEN p_patch ? 'block_kind'
      THEN (p_patch->>'block_kind')::broadcast_block_kind ELSE block_kind END,
    min_app_version = CASE WHEN p_patch ? 'min_app_version'
      THEN nullif(p_patch->>'min_app_version', '') ELSE min_app_version END,
    store_url = CASE WHEN p_patch ? 'store_url'
      THEN nullif(p_patch->>'store_url', '') ELSE store_url END,
    audience = coalesce(p_patch->'audience', audience),
    channels = coalesce(p_patch->'channels', channels),
    version = version + 1,
    updated_by = actor,
    updated_at = now()
  WHERE id = p_id
  RETURNING * INTO newrow;

  PERFORM broadcast_log(actor, 'update', p_id, to_jsonb(oldrow), to_jsonb(newrow));
  RETURN to_jsonb(newrow);
END
$function$;
