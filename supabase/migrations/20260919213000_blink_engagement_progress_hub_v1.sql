-- BLINK Engagement System v1: Progress Hub, weekly missions, achievements and progression rewards.
-- destructive-change-reviewed: additive tables/functions only; no existing user data or ranking rules are replaced.
-- rollback-plan: drop the RPCs/tables/config added by this migration. Existing XP, daily missions, coins and leaderboards remain intact.

begin;

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

-- Weekly mission definitions stay server-owned so targets/rewards can change without an APK release.
create table if not exists private.blink_weekly_mission_config (
    mission_key text primary key,
    title text not null,
    description text not null,
    event_types text[] not null,
    target_count integer not null check (target_count > 0),
    coin_reward integer not null check (coin_reward >= 0),
    xp_reward integer not null check (xp_reward >= 0),
    sort_order integer not null default 0,
    is_active boolean not null default true,
    updated_at timestamptz not null default now()
);
revoke all on private.blink_weekly_mission_config from public, anon, authenticated;

insert into private.blink_weekly_mission_config(
    mission_key,title,description,event_types,target_count,coin_reward,xp_reward,sort_order,is_active
) values
('creator_consistency','Creator Consistency','Create 3 posts or reels this week.',array['create_post']::text[],3,15,100,10,true),
('conversation_builder','Conversation Builder','Write 10 meaningful comments or replies this week.',array['comment','reply_comment']::text[],10,15,100,20,true),
('discovery_week','Discover BLINK','Explore 25 different posts or reels this week.',array['qualified_view']::text[],25,15,100,30,true),
('community_supporter','Support the Community','Complete 20 likes, saves, shares or comment likes this week.',array['like_post','save_post','share_post','like_comment']::text[],20,15,100,40,true),
('network_week','Grow Your Network','Follow 5 different people this week.',array['follow_user']::text[],5,15,100,50,true)
on conflict (mission_key) do update
set title=excluded.title,
    description=excluded.description,
    event_types=excluded.event_types,
    target_count=excluded.target_count,
    coin_reward=excluded.coin_reward,
    xp_reward=excluded.xp_reward,
    sort_order=excluded.sort_order,
    is_active=excluded.is_active,
    updated_at=now();

create table if not exists public.blink_weekly_mission_claims (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete cascade,
    week_start date not null,
    mission_key text not null,
    coin_reward integer not null check (coin_reward >= 0),
    xp_reward integer not null check (xp_reward >= 0),
    claimed_at timestamptz not null default now(),
    unique(user_id, week_start, mission_key)
);
create index if not exists blink_weekly_mission_claims_user_week_idx
    on public.blink_weekly_mission_claims(user_id, week_start desc, claimed_at desc);
alter table public.blink_weekly_mission_claims enable row level security;
drop policy if exists blink_weekly_mission_claims_read_own on public.blink_weekly_mission_claims;
create policy blink_weekly_mission_claims_read_own
on public.blink_weekly_mission_claims
for select
to authenticated
using ((select auth.uid()) = user_id);
revoke all on public.blink_weekly_mission_claims from anon, authenticated;
grant select on public.blink_weekly_mission_claims to authenticated;

create table if not exists public.blink_weekly_chest_claims (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete cascade,
    week_start date not null,
    coin_reward integer not null check (coin_reward >= 0),
    xp_reward integer not null check (xp_reward >= 0),
    claimed_at timestamptz not null default now(),
    unique(user_id, week_start)
);
create index if not exists blink_weekly_chest_claims_user_week_idx
    on public.blink_weekly_chest_claims(user_id, week_start desc);
alter table public.blink_weekly_chest_claims enable row level security;
drop policy if exists blink_weekly_chest_claims_read_own on public.blink_weekly_chest_claims;
create policy blink_weekly_chest_claims_read_own
on public.blink_weekly_chest_claims
for select
to authenticated
using ((select auth.uid()) = user_id);
revoke all on public.blink_weekly_chest_claims from anon, authenticated;
grant select on public.blink_weekly_chest_claims to authenticated;

-- One achievement engine for social, creator and progression milestones.
create table if not exists private.blink_achievement_config (
    achievement_key text primary key,
    title text not null,
    description text not null,
    category text not null,
    rarity text not null check (rarity in ('COMMON','RARE','EPIC','LEGENDARY')),
    metric text not null,
    target_count bigint not null check (target_count > 0),
    coin_reward integer not null check (coin_reward >= 0),
    xp_reward integer not null check (xp_reward >= 0),
    sort_order integer not null default 0,
    is_active boolean not null default true,
    updated_at timestamptz not null default now()
);
revoke all on private.blink_achievement_config from public, anon, authenticated;

insert into private.blink_achievement_config(
    achievement_key,title,description,category,rarity,metric,target_count,coin_reward,xp_reward,sort_order,is_active
) values
('first_post','First Post','Create your first BLINK post or reel.','Creator','COMMON','posts_created',1,5,50,10,true),
('creator_10','Creator 10','Create 10 posts or reels.','Creator','RARE','posts_created',10,20,150,20,true),
('reel_creator_10','Reel Creator','Publish 10 reels.','Creator','RARE','reels_created',10,25,175,30,true),
('first_100_followers','First 100 Followers','Reach 100 followers.','Social','RARE','followers',100,30,200,40,true),
('level_10','Level 10','Reach BLINK Level 10.','Progress','RARE','xp_level',10,25,150,50,true),
('level_25','Established','Reach BLINK Level 25.','Progress','EPIC','xp_level',25,75,350,60,true),
('streak_7','One Week Active','Reach a 7-day BLINK activity streak.','Progress','RARE','daily_streak',7,25,100,70,true),
('streak_30','Monthly Momentum','Reach a 30-day BLINK activity streak.','Progress','EPIC','daily_streak',30,100,300,80,true),
('creator_views_1k','1K Creator Views','Receive 1,000 total views across your posts and reels.','Creator','RARE','creator_views',1000,40,250,90,true),
('creator_views_10k','10K Creator Views','Receive 10,000 total views across your posts and reels.','Creator','EPIC','creator_views',10000,150,600,100,true),
('comments_100','Conversation Starter','Write 100 meaningful comments or replies.','Social','RARE','comments_written',100,40,250,110,true),
('followers_1k','1K Community','Reach 1,000 followers.','Social','LEGENDARY','followers',1000,300,1000,120,true)
on conflict (achievement_key) do update
set title=excluded.title,
    description=excluded.description,
    category=excluded.category,
    rarity=excluded.rarity,
    metric=excluded.metric,
    target_count=excluded.target_count,
    coin_reward=excluded.coin_reward,
    xp_reward=excluded.xp_reward,
    sort_order=excluded.sort_order,
    is_active=excluded.is_active,
    updated_at=now();

create table if not exists public.blink_achievement_claims (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete cascade,
    achievement_key text not null,
    coin_reward integer not null check (coin_reward >= 0),
    xp_reward integer not null check (xp_reward >= 0),
    claimed_at timestamptz not null default now(),
    unique(user_id, achievement_key)
);
create index if not exists blink_achievement_claims_user_idx
    on public.blink_achievement_claims(user_id, claimed_at desc);
alter table public.blink_achievement_claims enable row level security;
drop policy if exists blink_achievement_claims_read_own on public.blink_achievement_claims;
create policy blink_achievement_claims_read_own
on public.blink_achievement_claims
for select
to authenticated
using ((select auth.uid()) = user_id);
revoke all on public.blink_achievement_claims from anon, authenticated;
grant select on public.blink_achievement_claims to authenticated;

-- Claimable level/streak milestones. Cosmetic links are optional and only become active
-- after a real catalog item is intentionally attached server-side.
create table if not exists private.blink_progress_reward_config (
    reward_key text primary key,
    reward_type text not null check (reward_type in ('level','streak')),
    threshold integer not null check (threshold > 0),
    title text not null,
    description text not null,
    coin_reward integer not null check (coin_reward >= 0),
    xp_reward integer not null check (xp_reward >= 0),
    cosmetic_catalog_id text,
    sort_order integer not null default 0,
    is_active boolean not null default true,
    updated_at timestamptz not null default now(),
    unique(reward_type, threshold)
);
revoke all on private.blink_progress_reward_config from public, anon, authenticated;

insert into private.blink_progress_reward_config(
    reward_key,reward_type,threshold,title,description,coin_reward,xp_reward,cosmetic_catalog_id,sort_order,is_active
) values
('level_5_reward','level',5,'Level 5 Reward','Your first BLINK level milestone.',25,75,null,10,true),
('level_10_reward','level',10,'Level 10 Reward','A reward for becoming an active BLINK member.',50,125,null,20,true),
('level_20_reward','level',20,'Level 20 Reward','A reward for sustained meaningful activity.',100,225,null,30,true),
('level_30_reward','level',30,'Level 30 Reward','A reward for established BLINK participation.',150,325,null,40,true),
('level_50_reward','level',50,'Level 50 Reward','A major BLINK progression milestone.',250,500,null,50,true),
('level_75_reward','level',75,'Level 75 Reward','A high-activity long-term milestone.',400,750,null,60,true),
('level_100_reward','level',100,'Level 100 Reward','The maximum BLINK progression milestone.',1000,1500,null,70,true),
('streak_3_reward','streak',3,'3-Day Streak','Three active days on BLINK.',10,25,null,110,true),
('streak_7_reward','streak',7,'7-Day Streak','One full week of BLINK activity.',25,50,null,120,true),
('streak_14_reward','streak',14,'14-Day Streak','Two weeks of consistent activity.',50,100,null,130,true),
('streak_30_reward','streak',30,'30-Day Streak','A month of BLINK activity.',100,200,null,140,true),
('streak_60_reward','streak',60,'60-Day Streak','Two months of BLINK activity.',200,350,null,150,true),
('streak_100_reward','streak',100,'100-Day Streak','A long-term BLINK activity milestone.',400,600,null,160,true)
on conflict (reward_key) do update
set reward_type=excluded.reward_type,
    threshold=excluded.threshold,
    title=excluded.title,
    description=excluded.description,
    coin_reward=excluded.coin_reward,
    xp_reward=excluded.xp_reward,
    cosmetic_catalog_id=excluded.cosmetic_catalog_id,
    sort_order=excluded.sort_order,
    is_active=excluded.is_active,
    updated_at=now();

create table if not exists public.blink_progress_reward_claims (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete cascade,
    reward_key text not null,
    coin_reward integer not null check (coin_reward >= 0),
    xp_reward integer not null check (xp_reward >= 0),
    claimed_at timestamptz not null default now(),
    unique(user_id, reward_key)
);
create index if not exists blink_progress_reward_claims_user_idx
    on public.blink_progress_reward_claims(user_id, claimed_at desc);
alter table public.blink_progress_reward_claims enable row level security;
drop policy if exists blink_progress_reward_claims_read_own on public.blink_progress_reward_claims;
create policy blink_progress_reward_claims_read_own
on public.blink_progress_reward_claims
for select
to authenticated
using ((select auth.uid()) = user_id);
revoke all on public.blink_progress_reward_claims from anon, authenticated;
grant select on public.blink_progress_reward_claims to authenticated;

create or replace function private.blink_progress_metric(p_user uuid, p_metric text)
returns bigint
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_value bigint := 0;
begin
    if p_user is null then return 0; end if;

    case p_metric
        when 'posts_created' then
            select count(*)::bigint into v_value
            from public.feed_posts
            where user_id = p_user;
        when 'reels_created' then
            select count(*)::bigint into v_value
            from public.feed_posts
            where user_id = p_user and coalesce(is_reel,false) = true;
        when 'followers' then
            select coalesce(follower_count,0)::bigint into v_value
            from public.profiles where id = p_user;
        when 'xp_level' then
            select coalesce(xp_level,1)::bigint into v_value
            from public.profiles where id = p_user;
        when 'daily_streak' then
            select coalesce(daily_streak,0)::bigint into v_value
            from public.profiles where id = p_user;
        when 'creator_views' then
            select coalesce(sum(coalesce(view_count,0)),0)::bigint into v_value
            from public.feed_posts where user_id = p_user;
        when 'comments_written' then
            select count(*)::bigint into v_value
            from public.comments where author_id = p_user;
        else
            v_value := 0;
    end case;

    return coalesce(v_value,0);
end;
$$;
revoke all on function private.blink_progress_metric(uuid,text) from public, anon, authenticated;

create or replace function private.credit_blink_progress_reward(
    p_user uuid,
    p_kind text,
    p_item_name text,
    p_coin_reward integer,
    p_xp_event text,
    p_xp_key text,
    p_xp_reward integer
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_balance bigint := 0;
    v_awarded_xp integer := 0;
    v_total_xp bigint := 0;
    v_level integer := 1;
begin
    if p_user is null then raise exception 'AUTH_REQUIRED'; end if;

    insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
    values(p_user,0,now())
    on conflict(user_id) do nothing;

    if coalesce(p_coin_reward,0) > 0 then
        update public.user_balances
           set spendable_coin_balance = spendable_coin_balance + p_coin_reward,
               updated_at = now()
         where user_id = p_user
        returning floor(spendable_coin_balance)::bigint into v_balance;

        perform set_config('blink.coin_sync_bypass','1',true);
        insert into public.game_profiles(user_id,coins,updated_at)
        values(p_user,v_balance,now())
        on conflict(user_id) do update
          set coins=excluded.coins,updated_at=now();
        perform set_config('blink.coin_sync_bypass','0',true);

        insert into public.blink_coin_transactions(
            user_id,kind,item_name,amount,balance_after,metadata
        ) values (
            p_user,
            p_kind,
            p_item_name,
            p_coin_reward,
            v_balance,
            jsonb_build_object('progress_reward',true,'reward_key',p_xp_key)
        );
    else
        select coalesce(floor(spendable_coin_balance),0)::bigint
          into v_balance
          from public.user_balances
         where user_id = p_user;
    end if;

    v_awarded_xp := private.award_xp(
        p_user,
        p_xp_event,
        p_xp_key,
        'progress',
        p_item_name,
        greatest(coalesce(p_xp_reward,0),0)
    );

    select coalesce(total_xp,0),coalesce(xp_level,1)
      into v_total_xp,v_level
      from public.profiles where id=p_user;

    return jsonb_build_object(
        'success',true,
        'balance',coalesce(v_balance,0),
        'coin_reward',greatest(coalesce(p_coin_reward,0),0),
        'xp_reward',coalesce(v_awarded_xp,0),
        'total_xp',coalesce(v_total_xp,0),
        'xp_level',coalesce(v_level,1)
    );
end;
$$;
revoke all on function private.credit_blink_progress_reward(uuid,text,text,integer,text,text,integer)
from public, anon, authenticated;

create or replace function public.get_my_progress_hub()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_week_start date := date_trunc('week', now() at time zone 'UTC')::date;
    v_week_start_ts timestamptz := date_trunc('week', now() at time zone 'UTC') at time zone 'UTC';
    v_week_end_ts timestamptz := (date_trunc('week', now() at time zone 'UTC') + interval '7 days') at time zone 'UTC';
    v_total_xp bigint := 0;
    v_level integer := 1;
    v_streak integer := 0;
    v_balance bigint := 0;
    v_world_rank integer := 0;
    v_campus_rank integer := 0;
    v_weekly jsonb := '[]'::jsonb;
    v_achievements jsonb := '[]'::jsonb;
    v_rewards jsonb := '[]'::jsonb;
    v_history jsonb := '[]'::jsonb;
    v_creator_week jsonb := '{}'::jsonb;
    v_weekly_completed integer := 0;
    v_chest_claimed boolean := false;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;

    select coalesce(total_xp,0),coalesce(xp_level,1),coalesce(daily_streak,0)
      into v_total_xp,v_level,v_streak
      from public.profiles where id=v_user;

    select coalesce(floor(spendable_coin_balance),0)::bigint
      into v_balance
      from public.user_balances where user_id=v_user;

    select coalesce(world_rank,0),coalesce(campus_rank,0)
      into v_world_rank,v_campus_rank
      from public.leaderboard_snapshots
     where user_id=v_user
     order by snapshot_at desc
     limit 1;

    with mission_rows as (
        select
            c.mission_key,
            c.title,
            c.description,
            c.target_count,
            c.coin_reward,
            c.xp_reward,
            c.sort_order,
            least(
                c.target_count,
                (
                    select count(*)::integer
                    from private.blink_daily_mission_events e
                    where e.user_id=v_user
                      and e.event_type=any(c.event_types)
                      and e.created_at>=v_week_start_ts
                      and e.created_at<v_week_end_ts
                )
            ) as progress,
            exists(
                select 1 from public.blink_weekly_mission_claims cl
                where cl.user_id=v_user
                  and cl.week_start=v_week_start
                  and cl.mission_key=c.mission_key
            ) as claimed
        from private.blink_weekly_mission_config c
        where c.is_active=true
    )
    select
        coalesce(jsonb_agg(
            jsonb_build_object(
                'key',mission_key,
                'title',title,
                'description',description,
                'progress',progress,
                'target',target_count,
                'coin_reward',coin_reward,
                'xp_reward',xp_reward,
                'completed',progress>=target_count,
                'claimed',claimed,
                'claimable',progress>=target_count and not claimed
            )
            order by sort_order,mission_key
        ),'[]'::jsonb),
        count(*) filter(where progress>=target_count)::integer
    into v_weekly,v_weekly_completed
    from mission_rows;

    with achievement_rows as (
        select
            c.achievement_key,
            c.title,
            c.description,
            c.category,
            c.rarity,
            c.target_count,
            c.coin_reward,
            c.xp_reward,
            c.sort_order,
            private.blink_progress_metric(v_user,c.metric) as progress,
            exists(
                select 1 from public.blink_achievement_claims ac
                where ac.user_id=v_user and ac.achievement_key=c.achievement_key
            ) as claimed
        from private.blink_achievement_config c
        where c.is_active=true
    )
    select coalesce(jsonb_agg(
        jsonb_build_object(
            'key',achievement_key,
            'title',title,
            'description',description,
            'category',category,
            'rarity',rarity,
            'progress',progress,
            'target',target_count,
            'coin_reward',coin_reward,
            'xp_reward',xp_reward,
            'unlocked',progress>=target_count,
            'claimed',claimed,
            'claimable',progress>=target_count and not claimed
        )
        order by sort_order,achievement_key
    ),'[]'::jsonb)
    into v_achievements
    from achievement_rows;

    with reward_rows as (
        select
            c.reward_key,
            c.reward_type,
            c.threshold,
            c.title,
            c.description,
            c.coin_reward,
            c.xp_reward,
            c.cosmetic_catalog_id,
            c.sort_order,
            case
                when c.reward_type='level' then v_level>=c.threshold
                when c.reward_type='streak' then v_streak>=c.threshold
                else false
            end as eligible,
            exists(
                select 1 from public.blink_progress_reward_claims rc
                where rc.user_id=v_user and rc.reward_key=c.reward_key
            ) as claimed
        from private.blink_progress_reward_config c
        where c.is_active=true
    )
    select coalesce(jsonb_agg(
        jsonb_build_object(
            'key',reward_key,
            'type',reward_type,
            'threshold',threshold,
            'title',title,
            'description',description,
            'coin_reward',coin_reward,
            'xp_reward',xp_reward,
            'cosmetic_catalog_id',cosmetic_catalog_id,
            'eligible',eligible,
            'claimed',claimed,
            'claimable',eligible and not claimed
        )
        order by sort_order,reward_key
    ),'[]'::jsonb)
    into v_rewards
    from reward_rows;

    select coalesce(jsonb_agg(
        jsonb_build_object(
            'event_type',x.event_type,
            'xp_delta',x.xp_delta,
            'source_type',x.source_type,
            'created_at',x.created_at
        )
        order by x.created_at desc
    ),'[]'::jsonb)
    into v_history
    from (
        select event_type,xp_delta,source_type,created_at
        from public.xp_transactions
        where user_id=v_user
        order by created_at desc
        limit 25
    ) x;

    select exists(
        select 1 from public.blink_weekly_chest_claims
        where user_id=v_user and week_start=v_week_start
    ) into v_chest_claimed;

    select jsonb_build_object(
        'posts_created',
            count(*) filter(where coalesce(is_reel,false)=false),
        'reels_created',
            count(*) filter(where coalesce(is_reel,false)=true),
        'views_received',
            coalesce(sum(coalesce(view_count,0)),0),
        'likes_received',
            coalesce(sum(coalesce(like_count,0)),0),
        'comments_received',
            coalesce(sum(coalesce(comment_count,0)),0),
        'shares_received',
            coalesce(sum(coalesce(share_count,0)),0),
        'followers_gained',
            (
                select count(*)::integer
                from public.follows f
                where f.following_id=v_user
                  and f.created_at>=v_week_start_ts
                  and f.created_at<v_week_end_ts
            )
    )
    into v_creator_week
    from public.feed_posts
    where user_id=v_user
      and created_at>=v_week_start_ts
      and created_at<v_week_end_ts;

    return jsonb_build_object(
        'week_start',v_week_start,
        'total_xp',coalesce(v_total_xp,0),
        'xp_level',coalesce(v_level,1),
        'daily_streak',coalesce(v_streak,0),
        'world_rank',coalesce(v_world_rank,0),
        'campus_rank',coalesce(v_campus_rank,0),
        'coin_balance',coalesce(v_balance,0),
        'weekly_missions',coalesce(v_weekly,'[]'::jsonb),
        'weekly_chest',jsonb_build_object(
            'progress',coalesce(v_weekly_completed,0),
            'target',4,
            'coin_reward',50,
            'xp_reward',250,
            'eligible',coalesce(v_weekly_completed,0)>=4,
            'claimed',coalesce(v_chest_claimed,false),
            'claimable',coalesce(v_weekly_completed,0)>=4 and not coalesce(v_chest_claimed,false)
        ),
        'achievements',coalesce(v_achievements,'[]'::jsonb),
        'rewards',coalesce(v_rewards,'[]'::jsonb),
        'xp_history',coalesce(v_history,'[]'::jsonb),
        'creator_week',coalesce(v_creator_week,'{}'::jsonb)
    );
end;
$$;
revoke all on function public.get_my_progress_hub() from public, anon;
grant execute on function public.get_my_progress_hub() to authenticated;

create or replace function public.claim_weekly_mission(p_mission_key text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_week_start date := date_trunc('week', now() at time zone 'UTC')::date;
    v_week_start_ts timestamptz := date_trunc('week', now() at time zone 'UTC') at time zone 'UTC';
    v_week_end_ts timestamptz := (date_trunc('week', now() at time zone 'UTC') + interval '7 days') at time zone 'UTC';
    v_config private.blink_weekly_mission_config%rowtype;
    v_progress integer := 0;
    v_result jsonb;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
    if coalesce(trim(p_mission_key),'')='' then raise exception 'MISSION_REQUIRED'; end if;

    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text || ':weekly:' || v_week_start::text)::bigint);

    select * into v_config
      from private.blink_weekly_mission_config
     where mission_key=trim(p_mission_key) and is_active=true;
    if not found then raise exception 'MISSION_NOT_FOUND'; end if;

    if exists(
        select 1 from public.blink_weekly_mission_claims
        where user_id=v_user and week_start=v_week_start and mission_key=v_config.mission_key
    ) then
        return jsonb_build_object('success',true,'already_claimed',true,'mission_key',v_config.mission_key);
    end if;

    select count(*)::integer into v_progress
      from private.blink_daily_mission_events e
     where e.user_id=v_user
       and e.event_type=any(v_config.event_types)
       and e.created_at>=v_week_start_ts
       and e.created_at<v_week_end_ts;

    if v_progress<v_config.target_count then raise exception 'MISSION_NOT_COMPLETE'; end if;

    insert into public.blink_weekly_mission_claims(
        user_id,week_start,mission_key,coin_reward,xp_reward
    ) values (
        v_user,v_week_start,v_config.mission_key,v_config.coin_reward,v_config.xp_reward
    );

    v_result := private.credit_blink_progress_reward(
        v_user,
        'WEEKLY_MISSION',
        v_config.title,
        v_config.coin_reward,
        'weekly_mission',
        'weekly_mission:' || v_week_start::text || ':' || v_config.mission_key,
        v_config.xp_reward
    );

    return v_result || jsonb_build_object(
        'already_claimed',false,
        'mission_key',v_config.mission_key,
        'week_start',v_week_start
    );
end;
$$;
revoke all on function public.claim_weekly_mission(text) from public, anon;
grant execute on function public.claim_weekly_mission(text) to authenticated;

create or replace function public.claim_weekly_completion_chest()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_week_start date := date_trunc('week', now() at time zone 'UTC')::date;
    v_week_start_ts timestamptz := date_trunc('week', now() at time zone 'UTC') at time zone 'UTC';
    v_week_end_ts timestamptz := (date_trunc('week', now() at time zone 'UTC') + interval '7 days') at time zone 'UTC';
    v_completed integer := 0;
    v_result jsonb;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text || ':weekly_chest:' || v_week_start::text)::bigint);

    if exists(
        select 1 from public.blink_weekly_chest_claims
        where user_id=v_user and week_start=v_week_start
    ) then
        return jsonb_build_object('success',true,'already_claimed',true,'week_start',v_week_start);
    end if;

    select count(*)::integer into v_completed
    from private.blink_weekly_mission_config c
    where c.is_active=true
      and (
        select count(*)::integer
        from private.blink_daily_mission_events e
        where e.user_id=v_user
          and e.event_type=any(c.event_types)
          and e.created_at>=v_week_start_ts
          and e.created_at<v_week_end_ts
      ) >= c.target_count;

    if v_completed<4 then raise exception 'WEEKLY_CHEST_NOT_READY'; end if;

    insert into public.blink_weekly_chest_claims(user_id,week_start,coin_reward,xp_reward)
    values(v_user,v_week_start,50,250);

    v_result := private.credit_blink_progress_reward(
        v_user,
        'WEEKLY_CHEST',
        'Weekly Completion Chest',
        50,
        'weekly_mission',
        'weekly_chest:' || v_week_start::text,
        250
    );

    return v_result || jsonb_build_object(
        'already_claimed',false,
        'week_start',v_week_start,
        'completed_missions',v_completed
    );
end;
$$;
revoke all on function public.claim_weekly_completion_chest() from public, anon;
grant execute on function public.claim_weekly_completion_chest() to authenticated;

create or replace function public.claim_blink_achievement(p_achievement_key text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_config private.blink_achievement_config%rowtype;
    v_progress bigint := 0;
    v_result jsonb;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
    if coalesce(trim(p_achievement_key),'')='' then raise exception 'ACHIEVEMENT_REQUIRED'; end if;

    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text || ':achievement:' || trim(p_achievement_key))::bigint);

    select * into v_config
      from private.blink_achievement_config
     where achievement_key=trim(p_achievement_key) and is_active=true;
    if not found then raise exception 'ACHIEVEMENT_NOT_FOUND'; end if;

    if exists(
        select 1 from public.blink_achievement_claims
        where user_id=v_user and achievement_key=v_config.achievement_key
    ) then
        return jsonb_build_object('success',true,'already_claimed',true,'achievement_key',v_config.achievement_key);
    end if;

    v_progress := private.blink_progress_metric(v_user,v_config.metric);
    if v_progress<v_config.target_count then raise exception 'ACHIEVEMENT_LOCKED'; end if;

    insert into public.blink_achievement_claims(
        user_id,achievement_key,coin_reward,xp_reward
    ) values (
        v_user,v_config.achievement_key,v_config.coin_reward,v_config.xp_reward
    );

    v_result := private.credit_blink_progress_reward(
        v_user,
        'ACHIEVEMENT',
        v_config.title,
        v_config.coin_reward,
        'achievement',
        'achievement:' || v_config.achievement_key,
        v_config.xp_reward
    );

    return v_result || jsonb_build_object(
        'already_claimed',false,
        'achievement_key',v_config.achievement_key,
        'rarity',v_config.rarity
    );
end;
$$;
revoke all on function public.claim_blink_achievement(text) from public, anon;
grant execute on function public.claim_blink_achievement(text) to authenticated;

create or replace function public.claim_blink_progress_reward(p_reward_key text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_config private.blink_progress_reward_config%rowtype;
    v_level integer := 1;
    v_streak integer := 0;
    v_eligible boolean := false;
    v_result jsonb;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
    if coalesce(trim(p_reward_key),'')='' then raise exception 'REWARD_REQUIRED'; end if;

    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text || ':progress_reward:' || trim(p_reward_key))::bigint);

    select * into v_config
      from private.blink_progress_reward_config
     where reward_key=trim(p_reward_key) and is_active=true;
    if not found then raise exception 'REWARD_NOT_FOUND'; end if;

    if exists(
        select 1 from public.blink_progress_reward_claims
        where user_id=v_user and reward_key=v_config.reward_key
    ) then
        return jsonb_build_object('success',true,'already_claimed',true,'reward_key',v_config.reward_key);
    end if;

    select coalesce(xp_level,1),coalesce(daily_streak,0)
      into v_level,v_streak
      from public.profiles where id=v_user;

    v_eligible := case
        when v_config.reward_type='level' then v_level>=v_config.threshold
        when v_config.reward_type='streak' then v_streak>=v_config.threshold
        else false
    end;

    if not v_eligible then raise exception 'REWARD_LOCKED'; end if;

    insert into public.blink_progress_reward_claims(
        user_id,reward_key,coin_reward,xp_reward
    ) values (
        v_user,v_config.reward_key,v_config.coin_reward,v_config.xp_reward
    );

    v_result := private.credit_blink_progress_reward(
        v_user,
        upper(v_config.reward_type) || '_MILESTONE',
        v_config.title,
        v_config.coin_reward,
        case when v_config.reward_type='streak' then 'streak_milestone' else 'achievement' end,
        'progress_reward:' || v_config.reward_key,
        v_config.xp_reward
    );

    return v_result || jsonb_build_object(
        'already_claimed',false,
        'reward_key',v_config.reward_key,
        'reward_type',v_config.reward_type,
        'threshold',v_config.threshold,
        'cosmetic_catalog_id',v_config.cosmetic_catalog_id
    );
end;
$$;
revoke all on function public.claim_blink_progress_reward(text) from public, anon;
grant execute on function public.claim_blink_progress_reward(text) to authenticated;

commit;
