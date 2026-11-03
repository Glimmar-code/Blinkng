-- Feature 5 / Testlab candidate: self-declared campus account type.
-- This column does not confer official university/student verification.
-- Existing accounts remain unclassified until they explicitly complete setup.
begin;

alter table public.profiles
  add column if not exists account_type text;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'profiles_account_type_allowed'
      and conrelid = 'public.profiles'::regclass
  ) then
    alter table public.profiles
      add constraint profiles_account_type_allowed
      check (account_type is null
        or account_type in ('STUDENT','ASPIRANT','GRADUATE','STAFF'));
  end if;
end; $$;

comment on column public.profiles.account_type is
  'User-selected campus role; NOT independent proof of student or staff status.';

commit;
