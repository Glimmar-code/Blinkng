-- Fix stale notification writers after public.notifications.comment was removed.
-- Keep the functions server-authoritative and change only the notification payload column.

create or replace function private.expire_blink_items_and_remind_vip()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.blink_inventory
     set status = 'EXPIRED', updated_at = now()
   where status = 'ACTIVE'
     and expires_at is not null
     and expires_at <= now();

  update public.blink_boosts
     set status = 'ENDED'
   where status = 'ACTIVE'
     and ends_at <= now();

  insert into public.notifications(user_id, type, text)
  select
    v.user_id,
    'system',
    '👑 Your Blink VIP expires in less than 24 hours. Open Blink Store to review or renew.'
  from public.blink_vip_passes v
  where v.expires_at > now()
    and v.expires_at <= now() + interval '24 hours'
    and not v.expiry_reminder_sent;

  update public.blink_vip_passes
     set expiry_reminder_sent = true
   where expires_at > now()
     and expires_at <= now() + interval '24 hours'
     and not expiry_reminder_sent;

  update public.profiles p
     set blink_vip_until = (
       select max(v.expires_at)
       from public.blink_vip_passes v
       where v.user_id = p.id
         and v.expires_at > now()
     )
   where p.blink_vip_until is not null
     and p.blink_vip_until <= now();
end
$$;

create or replace function private.process_blink_vip_auto_renewals()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  r record;
  v_price integer := 350;
  v_balance bigint;
  v_pass uuid;
begin
  for r in
    select distinct on (user_id) id, user_id, expires_at
    from public.blink_vip_passes
    where auto_renew = true
      and expires_at <= now()
      and expires_at > now() - interval '2 hours'
    order by user_id, expires_at desc
  loop
    if exists (
      select 1
      from public.blink_vip_passes n
      where n.user_id = r.user_id
        and n.starts_at >= r.expires_at
        and n.expires_at > r.expires_at
    ) then
      continue;
    end if;

    insert into public.user_balances(user_id, spendable_coin_balance)
    values (r.user_id, 0)
    on conflict (user_id) do nothing;

    select spendable_coin_balance
      into v_balance
      from public.user_balances
     where user_id = r.user_id
     for update;

    if v_balance >= v_price then
      update public.user_balances
         set spendable_coin_balance = spendable_coin_balance - v_price,
             updated_at = now()
       where user_id = r.user_id
      returning spendable_coin_balance into v_balance;

      v_pass := private.activate_blink_vip_pass(r.user_id, null, null);

      update public.blink_vip_passes
         set auto_renew = true
       where id = v_pass;

      insert into public.blink_coin_transactions(
        user_id, kind, catalog_id, item_name, amount, balance_after
      )
      values (
        r.user_id, 'VIP_AUTO_RENEW', 'blink_vip_10d',
        'Blink VIP — 10 Days', -v_price, v_balance
      );

      insert into public.notifications(user_id, type, text)
      values (r.user_id, 'system', '👑 Blink VIP renewed for another 10 days.');
    else
      update public.blink_vip_passes
         set auto_renew = false
       where id = r.id;

      insert into public.notifications(user_id, type, text)
      values (
        r.user_id,
        'system',
        'Blink VIP auto-renew could not complete because your Blink Coin balance is too low.'
      );
    end if;
  end loop;
end
$$;

create or replace function public.gift_blink_vip(p_recipient_username text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_recipient uuid;
  v_item public.blink_store_catalog%rowtype;
  v_price integer;
  v_balance bigint;
  v_inv uuid;
begin
  if v_user is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  select id
    into v_recipient
    from public.profiles
   where lower(username) = lower(trim(p_recipient_username))
   limit 1;

  if v_recipient is null or v_recipient = v_user then
    raise exception 'INVALID_RECIPIENT';
  end if;

  select *
    into v_item
    from public.blink_store_catalog
   where id = 'blink_vip_10d';

  v_price := private.blink_effective_price(v_user, v_item, 1);

  insert into public.user_balances(user_id, spendable_coin_balance)
  values (v_user, 0)
  on conflict (user_id) do nothing;

  select spendable_coin_balance
    into v_balance
    from public.user_balances
   where user_id = v_user
   for update;

  if v_balance < v_price then
    raise exception 'INSUFFICIENT_BLINK_COINS';
  end if;

  update public.user_balances
     set spendable_coin_balance = spendable_coin_balance - v_price,
         updated_at = now()
   where user_id = v_user
  returning spendable_coin_balance into v_balance;

  insert into public.blink_inventory(
    user_id, catalog_id, quantity, status, gifted_by, metadata
  )
  values (
    v_recipient, 'blink_vip_10d', 1, 'AVAILABLE', v_user,
    jsonb_build_object('gift', true)
  )
  returning id into v_inv;

  insert into public.blink_coin_transactions(
    user_id, kind, catalog_id, item_name, amount, balance_after, metadata
  )
  values (
    v_user, 'VIP_GIFT', 'blink_vip_10d', 'Blink VIP — 10 Days gift',
    -v_price, v_balance, jsonb_build_object('recipient_id', v_recipient)
  );

  insert into public.notifications(user_id, actor_id, type, text)
  values (
    v_recipient,
    v_user,
    'system',
    '👑 You received Blink VIP — 10 Days. Open Blink Store → Vault to activate it.'
  );

  return jsonb_build_object(
    'success', true,
    'balance', v_balance,
    'recipient_id', v_recipient,
    'inventory_id', v_inv
  );
end
$$;

create or replace function public.send_blink_digital_gift(
  p_inventory_id uuid,
  p_recipient_username text,
  p_message text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_recipient uuid;
  v_recipient_username text;
  v_inv public.blink_inventory%rowtype;
  v_gift uuid;
  v_message text := nullif(btrim(coalesce(p_message, '')), '');
begin
  if v_user is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  if p_recipient_username is null or btrim(p_recipient_username) = '' then
    raise exception 'RECIPIENT_REQUIRED';
  end if;

  if v_message is not null and char_length(v_message) > 200 then
    raise exception 'MESSAGE_TOO_LONG';
  end if;

  select p.id, p.username
    into v_recipient, v_recipient_username
    from public.profiles p
   where lower(p.username) = lower(regexp_replace(btrim(p_recipient_username), '^@', ''))
   limit 1;

  if v_recipient is null or v_recipient = v_user then
    raise exception 'INVALID_RECIPIENT';
  end if;

  select *
    into v_inv
    from public.blink_inventory
   where id = p_inventory_id
     and user_id = v_user
   for update;

  if not found then
    raise exception 'INVENTORY_NOT_FOUND';
  end if;

  if v_inv.catalog_id <> 'digital_gift'
     or v_inv.status <> 'AVAILABLE'
     or v_inv.quantity < 1 then
    raise exception 'DIGITAL_GIFT_NOT_AVAILABLE';
  end if;

  if v_inv.quantity > 1 then
    update public.blink_inventory
       set quantity = quantity - 1,
           updated_at = now()
     where id = v_inv.id;
  else
    update public.blink_inventory
       set status = 'USED',
           activated_at = now(),
           target_type = 'PROFILE',
           target_id = v_recipient,
           updated_at = now()
     where id = v_inv.id;
  end if;

  insert into public.blink_item_activations(
    user_id, inventory_id, catalog_id, target_type, target_id, started_at, metadata
  )
  values (
    v_user, v_inv.id, 'digital_gift', 'PROFILE', v_recipient, now(),
    jsonb_build_object('recipient_username', v_recipient_username)
  );

  insert into public.blink_digital_gifts(sender_id, recipient_id, inventory_id, message)
  values (v_user, v_recipient, v_inv.id, v_message)
  returning id into v_gift;

  insert into public.notifications(user_id, actor_id, type, text)
  values (
    v_recipient,
    v_user,
    'system',
    coalesce(
      '🎁 You received a Blink digital gift'
        || case when v_message is null then '.' else ': ' || v_message end,
      '🎁 You received a Blink digital gift.'
    )
  );

  return jsonb_build_object(
    'success', true,
    'gift_id', v_gift,
    'recipient_id', v_recipient,
    'recipient_username', v_recipient_username
  );
end
$$;

-- Keep public client access exactly as before while preventing accidental PUBLIC execution.
revoke all on function public.gift_blink_vip(text) from public, anon;
grant execute on function public.gift_blink_vip(text) to authenticated;

revoke all on function public.send_blink_digital_gift(uuid, text, text) from public, anon;
grant execute on function public.send_blink_digital_gift(uuid, text, text) to authenticated;


-- The modern game client uses start_game_round/submit_game_answer, where the
-- server owns the question, correct answer, score and reward. Disable the
-- legacy generic RPC that accepted a client-supplied score.
revoke all on function public.record_game_session(text, integer, integer) from public, anon, authenticated;
grant execute on function public.record_game_session(text, integer, integer) to service_role;
