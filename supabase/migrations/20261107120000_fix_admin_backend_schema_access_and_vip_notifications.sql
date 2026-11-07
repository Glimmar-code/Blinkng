-- Repair the admin RPC boundary and two confirmed production regressions.
--
-- Authenticated clients intentionally have no USAGE on schema private. These
-- public wrappers delegate to private SECURITY DEFINER implementations which
-- independently enforce require_blink_admin()/require_blink_owner(). Elevate
-- only the wrapper execution context; never grant clients access to private.
--
-- Keep the fixed empty search_path on every elevated wrapper.

alter function public.admin_dashboard_stats() security definer;
alter function public.admin_execute_feature(integer,uuid,text,bigint,integer,jsonb) security definer;
alter function public.admin_execute_feature_v2(integer,text,text,bigint,integer,jsonb) security definer;
alter function public.admin_get_universities() security definer;
alter function public.admin_global_search_v2(text) security definer;
alter function public.admin_grant_coins(uuid,bigint,text) security definer;
alter function public.admin_grant_role(uuid,integer) security definer;
alter function public.admin_history_v2(integer,integer) security definer;
alter function public.admin_list_features() security definer;
alter function public.admin_list_features_v2() security definer;
alter function public.admin_post_action(uuid,text,numeric) security definer;
alter function public.admin_revert_action_v2(uuid,text) security definer;
alter function public.admin_revoke_role(uuid) security definer;
alter function public.admin_search_posts_v2(text,integer) security definer;
alter function public.admin_search_universities_v2(text,integer) security definer;
alter function public.admin_search_users(text,integer) security definer;
alter function public.admin_search_users_v2(text,integer) security definer;
alter function public.admin_send_announcement(text,uuid,text,text) security definer;
alter function public.admin_set_verification(uuid,text,integer) security definer;

-- Reassert a safe search_path and intended API grants on the elevated wrappers.
do $migration$
declare
  v_signature text;
  v_signatures text[] := array[
    'public.admin_dashboard_stats()',
    'public.admin_execute_feature(integer,uuid,text,bigint,integer,jsonb)',
    'public.admin_execute_feature_v2(integer,text,text,bigint,integer,jsonb)',
    'public.admin_get_universities()',
    'public.admin_global_search_v2(text)',
    'public.admin_grant_coins(uuid,bigint,text)',
    'public.admin_grant_role(uuid,integer)',
    'public.admin_history_v2(integer,integer)',
    'public.admin_list_features()',
    'public.admin_list_features_v2()',
    'public.admin_post_action(uuid,text,numeric)',
    'public.admin_revert_action_v2(uuid,text)',
    'public.admin_revoke_role(uuid)',
    'public.admin_search_posts_v2(text,integer)',
    'public.admin_search_universities_v2(text,integer)',
    'public.admin_search_users(text,integer)',
    'public.admin_search_users_v2(text,integer)',
    'public.admin_send_announcement(text,uuid,text,text)',
    'public.admin_set_verification(uuid,text,integer)'
  ];
begin
  foreach v_signature in array v_signatures loop
    execute format('alter function %s set search_path = %L',v_signature,'');
    execute format('revoke all on function %s from public, anon',v_signature);
    execute format('grant execute on function %s to authenticated, service_role',v_signature);
  end loop;
end
$migration$;

-- Daily streak writes are scoped to auth.uid(); SECURITY DEFINER is required
-- because the per-user state table is intentionally in the private schema.
create or replace function public.touch_daily_streak()
returns integer
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_uid uuid := auth.uid();
  v_today date := (now() at time zone 'UTC')::date;
  v_last date;
  v_streak integer;
begin
  if v_uid is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  if not exists (select 1 from public.profiles where id = v_uid) then
    raise exception 'PROFILE_NOT_FOUND';
  end if;

  insert into private.user_streak_state (user_id, last_active_date, current_streak)
  values (v_uid, null, 0)
  on conflict (user_id) do nothing;

  select last_active_date, current_streak
  into v_last, v_streak
  from private.user_streak_state
  where user_id = v_uid
  for update;

  if v_last is null then
    v_streak := 1;
  elsif v_last = v_today then
    v_streak := greatest(v_streak, 1);
  elsif v_last = v_today - 1 then
    v_streak := greatest(v_streak, 0) + 1;
  else
    v_streak := 1;
  end if;

  update private.user_streak_state
  set last_active_date = v_today,
      current_streak = v_streak,
      updated_at = now()
  where user_id = v_uid;

  update public.profiles
  set daily_streak = v_streak,
      updated_at = now()
  where id = v_uid;

  return v_streak;
end;
$function$;

revoke all on function public.touch_daily_streak() from public, anon;
grant execute on function public.touch_daily_streak() to authenticated, service_role;

-- notifications has text/sub_text, not the nonexistent comment column.
create or replace function private.expire_blink_items_and_remind_vip()
returns void
language plpgsql
security definer
set search_path = ''
as $function$
begin
  update public.blink_inventory
  set status = 'EXPIRED', updated_at = now()
  where status = 'ACTIVE' and expires_at is not null and expires_at <= now();

  update public.blink_boosts
  set status = 'ENDED'
  where status = 'ACTIVE' and ends_at <= now();

  insert into public.notifications(user_id, type, text, sub_text, is_read)
  select v.user_id,
         'system'::public.notification_type_enum,
         '👑 Your Blink VIP expires in less than 24 hours. Open Blink Store to review or renew.',
         'Blink VIP expiry reminder',
         false
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
    where v.user_id = p.id and v.expires_at > now()
  )
  where p.blink_vip_until is not null
    and p.blink_vip_until <= now();
end;
$function$;
