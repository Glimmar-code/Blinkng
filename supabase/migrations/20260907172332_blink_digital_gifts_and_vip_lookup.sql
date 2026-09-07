create table if not exists public.blink_digital_gifts (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references public.profiles(id) on delete cascade,
  recipient_id uuid not null references public.profiles(id) on delete cascade,
  inventory_id uuid references public.blink_inventory(id) on delete set null,
  message text,
  created_at timestamptz not null default now(),
  check (sender_id <> recipient_id),
  check (message is null or char_length(message) <= 200)
);

create index if not exists blink_digital_gifts_sender_idx on public.blink_digital_gifts(sender_id, created_at desc);
create index if not exists blink_digital_gifts_recipient_idx on public.blink_digital_gifts(recipient_id, created_at desc);

alter table public.blink_digital_gifts enable row level security;

drop policy if exists blink_digital_gifts_participant_read on public.blink_digital_gifts;
create policy blink_digital_gifts_participant_read
on public.blink_digital_gifts for select to authenticated
using ((select auth.uid()) = sender_id or (select auth.uid()) = recipient_id);

revoke all on public.blink_digital_gifts from anon, authenticated;
grant select on public.blink_digital_gifts to authenticated;

create or replace function public.send_blink_digital_gift(
  p_inventory_id uuid,
  p_recipient_username text,
  p_message text default null
) returns jsonb
language plpgsql security definer set search_path=''
as $$
declare
  v_user uuid := auth.uid();
  v_recipient uuid;
  v_recipient_username text;
  v_inv public.blink_inventory%rowtype;
  v_gift uuid;
  v_message text := nullif(btrim(coalesce(p_message,'')), '');
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_recipient_username is null or btrim(p_recipient_username) = '' then raise exception 'RECIPIENT_REQUIRED'; end if;
  if v_message is not null and char_length(v_message) > 200 then raise exception 'MESSAGE_TOO_LONG'; end if;

  select p.id, p.username into v_recipient, v_recipient_username
  from public.profiles p
  where lower(p.username) = lower(regexp_replace(btrim(p_recipient_username), '^@', ''))
  limit 1;

  if v_recipient is null or v_recipient = v_user then raise exception 'INVALID_RECIPIENT'; end if;

  select * into v_inv
  from public.blink_inventory
  where id = p_inventory_id and user_id = v_user
  for update;

  if not found then raise exception 'INVENTORY_NOT_FOUND'; end if;
  if v_inv.catalog_id <> 'digital_gift' or v_inv.status <> 'AVAILABLE' or v_inv.quantity < 1 then
    raise exception 'DIGITAL_GIFT_NOT_AVAILABLE';
  end if;

  if v_inv.quantity > 1 then
    update public.blink_inventory
    set quantity = quantity - 1, updated_at = now()
    where id = v_inv.id;
  else
    update public.blink_inventory
    set status = 'USED', activated_at = now(), target_type = 'PROFILE', target_id = v_recipient, updated_at = now()
    where id = v_inv.id;
  end if;

  insert into public.blink_item_activations(user_id, inventory_id, catalog_id, target_type, target_id, started_at, metadata)
  values(v_user, v_inv.id, 'digital_gift', 'PROFILE', v_recipient, now(), jsonb_build_object('recipient_username', v_recipient_username));

  insert into public.blink_digital_gifts(sender_id, recipient_id, inventory_id, message)
  values(v_user, v_recipient, v_inv.id, v_message)
  returning id into v_gift;

  insert into public.notifications(user_id, actor_id, type, comment)
  values(
    v_recipient,
    v_user,
    'system',
    '🎁 You received a Blink digital gift' || case when v_message is null then '.' else ': ' || v_message end
  );

  return jsonb_build_object(
    'success', true,
    'gift_id', v_gift,
    'recipient_id', v_recipient,
    'recipient_username', v_recipient_username
  );
end
$$;

revoke all on function public.send_blink_digital_gift(uuid,text,text) from public, anon;
grant execute on function public.send_blink_digital_gift(uuid,text,text) to authenticated;

create or replace function public.get_blink_vip_status_by_username(p_username text) returns jsonb
language sql stable security definer set search_path=''
as $$
  select coalesce((
    select jsonb_build_object(
      'user_id', p.id,
      'username', p.username,
      'is_vip', private.is_blink_vip_id(p.id, now()),
      'vip_until', p.blink_vip_until
    )
    from public.profiles p
    where lower(p.username) = lower(regexp_replace(btrim(coalesce(p_username,'')), '^@', ''))
    limit 1
  ), jsonb_build_object('is_vip', false));
$$;

revoke all on function public.get_blink_vip_status_by_username(text) from public, anon;
grant execute on function public.get_blink_vip_status_by_username(text) to authenticated;
