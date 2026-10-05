-- Keep legacy admin v2 wrappers authenticated-only.
-- Their private implementations already enforce Blink admin authorization.

revoke all on function public.admin_execute_feature_v2(integer,text,text,bigint,integer,jsonb) from public, anon;
grant execute on function public.admin_execute_feature_v2(integer,text,text,bigint,integer,jsonb) to authenticated;

revoke all on function public.admin_global_search_v2(text) from public, anon;
grant execute on function public.admin_global_search_v2(text) to authenticated;

revoke all on function public.admin_history_v2(integer,integer) from public, anon;
grant execute on function public.admin_history_v2(integer,integer) to authenticated;

revoke all on function public.admin_list_features_v2() from public, anon;
grant execute on function public.admin_list_features_v2() to authenticated;

revoke all on function public.admin_revert_action_v2(uuid,text) from public, anon;
grant execute on function public.admin_revert_action_v2(uuid,text) to authenticated;

revoke all on function public.admin_search_posts_v2(text,integer) from public, anon;
grant execute on function public.admin_search_posts_v2(text,integer) to authenticated;

revoke all on function public.admin_search_universities_v2(text,integer) from public, anon;
grant execute on function public.admin_search_universities_v2(text,integer) to authenticated;

revoke all on function public.admin_search_users_v2(text,integer) from public, anon;
grant execute on function public.admin_search_users_v2(text,integer) to authenticated;
