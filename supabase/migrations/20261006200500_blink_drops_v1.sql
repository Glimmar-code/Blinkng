-- BLINK Drops v1
-- destructive-change-reviewed
-- The only DROP TABLE statements in this migration are the existing pg_temp
-- scratch tables inside private_ranking.get_discovery_feed; no persistent
-- BLINK/user table or column is dropped.
-- rollback-plan: disable Drop creation/claims, refund every ACTIVE Drop's
-- unclaimed reserve through the existing coin ledger, revoke the Drop RPCs,
-- restore award_points/award_repost_distribution_points/capture_native_signal
-- and get_discovery_feed from the immediately preceding migrations, and keep
-- the Drop audit/claim tables read-only until balances and claims are reconciled.
-- Additive, server-authoritative giveaway system.
-- Coins are reserved from the organizer up front and rewards are deterministic:
-- the first eligible followers who complete the required action receive the reward.
-- There is no random draw, wagering, paid entry, or client-side balance mutation.

create table if not exists public.blink_drops (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references public.profiles(id) on delete cascade,
  target_type text not null check (target_type in ('POST','REEL','LISTING')),
  target_id uuid not null,
  action text not null check (action in ('LIKE','COMMENT','REPOST','SAVE_LISTING')),
  reward_per_user integer not null check (reward_per_user >= 100 and reward_per_user % 100 = 0),
  winner_count integer not null check (winner_count between 1 and 500),
  audience_scope text not null check (audience_scope in ('ALL_CAMPUSES','MY_CAMPUS')),
  target_university text,
  total_budget bigint not null check (total_budget > 0 and total_budget <= 10000000),
  claimed_count integer not null default 0 check (claimed_count >= 0),
  coin_distributed bigint not null default 0 check (coin_distributed >= 0),
  coin_refunded bigint not null default 0 check (coin_refunded >= 0),
  status text not null default 'ACTIVE' check (status in ('ACTIVE','COMPLETED','CANCELLED','EXPIRED')),
  starts_at timestamptz not null default now(),
  ends_at timestamptz not null,
  cancelled_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (ends_at > starts_at),
  check (claimed_count <= winner_count),
  check (coin_distributed + coin_refunded <= total_budget)
);

create index if not exists blink_drops_creator_created_idx
  on public.blink_drops(creator_id, created_at desc);
create index if not exists blink_drops_active_idx
  on public.blink_drops(status, ends_at, created_at desc)
  where status = 'ACTIVE';

create table if not exists public.blink_drop_eligible_followers (
  drop_id uuid not null references public.blink_drops(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(drop_id, user_id)
);
create index if not exists blink_drop_eligible_user_idx
  on public.blink_drop_eligible_followers(user_id, created_at desc);

create table if not exists public.blink_drop_claims (
  id uuid primary key default gen_random_uuid(),
  drop_id uuid not null references public.blink_drops(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  reward_amount integer not null check (reward_amount >= 100),
  action text not null,
  created_at timestamptz not null default now(),
  unique(drop_id, user_id)
);
create index if not exists blink_drop_claims_user_created_idx
  on public.blink_drop_claims(user_id, created_at desc);

create table if not exists public.blink_drop_incentivized_interactions (
  id uuid primary key default gen_random_uuid(),
  drop_id uuid not null references public.blink_drops(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  target_type text not null,
  target_id uuid not null,
  action text not null,
  created_at timestamptz not null default now(),
  unique(drop_id, user_id)
);
create index if not exists blink_drop_incentivized_target_idx
  on public.blink_drop_incentivized_interactions(target_type, target_id, created_at desc);

alter table public.blink_drops enable row level security;
alter table public.blink_drop_eligible_followers enable row level security;
alter table public.blink_drop_claims enable row level security;
alter table public.blink_drop_incentivized_interactions enable row level security;

revoke all on public.blink_drops from anon, authenticated;
revoke all on public.blink_drop_eligible_followers from anon, authenticated;
revoke all on public.blink_drop_claims from anon, authenticated;
revoke all on public.blink_drop_incentivized_interactions from anon, authenticated;

grant all on public.blink_drops to service_role;
grant all on public.blink_drop_eligible_followers to service_role;
grant all on public.blink_drop_claims to service_role;
grant all on public.blink_drop_incentivized_interactions to service_role;

-- Giveaway-paid engagement must never award social Rank Points/XP.
-- XP is downstream of point_transactions, so suppressing the point transaction
-- also prevents incentivized XP without changing normal organic actions.
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
    if current_setting('blink.incentivized_action', true) = 'on' then
        select coalesce(p.points,0) into v_new_points
        from public.profiles p where p.id=p_user_id;
        return coalesce(v_new_points,0)::integer;
    end if;

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

revoke all on function public.award_points(uuid,text,uuid) from public, anon;
grant execute on function public.award_points(uuid,text,uuid) to authenticated;

-- Repost distribution credits are a second Rank Point path and need the same guard.
create or replace function public.award_repost_distribution_points(
  p_post_id uuid,
  p_actor_id uuid,
  p_action_type text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_author_id uuid;
  v_reposter_id uuid;
  v_points integer;
  v_credit_id uuid;
  v_action text := lower(trim(coalesce(p_action_type, '')));
begin
  if current_setting('blink.incentivized_action', true) = 'on' then return; end if;
  if p_post_id is null or p_actor_id is null then return; end if;
  if v_action not in ('repost','view','like','comment','save','share') then return; end if;

  select fp.user_id into v_author_id
  from public.feed_posts fp
  where fp.id = p_post_id and fp.is_active = true;

  if v_author_id is null then return; end if;

  if v_action = 'repost' then
    v_reposter_id := p_actor_id;
    if not exists (
      select 1 from public.post_reposts r
      where r.post_id = p_post_id and r.user_id = p_actor_id
    ) then return; end if;
  else
    if p_actor_id = v_author_id then return; end if;
    select r.user_id into v_reposter_id
    from public.post_reposts r
    where r.post_id = p_post_id
      and r.user_id <> p_actor_id
      and exists (
        select 1 from public.follows f
        where f.follower_id = p_actor_id and f.following_id = r.user_id
      )
      and not exists (
        select 1 from public.blocks b
        where (b.blocker_id = p_actor_id and b.blocked_id = r.user_id)
           or (b.blocker_id = r.user_id and b.blocked_id = p_actor_id)
      )
    order by r.created_at desc
    limit 1;
  end if;

  if v_reposter_id is null or v_reposter_id = v_author_id then return; end if;

  v_points := case v_action
    when 'comment' then 2
    when 'save' then 2
    when 'share' then 2
    else 1
  end;

  insert into public.repost_point_credits(
    post_id, reposter_id, actor_id, action_type, points_each
  ) values (
    p_post_id, v_reposter_id, p_actor_id, v_action, v_points
  )
  on conflict (post_id, reposter_id, actor_id, action_type) do nothing
  returning id into v_credit_id;

  if v_credit_id is null then return; end if;

  update public.profiles
  set points = coalesce(points, 0) + v_points, updated_at = now()
  where id in (v_author_id, v_reposter_id);

  insert into public.point_transactions(user_id, action_type, points_delta, reference_id)
  values
    (v_author_id, 'repost_origin_' || v_action, v_points, p_post_id),
    (v_reposter_id, 'repost_distribution_' || v_action, v_points, p_post_id);
end;
$$;
revoke all on function public.award_repost_distribution_points(uuid,uuid,text)
from public, anon, authenticated;

-- Personal recommendation learning is also kept organic. Counts remain visible publicly,
-- but a Drop-paid like/comment/save cannot train the viewer's feed/reels interests.
create or replace function private_ranking.capture_native_signal()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid;
  v_target text;
  v_surface text;
  v_delta numeric;
  v_post_id uuid;
  v_game_type text;
  v_score integer;
begin
  if current_setting('blink.incentivized_action', true) = 'on' then
    if tg_op='DELETE' then return old; else return new; end if;
  end if;

  if tg_table_name='post_likes' then
    if tg_op='DELETE' then v_user:=old.user_id; v_post_id:=old.post_id;
    else v_user:=new.user_id; v_post_id:=new.post_id; end if;
    v_delta:=case when tg_op='DELETE' then -3 else 3 end;
  elsif tg_table_name='comments' then
    if tg_op='DELETE' then v_user:=old.author_id; v_post_id:=old.post_id;
    else v_user:=new.author_id; v_post_id:=new.post_id; end if;
    v_delta:=case when tg_op='DELETE' then -4 else 4 end;
  elsif tg_table_name='post_bookmarks' then
    if tg_op='DELETE' then v_user:=old.user_id; v_post_id:=old.post_id;
    else v_user:=new.user_id; v_post_id:=new.post_id; end if;
    v_delta:=case when tg_op='DELETE' then -4 else 4 end;
  elsif tg_table_name='post_shares' then
    if tg_op='DELETE' then v_user:=old.user_id; v_post_id:=old.post_id;
    else v_user:=new.user_id; v_post_id:=new.post_id; end if;
    v_delta:=case when tg_op='DELETE' then -5 else 5 end;
  elsif tg_table_name='marketplace_wishlist' then
    if tg_op='DELETE' then v_user:=old.user_id; v_target:=old.item_id::text;
    else v_user:=new.user_id; v_target:=new.item_id::text; end if;
    v_delta:=case when tg_op='DELETE' then -4 else 4 end;
    perform private_ranking.apply_target_signal(v_user,'market','market_item',v_target,v_delta);
    if tg_op='DELETE' then return old; else return new; end if;
  elsif tg_table_name='game_sessions' then
    if tg_op='DELETE' then v_user:=old.user_id; v_game_type:=old.game_type; v_score:=old.score;
    else v_user:=new.user_id; v_game_type:=new.game_type; v_score:=new.score; end if;
    v_delta:=least(4::numeric,1::numeric+greatest(0,coalesce(v_score,0))::numeric/200::numeric);
    if tg_op='DELETE' then v_delta:=-v_delta; end if;
    perform private_ranking.apply_target_signal(v_user,'game','game',v_game_type,v_delta);
    if tg_op='DELETE' then return old; else return new; end if;
  else
    if tg_op='DELETE' then return old; else return new; end if;
  end if;

  select case
      when fp.is_reel or nullif(fp.video_url,'') is not null then 'reels'
      else 'feed'
    end
    into v_surface
  from public.feed_posts fp
  where fp.id=v_post_id;

  if v_surface is not null then
    perform private_ranking.apply_target_signal(v_user,v_surface,'post',v_post_id::text,v_delta);
    perform private_ranking.refresh_discovery_interest_cache(v_user);
  end if;

  if tg_op='DELETE' then return old; else return new; end if;
end;
$$;
revoke all on function private_ranking.capture_native_signal()
from public, anon, authenticated;

create or replace function private.blink_drop_target_owned(
  p_user uuid,
  p_target_type text,
  p_target_id uuid
) returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select case upper(coalesce(p_target_type,''))
    when 'POST' then exists (
      select 1 from public.feed_posts fp
      where fp.id=p_target_id and fp.user_id=p_user
        and fp.is_active=true and coalesce(fp.is_reel,false)=false
    )
    when 'REEL' then exists (
      select 1 from public.feed_posts fp
      where fp.id=p_target_id and fp.user_id=p_user
        and fp.is_active=true and coalesce(fp.is_reel,false)=true
    )
    when 'LISTING' then exists (
      select 1 from public.market_items m
      where m.id=p_target_id and m.seller_id=p_user
        and lower(coalesce(m.status,'active'))='active'
        and coalesce(m.is_sold,false)=false
    )
    else false
  end;
$$;
revoke all on function private.blink_drop_target_owned(uuid,text,uuid)
from public, anon, authenticated;

create or replace function private.blink_drop_target_title(
  p_target_type text,
  p_target_id uuid
) returns text
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_title text;
begin
  if upper(coalesce(p_target_type,'')) in ('POST','REEL') then
    select left(
      coalesce(
        nullif(btrim(fp.text),''),
        nullif(btrim(fp.caption),''),
        case when fp.is_reel then 'Reel' else 'Post' end
      ),
      140
    ) into v_title
    from public.feed_posts fp where fp.id=p_target_id;
  elsif upper(coalesce(p_target_type,''))='LISTING' then
    select left(coalesce(nullif(btrim(m.title),''),'Market listing'),140)
      into v_title
    from public.market_items m where m.id=p_target_id;
  end if;
  return coalesce(v_title,initcap(lower(coalesce(p_target_type,'content'))));
end;
$$;
revoke all on function private.blink_drop_target_title(text,uuid)
from public, anon, authenticated;

create or replace function private.blink_drop_expire_stale()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_drop public.blink_drops%rowtype;
  v_refund bigint;
  v_balance numeric;
begin
  for v_drop in
    select * from public.blink_drops
    where status='ACTIVE' and ends_at<=now()
    order by ends_at
    for update
  loop
    v_refund := greatest(
      0,
      v_drop.total_budget - v_drop.coin_distributed - v_drop.coin_refunded
    );

    if v_refund>0 then
      insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
      values(v_drop.creator_id,0,now())
      on conflict(user_id) do nothing;

      update public.user_balances
      set spendable_coin_balance=spendable_coin_balance+v_refund,
          updated_at=now()
      where user_id=v_drop.creator_id
      returning spendable_coin_balance into v_balance;

      insert into public.blink_coin_transactions(
        user_id,kind,catalog_id,item_name,amount,balance_after,metadata
      ) values (
        v_drop.creator_id,
        'DROP_REFUND',
        null,
        'BLINK Drop expiry refund',
        v_refund,
        floor(v_balance)::bigint,
        jsonb_build_object('drop_id',v_drop.id,'reason','expired')
      );
    end if;

    update public.blink_drops
    set status='EXPIRED',
        coin_refunded=coin_refunded+v_refund,
        updated_at=now()
    where id=v_drop.id;
  end loop;
end;
$$;
revoke all on function private.blink_drop_expire_stale()
from public, anon, authenticated;

create or replace function public.create_blink_drop(
  p_target_type text,
  p_target_id uuid,
  p_action text,
  p_reward_per_user integer,
  p_winner_count integer,
  p_audience_scope text,
  p_duration_hours integer default 24
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_target_type text := upper(btrim(coalesce(p_target_type,'')));
  v_action text := upper(btrim(coalesce(p_action,'')));
  v_scope text := upper(btrim(coalesce(p_audience_scope,'')));
  v_university text;
  v_budget bigint;
  v_balance numeric;
  v_drop public.blink_drops%rowtype;
  v_eligible integer := 0;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  perform private.blink_drop_expire_stale();

  if p_reward_per_user < 100
     or p_reward_per_user > 100000
     or p_reward_per_user % 100 <> 0 then
    raise exception 'DROP_REWARD_INVALID';
  end if;
  if p_winner_count < 1 or p_winner_count > 500 then
    raise exception 'DROP_WINNER_COUNT_INVALID';
  end if;
  v_budget := p_reward_per_user::bigint * p_winner_count::bigint;
  if v_budget > 10000000 then raise exception 'DROP_BUDGET_TOO_LARGE'; end if;
  if p_duration_hours not in (1,6,12,24,72,168) then
    raise exception 'DROP_DURATION_INVALID';
  end if;

  if not private.blink_drop_target_owned(v_user,v_target_type,p_target_id) then
    raise exception 'DROP_TARGET_NOT_OWNED';
  end if;

  if v_target_type in ('POST','REEL') and v_action not in ('LIKE','COMMENT','REPOST') then
    raise exception 'DROP_ACTION_INVALID';
  elsif v_target_type='LISTING' and v_action<>'SAVE_LISTING' then
    raise exception 'DROP_ACTION_INVALID';
  end if;

  if v_scope='MY_CAMPUS' then
    select nullif(btrim(coalesce(p.university,'')),'')
      into v_university
    from public.profiles p where p.id=v_user;
    if v_university is null then raise exception 'UNIVERSITY_REQUIRED_FOR_DROP'; end if;
  elsif v_scope='ALL_CAMPUSES' then
    v_university := null;
  else
    raise exception 'DROP_AUDIENCE_INVALID';
  end if;

  insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
  values(v_user,0,now())
  on conflict(user_id) do nothing;

  select b.spendable_coin_balance into v_balance
  from public.user_balances b
  where b.user_id=v_user
  for update;

  if coalesce(v_balance,0)<v_budget then raise exception 'INSUFFICIENT_BLINK_COINS'; end if;

  update public.user_balances
  set spendable_coin_balance=spendable_coin_balance-v_budget,
      updated_at=now()
  where user_id=v_user
  returning spendable_coin_balance into v_balance;

  insert into public.blink_drops(
    creator_id,target_type,target_id,action,reward_per_user,winner_count,
    audience_scope,target_university,total_budget,starts_at,ends_at
  ) values (
    v_user,v_target_type,p_target_id,v_action,p_reward_per_user,p_winner_count,
    v_scope,v_university,v_budget,now(),now()+make_interval(hours=>p_duration_hours)
  )
  returning * into v_drop;

  insert into public.blink_coin_transactions(
    user_id,kind,catalog_id,item_name,amount,balance_after,metadata
  ) values (
    v_user,
    'DROP_RESERVE',
    null,
    'BLINK Drop reserve',
    -v_budget,
    floor(v_balance)::bigint,
    jsonb_build_object(
      'drop_id',v_drop.id,
      'target_type',v_target_type,
      'target_id',p_target_id,
      'action',v_action,
      'reward_per_user',p_reward_per_user,
      'winner_count',p_winner_count,
      'audience_scope',v_scope
    )
  );

  insert into public.blink_drop_eligible_followers(drop_id,user_id)
  select v_drop.id,f.follower_id
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
    )
  on conflict do nothing;

  get diagnostics v_eligible = row_count;

  insert into public.notifications(
    user_id,actor_id,type,target_type,target_id,text,sub_text,is_read,metadata
  )
  select
    e.user_id,
    v_user,
    'system'::public.notification_type_enum,
    'giveaway',
    v_drop.id,
    'started a BLINK Drop',
    p_reward_per_user::text || ' coins each · ' ||
      initcap(lower(replace(v_action,'_',' '))) || ' · ' ||
      p_winner_count::text || ' rewards',
    false,
    jsonb_build_object(
      'drop_id',v_drop.id,
      'reward_per_user',p_reward_per_user,
      'winner_count',p_winner_count,
      'action',v_action,
      'target_type',v_target_type
    )
  from public.blink_drop_eligible_followers e
  where e.drop_id=v_drop.id;

  return jsonb_build_object(
    'success',true,
    'drop_id',v_drop.id,
    'reserved',v_budget,
    'balance',floor(v_balance)::bigint,
    'eligible_followers',v_eligible,
    'ends_at',v_drop.ends_at
  );
end;
$$;
revoke all on function public.create_blink_drop(text,uuid,text,integer,integer,text,integer)
from public, anon;
grant execute on function public.create_blink_drop(text,uuid,text,integer,integer,text,integer)
to authenticated;

create or replace function public.complete_blink_drop_action(
  p_drop_id uuid,
  p_comment_text text default null
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_drop public.blink_drops%rowtype;
  v_balance numeric;
  v_remaining integer;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  perform private.blink_drop_expire_stale();

  select * into v_drop
  from public.blink_drops d
  where d.id=p_drop_id
  for update;

  if not found then raise exception 'DROP_NOT_FOUND'; end if;
  if v_drop.status<>'ACTIVE' or v_drop.ends_at<=now() then raise exception 'DROP_NOT_ACTIVE'; end if;
  if v_drop.creator_id=v_user then raise exception 'DROP_NOT_ELIGIBLE'; end if;
  if v_drop.claimed_count>=v_drop.winner_count then raise exception 'DROP_FULL'; end if;

  if not exists (
    select 1 from public.blink_drop_eligible_followers e
    where e.drop_id=v_drop.id and e.user_id=v_user
  ) then raise exception 'DROP_NOT_ELIGIBLE'; end if;

  if exists (
    select 1 from public.blink_drop_claims c
    where c.drop_id=v_drop.id and c.user_id=v_user
  ) then raise exception 'DROP_ALREADY_CLAIMED'; end if;

  -- All triggers fired by the required action see this transaction-local flag.
  perform set_config('blink.incentivized_action','on',true);

  if v_drop.target_type in ('POST','REEL') and v_drop.action='LIKE' then
    if exists (
      select 1 from public.post_likes l
      where l.post_id=v_drop.target_id and l.user_id=v_user
    ) then raise exception 'DROP_ACTION_ALREADY_DONE'; end if;
    insert into public.post_likes(post_id,user_id)
    values(v_drop.target_id,v_user);

  elsif v_drop.target_type in ('POST','REEL') and v_drop.action='COMMENT' then
    if nullif(btrim(coalesce(p_comment_text,'')),'') is null then
      raise exception 'DROP_COMMENT_REQUIRED';
    end if;
    if char_length(btrim(p_comment_text))>2000 then
      raise exception 'DROP_COMMENT_TOO_LONG';
    end if;
    insert into public.comments(post_id,author_id,content)
    values(v_drop.target_id,v_user,btrim(p_comment_text));

  elsif v_drop.target_type in ('POST','REEL') and v_drop.action='REPOST' then
    if exists (
      select 1 from public.post_reposts r
      where r.post_id=v_drop.target_id and r.user_id=v_user
    ) then raise exception 'DROP_ACTION_ALREADY_DONE'; end if;
    insert into public.post_reposts(post_id,user_id)
    values(v_drop.target_id,v_user);

  elsif v_drop.target_type='LISTING' and v_drop.action='SAVE_LISTING' then
    if exists (
      select 1 from public.marketplace_wishlist w
      where w.item_id=v_drop.target_id and w.user_id=v_user
    ) then raise exception 'DROP_ACTION_ALREADY_DONE'; end if;
    insert into public.marketplace_wishlist(user_id,item_id)
    values(v_user,v_drop.target_id);

  else
    raise exception 'DROP_ACTION_INVALID';
  end if;

  insert into public.blink_drop_claims(drop_id,user_id,reward_amount,action)
  values(v_drop.id,v_user,v_drop.reward_per_user,v_drop.action);

  insert into public.blink_drop_incentivized_interactions(
    drop_id,user_id,target_type,target_id,action
  ) values (
    v_drop.id,v_user,v_drop.target_type,v_drop.target_id,v_drop.action
  );

  insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
  values(v_user,0,now())
  on conflict(user_id) do nothing;

  update public.user_balances
  set spendable_coin_balance=spendable_coin_balance+v_drop.reward_per_user,
      updated_at=now()
  where user_id=v_user
  returning spendable_coin_balance into v_balance;

  update public.blink_drops
  set claimed_count=claimed_count+1,
      coin_distributed=coin_distributed+v_drop.reward_per_user,
      status=case
        when claimed_count+1>=winner_count then 'COMPLETED'
        else status
      end,
      updated_at=now()
  where id=v_drop.id
  returning winner_count-claimed_count into v_remaining;

  insert into public.blink_coin_transactions(
    user_id,kind,catalog_id,item_name,amount,balance_after,metadata
  ) values (
    v_user,
    'DROP_REWARD',
    null,
    'BLINK Drop reward',
    v_drop.reward_per_user,
    floor(v_balance)::bigint,
    jsonb_build_object(
      'drop_id',v_drop.id,
      'creator_id',v_drop.creator_id,
      'target_type',v_drop.target_type,
      'target_id',v_drop.target_id,
      'action',v_drop.action,
      'incentivized',true
    )
  );

  return jsonb_build_object(
    'success',true,
    'drop_id',v_drop.id,
    'reward',v_drop.reward_per_user,
    'balance',floor(v_balance)::bigint,
    'remaining',greatest(0,v_remaining),
    'status',case when v_remaining<=0 then 'COMPLETED' else 'ACTIVE' end
  );
end;
$$;
revoke all on function public.complete_blink_drop_action(uuid,text)
from public, anon;
grant execute on function public.complete_blink_drop_action(uuid,text)
to authenticated;

create or replace function public.cancel_blink_drop(
  p_drop_id uuid
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_drop public.blink_drops%rowtype;
  v_refund bigint;
  v_balance numeric;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  perform private.blink_drop_expire_stale();

  select * into v_drop
  from public.blink_drops d
  where d.id=p_drop_id and d.creator_id=v_user
  for update;

  if not found then raise exception 'DROP_NOT_FOUND'; end if;
  if v_drop.status<>'ACTIVE' then raise exception 'DROP_NOT_ACTIVE'; end if;

  v_refund := greatest(
    0,
    v_drop.total_budget-v_drop.coin_distributed-v_drop.coin_refunded
  );

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
      v_user,'DROP_REFUND',null,'BLINK Drop refund',
      v_refund,floor(v_balance)::bigint,
      jsonb_build_object('drop_id',v_drop.id,'reason','cancelled')
    );
  else
    select spendable_coin_balance into v_balance
    from public.user_balances where user_id=v_user;
  end if;

  update public.blink_drops
  set status='CANCELLED',
      cancelled_at=now(),
      coin_refunded=coin_refunded+v_refund,
      updated_at=now()
  where id=v_drop.id;

  return jsonb_build_object(
    'success',true,
    'drop_id',v_drop.id,
    'refunded',v_refund,
    'distributed',v_drop.coin_distributed,
    'balance',coalesce(floor(v_balance),0)::bigint
  );
end;
$$;
revoke all on function public.cancel_blink_drop(uuid) from public, anon;
grant execute on function public.cancel_blink_drop(uuid) to authenticated;

create or replace function public.follow_blink_drop_creator(
  p_creator_id uuid
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_following boolean;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  if p_creator_id is null or p_creator_id=v_user
     or not exists(select 1 from public.profiles p where p.id=p_creator_id) then
    raise exception 'INVALID_CREATOR';
  end if;

  perform public.follow_user(p_creator_id);
  select exists(
    select 1 from public.follows f
    where f.follower_id=v_user and f.following_id=p_creator_id
  ) into v_following;

  return jsonb_build_object('success',v_following,'creator_id',p_creator_id);
end;
$$;
revoke all on function public.follow_blink_drop_creator(uuid) from public, anon;
grant execute on function public.follow_blink_drop_creator(uuid) to authenticated;

create or replace function public.get_blink_drop_discovery(
  p_limit integer default 3
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_limit integer := greatest(1,least(coalesce(p_limit,3),5));
  v_university text;
  v_items jsonb;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  perform private.blink_drop_expire_stale();

  select nullif(btrim(coalesce(p.university,'')),'')
    into v_university
  from public.profiles p where p.id=v_user;

  select coalesce(jsonb_agg(x.item order by x.created_at desc),'[]'::jsonb)
  into v_items
  from (
    select
      d.created_at,
      jsonb_build_object(
        'drop_id',d.id,
        'creator_id',d.creator_id,
        'creator_username',coalesce(p.username,''),
        'creator_name',coalesce(p.full_name,p.username,'BLINK creator'),
        'creator_avatar',coalesce(p.avatar_url,''),
        'total_coins',d.total_budget,
        'reward_per_user',d.reward_per_user,
        'winner_count',d.winner_count,
        'claimed_count',d.claimed_count,
        'status',d.status,
        'action',d.action,
        'created_at',d.created_at
      ) item
    from public.blink_drops d
    join public.profiles p on p.id=d.creator_id
    where d.creator_id<>v_user
      and d.created_at>now()-interval '14 days'
      and not exists (
        select 1 from public.blink_drop_eligible_followers e
        where e.drop_id=d.id and e.user_id=v_user
      )
      and not exists (
        select 1 from public.follows f
        where f.follower_id=v_user and f.following_id=d.creator_id
      )
      and (
        d.audience_scope='ALL_CAMPUSES'
        or (
          v_university is not null
          and lower(coalesce(d.target_university,''))=lower(v_university)
        )
      )
      and not exists (
        select 1 from public.blocks b
        where (b.blocker_id=v_user and b.blocked_id=d.creator_id)
           or (b.blocker_id=d.creator_id and b.blocked_id=v_user)
      )
    order by d.created_at desc
    limit v_limit
  ) x;

  return jsonb_build_object('items',coalesce(v_items,'[]'::jsonb));
end;
$$;
revoke all on function public.get_blink_drop_discovery(integer) from public, anon;
grant execute on function public.get_blink_drop_discovery(integer) to authenticated;

create or replace function public.get_blink_drops_state(
  p_limit integer default 20
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_limit integer := greatest(1,least(coalesce(p_limit,20),50));
  v_balance bigint := 0;
  v_university text;
  v_posts jsonb := '[]'::jsonb;
  v_listings jsonb := '[]'::jsonb;
  v_active jsonb := '[]'::jsonb;
  v_mine jsonb := '[]'::jsonb;
  v_top jsonb := '[]'::jsonb;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  perform private.blink_drop_expire_stale();

  select coalesce(floor(b.spendable_coin_balance),0)::bigint
    into v_balance
  from public.user_balances b where b.user_id=v_user;
  v_balance := coalesce(v_balance,0);

  select nullif(btrim(coalesce(p.university,'')),'')
    into v_university
  from public.profiles p where p.id=v_user;

  select coalesce(jsonb_agg(x.item order by x.created_at desc),'[]'::jsonb)
  into v_posts
  from (
    select
      fp.created_at,
      jsonb_build_object(
        'id',fp.id,
        'type',case when fp.is_reel then 'REEL' else 'POST' end,
        'title',private.blink_drop_target_title(
          case when fp.is_reel then 'REEL' else 'POST' end,fp.id
        ),
        'subtitle',coalesce(fp.category,'')
      ) item
    from public.feed_posts fp
    where fp.user_id=v_user and fp.is_active=true
    order by fp.created_at desc
    limit 30
  ) x;

  select coalesce(jsonb_agg(x.item order by x.created_at desc),'[]'::jsonb)
  into v_listings
  from (
    select
      m.created_at,
      jsonb_build_object(
        'id',m.id,
        'type','LISTING',
        'title',coalesce(m.title,'Market listing'),
        'subtitle',coalesce(m.category,'')
      ) item
    from public.market_items m
    where m.seller_id=v_user
      and lower(coalesce(m.status,'active'))='active'
      and coalesce(m.is_sold,false)=false
    order by m.created_at desc
    limit 30
  ) x;

  select coalesce(jsonb_agg(x.item order by x.created_at desc),'[]'::jsonb)
  into v_active
  from (
    select
      d.created_at,
      jsonb_build_object(
        'id',d.id,
        'creator_id',d.creator_id,
        'creator_username',coalesce(p.username,''),
        'creator_name',coalesce(p.full_name,p.username,'BLINK creator'),
        'reward_per_user',d.reward_per_user,
        'winner_count',d.winner_count,
        'claimed_count',d.claimed_count,
        'total_coins',d.total_budget,
        'action',d.action,
        'target_type',d.target_type,
        'target_title',private.blink_drop_target_title(d.target_type,d.target_id),
        'audience_scope',d.audience_scope,
        'target_university',coalesce(d.target_university,''),
        'status',d.status,
        'ends_at',d.ends_at,
        'eligible',true
      ) item
    from public.blink_drops d
    join public.blink_drop_eligible_followers e
      on e.drop_id=d.id and e.user_id=v_user
    join public.profiles p on p.id=d.creator_id
    where d.status='ACTIVE'
      and d.ends_at>now()
      and d.claimed_count<d.winner_count
      and not exists (
        select 1 from public.blink_drop_claims c
        where c.drop_id=d.id and c.user_id=v_user
      )
    order by d.created_at desc
    limit v_limit
  ) x;

  select coalesce(jsonb_agg(x.item order by x.created_at desc),'[]'::jsonb)
  into v_mine
  from (
    select
      d.created_at,
      jsonb_build_object(
        'id',d.id,
        'creator_id',d.creator_id,
        'creator_username',coalesce(p.username,''),
        'creator_name',coalesce(p.full_name,p.username,'You'),
        'reward_per_user',d.reward_per_user,
        'winner_count',d.winner_count,
        'claimed_count',d.claimed_count,
        'total_coins',d.total_budget,
        'action',d.action,
        'target_type',d.target_type,
        'target_title',private.blink_drop_target_title(d.target_type,d.target_id),
        'audience_scope',d.audience_scope,
        'target_university',coalesce(d.target_university,''),
        'status',d.status,
        'ends_at',d.ends_at,
        'eligible',false
      ) item
    from public.blink_drops d
    join public.profiles p on p.id=d.creator_id
    where d.creator_id=v_user
    order by d.created_at desc
    limit v_limit
  ) x;

  select coalesce(jsonb_agg(x.item order by x.coins_given desc,x.people_rewarded desc),'[]'::jsonb)
  into v_top
  from (
    select
      d.creator_id,
      sum(d.coin_distributed)::bigint coins_given,
      sum(d.claimed_count)::integer people_rewarded,
      jsonb_build_object(
        'creator_id',d.creator_id,
        'username',coalesce(p.username,''),
        'name',coalesce(p.full_name,p.username,'BLINK creator'),
        'coins_given',sum(d.coin_distributed)::bigint,
        'drops_completed',count(*) filter(where d.coin_distributed>0)::integer,
        'people_rewarded',sum(d.claimed_count)::integer,
        'is_following',exists(
          select 1 from public.follows f
          where f.follower_id=v_user and f.following_id=d.creator_id
        )
      ) item
    from public.blink_drops d
    join public.profiles p on p.id=d.creator_id
    where d.coin_distributed>0
    group by d.creator_id,p.username,p.full_name
    order by coins_given desc,people_rewarded desc
    limit 50
  ) x;

  return jsonb_build_object(
    'balance',v_balance,
    'university',coalesce(v_university,''),
    'targets',jsonb_build_object(
      'posts',coalesce(v_posts,'[]'::jsonb),
      'listings',coalesce(v_listings,'[]'::jsonb)
    ),
    'active_drops',coalesce(v_active,'[]'::jsonb),
    'my_drops',coalesce(v_mine,'[]'::jsonb),
    'top_givers',coalesce(v_top,'[]'::jsonb)
  );
end;
$$;
revoke all on function public.get_blink_drops_state(integer) from public, anon;
grant execute on function public.get_blink_drops_state(integer) to authenticated;


-- Keep paid Drop engagement visible to users while excluding it from organic Feed/Reels ranking.
-- The public counters still show the interaction. Only ranking inputs remove
-- interactions recorded by BLINK Drops, so coin-funded activity cannot buy virality.
CREATE OR REPLACE FUNCTION private_ranking.get_discovery_feed(p_limit integer DEFAULT 40, p_offset integer DEFAULT 0, p_as_of timestamp with time zone DEFAULT now(), p_gravity numeric DEFAULT 1.5, p_w1 numeric DEFAULT 2.0, p_w2 numeric DEFAULT 1.0, p_w3 numeric DEFAULT 250.0, p_w4 numeric DEFAULT 4.0)
 RETURNS TABLE(item jsonb, feed_score numeric, ranking_components jsonb, feed_position integer, as_of timestamp with time zone, next_offset integer)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_user uuid := auth.uid();
  v_limit integer := greatest(1, least(coalesce(p_limit,40),100));
  v_offset integer := greatest(0,coalesce(p_offset,0));
  v_target_count integer;
  v_candidate_cap integer;
  v_position integer;
  v_want_reel boolean;
  v_want_tier text;
  v_pick record;
  v_last_creator_1 uuid;
  v_last_creator_2 uuid;
  v_viewer_location extensions.geography;
  v_positive_interests text[] := '{}'::text[];
  v_negative_interests text[] := '{}'::text[];
  v_positive_creators uuid[] := '{}'::uuid[];
  v_negative_creators uuid[] := '{}'::uuid[];
  v_tag_weights jsonb := '{}'::jsonb;
  v_creator_weights jsonb := '{}'::jsonb;
  v_feed_type text := lower(coalesce(nullif(current_setting('blink.feed_type', true), ''), 'all'));
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_as_of is null then p_as_of := now(); end if;
  if p_gravity < 0.5 or p_gravity > 3.0 then raise exception 'INVALID_GRAVITY'; end if;
  if least(p_w1,p_w2,p_w3,p_w4) < 0 then raise exception 'INVALID_WEIGHT'; end if;
  if v_offset > 5000 then raise exception 'OFFSET_TOO_LARGE'; end if;
  if v_feed_type not in ('all','posts','reels') then v_feed_type := 'all'; end if;

  v_target_count := least(5100,v_offset + v_limit);
  v_candidate_cap := least(10000,greatest(250,v_target_count * 20));

  select g.location into v_viewer_location
  from private_ranking.discovery_user_geo g
  where g.user_id=v_user;

  select c.positive_interests,c.negative_interests,c.positive_creators,c.negative_creators,c.tag_weights,c.creator_weights
  into v_positive_interests,v_negative_interests,v_positive_creators,v_negative_creators,v_tag_weights,v_creator_weights
  from private_ranking.discovery_interest_cache c
  where c.user_id=v_user;

  v_positive_interests := coalesce(v_positive_interests,'{}'::text[]);
  v_negative_interests := coalesce(v_negative_interests,'{}'::text[]);
  v_positive_creators := coalesce(v_positive_creators,'{}'::uuid[]);
  v_negative_creators := coalesce(v_negative_creators,'{}'::uuid[]);
  v_tag_weights := coalesce(v_tag_weights,'{}'::jsonb);
  v_creator_weights := coalesce(v_creator_weights,'{}'::jsonb);

  drop table if exists pg_temp.discovery_candidates;
  create temporary table discovery_candidates(
    post_id uuid primary key,
    creator_id uuid not null,
    is_reel boolean not null,
    creator_tier text not null,
    feed_score numeric not null,
    completion_rate numeric not null,
    verification_multiplier numeric not null,
    cold_start_multiplier numeric not null,
    affinity_component numeric not null,
    proximity_component numeric not null,
    created_at timestamptz not null
  ) on commit drop;

  drop table if exists pg_temp.discovery_selected;
  create temporary table discovery_selected(
    position integer primary key,
    post_id uuid unique not null
  ) on commit drop;

  insert into pg_temp.discovery_candidates(
    post_id,creator_id,is_reel,creator_tier,feed_score,completion_rate,
    verification_multiplier,cold_start_multiplier,affinity_component,proximity_component,created_at
  )
  with raw as (
    select
      fp.id as post_id,
      fp.user_id as creator_id,
      fp.is_reel,
      fp.creator_post_number,
      greatest(fp.created_at, coalesce(rr.created_at, fp.created_at)) as created_at,
      pr.created_at as creator_created_at,
      greatest(coalesce(pr.daily_streak,0),0) as creator_streak,
      greatest(
        coalesce(fp.like_count,0)
        - (
          select count(*)::integer
          from public.blink_drop_incentivized_interactions di
          where di.target_id=fp.id and di.action='LIKE'
        ),
        0
      ) as likes,
      greatest(
        coalesce(fp.comment_count,0)
        - (
          select count(*)::integer
          from public.blink_drop_incentivized_interactions di
          where di.target_id=fp.id and di.action='COMMENT'
        ),
        0
      ) as comments,
      greatest(coalesce(fp.share_count,0),0) as shares,
      greatest(
        coalesce(fp.repost_count,0)
        - (
          select count(*)::integer
          from public.blink_drop_incentivized_interactions di
          where di.target_id=fp.id and di.action='REPOST'
        ),
        0
      ) as reposts,
      greatest(coalesce(fp.view_count,0),0) as views,
      coalesce(rm.completion_rate,0)::numeric as completion_rate,
      coalesce(vm.multiplier,1)::numeric as verification_multiplier,
      (rr.id is not null) as has_followed_repost,
      array(
        select distinct lower(btrim(x))
        from unnest(
          coalesce(fp.tags,'{}'::text[])
          || coalesce(fp.hashtags,'{}'::text[])
          || array[coalesce(fp.category,'')]
        ) x
        where nullif(btrim(x),'') is not null
      ) as labels,
      case
        when v_viewer_location is not null and cg.location is not null then
          extensions.st_distance(v_viewer_location,cg.location)
        else null
      end as distance_meters
    from public.feed_posts fp
    join public.profiles pr on pr.id=fp.user_id
    left join private_ranking.discovery_reel_metrics rm on rm.post_id=fp.id
    left join private_ranking.discovery_user_geo cg on cg.user_id=fp.user_id
    left join private_ranking.discovery_verification_multipliers vm
      on vm.tier = case upper(coalesce(pr.verification_badge,'NONE'))
        when 'GOLD' then 'gold'::public.discovery_verification_tier_enum
        when 'BLUE' then 'blue'::public.discovery_verification_tier_enum
        else 'standard'::public.discovery_verification_tier_enum
      end
    left join lateral (
      select r.id, r.user_id, r.created_at
      from public.post_reposts r
      where r.post_id = fp.id
        and r.user_id <> v_user
        and not exists (
          select 1
          from public.blink_drop_incentivized_interactions di
          where di.target_id=fp.id
            and di.action='REPOST'
            and di.user_id=r.user_id
        )
        and exists (
          select 1 from public.follows f
          where f.follower_id = v_user and f.following_id = r.user_id
        )
        and not exists (
          select 1 from public.blocks b
          where (b.blocker_id=v_user and b.blocked_id=r.user_id)
             or (b.blocker_id=r.user_id and b.blocked_id=v_user)
        )
        and not exists (
          select 1 from public.muted_users mu
          where mu.user_id=v_user and mu.muted_id=r.user_id
        )
      order by r.created_at desc
      limit 1
    ) rr on true
    where fp.is_active=true
      and fp.is_flagged=false
      and (
        v_feed_type = 'all'
        or (v_feed_type = 'posts' and not coalesce(fp.is_reel,false))
        or (v_feed_type = 'reels' and coalesce(fp.is_reel,false))
      )
      and (lower(coalesce(fp.audience,'everyone'))='everyone' or fp.user_id=v_user)
      and not exists (
        select 1 from public.blocks b
        where (b.blocker_id=v_user and b.blocked_id=fp.user_id)
           or (b.blocker_id=fp.user_id and b.blocked_id=v_user)
      )
      and not exists (
        select 1 from public.muted_users mu
        where mu.user_id=v_user and mu.muted_id=fp.user_id
      )
      and greatest(fp.created_at, coalesce(rr.created_at, fp.created_at)) > p_as_of - interval '7 days'
      and greatest(fp.created_at, coalesce(rr.created_at, fp.created_at)) <= p_as_of
      and (fp.expires_at is null or fp.expires_at > p_as_of)
      and not exists (
        select 1
        from public.feed_preferences pref
        where pref.user_id=v_user
          and pref.post_id=fp.id
          and lower(pref.preference) in ('hide','hidden','not_interested','not interested')
      )
  ), components as (
    select
      r.*,
      ln((r.creator_streak + 1)::numeric) as streak_component,
      (
        r.likes::numeric
        + r.comments::numeric * 3
        + r.shares::numeric * 5
        + r.reposts::numeric * 5
        + r.views::numeric * 0.1
      ) as virality_component,
      case
        when r.distance_meters is null then 0::numeric
        else (1.0 / greatest(r.distance_meters,25.0))::numeric
      end as proximity_component,
      least(32::numeric,greatest(-20::numeric,
        cardinality(array(
          select a from unnest(r.labels) a
          intersect
          select b from unnest(v_positive_interests) b
        ))::numeric * 1.5
        - cardinality(array(
          select a from unnest(r.labels) a
          intersect
          select b from unnest(v_negative_interests) b
        ))::numeric * 6
        + coalesce((v_creator_weights ->> r.creator_id::text)::numeric,0)
        + case when r.has_followed_repost then 12::numeric else 0::numeric end
      )) as affinity_component,
      greatest(0::numeric,extract(epoch from (p_as_of-r.created_at))::numeric / 3600::numeric) as age_hours,
      case when r.creator_post_number <= 5 then 2::numeric else 1::numeric end as cold_start_multiplier,
      case
        when r.creator_created_at >= p_as_of - interval '30 days' or r.creator_post_number <= 10 then 'new'
        else 'established'
      end as creator_tier
    from raw r
  ), scored as (
    select
      c.*,
      (
        (
          p_w1 * c.streak_component
          + p_w2 * c.virality_component
          + p_w3 * c.proximity_component
          + p_w4 * c.affinity_component
        ) / power(c.age_hours + 2::numeric,p_gravity)
      ) * c.verification_multiplier * c.cold_start_multiplier * private_ranking.active_blink_boost_factor(c.post_id,p_as_of) + private_ranking.viewer_personalization_bonus(v_user,c.post_id,c.creator_id,p_as_of) + case when private.is_blink_owner_id(c.creator_id) then 1000000000::numeric else 0::numeric end as final_score
    from components c
  )
  select
    s.post_id,s.creator_id,s.is_reel,s.creator_tier,s.final_score,s.completion_rate,
    s.verification_multiplier,s.cold_start_multiplier,s.affinity_component,s.proximity_component,s.created_at
  from scored s
  order by s.final_score desc,s.created_at desc,s.post_id desc
  limit v_candidate_cap;

  for v_position in 1..v_target_count loop
    v_want_reel := case
      when v_feed_type = 'reels' then true
      when v_feed_type = 'posts' then false
      else mod(v_position,4)=0
    end;
    v_want_tier := case when mod(v_position-1,10) in (2,5,8) then 'new' else 'established' end;

    select c.* into v_pick
    from pg_temp.discovery_candidates c
    where not exists(select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id)
      and c.is_reel=v_want_reel
      and c.creator_tier=v_want_tier
      and not (
        v_last_creator_1 is not null
        and v_last_creator_2=v_last_creator_1
        and c.creator_id=v_last_creator_1
      )
    order by c.feed_score desc,c.created_at desc,c.post_id desc
    limit 1;

    if not found then
      select c.* into v_pick
      from pg_temp.discovery_candidates c
      where not exists(select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id)
        and c.is_reel=v_want_reel
        and not (
          v_last_creator_1 is not null
          and v_last_creator_2=v_last_creator_1
          and c.creator_id=v_last_creator_1
        )
      order by c.feed_score desc,c.created_at desc,c.post_id desc
      limit 1;
    end if;

    if not found then
      select c.* into v_pick
      from pg_temp.discovery_candidates c
      where not exists(select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id)
        and c.creator_tier=v_want_tier
        and not (
          v_last_creator_1 is not null
          and v_last_creator_2=v_last_creator_1
          and c.creator_id=v_last_creator_1
        )
      order by c.feed_score desc,c.created_at desc,c.post_id desc
      limit 1;
    end if;

    if not found then
      select c.* into v_pick
      from pg_temp.discovery_candidates c
      where not exists(select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id)
        and not (
          v_last_creator_1 is not null
          and v_last_creator_2=v_last_creator_1
          and c.creator_id=v_last_creator_1
        )
      order by c.feed_score desc,c.created_at desc,c.post_id desc
      limit 1;
    end if;

    if not found then exit; end if;

    insert into pg_temp.discovery_selected(position,post_id)
    values (v_position,v_pick.post_id);

    v_last_creator_2 := v_last_creator_1;
    v_last_creator_1 := v_pick.creator_id;
  end loop;

  return query
  select
    to_jsonb(fp) || jsonb_build_object(
      'repost_id', dist.id,
      'reposted_by_id', dist.user_id,
      'reposted_by_username', dist.username,
      'repost_count', coalesce(fp.repost_count,0),
      'is_reposted_by_me', exists(
        select 1 from public.post_reposts mine
        where mine.post_id=fp.id and mine.user_id=v_user
      )
    ) as item,
    c.feed_score,
    jsonb_build_object(
      'model','weighted_decay_v1',
      'format',case when c.is_reel then 'reel' else 'post' end,
      'creator_tier',c.creator_tier,
      'verification_multiplier',c.verification_multiplier,
      'cold_start_boost',c.cold_start_multiplier,
      'reel_completion_boost',1,
      'proximity_applied',c.proximity_component > 0,
      'affinity_direction',case when c.affinity_component > 0 then 'positive' when c.affinity_component < 0 then 'negative' else 'neutral' end,
      'repost_distribution_boost',dist.id is not null
    ) as ranking_components,
    s.position as feed_position,
    p_as_of as as_of,
    (v_offset + count(*) over())::integer as next_offset
  from pg_temp.discovery_selected s
  join pg_temp.discovery_candidates c on c.post_id=s.post_id
  join public.feed_posts fp on fp.id=s.post_id
  left join lateral (
    select r.id, r.user_id, rp.username, r.created_at
    from public.post_reposts r
    join public.profiles rp on rp.id=r.user_id
    where r.post_id=fp.id
      and r.user_id <> v_user
      and not exists (
        select 1
        from public.blink_drop_incentivized_interactions di
        where di.target_id=fp.id
          and di.action='REPOST'
          and di.user_id=r.user_id
      )
      and exists (
        select 1 from public.follows f
        where f.follower_id=v_user and f.following_id=r.user_id
      )
      and not exists (
        select 1 from public.blocks b
        where (b.blocker_id=v_user and b.blocked_id=r.user_id)
           or (b.blocker_id=r.user_id and b.blocked_id=v_user)
      )
      and not exists (
        select 1 from public.muted_users mu
        where mu.user_id=v_user and mu.muted_id=r.user_id
      )
    order by r.created_at desc
    limit 1
  ) dist on true
  where s.position > v_offset
    and s.position <= v_offset + v_limit
  order by s.position;
end;
$function$

