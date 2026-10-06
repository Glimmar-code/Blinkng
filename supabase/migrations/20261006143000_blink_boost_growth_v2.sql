-- BLINK Boost Growth v2.
-- Additive paid promotion + Earn Rank Points discovery. New paid campaigns never feed
-- the organic ranking multiplier used by legacy Store boosts.
-- rollback-plan: drop the v2 RPCs/tables/config introduced here and restore award_points
-- from the previous migration if view_listing is no longer required.

begin;

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table if not exists private.blink_boost_growth_config (
    key text primary key,
    numeric_value numeric not null,
    updated_at timestamptz not null default now()
);
revoke all on private.blink_boost_growth_config from public, anon, authenticated;

insert into private.blink_boost_growth_config(key,numeric_value) values
('post_base_daily',900),
('reel_base_daily',1100),
('profile_base_daily',800),
('listing_base_daily',1000),
('objective_reach_bps',10000),
('objective_views_bps',10000),
('objective_likes_bps',11500),
('objective_saves_bps',12000),
('objective_profile_visits_bps',11500),
('objective_engagement_bps',12500),
('objective_comments_bps',13000),
('objective_followers_bps',13000),
('objective_buyer_interest_bps',14000),
('audience_my_university_bps',10000),
('audience_selected_university_bps',11000),
('audience_all_campuses_bps',16000),
('duration_1_bps',10000),
('duration_3_bps',9500),
('duration_7_bps',9000),
('duration_14_bps',8500),
('duration_30_bps',8000),
('minimum_campaign_coins',50),
('mission_daily_suggested_points',20),
('post_reach_low_per_day',600),
('post_reach_high_per_day',1200),
('reel_reach_low_per_day',700),
('reel_reach_high_per_day',1400),
('profile_reach_low_per_day',400),
('profile_reach_high_per_day',800),
('listing_reach_low_per_day',500),
('listing_reach_high_per_day',1000)
on conflict(key) do update
set numeric_value=excluded.numeric_value,
    updated_at=now();

create or replace function private.blink_boost_growth_number(
    p_key text,
    p_default numeric
) returns numeric
language sql
stable
security definer
set search_path=''
as $$
    select coalesce(
        (select c.numeric_value from private.blink_boost_growth_config c where c.key=p_key),
        p_default
    );
$$;
revoke all on function private.blink_boost_growth_number(text,numeric)
from public, anon, authenticated;

create table if not exists public.blink_boost_campaigns_v2 (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete cascade,
    target_type text not null check (target_type in ('POST','REEL','PROFILE','LISTING')),
    target_id uuid not null,
    objective text not null check (objective in ('REACH','VIEWS','LIKES','COMMENTS','SAVES','ENGAGEMENT','PROFILE_VISITS','FOLLOWERS','BUYER_INTEREST')),
    boost_power smallint not null check (boost_power between 1 and 100),
    audience_scope text not null check (audience_scope in ('MY_UNIVERSITY','SELECTED_UNIVERSITY','ALL_CAMPUSES')),
    target_university text,
    duration_days smallint not null check (duration_days in (1,3,7,14,30)),
    coin_budget bigint not null check (coin_budget >= 0),
    coin_refunded bigint not null default 0 check (coin_refunded >= 0),
    estimated_reach_low bigint not null default 0 check (estimated_reach_low >= 0),
    estimated_reach_high bigint not null default 0 check (estimated_reach_high >= estimated_reach_low),
    status text not null default 'ACTIVE' check (status in ('ACTIVE','ENDED','CANCELLED')),
    starts_at timestamptz not null default now(),
    ends_at timestamptz not null,
    cancelled_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (ends_at > starts_at),
    check (
        (audience_scope='ALL_CAMPUSES' and target_university is null)
        or (audience_scope<>'ALL_CAMPUSES' and nullif(btrim(coalesce(target_university,'')),'') is not null)
    )
);
create index if not exists blink_boost_campaigns_v2_owner_idx
    on public.blink_boost_campaigns_v2(user_id,created_at desc);
create index if not exists blink_boost_campaigns_v2_delivery_idx
    on public.blink_boost_campaigns_v2(status,ends_at,target_type,boost_power desc);
create index if not exists blink_boost_campaigns_v2_university_idx
    on public.blink_boost_campaigns_v2(target_university,status,ends_at);

alter table public.blink_boost_campaigns_v2 enable row level security;
drop policy if exists blink_boost_campaigns_v2_read_own on public.blink_boost_campaigns_v2;
create policy blink_boost_campaigns_v2_read_own
on public.blink_boost_campaigns_v2
for select
to authenticated
using ((select auth.uid())=user_id);
revoke all on public.blink_boost_campaigns_v2 from anon, authenticated;
grant select on public.blink_boost_campaigns_v2 to authenticated;

create table if not exists public.blink_boost_delivery_events_v2 (
    id uuid primary key default gen_random_uuid(),
    campaign_id uuid not null references public.blink_boost_campaigns_v2(id) on delete cascade,
    viewer_id uuid not null references public.profiles(id) on delete cascade,
    event_type text not null check (event_type in ('IMPRESSION','CARD_OPEN','PROFILE_OPEN','LISTING_OPEN')),
    surface text not null check (surface in ('HOME','SEARCH','DISCOVER','MISSIONS','MARKET')),
    created_at timestamptz not null default now()
);
create unique index if not exists blink_boost_delivery_once_idx
    on public.blink_boost_delivery_events_v2(campaign_id,viewer_id,event_type,surface);
create index if not exists blink_boost_delivery_campaign_idx
    on public.blink_boost_delivery_events_v2(campaign_id,created_at desc);
create index if not exists blink_boost_delivery_viewer_idx
    on public.blink_boost_delivery_events_v2(viewer_id,created_at desc);

alter table public.blink_boost_delivery_events_v2 enable row level security;
revoke all on public.blink_boost_delivery_events_v2 from anon, authenticated;

create or replace function private.blink_boost_assert_owned_target(
    p_user uuid,
    p_target_type text,
    p_target_id uuid
) returns void
language plpgsql
security definer
set search_path=''
as $$
begin
    if p_user is null then raise exception 'AUTH_REQUIRED'; end if;
    if p_target_id is null then raise exception 'BOOST_TARGET_REQUIRED'; end if;

    case upper(coalesce(p_target_type,''))
        when 'POST' then
            if not exists (
                select 1 from public.feed_posts p
                where p.id=p_target_id and p.user_id=p_user
                  and p.is_active=true and coalesce(p.is_reel,false)=false
            ) then raise exception 'BOOST_POST_NOT_AVAILABLE'; end if;
        when 'REEL' then
            if not exists (
                select 1 from public.feed_posts p
                where p.id=p_target_id and p.user_id=p_user
                  and p.is_active=true and coalesce(p.is_reel,false)=true
            ) then raise exception 'BOOST_REEL_NOT_AVAILABLE'; end if;
        when 'PROFILE' then
            if p_target_id<>p_user or not exists(select 1 from public.profiles p where p.id=p_user) then
                raise exception 'BOOST_PROFILE_NOT_AVAILABLE';
            end if;
        when 'LISTING' then
            if not exists (
                select 1 from public.market_items m
                where m.id=p_target_id and m.seller_id=p_user and coalesce(m.is_sold,false)=false
            ) then raise exception 'BOOST_LISTING_NOT_AVAILABLE'; end if;
        else
            raise exception 'INVALID_BOOST_TARGET_TYPE';
    end case;
end;
$$;
revoke all on function private.blink_boost_assert_owned_target(uuid,text,uuid)
from public, anon, authenticated;

create or replace function private.blink_boost_quote(
    p_target_type text,
    p_boost_power integer,
    p_objective text,
    p_audience_scope text,
    p_duration_days integer
) returns jsonb
language plpgsql
stable
security definer
set search_path=''
as $$
declare
    v_type text := upper(coalesce(p_target_type,''));
    v_objective text := upper(coalesce(p_objective,''));
    v_audience text := upper(coalesce(p_audience_scope,''));
    v_base numeric;
    v_objective_bps numeric;
    v_audience_bps numeric;
    v_duration_bps numeric;
    v_minimum numeric;
    v_cost bigint;
    v_reach_low bigint;
    v_reach_high bigint;
    v_low_base numeric;
    v_high_base numeric;
begin
    if p_boost_power not between 1 and 100 then raise exception 'INVALID_BOOST_POWER'; end if;
    if p_duration_days not in (1,3,7,14,30) then raise exception 'INVALID_BOOST_DURATION'; end if;

    v_base := case v_type
        when 'POST' then private.blink_boost_growth_number('post_base_daily',900)
        when 'REEL' then private.blink_boost_growth_number('reel_base_daily',1100)
        when 'PROFILE' then private.blink_boost_growth_number('profile_base_daily',800)
        when 'LISTING' then private.blink_boost_growth_number('listing_base_daily',1000)
        else null end;
    if v_base is null then raise exception 'INVALID_BOOST_TARGET_TYPE'; end if;

    v_objective_bps := case v_objective
        when 'REACH' then private.blink_boost_growth_number('objective_reach_bps',10000)
        when 'VIEWS' then private.blink_boost_growth_number('objective_views_bps',10000)
        when 'LIKES' then private.blink_boost_growth_number('objective_likes_bps',11500)
        when 'SAVES' then private.blink_boost_growth_number('objective_saves_bps',12000)
        when 'PROFILE_VISITS' then private.blink_boost_growth_number('objective_profile_visits_bps',11500)
        when 'ENGAGEMENT' then private.blink_boost_growth_number('objective_engagement_bps',12500)
        when 'COMMENTS' then private.blink_boost_growth_number('objective_comments_bps',13000)
        when 'FOLLOWERS' then private.blink_boost_growth_number('objective_followers_bps',13000)
        when 'BUYER_INTEREST' then private.blink_boost_growth_number('objective_buyer_interest_bps',14000)
        else null end;
    if v_objective_bps is null then raise exception 'INVALID_BOOST_OBJECTIVE'; end if;

    v_audience_bps := case v_audience
        when 'MY_UNIVERSITY' then private.blink_boost_growth_number('audience_my_university_bps',10000)
        when 'SELECTED_UNIVERSITY' then private.blink_boost_growth_number('audience_selected_university_bps',11000)
        when 'ALL_CAMPUSES' then private.blink_boost_growth_number('audience_all_campuses_bps',16000)
        else null end;
    if v_audience_bps is null then raise exception 'INVALID_BOOST_AUDIENCE'; end if;

    v_duration_bps := private.blink_boost_growth_number(
        'duration_'||p_duration_days::text||'_bps',
        10000
    );
    v_minimum := private.blink_boost_growth_number('minimum_campaign_coins',50);

    v_cost := greatest(
        v_minimum,
        ceil(
            v_base
            * p_boost_power::numeric / 100
            * v_objective_bps / 10000
            * v_audience_bps / 10000
            * p_duration_days
            * v_duration_bps / 10000
        )
    )::bigint;

    v_low_base := private.blink_boost_growth_number(lower(v_type)||'_reach_low_per_day',400);
    v_high_base := private.blink_boost_growth_number(lower(v_type)||'_reach_high_per_day',800);
    v_reach_low := greatest(1,ceil(v_low_base*p_boost_power::numeric/100*p_duration_days*v_audience_bps/10000))::bigint;
    v_reach_high := greatest(v_reach_low,ceil(v_high_base*p_boost_power::numeric/100*p_duration_days*v_audience_bps/10000))::bigint;

    return jsonb_build_object(
        'target_type',v_type,
        'boost_power',p_boost_power,
        'objective',v_objective,
        'audience_scope',v_audience,
        'duration_days',p_duration_days,
        'coin_cost',v_cost,
        'estimated_reach_low',v_reach_low,
        'estimated_reach_high',v_reach_high,
        'estimate_only',true
    );
end;
$$;
revoke all on function private.blink_boost_quote(text,integer,text,text,integer)
from public, anon, authenticated;

create or replace function public.quote_blink_boost_campaign(
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
    v_scope text := upper(coalesce(p_audience_scope,''));
    v_university text;
    v_quote jsonb;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
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

    v_quote := private.blink_boost_quote(
        p_target_type,p_boost_power,p_objective,v_scope,p_duration_days
    );
    return v_quote || jsonb_build_object(
        'target_id',p_target_id,
        'target_university',v_university
    );
end;
$$;
revoke all on function public.quote_blink_boost_campaign(text,uuid,integer,text,text,integer,text)
from public, anon;
grant execute on function public.quote_blink_boost_campaign(text,uuid,integer,text,text,integer,text)
to authenticated;

create or replace function public.create_blink_boost_campaign(
    p_target_type text,
    p_target_id uuid,
    p_boost_power integer,
    p_objective text,
    p_audience_scope text,
    p_duration_days integer,
    p_target_university text default null
) returns jsonb
language plpgsql
security definer
set search_path=''
as $$
declare
    v_user uuid := auth.uid();
    v_quote jsonb;
    v_cost bigint;
    v_balance numeric;
    v_campaign public.blink_boost_campaigns_v2%rowtype;
    v_university text;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;

    v_quote := public.quote_blink_boost_campaign(
        p_target_type,p_target_id,p_boost_power,p_objective,
        p_audience_scope,p_duration_days,p_target_university
    );
    v_cost := (v_quote->>'coin_cost')::bigint;
    v_university := nullif(v_quote->>'target_university','');

    insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
    values(v_user,0,now())
    on conflict(user_id) do nothing;

    select b.spendable_coin_balance into v_balance
    from public.user_balances b
    where b.user_id=v_user
    for update;

    if coalesce(v_balance,0)<v_cost then raise exception 'INSUFFICIENT_BLINK_COINS'; end if;

    update public.user_balances
       set spendable_coin_balance=spendable_coin_balance-v_cost,
           updated_at=now()
     where user_id=v_user
    returning spendable_coin_balance into v_balance;

    insert into public.blink_boost_campaigns_v2(
        user_id,target_type,target_id,objective,boost_power,
        audience_scope,target_university,duration_days,coin_budget,
        estimated_reach_low,estimated_reach_high,starts_at,ends_at
    ) values (
        v_user,
        upper(p_target_type),
        p_target_id,
        upper(p_objective),
        p_boost_power,
        upper(p_audience_scope),
        v_university,
        p_duration_days,
        v_cost,
        (v_quote->>'estimated_reach_low')::bigint,
        (v_quote->>'estimated_reach_high')::bigint,
        now(),
        now()+make_interval(days=>p_duration_days)
    )
    returning * into v_campaign;

    insert into public.blink_coin_transactions(
        user_id,kind,catalog_id,item_name,amount,balance_after,metadata
    ) values (
        v_user,
        'BOOST_CAMPAIGN_PURCHASE',
        null,
        'Boost '||initcap(lower(v_campaign.target_type)),
        -v_cost,
        floor(v_balance)::bigint,
        jsonb_build_object(
            'campaign_id',v_campaign.id,
            'target_type',v_campaign.target_type,
            'target_id',v_campaign.target_id,
            'boost_power',v_campaign.boost_power,
            'objective',v_campaign.objective,
            'audience_scope',v_campaign.audience_scope,
            'duration_days',v_campaign.duration_days
        )
    );

    return jsonb_build_object(
        'success',true,
        'campaign_id',v_campaign.id,
        'charged',v_cost,
        'balance',floor(v_balance)::bigint,
        'campaign',to_jsonb(v_campaign),
        'quote',v_quote
    );
end;
$$;
revoke all on function public.create_blink_boost_campaign(text,uuid,integer,text,text,integer,text)
from public, anon;
grant execute on function public.create_blink_boost_campaign(text,uuid,integer,text,text,integer,text)
to authenticated;

create or replace function public.cancel_blink_boost_campaign(
    p_campaign_id uuid
) returns jsonb
language plpgsql
security definer
set search_path=''
as $$
declare
    v_user uuid := auth.uid();
    v_campaign public.blink_boost_campaigns_v2%rowtype;
    v_total_seconds numeric;
    v_remaining_seconds numeric;
    v_refund bigint := 0;
    v_balance numeric;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;

    select * into v_campaign
    from public.blink_boost_campaigns_v2 c
    where c.id=p_campaign_id and c.user_id=v_user
    for update;
    if not found then raise exception 'BOOST_CAMPAIGN_NOT_FOUND'; end if;
    if v_campaign.status<>'ACTIVE' then raise exception 'BOOST_CAMPAIGN_NOT_ACTIVE'; end if;

    v_total_seconds := greatest(1,extract(epoch from (v_campaign.ends_at-v_campaign.starts_at)));
    v_remaining_seconds := greatest(0,extract(epoch from (v_campaign.ends_at-now())));
    v_refund := least(
        v_campaign.coin_budget,
        floor(v_campaign.coin_budget::numeric*v_remaining_seconds/v_total_seconds)::bigint
    );

    update public.blink_boost_campaigns_v2
       set status='CANCELLED',
           cancelled_at=now(),
           coin_refunded=v_refund,
           updated_at=now()
     where id=v_campaign.id;

    insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
    values(v_user,0,now())
    on conflict(user_id) do nothing;

    if v_refund>0 then
        update public.user_balances
           set spendable_coin_balance=spendable_coin_balance+v_refund,
               updated_at=now()
         where user_id=v_user
        returning spendable_coin_balance into v_balance;

        insert into public.blink_coin_transactions(
            user_id,kind,catalog_id,item_name,amount,balance_after,metadata
        ) values (
            v_user,'BOOST_CAMPAIGN_REFUND',null,'Boost campaign refund',
            v_refund,floor(v_balance)::bigint,
            jsonb_build_object('campaign_id',v_campaign.id,'refund_method','time_prorated')
        );
    else
        select spendable_coin_balance into v_balance
        from public.user_balances where user_id=v_user;
    end if;

    return jsonb_build_object(
        'success',true,
        'campaign_id',v_campaign.id,
        'refunded',v_refund,
        'balance',coalesce(floor(v_balance),0)::bigint
    );
end;
$$;
revoke all on function public.cancel_blink_boost_campaign(uuid) from public, anon;
grant execute on function public.cancel_blink_boost_campaign(uuid) to authenticated;

-- Close a concurrency hole in the original ranking ledger. A reference-backed action
-- can have only one transaction for one user, even if two devices act simultaneously.
create unique index if not exists point_transactions_once_per_reference_idx
    on public.point_transactions(user_id,action_type,reference_id)
    where reference_id is not null;

create or replace function public.award_points(
    p_user_id uuid,
    p_action_type text,
    p_reference_id uuid default null
) returns integer
language plpgsql
security definer
set search_path=''
as $$
declare
    v_delta integer := case p_action_type
        when 'view_post' then 1
        when 'view_listing' then 1
        when 'like_post' then 1
        when 'comment' then 2
        when 'save_post' then 2
        when 'share_post' then 2
        when 'like_comment' then 2
        when 'reply_comment' then 2
        when 'like_status' then 3
        when 'create_status' then 5
        when 'message_user' then 5
        when 'reply_status' then 5
        when 'create_post' then 15
        when 'follow_user' then 3
        else 0
    end;
    v_new_points bigint;
    v_tx_id uuid;
begin
    if v_delta=0 then raise exception 'Unknown action_type: %',p_action_type; end if;

    if p_reference_id is not null then
        insert into public.point_transactions(user_id,action_type,points_delta,reference_id)
        values(p_user_id,p_action_type,v_delta,p_reference_id)
        on conflict (user_id,action_type,reference_id)
        where reference_id is not null
        do nothing
        returning id into v_tx_id;

        if v_tx_id is null then
            select coalesce(p.points,0) into v_new_points
            from public.profiles p where p.id=p_user_id;
            return coalesce(v_new_points,0)::integer;
        end if;
    else
        insert into public.point_transactions(user_id,action_type,points_delta,reference_id)
        values(p_user_id,p_action_type,v_delta,null)
        returning id into v_tx_id;
    end if;

    update public.profiles
       set points=coalesce(points,0)+v_delta,
           updated_at=now()
     where id=p_user_id
    returning points into v_new_points;

    if v_new_points is null then raise exception 'USER_NOT_FOUND'; end if;
    return v_new_points::integer;
end;
$$;

create or replace function private.blink_boost_viewer_matches(
    p_campaign public.blink_boost_campaigns_v2,
    p_viewer uuid
) returns boolean
language plpgsql
stable
security definer
set search_path=''
as $$
declare
    v_viewer_university text;
begin
    if p_viewer is null or p_campaign.user_id=p_viewer then return false; end if;
    if p_campaign.status<>'ACTIVE' or p_campaign.starts_at>now() or p_campaign.ends_at<=now() then return false; end if;
    if p_campaign.audience_scope='ALL_CAMPUSES' then return true; end if;

    select nullif(btrim(coalesce(p.university,'')),'')
      into v_viewer_university
      from public.profiles p where p.id=p_viewer;

    return v_viewer_university is not null
       and lower(v_viewer_university)=lower(coalesce(p_campaign.target_university,''));
end;
$$;
revoke all on function private.blink_boost_viewer_matches(public.blink_boost_campaigns_v2,uuid)
from public, anon, authenticated;

create or replace function public.record_blink_boost_delivery(
    p_campaign_id uuid,
    p_event_type text,
    p_surface text
) returns jsonb
language plpgsql
security definer
set search_path=''
as $$
declare
    v_user uuid := auth.uid();
    v_event text := upper(coalesce(p_event_type,''));
    v_surface text := upper(coalesce(p_surface,''));
    v_campaign public.blink_boost_campaigns_v2%rowtype;
    v_inserted boolean := false;
    v_points_before integer := 0;
    v_points_after integer := 0;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
    if v_event not in ('IMPRESSION','CARD_OPEN','PROFILE_OPEN','LISTING_OPEN') then
        raise exception 'INVALID_BOOST_EVENT';
    end if;
    if v_surface not in ('HOME','SEARCH','DISCOVER','MISSIONS','MARKET') then
        raise exception 'INVALID_BOOST_SURFACE';
    end if;

    select * into v_campaign
    from public.blink_boost_campaigns_v2 c
    where c.id=p_campaign_id;
    if not found or not private.blink_boost_viewer_matches(v_campaign,v_user) then
        raise exception 'BOOST_CAMPAIGN_NOT_AVAILABLE';
    end if;

    insert into public.blink_boost_delivery_events_v2(campaign_id,viewer_id,event_type,surface)
    values(v_campaign.id,v_user,v_event,v_surface)
    on conflict(campaign_id,viewer_id,event_type,surface) do nothing;
    v_inserted := found;

    select coalesce(p.points,0) into v_points_before
    from public.profiles p where p.id=v_user;

    if v_inserted and v_event='LISTING_OPEN' and v_campaign.target_type='LISTING' then
        perform public.award_points(v_user,'view_listing',v_campaign.target_id);
    end if;

    select coalesce(p.points,0) into v_points_after
    from public.profiles p where p.id=v_user;

    return jsonb_build_object(
        'success',true,
        'recorded',v_inserted,
        'campaign_id',v_campaign.id,
        'event_type',v_event,
        'points_awarded',greatest(0,v_points_after-v_points_before)
    );
end;
$$;
revoke all on function public.record_blink_boost_delivery(uuid,text,text)
from public, anon;
grant execute on function public.record_blink_boost_delivery(uuid,text,text)
to authenticated;

create or replace function public.get_blink_promoted_slots(
    p_surface text default 'HOME',
    p_limit integer default 3
) returns jsonb
language plpgsql
stable
security definer
set search_path=''
as $$
declare
    v_user uuid := auth.uid();
    v_surface text := upper(coalesce(p_surface,'HOME'));
    v_limit integer := greatest(1,least(coalesce(p_limit,3),10));
    v_items jsonb := '[]'::jsonb;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
    if v_surface not in ('HOME','SEARCH','DISCOVER','MISSIONS') then raise exception 'INVALID_BOOST_SURFACE'; end if;

    select coalesce(jsonb_agg(x.payload order by x.delivery_score desc,x.starts_at asc),'[]'::jsonb)
      into v_items
    from (
        select
            c.starts_at,
            (c.boost_power::numeric + least(25,extract(epoch from (now()-c.starts_at))/21600)) as delivery_score,
            jsonb_build_object(
                'campaign_id',c.id,
                'target_type',c.target_type,
                'target_id',c.target_id,
                'objective',c.objective,
                'boost_power',c.boost_power,
                'ad_label','Promoted',
                'surface',v_surface,
                'owner',jsonb_build_object(
                    'id',owner.id,
                    'username',owner.username,
                    'full_name',owner.full_name,
                    'avatar_url',owner.avatar_url,
                    'verification_badge',owner.verification_badge,
                    'university',owner.university
                ),
                'post',case when c.target_type in ('POST','REEL') then (
                    select to_jsonb(fp) from public.feed_posts fp where fp.id=c.target_id and fp.is_active=true
                ) else null end,
                'profile',case when c.target_type='PROFILE' then (
                    select jsonb_build_object(
                        'id',p.id,'username',p.username,'full_name',p.full_name,
                        'avatar_url',p.avatar_url,'verification_badge',p.verification_badge,
                        'university',p.university,'faculty',p.faculty,'department',p.department,
                        'follower_count',p.follower_count,'bio',p.bio
                    ) from public.profiles p where p.id=c.target_id
                ) else null end,
                'listing',case when c.target_type='LISTING' then (
                    select to_jsonb(m) from public.market_items m
                    where m.id=c.target_id and coalesce(m.is_sold,false)=false
                ) else null end
            ) as payload
        from public.blink_boost_campaigns_v2 c
        join public.profiles owner on owner.id=c.user_id
        where private.blink_boost_viewer_matches(c,v_user)
          and (
            (c.target_type in ('POST','REEL') and exists(
                select 1 from public.feed_posts fp where fp.id=c.target_id and fp.is_active=true
            ))
            or (c.target_type='PROFILE' and exists(
                select 1 from public.profiles p where p.id=c.target_id
            ))
            or (c.target_type='LISTING' and exists(
                select 1 from public.market_items m where m.id=c.target_id and coalesce(m.is_sold,false)=false
            ))
          )
        order by delivery_score desc,c.starts_at asc
        limit v_limit
    ) x;

    return jsonb_build_object('surface',v_surface,'items',v_items);
end;
$$;
revoke all on function public.get_blink_promoted_slots(text,integer)
from public, anon;
grant execute on function public.get_blink_promoted_slots(text,integer)
to authenticated;


create or replace function private.blink_boost_mission_points_today(
    p_user uuid
) returns integer
language sql
stable
security definer
set search_path=''
as $
    select coalesce(sum(t.points_delta),0)::integer
    from public.point_transactions t
    where t.user_id=p_user
      and t.created_at>=date_trunc('day',now())
      and exists (
          select 1
          from public.blink_boost_delivery_events_v2 e
          join public.blink_boost_campaigns_v2 c on c.id=e.campaign_id
          where e.viewer_id=p_user
            and e.surface='MISSIONS'
            and e.created_at>=date_trunc('day',now())
            and (
                (t.action_type in ('view_post','like_post','comment','save_post') and c.target_id=t.reference_id)
                or (t.action_type='follow_user' and c.user_id=t.reference_id)
                or (t.action_type='view_listing' and c.target_id=t.reference_id)
            )
      );
$;
revoke all on function private.blink_boost_mission_points_today(uuid)
from public, anon, authenticated;

create or replace function public.complete_blink_boost_mission_action(
    p_campaign_id uuid,
    p_action text,
    p_comment_text text default null
) returns jsonb
language plpgsql
security definer
set search_path=''
as $
declare
    v_user uuid:=auth.uid();
    v_campaign public.blink_boost_campaigns_v2%rowtype;
    v_action text:=lower(btrim(coalesce(p_action,'')));
    v_cap integer:=private.blink_boost_growth_number('mission_daily_suggested_points',20)::integer;
    v_earned integer:=0;
    v_delta integer:=0;
    v_reference uuid;
    v_action_type text;
    v_before integer:=0;
    v_after integer:=0;
    v_inserted boolean:=false;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;

    select * into v_campaign
    from public.blink_boost_campaigns_v2 c
    where c.id=p_campaign_id
      and private.blink_boost_viewer_matches(c,v_user);
    if not found then raise exception 'BOOST_CAMPAIGN_NOT_AVAILABLE'; end if;

    if not exists (
        select 1 from public.blink_boost_delivery_events_v2 e
        where e.campaign_id=v_campaign.id
          and e.viewer_id=v_user
          and e.surface='MISSIONS'
    ) then
        insert into public.blink_boost_delivery_events_v2(campaign_id,viewer_id,event_type,surface)
        values(v_campaign.id,v_user,'IMPRESSION','MISSIONS')
        on conflict(campaign_id,viewer_id,event_type,surface) do nothing;
    end if;

    case v_action
        when 'view' then
            if v_campaign.target_type not in ('POST','REEL') then raise exception 'MISSION_ACTION_NOT_AVAILABLE'; end if;
            v_delta:=1; v_reference:=v_campaign.target_id; v_action_type:='view_post';
        when 'like' then
            if v_campaign.target_type not in ('POST','REEL') then raise exception 'MISSION_ACTION_NOT_AVAILABLE'; end if;
            v_delta:=1; v_reference:=v_campaign.target_id; v_action_type:='like_post';
        when 'comment' then
            if v_campaign.target_type not in ('POST','REEL') then raise exception 'MISSION_ACTION_NOT_AVAILABLE'; end if;
            if nullif(btrim(coalesce(p_comment_text,'')),'') is null then raise exception 'COMMENT_REQUIRED'; end if;
            if char_length(btrim(p_comment_text))>2000 then raise exception 'COMMENT_TOO_LONG'; end if;
            v_delta:=2; v_reference:=v_campaign.target_id; v_action_type:='comment';
        when 'save' then
            if v_campaign.target_type not in ('POST','REEL') then raise exception 'MISSION_ACTION_NOT_AVAILABLE'; end if;
            v_delta:=2; v_reference:=v_campaign.target_id; v_action_type:='save_post';
        when 'follow' then
            v_delta:=3; v_reference:=v_campaign.user_id; v_action_type:='follow_user';
        when 'listing_open' then
            if v_campaign.target_type<>'LISTING' then raise exception 'MISSION_ACTION_NOT_AVAILABLE'; end if;
            v_delta:=1; v_reference:=v_campaign.target_id; v_action_type:='view_listing';
        else
            raise exception 'INVALID_MISSION_ACTION';
    end case;

    if exists(
        select 1 from public.point_transactions t
        where t.user_id=v_user
          and t.action_type=v_action_type
          and t.reference_id=v_reference
    ) then
        raise exception 'BOOST_MISSION_ALREADY_COMPLETED';
    end if;

    v_earned:=private.blink_boost_mission_points_today(v_user);
    if v_earned+v_delta>v_cap then raise exception 'BOOST_MISSION_DAILY_CAP_REACHED'; end if;

    select coalesce(p.points,0) into v_before from public.profiles p where p.id=v_user;

    case v_action
        when 'view' then
            perform public.record_content_view(v_campaign.target_id,gen_random_uuid());

        when 'like' then
            if exists(
                select 1 from public.post_likes l
                where l.post_id=v_campaign.target_id and l.user_id=v_user
            ) then raise exception 'CONTENT_ALREADY_LIKED'; end if;
            insert into public.post_likes(post_id,user_id)
            values(v_campaign.target_id,v_user);

        when 'comment' then
            insert into public.comments(post_id,author_id,content)
            values(v_campaign.target_id,v_user,btrim(p_comment_text));

        when 'save' then
            if exists(
                select 1 from public.post_bookmarks b
                where b.post_id=v_campaign.target_id and b.user_id=v_user
            ) then raise exception 'CONTENT_ALREADY_SAVED'; end if;
            insert into public.post_bookmarks(post_id,user_id)
            values(v_campaign.target_id,v_user);

        when 'follow' then
            if exists(
                select 1 from public.follows f
                where f.follower_id=v_user and f.following_id=v_campaign.user_id
            ) then raise exception 'CREATOR_ALREADY_FOLLOWED'; end if;
            perform public.follow_user(v_campaign.user_id);

        when 'listing_open' then
            perform public.record_blink_boost_delivery(v_campaign.id,'LISTING_OPEN','MISSIONS');
    end case;

    select coalesce(p.points,0) into v_after from public.profiles p where p.id=v_user;
    if v_after<=v_before then
        raise exception 'MISSION_ACTION_DID_NOT_EARN_POINTS';
    end if;

    return jsonb_build_object(
        'success',true,
        'campaign_id',v_campaign.id,
        'action',v_action,
        'points_awarded',v_after-v_before,
        'points_total',v_after,
        'mission_points_today',private.blink_boost_mission_points_today(v_user),
        'mission_points_cap',v_cap
    );
end;
$;
revoke all on function public.complete_blink_boost_mission_action(uuid,text,text)
from public, anon;
grant execute on function public.complete_blink_boost_mission_action(uuid,text,text)
to authenticated;

create or replace function public.get_blink_boost_missions(
    p_limit integer default 12
) returns jsonb
language plpgsql
stable
security definer
set search_path=''
as $$
declare
    v_user uuid := auth.uid();
    v_limit integer := greatest(1,least(coalesce(p_limit,12),30));
    v_cap integer := private.blink_boost_growth_number('mission_daily_suggested_points',20)::integer;
    v_earned_today integer := 0;
    v_remaining integer := 0;
    v_offered integer := 0;
    v_items jsonb := '[]'::jsonb;
    v_actions jsonb;
    v_campaign public.blink_boost_campaigns_v2%rowtype;
    v_owner public.profiles%rowtype;
    v_action_points integer;
    v_payload jsonb;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;

    select coalesce(sum(t.points_delta),0)::integer
      into v_earned_today
      from public.point_transactions t
     where t.user_id=v_user
       and t.created_at>=date_trunc('day',now())
       and exists (
           select 1
             from public.blink_boost_delivery_events_v2 e
             join public.blink_boost_campaigns_v2 c on c.id=e.campaign_id
            where e.viewer_id=v_user
              and e.surface='MISSIONS'
              and e.created_at>=date_trunc('day',now())
              and (
                  (t.action_type in ('view_post','like_post','comment','save_post') and c.target_id=t.reference_id)
                  or (t.action_type='follow_user' and c.user_id=t.reference_id)
                  or (t.action_type='view_listing' and c.target_id=t.reference_id)
              )
       );

    v_remaining:=greatest(0,v_cap-v_earned_today);

    for v_campaign in
        select c.*
        from public.blink_boost_campaigns_v2 c
        where private.blink_boost_viewer_matches(c,v_user)
        order by c.boost_power desc,c.starts_at asc
        limit v_limit
    loop
        exit when v_offered>=v_remaining;
        select * into v_owner from public.profiles p where p.id=v_campaign.user_id;
        v_actions := '[]'::jsonb;

        if v_campaign.target_type in ('POST','REEL') then
            if v_offered+1<=v_remaining and not exists(
                select 1 from public.point_transactions t
                where t.user_id=v_user and t.action_type='view_post' and t.reference_id=v_campaign.target_id
            ) then
                v_actions:=v_actions||jsonb_build_array(jsonb_build_object('key','view','label','Qualified view','points',1));
                v_offered:=v_offered+1;
            end if;
            if v_offered+1<=v_remaining and not exists(
                select 1 from public.point_transactions t
                where t.user_id=v_user and t.action_type='like_post' and t.reference_id=v_campaign.target_id
            ) then
                v_actions:=v_actions||jsonb_build_array(jsonb_build_object('key','like','label','Like','points',1));
                v_offered:=v_offered+1;
            end if;
            if v_offered+2<=v_remaining and not exists(
                select 1 from public.point_transactions t
                where t.user_id=v_user and t.action_type='comment' and t.reference_id=v_campaign.target_id
            ) then
                v_actions:=v_actions||jsonb_build_array(jsonb_build_object('key','comment','label','Comment','points',2));
                v_offered:=v_offered+2;
            end if;
            if v_offered+2<=v_remaining and not exists(
                select 1 from public.point_transactions t
                where t.user_id=v_user and t.action_type='save_post' and t.reference_id=v_campaign.target_id
            ) then
                v_actions:=v_actions||jsonb_build_array(jsonb_build_object('key','save','label','Save','points',2));
                v_offered:=v_offered+2;
            end if;
        elsif v_campaign.target_type='LISTING' then
            if v_offered+1<=v_remaining and not exists(
                select 1 from public.point_transactions t
                where t.user_id=v_user and t.action_type='view_listing' and t.reference_id=v_campaign.target_id
            ) then
                v_actions:=v_actions||jsonb_build_array(jsonb_build_object('key','listing_open','label','Explore listing','points',1));
                v_offered:=v_offered+1;
            end if;
        end if;

        if v_offered+3<=v_remaining and not exists(
            select 1 from public.point_transactions t
            where t.user_id=v_user and t.action_type='follow_user' and t.reference_id=v_campaign.user_id
        ) then
            v_actions:=v_actions||jsonb_build_array(jsonb_build_object('key','follow','label','Follow creator','points',3));
            v_offered:=v_offered+3;
        end if;

        if jsonb_array_length(v_actions)>0 then
            v_payload := jsonb_build_object(
                'campaign_id',v_campaign.id,
                'target_type',v_campaign.target_type,
                'target_id',v_campaign.target_id,
                'objective',v_campaign.objective,
                'boost_power',v_campaign.boost_power,
                'actions',v_actions,
                'owner',jsonb_build_object(
                    'id',v_owner.id,'username',v_owner.username,'full_name',v_owner.full_name,
                    'avatar_url',v_owner.avatar_url,'verification_badge',v_owner.verification_badge,
                    'university',v_owner.university
                ),
                'post',case when v_campaign.target_type in ('POST','REEL') then (
                    select to_jsonb(fp) from public.feed_posts fp
                    where fp.id=v_campaign.target_id and fp.is_active=true
                ) else null end,
                'profile',case when v_campaign.target_type='PROFILE' then (
                    select jsonb_build_object(
                        'id',p.id,'username',p.username,'full_name',p.full_name,
                        'avatar_url',p.avatar_url,'verification_badge',p.verification_badge,
                        'university',p.university,'faculty',p.faculty,'department',p.department,
                        'follower_count',p.follower_count,'bio',p.bio
                    ) from public.profiles p where p.id=v_campaign.target_id
                ) else null end,
                'listing',case when v_campaign.target_type='LISTING' then (
                    select to_jsonb(m) from public.market_items m
                    where m.id=v_campaign.target_id and coalesce(m.is_sold,false)=false
                ) else null end
            );
            v_items:=v_items||jsonb_build_array(v_payload);
        end if;
    end loop;

    return jsonb_build_object(
        'suggested_points_cap',v_cap,
        'earned_from_missions_today',v_earned_today,
        'remaining_mission_points_today',v_remaining,
        'offered_points',v_offered,
        'items',v_items,
        'note','Rewards use the existing idempotent Rank Points ledger; there is no second boost bonus.'
    );
end;
$$;
revoke all on function public.get_blink_boost_missions(integer)
from public, anon;
grant execute on function public.get_blink_boost_missions(integer)
to authenticated;

create or replace function public.get_my_blink_boost_campaigns()
returns jsonb
language sql
stable
security definer
set search_path=''
as $$
    with me as (select auth.uid() uid),
    rows as (
        select
            c.*,
            (select count(*)::bigint from public.blink_boost_delivery_events_v2 e
             where e.campaign_id=c.id and e.event_type='IMPRESSION') promoted_impressions,
            (select count(*)::bigint from public.blink_boost_delivery_events_v2 e
             where e.campaign_id=c.id and e.event_type in ('CARD_OPEN','PROFILE_OPEN','LISTING_OPEN')) promoted_opens
        from public.blink_boost_campaigns_v2 c,me
        where c.user_id=me.uid
        order by c.created_at desc
        limit 100
    )
    select jsonb_build_object(
        'items',coalesce(jsonb_agg(to_jsonb(rows) order by rows.created_at desc),'[]'::jsonb)
    )
    from rows
    where (select uid from me) is not null;
$$;
revoke all on function public.get_my_blink_boost_campaigns()
from public, anon;
grant execute on function public.get_my_blink_boost_campaigns()
to authenticated;

create or replace function public.get_blink_boost_growth_state()
returns jsonb
language plpgsql
stable
security definer
set search_path=''
as $$
declare
    v_user uuid := auth.uid();
    v_balance bigint := 0;
    v_campaigns jsonb;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
    select coalesce(floor(b.spendable_coin_balance),0)::bigint into v_balance
    from public.user_balances b where b.user_id=v_user;

    v_campaigns:=public.get_my_blink_boost_campaigns();

    return jsonb_build_object(
        'balance',coalesce(v_balance,0),
        'pricing',jsonb_build_object(
            'post_base_daily',private.blink_boost_growth_number('post_base_daily',900),
            'reel_base_daily',private.blink_boost_growth_number('reel_base_daily',1100),
            'profile_base_daily',private.blink_boost_growth_number('profile_base_daily',800),
            'listing_base_daily',private.blink_boost_growth_number('listing_base_daily',1000),
            'minimum_campaign_coins',private.blink_boost_growth_number('minimum_campaign_coins',50)
        ),
        'mission_daily_suggested_points',private.blink_boost_growth_number('mission_daily_suggested_points',20),
        'campaigns',coalesce(v_campaigns->'items','[]'::jsonb),
        'organic_ranking_separate',true
    );
end;
$$;
revoke all on function public.get_blink_boost_growth_state()
from public, anon;
grant execute on function public.get_blink_boost_growth_state()
to authenticated;

commit;
