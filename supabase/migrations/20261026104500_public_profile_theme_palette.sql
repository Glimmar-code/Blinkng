-- Staged only: DO NOT apply to production before staging / integration verification.
-- Owner-selected, public appearance only. No private settings or secrets are exposed.
alter table public.profiles
  add column if not exists profile_theme text not null default 'black';

do $$
begin
  if not exists (
    select 1
      from pg_constraint
     where conrelid = 'public.profiles'::regclass
       and conname = 'profiles_profile_theme_check'
  ) then
    alter table public.profiles
      add constraint profiles_profile_theme_check
      check (profile_theme in ('black', 'royal_purple', 'sapphire', 'emerald', 'rose', 'amber'));
  end if;
end
$$;

comment on column public.profiles.profile_theme is
  'Publicly viewable, owner-selected profile presentation, independent of viewer app theme.';
-- Existing profile owner UPDATE policies and get_profile_detail() privacy filtering remain in force.
