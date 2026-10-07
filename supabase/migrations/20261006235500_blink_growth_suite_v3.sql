begin;

-- BLINK Growth Suite v3
-- destructive-change-reviewed
-- rollback-plan: restore the prior trigger definitions from the preceding Growth migrations, remove the Growth v3 triggers/functions/tables introduced here, remove user_balances from supabase_realtime only if this migration added it, and unschedule blink-growth-settlement-v1 if created by this migration.
-- Adds realtime wallet sync, richer quotes/analytics/receipts, idempotent spend wrappers,
-- lifecycle notifications and scheduled settlement without changing organic ranking.

create table if not exists private.blink_growth_request_idempotency (
  user_id uuid not null,
  scope text not null check (scope in ('BOOST','DROP')),
  request_id uuid not null,
  result jsonb not null,
  created_at timestamptz not null default now(),
  primary key (user_id, scope, request_id)
);
revoke all on table private.blink_growth_request_idempotency from public, anon, authenticated;

create table if not exists private.blink_growth_milestone_notices (
  campaign_id uuid not null references public.blink_boost_campaigns_v2(id) on delete cascade,
  milestone integer not null check (milestone in (25,50,75,100)),
  created_at timestamptz not null default now(),
  primary key (campaign_id, milestone)
);
revoke all on table private.blink_growth_milestone_notices from public, anon, authenticated;

create or replace function public.get_blink_growth_receipts(
  p_limit integer default 30
) returns jsonb
language sql
stable
security definer
set search_path=''
as $$
  with me as (select auth.uid() uid),
  rows as (
    select
      t.id,t.kind,t.item_name,t.amount,t.balance_after,t.metadata,t.created_at
    from public.blink_coin_transactions t, me
    where t.user_id=me.uid
      and (t.kind like 'BOOST_%' or t.kind like 'DROP_%')
    order by t.created_at desc
    limit greatest(1,least(coalesce(p_limit,30),100))
  )
  select jsonb_build_object(
    'items',coalesce(jsonb_agg(to_jsonb(rows) order by rows.created_at desc),'[]'::jsonb)
  )
  from rows
  where (select uid from me) is not null;
$$;
revoke all on function public.get_blink_growth_receipts(integer) from public, anon;
grant execute on function public.get_blink_growth_receipts(integer) to authenticated;

create or replace function public.get_blink_growth_analytics()
returns jsonb
language plpgsql
stable
security definer
set search_path=''
as $$
declare
  v_user uuid := auth.uid();
  v_boost jsonb;
  v_drops jsonb;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;

  select jsonb_build_object(
    'campaigns',count(*)::integer,
    'active',count(*) filter(where c.status='ACTIVE')::integer,
    'reserved',coalesce(sum(c.coin_budget),0)::bigint,
    'spent',coalesce(sum(c.coin_spent),0)::bigint,
    'refunded',coalesce(sum(c.coin_refunded),0)::bigint,
    'impressions',coalesce(sum(coalesce(e.impressions,0)),0)::bigint,
    'opens',coalesce(sum(coalesce(e.opens,0)),0)::bigint,
    'conversion_rate',
      case when coalesce(sum(coalesce(e.impressions,0)),0)=0 then 0
      else round(
        100.0 * sum(coalesce(e.opens,0))::numeric /
        greatest(1,sum(coalesce(e.impressions,0)))::numeric,
        2
      ) end
  ) into v_boost
  from public.blink_boost_campaigns_v2 c
  left join lateral (
    select
      count(*) filter(where d.event_type='IMPRESSION')::bigint impressions,
      count(*) filter(where d.event_type in ('CARD_OPEN','PROFILE_OPEN','LISTING_OPEN'))::bigint opens
    from public.blink_boost_delivery_events_v2 d
    where d.campaign_id=c.id
  ) e on true
  where c.user_id=v_user;

  select jsonb_build_object(
    'drops',count(*)::integer,
    'active',count(*) filter(where d.status='ACTIVE')::integer,
    'reserved',coalesce(sum(d.total_budget),0)::bigint,
    'distributed',coalesce(sum(d.coin_distributed),0)::bigint,
    'refunded',coalesce(sum(d.coin_refunded),0)::bigint,
    'recipients',coalesce(sum(d.claimed_count),0)::integer
  ) into v_drops
  from public.blink_drops d
  where d.creator_id=v_user;

  return jsonb_build_object(
    'boost',coalesce(v_boost,'{}'::jsonb),
    'drops',coalesce(v_drops,'{}'::jsonb)
  );
end;
$$;
revoke all on function public.get_blink_growth_analytics() from public, anon;
grant execute on function public.get_blink_growth_analytics() to authenticated;

create or replace function public.get_blink_boost_growth_state_v2()
returns jsonb
language plpgsql
security definer
set search_path=''
as $$
declare
  v_base jsonb;
  v_receipts jsonb;
  v_analytics jsonb;
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
  v_base := public.get_blink_boost_growth_state();
  v_receipts := public.get_blink_growth_receipts(40);
  v_analytics := public.get_blink_growth_analytics();
  return coalesce(v_base,'{}'::jsonb) || jsonb_build_object(
    'receipts',coalesce(v_receipts->'items','[]'::jsonb),
    'analytics',coalesce(v_analytics->'boost','{}'::jsonb)
  );
end;
$$;
revoke all on function public.get_blink_boost_growth_state_v2() from public, anon;
grant execute on function public.get_blink_boost_growth_state_v2() to authenticated;

create or replace function public.get_blink_drops_state_v2(
  p_limit integer default 30
) returns jsonb
language plpgsql
security definer
set search_path=''
as $$
declare
  v_user uuid := auth.uid();
  v_base jsonb;
  v_mine jsonb := '[]'::jsonb;
  v_active jsonb := '[]'::jsonb;
  v_receipts jsonb;
  v_analytics jsonb;
  v_university text;
  v_eligible_all integer := 0;
  v_eligible_campus integer := 0;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  v_base := public.get_blink_drops_state(p_limit);

  select coalesce(jsonb_agg(
    item || jsonb_build_object(
      'coin_distributed',coalesce(d.coin_distributed,0),
      'coin_refunded',coalesce(d.coin_refunded,0),
      'created_at',d.created_at,
      'starts_at',d.starts_at,
      'ends_at',d.ends_at
    )
  ),'[]'::jsonb)
  into v_mine
  from jsonb_array_elements(coalesce(v_base->'my_drops','[]'::jsonb)) item
  left join public.blink_drops d on d.id=(item->>'id')::uuid;

  select coalesce(jsonb_agg(
    item || jsonb_build_object(
      'coin_distributed',coalesce(d.coin_distributed,0),
      'coin_refunded',coalesce(d.coin_refunded,0),
      'created_at',d.created_at,
      'starts_at',d.starts_at,
      'ends_at',d.ends_at
    )
  ),'[]'::jsonb)
  into v_active
  from jsonb_array_elements(coalesce(v_base->'active_drops','[]'::jsonb)) item
  left join public.blink_drops d on d.id=(item->>'id')::uuid;

  select nullif(btrim(coalesce(p.university,'')),'')
    into v_university
  from public.profiles p where p.id=v_user;

  select count(*)::integer into v_eligible_all
  from public.follows f
  where f.following_id=v_user
    and f.follower_id<>v_user
    and not exists (
      select 1 from public.blocks b
      where (b.blocker_id=v_user and b.blocked_id=f.follower_id)
         or (b.blocker_id=f.follower_id and b.blocked_id=v_user)
    );

  select count(*)::integer into v_eligible_campus
  from public.follows f
  join public.profiles p on p.id=f.follower_id
  where f.following_id=v_user
    and f.follower_id<>v_user
    and v_university is not null
    and lower(coalesce(p.university,''))=lower(v_university)
    and not exists (
      select 1 from public.blocks b
      where (b.blocker_id=v_user and b.blocked_id=f.follower_id)
         or (b.blocker_id=f.follower_id and b.blocked_id=v_user)
    );

  v_receipts := public.get_blink_growth_receipts(40);
  v_analytics := public.get_blink_growth_analytics();

  return coalesce(v_base,'{}'::jsonb) || jsonb_build_object(
    'active_drops',v_active,
    'my_drops',v_mine,
    'eligible_followers_all',v_eligible_all,
    'eligible_followers_my_campus',v_eligible_campus,
    'receipts',coalesce(v_receipts->'items','[]'::jsonb),
    'analytics',coalesce(v_analytics->'drops','{}'::jsonb)
  );
end;
$$;
revoke all on function public.get_blink_drops_state_v2(integer) from public, anon;
grant execute on function public.get_blink_drops_state_v2(integer) to authenticated;

create or replace function public.quote_blink_boost_campaign_v2(
  p_target_type text,
  p_target_id uuid,
  p_boost_power integer,
  p_objective text,
  p_audience_scope text,
  p_duration_days integer,
  p_target_university text default null
) returns jsonb
language plpgsql
stable
security definer
set search_path=''
as $$
declare
  v_user uuid := auth.uid();
  v_quote jsonb;
  v_scope text := upper(coalesce(p_audience_scope,''));
  v_university text;
  v_audience_count bigint := 0;
  v_balance bigint := 0;
  v_cost bigint := 0;
  v_low bigint := 0;
  v_high bigint := 0;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  v_quote := public.quote_blink_boost_campaign(
    p_target_type,p_target_id,p_boost_power,p_objective,
    p_audience_scope,p_duration_days,p_target_university
  );
  v_university := nullif(v_quote->>'target_university','');

  select coalesce(floor(b.spendable_coin_balance),0)::bigint
    into v_balance
  from public.user_balances b
  where b.user_id=v_user;
  v_balance := coalesce(v_balance,0);

  select count(*)::bigint into v_audience_count
  from public.profiles p
  where p.id<>v_user
    and (
      v_scope='ALL_CAMPUSES'
      or lower(coalesce(p.university,''))=lower(coalesce(v_university,''))
    )
    and not exists (
      select 1 from public.blocks b
      where (b.blocker_id=v_user and b.blocked_id=p.id)
         or (b.blocker_id=p.id and b.blocked_id=v_user)
    );

  v_cost := coalesce((v_quote->>'coin_cost')::bigint,0);
  v_low := coalesce((v_quote->>'estimated_reach_low')::bigint,0);
  v_high := coalesce((v_quote->>'estimated_reach_high')::bigint,0);

  return v_quote || jsonb_build_object(
    'audience_user_count',v_audience_count,
    'estimated_daily_reach_low',case when p_duration_days>0 then floor(v_low::numeric/p_duration_days)::bigint else v_low end,
    'estimated_daily_reach_high',case when p_duration_days>0 then ceil(v_high::numeric/p_duration_days)::bigint else v_high end,
    'balance',v_balance,
    'remaining_balance_after_reserve',v_balance-v_cost
  );
end;
$$;
revoke all on function public.quote_blink_boost_campaign_v2(text,uuid,integer,text,text,integer,text) from public, anon;
grant execute on function public.quote_blink_boost_campaign_v2(text,uuid,integer,text,text,integer,text) to authenticated;

create or replace function public.recommend_blink_boost_power(
  p_target_type text,
  p_target_id uuid,
  p_budget bigint,
  p_objective text,
  p_audience_scope text,
  p_duration_days integer,
  p_target_university text default null
) returns jsonb
language plpgsql
stable
security definer
set search_path=''
as $$
declare
  v_user uuid := auth.uid();
  v_scope text := upper(coalesce(p_audience_scope,''));
  v_university text;
  v_power integer;
  v_quote jsonb;
  v_cost bigint;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  if p_budget < 50 then raise exception 'BOOST_BUDGET_TOO_LOW'; end if;
  perform private.blink_boost_assert_owned_target(v_user,p_target_type,p_target_id);

  if v_scope='MY_UNIVERSITY' then
    select nullif(btrim(coalesce(p.university,'')),'') into v_university
    from public.profiles p where p.id=v_user;
    if v_university is null then raise exception 'UNIVERSITY_REQUIRED_FOR_BOOST'; end if;
  elsif v_scope='SELECTED_UNIVERSITY' then
    v_university := nullif(btrim(coalesce(p_target_university,'')),'');
    if v_university is null then raise exception 'BOOST_TARGET_UNIVERSITY_REQUIRED'; end if;
  elsif v_scope='ALL_CAMPUSES' then
    v_university := null;
  else
    raise exception 'INVALID_BOOST_AUDIENCE';
  end if;

  for v_power in reverse 100..1 loop
    v_quote := private.blink_boost_quote(
      p_target_type,v_power,p_objective,v_scope,p_duration_days
    );
    v_cost := coalesce((v_quote->>'coin_cost')::bigint,0);
    if v_cost<=p_budget then
      return v_quote || jsonb_build_object(
        'recommended_power',v_power,
        'requested_budget',p_budget,
        'target_university',v_university
      );
    end if;
  end loop;

  return jsonb_build_object(
    'recommended_power',0,
    'requested_budget',p_budget,
    'minimum_campaign_coins',private.blink_boost_growth_number('minimum_campaign_coins',50)
  );
end;
$$;
revoke all on function public.recommend_blink_boost_power(text,uuid,bigint,text,text,integer,text) from public, anon;
grant execute on function public.recommend_blink_boost_power(text,uuid,bigint,text,text,integer,text) to authenticated;

create or replace function public.quote_blink_drop(
  p_target_type text,
  p_target_id uuid,
  p_action text,
  p_reward_per_user integer,
  p_winner_count integer,
  p_audience_scope text,
  p_duration_hours integer default 24
) returns jsonb
language plpgsql
stable
security definer
set search_path=''
as $$
declare
  v_user uuid := auth.uid();
  v_target_type text := upper(btrim(coalesce(p_target_type,'')));
  v_action text := upper(btrim(coalesce(p_action,'')));
  v_scope text := upper(btrim(coalesce(p_audience_scope,'')));
  v_university text;
  v_budget bigint;
  v_balance bigint := 0;
  v_eligible integer := 0;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;

  if p_reward_per_user < 100
     or p_reward_per_user > 100000
     or p_reward_per_user % 100 <> 0 then
    raise exception 'DROP_REWARD_INVALID';
  end if;
  if p_winner_count < 1 or p_winner_count > 500 then raise exception 'DROP_WINNER_COUNT_INVALID'; end if;
  v_budget := p_reward_per_user::bigint * p_winner_count::bigint;
  if v_budget > 10000000 then raise exception 'DROP_BUDGET_TOO_LARGE'; end if;
  if p_duration_hours not in (1,6,12,24,72,168) then raise exception 'DROP_DURATION_INVALID'; end if;
  if not private.blink_drop_target_owned(v_user,v_target_type,p_target_id) then raise exception 'DROP_TARGET_NOT_OWNED'; end if;

  if v_target_type in ('POST','REEL') and v_action not in ('LIKE','COMMENT','REPOST') then
    raise exception 'DROP_ACTION_INVALID';
  elsif v_target_type='LISTING' and v_action<>'SAVE_LISTING' then
    raise exception 'DROP_ACTION_INVALID';
  end if;

  if v_scope='MY_CAMPUS' then
    select nullif(btrim(coalesce(p.university,'')),'') into v_university
    from public.profiles p where p.id=v_user;
    if v_university is null then raise exception 'UNIVERSITY_REQUIRED_FOR_DROP'; end if;
  elsif v_scope='ALL_CAMPUSES' then
    v_university := null;
  else
    raise exception 'DROP_AUDIENCE_INVALID';
  end if;

  select coalesce(floor(b.spendable_coin_balance),0)::bigint into v_balance
  from public.user_balances b where b.user_id=v_user;
  v_balance := coalesce(v_balance,0);

  select count(*)::integer into v_eligible
  from public.follows f
  join public.profiles recipient on recipient.id=f.follower_id
  where f.following_id=v_user
    and f.follower_id<>v_user
    and (
      v_scope='ALL_CAMPUSES'
      or lower(coalesce(recipient.university,''))=lower(coalesce(v_university,''))
    )
    and not exists (
      select 1 from public.blocks b
      where (b.blocker_id=v_user and b.blocked_id=f.follower_id)
         or (b.blocker_id=f.follower_id and b.blocked_id=v_user)
    );

  return jsonb_build_object(
    'total_budget',v_budget,
    'balance',v_balance,
    'remaining_balance_after_reserve',v_balance-v_budget,
    'eligible_followers',v_eligible,
    'requested_recipients',p_winner_count,
    'maximum_possible_recipients',least(v_eligible,p_winner_count),
    'audience_scope',v_scope,
    'target_university',coalesce(v_university,''),
    'ends_in_hours',p_duration_hours
  );
end;
$$;
revoke all on function public.quote_blink_drop(text,uuid,text,integer,integer,text,integer) from public, anon;
grant execute on function public.quote_blink_drop(text,uuid,text,integer,integer,text,integer) to authenticated;

create or replace function public.create_blink_boost_campaign_v2(
  p_target_type text,
  p_target_id uuid,
  p_boost_power integer,
  p_objective text,
  p_audience_scope text,
  p_duration_days integer,
  p_target_university text,
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

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text||':BOOST:'||p_request_id::text));

  select i.result into v_result
  from private.blink_growth_request_idempotency i
  where i.user_id=v_user and i.scope='BOOST' and i.request_id=p_request_id;
  if v_result is not null then return v_result; end if;

  v_result := public.create_blink_boost_campaign(
    p_target_type,p_target_id,p_boost_power,p_objective,
    p_audience_scope,p_duration_days,p_target_university
  );

  insert into private.blink_growth_request_idempotency(user_id,scope,request_id,result)
  values(v_user,'BOOST',p_request_id,v_result)
  on conflict(user_id,scope,request_id) do update set result=excluded.result;

  return v_result;
end;
$$;
revoke all on function public.create_blink_boost_campaign_v2(text,uuid,integer,text,text,integer,text,uuid) from public, anon;
grant execute on function public.create_blink_boost_campaign_v2(text,uuid,integer,text,text,integer,text,uuid) to authenticated;

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

  insert into private.blink_growth_request_idempotency(user_id,scope,request_id,result)
  values(v_user,'DROP',p_request_id,v_result)
  on conflict(user_id,scope,request_id) do update set result=excluded.result;

  return v_result;
end;
$$;
revoke all on function public.create_blink_drop_v2(text,uuid,text,integer,integer,text,integer,uuid) from public, anon;
grant execute on function public.create_blink_drop_v2(text,uuid,text,integer,integer,text,integer,uuid) to authenticated;

create or replace function private.notify_blink_boost_growth_change()
returns trigger
language plpgsql
security definer
set search_path=''
as $$
declare
  v_old_pct numeric := 0;
  v_new_pct numeric := 0;
  v_m integer;
  v_inserted boolean;
begin
  if tg_op='INSERT' then
    insert into public.notifications(
      user_id,actor_id,type,target_type,target_id,text,sub_text,is_read,metadata
    ) values (
      new.user_id,null,'system'::public.notification_type_enum,
      'boost',new.id,'Boost started',
      new.coin_budget::text||' Blink Coins reserved · '||new.duration_days::text||' day campaign',
      false,jsonb_build_object('campaign_id',new.id,'event','started')
    );
    return new;
  end if;

  if new.coin_budget>0 then
    v_old_pct := floor(100.0*old.coin_spent::numeric/new.coin_budget::numeric);
    v_new_pct := floor(100.0*new.coin_spent::numeric/new.coin_budget::numeric);
    foreach v_m in array array[25,50,75,100] loop
      if v_old_pct < v_m and v_new_pct >= v_m then
        insert into private.blink_growth_milestone_notices(campaign_id,milestone)
        values(new.id,v_m)
        on conflict do nothing;
        v_inserted := found;
        if v_inserted then
          insert into public.notifications(
            user_id,actor_id,type,target_type,target_id,text,sub_text,is_read,metadata
          ) values (
            new.user_id,null,'system'::public.notification_type_enum,
            'boost',new.id,'Boost delivery update',
            v_m::text||'% of the reserved delivery budget has been used.',
            false,jsonb_build_object('campaign_id',new.id,'event','milestone','milestone',v_m)
          );
        end if;
      end if;
    end loop;
  end if;

  if old.status is distinct from new.status and new.status in ('ENDED','CANCELLED') then
    insert into public.notifications(
      user_id,actor_id,type,target_type,target_id,text,sub_text,is_read,metadata
    ) values (
      new.user_id,null,'system'::public.notification_type_enum,
      'boost',new.id,
      case when new.status='CANCELLED' then 'Boost cancelled' else 'Boost completed' end,
      case
        when new.coin_refunded>0 then new.coin_refunded::text||' unused Blink Coins were returned.'
        else 'Campaign delivery is complete.'
      end,
      false,jsonb_build_object(
        'campaign_id',new.id,'event',lower(new.status),
        'spent',new.coin_spent,'refunded',new.coin_refunded
      )
    );
  end if;
  return new;
end;
$$;
revoke all on function private.notify_blink_boost_growth_change() from public, anon, authenticated;

drop trigger if exists blink_boost_growth_notifications on public.blink_boost_campaigns_v2;
create trigger blink_boost_growth_notifications
after insert or update of coin_spent,coin_refunded,status
on public.blink_boost_campaigns_v2
for each row execute function private.notify_blink_boost_growth_change();

create or replace function private.notify_blink_drop_growth_change()
returns trigger
language plpgsql
security definer
set search_path=''
as $$
begin
  if tg_op='INSERT' then
    insert into public.notifications(
      user_id,actor_id,type,target_type,target_id,text,sub_text,is_read,metadata
    ) values (
      new.creator_id,null,'system'::public.notification_type_enum,
      'giveaway',new.id,'BLINK Drop started',
      new.total_budget::text||' Blink Coins reserved for '||new.winner_count::text||' reward'||
        case when new.winner_count=1 then '' else 's' end||'.',
      false,jsonb_build_object('drop_id',new.id,'event','started')
    );
    return new;
  end if;

  if old.status is distinct from new.status and new.status in ('EXPIRED','CANCELLED','COMPLETED') then
    insert into public.notifications(
      user_id,actor_id,type,target_type,target_id,text,sub_text,is_read,metadata
    ) values (
      new.creator_id,null,'system'::public.notification_type_enum,
      'giveaway',new.id,
      case
        when new.status='CANCELLED' then 'BLINK Drop cancelled'
        when new.status='COMPLETED' then 'BLINK Drop completed'
        else 'BLINK Drop ended'
      end,
      case
        when new.coin_refunded>0 then new.coin_refunded::text||' unused Blink Coins were returned.'
        when new.status='COMPLETED' then 'All '||new.claimed_count::text||' rewards were claimed.'
        else new.claimed_count::text||' reward'||
          case when new.claimed_count=1 then '' else 's' end||' claimed.'
      end,
      false,jsonb_build_object(
        'drop_id',new.id,'event',lower(new.status),
        'distributed',new.coin_distributed,'refunded',new.coin_refunded
      )
    );
  end if;
  return new;
end;
$$;
revoke all on function private.notify_blink_drop_growth_change() from public, anon, authenticated;

drop trigger if exists blink_drop_growth_notifications on public.blink_drops;
create trigger blink_drop_growth_notifications
after insert or update of status,coin_refunded
on public.blink_drops
for each row execute function private.notify_blink_drop_growth_change();

create or replace function private.settle_all_blink_growth()
returns void
language plpgsql
security definer
set search_path=''
as $$
declare
  v_user uuid;
begin
  perform private.blink_drop_expire_stale();
  for v_user in
    select distinct c.user_id
    from public.blink_boost_campaigns_v2 c
    where c.status='ACTIVE' and c.ends_at<=now()
  loop
    perform private.settle_blink_boost_campaigns(v_user);
  end loop;

  delete from private.blink_growth_request_idempotency
  where created_at < now() - interval '30 days';
end;
$$;
revoke all on function private.settle_all_blink_growth() from public, anon, authenticated;

do $$
begin
  if not exists (
    select 1 from cron.job where jobname='blink-growth-settlement-v1'
  ) then
    perform cron.schedule(
      'blink-growth-settlement-v1',
      '*/5 * * * *',
      'select private.settle_all_blink_growth();'
    );
  end if;
end
$$;

do $$
begin
  if not exists (
    select 1
    from pg_publication_tables
    where pubname='supabase_realtime'
      and schemaname='public'
      and tablename='user_balances'
  ) then
    alter publication supabase_realtime add table public.user_balances;
  end if;
end
$$;

commit;
