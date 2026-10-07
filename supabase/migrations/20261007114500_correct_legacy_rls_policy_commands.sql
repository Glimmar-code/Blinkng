-- Correct legacy policies that were accidentally created as FOR ALL.
-- This preserves the intended read/write behavior while removing cross-command overlap
-- and uses init-plan-safe auth.uid() evaluation.

-- FCM tokens: owners can CRUD only their own rows.
drop policy if exists fcm_tokens_delete_own on public.fcm_tokens;
drop policy if exists fcm_tokens_insert_own on public.fcm_tokens;
drop policy if exists fcm_tokens_select_own on public.fcm_tokens;
drop policy if exists fcm_tokens_update_own on public.fcm_tokens;

create policy fcm_tokens_select_own
on public.fcm_tokens
for select
to authenticated
using ((select auth.uid()) = user_id);

create policy fcm_tokens_insert_own
on public.fcm_tokens
for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy fcm_tokens_update_own
on public.fcm_tokens
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

create policy fcm_tokens_delete_own
on public.fcm_tokens
for delete
to authenticated
using ((select auth.uid()) = user_id);

-- Interaction rows are readable to signed-in users, but only the current user can insert.
-- Mutation/removal continues through the dedicated server-authoritative interaction RPCs.
drop policy if exists interactions_insert_own on public.interactions;
drop policy if exists interactions_select_all on public.interactions;

create policy interactions_select_all
on public.interactions
for select
to authenticated
using (true);

create policy interactions_insert_own
on public.interactions
for insert
to authenticated
with check ((select auth.uid()) = user_id);

-- Compatibility messages: only conversation participants can read rows.
-- Only the sender can insert or delete their own compatibility row.
drop policy if exists messages_compat_delete_own on public.messages_compat;
drop policy if exists messages_compat_insert_own on public.messages_compat;
drop policy if exists messages_compat_select_own on public.messages_compat;

create policy messages_compat_select_own
on public.messages_compat
for select
to authenticated
using (
  exists (
    select 1
    from public.profiles p
    where p.id = (select auth.uid())
      and (
        p.username = messages_compat.sender_username
        or p.username = messages_compat.receiver_username
      )
  )
);

create policy messages_compat_insert_own
on public.messages_compat
for insert
to authenticated
with check (
  exists (
    select 1
    from public.profiles p
    where p.id = (select auth.uid())
      and p.username = messages_compat.sender_username
  )
);

create policy messages_compat_delete_own
on public.messages_compat
for delete
to authenticated
using (
  exists (
    select 1
    from public.profiles p
    where p.id = (select auth.uid())
      and p.username = messages_compat.sender_username
  )
);

-- Statuses: public-to-signed-in active stories, owners retain full lifecycle control.
drop policy if exists statuses_delete_own on public.statuses;
drop policy if exists statuses_insert_own on public.statuses;
drop policy if exists statuses_select_all on public.statuses;
drop policy if exists statuses_update_own on public.statuses;

create policy statuses_select_all
on public.statuses
for select
to authenticated
using (is_active = true or (select auth.uid()) = user_id);

create policy statuses_insert_own
on public.statuses
for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy statuses_update_own
on public.statuses
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

create policy statuses_delete_own
on public.statuses
for delete
to authenticated
using ((select auth.uid()) = user_id);

-- Story interactions: users manage only their own reactions/replies.
drop policy if exists story_interactions_delete_own on public.story_interactions;
drop policy if exists story_interactions_insert_own on public.story_interactions;
drop policy if exists story_interactions_select_own on public.story_interactions;

create policy story_interactions_select_own
on public.story_interactions
for select
to authenticated
using ((select auth.uid()) = user_id);

create policy story_interactions_insert_own
on public.story_interactions
for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy story_interactions_delete_own
on public.story_interactions
for delete
to authenticated
using ((select auth.uid()) = user_id);

-- User devices: remove the duplicate PUBLIC catch-all and retain explicit owner policies.
drop policy if exists "devices: owner manage" on public.user_devices;
drop policy if exists user_devices_delete_own on public.user_devices;
drop policy if exists user_devices_insert_own on public.user_devices;
drop policy if exists user_devices_select_own on public.user_devices;
drop policy if exists user_devices_update_own on public.user_devices;

create policy user_devices_select_own
on public.user_devices
for select
to authenticated
using ((select auth.uid()) = user_id);

create policy user_devices_insert_own
on public.user_devices
for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy user_devices_update_own
on public.user_devices
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

create policy user_devices_delete_own
on public.user_devices
for delete
to authenticated
using ((select auth.uid()) = user_id);

-- These capabilities are not required by BLINK clients and bypass ordinary row CRUD semantics.
revoke truncate, references, trigger on table public.fcm_tokens from authenticated;
revoke truncate, references, trigger on table public.interactions from authenticated;
revoke truncate, references, trigger on table public.messages_compat from authenticated;
revoke truncate, references, trigger on table public.statuses from authenticated;
revoke truncate, references, trigger on table public.story_interactions from authenticated;
revoke truncate, references, trigger on table public.user_devices from authenticated;
