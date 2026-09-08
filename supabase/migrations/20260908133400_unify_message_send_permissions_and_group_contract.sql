-- Unify Android/Windows message authorization around one server-side permission model.

create or replace function private.can_insert_conversation_message(p_conversation_id uuid,p_sender_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
stable
as $$
declare
  v_conv public.conversations;
  v_member public.conversation_participants;
  v_other uuid;
begin
  if p_conversation_id is null or p_sender_id is null then return false; end if;
  select * into v_conv from public.conversations c where c.id=p_conversation_id;
  if not found then return false; end if;
  select * into v_member from public.conversation_participants cp
  where cp.conversation_id=p_conversation_id and cp.user_id=p_sender_id and cp.left_at is null;
  if not found or not coalesce(v_member.can_message,true) then return false; end if;
  if v_member.muted_by_admin_until is not null and v_member.muted_by_admin_until>now() then return false; end if;

  if v_conv.is_group then
    if v_conv.announcement_only and v_member.role not in ('owner','admin') then return false; end if;
    if v_conv.who_can_message='admins' and v_member.role not in ('owner','admin') then return false; end if;
    if v_conv.who_can_message='owner' and v_member.role<>'owner' then return false; end if;
    if v_conv.slow_mode_seconds>0 and v_member.role not in ('owner','admin') and exists (
      select 1 from public.messages m
      where m.conversation_id=p_conversation_id and m.sender_id=p_sender_id
        and m.created_at > now()-make_interval(secs=>v_conv.slow_mode_seconds)
    ) then return false; end if;
    return true;
  end if;

  select cp.user_id into v_other
  from public.conversation_participants cp
  where cp.conversation_id=p_conversation_id and cp.user_id<>p_sender_id and cp.left_at is null
  order by cp.joined_at asc limit 1;
  if v_other is null then return false; end if;
  return private.chat_scope_allows(p_sender_id,v_other,'message');
end;
$$;

revoke all on function private.can_insert_conversation_message(uuid,uuid) from public, anon, authenticated;

drop policy if exists "messages_insert_member" on public.messages;
create policy "messages_insert_member"
on public.messages for insert
to authenticated
with check (
  sender_id=(select auth.uid())
  and private.can_insert_conversation_message(messages.conversation_id,(select auth.uid()))
);

-- Keep v2 for existing Android releases, but make it an explicitly checked boundary instead
-- of exposing the older SECURITY DEFINER send_message endpoint directly to clients.
create or replace function public.send_message_v2(p_receiver_username text,p_content text)
returns table(message_id uuid,conversation_id uuid,created_at timestamptz)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me uuid := auth.uid();
  v_receiver uuid;
  v_message_id uuid;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if length(btrim(coalesce(p_content,'')))<1 then raise exception 'MESSAGE_REQUIRED'; end if;
  if length(p_content)>20000 then raise exception 'MESSAGE_TOO_LONG'; end if;
  select p.id into v_receiver from public.profiles p
  where lower(p.username)=lower(btrim(p_receiver_username)) limit 1;
  if v_receiver is null then raise exception 'RECIPIENT_NOT_FOUND'; end if;
  if not private.chat_scope_allows(v_me,v_receiver,'message') then raise exception 'MESSAGE_PRIVACY_BLOCKED'; end if;

  v_message_id := public.send_message(p_receiver_username,btrim(p_content));
  return query
  select m.id,m.conversation_id,m.created_at
  from public.messages m where m.id=v_message_id and m.sender_id=v_me;
end;
$$;

revoke all on function public.send_message(text,text) from public, anon, authenticated;
revoke all on function public.send_message_v2(text,text) from public, anon;
grant execute on function public.send_message_v2(text,text) to authenticated;

create or replace function public.send_conversation_message_v3(
  p_conversation_id uuid,
  p_content text,
  p_client_message_id uuid,
  p_reply_to_message_id uuid default null,
  p_message_type text default 'text',
  p_media_url text default null
)
returns table(message_id uuid,conversation_id uuid,created_at timestamptz,sequence_no bigint)
language plpgsql
security invoker
set search_path = 'public','pg_temp'
as $$
declare
  v_me uuid := auth.uid();
  v_id uuid;
  v_created timestamptz;
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_client_message_id is null then raise exception 'CLIENT_MESSAGE_ID_REQUIRED'; end if;
  if length(coalesce(p_content,''))>20000 then raise exception 'MESSAGE_TOO_LONG'; end if;
  if length(btrim(coalesce(p_content,'')))<1 and p_media_url is null then raise exception 'MESSAGE_OR_MEDIA_REQUIRED'; end if;
  if p_message_type not in ('text','image','video','audio','voice','document','gif','sticker','contact','location','post','reel','profile','marketplace','gift','poll','event') then
    raise exception 'INVALID_MESSAGE_TYPE';
  end if;
  if not private.can_insert_conversation_message(p_conversation_id,v_me) then raise exception 'MESSAGE_NOT_ALLOWED'; end if;

  perform pg_advisory_xact_lock(hashtextextended(v_me::text||':conversation-message:'||p_client_message_id::text,0));
  select m.id,m.created_at into v_id,v_created from public.messages m
  where m.sender_id=v_me and m.client_message_id=p_client_message_id limit 1;
  if v_id is not null then
    return query select m.id,m.conversation_id,m.created_at,m.sequence_no from public.messages m where m.id=v_id;
    return;
  end if;

  if p_reply_to_message_id is not null and not exists (
    select 1 from public.messages parent
    where parent.id=p_reply_to_message_id and parent.conversation_id=p_conversation_id
      and coalesce(parent.deleted_for_everyone,false)=false
  ) then raise exception 'INVALID_REPLY_TARGET'; end if;

  insert into public.messages(
    conversation_id,sender_id,content,media_url,message_type,reply_to_message_id,client_message_id
  ) values (
    p_conversation_id,v_me,btrim(coalesce(p_content,'')),p_media_url,p_message_type,p_reply_to_message_id,p_client_message_id
  ) returning id,created_at into v_id,v_created;

  return query select m.id,m.conversation_id,m.created_at,m.sequence_no from public.messages m where m.id=v_id;
end;
$$;

revoke all on function public.send_conversation_message_v3(uuid,text,uuid,uuid,text,text) from public, anon;
grant execute on function public.send_conversation_message_v3(uuid,text,uuid,uuid,text,text) to authenticated;

comment on function public.send_conversation_message_v3(uuid,text,uuid,uuid,text,text) is
  'Shared direct/group send contract with RLS-backed permissions, slow mode and idempotency.';
