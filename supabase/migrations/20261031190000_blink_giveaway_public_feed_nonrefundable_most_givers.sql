-- BLINK GIVEAWAY: public discovery, non-refundable funding and Most Givers.
-- This release retains BLINK Coins as the existing funding and prize unit.
-- Followers receive start notifications; current participation is open to
-- anyone inside the published audience. Historical refunds are deducted from
-- the Most Givers total, while new Giveaway funding is never refunded.

create or replace function private.blink_drop_expire_stale()
returns void
language plpgsql
security definer
set search_path=''
as $function$
begin
  -- Giveaway funding is committed at publication. Expiry closes entry only;
  -- it never returns unclaimed funds to the creator.
  update public.blink_drops
  set status='EXPIRED', updated_at=now()
  where status='ACTIVE' and ends_at<=now();
end;
$function$;
create or replace function private.blink_drop_safe_card(p_drop public.blink_drops)
returns jsonb
language sql stable security definer set search_path=''
as $$
  select jsonb_build_object(
    'id',p_drop.id,
    'creator_id',p_drop.creator_id,
    'creator_username',coalesce((select p.username from public.profiles p where p.id=p_drop.creator_id),''),
    'creator_name',coalesce((select coalesce(nullif(p.full_name,''),p.username) from public.profiles p where p.id=p_drop.creator_id),'BLINK creator'),
    'creator_avatar',coalesce((select p.avatar_url from public.profiles p where p.id=p_drop.creator_id),''),
    'reward_per_user',p_drop.reward_per_user,
    'winner_count',p_drop.winner_count,
    'claimed_count',p_drop.claimed_count,
    'total_coins',p_drop.total_budget,
    'coin_distributed',p_drop.coin_distributed,
    'coin_refunded',p_drop.coin_refunded,
    'action',p_drop.action,
    'target_type',p_drop.target_type,
    'target_id',p_drop.target_id,
    'target_title',private.blink_drop_target_title(p_drop.target_type,p_drop.target_id),
    'target_preview_url',case
      when p_drop.target_type in ('POST','REEL') then
        coalesce((select coalesce(nullif(f.image_url,''),nullif(f.images[1],'')) from public.feed_posts f
                  where f.id=p_drop.target_id and f.is_active=true),'')
      when p_drop.target_type='LISTING' then
        coalesce((select coalesce(nullif(m.image_url,''),nullif(m.image_urls[1],'')) from public.market_items m
                  where m.id=p_drop.target_id and lower(coalesce(m.status,'active'))='active' and coalesce(m.is_sold,false)=false),'')
      else '' end,
    'target_video_url',case when p_drop.target_type='REEL' then
      coalesce((select f.video_url from public.feed_posts f where f.id=p_drop.target_id and f.is_active=true),'')
      else '' end,
    'audience_scope',p_drop.audience_scope,
    'target_university',coalesce(p_drop.target_university,''),
    'status',p_drop.status,
    'created_at',p_drop.created_at,
    'ends_at',p_drop.ends_at,
    'eligible',(
      p_drop.status='ACTIVE' and p_drop.ends_at>now()
      and p_drop.claimed_count<p_drop.winner_count
      and p_drop.creator_id<>auth.uid()
      and (
        p_drop.audience_scope='ALL_CAMPUSES'
        or (p_drop.audience_scope='MY_CAMPUS' and exists (
          select 1 from public.profiles viewer where viewer.id=auth.uid()
            and nullif(btrim(coalesce(viewer.university,'')),'') is not null
            and lower(coalesce(viewer.university,''))=lower(coalesce(p_drop.target_university,''))
        ))
      )
      and not exists (select 1 from public.blink_drop_claims c
        where c.drop_id=p_drop.id and c.user_id=auth.uid())
      and not exists (select 1 from public.blocks b
        where (b.blocker_id=auth.uid() and b.blocked_id=p_drop.creator_id)
           or (b.blocker_id=p_drop.creator_id and b.blocked_id=auth.uid()))
    )
  );
$$;
create or replace function public.get_blink_drop_page(
  p_scope text default 'MINE',
  p_offset integer default 0,
  p_limit integer default 30
) returns jsonb
language plpgsql stable security definer set search_path=''
as $$
declare
  v_user uuid := auth.uid();
  v_limit integer := greatest(1,least(coalesce(p_limit,30),50));
  v_offset integer := greatest(0,least(coalesce(p_offset,0),100000));
  v_scope text := upper(coalesce(p_scope,'MINE'));
  v_result jsonb;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  if v_scope not in ('MINE','AVAILABLE') then raise exception 'INVALID_DROP_SCOPE'; end if;
  with chosen as (
    select d.* from public.blink_drops d
    where (v_scope='MINE' and d.creator_id=v_user)
       or (v_scope='AVAILABLE' and d.creator_id<>v_user
           and d.status='ACTIVE' and d.ends_at>now() and d.claimed_count<d.winner_count
           and (
             d.audience_scope='ALL_CAMPUSES'
             or (d.audience_scope='MY_CAMPUS' and exists (
               select 1 from public.profiles viewer where viewer.id=v_user
                 and nullif(btrim(coalesce(viewer.university,'')),'') is not null
                 and lower(coalesce(viewer.university,''))=lower(coalesce(d.target_university,''))
             ))
           )
           and not exists (select 1 from public.blocks b
             where (b.blocker_id=v_user and b.blocked_id=d.creator_id)
                or (b.blocker_id=d.creator_id and b.blocked_id=v_user))
           and not exists(select 1 from public.blink_drop_claims c
                          where c.drop_id=d.id and c.user_id=v_user))
    order by d.created_at desc,d.id desc
    limit v_limit+1 offset v_offset
  )
  select jsonb_build_object(
    'items',coalesce((select jsonb_agg(private.blink_drop_safe_card(x) order by x.created_at desc,x.id desc)
                      from (select * from chosen order by created_at desc,id desc limit v_limit) x),'[]'::jsonb),
    'has_more',(select count(*)>v_limit from chosen),
    'next_offset',v_offset+v_limit
  ) into v_result;
  return v_result;
end;
$$;
create or replace function public.get_blink_drop_by_id(p_drop_id uuid)
returns jsonb
language plpgsql stable security definer set search_path=''
as $$
declare
  v_user uuid := auth.uid();
  v_item jsonb;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  select private.blink_drop_safe_card(d) into v_item
  from public.blink_drops d
  where d.id=p_drop_id
    and (
      d.creator_id=v_user
      or (
        d.created_at>now()-interval '14 days'
        and (
          d.audience_scope='ALL_CAMPUSES'
          or (d.audience_scope='MY_CAMPUS' and exists (
            select 1 from public.profiles viewer where viewer.id=v_user
              and nullif(btrim(coalesce(viewer.university,'')),'') is not null
              and lower(coalesce(viewer.university,''))=lower(coalesce(d.target_university,''))
          ))
        )
        and not exists (select 1 from public.blocks b
          where (b.blocker_id=v_user and b.blocked_id=d.creator_id)
             or (b.blocker_id=d.creator_id and b.blocked_id=v_user))
      )
    );
  return coalesce(v_item,jsonb_build_object('not_found',true));
end;
$$;
CREATE OR REPLACE FUNCTION public.complete_blink_drop_action(p_drop_id uuid, p_comment_text text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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

  -- Follower snapshots drive start notifications only. Anyone in the
  -- published audience may enter the current Giveaway.
  if not (
    v_drop.audience_scope='ALL_CAMPUSES'
    or (
      v_drop.audience_scope='MY_CAMPUS'
      and exists (
        select 1 from public.profiles p
        where p.id=v_user
          and nullif(btrim(coalesce(p.university,'')),'') is not null
          and lower(coalesce(p.university,''))=lower(coalesce(v_drop.target_university,''))
      )
    )
  ) then raise exception 'DROP_NOT_ELIGIBLE'; end if;

  if exists (
    select 1 from public.blocks b
    where (b.blocker_id=v_user and b.blocked_id=v_drop.creator_id)
       or (b.blocker_id=v_drop.creator_id and b.blocked_id=v_user)
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
    'BLINK Giveaway reward',
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
$function$

create or replace function public.cancel_blink_drop(p_drop_id uuid)
returns jsonb
language plpgsql
security definer
set search_path=''
as $function$
declare
  v_user uuid := auth.uid();
  v_drop public.blink_drops%rowtype;
  v_balance numeric := 0;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  perform private.blink_drop_expire_stale();

  select * into v_drop
  from public.blink_drops d
  where d.id=p_drop_id and d.creator_id=v_user
  for update;

  if not found then raise exception 'DROP_NOT_FOUND'; end if;
  if v_drop.status<>'ACTIVE' then raise exception 'DROP_NOT_ACTIVE'; end if;

  update public.blink_drops
  set status='CANCELLED', cancelled_at=now(), updated_at=now()
  where id=v_drop.id;

  select coalesce(spendable_coin_balance,0) into v_balance
  from public.user_balances where user_id=v_user;

  return jsonb_build_object(
    'success',true,'drop_id',v_drop.id,'refunded',0,
    'distributed',v_drop.coin_distributed,
    'balance',floor(coalesce(v_balance,0))::bigint
  );
end;
$function$;
CREATE OR REPLACE FUNCTION public.create_blink_drop(p_target_type text, p_target_id uuid, p_action text, p_reward_per_user integer, p_winner_count integer, p_audience_scope text, p_duration_hours integer DEFAULT 24)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
    'BLINK Giveaway reserve',
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
    'started a BLINK Giveaway',
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
$function$

CREATE OR REPLACE FUNCTION public.get_blink_drops_state(p_limit integer DEFAULT 20)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
    select d.created_at,
      jsonb_build_object(
        'id',d.id,'creator_id',d.creator_id,
        'creator_username',coalesce(p.username,''),
        'creator_name',coalesce(p.full_name,p.username,'BLINK creator'),
        'reward_per_user',d.reward_per_user,'winner_count',d.winner_count,
        'claimed_count',d.claimed_count,'total_coins',d.total_budget,
        'action',d.action,'target_type',d.target_type,
        'target_title',private.blink_drop_target_title(d.target_type,d.target_id),
        'audience_scope',d.audience_scope,
        'target_university',coalesce(d.target_university,''),
        'status',d.status,'ends_at',d.ends_at,'eligible',true
      ) item
    from public.blink_drops d
    join public.profiles p on p.id=d.creator_id
    where d.creator_id<>v_user
      and d.status='ACTIVE' and d.ends_at>now()
      and d.claimed_count<d.winner_count
      and (
        d.audience_scope='ALL_CAMPUSES'
        or (d.audience_scope='MY_CAMPUS' and v_university is not null
            and lower(coalesce(d.target_university,''))=lower(v_university))
      )
      and not exists (
        select 1 from public.blink_drop_claims c
        where c.drop_id=d.id and c.user_id=v_user
      )
      and not exists (
        select 1 from public.blocks b
        where (b.blocker_id=v_user and b.blocked_id=d.creator_id)
           or (b.blocker_id=d.creator_id and b.blocked_id=v_user)
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

  -- Rank by total net committed Giveaway funding, including still-reserved
  -- amounts. Legacy refunds are deducted so old refunded amounts do not count.
  select coalesce(jsonb_agg(x.item order by x.coins_spent desc,x.giveaways_created desc,x.creator_id),'[]'::jsonb)
  into v_top
  from (
    select d.creator_id,
      sum(greatest(d.total_budget-d.coin_refunded,0))::bigint coins_spent,
      count(*)::integer giveaways_created,
      sum(d.claimed_count)::integer people_rewarded,
      jsonb_build_object(
        'creator_id',d.creator_id,
        'username',coalesce(p.username,''),
        'name',coalesce(p.full_name,p.username,'BLINK creator'),
        'coins_spent',sum(greatest(d.total_budget-d.coin_refunded,0))::bigint,
        'giveaways_created',count(*)::integer,
        'people_rewarded',sum(d.claimed_count)::integer,
        'is_following',exists(
          select 1 from public.follows f
          where f.follower_id=v_user and f.following_id=d.creator_id
        )
      ) item
    from public.blink_drops d
    join public.profiles p on p.id=d.creator_id
    where d.total_budget>0
    group by d.creator_id,p.username,p.full_name
    order by coins_spent desc,giveaways_created desc,d.creator_id
    limit 20
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
$function$

create or replace function public.get_blink_giveaway_feed_discovery(p_limit integer default 20)
returns jsonb
language plpgsql
security definer
set search_path=''
as $$
declare
  v_user uuid := auth.uid();
  v_limit integer := greatest(1,least(coalesce(p_limit,20),20));
  v_university text;
  v_items jsonb;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  perform private.blink_drop_expire_stale();

  select nullif(btrim(coalesce(p.university,'')),'') into v_university
  from public.profiles p where p.id=v_user;

  select coalesce(jsonb_agg(x.item order by x.is_active desc,x.created_at desc),'[]'::jsonb)
  into v_items
  from (
    select d.created_at,
      (d.status='ACTIVE' and d.ends_at>now() and d.claimed_count<d.winner_count) as is_active,
      jsonb_build_object(
        'drop_id',d.id,'creator_id',d.creator_id,
        'creator_username',coalesce(p.username,''),
        'creator_name',coalesce(p.full_name,p.username,'BLINK creator'),
        'creator_avatar',coalesce(p.avatar_url,''),
        'total_coins',d.total_budget,'reward_per_user',d.reward_per_user,
        'winner_count',d.winner_count,'claimed_count',d.claimed_count,
        'status',d.status,'action',d.action,'created_at',d.created_at,'ends_at',d.ends_at
      ) item
    from public.blink_drops d
    join public.profiles p on p.id=d.creator_id
    where d.creator_id<>v_user
      and d.created_at>now()-interval '14 days'
      and (
        (d.status='ACTIVE' and d.ends_at>now() and d.claimed_count<d.winner_count)
        or d.status in ('COMPLETED','EXPIRED','CANCELLED')
      )
      and (
        d.audience_scope='ALL_CAMPUSES'
        or (d.audience_scope='MY_CAMPUS' and v_university is not null
            and lower(coalesce(d.target_university,''))=lower(v_university))
      )
      and not exists (select 1 from public.blink_drop_claims c
        where c.drop_id=d.id and c.user_id=v_user)
      and not exists (select 1 from public.blocks b
        where (b.blocker_id=v_user and b.blocked_id=d.creator_id)
           or (b.blocker_id=d.creator_id and b.blocked_id=v_user))
    order by is_active desc,d.created_at desc
    limit v_limit
  ) x;

  return jsonb_build_object('items',coalesce(v_items,'[]'::jsonb));
end;
$$;

revoke all on function private.blink_drop_expire_stale() from public,anon,authenticated;
revoke all on function private.blink_drop_safe_card(public.blink_drops) from public,anon,authenticated;
revoke all on function public.get_blink_drop_page(text,integer,integer) from public,anon;
grant execute on function public.get_blink_drop_page(text,integer,integer) to authenticated;
revoke all on function public.get_blink_drop_by_id(uuid) from public,anon;
grant execute on function public.get_blink_drop_by_id(uuid) to authenticated;
revoke all on function public.get_blink_giveaway_feed_discovery(integer) from public,anon;
grant execute on function public.get_blink_giveaway_feed_discovery(integer) to authenticated;
revoke all on function public.complete_blink_drop_action(uuid,text) from public,anon;
grant execute on function public.complete_blink_drop_action(uuid,text) to authenticated;
revoke all on function public.cancel_blink_drop(uuid) from public,anon;
grant execute on function public.cancel_blink_drop(uuid) to authenticated;
revoke all on function public.create_blink_drop(text,uuid,text,integer,integer,text,integer) from public,anon;
grant execute on function public.create_blink_drop(text,uuid,text,integer,integer,text,integer) to authenticated;
revoke all on function public.get_blink_drops_state(integer) from public,anon;
grant execute on function public.get_blink_drops_state(integer) to authenticated;

