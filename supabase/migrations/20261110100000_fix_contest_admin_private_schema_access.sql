-- Repair the Contest admin RPC boundary for authenticated Android admins.
--
-- Clients must never receive USAGE on schema private. Contest admin RPCs are
-- public wrappers that enforce private.require_blink_admin(); they therefore
-- must run as SECURITY DEFINER with an empty search_path and authenticated-only
-- execution, just like the rest of the admin API.

do $migration$
declare
  v_signature text;
  v_signatures text[] := array[
    'public.admin_list_blink_contests()',
    'public.admin_save_blink_contest(uuid,jsonb)',
    'public.admin_transition_blink_contest(uuid,text)',
    'public.admin_list_blink_contest_entries(uuid)',
    'public.admin_review_blink_contest_entry(uuid,text)',
    'public.admin_review_blink_contest_vote(uuid,text,text)',
    'public.admin_blink_contest_insights(uuid)',
    'public.admin_finalize_blink_contest(uuid)',
    'public.admin_record_blink_contest_prize(uuid,text,text,text)',
    'public.admin_get_blink_contest_entry_review_history(uuid,integer)',
    'public.admin_list_blink_contest_entry_appeals(uuid,integer)',
    'public.admin_decide_blink_contest_entry_appeal(uuid,text,text)',
    'public.admin_list_blink_contest_orphan_media(integer,integer)',
    'public.admin_save_blink_contest_topic(uuid,jsonb)',
    'public.admin_set_blink_contest_topic_active(uuid,boolean)'
  ];
begin
  foreach v_signature in array v_signatures loop
    if to_regprocedure(v_signature) is not null then
      execute format('alter function %s security definer', v_signature);
      execute format('alter function %s set search_path = %L', v_signature, '');
      execute format('revoke all on function %s from public, anon', v_signature);
      execute format('grant execute on function %s to authenticated, service_role', v_signature);
    end if;
  end loop;
end
$migration$;

notify pgrst, 'reload schema';
