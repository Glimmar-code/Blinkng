-- BLINK account onboarding v1.
-- destructive-change-reviewed: additive profile fields + owner-only private birthday storage.
-- rollback-plan: drop profile_private_details and the new interests/onboarding_step columns; restore onboarding_completed default false. Existing profile content remains intact.

begin;

alter table public.profiles
    add column if not exists interests text[] not null default '{}'::text[];

alter table public.profiles
    add column if not exists onboarding_step smallint not null default 0;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'profiles_onboarding_step_range'
          and conrelid = 'public.profiles'::regclass
    ) then
        alter table public.profiles
            add constraint profiles_onboarding_step_range
            check (onboarding_step between 0 and 4);
    end if;
end
$$;

-- The legacy column exists but was never used as a completed-onboarding signal:
-- every production profile was false before this migration. Preserve all accounts
-- already present at migration time as completed, then let future accounts default
-- to step 0 / incomplete.
update public.profiles
set onboarding_completed = true,
    onboarding_step = 4
where onboarding_completed is distinct from true
   or onboarding_step <> 4;

alter table public.profiles
    alter column onboarding_completed set default false;

create table if not exists public.profile_private_details (
    user_id uuid primary key references public.profiles(id) on delete cascade,
    birth_date date,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.profile_private_details enable row level security;

drop policy if exists profile_private_details_select_own
    on public.profile_private_details;
create policy profile_private_details_select_own
on public.profile_private_details
for select
to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists profile_private_details_insert_own
    on public.profile_private_details;
create policy profile_private_details_insert_own
on public.profile_private_details
for insert
to authenticated
with check ((select auth.uid()) = user_id);

drop policy if exists profile_private_details_update_own
    on public.profile_private_details;
create policy profile_private_details_update_own
on public.profile_private_details
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists profile_private_details_delete_own
    on public.profile_private_details;
create policy profile_private_details_delete_own
on public.profile_private_details
for delete
to authenticated
using ((select auth.uid()) = user_id);

revoke all on public.profile_private_details from public, anon, authenticated;
grant select, insert, update, delete on public.profile_private_details to authenticated;

comment on table public.profile_private_details is
    'Owner-only profile fields that must not be exposed through the broadly readable profiles table.';
comment on column public.profile_private_details.birth_date is
    'Optional exact birthday supplied during onboarding; readable and writable only by the profile owner.';

commit;
