-- TESTLAB DESIGN ONLY. Do not execute directly in production.
-- Convert to a generated Supabase migration after validation on a Supabase development branch.

create table if not exists public.call_preferences (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  audio_policy text not null default 'mutuals'
    check (audio_policy in ('everyone','followers','following','mutuals','nobody')),
  video_policy text not null default 'mutuals'
    check (video_policy in ('everyone','followers','following','mutuals','nobody')),
  silence_unknown_callers boolean not null default false,
  updated_at timestamptz not null default now()
);

alter table public.call_preferences enable row level security;

drop policy if exists "users read own call preferences" on public.call_preferences;
create policy "users read own call preferences"
on public.call_preferences for select
to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists "users insert own call preferences" on public.call_preferences;
create policy "users insert own call preferences"
on public.call_preferences for insert
to authenticated
with check ((select auth.uid()) = user_id);

drop policy if exists "users update own call preferences" on public.call_preferences;
create policy "users update own call preferences"
on public.call_preferences for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

grant select, insert, update on public.call_preferences to authenticated;
