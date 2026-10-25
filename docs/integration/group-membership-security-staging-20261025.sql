-- BLINK GROUP SECURITY HARDENING — STAGED SQL CANDIDATE ONLY.
-- Do NOT apply directly to hosted Supabase. Run isolated full PostgreSQL/RLS
-- tests and inspect current policies and consumers before versioned migration.
--
-- 2026-10-25 audit: existing authenticated cp_insert_own only checks user_id,
-- cp_update_own allows edits to is_admin, and conversations/messages membership
-- policies omit left_at. That can compromise group isolation after departures.
-- This candidate fixes the boundaries while preserving legacy 1-to-1 reads.
begin;

-- A tightly scoped predicate is needed for the membership INSERT policy:
-- the joining user does not yet have RLS visibility into the private group
-- row, so an ordinary invoker subquery can return a misleading NOT EXISTS.
create or replace function public.blink_conversation_is_group(p_conversation_id uuid)
returns boolean
language sql stable security definer set search_path = ''
as $$
  select coalesce(
    (select c.is_group from public.conversations c
     where c.id = p_conversation_id), false
  );
$$;
revoke all on function public.blink_conversation_is_group(uuid) from public, anon;
grant execute on function public.blink_conversation_is_group(uuid) to authenticated;

-- Normal clients may join their own existing 1:1 conversation under the
-- legacy contract, but MUST NOT self-invite to a private group.
-- Group creation and legitimate moderator invites must use vetted server
-- RPCs, not raw client inserts. Their privileged calls bypass RLS safely.
drop policy if exists cp_insert_own on public.conversation_participants;
create policy cp_insert_own on public.conversation_participants
for insert to authenticated
with check (
  (select auth.uid()) = user_id
  and not public.blink_conversation_is_group(conversation_id)
);

create or replace function public.blink_protect_conversation_membership()
returns trigger language plpgsql security invoker
set search_path = public, pg_temp
as $$
begin
  if current_user = 'authenticated' and (
    old.user_id is distinct from new.user_id or
    old.conversation_id is distinct from new.conversation_id or
    old.is_admin is distinct from new.is_admin or
    old.joined_at is distinct from new.joined_at or
    (old.left_at is not null and new.left_at is null)
  ) then
    raise exception 'BLINK_MEMBERSHIP_CHANGE_REQUIRES_SERVER'
      using errcode='42501';
  end if;
  return new;
end;
$$;
drop trigger if exists blink_protect_conversation_membership_guard
  on public.conversation_participants;
create trigger blink_protect_conversation_membership_guard
before update on public.conversation_participants
for each row execute function public.blink_protect_conversation_membership();

-- A departed participant must not read the conversation or old/new messages
-- simply because the relationship row remains for audit/history.
drop policy if exists conv_select_member on public.conversations;
create policy conv_select_member on public.conversations
for select to authenticated
using (exists (
  select 1 from public.conversation_participants cp
  where cp.conversation_id = conversations.id
    and cp.user_id = (select auth.uid()) and cp.left_at is null
));

drop policy if exists conv_update_member on public.conversations;
create policy conv_update_member on public.conversations
for update to authenticated
using (exists (
  select 1 from public.conversation_participants cp
  where cp.conversation_id = conversations.id
    and cp.user_id = (select auth.uid()) and cp.left_at is null
))
with check (exists (
  select 1 from public.conversation_participants cp
  where cp.conversation_id = conversations.id
    and cp.user_id = (select auth.uid()) and cp.left_at is null
));

drop policy if exists messages_select_member on public.messages;
create policy messages_select_member on public.messages
for select to authenticated
using (exists (
  select 1 from public.conversation_participants cp
  where cp.conversation_id = messages.conversation_id
    and cp.user_id = (select auth.uid()) and cp.left_at is null
));

drop policy if exists messages_insert_member on public.messages;
create policy messages_insert_member on public.messages
for insert to authenticated
with check (
  sender_id = (select auth.uid()) and exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id = messages.conversation_id
      and cp.user_id = (select auth.uid()) and cp.left_at is null
  )
);

commit;

-- Required local tests:
-- 1. Self-join private group denied by cp_insert_own even when group is
--    hidden from the caller by conversations RLS.
-- 2. Self-promote cp.is_admin, account reassignment, forged joined_at and
--    departed-member rejoin denied.
-- 3. Owner/server-authorized group creation still produces 3–50 member rows.
-- 4. Departed account cannot SELECT group/messages or send_group_message.
-- 5. DM send/reconnect and legacy receipts still work with active members.
-- 6. No anonymous exposure and repeated migrations are idempotent.
-- 7. Inspect all other permissive RLS policies before promotion: PostgreSQL
--    combines permissive policies with OR, which can defeat these changes.
-- 8. Version with Supabase CLI and test against full preview schema. Production
--    deployment requires a separately authorized, verified release.
