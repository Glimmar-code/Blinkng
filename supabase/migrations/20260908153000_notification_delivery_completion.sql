-- Blink notification delivery completion
-- Additive hardening only: existing notification/activity rows remain intact.

create table if not exists public.notification_preferences (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  master_enabled boolean not null default true,
  messages_enabled boolean not null default true,
  calls_enabled boolean not null default true,
  social_enabled boolean not null default true,
  mentions_enabled boolean not null default true,
  comments_enabled boolean not null default true,
  follows_enabled boolean not null default true,
  market_enabled boolean not null default true,
  admin_enabled boolean not null default true,
  coins_enabled boolean not null default true,
  stories_enabled boolean not null default true,
  reels_enabled boolean not null default true,
  vip_enabled boolean not null default true,
  boosts_enabled boolean not null default true,
  security_enabled boolean not null default true,
  quiet_hours_enabled boolean not null default false,
  quiet_start_minute smallint not null default 1320 check (quiet_start_minute between 0 and 1439),
  quiet_end_minute smallint not null default 420 check (quiet_end_minute between 0 and 1439),
  timezone text not null default 'UTC',
  updated_at timestamptz not null default now()
);

alter table public.notification_preferences enable row level security;

drop policy if exists notification_preferences_select_own on public.notification_preferences;
create policy notification_preferences_select_own
on public.notification_preferences
for select
to authenticated
using (user_id = auth.uid());

drop policy if exists notification_preferences_insert_own on public.notification_preferences;
create policy notification_preferences_insert_own
on public.notification_preferences
for insert
to authenticated
with check (user_id = auth.uid());

drop policy if exists notification_preferences_update_own on public.notification_preferences;
create policy notification_preferences_update_own
on public.notification_preferences
for update
to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

create or replace function public.touch_notification_preferences_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists trg_touch_notification_preferences on public.notification_preferences;
create trigger trg_touch_notification_preferences
before update on public.notification_preferences
for each row execute function public.touch_notification_preferences_updated_at();

-- Durable server-side idempotency for social/activity pushes.
create table if not exists public.notification_push_dispatches (
  notification_id uuid primary key references public.notifications(id) on delete cascade,
  status text not null default 'sending' check (status in ('sending','sent','failed')),
  attempts integer not null default 1 check (attempts > 0),
  last_error text,
  updated_at timestamptz not null default now()
);

alter table public.notification_push_dispatches enable row level security;
-- No client policies: only service-role Edge Functions manage this table.

-- Give notification rows durable target/read metadata without breaking existing clients.
alter table public.notifications
  add column if not exists target_type text,
  add column if not exists target_id uuid,
  add column if not exists read_at timestamptz,
  add column if not exists metadata jsonb not null default '{}'::jsonb;

create index if not exists idx_notifications_user_unread_created
  on public.notifications(user_id, created_at desc)
  where is_read = false;

create index if not exists idx_notifications_target
  on public.notifications(target_type, target_id)
  where target_id is not null;

create or replace function public.sync_notification_read_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.is_read then
    new.read_at := coalesce(new.read_at, now());
  elsif tg_op = 'UPDATE' and old.is_read and not new.is_read then
    new.read_at := null;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_sync_notification_read_at on public.notifications;
create trigger trg_sync_notification_read_at
before insert or update of is_read on public.notifications
for each row execute function public.sync_notification_read_at();

-- Route a newly-created actor notification to the authenticated Edge Function.
-- Message rows already have their own dedicated push pipeline and are skipped here.
create or replace function public.dispatch_notification_push_after_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_authorization text;
begin
  if new.actor_id is null then
    return new;
  end if;

  if new.type::text = 'system' and coalesce(new.text, '') ilike '% sent you a message%' then
    return new;
  end if;

  begin
    v_authorization := nullif(
      (current_setting('request.headers', true)::jsonb ->> 'authorization'),
      ''
    );
  exception when others then
    v_authorization := null;
  end;

  if v_authorization is not null then
    perform net.http_post(
      url := 'https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/send-activity-notification',
      body := jsonb_build_object('notification_id', new.id::text),
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'Authorization', v_authorization
      ),
      timeout_milliseconds := 5000
    );
  end if;

  return new;
end;
$$;

drop trigger if exists trg_dispatch_notification_push_after_insert on public.notifications;
create trigger trg_dispatch_notification_push_after_insert
after insert on public.notifications
for each row execute function public.dispatch_notification_push_after_insert();

-- Keep the existing Activity feed and also create authoritative notification rows.
create or replace function public.activity_from_post_like()
returns trigger
language plpgsql
security definer
set search_path = 'public', 'pg_temp'
as $$
declare
  v_owner_id uuid;
begin
  select fp.user_id into v_owner_id
  from public.feed_posts fp
  where fp.id = new.post_id;

  if v_owner_id is not null
     and v_owner_id <> new.user_id
     and not exists (
       select 1 from public.blocks b
       where (b.blocker_id = new.user_id and b.blocked_id = v_owner_id)
          or (b.blocker_id = v_owner_id and b.blocked_id = new.user_id)
     ) then
    insert into public.activities(
      recipient_id, actor_id, activity_type, entity_type, entity_id, message, is_read
    ) values (
      v_owner_id, new.user_id, 'LIKE', 'post', new.post_id, 'liked your post', false
    );

    insert into public.notifications(
      user_id, actor_id, type, post_id, target_type, target_id, text, sub_text, is_read
    ) values (
      v_owner_id, new.user_id, 'like', new.post_id, 'post', new.post_id,
      'liked your post', null, false
    );
  end if;

  return new;
end;
$$;

create or replace function public.activity_from_post_comment()
returns trigger
language plpgsql
security definer
set search_path = 'public', 'pg_temp'
as $$
declare
  v_recipient uuid;
  v_message text;
begin
  if new.parent_comment_id is null then
    select fp.user_id into v_recipient
    from public.feed_posts fp
    where fp.id = new.post_id;
    v_message := 'commented on your post';
  else
    select c.author_id into v_recipient
    from public.comments c
    where c.id = new.parent_comment_id;
    v_message := 'replied to your comment';
  end if;

  if v_recipient is not null
     and v_recipient <> new.author_id
     and not exists (
       select 1 from public.blocks b
       where (b.blocker_id = new.author_id and b.blocked_id = v_recipient)
          or (b.blocker_id = v_recipient and b.blocked_id = new.author_id)
     ) then
    insert into public.activities(
      recipient_id, actor_id, activity_type, entity_type, entity_id, message, is_read
    ) values (
      v_recipient,
      new.author_id,
      case when new.parent_comment_id is null then 'COMMENT' else 'REPLY' end,
      'post',
      new.post_id,
      v_message,
      false
    );

    insert into public.notifications(
      user_id, actor_id, type, post_id, target_type, target_id, text, sub_text, is_read, metadata
    ) values (
      v_recipient,
      new.author_id,
      'comment',
      new.post_id,
      'post',
      new.post_id,
      v_message,
      left(coalesce(new.content, ''), 240),
      false,
      jsonb_build_object(
        'comment_id', new.id,
        'parent_comment_id', new.parent_comment_id,
        'is_reply', new.parent_comment_id is not null
      )
    );
  end if;

  return new;
end;
$$;

create or replace function public.activity_from_comment_mention()
returns trigger
language plpgsql
security definer
set search_path = 'public', 'pg_temp'
as $$
declare
  v_actor uuid;
  v_post_id uuid;
  v_parent_comment_id uuid;
  v_parent_author uuid;
  v_post_owner uuid;
  v_content text;
begin
  select c.author_id, c.post_id, c.parent_comment_id, parent.author_id, fp.user_id, c.content
    into v_actor, v_post_id, v_parent_comment_id, v_parent_author, v_post_owner, v_content
  from public.comments c
  join public.feed_posts fp on fp.id = c.post_id
  left join public.comments parent on parent.id = c.parent_comment_id
  where c.id = new.comment_id;

  if v_actor is null or new.mentioned_user_id = v_actor then
    return new;
  end if;

  -- A post comment/reply already creates the more specific notification.
  if (v_parent_comment_id is null and new.mentioned_user_id = v_post_owner)
     or (v_parent_comment_id is not null and new.mentioned_user_id = v_parent_author) then
    return new;
  end if;

  if exists (
    select 1 from public.blocks b
    where (b.blocker_id = v_actor and b.blocked_id = new.mentioned_user_id)
       or (b.blocker_id = new.mentioned_user_id and b.blocked_id = v_actor)
  ) then
    return new;
  end if;

  insert into public.activities(
    recipient_id, actor_id, activity_type, entity_type, entity_id, message, is_read
  ) values (
    new.mentioned_user_id, v_actor, 'MENTION', 'post', v_post_id,
    'mentioned you in a comment', false
  );

  insert into public.notifications(
    user_id, actor_id, type, post_id, target_type, target_id, text, sub_text, is_read, metadata
  ) values (
    new.mentioned_user_id, v_actor, 'mention', v_post_id, 'post', v_post_id,
    'mentioned you in a comment', left(coalesce(v_content, ''), 240), false,
    jsonb_build_object('comment_id', new.comment_id)
  );

  return new;
end;
$$;

create or replace function public.notify_post_repost_created()
returns trigger
language plpgsql
security definer
set search_path = 'public', 'pg_temp'
as $$
declare
  v_owner_id uuid;
begin
  select fp.user_id into v_owner_id from public.feed_posts fp where fp.id = new.post_id;
  if v_owner_id is not null
     and v_owner_id <> new.user_id
     and not exists (
       select 1 from public.blocks b
       where (b.blocker_id = new.user_id and b.blocked_id = v_owner_id)
          or (b.blocker_id = v_owner_id and b.blocked_id = new.user_id)
     ) then
    insert into public.notifications(
      user_id, actor_id, type, post_id, target_type, target_id, text, is_read
    ) values (
      v_owner_id, new.user_id, 'repost', new.post_id, 'post', new.post_id,
      'reposted your post', false
    );
  end if;
  return new;
end;
$$;

drop trigger if exists trg_notify_post_repost_created on public.post_reposts;
create trigger trg_notify_post_repost_created
after insert on public.post_reposts
for each row execute function public.notify_post_repost_created();

create or replace function public.notify_comment_like_created()
returns trigger
language plpgsql
security definer
set search_path = 'public', 'pg_temp'
as $$
declare
  v_recipient uuid;
  v_post_id uuid;
  v_comment text;
begin
  select c.author_id, c.post_id, c.content
    into v_recipient, v_post_id, v_comment
  from public.comments c
  where c.id = new.comment_id;

  if v_recipient is not null
     and v_recipient <> new.user_id
     and not exists (
       select 1 from public.blocks b
       where (b.blocker_id = new.user_id and b.blocked_id = v_recipient)
          or (b.blocker_id = v_recipient and b.blocked_id = new.user_id)
     ) then
    insert into public.notifications(
      user_id, actor_id, type, post_id, target_type, target_id, text, sub_text, is_read, metadata
    ) values (
      v_recipient, new.user_id, 'like', v_post_id, 'comment', new.comment_id,
      'liked your comment', left(coalesce(v_comment, ''), 240), false,
      jsonb_build_object('comment_id', new.comment_id)
    );
  end if;
  return new;
end;
$$;

drop trigger if exists trg_notify_comment_like_created on public.comment_likes;
create trigger trg_notify_comment_like_created
after insert on public.comment_likes
for each row execute function public.notify_comment_like_created();

create or replace function public.notify_story_like_created()
returns trigger
language plpgsql
security definer
set search_path = 'public', 'pg_temp'
as $$
declare
  v_recipient uuid;
begin
  select s.user_id into v_recipient from public.stories s where s.id = new.story_id;
  if v_recipient is not null
     and v_recipient <> new.user_id
     and not exists (
       select 1 from public.blocks b
       where (b.blocker_id = new.user_id and b.blocked_id = v_recipient)
          or (b.blocker_id = v_recipient and b.blocked_id = new.user_id)
     ) then
    insert into public.notifications(
      user_id, actor_id, type, target_type, target_id, text, is_read, metadata
    ) values (
      v_recipient, new.user_id, 'like', 'story', new.story_id,
      'liked your story', false, jsonb_build_object('story_id', new.story_id)
    );
  end if;
  return new;
end;
$$;

drop trigger if exists trg_notify_story_like_created on public.story_likes;
create trigger trg_notify_story_like_created
after insert on public.story_likes
for each row execute function public.notify_story_like_created();

-- Follow notifications now carry a typed target while preserving current wording.
create or replace function public.notify_follow_created()
returns trigger
language plpgsql
security definer
set search_path = 'public', 'pg_temp'
as $$
declare
  v_actor_username text;
begin
  if new.follower_id <> new.following_id then
    select p.username into v_actor_username from public.profiles p where p.id = new.follower_id;
    insert into public.notifications(
      user_id, actor_id, type, target_type, target_id, text, sub_text, is_read
    ) values (
      new.following_id,
      new.follower_id,
      'follow',
      'profile',
      new.follower_id,
      coalesce('@' || v_actor_username, 'Someone') || ' started following you',
      null,
      false
    );
  end if;
  return new;
end;
$$;

-- Helper used by server-side dispatchers and diagnostics.
create or replace function public.notification_push_allowed(
  p_user_id uuid,
  p_type text,
  p_at timestamptz default now()
)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  p public.notification_preferences%rowtype;
  v_enabled boolean := true;
  v_local timestamp;
  v_minute integer;
  v_quiet boolean := false;
begin
  select * into p from public.notification_preferences where user_id = p_user_id;
  if not found then
    return true;
  end if;
  if not p.master_enabled then
    return false;
  end if;

  v_enabled := case lower(coalesce(p_type, 'social'))
    when 'message' then p.messages_enabled
    when 'incoming_call' then p.calls_enabled
    when 'call_update' then p.calls_enabled
    when 'mention' then p.mentions_enabled
    when 'comment' then p.comments_enabled
    when 'reply' then p.comments_enabled
    when 'follow' then p.follows_enabled
    when 'market' then p.market_enabled
    when 'market_order' then p.market_enabled
    when 'admin' then p.admin_enabled
    when 'security' then p.security_enabled
    when 'coin' then p.coins_enabled
    when 'story' then p.stories_enabled
    when 'story_like' then p.stories_enabled
    when 'reel' then p.reels_enabled
    when 'vip' then p.vip_enabled
    when 'boost' then p.boosts_enabled
    else p.social_enabled
  end;

  if not v_enabled then
    return false;
  end if;

  -- Security alerts bypass quiet hours. Calls also bypass quiet hours so Android/DND
  -- remains the authority for whether a real-time call may interrupt the user.
  if not p.quiet_hours_enabled
     or lower(coalesce(p_type, 'social')) in ('security', 'incoming_call', 'call_update') then
    return true;
  end if;

  begin
    v_local := p_at at time zone p.timezone;
  exception when others then
    v_local := p_at at time zone 'UTC';
  end;
  v_minute := extract(hour from v_local)::integer * 60 + extract(minute from v_local)::integer;

  if p.quiet_start_minute = p.quiet_end_minute then
    v_quiet := false;
  elsif p.quiet_start_minute < p.quiet_end_minute then
    v_quiet := v_minute >= p.quiet_start_minute and v_minute < p.quiet_end_minute;
  else
    v_quiet := v_minute >= p.quiet_start_minute or v_minute < p.quiet_end_minute;
  end if;

  return not v_quiet;
end;
$$;

revoke all on function public.notification_push_allowed(uuid, text, timestamptz) from public;
grant execute on function public.notification_push_allowed(uuid, text, timestamptz) to service_role;
