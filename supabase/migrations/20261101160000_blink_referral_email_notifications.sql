-- Referral confirmation email delivery.
-- Runs only after the existing auth/profile referral logic has created a durable
-- blink_referral_confirmed notification. Does not issue coins or alter signup confirmation.
begin;

alter table public.blink_essential_email_outbox
  drop constraint if exists blink_essential_email_outbox_kind_check;
alter table public.blink_essential_email_outbox
  add constraint blink_essential_email_outbox_kind_check
  check (kind in (
    'welcome','new_device','suspicious_activity','new_follower',
    'unread_messages','post_engagement','reel_milestone','leaderboard',
    'contest_launch','referral_confirmed'
  ));

create index if not exists blink_referral_email_due_idx
  on public.blink_essential_email_outbox(status,priority,available_at,created_at)
  where status='pending' and kind='referral_confirmed';

create or replace function private.blink_queue_referral_confirmation_email()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.metadata->>'kind' <> 'blink_referral_confirmed'
     or new.actor_id is null
     or new.user_id is null then
    return new;
  end if;

  -- Verify the notification matches a real, confirmed referral relationship.
  -- Only queue to a verified referrer's email with the existing email master
  -- preference explicitly enabled. The unique event key makes retries harmless.
  insert into public.blink_essential_email_outbox(user_id,kind,event_key,priority)
  select r.referrer_user_id,
         'referral_confirmed',
         'referral_confirmed:' || r.referred_user_id::text,
         6
    from public.blink_referrals r
    join auth.users u on u.id=r.referrer_user_id
    left join public.user_settings s on s.user_id=r.referrer_user_id
   where r.referred_user_id=new.actor_id
     and r.referrer_user_id=new.user_id
     and r.status='confirmed'
     and u.email_confirmed_at is not null
     and u.deleted_at is null
     and (u.banned_until is null or u.banned_until <= now())
     and coalesce(s.email_notifs_enabled,false) is true
  on conflict(user_id,kind,event_key) do nothing;

  return new;
end;
$$;

revoke all on function private.blink_queue_referral_confirmation_email()
  from public,anon,authenticated,service_role;

drop trigger if exists trg_blink_referral_confirmation_email
  on public.notifications;
create trigger trg_blink_referral_confirmation_email
after insert on public.notifications
for each row
when (new.metadata->>'kind'='blink_referral_confirmed')
execute function private.blink_queue_referral_confirmation_email();

notify pgrst,'reload schema';
commit;
