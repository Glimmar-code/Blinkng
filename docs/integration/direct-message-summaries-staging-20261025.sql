-- BLINK direct message summaries: STAGING ONLY, NOT DEPLOYED.
-- Same RPC signature/return as production; groups are listed separately
-- using blink_my_group_conversations, preventing duplicate fake DMs.
-- Require full schema integration checks, account tests and migration approval.
begin;
CREATE OR REPLACE FUNCTION public.get_conversation_summaries_page(p_limit integer DEFAULT 100, p_before timestamp with time zone DEFAULT NULL::timestamp with time zone, p_before_id uuid DEFAULT NULL::uuid)
 RETURNS TABLE(conversation_id uuid, partner_id uuid, partner_username text, partner_name text, partner_avatar text, partner_online boolean, partner_last_seen timestamp with time zone, last_message text, last_message_at timestamp with time zone, unread_count bigint, cursor_at timestamp with time zone)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
  with current_user_id as (
    select auth.uid() as uid
  ), summaries as (
    select
      c.id as conversation_id,
      other.user_id as partner_id,
      p.username as partner_username,
      p.full_name as partner_name,
      p.avatar_url as partner_avatar,
      case when private.blink_profile_presence_visible(p.id) then coalesce(p.online_now, p.is_online, false) else false end as partner_online,
      case when private.blink_profile_presence_visible(p.id) then coalesce(p.last_seen_at, p.last_seen) else null end as partner_last_seen,
      lm.content as last_message,
      lm.created_at as last_message_at,
      coalesce(uc.unread_count, 0)::bigint as unread_count,
      coalesce(lm.created_at, c.updated_at, c.created_at) as cursor_at
    from current_user_id me
    join public.conversation_participants mine
      on mine.user_id = me.uid and mine.left_at is null
    join public.conversations c
      on c.id = mine.conversation_id
     and coalesce(c.is_group,false)=false
    join public.conversation_participants other
      on other.conversation_id = c.id
     and other.user_id <> me.uid and other.left_at is null
    left join public.profiles p
      on p.id = other.user_id
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
        and m.sender_id <> me.uid
        and coalesce(m.deleted_for_everyone, false) = false
        and m.created_at > coalesce(mine.last_read_at, 'epoch'::timestamptz)
    ) uc on true
    where me.uid is not null
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
$function$

revoke all on function public.get_conversation_summaries_page(integer,timestamptz,uuid)
  from public,anon;
grant execute on function public.get_conversation_summaries_page(integer,timestamptz,uuid)
  to authenticated;
commit;
