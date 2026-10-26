-- destructive-change-reviewed
-- Reviewed operations are limited to dropping blink_channel_updates_page during
-- initial creation in THIS SAME migration, replacing it with a privacy-filtered
-- return shape; and dropping the temporary, same-migration body CHECK before
-- adding the expanded media/poll CHECK. No existing community/membership rows
-- or existing production functions are dropped.
-- rollback-plan: First disable new channel routes and revoke EXECUTE on the
-- blink_channel_* RPCs; stop channel-media uploads. Existing communities
-- remain unchanged. Preserve any new channel updates/polls/media for export
-- before considering a later reviewed down migration; do NOT drop or empty
-- tables/buckets automatically because that would destroy users' posts.

-- BLINK Channels: additive production contract, isolated CI verified.
-- Reuses existing community and membership records without migrating or deleting them.
-- Private media, per-user notification settings, polls, analytics and moderation.
-- Requires approved auth and storage schemas; do not bypass security checks.

-- BLINK CHANNELS: STAGING SQL CANDIDATE ONLY.
-- Reuses existing blink_communities and blink_community_members to preserve IDs,
-- ownership and invitations. Do not apply to production without local Supabase
-- migration generation, two-identity RLS tests, migration dry-run and mobile/
-- Windows smoke tests. Anonymous clients never receive follower identities.


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



-- PR #601 / BLINK Channels: completion candidate (NOT DEPLOYED)
-- Apply after blink-channels-broadcast-staging.sql, ONLY in disposable local
-- staging. Do NOT execute against production until RLS, storage, trigger and
-- large-follower-count tests pass. DO NOT fabricate notifications or analytics.

alter table public.blink_channel_updates
  add column if not exists media_kind text not null default 'text',
  add column if not exists media_path text,
  add column if not exists poll_question text,
  add column if not exists poll_options jsonb,
  add column if not exists scheduled_at timestamptz,
  add column if not exists pinned boolean not null default false,
  add column if not exists removed_at timestamptz,
  add column if not exists forwarded_from uuid references public.blink_channel_updates(id);
alter table public.blink_channel_updates
  add constraint channel_kind_check check (media_kind in ('text','image','video','audio','document','poll'));
alter table public.blink_channel_updates drop constraint if exists blink_channel_updates_body_check;
alter table public.blink_channel_updates add constraint channel_body_or_attachment_check
  check (length(btrim(body))<=5000 and
    (length(btrim(body))>0 or media_path is not null or poll_question is not null));
create index if not exists blink_channel_updates_pending
  on public.blink_channel_updates (scheduled_at)
  where scheduled_at is not null and removed_at is null;

create table if not exists public.blink_channel_poll_votes (
  update_id uuid not null references public.blink_channel_updates(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  selected_option integer not null check (selected_option between 0 and 7),
  created_at timestamptz not null default now(),
  primary key(update_id,user_id)
);
create table if not exists public.blink_channel_views (
  update_id uuid not null references public.blink_channel_updates(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  first_viewed_at timestamptz not null default now(),
  primary key(update_id,user_id)
);
create table if not exists public.blink_channel_reports (
  id uuid primary key default gen_random_uuid(),
  update_id uuid not null references public.blink_channel_updates(id) on delete cascade,
  reporter_id uuid not null references auth.users(id) on delete cascade,
  reason text not null check (length(reason) between 3 and 280),
  created_at timestamptz not null default now(),
  unique(update_id,reporter_id)
);
create table if not exists public.blink_channel_notifications (
  channel_id uuid not null references public.blink_communities(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  muted boolean not null default false,
  last_seen_at timestamptz,
  primary key(channel_id,user_id)
);

alter table public.blink_channel_poll_votes enable row level security;
alter table public.blink_channel_views enable row level security;
alter table public.blink_channel_reports enable row level security;
alter table public.blink_channel_notifications enable row level security;
revoke all on public.blink_channel_poll_votes, public.blink_channel_views,
  public.blink_channel_reports,public.blink_channel_notifications from public,anon,authenticated;
grant select,insert,update on public.blink_channel_notifications to authenticated;
create policy channel_notification_owner_read on public.blink_channel_notifications
 for select to authenticated using (user_id=(select auth.uid()));
create policy channel_notification_owner_insert on public.blink_channel_notifications
 for insert to authenticated with check (
   user_id=(select auth.uid()) and
   public.blink_channel_can_read(channel_id)
 );
create policy channel_notification_owner_update on public.blink_channel_notifications
 for update to authenticated using (user_id=(select auth.uid()))
 with check (user_id=(select auth.uid()));
-- Poll votes and viewer data never have direct Data API grants. Aggregate RPCs only.
-- Reports are accessible only to privileged moderation backend, not other users.

-- Storage is private; only owner/active moderators can upload to their own
-- channel/user subfolder. Viewers can read files only for accessible channels.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values ('blink-channel-media','blink-channel-media',false,26214400,
  array['image/jpeg','image/png','image/webp','video/mp4','audio/mp4',
        'audio/mpeg','application/pdf'])
on conflict(id) do update set public=false, file_size_limit=26214400,
  allowed_mime_types=excluded.allowed_mime_types;
create policy blink_channel_media_upload on storage.objects
 for insert to authenticated with check (
   bucket_id='blink-channel-media'
   and (case when name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/' then
       public.blink_channel_is_admin((split_part(name,'/',1))::uuid)
       else false end)
   and split_part(name,'/',2)=(select auth.uid())::text
 );
create policy blink_channel_media_read on storage.objects
 for select to authenticated using (
   bucket_id='blink-channel-media'
   and (case when name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/' then
       public.blink_channel_can_read((split_part(name,'/',1))::uuid)
       else false end)
 );
-- No client UPDATE/DELETE policy. Removing a channel update revokes its display
-- but byte removal requires a privileged garbage-collection job.

-- Evolve update listing in one backward-compatible client contract.
drop function if exists public.blink_channel_updates_page(uuid,integer);
create function public.blink_channel_updates_page(
  p_channel_id uuid, p_limit integer default 50
)
returns table (
 id uuid,channel_id uuid,author_id uuid,body text,created_at timestamptz,
 reactions jsonb,my_reaction text,media_kind text,media_path text,
 poll_question text,poll_options jsonb,poll_counts jsonb,
 my_vote integer,scheduled_at timestamptz,pinned boolean,view_count bigint
) language plpgsql stable security definer set search_path=''
as $$
begin
 if not public.blink_channel_can_read(p_channel_id) then
   raise exception 'CHANNEL_ACCESS_DENIED' using errcode='42501';
 end if;
 return query
 select u.id,u.channel_id,null::uuid,u.body,u.created_at,
   coalesce((select jsonb_object_agg(q.emoji,q.n) from
     (select r.emoji,count(*)::integer n from public.blink_channel_reactions r
      where r.update_id=u.id group by r.emoji) q),'{}'::jsonb),
   (select r.emoji from public.blink_channel_reactions r
    where r.update_id=u.id and r.user_id=(select auth.uid())),
   u.media_kind,u.media_path,u.poll_question,u.poll_options,
   coalesce((select jsonb_object_agg(v.selected_option,v.n) from
     (select pv.selected_option,count(*)::integer n
      from public.blink_channel_poll_votes pv where pv.update_id=u.id
      group by pv.selected_option) v),'{}'::jsonb),
   (select pv.selected_option from public.blink_channel_poll_votes pv
    where pv.update_id=u.id and pv.user_id=(select auth.uid())),
   u.scheduled_at,u.pinned,
   (select count(*) from public.blink_channel_views vw where vw.update_id=u.id)
 from public.blink_channel_updates u
 where u.channel_id=p_channel_id and u.removed_at is null
   and (u.scheduled_at is null or u.scheduled_at<=now())
 order by u.pinned desc,u.created_at desc,u.id desc
 limit greatest(1,least(coalesce(p_limit,50),100));
end
$$;

create or replace function public.blink_channel_publish_rich(
 p_channel_id uuid,p_body text,p_media_kind text default 'text',
 p_media_path text default null,p_poll_question text default null,
 p_poll_options jsonb default null,p_scheduled_at timestamptz default null,
 p_forwarded_from uuid default null
) returns uuid language plpgsql security definer set search_path=''
as $$
declare v_id uuid; v_media_path text:=nullif(btrim(p_media_path),'');
begin
 if not public.blink_channel_is_admin(p_channel_id) then
   raise exception 'CHANNEL_ADMIN_REQUIRED' using errcode='42501';
 end if;
 if p_media_kind not in ('text','image','video','audio','document','poll') then
   raise exception 'INVALID_UPDATE_TYPE';
 end if;
 if length(btrim(coalesce(p_body,'')))>5000 then raise exception 'INVALID_TEXT_LENGTH'; end if;
 if p_media_kind in ('image','video','audio','document') then
   if v_media_path is null or
     left(v_media_path,length(p_channel_id::text||'/'||auth.uid()::text||'/'))
       != p_channel_id::text||'/'||auth.uid()::text||'/' or
     v_media_path !~ ('^[-a-f0-9]+/[-a-f0-9]+/[-a-f0-9]+[.](jpg|jpeg|png|webp|mp4|mp3|m4a|pdf)' || chr(36))
   then raise exception 'INVALID_CHANNEL_MEDIA_PATH'; end if;
 elsif v_media_path is not null then raise exception 'MEDIA_NOT_ALLOWED'; end if;
 if p_media_kind='poll' then
   if length(btrim(coalesce(p_poll_question,''))) not between 3 and 300 or
      jsonb_typeof(p_poll_options)!='array' or
      jsonb_array_length(p_poll_options) not between 2 and 8 or
      exists(select 1 from jsonb_array_elements_text(p_poll_options) as item(value)
             where length(btrim(item.value)) not between 1 and 100)
   then raise exception 'INVALID_CHANNEL_POLL'; end if;
 elsif p_poll_question is not null or p_poll_options is not null then
   raise exception 'POLL_NOT_ALLOWED'; end if;
 if p_media_kind='text' and length(btrim(coalesce(p_body,'')))=0 then
   raise exception 'EMPTY_CHANNEL_UPDATE';
 end if;
 if p_scheduled_at is not null and
   (p_scheduled_at < now() or p_scheduled_at > now()+interval '180 days')
 then raise exception 'INVALID_SCHEDULE_TIME'; end if;
 if p_forwarded_from is not null and not exists (
   select 1 from public.blink_channel_updates other
   where other.id=p_forwarded_from and other.removed_at is null
     and public.blink_channel_can_read(other.channel_id)
 ) then raise exception 'SOURCE_UPDATE_UNAVAILABLE'; end if;
 insert into public.blink_channel_updates
   (channel_id,author_id,body,media_kind,media_path,poll_question,poll_options,
    scheduled_at,forwarded_from)
 values(p_channel_id,auth.uid(),btrim(coalesce(p_body,'')),p_media_kind,
   v_media_path,p_poll_question,p_poll_options,p_scheduled_at,p_forwarded_from)
 returning id into v_id;
 return v_id;
end
$$;


-- Older clients can call this RPC directly: enforce publication/soft deletion
-- there too, not only in the new updates-page RPC.
create or replace function public.blink_channel_react(p_update_id uuid,p_emoji text)
returns text language plpgsql security definer set search_path=''
as $$
declare v_channel uuid; v_old text;
begin
 if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
 if p_emoji not in ('👍','❤️','😂','😮','😢','🔥') then
   raise exception 'INVALID_REACTION'; end if;
 select u.channel_id into v_channel from public.blink_channel_updates u
 where u.id=p_update_id and u.removed_at is null
   and (u.scheduled_at is null or u.scheduled_at<=now());
 if v_channel is null or not public.blink_channel_can_read(v_channel) then
   raise exception 'CHANNEL_UPDATE_UNAVAILABLE' using errcode='42501'; end if;
 if not public.blink_channel_is_admin(v_channel) and not exists(
    select 1 from public.blink_community_members m
    where m.community_id=v_channel and m.user_id=auth.uid() and m.status='active')
 then raise exception 'FOLLOW_TO_REACT' using errcode='42501'; end if;
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
  set emoji=excluded.emoji,created_at=now();
 return p_emoji;
end
$$;

create or replace function public.blink_channel_poll_vote(
 p_update_id uuid,p_option integer
) returns boolean language plpgsql security definer set search_path=''
as $$
declare v_channel uuid; v_options jsonb;
begin
 select u.channel_id,u.poll_options into v_channel,v_options
 from public.blink_channel_updates u
 where u.id=p_update_id and u.media_kind='poll' and u.removed_at is null
   and (u.scheduled_at is null or u.scheduled_at<=now());
 if v_channel is null or not public.blink_channel_can_read(v_channel) then
   raise exception 'CHANNEL_POLL_UNAVAILABLE' using errcode='42501'; end if;
 if p_option<0 or p_option>=jsonb_array_length(v_options) then
   raise exception 'INVALID_POLL_OPTION'; end if;
 if not public.blink_channel_is_admin(v_channel) and not exists(
   select 1 from public.blink_community_members m
   where m.community_id=v_channel and m.user_id=auth.uid() and m.status='active')
 then raise exception 'FOLLOW_TO_VOTE' using errcode='42501'; end if;
 insert into public.blink_channel_poll_votes(update_id,user_id,selected_option)
 values(p_update_id,auth.uid(),p_option)
 on conflict(update_id,user_id) do update set selected_option=excluded.selected_option;
 return true;
end
$$;

create or replace function public.blink_channel_view(p_update_id uuid)
returns boolean language plpgsql security definer set search_path=''
as $$
declare v_channel uuid;
begin
 select u.channel_id into v_channel from public.blink_channel_updates u
 where u.id=p_update_id and u.removed_at is null
 and (u.scheduled_at is null or u.scheduled_at<=now());
 if v_channel is null or not public.blink_channel_can_read(v_channel) then
   return false; end if;
 insert into public.blink_channel_views(update_id,user_id)
   values(p_update_id,auth.uid()) on conflict do nothing;
 return true;
end
$$;

create or replace function public.blink_channel_report(
 p_update_id uuid,p_reason text
) returns boolean language plpgsql security definer set search_path=''
as $$
declare v_channel uuid;
begin
 select u.channel_id into v_channel from public.blink_channel_updates u
 where u.id=p_update_id and u.removed_at is null;
 if v_channel is null or not public.blink_channel_can_read(v_channel) then
   raise exception 'CHANNEL_UPDATE_UNAVAILABLE' using errcode='42501'; end if;
 if length(btrim(coalesce(p_reason,''))) not between 3 and 280 then
   raise exception 'INVALID_REPORT_REASON'; end if;
 insert into public.blink_channel_reports(update_id,reporter_id,reason)
 values(p_update_id,auth.uid(),btrim(p_reason))
 on conflict(update_id,reporter_id) do nothing;
 return true;
end
$$;

create or replace function public.blink_channel_manage_update(
 p_update_id uuid,p_action text
) returns boolean language plpgsql security definer set search_path=''
as $$
declare v_channel uuid;
begin
 select u.channel_id into v_channel from public.blink_channel_updates u
 where u.id=p_update_id;
 if v_channel is null or not public.blink_channel_is_admin(v_channel) then
   raise exception 'CHANNEL_ADMIN_REQUIRED' using errcode='42501'; end if;
 if p_action='remove' then
   update public.blink_channel_updates set removed_at=now(),pinned=false
   where id=p_update_id and removed_at is null;
 elsif p_action='pin' then
   update public.blink_channel_updates set pinned=false
   where channel_id=v_channel and pinned=true;
   update public.blink_channel_updates set pinned=true
   where id=p_update_id and removed_at is null;
 elsif p_action='unpin' then
   update public.blink_channel_updates set pinned=false where id=p_update_id;
 else raise exception 'INVALID_CHANNEL_ACTION';
 end if;
 return found;
end
$$;

create or replace function public.blink_channel_statistics(p_channel_id uuid)
returns jsonb language plpgsql stable security definer set search_path=''
as $$
begin
 if not public.blink_channel_is_admin(p_channel_id) then
   raise exception 'CHANNEL_ADMIN_REQUIRED' using errcode='42501'; end if;
 return jsonb_build_object(
   'followers',(select count(*) from public.blink_community_members m
                where m.community_id=p_channel_id and m.status='active'),
   'updates',(select count(*) from public.blink_channel_updates u
               where u.channel_id=p_channel_id and u.removed_at is null),
   'views',(select count(*) from public.blink_channel_views v
            join public.blink_channel_updates u on u.id=v.update_id
            where u.channel_id=p_channel_id),
   'reactions',(select count(*) from public.blink_channel_reactions r
               join public.blink_channel_updates u on u.id=r.update_id
               where u.channel_id=p_channel_id),
   'poll_votes',(select count(*) from public.blink_channel_poll_votes pv
                join public.blink_channel_updates u on u.id=pv.update_id
                where u.channel_id=p_channel_id)
 );
end
$$;


-- Existing direct Data API reads cannot bypass the RPC's scheduled/removed
-- visibility filters, otherwise ordinary followers could read future posts.
revoke select on public.blink_channel_updates from authenticated,anon;

create or replace function public.blink_channel_mute(
 p_channel_id uuid,p_muted boolean
) returns boolean language plpgsql security definer set search_path=''
as $$
begin
 if auth.uid() is null or not public.blink_channel_can_read(p_channel_id) then
   raise exception 'CHANNEL_ACCESS_DENIED' using errcode='42501'; end if;
 insert into public.blink_channel_notifications(channel_id,user_id,muted)
 values(p_channel_id,auth.uid(),coalesce(p_muted,false))
 on conflict(channel_id,user_id) do update set muted=excluded.muted;
 return true;
end
$$;

create or replace function public.blink_channel_is_muted(p_channel_id uuid)
returns boolean language sql stable security definer set search_path=''
as $$
 select case when not public.blink_channel_can_read(p_channel_id) then true
 else coalesce((select muted from public.blink_channel_notifications
                where channel_id=p_channel_id and user_id=auth.uid()),false)
 end
$$;
revoke all on function public.blink_channel_mute(uuid,boolean),
 public.blink_channel_is_muted(uuid) from public,anon;
grant execute on function public.blink_channel_mute(uuid,boolean),
 public.blink_channel_is_muted(uuid) to authenticated;


-- A public channel can be previewed without following; notification/read
-- cursors remain private to the signed-in viewer.
create or replace function public.blink_channel_unread_count(p_channel_id uuid)
returns integer language sql stable security definer set search_path=''
as $$
 select case when public.blink_channel_can_read(p_channel_id) then
  least(99, (select count(*)::integer from public.blink_channel_updates u
   where u.channel_id=p_channel_id and u.removed_at is null
     and (u.scheduled_at is null or u.scheduled_at<=now())
     and u.created_at > coalesce((
       select n.last_seen_at from public.blink_channel_notifications n
       where n.channel_id=p_channel_id and n.user_id=auth.uid()),'-infinity'::timestamptz)
  )) else 0 end;
$$;
create or replace function public.blink_channel_mark_seen(p_channel_id uuid)
returns boolean language plpgsql security definer set search_path=''
as $$
begin
 if not public.blink_channel_can_read(p_channel_id) then
   raise exception 'CHANNEL_ACCESS_DENIED' using errcode='42501'; end if;
 insert into public.blink_channel_notifications(channel_id,user_id,last_seen_at)
   values(p_channel_id,auth.uid(),now())
   on conflict(channel_id,user_id) do update set last_seen_at=excluded.last_seen_at;
 return true;
end
$$;
create or replace function public.blink_channel_scheduled_page(p_channel_id uuid)
returns table(id uuid,body text,scheduled_at timestamptz)
language plpgsql stable security definer set search_path=''
as $$
begin
 if not public.blink_channel_is_admin(p_channel_id) then
  raise exception 'CHANNEL_ADMIN_REQUIRED' using errcode='42501'; end if;
 return query
 select u.id,u.body,u.scheduled_at from public.blink_channel_updates u
 where u.channel_id=p_channel_id and u.scheduled_at>now() and u.removed_at is null
 order by u.scheduled_at asc limit 100;
end
$$;
create or replace function public.blink_channel_cancel_scheduled(p_update_id uuid)
returns boolean language plpgsql security definer set search_path=''
as $$
declare v_channel uuid;
begin
 select u.channel_id into v_channel from public.blink_channel_updates u
 where u.id=p_update_id and u.scheduled_at>now() and u.removed_at is null;
 if v_channel is null or not public.blink_channel_is_admin(v_channel) then
  raise exception 'CHANNEL_ADMIN_REQUIRED' using errcode='42501'; end if;
 update public.blink_channel_updates set removed_at=now()
 where id=p_update_id;
 return found;
end
$$;
revoke all on function public.blink_channel_unread_count(uuid),
 public.blink_channel_mark_seen(uuid),
 public.blink_channel_scheduled_page(uuid),
 public.blink_channel_cancel_scheduled(uuid) from public,anon;
grant execute on function public.blink_channel_unread_count(uuid),
 public.blink_channel_mark_seen(uuid),
 public.blink_channel_scheduled_page(uuid),
 public.blink_channel_cancel_scheduled(uuid) to authenticated;


-- Channel moderation inbox: reporter identity remains private even from
-- channel administrators; only aggregate issue, reason and update ID appear.
alter table public.blink_channel_reports
 add column if not exists reviewed_at timestamptz,
 add column if not exists resolution text;

create or replace function public.blink_channel_reports_page(p_channel_id uuid)
returns table(id uuid,update_id uuid,reason text,created_at timestamptz,reviewed boolean)
language plpgsql stable security definer set search_path=''
as $$
begin
 if not public.blink_channel_is_admin(p_channel_id) then
  raise exception 'CHANNEL_ADMIN_REQUIRED' using errcode='42501'; end if;
 return query
 select r.id,r.update_id,r.reason,r.created_at,r.reviewed_at is not null
 from public.blink_channel_reports r
 join public.blink_channel_updates u on u.id=r.update_id
 where u.channel_id=p_channel_id
 order by (r.reviewed_at is null) desc,r.created_at desc limit 100;
end
$$;

create or replace function public.blink_channel_review_report(p_report_id uuid,p_remove boolean)
returns boolean language plpgsql security definer set search_path=''
as $$
declare v_update uuid; v_channel uuid;
begin
 select r.update_id,u.channel_id into v_update,v_channel
 from public.blink_channel_reports r
 join public.blink_channel_updates u on u.id=r.update_id
 where r.id=p_report_id;
 if v_channel is null or not public.blink_channel_is_admin(v_channel) then
   raise exception 'CHANNEL_ADMIN_REQUIRED' using errcode='42501'; end if;
 if p_remove then
   update public.blink_channel_updates set removed_at=now(),pinned=false
   where id=v_update and removed_at is null;
 end if;
 update public.blink_channel_reports
 set reviewed_at=now(),resolution=case when p_remove then 'removed' else 'dismissed' end
 where id=p_report_id;
 return found;
end
$$;
revoke all on function public.blink_channel_reports_page(uuid),
 public.blink_channel_review_report(uuid,boolean) from public,anon;
grant execute on function public.blink_channel_reports_page(uuid),
 public.blink_channel_review_report(uuid,boolean) to authenticated;

revoke all on function public.blink_channel_updates_page(uuid,integer),
 public.blink_channel_publish_rich(uuid,text,text,text,text,jsonb,timestamptz,uuid),
 public.blink_channel_poll_vote(uuid,integer),
 public.blink_channel_view(uuid),
 public.blink_channel_report(uuid,text),
 public.blink_channel_manage_update(uuid,text),
 public.blink_channel_statistics(uuid) from public,anon;
grant execute on function public.blink_channel_updates_page(uuid,integer),
 public.blink_channel_publish_rich(uuid,text,text,text,text,jsonb,timestamptz,uuid),
 public.blink_channel_poll_vote(uuid,integer),
 public.blink_channel_view(uuid),
 public.blink_channel_report(uuid,text),
 public.blink_channel_manage_update(uuid,text),
 public.blink_channel_statistics(uuid) to authenticated;
-- Push delivery is NOT enabled here. FCM requires an authenticated fanout
-- worker, retries, per-channel mute checks, and verified device token ownership.
-- A successful SQL transaction alone is not evidence of a delivered push.
