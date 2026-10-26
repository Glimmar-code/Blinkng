-- A deliberately narrow public color-only API: private accounts are never revealed.
-- No IDs, contact data, membership or wallet fields can leave this function.
create or replace function public.get_public_web_profile_theme(p_username text)
returns text
language sql stable security definer
set search_path = ''
as $$
  select p.profile_theme
    from public.profiles p
   where lower(p.username) = lower(ltrim(btrim(coalesce(p_username, '')), '@'))
     and char_length(btrim(coalesce(p_username, ''))) between 2 and 60
     and not exists (
       select 1 from public.user_settings s
        where s.user_id = p.id and coalesce(s.private_account, false)
     )
   limit 1;
$$;
revoke all on function public.get_public_web_profile_theme(text) from public;
grant execute on function public.get_public_web_profile_theme(text) to anon, authenticated;
comment on function public.get_public_web_profile_theme(text) is
  'Return only the public six-color owner profile theme if profile is publicly visible.';