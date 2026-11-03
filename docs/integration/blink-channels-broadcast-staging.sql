-- BLINK CHANNELS: STAGING SQL CANDIDATE ONLY.
-- Reuses existing blink_communities and blink_community_members to preserve IDs,
-- ownership and invitations. Do not apply to production without local Supabase
-- migration generation, two-identity RLS tests, migration dry-run and mobile/
-- Windows smoke tests. Anonymous clients never receive follower identities.

begin;

create table if not exists public.blink_channel_updates (
  id uuid primary key default gen_random_uuid(),
  channel_id uuid not null references public.blink_communities(id) on delete cascade,
  author_id uuid not null references auth.users(id) on delete cascade,
  body text not null check (length(btrim(body)) between 1 and 5000),
  created_at timestamptz not null default now()
);
create index if not exists blink_channel_updates_chrono
  on public.blink_channel_updates(channel_id, created_at desc, id desc);

create table if not exists public.blink_channel_reactions (
  update_id uuid not null references public.blink_channel_updates(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  emoji text not null check (emoji in ('👍','❤️','😂','😮','😢','🔥')),
  created_at timestamptz not null default now(),
  primary key(update_id,user_id)
);
create index if not exists blink_channel_reactions_user
  on public.blink_channel_reactions(user_id, update_id);

alter table public.blink_channel_updates enable row level security;
alter table public.blink_channel_reactions enable row level security;
revoke all on public.blink_channel_updates,public.blink_channel_reactions from anon,authenticated;
grant select on public.blink_channel_updates to authenticated;

-- Each SECURITY DEFINER function below returns ONLY an authorization boolean,
-- the caller's own role, a safe aggregate, or authorized update content.
-- No bulk follower identities or reaction-user identities are exposed.
create or replace function public.blink_channel_can_read(p_channel_id uuid)
returns boolean language sql stable security definer set search_path=''
as $$
  select (select auth.uid()) is not null and exists (
    select 1 from public.blink_communities c
    where c.id=p_channel_id
      and not exists (
        select 1 from public.blink_community_members banned
        where banned.community_id=c.id and banned.user_id=(select auth.uid())
          and banned.status='banned'
      )
      and (c.visibility='public' or c.owner_id=(select auth.uid()) or exists (
        select 1 from public.blink_community_members cm
        where cm.community_id=c.id and cm.user_id=(select auth.uid())
          and cm.status='active'
      ))
  );
$$;

create or replace function public.blink_channel_is_admin(p_channel_id uuid)
returns boolean language sql stable security definer set search_path=''
as $$
  select (select auth.uid()) is not null and exists (
    select 1 from public.blink_communities c
    where c.id=p_channel_id and (
      c.owner_id=(select auth.uid()) or exists (
        select 1 from public.blink_community_members cm
        where cm.community_id=c.id and cm.user_id=(select auth.uid())
          and cm.status='active' and cm.role='moderator'
      )
    )
  );
$$;
revoke all on function public.blink_channel_can_read(uuid),public.blink_channel_is_admin(uuid) from public,anon;
grant execute on function public.blink_channel_can_read(uuid),public.blink_channel_is_admin(uuid) to authenticated;

create policy blink_channel_updates_read on public.blink_channel_updates
  for select to authenticated using (public.blink_channel_can_read(channel_id));
-- No direct client insert/update/delete grants or policies. All writes use RPC.

create or replace function public.blink_channel_my_role(p_channel_id uuid)
returns text language sql stable security definer set search_path=''
as $$
  select case
    when (select auth.uid()) is null then 'none'
    when exists(select 1 from public.blink_communities c
                where c.id=p_channel_id and c.owner_id=(select auth.uid())) then 'owner'
    else coalesce((
      select case when m.status='active' and m.role='moderator' then 'moderator'
                  when m.status='active' then 'follower' else m.status end
      from public.blink_community_members m
      where m.community_id=p_channel_id and m.user_id=(select auth.uid())
    ),'none')
  end;
$$;
create or replace function public.blink_channel_follower_count(p_channel_id uuid)
returns integer language sql stable security definer set search_path=''
as $$
  select case when public.blink_channel_can_read(p_channel_id) then (
    select count(*)::integer from public.blink_community_members m
    where m.community_id=p_channel_id and m.status='active'
  ) else 0 end;
$$;

create or replace function public.blink_channel_updates_page(
  p_channel_id uuid, p_limit integer default 50
)
returns table(
  id uuid, channel_id uuid, author_id uuid, body text,
  created_at timestamptz, reactions jsonb, my_reaction text
)
language plpgsql stable security definer set search_path=''
as $$
begin
  if not public.blink_channel_can_read(p_channel_id) then
    raise exception 'CHANNEL_ACCESS_DENIED' using errcode='42501';
  end if;
  return query
    select u.id,u.channel_id,u.author_id,u.body,u.created_at,
      coalesce((
        select jsonb_object_agg(q.emoji,q.n) from (
          select r.emoji,count(*)::integer n from public.blink_channel_reactions r
          where r.update_id=u.id group by r.emoji
        ) q
      ),'{}'::jsonb),
      (select r.emoji from public.blink_channel_reactions r
       where r.update_id=u.id and r.user_id=(select auth.uid()))
    from public.blink_channel_updates u
    where u.channel_id=p_channel_id
    order by u.created_at desc,u.id desc
    limit greatest(1,least(coalesce(p_limit,50),100));
end;
$$;

create or replace function public.blink_channel_publish(p_channel_id uuid,p_body text)
returns uuid language plpgsql security definer set search_path=''
as $$
declare v_id uuid;
begin
  if not public.blink_channel_is_admin(p_channel_id) then
    raise exception 'CHANNEL_ADMIN_REQUIRED' using errcode='42501';
  end if;
  if p_body is null or length(btrim(p_body)) not between 1 and 5000 then
    raise exception 'INVALID_CHANNEL_UPDATE';
  end if;
  insert into public.blink_channel_updates(channel_id,author_id,body)
    values(p_channel_id,auth.uid(),btrim(p_body)) returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.blink_channel_react(p_update_id uuid,p_emoji text)
returns text language plpgsql security definer set search_path=''
as $$
declare v_channel uuid; v_old text;
begin
  if (select auth.uid()) is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_emoji not in ('👍','❤️','😂','😮','😢','🔥') then
    raise exception 'INVALID_REACTION';
  end if;
  select u.channel_id into v_channel from public.blink_channel_updates u where u.id=p_update_id;
  if v_channel is null or not public.blink_channel_can_read(v_channel) then
    raise exception 'CHANNEL_ACCESS_DENIED' using errcode='42501';
  end if;
  if not public.blink_channel_is_admin(v_channel) and
     not exists(select 1 from public.blink_community_members m
       where m.community_id=v_channel and m.user_id=auth.uid() and m.status='active') then
    raise exception 'FOLLOW_CHANNEL_TO_REACT' using errcode='42501';
  end if;
  select r.emoji into v_old from public.blink_channel_reactions r
   where r.update_id=p_update_id and r.user_id=auth.uid() for update;
  if v_old=p_emoji then
    delete from public.blink_channel_reactions
     where update_id=p_update_id and user_id=auth.uid();
    return '';
  end if;
  insert into public.blink_channel_reactions(update_id,user_id,emoji)
    values(p_update_id,auth.uid(),p_emoji)
    on conflict(update_id,user_id) do update
      set emoji=excluded.emoji, created_at=now();
  return p_emoji;
end;
$$;

revoke all on function public.blink_channel_my_role(uuid),
  public.blink_channel_follower_count(uuid),
  public.blink_channel_updates_page(uuid,integer),
  public.blink_channel_publish(uuid,text),
  public.blink_channel_react(uuid,text) from public,anon;
grant execute on function public.blink_channel_my_role(uuid),
  public.blink_channel_follower_count(uuid),
  public.blink_channel_updates_page(uuid,integer),
  public.blink_channel_publish(uuid,text),
  public.blink_channel_react(uuid,text) to authenticated;

commit;
