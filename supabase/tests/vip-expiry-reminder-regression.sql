-- The VIP reminder job must compile and write using current notification columns.
insert into public.profiles(id, blink_vip_until)
values ('00000000-0000-4000-8000-000000000091', now() - interval '1 day');
insert into public.blink_vip_passes(user_id, expires_at, expiry_reminder_sent)
values ('00000000-0000-4000-8000-000000000091', now() + interval '12 hours', false);
insert into public.blink_inventory(status, expires_at)
values ('ACTIVE', now() - interval '1 hour');
insert into public.blink_boosts(status, ends_at)
values ('ACTIVE', now() - interval '1 hour');

select private.expire_blink_items_and_remind_vip();

do $vip_test$
begin
  if not exists (
    select 1 from public.notifications
    where user_id='00000000-0000-4000-8000-000000000091'
      and type='system'::public.notification_type_enum
      and text like '👑 Your Blink VIP expires%'
      and sub_text='Blink VIP expiry reminder'
      and is_read=false
  ) then
    raise exception 'VIP_EXPIRY_NOTIFICATION_NOT_WRITTEN';
  end if;
  if not exists (
    select 1 from public.blink_vip_passes
    where user_id='00000000-0000-4000-8000-000000000091'
      and expiry_reminder_sent
  ) then
    raise exception 'VIP_EXPIRY_REMINDER_FLAG_NOT_SET';
  end if;
  if not exists (select 1 from public.blink_inventory where status='EXPIRED') then
    raise exception 'EXPIRED_INVENTORY_NOT_MAINTAINED';
  end if;
  if not exists (select 1 from public.blink_boosts where status='ENDED') then
    raise exception 'EXPIRED_BOOST_NOT_MAINTAINED';
  end if;
end;
$vip_test$;
