-- Blink AI private history + retrieval learning foundation.
-- Testlab first: do not apply to production until staging/runtime checks pass.

create table if not exists public.blink_ai_conversations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null default 'New conversation',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint blink_ai_conversations_title_length
    check (char_length(title) between 1 and 160)
);

create table if not exists public.blink_ai_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.blink_ai_conversations(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null,
  content text not null,
  provider_interaction_id text,
  web_sources jsonb not null default '[]'::jsonb,
  has_image boolean not null default false,
  has_audio boolean not null default false,
  created_at timestamptz not null default now(),
  constraint blink_ai_messages_role_check check (role in ('user', 'assistant')),
  constraint blink_ai_messages_content_length check (char_length(content) between 1 and 20000),
  constraint blink_ai_messages_web_sources_array check (jsonb_typeof(web_sources) = 'array')
);

create index if not exists blink_ai_conversations_user_recent_idx
  on public.blink_ai_conversations (user_id, updated_at desc);

create index if not exists blink_ai_messages_user_recent_idx
  on public.blink_ai_messages (user_id, created_at desc);

create index if not exists blink_ai_messages_conversation_recent_idx
  on public.blink_ai_messages (conversation_id, created_at desc);

create index if not exists blink_ai_messages_provider_interaction_idx
  on public.blink_ai_messages (user_id, provider_interaction_id)
  where provider_interaction_id is not null;

alter table public.blink_ai_conversations enable row level security;
alter table public.blink_ai_messages enable row level security;

revoke all on table public.blink_ai_conversations from anon;
revoke all on table public.blink_ai_messages from anon;

grant select, insert, update, delete on table public.blink_ai_conversations to authenticated;
grant select, insert, update, delete on table public.blink_ai_messages to authenticated;

drop policy if exists blink_ai_conversations_select_own on public.blink_ai_conversations;
create policy blink_ai_conversations_select_own
  on public.blink_ai_conversations
  for select
  to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists blink_ai_conversations_insert_own on public.blink_ai_conversations;
create policy blink_ai_conversations_insert_own
  on public.blink_ai_conversations
  for insert
  to authenticated
  with check ((select auth.uid()) = user_id);

drop policy if exists blink_ai_conversations_update_own on public.blink_ai_conversations;
create policy blink_ai_conversations_update_own
  on public.blink_ai_conversations
  for update
  to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

drop policy if exists blink_ai_conversations_delete_own on public.blink_ai_conversations;
create policy blink_ai_conversations_delete_own
  on public.blink_ai_conversations
  for delete
  to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists blink_ai_messages_select_own on public.blink_ai_messages;
create policy blink_ai_messages_select_own
  on public.blink_ai_messages
  for select
  to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists blink_ai_messages_insert_own on public.blink_ai_messages;
create policy blink_ai_messages_insert_own
  on public.blink_ai_messages
  for insert
  to authenticated
  with check (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.blink_ai_conversations c
      where c.id = conversation_id
        and c.user_id = (select auth.uid())
    )
  );

drop policy if exists blink_ai_messages_update_own on public.blink_ai_messages;
create policy blink_ai_messages_update_own
  on public.blink_ai_messages
  for update
  to authenticated
  using ((select auth.uid()) = user_id)
  with check (
    (select auth.uid()) = user_id
    and exists (
      select 1
      from public.blink_ai_conversations c
      where c.id = conversation_id
        and c.user_id = (select auth.uid())
    )
  );

drop policy if exists blink_ai_messages_delete_own on public.blink_ai_messages;
create policy blink_ai_messages_delete_own
  on public.blink_ai_messages
  for delete
  to authenticated
  using ((select auth.uid()) = user_id);
