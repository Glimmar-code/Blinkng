-- BLINK — security-definer privacy guard for departed conversation members.
-- STAGING ONLY. This source is not a production migration.
-- The legacy endpoints below are SECURITY DEFINER, so RLS alone CANNOT stop
-- departed members reading or acknowledging messages through these RPCs.
-- Keep signature/result shape stable for older Android + Windows clients.
begin;

create or replace function public.get_my_conversation_partners()
returns table (conversation_id uuid,partner_id uuid)
language sql stable security definer set search_path=''
as $$
  select mine.conversation_id,other.user_id
  from public.conversation_participants mine
  join public.conversation_participants other
    on other.conversation_id=mine.conversation_id
   and other.user_id<>mine.user_id and other.left_at is null
  join public.conversations c on c.id=mine.conversation_id
  where mine.user_id=(select auth.uid()) and mine.left_at is null
    and coalesce(c.is_group,false)=false
$$;
revoke all on function public.get_my_conversation_partners() from public,anon;
grant execute on function public.get_my_conversation_partners() to authenticated;

create or replace function public.get_my_unread_message_notifications(
  p_limit integer default 200
) returns table (
  message_id uuid, conversation_id uuid, sender_id uuid,
  sender_username text, sender_name text, sender_avatar text,
  content text, created_at timestamptz
)
language sql stable security definer set search_path=''
as $$
  select m.id,m.conversation_id,m.sender_id,
    coalesce(p.username,''),
    coalesce(nullif(btrim(p.full_name),''),nullif(btrim(p.username),''),'Blink user'),
    coalesce(p.avatar_url,''),m.content,m.created_at
  from public.messages m
  join public.conversation_participants cp
    on cp.conversation_id=m.conversation_id
   and cp.user_id=(select auth.uid()) and cp.left_at is null
  left join public.profiles p on p.id=m.sender_id
  where (select auth.uid()) is not null
    and m.sender_id<>(select auth.uid())
    and coalesce(m.is_read,false)=false
    and m.read_at is null
    and coalesce(m.deleted_for_everyone,false)=false
    and m.created_at > coalesce(cp.last_read_at,'-infinity'::timestamptz)
  order by m.created_at asc
  limit least(greatest(coalesce(p_limit,200),1),200)
$$;
revoke all on function public.get_my_unread_message_notifications(integer) from public,anon;
grant execute on function public.get_my_unread_message_notifications(integer) to authenticated;

create or replace function public.ack_message_delivered(p_message_id uuid)
returns boolean language plpgsql security definer set search_path=''
as $$
declare v_uid uuid:=auth.uid(); v_updated boolean:=false;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  update public.messages m
    set delivered_at=coalesce(m.delivered_at,now())
  where m.id=p_message_id
    and m.sender_id<>v_uid
    and coalesce(m.deleted_for_everyone,false)=false
    and m.delivered_at is null
    and exists (
      select 1 from public.conversation_participants cp
      where cp.conversation_id=m.conversation_id
        and cp.user_id=v_uid and cp.left_at is null
    );
  v_updated:=found;
  if not v_updated then
    select exists(
      select 1 from public.messages m
      join public.conversation_participants cp
        on cp.conversation_id=m.conversation_id
       and cp.user_id=v_uid and cp.left_at is null
      where m.id=p_message_id and m.sender_id<>v_uid
        and m.delivered_at is not null
        and coalesce(m.deleted_for_everyone,false)=false
    ) into v_updated;
  end if;
  return v_updated;
end;
$$;
revoke all on function public.ack_message_delivered(uuid) from public,anon;
grant execute on function public.ack_message_delivered(uuid) to authenticated;

create or replace function public.ack_pending_message_deliveries()
returns integer language plpgsql security definer set search_path=''
as $$
declare v_uid uuid:=auth.uid(); v_changed integer:=0;
begin
  if v_uid is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  update public.messages m
    set delivered_at=coalesce(m.delivered_at,now())
  where m.sender_id<>v_uid
    and m.delivered_at is null
    and coalesce(m.deleted_for_everyone,false)=false
    and exists (
      select 1 from public.conversation_participants cp
      where cp.conversation_id=m.conversation_id
        and cp.user_id=v_uid and cp.left_at is null
    );
  get diagnostics v_changed=row_count;
  return v_changed;
end;
$$;
revoke all on function public.ack_pending_message_deliveries() from public,anon;
grant execute on function public.ack_pending_message_deliveries() to authenticated;

-- Membership-aware paginated message reader. Preserve the established RPC
-- signature/return type, and do not rely exclusively on external table RLS.
create or replace function public.get_conversation_messages_page(
  p_conversation_id uuid, p_limit integer default 40,
  p_before timestamptz default null, p_before_id uuid default null
) returns setof public.messages
language sql stable security invoker set search_path=''
as $$
  select m.*
  from public.messages m
  where m.conversation_id=p_conversation_id
    and coalesce(m.deleted_for_everyone,false)=false
    and (select auth.uid()) is not null
    and exists (
      select 1 from public.conversation_participants cp
      where cp.conversation_id=p_conversation_id
        and cp.user_id=(select auth.uid()) and cp.left_at is null
    )
    and (p_before is null or m.created_at<p_before
      or (m.created_at=p_before and
        (p_before_id is null or m.id<p_before_id)))
  order by m.created_at desc,m.id desc
  limit greatest(1,least(coalesce(p_limit,40),100))
$$;
revoke all on function public.get_conversation_messages_page(uuid,integer,timestamptz,uuid)
  from public,anon;
grant execute on function public.get_conversation_messages_page(uuid,integer,timestamptz,uuid)
  to authenticated;

-- The existing username-based read receipt RPC is SECURITY DEFINER.
-- Filter both sides to active direct-chat members before touching message rows.
-- Do not grant departed people indirect access via read receipt side effects.
create or replace function public.mark_conversation_read(p_partner_username text)
returns integer language plpgsql security definer set search_path=''
as $$
declare
  v_me uuid:=auth.uid();
  v_partner uuid;
  v_changed integer:=0;
  v_identifier text:=lower(trim(leading '@' from coalesce(p_partner_username,'')));
begin
  if v_me is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if v_identifier='' then raise exception 'RECIPIENT_REQUIRED'; end if;
  select p.id into v_partner
    from public.profiles p
    where lower(btrim(p.username))=v_identifier
      or lower(btrim(coalesce(p.handle,'')))=v_identifier
    order by case when lower(btrim(p.username))=v_identifier then 0 else 1 end
    limit 1;
  if v_partner is null then raise exception 'RECIPIENT_NOT_FOUND'; end if;
  update public.messages m set
    is_read=true,
    delivered_at=coalesce(m.delivered_at,now()),
    read_at=coalesce(m.read_at,now())
  where m.sender_id=v_partner
    and coalesce(m.deleted_for_everyone,false)=false
    and exists (
      select 1 from public.conversations c
      join public.conversation_participants mine
        on mine.conversation_id=c.id
       and mine.user_id=v_me and mine.left_at is null
      join public.conversation_participants theirs
        on theirs.conversation_id=c.id
       and theirs.user_id=v_partner and theirs.left_at is null
      where c.id=m.conversation_id and coalesce(c.is_group,false)=false
    )
    and (coalesce(m.is_read,false)=false
      or m.delivered_at is null or m.read_at is null);
  get diagnostics v_changed=row_count;
  update public.conversation_participants mine
    set last_read_at=now()
  where mine.user_id=v_me and mine.left_at is null
    and exists (
      select 1 from public.conversations c
      join public.conversation_participants theirs
        on theirs.conversation_id=c.id
       and theirs.user_id=v_partner and theirs.left_at is null
      where c.id=mine.conversation_id and coalesce(c.is_group,false)=false
    );
  -- Only clear legitimate DM notifications for an active, still-authorized
  -- conversation; a disconnected former participant cannot perform this action.
  if exists (
    select 1 from public.conversations c
    join public.conversation_participants mine
      on mine.conversation_id=c.id
     and mine.user_id=v_me and mine.left_at is null
    join public.conversation_participants theirs
      on theirs.conversation_id=c.id
     and theirs.user_id=v_partner and theirs.left_at is null
    where coalesce(c.is_group,false)=false
  ) then
    update public.notifications n set is_read=true
    where n.user_id=v_me and n.actor_id=v_partner
      and coalesce(n.is_read,false)=false
      and lower(coalesce(n.text,'')) like '% sent you a message%';
  end if;
  return v_changed;
end;
$$;
revoke all on function public.mark_conversation_read(text) from public,anon;
grant execute on function public.mark_conversation_read(text) to authenticated;
commit;

-- Verify live role grants and migration snapshots before deployment.
-- Include regression tests for active DMs, groups, old unread content,
-- user leave/rejoin behavior and idempotent delivery acknowledgements.
