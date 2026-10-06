-- BLINK Drops v1
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
