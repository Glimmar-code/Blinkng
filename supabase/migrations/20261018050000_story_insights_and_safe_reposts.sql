-- Feature 6 / Testlab staging only: owner-gated viewer insights and explicit repost permission.
begin;

alter table public.stories
  add column if not exists allow_reposts boolean not null default false;

alter table public.stories
  add column if not exists reposted_from_id uuid references public.stories(id) on delete set null;

create index if not exists idx_stories_reposted_from_id on public.stories(reposted_from_id);

-- Owner-only visitor/like identities; private profiles and blocked relationships
-- must never be returned. No elevated client token may bypass auth.uid().
create or replace function public.get_my_story_insights(p_story_id uuid)
returns jsonb language plpgsql stable security definer
set search_path = public, pg_temp as $$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if not exists(select 1 from public.stories s
                where s.id = p_story_id and s.user_id = v_uid) then
    raise exception 'STORY_ACCESS_DENIED';
  end if;
  return jsonb_build_object(
    'viewers', coalesce((
      select jsonb_agg(jsonb_build_object(
        'user_id', x.user_id, 'username', x.username,
        'name', x.full_name, 'avatar_url', x.avatar_url,
        'viewed_at', x.viewed_at
      ) order by x.viewed_at desc)
      from (
        select sv.user_id, p.username, p.full_name, p.avatar_url,
               coalesce(to_jsonb(sv)->>'created_at', '') as viewed_at
        from public.story_views sv
        join public.profiles p on p.id = sv.user_id
        where sv.story_id = p_story_id
          and not exists(
            select 1 from public.blocks b
            where (b.blocker_id = v_uid and b.blocked_id = sv.user_id)
               or (b.blocker_id = sv.user_id and b.blocked_id = v_uid)
          )
        order by to_jsonb(sv)->>'created_at' desc nulls last limit 100
      ) x
    ), '[]'::jsonb),
    'likers', coalesce((
      select jsonb_agg(jsonb_build_object(
        'user_id', p.id, 'username', p.username,
        'name', p.full_name, 'avatar_url', p.avatar_url
      ) order by p.username)
      from public.story_likes sl join public.profiles p on p.id = sl.user_id
      where sl.story_id = p_story_id
        and not exists(
          select 1 from public.blocks b
          where (b.blocker_id = v_uid and b.blocked_id = p.id)
             or (b.blocker_id = p.id and b.blocked_id = v_uid)
        )
    ), '[]'::jsonb)
  );
end; $$;

-- A user may choose whether others can repost each own story, but may not
-- modify someone else's story or resurrect expired content.
create or replace function public.set_my_story_reposts_allowed(
  p_story_id uuid, p_allow boolean
)
returns boolean language plpgsql security definer
set search_path = public, pg_temp as $$
begin
  if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_allow is null then return false; end if;
  update public.stories
    set allow_reposts = p_allow
    where id = p_story_id and user_id = auth.uid()
      and active and created_at > now() - interval '24 hours'
      and reposted_from_id is null;
  return found;
end; $$;

create or replace function public.repost_blink_story(p_story_id uuid)
returns uuid language plpgsql security definer
set search_path = public, pg_temp as $$
declare v_uid uuid := auth.uid(); v_orig public.stories%rowtype; v_new uuid;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select * into v_orig from public.stories s
   where s.id = p_story_id and s.active and s.allow_reposts
     and s.created_at > now() - interval '24 hours'
     and s.reposted_from_id is null;
  if not found or v_orig.user_id = v_uid then
    raise exception 'STORY_NOT_AVAILABLE_FOR_REPOST';
  end if;
  if exists(
    select 1 from public.blocks b
    where (b.blocker_id = v_uid and b.blocked_id = v_orig.user_id)
       or (b.blocker_id = v_orig.user_id and b.blocked_id = v_uid)
  ) then raise exception 'STORY_NOT_AVAILABLE_FOR_REPOST'; end if;
  -- One repost per original per recipient; duplicate taps do not create spam.
  select s.id into v_new from public.stories s
    where s.user_id = v_uid and s.reposted_from_id = p_story_id
    limit 1;
  if v_new is not null then return v_new; end if;
  v_new := gen_random_uuid();
  insert into public.stories(
    id,user_id,active,media_url,image_url,video_url,media_type,
    caption,likes_count,allow_reposts,reposted_from_id
  ) values (
    v_new,v_uid,true,v_orig.media_url,v_orig.image_url,v_orig.video_url,
    v_orig.media_type,v_orig.caption,0,false,p_story_id
  );
  return v_new;
end; $$;

revoke all on function public.get_my_story_insights(uuid) from public, anon;
revoke all on function public.set_my_story_reposts_allowed(uuid, boolean) from public, anon;
revoke all on function public.repost_blink_story(uuid) from public, anon;
grant execute on function public.get_my_story_insights(uuid) to authenticated;
grant execute on function public.set_my_story_reposts_allowed(uuid, boolean) to authenticated;
grant execute on function public.repost_blink_story(uuid) to authenticated;

commit;
