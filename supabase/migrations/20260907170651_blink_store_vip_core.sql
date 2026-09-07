create extension if not exists pgcrypto;

alter table public.profiles add column if not exists blink_vip_until timestamptz;
alter table public.notifications add column if not exists actor_is_vip boolean not null default false;
alter table public.notifications add column if not exists vip_priority boolean not null default false;

create table if not exists public.blink_store_catalog (
  id text primary key,
  name text not null,
  description text not null,
  icon_key text not null,
  category text not null,
  price integer not null check (price >= 0),
  item_type text not null check (item_type in ('CONSUMABLE','TIMED','PERMANENT','CONTENT_SPECIFIC','PASS')),
  target_type text not null default 'NONE' check (target_type in ('NONE','PROFILE','POST','REEL','COMMENT','CHAT','MARKETPLACE','APP')),
  duration_seconds bigint,
  stackable boolean not null default false,
  vip_only boolean not null default false,
  boost_multipliers integer[] not null default '{}'::integer[],
  is_active boolean not null default true,
  sort_order integer not null default 0,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.blink_inventory (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  catalog_id text not null references public.blink_store_catalog(id),
  quantity integer not null default 1 check (quantity > 0),
  status text not null default 'AVAILABLE' check (status in ('AVAILABLE','ACTIVE','PERMANENT','USED','EXPIRED')),
  purchased_at timestamptz not null default now(),
  activated_at timestamptz,
  expires_at timestamptz,
  target_type text,
  target_id uuid,
  boost_multiplier integer,
  gifted_by uuid references public.profiles(id) on delete set null,
  metadata jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

create index if not exists blink_inventory_user_status_idx on public.blink_inventory(user_id,status,purchased_at desc);
create index if not exists blink_inventory_user_catalog_idx on public.blink_inventory(user_id,catalog_id);

create table if not exists public.blink_coin_transactions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  kind text not null,
  catalog_id text references public.blink_store_catalog(id),
  item_name text not null,
  amount bigint not null,
  balance_after bigint not null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists blink_coin_transactions_user_idx on public.blink_coin_transactions(user_id,created_at desc);

create table if not exists public.blink_item_activations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  inventory_id uuid references public.blink_inventory(id) on delete set null,
  catalog_id text not null references public.blink_store_catalog(id),
  target_type text,
  target_id uuid,
  started_at timestamptz not null default now(),
  expires_at timestamptz,
  ended_at timestamptz,
  metadata jsonb not null default '{}'::jsonb
);
create index if not exists blink_item_activations_user_idx on public.blink_item_activations(user_id,started_at desc);
create index if not exists blink_item_activations_target_idx on public.blink_item_activations(target_type,target_id,expires_at);

create table if not exists public.blink_equipped_items (
  user_id uuid not null references public.profiles(id) on delete cascade,
  slot text not null,
  inventory_id uuid not null references public.blink_inventory(id) on delete cascade,
  catalog_id text not null references public.blink_store_catalog(id),
  equipped_at timestamptz not null default now(),
  primary key(user_id,slot)
);

create table if not exists public.blink_vip_passes (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  inventory_id uuid references public.blink_inventory(id) on delete set null,
  starts_at timestamptz not null,
  expires_at timestamptz not null,
  auto_renew boolean not null default false,
  expiry_reminder_sent boolean not null default false,
  gifted_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  check (expires_at > starts_at)
);
create index if not exists blink_vip_passes_user_expiry_idx on public.blink_vip_passes(user_id,expires_at desc);

create table if not exists public.blink_vip_benefit_balances (
  pass_id uuid primary key references public.blink_vip_passes(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  post_boosts_2x integer not null default 2 check (post_boosts_2x >= 0),
  reel_boosts_2x integer not null default 2 check (reel_boosts_2x >= 0),
  profile_spotlights integer not null default 1 check (profile_spotlights >= 0),
  post_spotlights integer not null default 1 check (post_spotlights >= 0),
  reel_spotlights integer not null default 1 check (reel_spotlights >= 0),
  marketplace_highlights integer not null default 1 check (marketplace_highlights >= 0),
  updated_at timestamptz not null default now()
);

create table if not exists public.blink_vip_claims (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  pass_id uuid references public.blink_vip_passes(id) on delete cascade,
  claim_key text not null,
  claim_date date,
  created_at timestamptz not null default now()
);
create unique index if not exists blink_vip_daily_claim_unique on public.blink_vip_claims(user_id,claim_key,claim_date) where claim_date is not null;

create table if not exists public.blink_boosts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  inventory_id uuid references public.blink_inventory(id) on delete set null,
  content_id uuid not null references public.feed_posts(id) on delete cascade,
  content_type text not null check (content_type in ('POST','REEL')),
  multiplier integer not null check (multiplier in (1,2,3,5)),
  starts_at timestamptz not null default now(),
  ends_at timestamptz not null,
  status text not null default 'ACTIVE' check (status in ('ACTIVE','ENDED','CANCELLED')),
  extra_impressions bigint not null default 0,
  profile_visits bigint not null default 0,
  followers_attributed bigint not null default 0,
  created_at timestamptz not null default now(),
  check (ends_at > starts_at)
);
create index if not exists blink_boosts_content_active_idx on public.blink_boosts(content_id,ends_at) where status='ACTIVE';
create index if not exists blink_boosts_user_idx on public.blink_boosts(user_id,created_at desc);

create table if not exists public.blink_boost_touches (
  boost_id uuid not null references public.blink_boosts(id) on delete cascade,
  viewer_id uuid not null references public.profiles(id) on delete cascade,
  last_touched_at timestamptz not null default now(),
  profile_visit_at timestamptz,
  follow_at timestamptz,
  primary key(boost_id,viewer_id)
);

alter table public.blink_store_catalog enable row level security;
alter table public.blink_inventory enable row level security;
alter table public.blink_coin_transactions enable row level security;
alter table public.blink_item_activations enable row level security;
alter table public.blink_equipped_items enable row level security;
alter table public.blink_vip_passes enable row level security;
alter table public.blink_vip_benefit_balances enable row level security;
alter table public.blink_vip_claims enable row level security;
alter table public.blink_boosts enable row level security;
alter table public.blink_boost_touches enable row level security;

drop policy if exists blink_store_catalog_read on public.blink_store_catalog;
create policy blink_store_catalog_read on public.blink_store_catalog for select to authenticated using (is_active=true);
drop policy if exists blink_inventory_own_read on public.blink_inventory;
create policy blink_inventory_own_read on public.blink_inventory for select to authenticated using (user_id=auth.uid());
drop policy if exists blink_transactions_own_read on public.blink_coin_transactions;
create policy blink_transactions_own_read on public.blink_coin_transactions for select to authenticated using (user_id=auth.uid());
drop policy if exists blink_activations_own_read on public.blink_item_activations;
create policy blink_activations_own_read on public.blink_item_activations for select to authenticated using (user_id=auth.uid());
drop policy if exists blink_equipped_own_read on public.blink_equipped_items;
create policy blink_equipped_own_read on public.blink_equipped_items for select to authenticated using (user_id=auth.uid());
drop policy if exists blink_vip_passes_own_read on public.blink_vip_passes;
create policy blink_vip_passes_own_read on public.blink_vip_passes for select to authenticated using (user_id=auth.uid());
drop policy if exists blink_vip_benefits_own_read on public.blink_vip_benefit_balances;
create policy blink_vip_benefits_own_read on public.blink_vip_benefit_balances for select to authenticated using (user_id=auth.uid());
drop policy if exists blink_vip_claims_own_read on public.blink_vip_claims;
create policy blink_vip_claims_own_read on public.blink_vip_claims for select to authenticated using (user_id=auth.uid());
drop policy if exists blink_boosts_own_read on public.blink_boosts;
create policy blink_boosts_own_read on public.blink_boosts for select to authenticated using (user_id=auth.uid());
drop policy if exists blink_boost_touches_owner_read on public.blink_boost_touches;
create policy blink_boost_touches_owner_read on public.blink_boost_touches for select to authenticated using (exists(select 1 from public.blink_boosts b where b.id=boost_id and b.user_id=auth.uid()));

revoke all on public.blink_inventory,public.blink_coin_transactions,public.blink_item_activations,public.blink_equipped_items,public.blink_vip_passes,public.blink_vip_benefit_balances,public.blink_vip_claims,public.blink_boosts,public.blink_boost_touches from anon,authenticated;
grant select on public.blink_store_catalog,public.blink_inventory,public.blink_coin_transactions,public.blink_item_activations,public.blink_equipped_items,public.blink_vip_passes,public.blink_vip_benefit_balances,public.blink_vip_claims,public.blink_boosts to authenticated;

insert into public.blink_store_catalog(id,name,description,icon_key,category,price,item_type,target_type,duration_seconds,stackable,vip_only,boost_multipliers,sort_order) values
('profile_highlight_1h','Profile Highlight — 1 hour','Highlight your profile in eligible discovery surfaces for one hour.','auto_awesome','Profile',10,'TIMED','PROFILE',3600,false,false,'{}',1),
('comment_highlight','Comment Highlight','Give one selected comment a highlighted treatment.','push_pin','Social',10,'CONTENT_SPECIFIC','COMMENT',null,true,false,'{}',2),
('comment_color','Special Comment Color','Unlock a premium comment accent style.','palette','Social',15,'PERMANENT','COMMENT',null,false,false,'{}',3),
('animated_like','Animated Like Effect','Unlock a premium like animation.','favorite','Social',15,'PERMANENT','APP',null,false,false,'{}',4),
('profile_glow_1h','Profile Glow — 1 hour','Activate a profile glow for one hour.','flare','Profile',20,'TIMED','PROFILE',3600,true,false,'{}',5),
('chat_bubble_theme','Custom Chat Bubble Theme','Unlock an additional chat bubble theme.','chat_bubble','Chat',20,'PERMANENT','CHAT',null,false,false,'{}',6),
('reaction_pack','Special Reaction Pack','Unlock additional reactions.','emoji_emotions','Chat',20,'PERMANENT','CHAT',null,false,false,'{}',7),
('profile_ring','Custom Profile Ring','Unlock a custom profile ring.','radio_button_unchecked','Profile',25,'PERMANENT','PROFILE',null,false,false,'{}',8),
('username_glow_24h','Username Glow — 24 hours','Apply a glowing username treatment for 24 hours.','text_fields','Profile',25,'TIMED','PROFILE',86400,true,false,'{}',9),
('post_border','Post Border Effect','Apply a premium border to one post.','crop_square','Posts',25,'CONTENT_SPECIFIC','POST',null,true,false,'{}',10),
('story_highlight','Story Highlight Effect','Highlight one story placement.','auto_stories','Posts',30,'CONSUMABLE','POST',null,true,false,'{}',11),
('profile_background','Profile Background Theme','Unlock a profile background theme.','wallpaper','Profile',30,'PERMANENT','PROFILE',null,false,false,'{}',12),
('emoji_pack','Exclusive Emoji Pack','Unlock an exclusive emoji pack.','mood','Chat',30,'PERMANENT','CHAT',null,false,false,'{}',13),
('animated_profile_ring','Animated Profile Ring','Unlock an animated profile ring.','motion_photos_on','Profile',35,'PERMANENT','PROFILE',null,false,false,'{}',14),
('chat_background','Chat Background Theme','Unlock a premium chat background.','wallpaper','Chat',35,'PERMANENT','CHAT',null,false,false,'{}',15),
('profile_entrance_animation','Profile Entrance Animation','Unlock an entrance animation for your profile.','animation','Profile',40,'PERMANENT','PROFILE',null,false,false,'{}',16),
('post_highlight_1h','Post Highlight — 1 hour','Highlight one selected post for one hour.','star','Posts',40,'CONTENT_SPECIFIC','POST',3600,true,false,'{}',17),
('reel_highlight_1h','Reel Highlight — 1 hour','Highlight one selected reel for one hour.','smart_display','Reels',40,'CONTENT_SPECIFIC','REEL',3600,true,false,'{}',18),
('visitor_insights_24h','Profile Visitor Insights — 24 hours','See profile visitor analytics for 24 hours.','visibility','Analytics',40,'TIMED','PROFILE',86400,true,false,'{}',19),
('notification_sound_pack','Notification Sound Pack','Unlock premium notification sounds.','notifications_active','App',45,'PERMANENT','APP',null,false,false,'{}',20),
('app_icon_pack','Custom App Icon Pack','Unlock additional Blink app icons.','apps','App',50,'PERMANENT','APP',null,false,false,'{}',21),
('profile_spotlight_1h','Profile Spotlight — 1 hour','Give your profile a one-hour spotlight placement.','lightbulb','Profile',50,'TIMED','PROFILE',3600,true,false,'{}',22),
('username_font','Special Username Font','Unlock an additional username font treatment.','font_download','Profile',50,'PERMANENT','PROFILE',null,false,false,'{}',23),
('sticker_pack','Premium Sticker Pack','Unlock a premium sticker pack.','sticky_note_2','Chat',50,'PERMANENT','CHAT',null,false,false,'{}',24),
('digital_gift','Digital Gift','Send one Blink digital gift to another user.','redeem','Gifts',50,'CONSUMABLE','PROFILE',null,true,false,'{}',25),
('post_boost','Post Boost','Boost one post using 1×, 2×, 3× or 5× distribution strength.','trending_up','Boosts',60,'CONTENT_SPECIFIC','POST',21600,true,false,'{1,2,3,5}',26),
('reel_boost','Reel Boost','Boost one reel using 1×, 2×, 3× or 5× distribution strength.','rocket_launch','Boosts',60,'CONTENT_SPECIFIC','REEL',21600,true,false,'{1,2,3,5}',27),
('market_listing_highlight','Marketplace Listing Highlight','Highlight one marketplace listing.','storefront','Marketplace',60,'CONTENT_SPECIFIC','MARKETPLACE',21600,true,false,'{}',28),
('profile_discovery_boost','Profile Discovery Boost','Increase eligible profile discovery opportunities for six hours.','explore','Boosts',65,'TIMED','PROFILE',21600,true,false,'{}',29),
('custom_profile_badge','Custom Profile Badge','Unlock an additional cosmetic profile badge.','workspace_premium','Profile',70,'PERMANENT','PROFILE',null,false,false,'{}',30),
('profile_theme_3d','3-Day Profile Theme','Use a premium profile theme for three days.','brush','Profile',75,'TIMED','PROFILE',259200,true,false,'{}',31),
('animated_name','Animated Name Effect','Unlock an animated name treatment.','animation','Profile',75,'PERMANENT','PROFILE',null,false,false,'{}',32),
('special_dm_theme','Special DM Theme','Unlock a premium direct-message theme.','forum','Chat',80,'PERMANENT','CHAT',null,false,false,'{}',33),
('post_spotlight_6h','Post Spotlight — 6 hours','Spotlight one selected post for six hours.','campaign','Posts',80,'CONTENT_SPECIFIC','POST',21600,true,false,'{}',34),
('reel_spotlight_6h','Reel Spotlight — 6 hours','Spotlight one selected reel for six hours.','play_circle','Reels',80,'CONTENT_SPECIFIC','REEL',21600,true,false,'{}',35),
('market_seller_spotlight','Marketplace Seller Spotlight','Spotlight your seller profile for six hours.','shopping_bag','Marketplace',90,'TIMED','MARKETPLACE',21600,true,false,'{}',36),
('profile_spotlight_24h','Profile Spotlight — 24 hours','Give your profile a 24-hour spotlight placement.','person_search','Profile',100,'TIMED','PROFILE',86400,true,false,'{}',37),
('post_boost_plus','Post Boost Plus','A ready-to-use 2× six-hour post boost.','bolt','Boosts',100,'CONTENT_SPECIFIC','POST',21600,true,false,'{2}',38),
('reel_boost_plus','Reel Boost Plus','A ready-to-use 2× six-hour reel boost.','whatshot','Boosts',100,'CONTENT_SPECIFIC','REEL',21600,true,false,'{2}',39),
('vip_theme','Exclusive VIP Theme','Unlock a VIP theme usable while VIP is active.','diamond','VIP',120,'PERMANENT','APP',null,false,true,'{}',40),
('profile_glow_7d','7-Day Profile Glow','Activate a profile glow for seven days.','brightness_7','Profile',120,'TIMED','PROFILE',604800,true,false,'{}',41),
('premium_profile_frame','Premium Profile Frame','Unlock a premium profile frame.','account_box','Profile',130,'PERMANENT','PROFILE',null,false,false,'{}',42),
('creator_badge','Premium Creator Badge','Unlock a cosmetic creator badge.','stars','Creator',150,'PERMANENT','PROFILE',null,false,false,'{}',43),
('post_spotlight_24h','Post Spotlight — 24 hours','Spotlight one selected post for 24 hours.','volume_up','Posts',150,'CONTENT_SPECIFIC','POST',86400,true,false,'{}',44),
('reel_spotlight_24h','Reel Spotlight — 24 hours','Spotlight one selected reel for 24 hours.','movie_filter','Reels',150,'CONTENT_SPECIFIC','REEL',86400,true,false,'{}',45),
('discovery_boost_7d','7-Day Discovery Boost','Increase eligible profile discovery opportunities for seven days.','travel_explore','Boosts',180,'TIMED','PROFILE',604800,true,false,'{}',46),
('profile_theme_bundle','Premium Profile Theme Bundle','Unlock a collection of premium profile themes.','style','Profile',200,'PERMANENT','PROFILE',null,false,false,'{}',47),
('creator_promo_bundle','Creator Promotion Bundle','A bundle of creator spotlights and boost credits.','campaign','Creator',250,'CONSUMABLE','PROFILE',null,true,false,'{}',48),
('market_promo_bundle','Marketplace Promotion Bundle','A bundle of marketplace highlights and seller spotlight time.','local_mall','Marketplace',300,'CONSUMABLE','MARKETPLACE',null,true,false,'{}',49),
('blink_vip_10d','Blink VIP — 10 Days','Ten days of Blink VIP benefits, rewards, styling, analytics and claimable boosts.','crown','VIP',350,'PASS','PROFILE',864000,true,false,'{}',50)
on conflict(id) do update set name=excluded.name,description=excluded.description,icon_key=excluded.icon_key,category=excluded.category,price=excluded.price,item_type=excluded.item_type,target_type=excluded.target_type,duration_seconds=excluded.duration_seconds,stackable=excluded.stackable,vip_only=excluded.vip_only,boost_multipliers=excluded.boost_multipliers,is_active=true,sort_order=excluded.sort_order,updated_at=now();

create or replace function private.is_blink_vip_id(p_user uuid, p_at timestamptz default now()) returns boolean
language sql stable security definer set search_path=''
as $$ select p_user is not null and exists(select 1 from public.blink_vip_passes v where v.user_id=p_user and v.starts_at<=p_at and v.expires_at>p_at); $$;
revoke all on function private.is_blink_vip_id(uuid,timestamptz) from public,anon,authenticated;

drop function if exists public.blink_is_vip(uuid);
create function public.blink_is_vip(p_user uuid) returns boolean
language sql stable security definer set search_path=''
as $$ select private.is_blink_vip_id(p_user,now()); $$;
grant execute on function public.blink_is_vip(uuid) to authenticated;

create or replace function private.blink_effective_price(p_user uuid,p_item public.blink_store_catalog,p_multiplier integer default 1) returns integer
language plpgsql stable security definer set search_path=''
as $$
declare v_price integer; begin
  v_price := case when cardinality(p_item.boost_multipliers)>0 then case p_multiplier when 1 then p_item.price when 2 then floor(p_item.price*1.65)::integer when 3 then floor(p_item.price*2.35)::integer when 5 then floor(p_item.price*3.65)::integer else p_item.price end else p_item.price end;
  if private.is_blink_vip_id(p_user,now()) then v_price := greatest(0,floor(v_price*0.90)::integer); end if;
  return v_price;
end $$;
revoke all on function private.blink_effective_price(uuid,public.blink_store_catalog,integer) from public,anon,authenticated;

create or replace function private.blink_current_pass(p_user uuid) returns uuid
language sql stable security definer set search_path=''
as $$ select v.id from public.blink_vip_passes v where v.user_id=p_user and v.starts_at<=now() and v.expires_at>now() order by v.expires_at desc limit 1; $$;
revoke all on function private.blink_current_pass(uuid) from public,anon,authenticated;

create or replace function private.activate_blink_vip_pass(p_user uuid,p_inventory uuid,p_gifted_by uuid default null) returns uuid
language plpgsql security definer set search_path=''
as $$
declare v_base timestamptz; v_pass uuid; v_auto boolean:=false; begin
  select greatest(now(),coalesce(max(expires_at),now())) into v_base from public.blink_vip_passes where user_id=p_user and expires_at>now();
  select coalesce((select auto_renew from public.blink_vip_passes where user_id=p_user order by expires_at desc limit 1),false) into v_auto;
  insert into public.blink_vip_passes(user_id,inventory_id,starts_at,expires_at,auto_renew,gifted_by) values(p_user,p_inventory,v_base,v_base+interval '10 days',v_auto,p_gifted_by) returning id into v_pass;
  insert into public.blink_vip_benefit_balances(pass_id,user_id) values(v_pass,p_user);
  update public.profiles set blink_vip_until=v_base+interval '10 days' where id=p_user;
  return v_pass;
end $$;
revoke all on function private.activate_blink_vip_pass(uuid,uuid,uuid) from public,anon,authenticated;

create or replace function public.purchase_blink_item(p_catalog_id text,p_quantity integer default 1,p_boost_multiplier integer default 1) returns jsonb
language plpgsql security definer set search_path=''
as $$
declare v_user uuid:=auth.uid(); v_item public.blink_store_catalog%rowtype; v_balance bigint; v_unit integer; v_total bigint; v_inventory uuid; v_existing uuid; begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
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

create or replace function public.set_blink_item_equipped(p_inventory_id uuid,p_slot text,p_enabled boolean) returns jsonb
language plpgsql security definer set search_path=''
as $$
declare v_user uuid:=auth.uid(); v_inv public.blink_inventory%rowtype; v_item public.blink_store_catalog%rowtype; begin
 if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
 select * into v_inv from public.blink_inventory where id=p_inventory_id and user_id=v_user;
 if not found then raise exception 'INVENTORY_NOT_FOUND'; end if;
 select * into v_item from public.blink_store_catalog where id=v_inv.catalog_id;
 if v_item.item_type<>'PERMANENT' then raise exception 'NOT_PERMANENT'; end if;
 if v_item.vip_only and not private.is_blink_vip_id(v_user,now()) then raise exception 'VIP_REQUIRED'; end if;
 if p_enabled then insert into public.blink_equipped_items(user_id,slot,inventory_id,catalog_id) values(v_user,left(p_slot,80),v_inv.id,v_inv.catalog_id) on conflict(user_id,slot) do update set inventory_id=excluded.inventory_id,catalog_id=excluded.catalog_id,equipped_at=now();
 else delete from public.blink_equipped_items where user_id=v_user and slot=left(p_slot,80) and inventory_id=v_inv.id; end if;
 return jsonb_build_object('success',true,'equipped',p_enabled);
end $$;
grant execute on function public.set_blink_item_equipped(uuid,text,boolean) to authenticated;

create or replace function public.claim_blink_vip_benefit(p_benefit text) returns jsonb
language plpgsql security definer set search_path=''
as $$
declare v_user uuid:=auth.uid(); v_pass uuid; v_bal public.blink_vip_benefit_balances%rowtype; v_wallet bigint; begin
 if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
 v_pass:=private.blink_current_pass(v_user); if v_pass is null then raise exception 'VIP_REQUIRED'; end if;
 select * into v_bal from public.blink_vip_benefit_balances where pass_id=v_pass for update;
 if p_benefit='daily_coin_bonus' then
   insert into public.blink_vip_claims(user_id,pass_id,claim_key,claim_date) values(v_user,v_pass,p_benefit,current_date) on conflict do nothing;
   if not found then raise exception 'ALREADY_CLAIMED_TODAY'; end if;
   insert into public.user_balances(user_id,spendable_coin_balance) values(v_user,0) on conflict(user_id) do nothing;
   update public.user_balances set spendable_coin_balance=spendable_coin_balance+5,updated_at=now() where user_id=v_user returning spendable_coin_balance into v_wallet;
   insert into public.blink_coin_transactions(user_id,kind,item_name,amount,balance_after,metadata) values(v_user,'VIP_DAILY_BONUS','VIP daily coin bonus',5,v_wallet,jsonb_build_object('pass_id',v_pass));
 elsif p_benefit='post_boost_2x' then
   if v_bal.post_boosts_2x<1 then raise exception 'BENEFIT_EXHAUSTED'; end if; update public.blink_vip_benefit_balances set post_boosts_2x=post_boosts_2x-1,updated_at=now() where pass_id=v_pass; insert into public.blink_inventory(user_id,catalog_id,quantity,status,boost_multiplier,metadata) values(v_user,'post_boost_plus',1,'AVAILABLE',2,jsonb_build_object('vip_pass_id',v_pass));
 elsif p_benefit='reel_boost_2x' then
   if v_bal.reel_boosts_2x<1 then raise exception 'BENEFIT_EXHAUSTED'; end if; update public.blink_vip_benefit_balances set reel_boosts_2x=reel_boosts_2x-1,updated_at=now() where pass_id=v_pass; insert into public.blink_inventory(user_id,catalog_id,quantity,status,boost_multiplier,metadata) values(v_user,'reel_boost_plus',1,'AVAILABLE',2,jsonb_build_object('vip_pass_id',v_pass));
 elsif p_benefit='profile_spotlight' then
   if v_bal.profile_spotlights<1 then raise exception 'BENEFIT_EXHAUSTED'; end if; update public.blink_vip_benefit_balances set profile_spotlights=profile_spotlights-1,updated_at=now() where pass_id=v_pass; insert into public.blink_inventory(user_id,catalog_id,quantity,status,metadata) values(v_user,'profile_spotlight_1h',1,'AVAILABLE',jsonb_build_object('vip_pass_id',v_pass));
 elsif p_benefit='post_spotlight' then
   if v_bal.post_spotlights<1 then raise exception 'BENEFIT_EXHAUSTED'; end if; update public.blink_vip_benefit_balances set post_spotlights=post_spotlights-1,updated_at=now() where pass_id=v_pass; insert into public.blink_inventory(user_id,catalog_id,quantity,status,metadata) values(v_user,'post_spotlight_6h',1,'AVAILABLE',jsonb_build_object('vip_pass_id',v_pass));
 elsif p_benefit='reel_spotlight' then
   if v_bal.reel_spotlights<1 then raise exception 'BENEFIT_EXHAUSTED'; end if; update public.blink_vip_benefit_balances set reel_spotlights=reel_spotlights-1,updated_at=now() where pass_id=v_pass; insert into public.blink_inventory(user_id,catalog_id,quantity,status,metadata) values(v_user,'reel_spotlight_6h',1,'AVAILABLE',jsonb_build_object('vip_pass_id',v_pass));
 elsif p_benefit='marketplace_highlight' then
   if v_bal.marketplace_highlights<1 then raise exception 'BENEFIT_EXHAUSTED'; end if; update public.blink_vip_benefit_balances set marketplace_highlights=marketplace_highlights-1,updated_at=now() where pass_id=v_pass; insert into public.blink_inventory(user_id,catalog_id,quantity,status,metadata) values(v_user,'market_listing_highlight',1,'AVAILABLE',jsonb_build_object('vip_pass_id',v_pass));
 else raise exception 'UNKNOWN_BENEFIT'; end if;
 return jsonb_build_object('success',true,'benefit',p_benefit,'balance',v_wallet);
end $$;
grant execute on function public.claim_blink_vip_benefit(text) to authenticated;

create or replace function public.renew_blink_vip() returns jsonb
language plpgsql security definer set search_path=''
as $$
declare v_user uuid:=auth.uid(); v_item public.blink_store_catalog%rowtype; v_price integer; v_balance bigint; v_pass uuid; v_exp timestamptz; begin
 if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
 select * into v_item from public.blink_store_catalog where id='blink_vip_10d'; v_price:=private.blink_effective_price(v_user,v_item,1);
 insert into public.user_balances(user_id,spendable_coin_balance) values(v_user,0) on conflict(user_id) do nothing;
 select spendable_coin_balance into v_balance from public.user_balances where user_id=v_user for update; if v_balance<v_price then raise exception 'INSUFFICIENT_BLINK_COINS'; end if;
 update public.user_balances set spendable_coin_balance=spendable_coin_balance-v_price,updated_at=now() where user_id=v_user returning spendable_coin_balance into v_balance;
 v_pass:=private.activate_blink_vip_pass(v_user,null,null); select expires_at into v_exp from public.blink_vip_passes where id=v_pass;
 insert into public.blink_coin_transactions(user_id,kind,catalog_id,item_name,amount,balance_after) values(v_user,'VIP_RENEWAL','blink_vip_10d','Blink VIP — 10 Days',-v_price,v_balance);
 return jsonb_build_object('success',true,'balance',v_balance,'expires_at',v_exp,'pass_id',v_pass);
end $$;
grant execute on function public.renew_blink_vip() to authenticated;

create or replace function public.set_blink_vip_auto_renew(p_enabled boolean) returns jsonb
language plpgsql security definer set search_path=''
as $$ declare v_user uuid:=auth.uid(); begin if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if; update public.blink_vip_passes set auto_renew=p_enabled where user_id=v_user and expires_at=(select max(expires_at) from public.blink_vip_passes where user_id=v_user); return jsonb_build_object('success',true,'auto_renew',p_enabled); end $$;
grant execute on function public.set_blink_vip_auto_renew(boolean) to authenticated;

create or replace function public.gift_blink_vip(p_recipient_username text) returns jsonb
language plpgsql security definer set search_path=''
as $$
declare v_user uuid:=auth.uid(); v_recipient uuid; v_item public.blink_store_catalog%rowtype; v_price integer; v_balance bigint; v_inv uuid; begin
 if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
 select id into v_recipient from public.profiles where lower(username)=lower(trim(p_recipient_username)) limit 1; if v_recipient is null or v_recipient=v_user then raise exception 'INVALID_RECIPIENT'; end if;
 select * into v_item from public.blink_store_catalog where id='blink_vip_10d'; v_price:=private.blink_effective_price(v_user,v_item,1);
 insert into public.user_balances(user_id,spendable_coin_balance) values(v_user,0) on conflict(user_id) do nothing;
 select spendable_coin_balance into v_balance from public.user_balances where user_id=v_user for update; if v_balance<v_price then raise exception 'INSUFFICIENT_BLINK_COINS'; end if;
 update public.user_balances set spendable_coin_balance=spendable_coin_balance-v_price,updated_at=now() where user_id=v_user returning spendable_coin_balance into v_balance;
 insert into public.blink_inventory(user_id,catalog_id,quantity,status,gifted_by,metadata) values(v_recipient,'blink_vip_10d',1,'AVAILABLE',v_user,jsonb_build_object('gift',true)) returning id into v_inv;
 insert into public.blink_coin_transactions(user_id,kind,catalog_id,item_name,amount,balance_after,metadata) values(v_user,'VIP_GIFT','blink_vip_10d','Blink VIP — 10 Days gift',-v_price,v_balance,jsonb_build_object('recipient_id',v_recipient));
 insert into public.notifications(user_id,actor_id,type,comment) values(v_recipient,v_user,'system','👑 You received Blink VIP — 10 Days. Open Blink Store → Vault to activate it.');
 return jsonb_build_object('success',true,'balance',v_balance,'recipient_id',v_recipient,'inventory_id',v_inv);
end $$;
grant execute on function public.gift_blink_vip(text) to authenticated;

create or replace function public.get_blink_store_state() returns jsonb
language sql stable security definer set search_path=''
as $$
with me as (select auth.uid() uid), pass as (select v.* from public.blink_vip_passes v,me where v.user_id=me.uid and v.starts_at<=now() and v.expires_at>now() order by v.expires_at desc limit 1), benefits as (select b.* from public.blink_vip_benefit_balances b join pass p on p.id=b.pass_id), latest as (select count(*)::int completed,coalesce(sum(extract(epoch from (expires_at-starts_at))/86400)::int,0) cumulative from public.blink_vip_passes v,me where v.user_id=me.uid)
select jsonb_build_object(
'balance',coalesce((select spendable_coin_balance from public.user_balances u,me where u.user_id=me.uid),0),
'catalog',coalesce((select jsonb_agg(to_jsonb(c) order by sort_order) from public.blink_store_catalog c where c.is_active),'[]'::jsonb),
'inventory',coalesce((select jsonb_agg(to_jsonb(i)||jsonb_build_object('name',c.name,'icon_key',c.icon_key) order by i.purchased_at desc) from public.blink_inventory i join public.blink_store_catalog c on c.id=i.catalog_id,me where i.user_id=me.uid),'[]'::jsonb),
'transactions',coalesce((select jsonb_agg(to_jsonb(t) order by t.created_at desc) from (select x.* from public.blink_coin_transactions x,me where x.user_id=me.uid order by x.created_at desc limit 100) t),'[]'::jsonb),
'vip',jsonb_build_object('active',exists(select 1 from pass),'expires_at',(select expires_at from pass),'remaining_seconds',greatest(0,coalesce(extract(epoch from ((select expires_at from pass)-now()))::bigint,0)),'completed_passes',(select completed from latest),'cumulative_vip_days',(select cumulative from latest),'auto_renew',coalesce((select auto_renew from pass),false),'post_boosts_2x',coalesce((select post_boosts_2x from benefits),0),'reel_boosts_2x',coalesce((select reel_boosts_2x from benefits),0),'profile_spotlights',coalesce((select profile_spotlights from benefits),0),'post_spotlights',coalesce((select post_spotlights from benefits),0),'reel_spotlights',coalesce((select reel_spotlights from benefits),0),'marketplace_highlights',coalesce((select marketplace_highlights from benefits),0),'daily_coin_bonus_claimed',exists(select 1 from public.blink_vip_claims q,me where q.user_id=me.uid and q.claim_key='daily_coin_bonus' and q.claim_date=current_date)),
'active_boosts',coalesce((select jsonb_agg(to_jsonb(b) order by b.ends_at) from public.blink_boosts b,me where b.user_id=me.uid and b.status='ACTIVE' and b.ends_at>now()),'[]'::jsonb),
'equipped',coalesce((select jsonb_agg(to_jsonb(e)) from public.blink_equipped_items e,me where e.user_id=me.uid),'[]'::jsonb)
) where (select uid from me) is not null;
$$;
grant execute on function public.get_blink_store_state() to authenticated;

create or replace function public.get_my_blink_boostable_content() returns jsonb
language sql stable security definer set search_path=''
as $$ select jsonb_build_object('posts',coalesce((select jsonb_agg(jsonb_build_object('id',p.id,'type',case when p.is_reel then 'REEL' else 'POST' end,'text',left(coalesce(nullif(p.text,''),p.caption,''),120),'created_at',p.created_at) order by p.created_at desc) from (select * from public.feed_posts where user_id=auth.uid() and is_active=true order by created_at desc limit 80) p),'[]'::jsonb),'market',coalesce((select jsonb_agg(jsonb_build_object('id',m.id,'type','MARKETPLACE','text',m.title,'created_at',m.created_at) order by m.created_at desc) from (select * from public.market_items where seller_id=auth.uid() order by created_at desc limit 80) m),'[]'::jsonb)); $$;
grant execute on function public.get_my_blink_boostable_content() to authenticated;

create or replace function public.get_blink_vip_statuses(p_user_ids uuid[]) returns table(user_id uuid,is_vip boolean,vip_until timestamptz)
language sql stable security definer set search_path=''
as $$ select p.id,private.is_blink_vip_id(p.id,now()),p.blink_vip_until from public.profiles p where p.id=any(coalesce(p_user_ids,'{}'::uuid[])); $$;
grant execute on function public.get_blink_vip_statuses(uuid[]) to authenticated;

create or replace function private.decorate_blink_notification() returns trigger
language plpgsql security definer set search_path=''
as $$ begin new.actor_is_vip:=private.is_blink_vip_id(new.actor_id,coalesce(new.created_at,now())); new.vip_priority:=new.actor_is_vip and new.type in ('like','comment','repost','follow','mention'); return new; end $$;
drop trigger if exists trg_decorate_blink_notification on public.notifications;
create trigger trg_decorate_blink_notification before insert or update of actor_id,type on public.notifications for each row execute function private.decorate_blink_notification();
update public.notifications n set actor_is_vip=private.is_blink_vip_id(n.actor_id,n.created_at),vip_priority=private.is_blink_vip_id(n.actor_id,n.created_at) and n.type in ('like','comment','repost','follow','mention') where n.created_at>now()-interval '30 days';

create or replace function private.track_blink_boost_view() returns trigger
language plpgsql security definer set search_path=''
as $$ declare v_boost uuid; v_delta bigint; begin
 select b.id into v_boost from public.blink_boosts b where b.content_id=new.post_id and b.status='ACTIVE' and b.starts_at<=now() and b.ends_at>now() order by b.multiplier desc,b.ends_at desc limit 1;
 if v_boost is null or new.viewer_id is null then return new; end if;
 v_delta:=case when tg_op='UPDATE' then greatest(0,coalesce(new.impression_count,0)-coalesce(old.impression_count,0)) else greatest(1,coalesce(new.impression_count,1)) end;
 if v_delta>0 then update public.blink_boosts set extra_impressions=extra_impressions+v_delta where id=v_boost; insert into public.blink_boost_touches(boost_id,viewer_id,last_touched_at) values(v_boost,new.viewer_id,now()) on conflict(boost_id,viewer_id) do update set last_touched_at=excluded.last_touched_at; end if;
 return new;
end $$;
drop trigger if exists trg_track_blink_boost_view on public.post_views;
create trigger trg_track_blink_boost_view after insert or update of impression_count on public.post_views for each row execute function private.track_blink_boost_view();

create or replace function private.track_blink_boost_profile_visit() returns trigger
language plpgsql security definer set search_path=''
as $$ declare v_ids uuid[]; begin
 if new.viewer_id is null or new.profile_id is null then return new; end if;
 with touched as (update public.blink_boost_touches t set profile_visit_at=coalesce(t.profile_visit_at,now()) from public.blink_boosts b where t.boost_id=b.id and t.viewer_id=new.viewer_id and b.user_id=new.profile_id and t.profile_visit_at is null and t.last_touched_at>now()-interval '24 hours' returning b.id) select array_agg(id) into v_ids from touched;
 if v_ids is not null then update public.blink_boosts set profile_visits=profile_visits+1 where id=any(v_ids); end if; return new;
end $$;
drop trigger if exists trg_track_blink_boost_profile_visit on public.profile_views;
create trigger trg_track_blink_boost_profile_visit after insert on public.profile_views for each row execute function private.track_blink_boost_profile_visit();

create or replace function private.track_blink_boost_follow() returns trigger
language plpgsql security definer set search_path=''
as $$ declare v_ids uuid[]; begin
 with touched as (update public.blink_boost_touches t set follow_at=coalesce(t.follow_at,now()) from public.blink_boosts b where t.boost_id=b.id and t.viewer_id=new.follower_id and b.user_id=new.following_id and t.follow_at is null and t.last_touched_at>now()-interval '7 days' returning b.id) select array_agg(id) into v_ids from touched;
 if v_ids is not null then update public.blink_boosts set followers_attributed=followers_attributed+1 where id=any(v_ids); end if; return new;
end $$;
drop trigger if exists trg_track_blink_boost_follow on public.follows;
create trigger trg_track_blink_boost_follow after insert on public.follows for each row execute function private.track_blink_boost_follow();

create or replace function private.expire_blink_items_and_remind_vip() returns void
language plpgsql security definer set search_path=''
as $$ begin
 update public.blink_inventory set status='EXPIRED',updated_at=now() where status='ACTIVE' and expires_at is not null and expires_at<=now();
 update public.blink_boosts set status='ENDED' where status='ACTIVE' and ends_at<=now();
 insert into public.notifications(user_id,type,comment) select v.user_id,'system','👑 Your Blink VIP expires in less than 24 hours. Open Blink Store to review or renew.' from public.blink_vip_passes v where v.expires_at>now() and v.expires_at<=now()+interval '24 hours' and not v.expiry_reminder_sent;
 update public.blink_vip_passes set expiry_reminder_sent=true where expires_at>now() and expires_at<=now()+interval '24 hours' and not expiry_reminder_sent;
 update public.profiles p set blink_vip_until=(select max(v.expires_at) from public.blink_vip_passes v where v.user_id=p.id and v.expires_at>now()) where p.blink_vip_until is not null and p.blink_vip_until<=now();
end $$;

create or replace function private.process_blink_vip_auto_renewals() returns void
language plpgsql security definer set search_path=''
as $$ declare r record; v_price integer:=350; v_balance bigint; v_pass uuid; begin
 for r in select distinct on(user_id) id,user_id,expires_at from public.blink_vip_passes where auto_renew=true and expires_at<=now() and expires_at>now()-interval '2 hours' order by user_id,expires_at desc loop
   if exists(select 1 from public.blink_vip_passes n where n.user_id=r.user_id and n.starts_at>=r.expires_at and n.expires_at>r.expires_at) then continue; end if;
   insert into public.user_balances(user_id,spendable_coin_balance) values(r.user_id,0) on conflict(user_id) do nothing;
   select spendable_coin_balance into v_balance from public.user_balances where user_id=r.user_id for update;
   if v_balance>=v_price then
     update public.user_balances set spendable_coin_balance=spendable_coin_balance-v_price,updated_at=now() where user_id=r.user_id returning spendable_coin_balance into v_balance;
     v_pass:=private.activate_blink_vip_pass(r.user_id,null,null); update public.blink_vip_passes set auto_renew=true where id=v_pass;
     insert into public.blink_coin_transactions(user_id,kind,catalog_id,item_name,amount,balance_after) values(r.user_id,'VIP_AUTO_RENEW','blink_vip_10d','Blink VIP — 10 Days',-v_price,v_balance);
     insert into public.notifications(user_id,type,comment) values(r.user_id,'system','👑 Blink VIP renewed for another 10 days.');
   else
     update public.blink_vip_passes set auto_renew=false where id=r.id;
     insert into public.notifications(user_id,type,comment) values(r.user_id,'system','Blink VIP auto-renew could not complete because your Blink Coin balance is too low.');
   end if;
 end loop;
end $$;

select cron.unschedule(jobid) from cron.job where jobname in ('blink-vip-expiry-maintenance','blink-vip-auto-renew');
select cron.schedule('blink-vip-expiry-maintenance','17 * * * *',$$select private.expire_blink_items_and_remind_vip();$$);
select cron.schedule('blink-vip-auto-renew','27 * * * *',$$select private.process_blink_vip_auto_renewals();$$);
