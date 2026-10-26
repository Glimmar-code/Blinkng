-- BLINK Channels push retry worker authorization and server-only queue.
-- No service-role API key or worker token is embedded in source or app.
-- The random token is created inside PostgreSQL and is visible only to
-- private definer SQL. Edge function receives it through pg_net over HTTPS.
create schema if not exists private;

create table if not exists private.blink_channel_worker_credentials (
  singleton boolean primary key default true check(singleton),
  worker_token text not null check (length(worker_token)=64)
);
alter table private.blink_channel_worker_credentials enable row level security;
revoke all on private.blink_channel_worker_credentials from public,anon,authenticated,service_role;
insert into private.blink_channel_worker_credentials(singleton,worker_token)
values(true,encode(gen_random_bytes(32),'hex')) on conflict(singleton) do nothing;

create or replace function public.blink_verify_channel_worker(p_token text)
returns boolean language sql stable security definer set search_path=''
as $$
 select (length(coalesce(p_token,''))=64)
  and exists(select 1 from private.blink_channel_worker_credentials w
     where w.singleton and
       digest(w.worker_token,'sha256')=digest(coalesce(p_token,''),'sha256'))
$$;
revoke all on function public.blink_verify_channel_worker(text) from public,anon,authenticated;
grant execute on function public.blink_verify_channel_worker(text) to service_role;

-- This returns no recipient profile or token. The service-only worker
-- separately loads permitted notifications and their FCM tokens.
create or replace function public.blink_claim_channel_push_notifications(
 p_limit integer default 30
) returns table(notification_id uuid)
language sql stable security definer set search_path=''
as $$
 select n.id
 from public.notifications n
 left join public.notification_push_dispatches d on d.notification_id=n.id
 where n.target_type='channel'
   and n.metadata->>'kind'='blink_channel_update'
   and n.created_at >= now()-interval '7 days'
   and (d.notification_id is null or
        (d.status='failed' and d.attempts<8
          and d.updated_at<now()-make_interval(secs=>least(1800,30*power(2,least(d.attempts,6)))::integer)) or
        (d.status='sending' and d.attempts<8 and d.updated_at<now()-interval '3 minutes'))
 order by n.created_at,n.id
 limit greatest(1,least(coalesce(p_limit,30),100))
$$;
revoke all on function public.blink_claim_channel_push_notifications(integer) from public,anon,authenticated;
grant execute on function public.blink_claim_channel_push_notifications(integer) to service_role;

-- pg_net is asynchronous and fires after commit. The existing activity push
-- worker verifies each recipient independently and has persistent idempotence.
create or replace function private.blink_channel_dispatch_due()
returns boolean language plpgsql security definer set search_path=''
as $$
declare secret text;
begin
  perform private.blink_channel_emit_due(200);
  if not exists(select 1 from public.blink_claim_channel_push_notifications(1)) then
    return false;
  end if;
  select worker_token into secret from private.blink_channel_worker_credentials where singleton;
  if secret is null then raise exception 'CHANNEL_WORKER_SECRET_MISSING'; end if;
  perform net.http_post(
    url := 'https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/blink-channel-push-worker',
    headers := jsonb_build_object(
      'Content-Type','application/json',
      'x-blink-channel-worker',secret
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 5000
  );
  return true;
end
$$;
revoke all on function private.blink_channel_dispatch_due() from public,anon,authenticated,service_role;
