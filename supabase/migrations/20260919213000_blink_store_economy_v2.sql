-- BLINK Store Economy v2
-- Rebalances the Store around 7-day / 30-day / permanent value tiers,
-- makes paid BLINK Verified a 30-day renewable entitlement,
-- and adds server-authoritative Wishlist + Saved Looks foundations.
-- Risky production-affecting change: validate on Testlab/staging before promotion.
-- destructive-change-reviewed
-- rollback-plan: restore Store catalog names/prices/durations and the prior VIP/verification/store-state functions from their preceding migrations; unschedule blink-verification-expiry; then drop blink_store_wishlist, blink_saved_look_items, blink_saved_looks and their new RPCs only after confirming no retained user data is required.

-- 1) Store pricing and duration normalization.
with pricing(id, name, price, duration_seconds) as (
  values
    ('profile_highlight_1h','Profile Aura — 7 Days',300,604800),
    ('comment_highlight','Comment Spotlight — 7 Days',150,604800),
    ('comment_color','Aurora Comment Style',500,null),
    ('animated_like','Animated Like Effect',250,null),
    ('profile_glow_1h','Profile Glow — 7 Days',350,604800),
    ('chat_bubble_theme','Custom Chat Bubble Theme',500,null),
    ('reaction_pack','Special Reaction Pack',400,null),
    ('profile_ring','Custom Profile Ring',500,null),
    ('username_glow_24h','Username Glow — 7 Days',300,604800),
    ('post_border','Post Border Effect',150,null),
    ('story_highlight','Story Highlight Effect',80,null),
    ('profile_background','Profile Background Theme',650,null),
    ('emoji_pack','Exclusive Emoji Pack',350,null),
    ('animated_profile_ring','Animated Profile Ring',750,null),
    ('chat_background','Chat Background Theme',600,null),
    ('profile_entrance_animation','Profile Entrance Animation',700,null),
    ('post_highlight_1h','Post Highlight — 7 Days',300,604800),
    ('reel_highlight_1h','Reel Highlight — 7 Days',300,604800),
    ('visitor_insights_24h','Profile Visitor Insights — 7 Days',350,604800),
    ('notification_sound_pack','Notification Sound Pack',250,null),
    ('app_icon_pack','Custom App Icon Pack',300,null),
    ('profile_spotlight_1h','Profile Spotlight — 7 Days',550,604800),
    ('username_font','Signature Nameplate',450,null),
    ('sticker_pack','Premium Sticker Pack',400,null),
    ('digital_gift','Digital Gift',80,null),
    ('post_boost','Post Boost — 6 Hours',150,21600),
    ('reel_boost','Reel Boost — 6 Hours',150,21600),
    ('market_listing_highlight','Marketplace Listing Highlight — 7 Days',300,604800),
    ('profile_discovery_boost','Profile Discovery Boost — 6 Hours',200,21600),
    ('custom_profile_badge','Custom Profile Badge',700,null),
    ('profile_theme_3d','Premium Profile Theme — 30 Days',1000,2592000),
    ('animated_name','Living Nameplate',850,null),
    ('special_dm_theme','Special DM Theme',800,null),
    ('post_spotlight_6h','Post Spotlight — 7 Days',500,604800),
    ('reel_spotlight_6h','Reel Spotlight — 7 Days',500,604800),
    ('market_seller_spotlight','Marketplace Seller Spotlight — 7 Days',500,604800),
    ('profile_spotlight_24h','Profile Spotlight Plus — 30 Days',1200,2592000),
    ('post_boost_plus','Post Boost Plus — 2× / 6 Hours',250,21600),
    ('reel_boost_plus','Reel Boost Plus — 2× / 6 Hours',250,21600),
    ('vip_theme','Exclusive VIP Theme',400,null),
    ('profile_glow_7d','Premium Profile Glow — 7 Days',500,604800),
    ('premium_profile_frame','Premium Profile Frame',1000,null),
    ('creator_badge','Premium Creator Badge',1200,null),
    ('post_spotlight_24h','Post Spotlight Plus — 30 Days',1200,2592000),
    ('reel_spotlight_24h','Reel Spotlight Plus — 30 Days',1200,2592000),
    ('discovery_boost_7d','7-Day Discovery Boost',900,604800),
    ('profile_theme_bundle','Premium Profile Theme Bundle',1600,null),
    ('creator_promo_bundle','Creator Promotion Bundle',900,null),
    ('market_promo_bundle','Marketplace Promotion Bundle',700,null),
    ('blink_vip_10d','Blink VIP — 30 Days',1200,2592000),
    ('super_reaction','Super Reaction',300,null),
    ('profile_banner','Premium Profile Banner',800,null),
    ('avatar_decoration','Avatar Decoration',800,null),
    ('reel_frame_effect','Reel Frame Effect',150,null),
    ('post_entrance_animation','Post Entrance Animation',150,null),
    ('profile_particle_effect','Profile Particle Effect',900,null),
    ('comment_entrance_animation','Comment Premiere — 7 Days',150,604800),
    ('follow_animation','Exclusive Follow Animation',400,null),
    ('birthday_profile_theme','Birthday Profile Theme — 7 Days',350,604800),
    ('limited_edition_badge','Limited Edition Badge',1500,null),
    ('gift_crown','Gift Crown Collectible',700,null),
    ('gift_rose','Gift Rose Collectible',200,null),
    ('gift_trophy','Gift Trophy Collectible',500,null),
    ('gift_galaxy','Gift Galaxy Collectible',1500,null),
    ('profile_music_theme','Profile Music Theme',1000,null),
    ('creator_intro_card','Creator Intro Card',1200,null),
    ('premium_poll_style','Premium Poll Style',500,null),
    ('vip_comment_effect','VIP Comment Effect',300,null),
    ('vip_reaction_pack','VIP Reaction Pack',400,null),
    ('vip_profile_entrance','VIP Profile Entrance',700,null)
)
update public.blink_store_catalog c
set name = p.name,
    price = p.price,
    duration_seconds = p.duration_seconds,
    metadata = coalesce(c.metadata,'{}'::jsonb) || jsonb_build_object(
      'preview_required', true,
      'economy_version', 2,
      'duration_tier',
        case
          when p.duration_seconds is null then
            case when c.item_type='PERMANENT' then 'PERMANENT' else 'SINGLE_USE' end
          when p.duration_seconds >= 2592000 then '30_DAYS'
          when p.duration_seconds >= 604800 then '7_DAYS'
          else 'SHORT_DISTRIBUTION'
        end
    ),
    updated_at = now()
from pricing p
where c.id = p.id;


-- Keep server descriptions aligned with the visible duration names.
with descriptions(id, description) as (
  values
    ('profile_highlight_1h','Transform your complete public profile header with an animated aura, avatar light and coordinated identity accents for seven days.'),
    ('comment_highlight','Transform one selected comment into a complete premium card with an animated edge, avatar accent and reaction glow for seven days.'),
    ('profile_glow_1h','Add a premium animated glow around your profile identity for seven days.'),
    ('username_glow_24h','Make your display name glow on supported identity surfaces for seven days.'),
    ('username_font','Unlock a permanent signature nameplate treatment across supported profile, feed, comment, search and message identity surfaces.'),
    ('animated_name','Unlock a permanent animated nameplate treatment across supported public identity surfaces.'),
    ('post_highlight_1h','Visually highlight one selected post for seven days.'),
    ('reel_highlight_1h','Visually highlight one selected Reel for seven days.'),
    ('visitor_insights_24h','Unlock private aggregate profile-visitor analytics for seven days while ownership remains visible in your Blink Collection.'),
    ('profile_spotlight_1h','Place your profile in eligible Spotlight discovery surfaces for seven days.'),
    ('market_listing_highlight','Give one Marketplace listing a premium highlighted card and featured treatment for seven days.'),
    ('profile_theme_3d','Transform your public profile with a premium coordinated theme for thirty days.'),
    ('post_spotlight_6h','Place one selected post in eligible Spotlight surfaces for seven days.'),
    ('reel_spotlight_6h','Place one selected Reel in eligible Reel Spotlight surfaces for seven days.'),
    ('market_seller_spotlight','Feature your Marketplace seller storefront for seven days.'),
    ('profile_spotlight_24h','Keep your profile prominently featured in eligible discovery surfaces for thirty days.'),
    ('post_spotlight_24h','Prominently feature one selected post for thirty days.'),
    ('reel_spotlight_24h','Prominently feature one selected Reel for thirty days.'),
    ('comment_entrance_animation','Give one selected comment a polished premium entrance and complete highlighted surface for seven days.'),
    ('birthday_profile_theme','Activate a celebration profile theme visible for seven days.'),
    ('blink_vip_10d','Thirty days of VIP identity styling, Store discount, analytics and claimable promotion benefits.')
)
update public.blink_store_catalog c
set description=d.description,updated_at=now()
from descriptions d
where c.id=d.id;

-- Every sellable item must have a preview contract.
update public.blink_store_catalog
set metadata = coalesce(metadata,'{}'::jsonb) || jsonb_build_object('preview_required',true,'economy_version',2),
    updated_at = now()
where is_active = true;

-- 2) Saved Looks: capture and re-apply owned permanent cosmetics.
create table if not exists public.blink_saved_looks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  name text not null check(char_length(btrim(name)) between 1 and 40),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.blink_saved_look_items (
  look_id uuid not null references public.blink_saved_looks(id) on delete cascade,
  slot text not null check(char_length(slot) between 1 and 80),
  inventory_id uuid not null references public.blink_inventory(id) on delete cascade,
  catalog_id text not null references public.blink_store_catalog(id),
  primary key (look_id, slot)
);

create index if not exists blink_saved_looks_user_updated_idx
  on public.blink_saved_looks(user_id, updated_at desc);

alter table public.blink_saved_looks enable row level security;
alter table public.blink_saved_look_items enable row level security;

revoke all on public.blink_saved_looks from anon, authenticated;
revoke all on public.blink_saved_look_items from anon, authenticated;
grant select on public.blink_saved_looks to authenticated;
grant select on public.blink_saved_look_items to authenticated;

drop policy if exists blink_saved_looks_own_read on public.blink_saved_looks;
create policy blink_saved_looks_own_read
on public.blink_saved_looks for select to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists blink_saved_look_items_own_read on public.blink_saved_look_items;
create policy blink_saved_look_items_own_read
on public.blink_saved_look_items for select to authenticated
using (exists (
  select 1 from public.blink_saved_looks l
  where l.id=look_id and l.user_id=(select auth.uid())
));

create or replace function public.save_current_blink_look(p_name text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_name text := left(btrim(coalesce(p_name,'')),40);
  v_look uuid;
  v_count integer;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if v_name='' then raise exception 'LOOK_NAME_REQUIRED'; end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text)::bigint);

  select count(*) into v_count from public.blink_saved_looks where user_id=v_user;
  if v_count >= 8 then raise exception 'SAVED_LOOK_LIMIT_REACHED'; end if;

  if not exists(select 1 from public.blink_equipped_items where user_id=v_user) then
    raise exception 'NO_EQUIPPED_ITEMS';
  end if;

  insert into public.blink_saved_looks(user_id,name)
  values(v_user,v_name)
  returning id into v_look;

  insert into public.blink_saved_look_items(look_id,slot,inventory_id,catalog_id)
  select v_look,e.slot,e.inventory_id,e.catalog_id
  from public.blink_equipped_items e
  join public.blink_inventory i on i.id=e.inventory_id and i.user_id=v_user
  where e.user_id=v_user and i.status='PERMANENT';

  return jsonb_build_object('success',true,'look_id',v_look,'name',v_name);
end
$$;
revoke all on function public.save_current_blink_look(text) from public, anon;
grant execute on function public.save_current_blink_look(text) to authenticated;

create or replace function public.apply_blink_saved_look(p_look_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  r record;
  v_applied integer := 0;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if not exists(select 1 from public.blink_saved_looks where id=p_look_id and user_id=v_user) then
    raise exception 'LOOK_NOT_FOUND';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text)::bigint);

  for r in
    select li.slot,li.inventory_id,li.catalog_id,c.vip_only
    from public.blink_saved_look_items li
    join public.blink_saved_looks l on l.id=li.look_id
    join public.blink_inventory i on i.id=li.inventory_id and i.user_id=v_user and i.status='PERMANENT'
    join public.blink_store_catalog c on c.id=li.catalog_id and c.is_active=true
    where li.look_id=p_look_id and l.user_id=v_user
  loop
    if not r.vip_only or private.is_blink_vip_id(v_user,now()) then
      insert into public.blink_equipped_items(user_id,slot,inventory_id,catalog_id)
      values(v_user,r.slot,r.inventory_id,r.catalog_id)
      on conflict(user_id,slot) do update
        set inventory_id=excluded.inventory_id,
            catalog_id=excluded.catalog_id,
            equipped_at=now();
      v_applied := v_applied + 1;
    end if;
  end loop;

  update public.blink_saved_looks set updated_at=now() where id=p_look_id;
  return jsonb_build_object('success',true,'applied',v_applied);
end
$$;
revoke all on function public.apply_blink_saved_look(uuid) from public, anon;
grant execute on function public.apply_blink_saved_look(uuid) to authenticated;

create or replace function public.delete_blink_saved_look(p_look_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  delete from public.blink_saved_looks where id=p_look_id and user_id=v_user;
  if not found then raise exception 'LOOK_NOT_FOUND'; end if;
  return jsonb_build_object('success',true);
end
$$;
revoke all on function public.delete_blink_saved_look(uuid) from public, anon;
grant execute on function public.delete_blink_saved_look(uuid) to authenticated;

-- 3) Wishlist.
create table if not exists public.blink_store_wishlist (
  user_id uuid not null references public.profiles(id) on delete cascade,
  catalog_id text not null references public.blink_store_catalog(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(user_id,catalog_id)
);

alter table public.blink_store_wishlist enable row level security;
revoke all on public.blink_store_wishlist from anon, authenticated;
grant select on public.blink_store_wishlist to authenticated;

drop policy if exists blink_store_wishlist_own_read on public.blink_store_wishlist;
create policy blink_store_wishlist_own_read
on public.blink_store_wishlist for select to authenticated
using ((select auth.uid()) = user_id);

create or replace function public.set_blink_wishlist_item(p_catalog_id text,p_enabled boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare v_user uuid:=auth.uid();
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if not exists(select 1 from public.blink_store_catalog where id=p_catalog_id and is_active=true) then
    raise exception 'ITEM_NOT_FOUND';
  end if;
  if p_enabled then
    insert into public.blink_store_wishlist(user_id,catalog_id)
    values(v_user,p_catalog_id)
    on conflict do nothing;
  else
    delete from public.blink_store_wishlist where user_id=v_user and catalog_id=p_catalog_id;
  end if;
  return jsonb_build_object('success',true,'catalog_id',p_catalog_id,'wishlisted',p_enabled);
end
$$;
revoke all on function public.set_blink_wishlist_item(text,boolean) from public, anon;
grant execute on function public.set_blink_wishlist_item(text,boolean) to authenticated;

-- 4) VIP is now a 30-day pass while preserving the existing catalog id for compatibility.
create or replace function private.activate_blink_vip_pass(
  p_user uuid,
  p_inventory uuid,
  p_gifted_by uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_base timestamptz;
  v_pass uuid;
  v_auto boolean:=false;
begin
  select greatest(now(),coalesce(max(expires_at),now()))
    into v_base
  from public.blink_vip_passes
  where user_id=p_user and expires_at>now();

  select coalesce((
    select auto_renew from public.blink_vip_passes
    where user_id=p_user order by expires_at desc limit 1
  ),false) into v_auto;

  insert into public.blink_vip_passes(
    user_id,inventory_id,starts_at,expires_at,auto_renew,gifted_by
  )
  values(p_user,p_inventory,v_base,v_base+interval '30 days',v_auto,p_gifted_by)
  returning id into v_pass;

  insert into public.blink_vip_benefit_balances(pass_id,user_id)
  values(v_pass,p_user);

  update public.profiles
  set blink_vip_until=v_base+interval '30 days'
  where id=p_user;

  return v_pass;
end
$$;
revoke all on function private.activate_blink_vip_pass(uuid,uuid,uuid) from public,anon,authenticated;

-- Keep VIP renewal and gifting copy aligned with the new 30-day product.
create or replace function public.renew_blink_vip()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid:=auth.uid();
  v_item public.blink_store_catalog%rowtype;
  v_price integer;
  v_balance bigint;
  v_pass uuid;
  v_exp timestamptz;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select * into v_item from public.blink_store_catalog where id='blink_vip_10d' and is_active=true;
  if not found then raise exception 'VIP_ITEM_NOT_FOUND'; end if;
  v_price:=private.blink_effective_price(v_user,v_item,1);

  insert into public.user_balances(user_id,spendable_coin_balance)
  values(v_user,0) on conflict(user_id) do nothing;
  select spendable_coin_balance into v_balance from public.user_balances where user_id=v_user for update;
  if v_balance<v_price then raise exception 'INSUFFICIENT_BLINK_COINS'; end if;

  update public.user_balances
  set spendable_coin_balance=spendable_coin_balance-v_price,updated_at=now()
  where user_id=v_user
  returning spendable_coin_balance into v_balance;

  v_pass:=private.activate_blink_vip_pass(v_user,null,null);
  select expires_at into v_exp from public.blink_vip_passes where id=v_pass;

  insert into public.blink_coin_transactions(user_id,kind,catalog_id,item_name,amount,balance_after)
  values(v_user,'VIP_RENEWAL','blink_vip_10d','Blink VIP — 30 Days',-v_price,v_balance);

  return jsonb_build_object('success',true,'balance',v_balance,'expires_at',v_exp,'pass_id',v_pass);
end
$$;
revoke all on function public.renew_blink_vip() from public,anon;
grant execute on function public.renew_blink_vip() to authenticated;

create or replace function public.gift_blink_vip(p_recipient_username text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid:=auth.uid();
  v_recipient uuid;
  v_item public.blink_store_catalog%rowtype;
  v_price integer;
  v_balance bigint;
  v_inv uuid;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  select id into v_recipient from public.profiles
   where lower(username)=lower(trim(leading '@' from coalesce(p_recipient_username,''))) limit 1;
  if v_recipient is null or v_recipient=v_user then raise exception 'INVALID_RECIPIENT'; end if;

  select * into v_item from public.blink_store_catalog where id='blink_vip_10d' and is_active=true;
  v_price:=private.blink_effective_price(v_user,v_item,1);

  insert into public.user_balances(user_id,spendable_coin_balance)
  values(v_user,0) on conflict(user_id) do nothing;
  select spendable_coin_balance into v_balance from public.user_balances where user_id=v_user for update;
  if v_balance<v_price then raise exception 'INSUFFICIENT_BLINK_COINS'; end if;

  update public.user_balances
  set spendable_coin_balance=spendable_coin_balance-v_price,updated_at=now()
  where user_id=v_user
  returning spendable_coin_balance into v_balance;

  insert into public.blink_inventory(user_id,catalog_id,quantity,status,gifted_by,metadata)
  values(v_recipient,'blink_vip_10d',1,'AVAILABLE',v_user,jsonb_build_object('gift',true))
  returning id into v_inv;

  insert into public.blink_coin_transactions(user_id,kind,catalog_id,item_name,amount,balance_after,metadata)
  values(v_user,'VIP_GIFT','blink_vip_10d','Blink VIP — 30 Days gift',-v_price,v_balance,jsonb_build_object('recipient_id',v_recipient));

  insert into public.notifications(user_id,actor_id,type,text)
  values(v_recipient,v_user,'system','👑 You received Blink VIP — 30 Days. Open Blink Store → Collection to activate it.');

  return jsonb_build_object('success',true,'balance',v_balance,'recipient_id',v_recipient,'inventory_id',v_inv);
end
$$;
revoke all on function public.gift_blink_vip(text) from public,anon;
grant execute on function public.gift_blink_vip(text) to authenticated;

-- 5) Paid BLINK Verified is a 30-day renewable entitlement.
insert into private.blink_economy_config(key,int_value)
values ('blue_verification_valid_days',30)
on conflict(key) do update set int_value=excluded.int_value,updated_at=now();

create or replace function public.purchase_blink_blue_verification_with_coins()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid:=auth.uid();
  v_cost integer:=3000;
  v_days integer:=30;
  v_balance bigint;
  v_badge text;
  v_current_exp timestamptz;
  v_base timestamptz;
  v_exp timestamptz;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text)::bigint);

  select upper(coalesce(verification_badge,'NONE')),verification_expires_at
    into v_badge,v_current_exp
  from public.profiles
  where id=v_user
  for update;
  if not found then raise exception 'PROFILE_NOT_FOUND'; end if;

  -- Gold remains an owner/admin-managed tier and is not replaced by Blue.
  if v_badge='GOLD' and (v_current_exp is null or v_current_exp>now()) then
    select coalesce(floor(spendable_coin_balance),0)::bigint into v_balance
    from public.user_balances where user_id=v_user;
    return jsonb_build_object(
      'success',true,'already_verified',true,'cost',0,'balance',coalesce(v_balance,0),
      'badge','GOLD','expires_at',v_current_exp
    );
  end if;

  -- Legacy permanent Blue badges are grandfathered instead of charging unexpectedly.
  if v_badge='BLUE' and v_current_exp is null then
    select coalesce(floor(spendable_coin_balance),0)::bigint into v_balance
    from public.user_balances where user_id=v_user;
    return jsonb_build_object(
      'success',true,'already_verified',true,'grandfathered',true,'cost',0,
      'balance',coalesce(v_balance,0),'badge','BLUE','expires_at',null
    );
  end if;

  select coalesce(int_value,3000)::integer into v_cost
    from private.blink_economy_config where key='blue_verification_coin_cost';
  select coalesce(int_value,30)::integer into v_days
    from private.blink_economy_config where key='blue_verification_valid_days';

  insert into public.user_balances(user_id,spendable_coin_balance,updated_at)
  values(v_user,0,now()) on conflict(user_id) do nothing;

  select floor(spendable_coin_balance)::bigint into v_balance
  from public.user_balances where user_id=v_user for update;

  if coalesce(v_balance,0)<v_cost then raise exception 'INSUFFICIENT_BLINK_COINS'; end if;

  update public.user_balances
  set spendable_coin_balance=spendable_coin_balance-v_cost,updated_at=now()
  where user_id=v_user
  returning floor(spendable_coin_balance)::bigint into v_balance;

  perform set_config('blink.coin_sync_bypass','1',true);
  insert into public.game_profiles(user_id,coins,updated_at)
  values(v_user,v_balance,now())
  on conflict(user_id) do update set coins=excluded.coins,updated_at=now();
  perform set_config('blink.coin_sync_bypass','0',true);

  v_base := case
    when v_badge='BLUE' and v_current_exp is not null and v_current_exp>now()
      then v_current_exp
    else now()
  end;
  v_exp := v_base + make_interval(days=>greatest(1,least(v_days,365)));

  update public.profiles
  set verification_badge='BLUE',
      verification_tier='Standard'::public.verification_tier_enum,
      is_verified=true,
      verified_at=coalesce(verified_at,now()),
      verification_expires_at=v_exp,
      updated_at=now()
  where id=v_user;

  insert into public.blink_coin_transactions(user_id,kind,item_name,amount,balance_after,metadata)
  values(
    v_user,'VERIFICATION_PURCHASE','BLINK Verified — 30 Days',-v_cost,v_balance,
    jsonb_build_object('badge','BLUE','purchase_method','blink_coins','duration_days',v_days,'expires_at',v_exp)
  );

  return jsonb_build_object(
    'success',true,'already_verified',false,'renewed',v_badge='BLUE',
    'cost',v_cost,'balance',v_balance,'badge','BLUE',
    'duration_days',v_days,'expires_at',v_exp
  );
end
$$;
revoke all on function public.purchase_blink_blue_verification_with_coins() from public,anon;
grant execute on function public.purchase_blink_blue_verification_with_coins() to authenticated;

create or replace function private.expire_blink_verifications()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.profiles
  set verification_badge='NONE',
      verification_tier='None'::public.verification_tier_enum,
      is_verified=false,
      verification_expires_at=null,
      updated_at=now()
  where verification_expires_at is not null
    and verification_expires_at<=now()
    and upper(coalesce(verification_badge,'NONE')) in ('BLUE','GOLD');
end
$$;
revoke all on function private.expire_blink_verifications() from public,anon,authenticated;

do $$
declare v_job bigint;
begin
  for v_job in select jobid from cron.job where jobname='blink-verification-expiry' loop
    perform cron.unschedule(v_job);
  end loop;
  perform cron.schedule('blink-verification-expiry','11 * * * *',$job$select private.expire_blink_verifications();$job$);
end
$$;

-- Store collection schema is created before Store state references it.
create table if not exists public.blink_store_collections (
  id text primary key,
  name text not null,
  description text not null default '',
  collection_type text not null default 'STANDARD',
  scope_key text,
  available_from timestamptz,
  available_until timestamptz,
  is_active boolean not null default true,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (available_until is null or available_from is null or available_until > available_from)
);

alter table public.blink_store_collections enable row level security;
drop policy if exists blink_store_collections_read on public.blink_store_collections;
create policy blink_store_collections_read
on public.blink_store_collections for select to authenticated
using (is_active=true);
grant select on public.blink_store_collections to authenticated;
revoke insert,update,delete,truncate on public.blink_store_collections from anon,authenticated;

alter table public.blink_store_catalog
  add column if not exists collection_id text references public.blink_store_collections(id) on delete set null,
  add column if not exists rarity text not null default 'STANDARD',
  add column if not exists unlock_level integer,
  add column if not exists available_from timestamptz,
  add column if not exists available_until timestamptz;

-- 6) Extend Store state with Wishlist + Saved Looks without exposing other users' data.
create or replace function public.get_blink_store_state()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
with
me as (select auth.uid() uid),
pass as (
  select v.* from public.blink_vip_passes v,me
  where v.user_id=me.uid and v.starts_at<=now() and v.expires_at>now()
  order by v.expires_at desc limit 1
),
benefits as (
  select b.* from public.blink_vip_benefit_balances b join pass p on p.id=b.pass_id
),
latest as (
  select count(*)::int completed,
         coalesce(sum(extract(epoch from (expires_at-starts_at))/86400)::int,0) cumulative
  from public.blink_vip_passes v,me where v.user_id=me.uid
)
select jsonb_build_object(
  'balance',coalesce((select spendable_coin_balance from public.user_balances u,me where u.user_id=me.uid),0),
  'xp_level',coalesce((select p.xp_level from public.profiles p,me where p.id=me.uid),1),
  'collections',coalesce((
    select jsonb_agg(to_jsonb(sc) order by sc.name)
    from public.blink_store_collections sc
    where sc.is_active=true
  ),'[]'::jsonb),
  'catalog',coalesce((
    select jsonb_agg(to_jsonb(c) order by sort_order)
    from public.blink_store_catalog c where c.is_active
  ),'[]'::jsonb),
  'inventory',coalesce((
    select jsonb_agg(to_jsonb(i)||jsonb_build_object('name',c.name,'icon_key',c.icon_key) order by i.purchased_at desc)
    from public.blink_inventory i
    join public.blink_store_catalog c on c.id=i.catalog_id,me
    where i.user_id=me.uid
  ),'[]'::jsonb),
  'transactions',coalesce((
    select jsonb_agg(to_jsonb(t) order by t.created_at desc)
    from (
      select
        x.*,
        c.duration_seconds as catalog_duration_seconds,
        c.item_type as catalog_item_type,
        (
          select i.status
          from public.blink_inventory i
          where i.user_id=me.uid and i.catalog_id=x.catalog_id
          order by i.purchased_at desc
          limit 1
        ) as current_inventory_status,
        (
          select i.activated_at
          from public.blink_inventory i
          where i.user_id=me.uid and i.catalog_id=x.catalog_id
          order by i.purchased_at desc
          limit 1
        ) as current_inventory_activated_at,
        (
          select i.expires_at
          from public.blink_inventory i
          where i.user_id=me.uid and i.catalog_id=x.catalog_id
          order by i.purchased_at desc
          limit 1
        ) as current_inventory_expires_at
      from public.blink_coin_transactions x
      cross join me
      left join public.blink_store_catalog c on c.id=x.catalog_id
      where x.user_id=me.uid
      order by x.created_at desc
      limit 100
    ) t
  ),'[]'::jsonb),
  'vip',jsonb_build_object(
    'active',exists(select 1 from pass),
    'expires_at',(select expires_at from pass),
    'remaining_seconds',greatest(0,coalesce(extract(epoch from ((select expires_at from pass)-now()))::bigint,0)),
    'completed_passes',(select completed from latest),
    'cumulative_vip_days',(select cumulative from latest),
    'auto_renew',coalesce((select auto_renew from pass),false),
    'post_boosts_2x',coalesce((select post_boosts_2x from benefits),0),
    'reel_boosts_2x',coalesce((select reel_boosts_2x from benefits),0),
    'profile_spotlights',coalesce((select profile_spotlights from benefits),0),
    'post_spotlights',coalesce((select post_spotlights from benefits),0),
    'reel_spotlights',coalesce((select reel_spotlights from benefits),0),
    'marketplace_highlights',coalesce((select marketplace_highlights from benefits),0),
    'daily_coin_bonus_claimed',exists(
      select 1 from public.blink_vip_claims q,me
      where q.user_id=me.uid and q.claim_key='daily_coin_bonus' and q.claim_date=current_date
    )
  ),
  'active_boosts',coalesce((
    select jsonb_agg(to_jsonb(b) order by b.ends_at)
    from public.blink_boosts b,me
    where b.user_id=me.uid and b.status='ACTIVE' and b.ends_at>now()
  ),'[]'::jsonb),
  'equipped',coalesce((
    select jsonb_agg(to_jsonb(e))
    from public.blink_equipped_items e,me where e.user_id=me.uid
  ),'[]'::jsonb),
  'wishlist',coalesce((
    select jsonb_agg(w.catalog_id order by w.created_at desc)
    from public.blink_store_wishlist w,me where w.user_id=me.uid
  ),'[]'::jsonb),
  'saved_looks',coalesce((
    select jsonb_agg(
      jsonb_build_object(
        'id',l.id,
        'name',l.name,
        'updated_at',l.updated_at,
        'item_count',(select count(*) from public.blink_saved_look_items li where li.look_id=l.id)
      )
      order by l.updated_at desc
    )
    from public.blink_saved_looks l,me where l.user_id=me.uid
  ),'[]'::jsonb)
) where (select uid from me) is not null;
$$;
revoke all on function public.get_blink_store_state() from public,anon;
grant execute on function public.get_blink_store_state() to authenticated;


-- 7) Economy status exposes the server-controlled monthly verification term.
create or replace function public.get_blink_economy_status()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_base integer;
  v_limit integer;
  v_cash integer;
  v_verify_coins integer;
  v_verify_days integer;
  v_cash_enabled boolean;
  v_milestones jsonb;
  v_packs jsonb;
  v_ads_today integer;
  v_earned_today bigint;
  v_balance bigint;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;

  select coalesce(int_value,10)::integer into v_base
    from private.blink_economy_config where key='rewarded_ad_base_coins';
  select coalesce(int_value,15)::integer into v_limit
    from private.blink_economy_config where key='rewarded_ad_daily_limit';
  select coalesce(int_value,800)::integer into v_cash
    from private.blink_economy_config where key='blue_verification_cash_ngn';
  select coalesce(int_value,3000)::integer into v_verify_coins
    from private.blink_economy_config where key='blue_verification_coin_cost';
  select coalesce(int_value,30)::integer into v_verify_days
    from private.blink_economy_config where key='blue_verification_valid_days';
  select coalesce(bool_value,false) into v_cash_enabled
    from private.blink_economy_config where key='cash_checkout_enabled';
  select coalesce(json_value,'[]'::jsonb) into v_milestones
    from private.blink_economy_config where key='rewarded_milestones';
  select coalesce(json_value,'[]'::jsonb) into v_packs
    from private.blink_economy_config where key='coin_packs';

  select count(*)::integer,coalesce(sum(credited_amount),0)::bigint
    into v_ads_today,v_earned_today
    from public.blink_rewarded_ad_claims
   where user_id=v_user
     and rewarded_at >= date_trunc('day',now() at time zone 'UTC') at time zone 'UTC';

  select coalesce(floor(spendable_coin_balance),0)::bigint into v_balance
    from public.user_balances where user_id=v_user;

  return jsonb_build_object(
    'rewarded_ad_base_coins',coalesce(v_base,10),
    'rewarded_ad_daily_limit',coalesce(v_limit,15),
    'rewarded_milestones',coalesce(v_milestones,'[]'::jsonb),
    'blue_verification_cash_ngn',coalesce(v_cash,800),
    'blue_verification_coin_cost',coalesce(v_verify_coins,3000),
    'blue_verification_valid_days',coalesce(v_verify_days,30),
    'coin_packs',coalesce(v_packs,'[]'::jsonb),
    'cash_checkout_enabled',coalesce(v_cash_enabled,false),
    'ads_today',coalesce(v_ads_today,0),
    'coins_earned_from_ads_today',coalesce(v_earned_today,0),
    'balance',coalesce(v_balance,0)
  );
end
$$;
revoke all on function public.get_blink_economy_status() from public, anon;
grant execute on function public.get_blink_economy_status() to authenticated;


-- 8) Activation anti-abuse: duplicate effects do not stack, active distribution boosts cannot overlap,
-- and the same post/reel gets a two-hour cooldown after a distribution boost ends.
create or replace function public.activate_blink_item(p_inventory_id uuid,p_target_id uuid default null) returns jsonb
language plpgsql security definer set search_path=''
as $$
declare v_user uuid:=auth.uid(); v_inv public.blink_inventory%rowtype; v_item public.blink_store_catalog%rowtype; v_active_id uuid; v_expires timestamptz; v_target_type text; v_boost uuid; begin
 if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
 select * into v_inv from public.blink_inventory where id=p_inventory_id and user_id=v_user for update;
 if not found then raise exception 'INVENTORY_NOT_FOUND'; end if;
 if v_inv.status<>'AVAILABLE' then raise exception 'ITEM_NOT_AVAILABLE'; end if;
 select * into v_item from public.blink_store_catalog where id=v_inv.catalog_id and is_active=true;
 if not found then raise exception 'ITEM_NOT_FOUND'; end if;
 if v_item.vip_only and not private.is_blink_vip_id(v_user,now()) then raise exception 'VIP_REQUIRED'; end if;
 v_target_type:=v_item.target_type;
 if v_item.item_type='PERMANENT' then raise exception 'USE_EQUIP_FOR_PERMANENT'; end if;
 if v_item.item_type='CONTENT_SPECIFIC' and p_target_id is null then raise exception 'TARGET_REQUIRED'; end if;
 if v_target_type in ('POST','REEL') then
   if not exists(select 1 from public.feed_posts p where p.id=p_target_id and p.user_id=v_user and coalesce(p.is_reel,false)=(v_target_type='REEL')) then raise exception 'INVALID_CONTENT_TARGET'; end if;
 elsif v_target_type='COMMENT' then
   if not exists(select 1 from public.comments c where c.id=p_target_id and c.author_id=v_user) then raise exception 'INVALID_COMMENT_TARGET'; end if;
 elsif v_target_type='MARKETPLACE' and p_target_id is not null then
   if not exists(select 1 from public.market_items m where m.id=p_target_id and m.seller_id=v_user) then raise exception 'INVALID_MARKET_TARGET'; end if;
 end if;

 -- Do not let repeat purchases turn into stacked distribution or duplicate visual effects.
 if exists(
   select 1 from public.blink_item_activations a
   where a.user_id=v_user
     and a.catalog_id=v_item.id
     and a.target_type is not distinct from v_target_type
     and a.target_id is not distinct from p_target_id
     and a.ended_at is null
     and (a.expires_at is null or a.expires_at>now())
 ) then
   raise exception 'EFFECT_ALREADY_ACTIVE';
 end if;

 if v_item.id in ('post_boost','reel_boost','post_boost_plus','reel_boost_plus') then
   if exists(
     select 1 from public.blink_boosts b
     where b.user_id=v_user
       and b.content_id=p_target_id
       and b.content_type=v_target_type
       and b.status='ACTIVE'
       and b.ends_at>now()
   ) then
     raise exception 'BOOST_ALREADY_ACTIVE';
   end if;
   if exists(
     select 1 from public.blink_boosts b
     where b.user_id=v_user
       and b.content_id=p_target_id
       and b.content_type=v_target_type
       and b.ends_at<=now()
       and b.ends_at>now()-interval '2 hours'
   ) then
     raise exception 'BOOST_COOLDOWN';
   end if;
 end if;

 if v_item.id in (
   'profile_spotlight_1h','profile_spotlight_24h',
   'post_spotlight_6h','post_spotlight_24h',
   'reel_spotlight_6h','reel_spotlight_24h',
   'market_listing_highlight','market_seller_spotlight'
 ) then
   if exists(
     select 1 from public.blink_item_activations a
     where a.user_id=v_user
       and a.target_type is not distinct from v_target_type
       and a.target_id is not distinct from p_target_id
       and a.ended_at is null
       and (a.expires_at is null or a.expires_at>now())
       and (
         (v_item.id like 'profile_spotlight_%' and a.catalog_id like 'profile_spotlight_%')
         or (v_item.id like 'post_spotlight_%' and a.catalog_id like 'post_spotlight_%')
         or (v_item.id like 'reel_spotlight_%' and a.catalog_id like 'reel_spotlight_%')
         or (v_item.id='market_listing_highlight' and a.catalog_id='market_listing_highlight')
         or (v_item.id='market_seller_spotlight' and a.catalog_id='market_seller_spotlight')
       )
   ) then
     raise exception 'SPOTLIGHT_ALREADY_ACTIVE';
   end if;
 end if;

 v_expires:=case when v_item.duration_seconds is not null then now()+make_interval(secs=>v_item.duration_seconds::double precision) else null end;
 if v_inv.quantity>1 then
   update public.blink_inventory set quantity=quantity-1,updated_at=now() where id=v_inv.id;
   insert into public.blink_inventory(user_id,catalog_id,quantity,status,purchased_at,activated_at,expires_at,target_type,target_id,boost_multiplier,gifted_by,metadata)
   values(v_user,v_inv.catalog_id,1,case when v_item.item_type='PASS' or v_expires is not null then 'ACTIVE' else 'USED' end,v_inv.purchased_at,now(),v_expires,v_target_type,p_target_id,v_inv.boost_multiplier,v_inv.gifted_by,v_inv.metadata) returning id into v_active_id;
 else
   v_active_id:=v_inv.id;
   update public.blink_inventory set status=case when v_item.item_type='PASS' or v_expires is not null then 'ACTIVE' else 'USED' end,activated_at=now(),expires_at=v_expires,target_type=v_target_type,target_id=p_target_id,updated_at=now() where id=v_inv.id;
 end if;
 if v_item.item_type='PASS' then
   perform private.activate_blink_vip_pass(v_user,v_active_id,v_inv.gifted_by);
   select blink_vip_until into v_expires from public.profiles where id=v_user;
   update public.blink_inventory set expires_at=v_expires,status='ACTIVE' where id=v_active_id;
 elsif v_item.id in ('post_boost','reel_boost','post_boost_plus','reel_boost_plus') then
   insert into public.blink_boosts(user_id,inventory_id,content_id,content_type,multiplier,starts_at,ends_at) values(v_user,v_active_id,p_target_id,v_target_type,coalesce(v_inv.boost_multiplier,case when v_item.id like '%_plus' then 2 else 1 end),now(),coalesce(v_expires,now()+interval '6 hours')) returning id into v_boost;
 elsif v_item.id='creator_promo_bundle' then
   insert into public.blink_inventory(user_id,catalog_id,quantity,status,boost_multiplier,metadata) values(v_user,'post_boost_plus',1,'AVAILABLE',2,jsonb_build_object('source','creator_promo_bundle'));
   insert into public.blink_inventory(user_id,catalog_id,quantity,status,boost_multiplier,metadata) values(v_user,'reel_boost_plus',1,'AVAILABLE',2,jsonb_build_object('source','creator_promo_bundle'));
   insert into public.blink_inventory(user_id,catalog_id,quantity,status,metadata) values(v_user,'profile_spotlight_1h',1,'AVAILABLE',jsonb_build_object('source','creator_promo_bundle'));
 elsif v_item.id='market_promo_bundle' then
   insert into public.blink_inventory(user_id,catalog_id,quantity,status,metadata) values(v_user,'market_listing_highlight',2,'AVAILABLE',jsonb_build_object('source','market_promo_bundle'));
   insert into public.blink_inventory(user_id,catalog_id,quantity,status,metadata) values(v_user,'market_seller_spotlight',1,'AVAILABLE',jsonb_build_object('source','market_promo_bundle'));
 end if;
 insert into public.blink_item_activations(user_id,inventory_id,catalog_id,target_type,target_id,started_at,expires_at,metadata) values(v_user,v_active_id,v_item.id,v_target_type,p_target_id,now(),v_expires,jsonb_build_object('boost_id',v_boost));
 return jsonb_build_object('success',true,'message','Activated','inventory_id',v_active_id,'expires_at',v_expires,'boost_id',v_boost);
end $$;
grant execute on function public.activate_blink_item(uuid,uuid) to authenticated;


-- 9) General cosmetic gifting. Distribution, analytics, VIP-only and content-targeted promotion
-- products are intentionally excluded; gifts land inactive in the recipient's Collection.
update public.blink_store_catalog
set metadata = coalesce(metadata,'{}'::jsonb) || jsonb_build_object(
  'giftable',
  (
    not vip_only
    and item_type in ('PERMANENT','TIMED')
    and category not in ('Boosts','Analytics','Marketplace')
    and id not in (
      'profile_spotlight_1h','profile_spotlight_24h',
      'post_spotlight_6h','post_spotlight_24h',
      'reel_spotlight_6h','reel_spotlight_24h',
      'discovery_boost_7d','profile_discovery_boost',
      'birthday_profile_theme'
    )
  )
),
updated_at=now()
where is_active=true;

create or replace function public.gift_blink_store_item(
  p_catalog_id text,
  p_recipient_username text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_sender uuid:=auth.uid();
  v_recipient uuid;
  v_recipient_username text;
  v_item public.blink_store_catalog%rowtype;
  v_price integer;
  v_balance bigint;
  v_inventory uuid;
  v_giftable boolean:=false;
begin
  if v_sender is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;

  select p.id,p.username
    into v_recipient,v_recipient_username
  from public.profiles p
  where lower(p.username)=lower(trim(leading '@' from coalesce(p_recipient_username,'')))
  limit 1;

  if v_recipient is null then raise exception 'RECIPIENT_NOT_FOUND'; end if;
  if v_recipient=v_sender then raise exception 'INVALID_RECIPIENT'; end if;

  select * into v_item
  from public.blink_store_catalog
  where id=trim(coalesce(p_catalog_id,'')) and is_active=true;

  if not found then raise exception 'ITEM_NOT_FOUND'; end if;
  if v_item.available_from is not null and now()<v_item.available_from then
    raise exception 'ITEM_NOT_AVAILABLE_YET';
  end if;
  if v_item.available_until is not null and now()>=v_item.available_until then
    raise exception 'ITEM_NO_LONGER_AVAILABLE';
  end if;

  v_giftable:=coalesce((v_item.metadata->>'giftable')::boolean,false);
  if not v_giftable then raise exception 'ITEM_NOT_GIFTABLE'; end if;

  if v_item.item_type='PERMANENT' and exists(
    select 1 from public.blink_inventory i
    where i.user_id=v_recipient
      and i.catalog_id=v_item.id
      and i.status='PERMANENT'
  ) then
    raise exception 'RECIPIENT_ALREADY_OWNS';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_sender::text)::bigint);
  v_price:=private.blink_effective_price(v_sender,v_item,1);

  insert into public.user_balances(user_id,spendable_coin_balance)
  values(v_sender,0) on conflict(user_id) do nothing;

  select spendable_coin_balance into v_balance
  from public.user_balances where user_id=v_sender for update;

  if coalesce(v_balance,0)<v_price then raise exception 'INSUFFICIENT_BLINK_COINS'; end if;

  update public.user_balances
  set spendable_coin_balance=spendable_coin_balance-v_price,updated_at=now()
  where user_id=v_sender
  returning spendable_coin_balance into v_balance;

  insert into public.blink_inventory(
    user_id,catalog_id,quantity,status,gifted_by,metadata
  )
  values(
    v_recipient,
    v_item.id,
    1,
    case when v_item.item_type='PERMANENT' then 'PERMANENT' else 'AVAILABLE' end,
    v_sender,
    jsonb_build_object('gift',true,'gifted_catalog_id',v_item.id)
  )
  returning id into v_inventory;

  insert into public.blink_coin_transactions(
    user_id,kind,catalog_id,item_name,amount,balance_after,metadata
  )
  values(
    v_sender,'COSMETIC_GIFT',v_item.id,v_item.name,-v_price,v_balance,
    jsonb_build_object(
      'recipient_id',v_recipient,
      'recipient_username',v_recipient_username,
      'inventory_id',v_inventory
    )
  );

  insert into public.notifications(user_id,actor_id,type,text)
  values(
    v_recipient,v_sender,'system',
    '🎁 You received '||v_item.name||'. Open Blink Store → Collection to preview or apply it.'
  );

  return jsonb_build_object(
    'success',true,
    'catalog_id',v_item.id,
    'recipient_id',v_recipient,
    'recipient_username',v_recipient_username,
    'inventory_id',v_inventory,
    'charged',v_price,
    'balance',v_balance
  );
end
$$;
revoke all on function public.gift_blink_store_item(text,text) from public,anon;
grant execute on function public.gift_blink_store_item(text,text) to authenticated;


-- 10) Store collections, XP-earned cosmetics and genuinely time-bounded seasonal drops.
insert into public.blink_store_collections(
  id,name,description,collection_type,scope_key,available_from,available_until,is_active,metadata
) values
(
  'campus_signature',
  'Campus Signature',
  'Coordinated campus identity cosmetics designed to work with a student’s own university identity without impersonating official university branding.',
  'CAMPUS',
  'USER_UNIVERSITY',
  null,null,true,
  '{"preview_required":true}'::jsonb
),
(
  'level_rewards',
  'Level Rewards',
  'Permanent cosmetics that can be purchased normally or claimed free after reaching the required BLINK XP level.',
  'EARNABLE',
  'XP_LEVEL',
  null,null,true,
  '{"preview_required":true}'::jsonb
),
(
  'christmas_2026',
  'Christmas 2026 Drop',
  'A real limited-time BLINK holiday collection. Availability is enforced by the backend dates shown in Store.',
  'SEASONAL',
  'GLOBAL',
  '2026-12-01 00:00:00+01'::timestamptz,
  '2027-01-01 00:00:00+01'::timestamptz,
  true,
  '{"preview_required":true,"limited":true}'::jsonb
)
on conflict(id) do update set
  name=excluded.name,
  description=excluded.description,
  collection_type=excluded.collection_type,
  scope_key=excluded.scope_key,
  available_from=excluded.available_from,
  available_until=excluded.available_until,
  is_active=excluded.is_active,
  metadata=excluded.metadata,
  updated_at=now();

insert into public.blink_store_catalog(
  id,name,description,icon_key,category,price,item_type,target_type,duration_seconds,
  stackable,vip_only,boost_multipliers,is_active,sort_order,metadata,
  collection_id,rarity,unlock_level,available_from,available_until
) values
(
  'campus_signature_theme','Campus Signature Theme',
  'A permanent coordinated profile theme made for campus identity. It uses BLINK styling and can sit alongside the user’s real university name without pretending to be official university artwork.',
  'school','Campus',1000,'PERMANENT','PROFILE',null,false,false,'{}',true,201,
  '{"public_collection":true,"preview_required":true,"giftable":true}'::jsonb,
  'campus_signature','PREMIUM',null,null,null
),
(
  'campus_signature_frame','Campus Signature Frame',
  'A permanent premium avatar frame for campus profiles and supported public identity surfaces.',
  'school','Campus',800,'PERMANENT','PROFILE',null,false,false,'{}',true,202,
  '{"public_collection":true,"preview_required":true,"giftable":true}'::jsonb,
  'campus_signature','PREMIUM',null,null,null
),
(
  'campus_signature_nameplate','Campus Signature Nameplate',
  'A permanent campus-styled nameplate for supported profile, feed, comment, search and message identity surfaces.',
  'badge','Campus',650,'PERMANENT','PROFILE',null,false,false,'{}',true,203,
  '{"public_collection":true,"preview_required":true,"giftable":true}'::jsonb,
  'campus_signature','PREMIUM',null,null,null
),
(
  'campus_signature_chat','Campus Signature Chat Theme',
  'A permanent coordinated campus conversation style for supported chats.',
  'chat_bubble','Campus',600,'PERMANENT','CHAT',null,false,false,'{}',true,204,
  '{"public_collection":true,"preview_required":true,"giftable":true}'::jsonb,
  'campus_signature','STANDARD',null,null,null
),
(
  'level_10_neon_frame','Level 10 Neon Frame',
  'A permanent frame that can be purchased normally or claimed free once BLINK XP Level 10 is reached.',
  'auto_awesome','Earned',700,'PERMANENT','PROFILE',null,false,false,'{}',true,211,
  '{"public_collection":true,"preview_required":true,"giftable":true,"earnable":true}'::jsonb,
  'level_rewards','PREMIUM',10,null,null
),
(
  'level_25_signature_nameplate','Level 25 Signature Nameplate',
  'A permanent animated identity nameplate that can be purchased or claimed free at BLINK XP Level 25.',
  'badge','Earned',900,'PERMANENT','PROFILE',null,false,false,'{}',true,212,
  '{"public_collection":true,"preview_required":true,"giftable":true,"earnable":true}'::jsonb,
  'level_rewards','RARE',25,null,null
),
(
  'level_50_legend_aura','Level 50 Legend Aura',
  'A permanent high-tier profile aura that can be purchased or claimed free at BLINK XP Level 50.',
  'auto_awesome','Earned',1400,'PERMANENT','PROFILE',null,false,false,'{}',true,213,
  '{"public_collection":true,"preview_required":true,"giftable":true,"earnable":true}'::jsonb,
  'level_rewards','LEGENDARY',50,null,null
),
(
  'christmas_2026_profile_theme','Christmas 2026 Profile Theme',
  'A permanent collectible holiday profile theme sold only during the backend-enforced Christmas 2026 availability window.',
  'redeem','Seasonal',1200,'PERMANENT','PROFILE',null,false,false,'{}',true,221,
  '{"public_collection":true,"preview_required":true,"giftable":true,"limited_edition":true}'::jsonb,
  'christmas_2026','LIMITED',null,
  '2026-12-01 00:00:00+01'::timestamptz,'2027-01-01 00:00:00+01'::timestamptz
),
(
  'christmas_2026_nameplate','Christmas 2026 Nameplate',
  'A permanent collectible holiday nameplate sold only during the backend-enforced Christmas 2026 availability window.',
  'redeem','Seasonal',800,'PERMANENT','PROFILE',null,false,false,'{}',true,222,
  '{"public_collection":true,"preview_required":true,"giftable":true,"limited_edition":true}'::jsonb,
  'christmas_2026','LIMITED',null,
  '2026-12-01 00:00:00+01'::timestamptz,'2027-01-01 00:00:00+01'::timestamptz
)
on conflict(id) do update set
  name=excluded.name,
  description=excluded.description,
  icon_key=excluded.icon_key,
  category=excluded.category,
  price=excluded.price,
  item_type=excluded.item_type,
  target_type=excluded.target_type,
  duration_seconds=excluded.duration_seconds,
  stackable=excluded.stackable,
  vip_only=excluded.vip_only,
  boost_multipliers=excluded.boost_multipliers,
  is_active=excluded.is_active,
  sort_order=excluded.sort_order,
  metadata=coalesce(public.blink_store_catalog.metadata,'{}'::jsonb)||excluded.metadata,
  collection_id=excluded.collection_id,
  rarity=excluded.rarity,
  unlock_level=excluded.unlock_level,
  available_from=excluded.available_from,
  available_until=excluded.available_until,
  updated_at=now();

-- Replace purchase with the same atomic wallet contract plus real availability enforcement.
create or replace function public.purchase_blink_item(
  p_catalog_id text,
  p_quantity integer default 1,
  p_boost_multiplier integer default 1
) returns jsonb
language plpgsql security definer set search_path=''
as $$
declare
  v_user uuid:=auth.uid();
  v_item public.blink_store_catalog%rowtype;
  v_balance bigint;
  v_unit integer;
  v_total bigint;
  v_inventory uuid;
  v_existing uuid;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_quantity<1 or p_quantity>20 then raise exception 'INVALID_QUANTITY'; end if;

  select * into v_item
  from public.blink_store_catalog
  where id=p_catalog_id and is_active=true;
  if not found then raise exception 'ITEM_NOT_FOUND'; end if;

  if v_item.available_from is not null and now()<v_item.available_from then
    raise exception 'ITEM_NOT_AVAILABLE_YET';
  end if;
  if v_item.available_until is not null and now()>=v_item.available_until then
    raise exception 'ITEM_NO_LONGER_AVAILABLE';
  end if;

  if v_item.vip_only and not private.is_blink_vip_id(v_user,now()) then raise exception 'VIP_REQUIRED'; end if;
  if v_item.item_type='PERMANENT' and p_quantity<>1 then raise exception 'PERMANENT_ITEM_QUANTITY_ONE'; end if;
  if v_item.item_type='PERMANENT' and exists(
    select 1 from public.blink_inventory
    where user_id=v_user and catalog_id=v_item.id and status='PERMANENT'
  ) then raise exception 'ALREADY_OWNED'; end if;

  if cardinality(v_item.boost_multipliers)>0 and not (p_boost_multiplier=any(v_item.boost_multipliers)) then
    raise exception 'INVALID_BOOST_MULTIPLIER';
  end if;
  if cardinality(v_item.boost_multipliers)=0 then p_boost_multiplier:=1; end if;

  v_unit:=private.blink_effective_price(v_user,v_item,p_boost_multiplier);
  v_total:=v_unit::bigint*p_quantity;

  insert into public.user_balances(user_id,spendable_coin_balance)
  values(v_user,0) on conflict(user_id) do nothing;

  select spendable_coin_balance into v_balance
  from public.user_balances where user_id=v_user for update;
  if v_balance<v_total then raise exception 'INSUFFICIENT_BLINK_COINS'; end if;

  update public.user_balances
  set spendable_coin_balance=spendable_coin_balance-v_total,updated_at=now()
  where user_id=v_user
  returning spendable_coin_balance into v_balance;

  if v_item.stackable and v_item.item_type<>'PERMANENT' then
    select id into v_existing
    from public.blink_inventory
    where user_id=v_user
      and catalog_id=v_item.id
      and status='AVAILABLE'
      and coalesce(boost_multiplier,1)=p_boost_multiplier
      and gifted_by is null
    order by purchased_at
    limit 1
    for update;
  end if;

  if v_existing is not null then
    update public.blink_inventory
    set quantity=quantity+p_quantity,updated_at=now()
    where id=v_existing
    returning id into v_inventory;
  else
    insert into public.blink_inventory(user_id,catalog_id,quantity,status,boost_multiplier)
    values(
      v_user,v_item.id,p_quantity,
      case when v_item.item_type='PERMANENT' then 'PERMANENT' else 'AVAILABLE' end,
      case when cardinality(v_item.boost_multipliers)>0 then p_boost_multiplier else null end
    )
    returning id into v_inventory;
  end if;

  insert into public.blink_coin_transactions(
    user_id,kind,catalog_id,item_name,amount,balance_after,metadata
  )
  values(
    v_user,'PURCHASE',v_item.id,v_item.name,-v_total,v_balance,
    jsonb_build_object(
      'quantity',p_quantity,
      'unit_price',v_unit,
      'boost_multiplier',p_boost_multiplier,
      'inventory_id',v_inventory
    )
  );

  return jsonb_build_object(
    'success',true,'message','Purchased','balance',v_balance,
    'inventory_id',v_inventory,'charged',v_total
  );
end
$$;
revoke all on function public.purchase_blink_item(text,integer,integer) from public,anon;
grant execute on function public.purchase_blink_item(text,integer,integer) to authenticated;

create or replace function public.claim_blink_level_cosmetic(p_catalog_id text)
returns jsonb
language plpgsql
security definer
set search_path=''
as $$
declare
  v_user uuid:=auth.uid();
  v_level integer:=1;
  v_item public.blink_store_catalog%rowtype;
  v_inventory uuid;
  v_balance bigint:=0;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(v_user::text)::bigint);

  select coalesce(xp_level,1) into v_level
  from public.profiles where id=v_user for update;
  if not found then raise exception 'PROFILE_NOT_FOUND'; end if;

  select * into v_item
  from public.blink_store_catalog
  where id=p_catalog_id and is_active=true;
  if not found then raise exception 'ITEM_NOT_FOUND'; end if;
  if v_item.unlock_level is null then raise exception 'ITEM_NOT_LEVEL_EARNABLE'; end if;
  if v_level<v_item.unlock_level then raise exception 'LEVEL_REQUIREMENT_NOT_MET'; end if;
  if v_item.item_type<>'PERMANENT' then raise exception 'LEVEL_REWARD_MUST_BE_PERMANENT'; end if;

  if exists(
    select 1 from public.blink_inventory
    where user_id=v_user and catalog_id=v_item.id and status='PERMANENT'
  ) then raise exception 'ALREADY_OWNED'; end if;

  insert into public.blink_inventory(user_id,catalog_id,quantity,status,metadata)
  values(
    v_user,v_item.id,1,'PERMANENT',
    jsonb_build_object('earned',true,'unlock_level',v_item.unlock_level)
  )
  returning id into v_inventory;

  insert into public.user_balances(user_id,spendable_coin_balance)
  values(v_user,0) on conflict(user_id) do nothing;
  select coalesce(spendable_coin_balance,0) into v_balance
  from public.user_balances where user_id=v_user;

  insert into public.blink_coin_transactions(
    user_id,kind,catalog_id,item_name,amount,balance_after,metadata
  )
  values(
    v_user,'LEVEL_COSMETIC_UNLOCK',v_item.id,v_item.name,0,v_balance,
    jsonb_build_object('inventory_id',v_inventory,'unlock_level',v_item.unlock_level)
  );

  return jsonb_build_object(
    'success',true,'catalog_id',v_item.id,'inventory_id',v_inventory,
    'unlock_level',v_item.unlock_level,'current_level',v_level
  );
end
$$;
revoke all on function public.claim_blink_level_cosmetic(text) from public,anon;
grant execute on function public.claim_blink_level_cosmetic(text) to authenticated;

create table if not exists public.blink_store_availability_notifications (
  user_id uuid not null references public.profiles(id) on delete cascade,
  catalog_id text not null references public.blink_store_catalog(id) on delete cascade,
  notified_at timestamptz not null default now(),
  primary key(user_id,catalog_id)
);
alter table public.blink_store_availability_notifications enable row level security;
revoke all on public.blink_store_availability_notifications from public,anon,authenticated;

create or replace function private.notify_blink_wishlist_availability()
returns void
language plpgsql
security definer
set search_path=''
as $$
begin
  with ready as (
    insert into public.blink_store_availability_notifications(user_id,catalog_id)
    select w.user_id,w.catalog_id
    from public.blink_store_wishlist w
    join public.blink_store_catalog c on c.id=w.catalog_id
    where c.is_active=true
      and c.available_from is not null
      and c.available_from<=now()
      and (c.available_until is null or c.available_until>now())
    on conflict do nothing
    returning user_id,catalog_id
  )
  insert into public.notifications(user_id,type,text)
  select r.user_id,'system','✨ '||c.name||' is now available in Blink Store.'
  from ready r
  join public.blink_store_catalog c on c.id=r.catalog_id;
end
$$;
revoke all on function private.notify_blink_wishlist_availability() from public,anon,authenticated;

do $$
declare v_job bigint;
begin
  for v_job in select jobid from cron.job where jobname='blink-store-wishlist-availability' loop
    perform cron.unschedule(v_job);
  end loop;
  perform cron.schedule(
    'blink-store-wishlist-availability',
    '19 * * * *',
    $job$select private.notify_blink_wishlist_availability();$job$
  );
end
$$;
