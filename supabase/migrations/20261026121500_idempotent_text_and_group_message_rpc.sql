-- Persistent client-idempotency for text and group messaging.
-- A stable client UUID guarantees that retrying after an ambiguous timeout cannot
-- create a second message. This is an additive endpoint; no old RPC is overwritten.
create table if not exists private.blink_text_message_requests (
  sender_id uuid not null references auth.users(id) on delete cascade,
  client_message_id uuid not null,
  target_key text not null,
  payload_hash text not null,
  -- Keep the idempotency tombstone if a user deletes the original message.
  message_id uuid not null,
  created_at timestamptz not null default now(),
  primary key (sender_id,client_message_id)
);
alter table private.blink_text_message_requests enable row level security;
revoke all on table private.blink_text_message_requests from public,anon,authenticated;
create index if not exists blink_text_requests_message_idx
  on private.blink_text_message_requests(message_id);

create or replace function public.blink_send_direct_text_idempotent(
  p_receiver_username text, p_content text, p_client_message_id uuid
)
returns table(message_id uuid, conversation_id uuid, created_at timestamptz)
language plpgsql volatile security definer
set search_path = ''
as $$
declare
  v_sender uuid := auth.uid();
  v_id uuid;
  v_target text := 'direct:' || lower(trim(leading '@' from btrim(coalesce(p_receiver_username,''))));
  v_body text := btrim(coalesce(p_content,''));
  v_hash text := md5(v_target || chr(31) || v_body);
  v_existing record;
begin
  if v_sender is null then
    raise exception 'AUTHENTICATION_REQUIRED' using errcode='42501';
  end if;
  if p_client_message_id is null or char_length(v_body) not between 1 and 5000
     or char_length(v_target) <= 7 then
    raise exception 'INVALID_MESSAGE' using errcode='22023';
  end if;
  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(v_sender::text || '/' || p_client_message_id::text,0)
  );
  select r.* into v_existing from private.blink_text_message_requests r
    where r.sender_id=v_sender and r.client_message_id=p_client_message_id;
  if found then
    if v_existing.target_key <> v_target or v_existing.payload_hash <> v_hash then
      raise exception 'CLIENT_MESSAGE_ID_REUSED' using errcode='22023';
    end if;
    v_id := v_existing.message_id;
  else
    v_id := public.send_message(p_receiver_username, v_body);
    insert into private.blink_text_message_requests(
      sender_id,client_message_id,target_key,payload_hash,message_id
    ) values(v_sender,p_client_message_id,v_target,v_hash,v_id);
  end if;
  return query select m.id,m.conversation_id,m.created_at
    from public.messages m where m.id=v_id and m.sender_id=v_sender;
end;
$$;
revoke all on function public.blink_send_direct_text_idempotent(text,text,uuid)
  from public,anon,authenticated;
grant execute on function public.blink_send_direct_text_idempotent(text,text,uuid)
  to authenticated;

create or replace function public.blink_send_group_text_idempotent(
  p_conversation_id uuid, p_content text, p_client_message_id uuid
)
returns table(message_id uuid, conversation_id uuid, created_at timestamptz)
language plpgsql volatile security definer
set search_path = ''
as $$
declare
  v_sender uuid := auth.uid();
  v_id uuid;
  v_target text := 'group:' || coalesce(p_conversation_id::text,'');
  v_body text := btrim(coalesce(p_content,''));
  v_hash text := md5(v_target || chr(31) || v_body);
  v_existing record;
begin
  if v_sender is null then
    raise exception 'AUTHENTICATION_REQUIRED' using errcode='42501';
  end if;
  if p_client_message_id is null or p_conversation_id is null
     or char_length(v_body) not between 1 and 5000 then
    raise exception 'INVALID_MESSAGE' using errcode='22023';
  end if;
  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(v_sender::text || '/' || p_client_message_id::text,0)
  );
  select r.* into v_existing from private.blink_text_message_requests r
    where r.sender_id=v_sender and r.client_message_id=p_client_message_id;
  if found then
    if v_existing.target_key <> v_target or v_existing.payload_hash <> v_hash then
      raise exception 'CLIENT_MESSAGE_ID_REUSED' using errcode='22023';
    end if;
    v_id := v_existing.message_id;
  else
    v_id := public.send_group_message(p_conversation_id,v_body);
    insert into private.blink_text_message_requests(
      sender_id,client_message_id,target_key,payload_hash,message_id
    ) values(v_sender,p_client_message_id,v_target,v_hash,v_id);
  end if;
  return query select m.id,m.conversation_id,m.created_at
    from public.messages m where m.id=v_id and m.sender_id=v_sender;
end;
$$;
revoke all on function public.blink_send_group_text_idempotent(uuid,text,uuid)
  from public,anon,authenticated;
grant execute on function public.blink_send_group_text_idempotent(uuid,text,uuid)
  to authenticated;
