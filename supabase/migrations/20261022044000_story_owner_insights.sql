-- BLINK Status 2.0: private per-story visitor and like lists.
-- Deploy to a disposable/local staging database first; do not enable on public
-- instances until authorization and block-relationship regression tests pass.
begin;

create or replace function public.get_my_story_insights(p_story_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = pg_catalog, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
begin
  if v_user_id is null then
    raise exception 'STORY_AUTH_REQUIRED' using errcode='42501';
  end if;
  -- Do not disclose whether the identifier exists for any other owner.
  if not exists (
    select 1 from public.stories s
    where s.id = p_story_id
      and s.user_id = v_user_id
      and s.active = true
      and s.created_at <= now()
      and s.created_at > now() - interval '24 hours'
  ) then
    raise exception 'STORY_INSIGHTS_UNAVAILABLE' using errcode='42501';
  end if;

  return jsonb_build_object(
    'viewers', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'user_id', v.id,
          'username', v.username,
          'name', v.full_name,
          'avatar_url', v.avatar_url,
          'viewed_at', v.viewed_at
        )
        order by v.viewed_at desc
      )
      from (
        select p.id, p.username, p.full_name, p.avatar_url,
               sv.created_at as viewed_at
        from public.story_views sv
        join public.profiles p on p.id=sv.user_id
        where sv.story_id=p_story_id
          and sv.user_id<>v_user_id
          and not exists (
            select 1 from public.blocks b
            where (b.blocker_id=v_user_id and b.blocked_id=sv.user_id)
               or (b.blocked_id=v_user_id and b.blocker_id=sv.user_id)
          )
        order by sv.created_at desc
        limit 100
      ) v
    ), '[]'::jsonb),
    'likers', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'user_id', l.id,
          'username', l.username,
          'name', l.full_name,
          'avatar_url', l.avatar_url
        )
        order by l.username
      )
      from (
        select p.id, p.username, p.full_name, p.avatar_url
        from public.story_likes sl
        join public.profiles p on p.id=sl.user_id
        where sl.story_id=p_story_id
          and sl.user_id<>v_user_id
          and not exists (
            select 1 from public.blocks b
            where (b.blocker_id=v_user_id and b.blocked_id=sl.user_id)
               or (b.blocked_id=v_user_id and b.blocker_id=sl.user_id)
          )
        order by sl.created_at desc
        limit 100
      ) l
    ), '[]'::jsonb)
  );
end;
$$;

revoke all on function public.get_my_story_insights(uuid) from public, anon;
grant execute on function public.get_my_story_insights(uuid) to authenticated;

commit;
