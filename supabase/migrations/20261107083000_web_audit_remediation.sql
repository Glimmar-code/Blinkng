-- BLINK Web & PWA audit remediation.
-- Safe rollback: drop only the new atomic RPC/settings table and restore the prior search/purchase function definitions from their source migrations.

create table if not exists public.blink_store_settings (
  id boolean primary key default true check (id),
  is_paused boolean not null default true,
  updated_at timestamptz not null default now()
);
insert into public.blink_store_settings(id,is_paused) values(true,true) on conflict(id) do nothing;
revoke all on public.blink_store_settings from public, anon, authenticated;
create or replace function private.blink_store_is_paused() returns boolean
language sql stable security definer set search_path=''
as $$ select coalesce((select is_paused from public.blink_store_settings where id=true),true); $$;
revoke all on function private.blink_store_is_paused() from public, anon, authenticated;

create or replace function public.search_profiles_page(
  p_query text,
  p_limit integer default 30,
  p_after_username text default null,
  p_after_id uuid default null
)
returns table(
  id uuid,
  full_name text,
  name text,
  username text,
  handle text,
  avatar_url text,
  university text,
  faculty text,
  department text,
  academic_level text,
  bio text,
  professional_headline text,
  is_verified boolean,
  verification_badge text,
  verification_tier text,
  follower_count integer,
  following_count integer,
  posts_count integer,
  online_now boolean,
  is_online boolean,
  last_seen_at timestamptz,
  points integer,
  total_xp bigint,
  xp_level integer,
  created_at timestamptz,
  blink_vip_until timestamptz,
  verified_at timestamptz,
  daily_streak integer,
  world_rank integer,
  campus_rank integer
)
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  with q as (
    select lower(trim(coalesce(p_query,''))) as term
  )
  select
    p.id,
    coalesce(p.full_name, p.username, ''),
    coalesce(p.name, p.full_name, p.username, ''),
    p.username,
    coalesce(p.handle, p.username, ''),
    p.avatar_url,
    p.university,
    p.faculty,
    p.department,
    p.academic_level,
    p.bio,
    p.professional_headline,
    coalesce(p.is_verified, false),
    coalesce(p.verification_badge, p.verification_tier::text, ''),
    coalesce(p.verification_tier::text, ''),
    greatest(coalesce(p.follower_count,0),0),
    greatest(coalesce(p.following_count,0),0),
    greatest(coalesce(p.posts_count,0),0),
    case
      when p.id = auth.uid() then coalesce(p.online_now,false)
      when coalesce((select us.show_online_status from public.user_settings us where us.user_id = p.id), true) and p.presence_visibility = 'PUBLIC' then coalesce(p.online_now,false)
      when coalesce((select us.show_online_status from public.user_settings us where us.user_id = p.id), true) and p.presence_visibility = 'FOLLOWERS' and exists(
        select 1 from public.follows f
        where f.follower_id = auth.uid() and f.following_id = p.id
      ) then coalesce(p.online_now,false)
      else false
    end,
    case
      when p.id = auth.uid() then coalesce(p.is_online,false)
      when coalesce((select us.show_online_status from public.user_settings us where us.user_id = p.id), true) and p.presence_visibility = 'PUBLIC' then coalesce(p.is_online,false)
      when coalesce((select us.show_online_status from public.user_settings us where us.user_id = p.id), true) and p.presence_visibility = 'FOLLOWERS' and exists(
        select 1 from public.follows f
        where f.follower_id = auth.uid() and f.following_id = p.id
      ) then coalesce(p.is_online,false)
      else false
    end,
    case
      when p.id = auth.uid() then p.last_seen_at
      when coalesce((select us.show_online_status from public.user_settings us where us.user_id = p.id), true) and p.presence_visibility = 'PUBLIC' then p.last_seen_at
      when coalesce((select us.show_online_status from public.user_settings us where us.user_id = p.id), true) and p.presence_visibility = 'FOLLOWERS' and exists(
        select 1 from public.follows f
        where f.follower_id = auth.uid() and f.following_id = p.id
      ) then p.last_seen_at
      else null
    end,
    greatest(coalesce(p.points,0),0),
    greatest(coalesce(p.total_xp,0),0),
    greatest(coalesce(p.xp_level,1),1),
    p.created_at,
    p.blink_vip_until,
    p.verified_at,
    greatest(coalesce(p.daily_streak,0),0),
    greatest(coalesce(p.world_rank,0),0),
    greatest(coalesce(p.campus_rank,0),0)
  from public.profiles p, q
  where auth.uid() is not null
    and q.term <> ''
    and nullif(trim(p.username),'') is not null
    and (
      lower(coalesce(p.username,'')) like '%' || q.term || '%'
      or lower(coalesce(p.full_name,'')) like '%' || q.term || '%'
      or lower(coalesce(p.university,'')) like '%' || q.term || '%'
      or lower(coalesce(p.faculty,'')) like '%' || q.term || '%'
      or lower(coalesce(p.department,'')) like '%' || q.term || '%'
    )
    and (
      p_after_username is null
      or lower(p.username) > lower(p_after_username)
      or (lower(p.username) = lower(p_after_username) and (p_after_id is null or p.id > p_after_id))
    )
    and not exists (
      select 1 from public.user_settings private_settings
      where private_settings.user_id=p.id and coalesce(private_settings.private_account,false)=true
    )
    and not exists (
      select 1 from public.blocks b
      where (b.blocker_id = auth.uid() and b.blocked_id = p.id)
         or (b.blocker_id = p.id and b.blocked_id = auth.uid())
    )
  order by lower(p.username), p.id
  limit greatest(1, least(coalesce(p_limit,30),60));
$$;
revoke all on function public.search_profiles_page(text,integer,text,uuid) from public, anon;

create or replace function public.mark_all_my_notifications_read_atomic() returns jsonb
language plpgsql security definer set search_path=public,pg_temp
as $$
declare v_user uuid:=auth.uid(); v_notifications integer:=0; v_activities integer:=0;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  update public.notifications set is_read=true where user_id=v_user and is_read=false; get diagnostics v_notifications=row_count;
  update public.activities set is_read=true where recipient_id=v_user and is_read=false; get diagnostics v_activities=row_count;
  return jsonb_build_object('notifications',v_notifications,'activities',v_activities);
end $$;
revoke all on function public.mark_all_my_notifications_read_atomic() from public,anon;
grant execute on function public.mark_all_my_notifications_read_atomic() to authenticated;

create or replace function public.purchase_blink_item(p_catalog_id text,p_quantity integer default 1,p_boost_multiplier integer default 1) returns jsonb
language plpgsql security definer set search_path=''
as $$
declare v_user uuid:=auth.uid(); v_item public.blink_store_catalog%rowtype; v_balance bigint; v_unit integer; v_total bigint; v_inventory uuid; v_existing uuid; begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if private.blink_store_is_paused() then raise exception 'STORE_PAUSED'; end if;
  if p_quantity<1 or p_quantity>20 then raise exception 'INVALID_QUANTITY'; end if;
  select * into v_item from public.blink_store_catalog where id=p_catalog_id and is_active=true;
  if not found then raise exception 'ITEM_NOT_FOUND'; end if;
  if v_item.vip_only and not private.is_blink_vip_id(v_user,now()) then raise exception 'VIP_REQUIRED'; end if;
  if v_item.item_type='PERMANENT' and p_quantity<>1 then raise exception 'PERMANENT_ITEM_QUANTITY_ONE'; end if;
  if v_item.item_type='PERMANENT' and exists(select 1 from public.blink_inventory where user_id=v_user and catalog_id=v_item.id and status='PERMANENT') then raise exception 'ALREADY_OWNED'; end if;
  if cardinality(v_item.boost_multipliers)>0 and not (p_boost_multiplier=any(v_item.boost_multipliers)) then raise exception 'INVALID_BOOST_MULTIPLIER'; end if;
  if cardinality(v_item.boost_multipliers)=0 then p_boost_multiplier:=1; end if;
  v_unit:=private.blink_effective_price(v_user,v_item,p_boost_multiplier); v_total:=v_unit::bigint*p_quantity;
  insert into public.user_balances(user_id,spendable_coin_balance) values(v_user,0) on conflict(user_id) do nothing;
  select spendable_coin_balance into v_balance from public.user_balances where user_id=v_user for update;
  if v_balance<v_total then raise exception 'INSUFFICIENT_BLINK_COINS'; end if;
  update public.user_balances set spendable_coin_balance=spendable_coin_balance-v_total,updated_at=now() where user_id=v_user returning spendable_coin_balance into v_balance;
  if v_item.stackable and v_item.item_type<>'PERMANENT' then
    select id into v_existing from public.blink_inventory where user_id=v_user and catalog_id=v_item.id and status='AVAILABLE' and coalesce(boost_multiplier,1)=p_boost_multiplier and gifted_by is null order by purchased_at limit 1 for update;
  end if;
  if v_existing is not null then update public.blink_inventory set quantity=quantity+p_quantity,updated_at=now() where id=v_existing returning id into v_inventory;
  else insert into public.blink_inventory(user_id,catalog_id,quantity,status,boost_multiplier) values(v_user,v_item.id,p_quantity,case when v_item.item_type='PERMANENT' then 'PERMANENT' else 'AVAILABLE' end,case when cardinality(v_item.boost_multipliers)>0 then p_boost_multiplier else null end) returning id into v_inventory; end if;
  insert into public.blink_coin_transactions(user_id,kind,catalog_id,item_name,amount,balance_after,metadata) values(v_user,'PURCHASE',v_item.id,v_item.name,-v_total,v_balance,jsonb_build_object('quantity',p_quantity,'unit_price',v_unit,'boost_multiplier',p_boost_multiplier));
  return jsonb_build_object('success',true,'message','Purchased','balance',v_balance,'inventory_id',v_inventory,'charged',v_total);
end $$;

grant execute on function public.purchase_blink_item(text,integer,integer) to authenticated;
