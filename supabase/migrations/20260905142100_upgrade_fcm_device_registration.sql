begin;

-- An FCM registration token belongs to one current authenticated account.
-- Keeping token globally unique prevents a shared/reused installation token from
-- remaining attached to an account that is no longer signed in on that device.
create unique index if not exists fcm_tokens_token_uidx
  on public.fcm_tokens(token);

create index if not exists fcm_tokens_active_user_updated_idx
  on public.fcm_tokens(user_id, updated_at desc)
  where is_active = true;

create or replace function public.register_my_fcm_token(p_token text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_token text := nullif(btrim(p_token), '');
begin
  if v_user_id is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  if v_token is null or length(v_token) < 20 or length(v_token) > 4096 then
    raise exception 'INVALID_FCM_TOKEN';
  end if;

  insert into public.fcm_tokens (
    user_id,
    token,
    platform,
    is_active,
    updated_at
  )
  values (
    v_user_id,
    v_token,
    'android',
    true,
    now()
  )
  on conflict (token) do update
  set user_id = excluded.user_id,
      platform = excluded.platform,
      is_active = true,
      updated_at = now();

  -- Backward compatibility for the existing push Edge Function.
  update public.profiles
  set fcm_token = v_token
  where id = v_user_id;

  return true;
end;
$$;

revoke all on function public.register_my_fcm_token(text) from public, anon;
grant execute on function public.register_my_fcm_token(text) to authenticated;

create or replace function public.unregister_my_fcm_token(p_token text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_token text := nullif(btrim(p_token), '');
begin
  if v_user_id is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  if v_token is null then
    return false;
  end if;

  update public.fcm_tokens
  set is_active = false,
      updated_at = now()
  where user_id = v_user_id
    and token = v_token;

  update public.profiles
  set fcm_token = null
  where id = v_user_id
    and fcm_token = v_token;

  return true;
end;
$$;

revoke all on function public.unregister_my_fcm_token(text) from public, anon;
grant execute on function public.unregister_my_fcm_token(text) to authenticated;

grant select, insert, update, delete on table public.fcm_tokens to service_role;

commit;
