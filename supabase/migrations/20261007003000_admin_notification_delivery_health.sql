-- Read-only notification delivery health for authenticated Blink admins.
-- Exposes aggregate counts only: never device tokens, message content, or raw failure payloads.

create or replace function private.admin_notification_delivery_health_impl()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_actor uuid;
  v_active_devices bigint := 0;
  v_users_with_push bigint := 0;
  v_social_sent bigint := 0;
  v_social_failed bigint := 0;
  v_social_sending bigint := 0;
  v_social_retried bigint := 0;
  v_message_sent bigint := 0;
  v_message_failed bigint := 0;
  v_message_sending bigint := 0;
  v_message_retried bigint := 0;
  v_social_failures_24h bigint := 0;
  v_message_failures_24h bigint := 0;
  v_failures_24h bigint := 0;
begin
  v_actor := private.require_blink_admin();

  if to_regclass('public.fcm_tokens') is not null then
    execute $q$
      select
        count(*) filter (where is_active),
        count(distinct user_id) filter (where is_active)
      from public.fcm_tokens
    $q$ into v_active_devices, v_users_with_push;
  end if;

  if to_regclass('public.notification_push_dispatches') is not null then
    execute $q$
      select
        count(*) filter (where status = 'sent'),
        count(*) filter (where status = 'failed'),
        count(*) filter (where status = 'sending'),
        count(*) filter (where attempts > 1),
        count(*) filter (where status = 'failed' and updated_at >= now() - interval '24 hours')
      from public.notification_push_dispatches
    $q$ into v_social_sent, v_social_failed, v_social_sending, v_social_retried, v_social_failures_24h;
  end if;

  if to_regclass('public.message_push_dispatches') is not null then
    execute $q$
      select
        count(*) filter (where status = 'sent'),
        count(*) filter (where status = 'failed'),
        count(*) filter (where status = 'sending'),
        count(*) filter (where attempts > 1),
        count(*) filter (where status = 'failed' and updated_at >= now() - interval '24 hours')
      from public.message_push_dispatches
    $q$ into v_message_sent, v_message_failed, v_message_sending, v_message_retried, v_message_failures_24h;
  end if;

  v_failures_24h := coalesce(v_social_failures_24h, 0) + coalesce(v_message_failures_24h, 0);

  return jsonb_build_object(
    'active_devices', coalesce(v_active_devices, 0),
    'users_with_push', coalesce(v_users_with_push, 0),
    'social_sent', coalesce(v_social_sent, 0),
    'social_failed', coalesce(v_social_failed, 0),
    'social_sending', coalesce(v_social_sending, 0),
    'social_retried', coalesce(v_social_retried, 0),
    'message_sent', coalesce(v_message_sent, 0),
    'message_failed', coalesce(v_message_failed, 0),
    'message_sending', coalesce(v_message_sending, 0),
    'message_retried', coalesce(v_message_retried, 0),
    'failures_24h', coalesce(v_failures_24h, 0)
  );
end
$$;

create or replace function public.admin_notification_delivery_health()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select private.admin_notification_delivery_health_impl()
$$;

revoke all on function public.admin_notification_delivery_health() from public, anon;
grant execute on function public.admin_notification_delivery_health() to authenticated;
