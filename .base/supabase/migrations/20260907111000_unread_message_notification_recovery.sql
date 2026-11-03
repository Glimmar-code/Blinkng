create or replace function public.get_my_unread_message_notifications(p_limit integer default 200)
returns table (
  message_id uuid,
  conversation_id uuid,
  sender_id uuid,
  sender_username text,
  sender_name text,
  sender_avatar text,
  content text,
  created_at timestamptz
)
language sql
security definer
set search_path = ''
as $$
  select
    m.id as message_id,
    m.conversation_id,
    m.sender_id,
    coalesce(p.username, '') as sender_username,
    coalesce(nullif(btrim(p.full_name), ''), nullif(btrim(p.username), ''), 'Blink user') as sender_name,
    coalesce(p.avatar_url, '') as sender_avatar,
    m.content,
    m.created_at
  from public.messages m
  join public.conversation_participants cp
    on cp.conversation_id = m.conversation_id
   and cp.user_id = auth.uid()
  left join public.profiles p
    on p.id = m.sender_id
  where auth.uid() is not null
    and m.sender_id <> auth.uid()
    and coalesce(m.is_read, false) = false
    and m.read_at is null
    and coalesce(m.deleted_for_everyone, false) = false
    and m.created_at > coalesce(cp.last_read_at, '-infinity'::timestamptz)
  order by m.created_at asc
  limit least(greatest(coalesce(p_limit, 200), 1), 200);
$$;

revoke execute on function public.get_my_unread_message_notifications(integer) from public, anon;
grant execute on function public.get_my_unread_message_notifications(integer) to authenticated;
