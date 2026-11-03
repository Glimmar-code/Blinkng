-- Remove direct client access to the retired, client-scored game-session API.
-- destructive-change-reviewed
-- rollback-plan: only after replacing client-provided scoring with server validation,
-- restore EXECUTE for the intended authenticated role if a supported legacy client needs it.
-- The supported General Study scoring flow is separate; it must be regression-tested.
-- This migration only changes function EXECUTE permissions, not data or scoring records.
--
-- Security: record_game_session(text, integer, integer) accepts a score from the caller,
-- so it must not be callable by anon/authenticated client roles.
-- Keep this guard idempotent for environments without the legacy function.
do $$
begin
  if to_regprocedure('public.record_game_session(text,integer,integer)') is not null then
    revoke execute on function public.record_game_session(text,integer,integer)
      from public, anon, authenticated;
  end if;
end;
$$;
