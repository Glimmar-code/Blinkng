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
    and c.is_group is false
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
commit;

-- Verify live role grants and migration snapshots before deployment.
-- Include regression tests for active DMs, groups, old unread content,
-- user leave/rejoin behavior and idempotent delivery acknowledgements.
