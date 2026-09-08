-- Blinkng professional chat foundations shared by Android and Windows.
-- Adds group governance, privacy, per-device state, media metadata and Realtime presence.

alter table public.conversations
  add column if not exists topic text,
  add column if not exists rules text,
  add column if not exists cover_url text,
  add column if not exists announcement_only boolean not null default false,
  add column if not exists join_approval_required boolean not null default false,
  add column if not exists slow_mode_seconds integer not null default 0,
  add column if not exists who_can_invite text not null default 'admins',
  add column if not exists who_can_message text not null default 'members',
  add column if not exists who_can_call text not null default 'members',
  add column if not exists who_can_post_media text not null default 'members',
  add column if not exists who_can_post_links text not null default 'members',
  add column if not exists who_can_post_polls text not null default 'members';

alter table public.conversation_participants
  add column if not exists role text not null default 'member',
  add column if not exists can_message boolean not null default true,
  add column if not exists can_call boolean not null default true,
  add column if not exists can_post_media boolean not null default true,
  add column if not exists can_post_links boolean not null default true,
  add column if not exists can_post_polls boolean not null default true,
  add column if not exists muted_by_admin_until timestamptz,
  add column if not exists left_at timestamptz;

update public.conversation_participants cp
set role = 'admin'
where cp.is_admin = true and cp.role = 'member';

update public.conversation_participants cp
set role = 'owner', is_admin = true
from public.conversations c
where c.id = cp.conversation_id
  and c.created_by = cp.user_id;

create or replace function private.guard_conversation_participant_privileges()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
begin
  if new.conversation_id is distinct from old.conversation_id
     or new.user_id is distinct from old.user_id
     or new.joined_at is distinct from old.joined_at then
    raise exception 'CONVERSATION_PARTICIPANT_IDENTITY_IMMUTABLE';
  end if;

  if v_me is not null and v_me = old.user_id and (
       new.role is distinct from old.role
       or new.is_admin is distinct from old.is_admin
       or new.can_message is distinct from old.can_message
       or new.can_call is distinct from old.can_call
       or new.can_post_media is distinct from old.can_post_media
       or new.can_post_links is distinct from old.can_post_links
       or new.can_post_polls is distinct from old.can_post_polls
       or new.muted_by_admin_until is distinct from old.muted_by_admin_until
     ) then
    raise exception 'PARTICIPANT_CANNOT_SELF_ELEVATE';
  end if;

  return new;
end;
$$;

revoke all on function private.guard_conversation_participant_privileges() from public, anon, authenticated;

drop trigger if exists trg_guard_conversation_participant_privileges on public.conversation_participants;
create trigger trg_guard_conversation_participant_privileges
before update on public.conversation_participants
for each row execute function private.guard_conversation_participant_privileges();

create table if not exists public.user_chat_privacy (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  who_can_message text not null default 'everyone' check (who_can_message in ('everyone','followers','mutuals','connections','nobody')),
  who_can_call text not null default 'everyone' check (who_can_call in ('everyone','followers','mutuals','connections','nobody')),
  who_can_group_invite text not null default 'everyone' check (who_can_group_invite in ('everyone','followers','mutuals','connections','nobody')),
  show_online boolean not null default true,
  show_last_seen boolean not null default true,
  send_read_receipts boolean not null default true,
  show_typing boolean not null default true,
  show_recording boolean not null default true,
  show_profile_photo_in_chat boolean not null default true,
  allow_link_previews boolean not null default true,
  notification_preview text not null default 'full' check (notification_preview in ('full','sender_only','hidden')),
  updated_at timestamptz not null default now()
);

alter table public.user_chat_privacy enable row level security;
drop policy if exists "user_chat_privacy_select_self" on public.user_chat_privacy;
create policy "user_chat_privacy_select_self" on public.user_chat_privacy
for select to authenticated using (user_id = (select auth.uid()));
drop policy if exists "user_chat_privacy_insert_self" on public.user_chat_privacy;
create policy "user_chat_privacy_insert_self" on public.user_chat_privacy
for insert to authenticated with check (user_id = (select auth.uid()));
drop policy if exists "user_chat_privacy_update_self" on public.user_chat_privacy;
create policy "user_chat_privacy_update_self" on public.user_chat_privacy
for update to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
drop policy if exists "user_chat_privacy_delete_self" on public.user_chat_privacy;
create policy "user_chat_privacy_delete_self" on public.user_chat_privacy
for delete to authenticated using (user_id = (select auth.uid()));
revoke all on public.user_chat_privacy from anon;
grant select, insert, update, delete on public.user_chat_privacy to authenticated;

create or replace function private.chat_scope_allows(p_actor uuid, p_target uuid, p_scope text)
returns boolean
language plpgsql
security definer
set search_path = ''
stable
as $$
declare
  v_mode text;
  v_blocked boolean;
begin
  if p_actor is null or p_target is null or p_actor = p_target then
    return false;
  end if;

  select exists (
    select 1 from public.blocks b
    where (b.blocker_id = p_actor and b.blocked_id = p_target)
       or (b.blocker_id = p_target and b.blocked_id = p_actor)
  ) into v_blocked;
  if v_blocked then return false; end if;

  select case p_scope
    when 'call' then coalesce(p.who_can_call, 'everyone')
    when 'group_invite' then coalesce(p.who_can_group_invite, 'everyone')
    else coalesce(p.who_can_message, 'everyone')
  end
  into v_mode
  from public.user_chat_privacy p
  where p.user_id = p_target;

  v_mode := coalesce(v_mode, 'everyone');
  if v_mode = 'everyone' then return true; end if;
  if v_mode = 'nobody' then return false; end if;

  if v_mode = 'followers' then
    return exists (
      select 1 from public.follows f
      where f.follower_id = p_actor and f.following_id = p_target
    );
  end if;

  if v_mode = 'mutuals' then
    return exists (
      select 1 from public.follows f
      where f.follower_id = p_actor and f.following_id = p_target
    ) and exists (
      select 1 from public.follows f
      where f.follower_id = p_target and f.following_id = p_actor
    );
  end if;

  if v_mode = 'connections' then
    return exists (
      select 1 from public.connection_requests r
      where r.status = 'accepted'
        and ((r.sender_id = p_actor and r.receiver_id = p_target)
          or (r.sender_id = p_target and r.receiver_id = p_actor))
    );
  end if;

  return false;
end;
$$;

revoke all on function private.chat_scope_allows(uuid, uuid, text) from public, anon, authenticated;

create table if not exists public.conversation_drafts (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  device_id text not null,
  content text not null default '',
  reply_to_message_id uuid references public.messages(id) on delete set null,
  attachment_draft jsonb not null default '[]'::jsonb,
  updated_at timestamptz not null default now(),
  primary key (conversation_id, user_id, device_id)
);

alter table public.conversation_drafts enable row level security;
drop policy if exists "conversation_drafts_self" on public.conversation_drafts;
create policy "conversation_drafts_self" on public.conversation_drafts
for all to authenticated
using (
  user_id = (select auth.uid()) and exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = conversation_drafts.conversation_id
      and cp.user_id = (select auth.uid())
      and cp.left_at is null
  )
)
with check (
  user_id = (select auth.uid()) and exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = conversation_drafts.conversation_id
      and cp.user_id = (select auth.uid())
      and cp.left_at is null
  )
);
revoke all on public.conversation_drafts from anon;
grant select, insert, update, delete on public.conversation_drafts to authenticated;

create table if not exists public.conversation_presence (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  device_id text not null,
  state text not null default 'online' check (state in ('online','typing','recording','uploading')),
  updated_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '30 seconds'),
  primary key (conversation_id, user_id, device_id)
);

create index if not exists conversation_presence_expiry_idx on public.conversation_presence(expires_at);
alter table public.conversation_presence enable row level security;
drop policy if exists "conversation_presence_member_select" on public.conversation_presence;
create policy "conversation_presence_member_select" on public.conversation_presence
for select to authenticated
using (
  exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = conversation_presence.conversation_id
      and cp.user_id = (select auth.uid())
      and cp.left_at is null
  )
);
drop policy if exists "conversation_presence_self_insert" on public.conversation_presence;
create policy "conversation_presence_self_insert" on public.conversation_presence
for insert to authenticated
with check (
  user_id = (select auth.uid()) and exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = conversation_presence.conversation_id
      and cp.user_id = (select auth.uid())
      and cp.left_at is null
  )
);
drop policy if exists "conversation_presence_self_update" on public.conversation_presence;
create policy "conversation_presence_self_update" on public.conversation_presence
for update to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));
drop policy if exists "conversation_presence_self_delete" on public.conversation_presence;
create policy "conversation_presence_self_delete" on public.conversation_presence
for delete to authenticated using (user_id = (select auth.uid()));
revoke all on public.conversation_presence from anon;
grant select, insert, update, delete on public.conversation_presence to authenticated;

create table if not exists public.message_receipts (
  message_id uuid not null references public.messages(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  device_id text not null,
  delivered_at timestamptz,
  read_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (message_id, user_id, device_id)
);

create index if not exists message_receipts_user_updated_idx on public.message_receipts(user_id, updated_at desc);
alter table public.message_receipts enable row level security;
drop policy if exists "message_receipts_participant_select" on public.message_receipts;
create policy "message_receipts_participant_select" on public.message_receipts
for select to authenticated
using (
  exists (
    select 1
    from public.messages m
    join public.conversation_participants cp on cp.conversation_id = m.conversation_id
    where m.id = message_receipts.message_id
      and cp.user_id = (select auth.uid())
      and cp.left_at is null
  )
);
drop policy if exists "message_receipts_self_insert" on public.message_receipts;
create policy "message_receipts_self_insert" on public.message_receipts
for insert to authenticated
with check (
  user_id = (select auth.uid()) and exists (
    select 1
    from public.messages m
    join public.conversation_participants cp on cp.conversation_id = m.conversation_id
    where m.id = message_receipts.message_id
      and cp.user_id = (select auth.uid())
      and cp.left_at is null
  )
);
drop policy if exists "message_receipts_self_update" on public.message_receipts;
create policy "message_receipts_self_update" on public.message_receipts
for update to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));
revoke all on public.message_receipts from anon;
grant select, insert, update on public.message_receipts to authenticated;

create table if not exists public.message_sync_cursors (
  user_id uuid not null references public.profiles(id) on delete cascade,
  device_id text not null,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  last_sequence_no bigint,
  last_message_id uuid references public.messages(id) on delete set null,
  last_synced_at timestamptz not null default now(),
  primary key (user_id, device_id, conversation_id)
);

alter table public.message_sync_cursors enable row level security;
drop policy if exists "message_sync_cursors_self" on public.message_sync_cursors;
create policy "message_sync_cursors_self" on public.message_sync_cursors
for all to authenticated
using (user_id = (select auth.uid()))
with check (
  user_id = (select auth.uid()) and exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = message_sync_cursors.conversation_id
      and cp.user_id = (select auth.uid())
      and cp.left_at is null
  )
);
revoke all on public.message_sync_cursors from anon;
grant select, insert, update, delete on public.message_sync_cursors to authenticated;

create table if not exists public.conversation_notification_preferences (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  messages_enabled boolean not null default true,
  calls_enabled boolean not null default true,
  mentions_only boolean not null default false,
  preview_mode text not null default 'inherit' check (preview_mode in ('inherit','full','sender_only','hidden')),
  sound_key text,
  ringtone_key text,
  vibration_enabled boolean not null default true,
  updated_at timestamptz not null default now(),
  primary key (conversation_id, user_id)
);

alter table public.conversation_notification_preferences enable row level security;
drop policy if exists "conversation_notification_preferences_self" on public.conversation_notification_preferences;
create policy "conversation_notification_preferences_self" on public.conversation_notification_preferences
for all to authenticated
using (user_id = (select auth.uid()))
with check (
  user_id = (select auth.uid()) and exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = conversation_notification_preferences.conversation_id
      and cp.user_id = (select auth.uid())
      and cp.left_at is null
  )
);
revoke all on public.conversation_notification_preferences from anon;
grant select, insert, update, delete on public.conversation_notification_preferences to authenticated;

create table if not exists public.message_attachments (
  id uuid primary key default gen_random_uuid(),
  message_id uuid not null references public.messages(id) on delete cascade,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  uploader_id uuid not null references public.profiles(id) on delete cascade,
  kind text not null check (kind in ('image','video','audio','voice','document','gif','sticker','contact','location','post','reel','profile','marketplace','gift')),
  storage_bucket text,
  storage_path text,
  display_name text,
  mime_type text,
  byte_size bigint not null default 0 check (byte_size >= 0 and byte_size <= 536870912),
  width integer check (width is null or width > 0),
  height integer check (height is null or height > 0),
  duration_ms bigint check (duration_ms is null or duration_ms >= 0),
  sha256 text,
  thumbnail_path text,
  status text not null default 'ready' check (status in ('uploading','processing','ready','failed','expired','deleted')),
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (message_id, id)
);

create index if not exists message_attachments_conversation_created_idx on public.message_attachments(conversation_id, created_at desc);
create index if not exists message_attachments_sha_idx on public.message_attachments(sha256) where sha256 is not null;
alter table public.message_attachments enable row level security;
drop policy if exists "message_attachments_member_select" on public.message_attachments;
create policy "message_attachments_member_select" on public.message_attachments
for select to authenticated
using (
  exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = message_attachments.conversation_id
      and cp.user_id = (select auth.uid())
      and cp.left_at is null
  )
);
drop policy if exists "message_attachments_sender_insert" on public.message_attachments;
create policy "message_attachments_sender_insert" on public.message_attachments
for insert to authenticated
with check (
  uploader_id = (select auth.uid())
  and exists (
    select 1 from public.messages m
    where m.id = message_attachments.message_id
      and m.conversation_id = message_attachments.conversation_id
      and m.sender_id = (select auth.uid())
  )
);
drop policy if exists "message_attachments_uploader_update" on public.message_attachments;
create policy "message_attachments_uploader_update" on public.message_attachments
for update to authenticated
using (uploader_id = (select auth.uid()))
with check (uploader_id = (select auth.uid()));
drop policy if exists "message_attachments_uploader_delete" on public.message_attachments;
create policy "message_attachments_uploader_delete" on public.message_attachments
for delete to authenticated using (uploader_id = (select auth.uid()));
revoke all on public.message_attachments from anon;
grant select, insert, update, delete on public.message_attachments to authenticated;

create table if not exists public.conversation_invites (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  created_by uuid not null references public.profiles(id) on delete cascade,
  token uuid not null default gen_random_uuid() unique,
  max_uses integer check (max_uses is null or max_uses > 0),
  uses integer not null default 0 check (uses >= 0),
  expires_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists conversation_invites_conversation_idx on public.conversation_invites(conversation_id, created_at desc);
alter table public.conversation_invites enable row level security;
drop policy if exists "conversation_invites_admin_select" on public.conversation_invites;
create policy "conversation_invites_admin_select" on public.conversation_invites
for select to authenticated
using (
  exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = conversation_invites.conversation_id
      and cp.user_id = (select auth.uid())
      and cp.role in ('owner','admin')
      and cp.left_at is null
  )
);
revoke all on public.conversation_invites from anon;
grant select on public.conversation_invites to authenticated;

create table if not exists public.conversation_join_requests (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending','approved','declined','cancelled')),
  requested_at timestamptz not null default now(),
  resolved_at timestamptz,
  resolved_by uuid references public.profiles(id) on delete set null,
  unique (conversation_id, user_id)
);

alter table public.conversation_join_requests enable row level security;
drop policy if exists "conversation_join_requests_visible" on public.conversation_join_requests;
create policy "conversation_join_requests_visible" on public.conversation_join_requests
for select to authenticated
using (
  user_id = (select auth.uid()) or exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = conversation_join_requests.conversation_id
      and cp.user_id = (select auth.uid())
      and cp.role in ('owner','admin')
      and cp.left_at is null
  )
);
drop policy if exists "conversation_join_requests_self_insert" on public.conversation_join_requests;
create policy "conversation_join_requests_self_insert" on public.conversation_join_requests
for insert to authenticated with check (user_id = (select auth.uid()));
drop policy if exists "conversation_join_requests_self_cancel" on public.conversation_join_requests;
create policy "conversation_join_requests_self_cancel" on public.conversation_join_requests
for update to authenticated
using (user_id = (select auth.uid()) and status = 'pending')
with check (user_id = (select auth.uid()) and status in ('pending','cancelled'));
revoke all on public.conversation_join_requests from anon;
grant select, insert, update on public.conversation_join_requests to authenticated;

create table if not exists public.conversation_events (
  id bigint generated by default as identity primary key,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  actor_id uuid references public.profiles(id) on delete set null,
  target_user_id uuid references public.profiles(id) on delete set null,
  event_type text not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists conversation_events_conversation_idx on public.conversation_events(conversation_id, id desc);
alter table public.conversation_events enable row level security;
drop policy if exists "conversation_events_member_select" on public.conversation_events;
create policy "conversation_events_member_select" on public.conversation_events
for select to authenticated
using (
  exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = conversation_events.conversation_id
      and cp.user_id = (select auth.uid())
      and cp.left_at is null
  )
);
revoke all on public.conversation_events from anon, authenticated;
grant select on public.conversation_events to authenticated;

create or replace function public.create_group_invite(
  p_conversation_id uuid,
  p_expires_at timestamptz default null,
  p_max_uses integer default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
  v_token uuid;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if not exists (
    select 1 from public.conversations c
    where c.id = p_conversation_id and c.is_group = true
  ) then raise exception 'GROUP_NOT_FOUND'; end if;
  if not exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = p_conversation_id
      and cp.user_id = v_me
      and cp.role in ('owner','admin')
      and cp.left_at is null
  ) then raise exception 'GROUP_ADMIN_REQUIRED'; end if;
  if p_max_uses is not null and p_max_uses < 1 then raise exception 'INVALID_MAX_USES'; end if;
  if p_expires_at is not null and p_expires_at <= now() then raise exception 'INVALID_EXPIRY'; end if;

  insert into public.conversation_invites(conversation_id, created_by, expires_at, max_uses)
  values (p_conversation_id, v_me, p_expires_at, p_max_uses)
  returning token into v_token;

  insert into public.conversation_events(conversation_id, actor_id, event_type, metadata)
  values (p_conversation_id, v_me, 'invite_link_created', jsonb_build_object('token', v_token));
  return v_token;
end;
$$;

create or replace function public.accept_group_invite(p_token uuid)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
  v_invite public.conversation_invites;
  v_requires_approval boolean;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select * into v_invite from public.conversation_invites where token = p_token for update;
  if not found then raise exception 'INVITE_NOT_FOUND'; end if;
  if v_invite.revoked_at is not null then raise exception 'INVITE_REVOKED'; end if;
  if v_invite.expires_at is not null and v_invite.expires_at <= now() then raise exception 'INVITE_EXPIRED'; end if;
  if v_invite.max_uses is not null and v_invite.uses >= v_invite.max_uses then raise exception 'INVITE_EXHAUSTED'; end if;
  if not private.chat_scope_allows(v_invite.created_by, v_me, 'group_invite') then
    raise exception 'GROUP_INVITE_NOT_ALLOWED';
  end if;

  select c.join_approval_required into v_requires_approval
  from public.conversations c where c.id = v_invite.conversation_id;

  if coalesce(v_requires_approval, false) then
    insert into public.conversation_join_requests(conversation_id, user_id, status, requested_at)
    values (v_invite.conversation_id, v_me, 'pending', now())
    on conflict (conversation_id, user_id)
    do update set status = 'pending', requested_at = now(), resolved_at = null, resolved_by = null;
  else
    insert into public.conversation_participants(conversation_id, user_id, last_read_at, is_admin, joined_at, role, left_at)
    values (v_invite.conversation_id, v_me, now(), false, now(), 'member', null)
    on conflict (conversation_id, user_id)
    do update set left_at = null, joined_at = now(), role = 'member', is_admin = false;

    update public.conversation_invites set uses = uses + 1 where id = v_invite.id;
    insert into public.conversation_events(conversation_id, actor_id, target_user_id, event_type)
    values (v_invite.conversation_id, v_me, v_me, 'member_joined');
  end if;
  return v_invite.conversation_id;
end;
$$;

create or replace function public.respond_group_join_request(p_request_id uuid, p_approve boolean)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
  v_request public.conversation_join_requests;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select * into v_request from public.conversation_join_requests where id = p_request_id for update;
  if not found or v_request.status <> 'pending' then return false; end if;
  if not exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = v_request.conversation_id
      and cp.user_id = v_me
      and cp.role in ('owner','admin')
      and cp.left_at is null
  ) then raise exception 'GROUP_ADMIN_REQUIRED'; end if;

  update public.conversation_join_requests
     set status = case when p_approve then 'approved' else 'declined' end,
         resolved_at = now(), resolved_by = v_me
   where id = p_request_id;

  if p_approve then
    insert into public.conversation_participants(conversation_id, user_id, last_read_at, is_admin, joined_at, role, left_at)
    values (v_request.conversation_id, v_request.user_id, now(), false, now(), 'member', null)
    on conflict (conversation_id, user_id)
    do update set left_at = null, joined_at = now(), role = 'member', is_admin = false;
  end if;

  insert into public.conversation_events(conversation_id, actor_id, target_user_id, event_type)
  values (
    v_request.conversation_id,
    v_me,
    v_request.user_id,
    case when p_approve then 'join_request_approved' else 'join_request_declined' end
  );
  return true;
end;
$$;

create or replace function public.set_group_member_role(
  p_conversation_id uuid,
  p_target_user_id uuid,
  p_role text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
  v_my_role text;
  v_target_role text;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_role not in ('admin','member') then raise exception 'INVALID_ROLE'; end if;

  select cp.role into v_my_role
  from public.conversation_participants cp
  where cp.conversation_id = p_conversation_id and cp.user_id = v_me and cp.left_at is null;
  select cp.role into v_target_role
  from public.conversation_participants cp
  where cp.conversation_id = p_conversation_id and cp.user_id = p_target_user_id and cp.left_at is null;

  if v_my_role not in ('owner','admin') then raise exception 'GROUP_ADMIN_REQUIRED'; end if;
  if v_target_role is null then raise exception 'MEMBER_NOT_FOUND'; end if;
  if v_target_role = 'owner' then raise exception 'OWNER_ROLE_CANNOT_BE_CHANGED'; end if;
  if v_my_role = 'admin' and v_target_role = 'admin' then raise exception 'OWNER_REQUIRED'; end if;

  update public.conversation_participants
     set role = p_role, is_admin = (p_role = 'admin')
   where conversation_id = p_conversation_id and user_id = p_target_user_id;

  insert into public.conversation_events(conversation_id, actor_id, target_user_id, event_type, metadata)
  values (p_conversation_id, v_me, p_target_user_id, 'member_role_changed', jsonb_build_object('role', p_role));
  return true;
end;
$$;

create or replace function public.transfer_group_ownership(
  p_conversation_id uuid,
  p_new_owner_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if not exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = p_conversation_id
      and cp.user_id = v_me
      and cp.role = 'owner'
      and cp.left_at is null
  ) then raise exception 'GROUP_OWNER_REQUIRED'; end if;
  if not exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = p_conversation_id
      and cp.user_id = p_new_owner_id
      and cp.left_at is null
  ) then raise exception 'NEW_OWNER_MUST_BE_MEMBER'; end if;

  update public.conversation_participants
     set role = 'admin', is_admin = true
   where conversation_id = p_conversation_id and user_id = v_me;
  update public.conversation_participants
     set role = 'owner', is_admin = true
   where conversation_id = p_conversation_id and user_id = p_new_owner_id;
  update public.conversations set created_by = p_new_owner_id where id = p_conversation_id;

  insert into public.conversation_events(conversation_id, actor_id, target_user_id, event_type)
  values (p_conversation_id, v_me, p_new_owner_id, 'ownership_transferred');
  return true;
end;
$$;

create or replace function public.leave_group_conversation(p_conversation_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
  v_role text;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select cp.role into v_role from public.conversation_participants cp
  join public.conversations c on c.id = cp.conversation_id and c.is_group = true
  where cp.conversation_id = p_conversation_id and cp.user_id = v_me and cp.left_at is null;
  if v_role is null then return false; end if;
  if v_role = 'owner' then raise exception 'TRANSFER_OWNERSHIP_BEFORE_LEAVING'; end if;

  update public.conversation_participants set left_at = now(), is_admin = false, role = 'member'
  where conversation_id = p_conversation_id and user_id = v_me;
  insert into public.conversation_events(conversation_id, actor_id, target_user_id, event_type)
  values (p_conversation_id, v_me, v_me, 'member_left');
  return true;
end;
$$;

create or replace function public.update_chat_privacy(
  p_who_can_message text,
  p_who_can_call text,
  p_who_can_group_invite text,
  p_show_online boolean,
  p_show_last_seen boolean,
  p_send_read_receipts boolean,
  p_show_typing boolean,
  p_show_recording boolean,
  p_show_profile_photo_in_chat boolean,
  p_allow_link_previews boolean,
  p_notification_preview text
)
returns boolean
language plpgsql
security invoker
set search_path = 'public', 'pg_temp'
as $$
declare
  v_me uuid := auth.uid();
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  insert into public.user_chat_privacy(
    user_id, who_can_message, who_can_call, who_can_group_invite,
    show_online, show_last_seen, send_read_receipts, show_typing, show_recording,
    show_profile_photo_in_chat, allow_link_previews, notification_preview, updated_at
  ) values (
    v_me, p_who_can_message, p_who_can_call, p_who_can_group_invite,
    p_show_online, p_show_last_seen, p_send_read_receipts, p_show_typing, p_show_recording,
    p_show_profile_photo_in_chat, p_allow_link_previews, p_notification_preview, now()
  )
  on conflict (user_id) do update set
    who_can_message = excluded.who_can_message,
    who_can_call = excluded.who_can_call,
    who_can_group_invite = excluded.who_can_group_invite,
    show_online = excluded.show_online,
    show_last_seen = excluded.show_last_seen,
    send_read_receipts = excluded.send_read_receipts,
    show_typing = excluded.show_typing,
    show_recording = excluded.show_recording,
    show_profile_photo_in_chat = excluded.show_profile_photo_in_chat,
    allow_link_previews = excluded.allow_link_previews,
    notification_preview = excluded.notification_preview,
    updated_at = now();
  return true;
end;
$$;

create or replace function public.upsert_conversation_presence(
  p_conversation_id uuid,
  p_device_id text,
  p_state text,
  p_ttl_seconds integer default 30
)
returns boolean
language plpgsql
security invoker
set search_path = 'public', 'pg_temp'
as $$
declare
  v_me uuid := auth.uid();
  v_ttl integer := greatest(5, least(coalesce(p_ttl_seconds, 30), 120));
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if length(btrim(coalesce(p_device_id,''))) < 1 then raise exception 'DEVICE_ID_REQUIRED'; end if;
  insert into public.conversation_presence(conversation_id,user_id,device_id,state,updated_at,expires_at)
  values (p_conversation_id,v_me,left(p_device_id,200),p_state,now(),now()+make_interval(secs=>v_ttl))
  on conflict (conversation_id,user_id,device_id) do update
  set state=excluded.state,updated_at=now(),expires_at=excluded.expires_at;
  return true;
end;
$$;

create or replace function public.ack_message_receipt(
  p_message_id uuid,
  p_device_id text,
  p_read boolean default false
)
returns boolean
language plpgsql
security invoker
set search_path = 'public', 'pg_temp'
as $$
declare
  v_me uuid := auth.uid();
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if length(btrim(coalesce(p_device_id,''))) < 1 then raise exception 'DEVICE_ID_REQUIRED'; end if;
  if not exists (
    select 1 from public.messages m
    join public.conversation_participants cp on cp.conversation_id=m.conversation_id
    where m.id=p_message_id and cp.user_id=v_me and cp.left_at is null
  ) then raise exception 'MESSAGE_NOT_ACCESSIBLE'; end if;

  insert into public.message_receipts(message_id,user_id,device_id,delivered_at,read_at,updated_at)
  values (p_message_id,v_me,left(p_device_id,200),now(),case when p_read then now() else null end,now())
  on conflict (message_id,user_id,device_id) do update
  set delivered_at=coalesce(message_receipts.delivered_at,excluded.delivered_at),
      read_at=case when p_read then coalesce(message_receipts.read_at,excluded.read_at) else message_receipts.read_at end,
      updated_at=now();

  update public.messages m
     set delivered_at = coalesce(m.delivered_at, now()),
         read_at = case when p_read then coalesce(m.read_at, now()) else m.read_at end,
         is_read = case when p_read then true else m.is_read end
   where m.id = p_message_id and m.sender_id <> v_me;
  return true;
end;
$$;

-- Recreate v3 with privacy enforcement after the privacy table/helper exists.
create or replace function public.send_message_v3(
  p_receiver_username text,
  p_content text,
  p_client_message_id uuid,
  p_reply_to_message_id uuid default null
)
returns table(message_id uuid, conversation_id uuid, created_at timestamptz, sequence_no bigint)
language plpgsql
security invoker
set search_path = 'public', 'pg_temp'
as $$
declare
  v_me uuid := auth.uid();
  v_receiver uuid;
  v_message_id uuid;
  v_conversation_id uuid;
  v_created_at timestamptz;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_client_message_id is null then raise exception 'CLIENT_MESSAGE_ID_REQUIRED'; end if;
  select p.id into v_receiver from public.profiles p
  where lower(p.username)=lower(btrim(p_receiver_username)) limit 1;
  if v_receiver is null then raise exception 'RECIPIENT_NOT_FOUND'; end if;
  if not private.chat_scope_allows(v_me, v_receiver, 'message') then raise exception 'MESSAGE_PRIVACY_BLOCKED'; end if;

  perform pg_advisory_xact_lock(hashtextextended(v_me::text || ':' || p_client_message_id::text,0));
  select m.id,m.conversation_id,m.created_at into v_message_id,v_conversation_id,v_created_at
  from public.messages m where m.sender_id=v_me and m.client_message_id=p_client_message_id limit 1;
  if v_message_id is not null then
    return query select m.id,m.conversation_id,m.created_at,m.sequence_no from public.messages m where m.id=v_message_id;
    return;
  end if;

  select r.message_id,r.conversation_id,r.created_at into v_message_id,v_conversation_id,v_created_at
  from public.send_message_v2(p_receiver_username,p_content) r;
  if v_message_id is null or v_conversation_id is null then raise exception 'MESSAGE_SEND_DID_NOT_RETURN_IDENTITY'; end if;
  if p_reply_to_message_id is not null and not exists (
    select 1 from public.messages parent
    where parent.id=p_reply_to_message_id and parent.conversation_id=v_conversation_id
      and coalesce(parent.deleted_for_everyone,false)=false
  ) then raise exception 'INVALID_REPLY_TARGET'; end if;

  update public.messages m
     set client_message_id=p_client_message_id,reply_to_message_id=p_reply_to_message_id
   where m.id=v_message_id and m.sender_id=v_me;
  return query select m.id,m.conversation_id,m.created_at,m.sequence_no from public.messages m where m.id=v_message_id;
end;
$$;

revoke all on function public.create_group_invite(uuid,timestamptz,integer) from public, anon;
revoke all on function public.accept_group_invite(uuid) from public, anon;
revoke all on function public.respond_group_join_request(uuid,boolean) from public, anon;
revoke all on function public.set_group_member_role(uuid,uuid,text) from public, anon;
revoke all on function public.transfer_group_ownership(uuid,uuid) from public, anon;
revoke all on function public.leave_group_conversation(uuid) from public, anon;
revoke all on function public.update_chat_privacy(text,text,text,boolean,boolean,boolean,boolean,boolean,boolean,boolean,text) from public, anon;
revoke all on function public.upsert_conversation_presence(uuid,text,text,integer) from public, anon;
revoke all on function public.ack_message_receipt(uuid,text,boolean) from public, anon;
revoke all on function public.send_message_v3(text,text,uuid,uuid) from public, anon;

grant execute on function public.create_group_invite(uuid,timestamptz,integer) to authenticated;
grant execute on function public.accept_group_invite(uuid) to authenticated;
grant execute on function public.respond_group_join_request(uuid,boolean) to authenticated;
grant execute on function public.set_group_member_role(uuid,uuid,text) to authenticated;
grant execute on function public.transfer_group_ownership(uuid,uuid) to authenticated;
grant execute on function public.leave_group_conversation(uuid) to authenticated;
grant execute on function public.update_chat_privacy(text,text,text,boolean,boolean,boolean,boolean,boolean,boolean,boolean,text) to authenticated;
grant execute on function public.upsert_conversation_presence(uuid,text,text,integer) to authenticated;
grant execute on function public.ack_message_receipt(uuid,text,boolean) to authenticated;
grant execute on function public.send_message_v3(text,text,uuid,uuid) to authenticated;

alter table public.conversation_presence replica identity full;
alter table public.message_receipts replica identity full;
alter table public.conversation_events replica identity full;
alter table public.message_attachments replica identity full;

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname='supabase_realtime' and schemaname='public' and tablename='conversation_presence'
  ) then alter publication supabase_realtime add table public.conversation_presence; end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname='supabase_realtime' and schemaname='public' and tablename='message_receipts'
  ) then alter publication supabase_realtime add table public.message_receipts; end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname='supabase_realtime' and schemaname='public' and tablename='conversation_events'
  ) then alter publication supabase_realtime add table public.conversation_events; end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname='supabase_realtime' and schemaname='public' and tablename='message_attachments'
  ) then alter publication supabase_realtime add table public.message_attachments; end if;
end $$;
