-- TESTLAB ONLY: do not deploy to production until an isolated database validates
-- coin idempotency, RLS, privileges and the FCM notification delivery behavior.
-- Receiver notifications already exist in public.gift_blink_coins_once.
-- A sender record makes wallet transfers visible in Notifications on both clients.
-- Using the immutable gift UUID means an idempotent retry never fires this trigger.
create or replace function private.notify_blink_coin_gift_sender()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.sender_id is null or new.receiver_id is null or
     new.sender_id = new.receiver_id or new.amount is null or new.amount <= 0 then
    return new;
  end if;

  insert into public.notifications (
    user_id, actor_id, type, text, sub_text, target_type, target_id, metadata
  ) values (
    new.sender_id, new.receiver_id, 'tip',
    'You sent ' || new.amount || ' BLINK Coins',
    'Your coin transfer has been recorded.',
    'coin_gift', new.id,
    jsonb_build_object(
      'gift_id', new.id,
      'amount', new.amount,
      'sender_id', new.sender_id,
      'receiver_id', new.receiver_id,
      'direction', 'sent'
    )
  );
  return new;
end;
$$;

revoke all on function private.notify_blink_coin_gift_sender() from public, anon, authenticated;
drop trigger if exists trg_notify_blink_coin_gift_sender on public.blink_coin_gifts;
create trigger trg_notify_blink_coin_gift_sender
after insert on public.blink_coin_gifts
for each row execute function private.notify_blink_coin_gift_sender();
