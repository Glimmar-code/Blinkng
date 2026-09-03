begin;

create or replace function public.get_my_conversation_partners()
returns table(conversation_id uuid, partner_id uuid)
language sql
security definer
set search_path = public, pg_temp
stable
as $$
  select mine.conversation_id, other.user_id
  from public.conversation_participants mine
  join public.conversation_participants other
    on other.conversation_id = mine.conversation_id
   and other.user_id <> mine.user_id
  join public.conversations c
    on c.id = mine.conversation_id
  where mine.user_id = auth.uid()
    and coalesce(c.is_group, false) = false
$$;

revoke all on function public.get_my_conversation_partners() from public, anon;
grant execute on function public.get_my_conversation_partners() to authenticated;

create or replace function public.mark_conversation_read(p_partner_username text)
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_me uuid := auth.uid();
  v_partner uuid;
  v_changed integer := 0;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;

  select p.id into v_partner
  from public.profiles p
  where lower(p.username) = lower(trim(p_partner_username))
  limit 1;

  if v_partner is null then raise exception 'RECIPIENT_NOT_FOUND'; end if;

  update public.messages m
     set is_read = true
   where m.sender_id = v_partner
     and coalesce(m.is_read, false) = false
     and exists (
       select 1
       from public.conversation_participants mine
       join public.conversation_participants theirs
         on theirs.conversation_id = mine.conversation_id
        and theirs.user_id = v_partner
       where mine.user_id = v_me
         and mine.conversation_id = m.conversation_id
     );

  get diagnostics v_changed = row_count;
  return v_changed;
end;
$$;

revoke all on function public.mark_conversation_read(text) from public, anon;
grant execute on function public.mark_conversation_read(text) to authenticated;

create or replace function public.get_or_create_direct_conversation(p_receiver_username text)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_me uuid := auth.uid();
  v_receiver uuid;
  v_conversation uuid;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if nullif(trim(p_receiver_username), '') is null then raise exception 'RECIPIENT_REQUIRED'; end if;

  select p.id into v_receiver
  from public.profiles p
  where lower(p.username) = lower(trim(p_receiver_username))
  limit 1;

  if v_receiver is null then raise exception 'RECIPIENT_NOT_FOUND'; end if;
  if v_receiver = v_me then raise exception 'CANNOT_MESSAGE_SELF'; end if;

  if exists (
    select 1 from public.blocks b
    where (b.blocker_id = v_me and b.blocked_id = v_receiver)
       or (b.blocker_id = v_receiver and b.blocked_id = v_me)
  ) then
    raise exception 'USER_BLOCKED';
  end if;

  select c.id into v_conversation
  from public.conversations c
  where coalesce(c.is_group, false) = false
    and exists (
      select 1 from public.conversation_participants cp
      where cp.conversation_id = c.id and cp.user_id = v_me
    )
    and exists (
      select 1 from public.conversation_participants cp
      where cp.conversation_id = c.id and cp.user_id = v_receiver
    )
  limit 1;

  if v_conversation is null then
    insert into public.conversations(created_by, is_group, last_message_at)
    values(v_me, false, now())
    returning id into v_conversation;

    insert into public.conversation_participants(conversation_id, user_id)
    values (v_conversation, v_me), (v_conversation, v_receiver)
    on conflict do nothing;
  end if;

  return v_conversation;
end;
$$;

revoke all on function public.get_or_create_direct_conversation(text) from public, anon;
grant execute on function public.get_or_create_direct_conversation(text) to authenticated;


create or replace function public.complete_profile_onboarding(
  p_university text,
  p_department text,
  p_academic_level text,
  p_bio text default '',
  p_core_skills text[] default '{}'::text[],
  p_phone text default null,
  p_whatsapp text default null
)
returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_me uuid := auth.uid();
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if nullif(trim(p_university), '') is null then raise exception 'UNIVERSITY_REQUIRED'; end if;
  if nullif(trim(p_department), '') is null then raise exception 'DEPARTMENT_REQUIRED'; end if;
  if nullif(trim(p_academic_level), '') is null then raise exception 'ACADEMIC_LEVEL_REQUIRED'; end if;

  update public.profiles
     set university = trim(p_university),
         department = trim(p_department),
         academic_level = trim(p_academic_level),
         bio = coalesce(trim(p_bio), ''),
         core_skills = coalesce(p_core_skills, '{}'::text[]),
         phone = coalesce(nullif(trim(p_phone), ''), phone),
         whatsapp = coalesce(nullif(trim(p_whatsapp), ''), whatsapp),
         onboarding_completed = true,
         updated_at = now()
   where id = v_me;

  if not found then raise exception 'PROFILE_NOT_FOUND'; end if;
  return true;
end;
$$;

revoke all on function public.complete_profile_onboarding(text,text,text,text,text[],text,text) from public, anon;
grant execute on function public.complete_profile_onboarding(text,text,text,text,text[],text,text) to authenticated;

commit;
