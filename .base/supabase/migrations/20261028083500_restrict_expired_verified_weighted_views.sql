-- Server-authoritative verification weighting for all counted Feed/Reel views.
-- Preserve atomic repeated events (100 per viewer/content), existing XP, and 1/3/6 weights.
-- Expired or revoked verification no longer contributes premium-weighted views.
-- This migration rewrites only the eligibility CASE expression of the installed RPC:
-- it deliberately preserves security definer, event idempotency, row-lock behavior,
-- permissions, point awards, and the current immediate-write semantics.
do $migration$
declare
  v_source text := pg_get_functiondef('public.record_content_view(uuid,uuid)'::regprocedure);
  v_before text := $segment$
    select case
        when upper(coalesce(p.verification_badge::text, '')) = 'GOLD'
          or upper(coalesce(p.verification_tier::text, '')) = 'GOLD' then 6
        when upper(coalesce(p.verification_badge::text, '')) = 'BLUE'
          or upper(coalesce(p.verification_tier::text, '')) = 'STANDARD'
          or coalesce(p.is_verified, false) then 3
        else 1
    end
    into v_view_weight
    from public.profiles p
    where p.id = v_viewer_id;$segment$;
  v_after text := $segment$
    select case
        when coalesce(p.is_verified, false)
          and (p.verification_expires_at is null or p.verification_expires_at > now())
          and (upper(coalesce(p.verification_badge::text, '')) = 'GOLD'
            or upper(coalesce(p.verification_tier::text, '')) = 'GOLD') then 6
        when coalesce(p.is_verified, false)
          and (p.verification_expires_at is null or p.verification_expires_at > now())
          then 3
        else 1
    end
    into v_view_weight
    from public.profiles p
    where p.id = v_viewer_id;$segment$;
begin
  if v_source is null
     or position(v_before in v_source) = 0
     or length(v_source)-length(replace(v_source, v_before, '')) <> length(v_before) then
    raise exception 'RECORD_CONTENT_VIEW_WEIGHT_SOURCE_CHANGED: review migration against active RPC';
  end if;
  execute replace(v_source, v_before, v_after);
end;
$migration$;

-- Backend grants and policies are intentionally unchanged. Existing RPC access
-- must remain authenticated-only and anonymous web views are separate.
