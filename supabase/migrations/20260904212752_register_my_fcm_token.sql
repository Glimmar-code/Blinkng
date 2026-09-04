create or replace function public.register_my_fcm_token(p_token text)
returns boolean
language plpgsql
security invoker
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  update public.profiles
  set fcm_token = nullif(btrim(p_token), '')
  where id = auth.uid();

  return found;
end;
$$;

revoke all on function public.register_my_fcm_token(text) from public, anon;
grant execute on function public.register_my_fcm_token(text) to authenticated;
