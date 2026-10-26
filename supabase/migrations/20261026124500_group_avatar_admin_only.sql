-- Set group chat avatars only with the group's owner/admin authorization.
-- Uploaded images must already belong to the current account's profile-media
-- object prefix; this never grants storage access or trusts arbitrary URLs.
create or replace function public.blink_set_group_avatar(
  p_conversation_id uuid, p_avatar_url text
)
returns boolean
language plpgsql
volatile security definer
set search_path = ''
as $$
declare
  v_actor uuid := auth.uid();
  v_prefix text;
  v_object_path text;
begin
  if v_actor is null then
    raise exception 'AUTH_REQUIRED' using errcode='42501';
  end if;
  if p_conversation_id is null or p_avatar_url is null or length(p_avatar_url)>1500 then
    return false;
  end if;
  if not exists (
    select 1 from public.conversations c
    where c.id=p_conversation_id and c.is_group = true
      and (
        c.created_by=v_actor or exists (
          select 1 from public.conversation_participants cp
          where cp.conversation_id=c.id and cp.user_id=v_actor
            and cp.is_admin=true
        )
      )
      and exists (
        select 1 from public.conversation_participants member
        where member.conversation_id=c.id and member.user_id=v_actor
      )
  ) then
    raise exception 'GROUP_ADMIN_REQUIRED' using errcode='42501';
  end if;
  v_prefix := 'users/'||v_actor::text||'/avatar/';
  -- Match only HTTPS objects under the authenticated account's existing
  -- profile-media upload path. URL prefix and storage object are both checked.
  if p_avatar_url !~ '^https://jhwgifrlxwspoedxjaly[.]supabase[.]co/storage/v1/object/public/profile-media/' then
    return false;
  end if;
  v_object_path := split_part(p_avatar_url,'/storage/v1/object/public/profile-media/',2);
  if v_object_path not like (v_prefix||'%') or
     v_object_path !~ '^[a-zA-Z0-9/_-]+\.(jpg|jpeg|png|webp)$' then
    return false;
  end if;
  if not exists (
    select 1 from storage.objects o
    where o.bucket_id='profile-media' and o.name=v_object_path
      and (o.owner_id=v_actor::text or o.owner=v_actor)
  ) then return false; end if;
  update public.conversations c
    set avatar_url=p_avatar_url,updated_at=now()
    where c.id=p_conversation_id and c.is_group=true;
  return found;
end;
$$;
revoke all on function public.blink_set_group_avatar(uuid,text)
  from public,anon,authenticated;
grant execute on function public.blink_set_group_avatar(uuid,text) to authenticated;
comment on function public.blink_set_group_avatar(uuid,text) is
 'Only accepted group owner/admin members may set a group icon to an existing image under their own profile-media avatar object prefix.';
