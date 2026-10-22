-- destructive-change-reviewed
-- Review: turning OFF repost consent deletes only derivative repost records
-- from public.stories, preserving their original parent status and media object.
-- This revocation is intentional and irreversible for derivative interactions.
-- rollback-plan: Before production promotion, snapshot public.stories and its
-- interaction tables; on rollback disable the feature and restore authorized
-- derivative rows from that snapshot only after validating parent consent.
-- Do not automatically resurrect revoked reposts or bypass owner permissions.
-- BLINK Status 2.0 Phase 4, staging only.
-- This enhances visibility rules for already PUBLIC stories, NOT private audiences.
-- story-media is a public bucket. Close Friends and campus-only stories must
-- remain disabled until media migrates to private Storage + authorized delivery.
begin;

alter table public.stories
  add column if not exists allow_reposts boolean not null default false;
alter table public.stories
  add column if not exists reposted_from_id uuid
    references public.stories(id) on delete cascade;

create unique index if not exists stories_one_repost_per_viewer
  on public.stories(user_id, reposted_from_id)
  where reposted_from_id is not null;

-- Read block decisions in both directions; public.blocks RLS intentionally
-- hides other people's rows, so an ordinary policy subquery is insufficient.
-- This helper only exposes the caller's own mutual block predicate.
create or replace function public.blink_story_unblocked(p_author_id uuid)
returns boolean
language sql stable security definer
set search_path = pg_catalog, pg_temp
as $$
    select auth.uid() is not null
      and p_author_id is not null
      and not exists (
        select 1 from public.blocks b
        where (b.blocker_id = auth.uid() and b.blocked_id = p_author_id)
           or (b.blocker_id = p_author_id and b.blocked_id = auth.uid())
      );
$$;

revoke all on function public.blink_story_unblocked(uuid) from public, anon;
grant execute on function public.blink_story_unblocked(uuid) to authenticated;

-- Referencing the same RLS-protected stories table from its own SELECT
-- policy would recurse. Use a strictly scoped auth-bound definer predicate.
create or replace function public.blink_story_original_shareable(p_story_id uuid)
returns boolean
language sql stable security definer
set search_path = pg_catalog, pg_temp
as $$
    select auth.uid() is not null
      and exists (
        select 1 from public.stories original
        where original.id = p_story_id
          and original.reposted_from_id is null
          and original.active is true
          and original.allow_reposts is true
          and original.created_at <= now()
          and original.created_at > now() - interval '24 hours'
          and public.blink_story_unblocked(original.user_id)
          and not exists (
            select 1 from public.muted_users m
            where m.user_id = auth.uid() and m.muted_id = original.user_id
          )
      );
$$;

revoke all on function public.blink_story_original_shareable(uuid) from public, anon;
grant execute on function public.blink_story_original_shareable(uuid) to authenticated;

drop policy if exists select_stories on public.stories;
create policy select_stories on public.stories for select to authenticated
using (
    active is true
    and created_at <= now()
    and created_at > now() - interval '24 hours'
    and public.blink_story_unblocked(user_id)
    and (
        user_id = auth.uid()
        or not exists (
            select 1 from public.muted_users m
            where m.user_id = auth.uid() and m.muted_id = stories.user_id
        )
    )
    and (
        reposted_from_id is null
        or public.blink_story_original_shareable(reposted_from_id)
    )
);

-- Direct clients may create their own original statuses only. An authorized
-- repost must go through the RPC below, which enforces the creator's choice.
drop policy if exists insert_stories on public.stories;
create policy insert_stories on public.stories for insert to authenticated
with check (auth.uid() = user_id and reposted_from_id is null);

-- For future client updates, forbid rewriting the server-controlled parent
-- reference. RPC inserts still work because this guard applies to UPDATE only.
create or replace function public.blink_story_guard_repost_update()
returns trigger
language plpgsql security invoker
set search_path = pg_catalog, pg_temp
as $$
begin
    if auth.uid() is not null and
       (new.reposted_from_id is distinct from old.reposted_from_id
        or new.user_id is distinct from old.user_id
        or new.created_at is distinct from old.created_at) then
        raise exception 'STORY_PROTECTED_FIELDS' using errcode = '42501';
    end if;
    return new;
end;
$$;

drop trigger if exists blink_story_protected_fields on public.stories;
create trigger blink_story_protected_fields
before update on public.stories
for each row execute function public.blink_story_guard_repost_update();

revoke all on function public.blink_story_guard_repost_update() from public, anon, authenticated;

-- A creator can grant or withdraw repost permission only for their own,
-- still-active original status. Withdrawing consent deletes derived repost
-- metadata. Media deletion remains managed by original author.
create or replace function public.set_my_story_reposts_allowed(
    p_story_id uuid, p_allow boolean
)
returns boolean
language plpgsql security definer
set search_path = pg_catalog, pg_temp
as $$
declare v_uid uuid := auth.uid(); v_id uuid;
begin
    if v_uid is null or p_allow is null then
        return false;
    end if;
    update public.stories s
       set allow_reposts = p_allow
     where s.id = p_story_id and s.user_id = v_uid
       and s.reposted_from_id is null
       and s.active is true
       and s.created_at <= now()
       and s.created_at > now() - interval '24 hours'
    returning s.id into v_id;
    if v_id is null then return false; end if;

    if not p_allow then
        delete from public.stories s where s.reposted_from_id = p_story_id;
    end if;
    return true;
end;
$$;

-- A repost is a reference to a public creator-approved status.
-- It inherits the ORIGINAL timestamp, so sharing never extends the 24h life.
-- A DB index makes concurrent taps idempotent.
create or replace function public.repost_blink_story(p_story_id uuid)
returns uuid
language plpgsql security definer
set search_path = pg_catalog, pg_temp
as $$
declare v_uid uuid := auth.uid();
        v_original public.stories%rowtype;
        v_new_id uuid;
begin
    if v_uid is null then
        raise exception 'STORY_AUTH_REQUIRED' using errcode='42501';
    end if;
    select s.* into v_original from public.stories s
    where s.id = p_story_id and s.user_id <> v_uid
      and s.reposted_from_id is null
      and s.allow_reposts is true
      and s.active is true
      and s.created_at <= now()
      and s.created_at > now() - interval '24 hours'
      and public.blink_story_unblocked(s.user_id)
      and not exists (
        select 1 from public.muted_users m
        where m.user_id = v_uid and m.muted_id = s.user_id
      );
    if not found then
        raise exception 'STORY_REPOST_NOT_ALLOWED' using errcode='42501';
    end if;

    insert into public.stories(
      id, user_id, active, created_at, media_url, media_type,
      image_url, video_url, caption, text, background,
      audio_title, allow_reposts, reposted_from_id
    )
    values (
      gen_random_uuid(), v_uid, true, v_original.created_at,
      v_original.media_url, v_original.media_type,
      v_original.image_url, v_original.video_url, v_original.caption,
      v_original.text, v_original.background, v_original.audio_title,
      false, v_original.id
    )
    on conflict (user_id,reposted_from_id)
      where reposted_from_id is not null
    do nothing
    returning id into v_new_id;

    if v_new_id is null then
        select s.id into v_new_id from public.stories s
        where s.user_id = v_uid and s.reposted_from_id = p_story_id;
    end if;
    return v_new_id;
end;
$$;

revoke all on function public.set_my_story_reposts_allowed(uuid,boolean) from public, anon;
revoke all on function public.repost_blink_story(uuid) from public, anon;
grant execute on function public.set_my_story_reposts_allowed(uuid,boolean) to authenticated;
grant execute on function public.repost_blink_story(uuid) to authenticated;

-- Engagement only on statuses actually visible to the actor. Prevent adding
-- viewer/like/reaction/reply records to expired, blocked or hidden stories.
drop policy if exists story_views_insert_own on public.story_views;
create policy story_views_insert_own on public.story_views for insert to authenticated
with check (
    auth.uid() = user_id and exists (
      select 1 from public.stories s where s.id = story_views.story_id
        and s.user_id <> auth.uid()
    )
);

drop policy if exists story_likes_insert_own on public.story_likes;
create policy story_likes_insert_own on public.story_likes for insert to authenticated
with check (
    auth.uid() = user_id and exists (
      select 1 from public.stories s where s.id = story_likes.story_id
        and s.user_id <> auth.uid()
    )
);

drop policy if exists story_reactions_insert_own on public.story_reactions;
create policy story_reactions_insert_own on public.story_reactions for insert to authenticated
with check (
    auth.uid() = user_id and exists (
      select 1 from public.stories s where s.id = story_reactions.story_id
        and s.user_id <> auth.uid()
    )
);

drop policy if exists story_replies_insert_own on public.story_replies;
create policy story_replies_insert_own on public.story_replies for insert to authenticated
with check (
    auth.uid() = user_id and exists (
      select 1 from public.stories s where s.id = story_replies.story_id
        and s.user_id <> auth.uid()
    )
);

commit;
