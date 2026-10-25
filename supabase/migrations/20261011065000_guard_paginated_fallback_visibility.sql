-- get_feed_page is also the emergency fallback when ranking RPC times out.
-- The fallback must not bypass post moderation, expiration, or audience privacy.
-- Preserve legitimate follower-only content for actual followers.
do $migration$
declare
  v_function regprocedure := 'public.get_feed_page(integer,timestamp with time zone,uuid,text,uuid)'::regprocedure;
  v_source text := pg_get_functiondef(v_function);
  v_before text := $old$
  WHERE (SELECT auth.uid()) IS NOT NULL
    AND fp.is_active = true
$old$;
  v_after text := $new$
  WHERE (SELECT auth.uid()) IS NOT NULL
    AND fp.is_active = true
    AND coalesce(fp.is_flagged, false) = false
    AND (fp.expires_at is null OR fp.expires_at > now())
    AND (
      lower(coalesce(fp.audience, 'everyone')) = 'everyone'
      OR fp.user_id = (SELECT auth.uid())
      OR (
        lower(fp.audience) = 'followers'
        AND EXISTS (
          SELECT 1 FROM public.follows audience_follow
          WHERE audience_follow.follower_id = (SELECT auth.uid())
            AND audience_follow.following_id = fp.user_id
        )
      )
    )
$new$;
begin
  if v_source is null or position(v_before in v_source) = 0 then
    raise exception 'GET_FEED_PAGE_SOURCE_CHANGED: review visibility before fallback rollout';
  end if;
  execute replace(v_source, v_before, v_after);
end;
$migration$;
