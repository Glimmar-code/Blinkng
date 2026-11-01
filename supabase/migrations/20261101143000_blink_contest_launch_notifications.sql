-- Notify the full BLINK audience exactly once when an admin first publishes a contest.
-- In-app notifications are durable; push and email are dispatched by existing workers.
begin;

alter table public.blink_essential_email_outbox
  drop constraint if exists blink_essential_email_outbox_kind_check;
alter table public.blink_essential_email_outbox
  add constraint blink_essential_email_outbox_kind_check
  check (kind in ('welcome','new_device','suspicious_activity','new_follower',
    'unread_messages','post_engagement','reel_milestone','leaderboard','contest_launch'));

create index if not exists blink_contest_launch_email_due_idx
  on public.blink_essential_email_outbox(status,available_at,created_at)
  where status='pending' and kind='contest_launch';

create or replace function private.blink_contest_announce_launch()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_event_key text := 'contest_launch:' || new.id::text;
begin
  -- A launch is only draft -> published. Resume/edit never broadcasts again.
  if old.status <> 'draft' or new.status <> 'published' then
    return new;
  end if;

  -- A single idempotency key protects both fan-out channels on retries.
  insert into public.notifications(
    user_id,actor_id,type,target_type,target_id,text,sub_text,is_read,metadata
  )
  select u.id,null,'system'::public.notification_type_enum,
    'contest',new.id,'A new BLINK Contest is LIVE!',
    left(new.title,240),false,
    jsonb_build_object(
      'kind','blink_contest_launch',
      'contest_id',new.id,
      'contest_title',new.title,
      'event_key',v_event_key,
      'action','open_contest'
    )
  from auth.users u
  where u.deleted_at is null
    and not exists (
      select 1 from public.notifications n
      where n.user_id=u.id
        and n.target_type='contest'
        and n.target_id=new.id
        and n.metadata->>'kind'='blink_contest_launch'
    );

  -- Email only to verified addresses, respecting the user's email master switch.
  -- The outbox unique key prevents duplicate sends if the transition is retried.
  insert into public.blink_essential_email_outbox(user_id,kind,event_key,priority)
  select u.id,'contest_launch',v_event_key,8
  from auth.users u
  left join public.user_settings s on s.user_id=u.id
  where u.email_confirmed_at is not null
    and u.deleted_at is null
    and coalesce(s.email_notifs_enabled,true)
    and (u.banned_until is null or u.banned_until <= now())
  on conflict(user_id,kind,event_key) do nothing;

  return new;
end;
$$;

revoke all on function private.blink_contest_announce_launch() from public,anon,authenticated;

drop trigger if exists trg_blink_contest_announce_launch on public.blink_contests;
create trigger trg_blink_contest_announce_launch
after update of status on public.blink_contests
for each row
when (old.status='draft' and new.status='published')
execute function private.blink_contest_announce_launch();

-- Include contest launch alerts in the existing idempotent FCM dispatch backlog.
create or replace function public.blink_claim_channel_push_notifications(
 p_limit integer default 30
) returns table(notification_id uuid)
language sql stable security definer set search_path=''
as $$
 select n.id
 from public.notifications n
 left join public.notification_push_dispatches d on d.notification_id=n.id
 where (
   (n.target_type='channel' and n.metadata->>'kind'='blink_channel_update'
     and n.created_at >= now()-interval '7 days')
   or
   (n.target_type='contest' and n.metadata->>'kind'='blink_contest_launch'
     and n.created_at >= now()-interval '30 days')
 )
 and (d.notification_id is null or
      (d.status='failed' and d.attempts<8
        and d.updated_at<now()-make_interval(secs=>least(1800,30*power(2,least(d.attempts,6)))::integer)) or
      (d.status='sending' and d.attempts<8 and d.updated_at<now()-interval '3 minutes'))
 order by n.created_at,n.id
 limit greatest(1,least(coalesce(p_limit,30),100))
$$;
revoke all on function public.blink_claim_channel_push_notifications(integer) from public,anon,authenticated;
grant execute on function public.blink_claim_channel_push_notifications(integer) to service_role;

notify pgrst,'reload schema';
commit;
