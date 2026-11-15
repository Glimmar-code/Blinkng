-- A new message must enqueue its Rank Point reward without touching profiles.points.
insert into public.profiles(id, points)
values ('00000000-0000-4000-8000-000000000093', 0);

insert into public.messages(id, sender_id, content)
values (
  '00000000-0000-4000-8000-000000000094',
  '00000000-0000-4000-8000-000000000093',
  'message outbox test'
);

do $permissions_test$
begin
  if has_function_privilege('anon', 'public.process_message_rank_reward_outbox(integer)', 'EXECUTE')
     or has_function_privilege('authenticated', 'public.process_message_rank_reward_outbox(integer)', 'EXECUTE')
     or not has_function_privilege('service_role', 'public.process_message_rank_reward_outbox(integer)', 'EXECUTE') then
    raise exception 'MESSAGE_REWARD_WORKER_EXECUTE_GRANTS_INVALID';
  end if;
  if not exists (
    select 1 from pg_class c
    join pg_namespace n on n.oid=c.relnamespace
    where n.nspname='private'
      and c.relname='blink_message_rank_reward_outbox'
      and c.relrowsecurity
      and not exists (select 1 from pg_policy p where p.polrelid=c.oid)
  ) then
    raise exception 'MESSAGE_REWARD_OUTBOX_MUST_REMAIN_PRIVATE';
  end if;
end;
$permissions_test$;

do $enqueue_test$
begin
  if (select points from public.profiles where id='00000000-0000-4000-8000-000000000093') <> 0 then
    raise exception 'MESSAGE_SEND_SYNCHRONOUSLY_LOCKED_PROFILE_POINTS';
  end if;
  if not exists (
    select 1 from private.blink_message_rank_reward_outbox
    where message_id='00000000-0000-4000-8000-000000000094'
      and sender_id='00000000-0000-4000-8000-000000000093'
      and status='pending'
  ) then
    raise exception 'MESSAGE_RANK_REWARD_NOT_QUEUED';
  end if;
  if not exists (
    select 1 from pg_trigger
    where not tgisinternal
      and tgname='trg_points_message'
      and tgfoid='public.award_message_points()'::regprocedure
  ) then
    raise exception 'MESSAGE_POINTS_TRIGGER_MISSING';
  end if;
end;
$enqueue_test$;

do $cron_wakeup_test$
begin
  if private.blink_channel_dispatch_due() is not true then
    raise exception 'MESSAGE_REWARD_OUTBOX_DID_NOT_WAKE_MINUTE_WORKER';
  end if;
end;
$cron_wakeup_test$;

select public.process_message_rank_reward_outbox(10);
select public.process_message_rank_reward_outbox(10);

do $process_test$
begin
  if (select points from public.profiles where id='00000000-0000-4000-8000-000000000093') <> 5 then
    raise exception 'MESSAGE_RANK_REWARD_NOT_APPLIED_EXACTLY_ONCE';
  end if;
  if (select count(*) from public.point_transactions
      where user_id='00000000-0000-4000-8000-000000000093'
        and action_type='message_user'
        and reference_id='00000000-0000-4000-8000-000000000094') <> 1 then
    raise exception 'MESSAGE_RANK_REWARD_LEDGER_NOT_IDEMPOTENT';
  end if;
  if not exists (
    select 1 from private.blink_message_rank_reward_outbox
    where message_id='00000000-0000-4000-8000-000000000094'
      and status='processed' and attempts=1
  ) then
    raise exception 'MESSAGE_RANK_REWARD_OUTBOX_NOT_SETTLED';
  end if;
end;
$process_test$;
