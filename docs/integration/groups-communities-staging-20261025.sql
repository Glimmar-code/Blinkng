-- BLINK COMMUNITIES / GROUPS — DESIGN CANDIDATE, NOT A DEPLOYED MIGRATION.
-- DO NOT run on production. First create a versioned Supabase migration with
-- Supabase CLI and test it on an isolated free local Postgres/Supabase instance.
--
-- Verified production 2026-10-25:
--   create_group_conversation(p_title text,p_member_ids uuid[]) exists,
--   enforces authenticated creator, 3–50 members, valid profiles and no blocks.
--   get_conversation_summaries_page currently JOINS one row per other member;
--   for groups it repeats the same conversation and returns one member's name.
--   It must not be treated as a production-ready group inbox.
--   No public communities/community_members tables currently exist.
--
-- Community foundation proposal (owner-only mutation until role and invite QA):
begin;

create table if not exists public.blink_communities (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    name text not null check (length(btrim(name)) between 3 and 80),
    description text not null default '' check (length(description) <= 1500),
    cover_url text,
    university text not null check (length(btrim(university)) between 2 and 150),
    category text not null check (length(btrim(category)) between 2 and 80),
    visibility text not null default 'public' check (visibility in ('public','private')),
    rules text[] not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.blink_community_members (
    community_id uuid not null references public.blink_communities(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null default 'member' check (role in ('member','moderator')),
    status text not null default 'pending' check (status in ('pending','active','banned')),
    joined_at timestamptz not null default now(),
    primary key (community_id,user_id)
);

create index if not exists blink_communities_university_newest
    on public.blink_communities (university,created_at desc);
create index if not exists blink_community_members_user
    on public.blink_community_members (user_id,community_id);

alter table public.blink_communities enable row level security;
alter table public.blink_community_members enable row level security;

-- Existing SELECT policies are deliberately not dropped here. This draft
-- is for a NEW schema, and must not be applied over an unknown existing state.
create policy blink_communities_select on public.blink_communities
    for select to authenticated
    using (visibility = 'public' or owner_id = (select auth.uid()));
create policy blink_communities_insert on public.blink_communities
    for insert to authenticated
    with check (owner_id = (select auth.uid()));
create policy blink_communities_update on public.blink_communities
    for update to authenticated
    using (owner_id = (select auth.uid()))
    with check (owner_id = (select auth.uid()));
create policy blink_communities_delete on public.blink_communities
    for delete to authenticated
    using (owner_id = (select auth.uid()));

create policy blink_community_members_read on public.blink_community_members
    for select to authenticated
    using (
        user_id = (select auth.uid()) or exists (
            select 1 from public.blink_communities c
            where c.id = community_id and c.owner_id = (select auth.uid())
        )
    );
create policy blink_community_members_insert on public.blink_community_members
    for insert to authenticated
    with check (
        user_id = (select auth.uid()) and role = 'member' and
        (status = 'pending' or (status = 'active' and exists (
            select 1 from public.blink_communities c
            where c.id = community_id and c.visibility = 'public'
        )))
    );
create policy blink_community_members_owner_moderate on public.blink_community_members
    for update to authenticated
    using (exists (
        select 1 from public.blink_communities c
        where c.id = community_id and c.owner_id = (select auth.uid())
    ))
    with check (exists (
        select 1 from public.blink_communities c
        where c.id = community_id and c.owner_id = (select auth.uid())
    ));
create policy blink_community_members_leave on public.blink_community_members
    for delete to authenticated
    using (user_id = (select auth.uid()) or exists (
        select 1 from public.blink_communities c
        where c.id = community_id and c.owner_id = (select auth.uid())
    ));

grant select,insert,update,delete on public.blink_communities to authenticated;
grant select,insert,update,delete on public.blink_community_members to authenticated;

-- Isolated server-backed group listing; never modify existing DM summary RPC
-- until Android/Windows consumers support group titles and member counts.
create or replace function public.blink_my_group_conversations(p_limit integer default 50)
returns table (
    group_id uuid, title text, description text,
    avatar_url text, member_count bigint, updated_at timestamptz
)
language sql stable security definer set search_path = ''
as $$
    select
        c.id,
        coalesce(nullif(c.title,''),'Group'),
        coalesce(c.description,''),
        c.avatar_url,
        (select count(*) from public.conversation_participants cp
         where cp.conversation_id=c.id and cp.left_at is null),
        c.updated_at
    from public.conversations c
    join public.conversation_participants mine
      on mine.conversation_id=c.id
      and mine.user_id=auth.uid()
      and mine.left_at is null
    where auth.uid() is not null and c.is_group=true
    order by c.updated_at desc,c.id desc
    limit greatest(1,least(coalesce(p_limit,50),100))
$$;
revoke all on function public.blink_my_group_conversations(integer)
    from public,anon;
grant execute on function public.blink_my_group_conversations(integer)
    to authenticated;

commit;

-- RELEASE HOLD / FOLLOW-ON TESTS:
-- a. Verify owner cannot reassign community ownership; blocked/banned accounts
--    cannot rejoin or view private content.
-- b. Private communities require a vetted membership-aware read policy BEFORE
--    enabling member views; the above owner/public SELECT is intentionally limited.
-- c. Fix moderator permission escalation and self-banning/role takeover tests.
-- d. Test SQL RLS with two different authenticated IDs and no JWT.
-- e. Test group message send/read/retry, online/offline and 3–50 members;
--    group summary must show ONE row per group, not a fake 1:1 conversation.
-- f. Test Android and Windows parity, invite/leave/member moderation screens.
-- g. No production deployment until security review and all staging checks pass.
