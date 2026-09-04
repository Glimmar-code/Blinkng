grant usage on schema private to authenticated;
grant select, insert, update on private.user_streak_state to authenticated;

create policy user_streak_select_own
on private.user_streak_state
for select
to authenticated
using ((select auth.uid()) = user_id);

create policy user_streak_insert_own
on private.user_streak_state
for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy user_streak_update_own
on private.user_streak_state
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

alter function public.touch_daily_streak() security invoker;
