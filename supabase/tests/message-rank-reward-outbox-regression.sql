-- A new message must enqueue its Rank Point reward without touching profiles.points.
insert into public.profiles(id, points)
values ('00000000-0000-4000-8000-000000000093', 0);

insert into public.messages(id, sender_id, content)
values (
  '00000000-0000-4000-8000-000000000094',
  '00000000-0000-4000-8000-000000000093',
  'message outbox test'
);

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
