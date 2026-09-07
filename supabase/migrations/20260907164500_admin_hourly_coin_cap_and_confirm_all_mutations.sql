-- Normal admins may distribute at most 50 positive Blink Coins in any rolling
-- 60-minute window. The immutable Blink owner is exempt.
--
-- All data-changing admin tools require an explicit confirmation in the V3 UI.

create or replace function private.admin_change_coins(
  p_actor uuid,
  p_target uuid,
  p_amount bigint,
  p_type text,
  p_reason text default null
)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_balance bigint;
  v_used bigint := 0;
begin
  perform private.admin_ensure_control(p_actor,p_target);
  if p_amount=0 then raise exception 'COIN_AMOUNT_CANNOT_BE_ZERO'; end if;

  if p_amount > 0 and not private.is_blink_owner_id(p_actor) then
    -- Serialize positive grants by one admin so concurrent requests cannot
    -- bypass the hourly allowance.
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(p_actor::text)::bigint);

    select coalesce(sum(amount),0)
      into v_used
      from private.admin_coin_transactions
     where actor_id=p_actor
       and amount>0
       and created_at > now() - interval '1 hour';

    if v_used + p_amount > 50 then
      raise exception 'NORMAL_ADMIN_HOURLY_COIN_LIMIT_EXCEEDED: % coins remaining in the current 60-minute window', greatest(50-v_used,0)
        using errcode='42501';
    end if;
  end if;

  insert into public.game_profiles(user_id,coins)
  values(p_target,greatest(p_amount,0))
  on conflict(user_id) do update
    set coins=greatest(0,public.game_profiles.coins+p_amount),updated_at=now()
  returning coins into v_balance;

  insert into private.admin_coin_transactions(actor_id,user_id,amount,transaction_type,reason)
  values(p_actor,p_target,p_amount,left(coalesce(p_type,'adjustment'),80),left(p_reason,500));

  return v_balance;
end;
$$;

create or replace function private.admin_grant_coins_impl(
  p_user_id uuid,
  p_amount bigint,
  p_reason text default 'Admin grant'
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid;
  v_owner uuid;
  v_balance bigint;
begin
  v_actor := private.require_blink_admin();

  if p_user_id is null or p_amount is null or p_amount < 1 or p_amount > 1000000 then
    raise exception 'INVALID_COIN_GRANT' using errcode='22023';
  end if;

  if not exists(select 1 from public.profiles where id=p_user_id) then
    raise exception 'PROFILE_NOT_FOUND' using errcode='P0002';
  end if;

  -- Keep every positive grant path behind the same hourly enforcement.
  v_balance := private.admin_change_coins(
    v_actor,
    p_user_id,
    p_amount,
    'grant',
    coalesce(nullif(trim(p_reason),''),'Admin grant')
  );

  select user_id into v_owner from private.admin_roles where role='owner' limit 1;

  insert into public.activities(recipient_id,actor_id,activity_type,entity_type,message,is_read)
  values(p_user_id,v_owner,'admin_coin_grant','system','Blink • You received '||p_amount||' Blink Coins.',false);

  insert into public.notifications(user_id,actor_id,type,text,sub_text,is_read)
  values(
    p_user_id,
    v_owner,
    'system'::public.notification_type_enum,
    'You received '||p_amount||' Blink Coins.',
    coalesce(nullif(trim(p_reason),''),'Admin grant'),
    false
  );

  insert into private.admin_audit_log(actor_id,action,target_user_id,details)
  values(v_actor,'grant_coins',p_user_id,jsonb_build_object('amount',p_amount,'reason',coalesce(p_reason,'')));

  return jsonb_build_object('ok',true,'balance',v_balance);
end;
$$;

-- These are the existing V3 data-changing feature ranges. Read-only searches,
-- previews, analytics and insights stay instant and do not ask for confirmation.
update private.admin_feature_registry_v2
set confirmation_kind='typed',
    description=case
      when description ilike 'Are you sure?%' then description
      else 'Are you sure? ' || description
    end
where feature_id between 1 and 200
  and (
    feature_id between 21 and 33 or
    feature_id between 35 and 43 or
    feature_id between 48 and 68 or
    feature_id in (75,76) or
    feature_id between 81 and 87 or
    feature_id between 94 and 109 or
    feature_id=111 or
    feature_id between 113 and 120 or
    feature_id between 127 and 139 or
    feature_id between 142 and 146 or
    feature_id between 153 and 156 or
    feature_id between 160 and 178 or
    feature_id between 197 and 200
  );
