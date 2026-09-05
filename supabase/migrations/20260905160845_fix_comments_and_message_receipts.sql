-- Fix comment creation: comments use author_id, not user_id.
create or replace function public.award_interaction_points()
returns trigger
language plpgsql
security definer
set search_path to 'public', 'extensions'
as $function$
begin
  if tg_table_name = 'feed_posts' then
    perform public.award_points(new.user_id, 'create_post', new.id);
  elsif tg_table_name = 'post_likes' then
    perform public.award_points(new.user_id, 'like_post', new.post_id);
  elsif tg_table_name = 'comments' then
    perform public.award_points(new.author_id, 'comment', new.post_id);
  elsif tg_table_name = 'comment_likes' then
    perform public.award_points(new.user_id, 'like_comment', new.comment_id);
  elsif tg_table_name = 'follows' then
    perform public.award_points(new.follower_id, 'follow_user', new.following_id);
  end if;
  return new;
end;
$function$;

-- Persist real delivery/read receipts for direct messages.
alter table public.messages
  add column if not exists delivered_at timestamptz,
  add column if not exists read_at timestamptz;

update public.messages
set delivered_at = coalesce(delivered_at, updated_at, created_at),
    read_at = coalesce(read_at, updated_at, created_at)
where coalesce(is_read, false) = true
  and (delivered_at is null or read_at is null);

create index if not exists idx_messages_conversation_receipts
  on public.messages (conversation_id, sender_id, created_at desc)
  include (delivered_at, read_at, is_read);

-- A recipient can acknowledge only a message that belongs to a conversation they are in.
create or replace function public.ack_message_delivered(p_message_id uuid)
returns boolean
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $function$
declare
  v_me uuid := auth.uid();
  v_updated boolean := false;
begin
  if v_me is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  update public.messages m
     set delivered_at = coalesce(m.delivered_at, now())
   where m.id = p_message_id
     and m.sender_id <> v_me
     and coalesce(m.deleted_for_everyone, false) = false
     and exists (
       select 1
       from public.conversation_participants cp
       where cp.conversation_id = m.conversation_id
         and cp.user_id = v_me
     )
     and m.delivered_at is null;

  v_updated := found;

  -- Idempotent success when the same recipient already acknowledged it.
  if not v_updated then
    select exists (
      select 1
      from public.messages m
      join public.conversation_participants cp
        on cp.conversation_id = m.conversation_id
       and cp.user_id = v_me
      where m.id = p_message_id
        and m.sender_id <> v_me
        and m.delivered_at is not null
    ) into v_updated;
  end if;

  return v_updated;
end;
$function$;

revoke all on function public.ack_message_delivered(uuid) from public;
revoke all on function public.ack_message_delivered(uuid) from anon;
grant execute on function public.ack_message_delivered(uuid) to authenticated;

-- Opening a conversation marks partner messages delivered + seen and advances last_read_at.
create or replace function public.mark_conversation_read(p_partner_username text)
returns integer
language plpgsql
security definer
set search_path to 'public', 'pg_temp'
as $function$
declare
  v_me uuid := auth.uid();
  v_partner uuid;
  v_changed integer := 0;
  v_identifier text := lower(trim(leading '@' from coalesce(p_partner_username, '')));
begin
  if v_me is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;
  if v_identifier = '' then
    raise exception 'RECIPIENT_REQUIRED';
  end if;

  select p.id
    into v_partner
  from public.profiles p
  where lower(btrim(p.username)) = v_identifier
     or lower(btrim(coalesce(p.handle, ''))) = v_identifier
  order by case when lower(btrim(p.username)) = v_identifier then 0 else 1 end
  limit 1;

  if v_partner is null then
    raise exception 'RECIPIENT_NOT_FOUND';
  end if;

  update public.messages m
     set is_read = true,
         delivered_at = coalesce(m.delivered_at, now()),
         read_at = coalesce(m.read_at, now())
   where m.sender_id = v_partner
     and coalesce(m.deleted_for_everyone, false) = false
     and exists (
       select 1
       from public.conversation_participants mine
       join public.conversation_participants theirs
         on theirs.conversation_id = mine.conversation_id
        and theirs.user_id = v_partner
       where mine.user_id = v_me
         and mine.conversation_id = m.conversation_id
     )
     and (
       coalesce(m.is_read, false) = false
       or m.delivered_at is null
       or m.read_at is null
     );

  get diagnostics v_changed = row_count;

  update public.conversation_participants mine
     set last_read_at = now()
   where mine.user_id = v_me
     and exists (
       select 1
       from public.conversation_participants theirs
       where theirs.conversation_id = mine.conversation_id
         and theirs.user_id = v_partner
     );

  return v_changed;
end;
$function$;

revoke all on function public.mark_conversation_read(text) from public;
revoke all on function public.mark_conversation_read(text) from anon;
grant execute on function public.mark_conversation_read(text) to authenticated;
