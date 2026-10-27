-- BLINK Drops: fail closed when no followers can receive the reserved Coins.
-- The exception aborts the enclosing transaction, rolling back the earlier wallet debit,
-- Drop creation and activity notifications as one atomic operation.
create or replace function public.create_blink_drop_v2(
  p_target_type text,
  p_target_id uuid,
  p_action text,
  p_reward_per_user integer,
  p_winner_count integer,
  p_audience_scope text,
  p_duration_hours integer,
  p_request_id uuid
) returns jsonb
language plpgsql
security definer
set search_path=''
as $$
declare
  v_user uuid := auth.uid();
  v_result jsonb;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  if p_request_id is null then raise exception 'REQUEST_ID_REQUIRED'; end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text||':DROP:'||p_request_id::text));

  select i.result into v_result
  from private.blink_growth_request_idempotency i
  where i.user_id=v_user and i.scope='DROP' and i.request_id=p_request_id;
  if v_result is not null then return v_result; end if;

  v_result := public.create_blink_drop(
    p_target_type,p_target_id,p_action,p_reward_per_user,p_winner_count,
    p_audience_scope,p_duration_hours
  );

  if coalesce((v_result->>'eligible_followers')::integer,0) < 1 then
    raise exception 'DROP_NO_ELIGIBLE_FOLLOWERS';
  end if;

  insert into private.blink_growth_request_idempotency(user_id,scope,request_id,result)
  values(v_user,'DROP',p_request_id,v_result)
  on conflict(user_id,scope,request_id) do update set result=excluded.result;

  return v_result;
end;
$$;
revoke all on function public.create_blink_drop_v2(text,uuid,text,integer,integer,text,integer,uuid) from public, anon;
grant execute on function public.create_blink_drop_v2(text,uuid,text,integer,integer,text,integer,uuid) to authenticated;
