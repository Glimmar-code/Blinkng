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
begin
  perform private.admin_ensure_control(p_actor,p_target);
  if p_amount=0 then raise exception 'COIN_AMOUNT_CANNOT_BE_ZERO'; end if;

  if p_amount > 50 and not private.is_blink_owner_id(p_actor) then
    raise exception 'ONLY_BLINK_OWNER_CAN_GRANT_MORE_THAN_50_COINS' using errcode='42501';
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
  if p_amount > 50 and not private.is_blink_owner_id(v_actor) then
    raise exception 'ONLY_BLINK_OWNER_CAN_GRANT_MORE_THAN_50_COINS' using errcode='42501';
  end if;
  if not exists(select 1 from public.profiles where id=p_user_id) then
    raise exception 'PROFILE_NOT_FOUND' using errcode='P0002';
  end if;

  insert into public.game_profiles(user_id) values(p_user_id) on conflict(user_id) do nothing;
  update public.game_profiles
    set coins=coins+p_amount, updated_at=now()
    where user_id=p_user_id
    returning coins into v_balance;

  select user_id into v_owner from private.admin_roles where role='owner' limit 1;
  insert into public.activities(recipient_id,actor_id,activity_type,entity_type,message,is_read)
  values(p_user_id,v_owner,'admin_coin_grant','system','Blink • You received '||p_amount||' Blink Coins.',false);
  insert into public.notifications(user_id,actor_id,type,text,sub_text,is_read)
  values(p_user_id,v_owner,'system'::public.notification_type_enum,'You received '||p_amount||' Blink Coins.',coalesce(nullif(trim(p_reason),''),'Admin grant'),false);
  insert into private.admin_audit_log(actor_id,action,target_user_id,details)
  values(v_actor,'grant_coins',p_user_id,jsonb_build_object('amount',p_amount,'reason',coalesce(p_reason,'')));

  return jsonb_build_object('ok',true,'balance',v_balance);
end;
$$;

create or replace function private.admin_set_verification_internal(
  p_actor uuid,
  p_target uuid,
  p_badge text,
  p_hours integer default 720
)
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_badge text:=upper(coalesce(p_badge,'NONE'));
  v_hours integer:=greatest(1,least(coalesce(p_hours,720),8760));
  v_exp timestamptz;
begin
  perform private.admin_assert_target_user(p_actor,p_target);
  if v_badge not in ('NONE','BLUE','GOLD') then raise exception 'INVALID_VERIFICATION_BADGE'; end if;
  if private.is_blink_owner_id(p_target) and not private.is_blink_owner_id(p_actor) then
    raise exception 'BLINK_OWNER_VERIFICATION_IS_PROTECTED' using errcode='42501';
  end if;
  if v_badge='GOLD' and not private.is_blink_owner_id(p_actor) then
    raise exception 'ONLY_BLINK_OWNER_CAN_GRANT_GOLD_VERIFICATION' using errcode='42501';
  end if;
  if v_badge='BLUE' and v_hours > 48 and not private.is_blink_owner_id(p_actor) then
    raise exception 'ONLY_BLINK_OWNER_CAN_GRANT_BLUE_VERIFICATION_OVER_48_HOURS' using errcode='42501';
  end if;

  if v_badge='NONE' then
    update public.profiles
      set verification_badge='NONE',verification_tier='None'::public.verification_tier_enum,
          is_verified=false,verified_at=null,verification_expires_at=null
      where id=p_target;
    return null;
  end if;

  v_exp:=now()+make_interval(hours=>v_hours);
  update public.profiles
    set verification_badge=v_badge,
        verification_tier=case when v_badge='GOLD' then 'Gold'::public.verification_tier_enum else 'Standard'::public.verification_tier_enum end,
        is_verified=true,verified_at=coalesce(verified_at,now()),verification_expires_at=v_exp
    where id=p_target;
  return v_exp;
end;
$$;

create or replace function private.admin_set_verification_impl(
  p_user_id uuid,
  p_badge text,
  p_duration_hours integer default 720
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid;
  v_owner uuid;
  v_badge text := upper(trim(coalesce(p_badge,'')));
  v_hours integer;
  v_expires timestamptz;
begin
  v_actor := private.require_blink_admin();
  if p_user_id is null or v_badge not in ('NONE','BLUE','GOLD') then
    raise exception 'INVALID_VERIFICATION' using errcode='22023';
  end if;

  if v_badge='NONE' then
    v_expires := null;
    update public.profiles
      set verification_badge='NONE', verification_tier='None'::public.verification_tier_enum,
          is_verified=false, verified_at=null, verification_expires_at=null, updated_at=now()
      where id=p_user_id;
  else
    if p_duration_hours is null or p_duration_hours < 1 or p_duration_hours > 8760 then
      raise exception 'INVALID_DURATION' using errcode='22023';
    end if;
    v_hours := p_duration_hours;
    if v_badge='GOLD' and not private.is_blink_owner_id(v_actor) then
      raise exception 'ONLY_BLINK_OWNER_CAN_GRANT_GOLD_VERIFICATION' using errcode='42501';
    end if;
    if v_badge='BLUE' and v_hours > 48 and not private.is_blink_owner_id(v_actor) then
      raise exception 'ONLY_BLINK_OWNER_CAN_GRANT_BLUE_VERIFICATION_OVER_48_HOURS' using errcode='42501';
    end if;

    v_expires := now() + make_interval(hours=>v_hours);
    update public.profiles
      set verification_badge=v_badge,
          verification_tier=(case when v_badge='GOLD' then 'Gold' else 'Standard' end)::public.verification_tier_enum,
          is_verified=true, verified_at=now(), verification_expires_at=v_expires, updated_at=now()
      where id=p_user_id;
  end if;

  if not found then raise exception 'PROFILE_NOT_FOUND' using errcode='P0002'; end if;
  select user_id into v_owner from private.admin_roles where role='owner' limit 1;
  insert into public.activities(recipient_id,actor_id,activity_type,entity_type,message,is_read)
  values(p_user_id,v_owner,'admin_verification','system',case when v_badge='NONE' then 'Blink • Your verification was updated.' else 'Blink • '||v_badge||' verification activated.' end,false);
  insert into private.admin_audit_log(actor_id,action,target_user_id,details)
  values(v_actor,'set_verification',p_user_id,jsonb_build_object('badge',v_badge,'expires_at',v_expires));

  return jsonb_build_object('ok',true,'badge',v_badge,'expires_at',v_expires);
end;
$$;