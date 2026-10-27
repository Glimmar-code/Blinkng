-- Add a non-student onboarding path; no existing profile is reassigned.
begin;
alter table public.profiles drop constraint if exists profiles_account_type_allowed;
alter table public.profiles add constraint profiles_account_type_allowed check (account_type is null or account_type in ('STUDENT','ASPIRANT','GRADUATE','STAFF','NON_STUDENT'));
comment on column public.profiles.account_type is 'Self-declared account type, not verified school affiliation.';
commit;
