-- Channels follow-up: idempotent in-app notification fanout.
-- Automatic 1-minute scheduling of this routine is configured separately
-- on production after passing free disposable PostgreSQL CI.
-- Existing direct-message and notification tables are not changed.
create schema if not exists private;

create table if not exists private.blink_channel_notification_marks (
  update_id uuid not null references public.blink_channel_updates(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(update_id,user_id)
);
create index if not exists blink_channel_notifications_marks_created
  on private.blink_channel_notification_marks(created_at desc);
alter table private.blink_channel_notification_marks enable row level security;
revoke all on private.blink_channel_notification_marks from public,anon,authenticated,service_role;
grant select on private.blink_channel_notification_marks to service_role;

create or replace function private.blink_channel_emit_due(p_limit integer default 100)
returns integer language plpgsql security definer set search_path=''
as $$
declare notified integer;
begin
  with eligible as (
    select u.id,u.channel_id,u.author_id,u.body,c.name,m.user_id
    from public.blink_channel_updates u
    join public.blink_communities c on c.id=u.channel_id
    join public.blink_community_members m
      on m.community_id=u.channel_id and m.status='active'
    left join public.blink_channel_notifications opt
      on opt.channel_id=u.channel_id and opt.user_id=m.user_id
    where u.removed_at is null
      and (u.scheduled_at is null or u.scheduled_at<=now())
      and m.user_id<>u.author_id
      and m.user_id<>c.owner_id
      and coalesce(opt.muted,false)=false
      and not exists(select 1 from private.blink_channel_notification_marks done
         where done.update_id=u.id and done.user_id=m.user_id)
    order by coalesce(u.scheduled_at,u.created_at),u.id,m.user_id
    limit greatest(1,least(coalesce(p_limit,100),200))
  ), claimed as (
    insert into private.blink_channel_notification_marks(update_id,user_id)
    select e.id,e.user_id from eligible e
    on conflict do nothing
    returning update_id,user_id
  ), ins as (
    insert into public.notifications(
      user_id,actor_id,type,target_type,target_id,text,sub_text,is_read,metadata
    )
    select claim.user_id,u.author_id,'system'::public.notification_type_enum,
      'channel',u.channel_id,
      left('New Channel update · '||c.name,180),left(u.body,240),false,
      jsonb_build_object('kind','blink_channel_update','channel_id',u.channel_id,'update_id',u.id)
    from claimed claim
    join public.blink_channel_updates u on u.id=claim.update_id
    join public.blink_communities c on c.id=u.channel_id
    returning id
  )
  select count(*)::integer into notified from ins;
  return coalesce(notified,0);
end
$$;
revoke all on function private.blink_channel_emit_due(integer) from public,anon,authenticated;
grant execute on function private.blink_channel_emit_due(integer) to service_role;

-- Immediate broadcasts are enqueued in their originating transaction, so
-- existing notification hooks may dispatch them using the actor's JWT.
-- Scheduled broadcasts become eligible only when due; the cron worker will
-- add persistent notification rows, but server-side push credentials for
-- scheduled delivery must be configured separately.
create or replace function private.blink_channel_immediate_notify()
returns trigger language plpgsql security definer set search_path=''
as $$
begin
 if new.removed_at is null and (new.scheduled_at is null or new.scheduled_at<=now()) then
   perform private.blink_channel_emit_due(200);
 end if;
 return new;
end
$$;
revoke all on function private.blink_channel_immediate_notify() from public,anon,authenticated;
drop trigger if exists blink_channel_immediate_notify on public.blink_channel_updates;
create trigger blink_channel_immediate_notify
  after insert on public.blink_channel_updates
  for each row execute function private.blink_channel_immediate_notify();
