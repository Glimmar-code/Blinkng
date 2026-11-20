-- Run as database owner against Testlab after applying the admin backend
-- stabilization migration. The transaction is read-only and rolls back.
begin;

do $test$
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
  v_oid regprocedure;
begin
  if has_schema_privilege('authenticated','private','USAGE') then
    raise exception 'PRIVATE_SCHEMA_EXPOSED_TO_AUTHENTICATED';
  end if;
  if has_schema_privilege('anon','private','USAGE') then
    raise exception 'PRIVATE_SCHEMA_EXPOSED_TO_ANON';
  end if;

  foreach v_signature in array v_signatures loop
    v_oid := to_regprocedure(v_signature);
    if v_oid is null then raise exception 'MISSING_ADMIN_RPC: %',v_signature; end if;
    if not (select prosecdef from pg_proc where oid=v_oid) then
      raise exception 'ADMIN_WRAPPER_NOT_SECURITY_DEFINER: %',v_signature;
    end if;
    if not has_function_privilege('authenticated',v_oid,'EXECUTE') then
      raise exception 'AUTHENTICATED_CANNOT_EXECUTE_ADMIN_RPC: %',v_signature;
    end if;
    if has_function_privilege('anon',v_oid,'EXECUTE') then
      raise exception 'ANON_CAN_EXECUTE_ADMIN_RPC: %',v_signature;
    end if;
    if not (select coalesce(proconfig @> array['search_path=""'],false) from pg_proc where oid=v_oid) then
      raise exception 'UNSAFE_SEARCH_PATH: %',v_signature;
    end if;
  end loop;

  if not (select prosecdef from pg_proc where oid='public.touch_daily_streak()'::regprocedure) then
    raise exception 'DAILY_STREAK_WRAPPER_NOT_SECURITY_DEFINER';
  end if;
  if not (select pg_get_functiondef('private.expire_blink_items_and_remind_vip()'::regprocedure) ilike '%notifications(user_id, type, text, sub_text, is_read)%') then
    raise exception 'VIP_REMINDER_NOTIFICATION_CONTRACT_NOT_FIXED';
  end if;
  if (select pg_get_functiondef('private.expire_blink_items_and_remind_vip()'::regprocedure) ilike '%notifications(user_id,type,comment)%') then
    raise exception 'VIP_REMINDER_STILL_REFERENCES_MISSING_COMMENT_COLUMN';
  end if;
end
$test$;

rollback;
