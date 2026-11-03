create or replace function public.get_conversation_summaries_page(
  p_limit integer default 100,
  p_before timestamptz default null,
  p_before_id uuid default null
)
returns table(
  conversation_id uuid,
  partner_id uuid,
  partner_username text,
  partner_name text,
  partner_avatar text,
  partner_online boolean,
  partner_last_seen timestamptz,
  last_message text,
  last_message_at timestamptz,
  unread_count bigint,
  cursor_at timestamptz
)
language sql
stable
security invoker
set search_path = 'public', 'pg_temp'
as $function$
  with summaries as (
    select
      c.id as conversation_id,
      other.user_id as partner_id,
      p.username as partner_username,
      p.full_name as partner_name,
      p.avatar_url as partner_avatar,
      coalesce(p.online_now, p.is_online, false) as partner_online,
      coalesce(p.last_seen_at, p.last_seen) as partner_last_seen,
      lm.content as last_message,
      lm.created_at as last_message_at,
      coalesce(uc.unread_count, 0)::bigint as unread_count,
      coalesce(lm.created_at, c.updated_at, c.created_at) as cursor_at
    from public.conversation_participants mine
    join public.conversations c on c.id = mine.conversation_id
    join public.conversation_participants other
      on other.conversation_id = c.id
     and other.user_id <> (select auth.uid())
    left join public.profiles p on p.id = other.user_id
    left join lateral (
      select m.content, m.created_at
      from public.messages m
      where m.conversation_id = c.id
        and coalesce(m.deleted_for_everyone, false) = false
      order by m.created_at desc, m.id desc
      limit 1
    ) lm on true
    left join lateral (
      select count(*) as unread_count
      from public.messages m
      where m.conversation_id = c.id
        and m.sender_id <> (select auth.uid())
        and coalesce(m.deleted_for_everyone, false) = false
        and m.created_at > coalesce(mine.last_read_at, 'epoch'::timestamptz)
    ) uc on true
    where mine.user_id = (select auth.uid())
  )
  select
    s.conversation_id,
    s.partner_id,
    s.partner_username,
    s.partner_name,
    s.partner_avatar,
    s.partner_online,
    s.partner_last_seen,
    s.last_message,
    s.last_message_at,
    s.unread_count,
    s.cursor_at
  from summaries s
  where p_before is null
     or s.cursor_at < p_before
     or (s.cursor_at = p_before and (p_before_id is null or s.conversation_id < p_before_id))
  order by s.cursor_at desc, s.conversation_id desc
  limit greatest(1, least(coalesce(p_limit, 100), 100));
$function$;

revoke all on function public.get_conversation_summaries_page(integer, timestamptz, uuid) from public;
revoke all on function public.get_conversation_summaries_page(integer, timestamptz, uuid) from anon;
grant execute on function public.get_conversation_summaries_page(integer, timestamptz, uuid) to authenticated;
grant execute on function public.get_conversation_summaries_page(integer, timestamptz, uuid) to service_role;
