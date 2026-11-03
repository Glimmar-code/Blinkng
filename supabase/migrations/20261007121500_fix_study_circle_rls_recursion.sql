begin;

-- Fix a real production RLS recursion between study_circles and
-- study_circle_members. The previous member SELECT policy queried the same
-- table again and also used the tautology m2.circle_id = m2.circle_id.
-- These helpers expose only the current authenticated user's own membership/
-- visibility decision, while SECURITY DEFINER lets the helper perform the
-- internal lookup without re-entering table RLS.
--
-- Rollback-plan: restore the previous circle_select/circle_members_select
-- policies and drop these two helper functions only as a coordinated rollback.

create or replace function public.study_circle_is_current_user_member(p_circle_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    auth.uid() is not null
    and exists (
      select 1
      from public.study_circle_members m
      where m.circle_id = p_circle_id
        and m.user_id = auth.uid()
    );
$$;

revoke all on function public.study_circle_is_current_user_member(uuid)
  from public, anon;
grant execute on function public.study_circle_is_current_user_member(uuid)
  to authenticated;

create or replace function public.study_circle_can_current_user_view(p_circle_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    auth.uid() is not null
    and exists (
      select 1
      from public.study_circles c
      where c.id = p_circle_id
        and (
          not c.is_private
          or c.owner_id = auth.uid()
          or exists (
            select 1
            from public.study_circle_members m
            where m.circle_id = c.id
              and m.user_id = auth.uid()
          )
        )
    );
$$;

revoke all on function public.study_circle_can_current_user_view(uuid)
  from public, anon;
grant execute on function public.study_circle_can_current_user_view(uuid)
  to authenticated;

drop policy if exists circle_members_select on public.study_circle_members;
create policy circle_members_select
on public.study_circle_members
for select
to authenticated
using (
  public.study_circle_can_current_user_view(circle_id)
);

drop policy if exists circle_select on public.study_circles;
create policy circle_select
on public.study_circles
for select
to authenticated
using (
  not is_private
  or owner_id = (select auth.uid())
  or public.study_circle_is_current_user_member(id)
);

notify pgrst, 'reload schema';
commit;
