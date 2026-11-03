-- Blink Store public Collection + 70-item catalog expansion.
-- Public Collection exposes ownership only: never balances, quantities, spend totals,
-- private analytics, gift messages, inventory ids, or targeted content ids.

update public.blink_store_catalog c
set description = v.description,
    updated_at = now()
from (values
('profile_highlight_1h','Feature your profile in eligible Connect/Search discovery with a visible Highlighted treatment for one hour.'),
('comment_highlight','Give one selected comment a premium animated highlight so it stands out in its thread.'),
('comment_color','Unlock a recognizable premium accent style for your comments and replies.'),
('animated_like','Unlock a premium animated Blink like effect visible during supported interactions.'),
('profile_glow_1h','Add a premium animated glow around your profile identity for one hour.'),
('chat_bubble_theme','Unlock a premium message-bubble design for supported conversations.'),
('reaction_pack','Unlock exclusive animated reactions other people can receive and see.'),
('profile_ring','Unlock an equipable premium ring around your profile picture across supported Blink surfaces.'),
('username_glow_24h','Make your display name glow on supported identity surfaces for 24 hours.'),
('post_border','Apply a premium animated frame to one selected post.'),
('story_highlight','Give one story a premium highlight treatment and opening effect.'),
('profile_background','Unlock a premium public profile background theme.'),
('emoji_pack','Unlock Blink-exclusive emoji for supported conversations.'),
('animated_profile_ring','Unlock a moving premium avatar ring visible across supported identity surfaces.'),
('chat_background','Unlock a premium conversation background theme.'),
('profile_entrance_animation','Play a premium entrance animation when another person opens your profile.'),
('post_highlight_1h','Visually highlight one selected post for one hour.'),
('reel_highlight_1h','Visually highlight one selected Reel for one hour.'),
('visitor_insights_24h','Unlock private aggregate profile-visitor analytics for 24 hours while ownership remains visible in your Blink Collection.'),
('notification_sound_pack','Unlock premium Blink notification sounds; ownership appears in your public Blink Collection.'),
('app_icon_pack','Unlock premium Blink launcher icons; ownership appears in your public Blink Collection.'),
('profile_spotlight_1h','Place your profile in eligible Spotlight discovery surfaces for one hour.'),
('username_font','Unlock an equipable premium display-name font on supported identity surfaces.'),
('sticker_pack','Unlock premium animated stickers other people can receive in supported conversations.'),
('digital_gift','Send a collectible Blink digital gift to another user.'),
('post_boost','Increase eligible distribution opportunity for one post with selectable 1x, 2x, 3x or 5x strength.'),
('reel_boost','Increase eligible distribution opportunity for one Reel with selectable 1x, 2x, 3x or 5x strength.'),
('market_listing_highlight','Give one Marketplace listing a premium highlighted card and featured treatment.'),
('profile_discovery_boost','Increase eligible profile-discovery opportunities for six hours without guaranteeing ranking.'),
('custom_profile_badge','Unlock an equipable cosmetic profile badge that stays distinct from verification.'),
('profile_theme_3d','Transform your public profile with a premium coordinated theme for three days.'),
('animated_name','Unlock an equipable animated display-name treatment.'),
('special_dm_theme','Unlock a premium direct-message theme for supported conversations.'),
('post_spotlight_6h','Place one selected post in eligible Spotlight surfaces for six hours.'),
('reel_spotlight_6h','Place one selected Reel in eligible Reel Spotlight surfaces for six hours.'),
('market_seller_spotlight','Feature your Marketplace seller storefront for six hours.'),
('profile_spotlight_24h','Keep your profile prominently featured in eligible discovery surfaces for 24 hours.'),
('post_boost_plus','One-tap ready-to-use 2x six-hour distribution boost for one post.'),
('reel_boost_plus','One-tap ready-to-use 2x six-hour distribution boost for one Reel.'),
('vip_theme','Unlock a visibly premium VIP theme while Blink VIP is active.'),
('profile_glow_7d','Keep a premium animated profile-identity glow active for seven days.'),
('premium_profile_frame','Unlock a premium avatar frame visible on supported public identity surfaces.'),
('creator_badge','Unlock a public Blink Creator identity badge that remains distinct from verification.'),
('post_spotlight_24h','Prominently feature one selected post for 24 hours.'),
('reel_spotlight_24h','Prominently feature one selected Reel for 24 hours.'),
('discovery_boost_7d','Increase eligible profile-discovery opportunities across seven days without fake engagement.'),
('profile_theme_bundle','Permanently unlock a collection of premium public profile themes.'),
('creator_promo_bundle','Combine creator Spotlight and post/Reel boost credits in one promotion package.'),
('market_promo_bundle','Combine seller Spotlight and Marketplace listing-highlight promotion credits.'),
('blink_vip_10d','Ten days of VIP identity styling, Store discount, analytics and claimable promotion benefits.')
) as v(id, description)
where c.id = v.id;

insert into public.blink_store_catalog(
  id,name,description,icon_key,category,price,item_type,target_type,duration_seconds,stackable,vip_only,boost_multipliers,sort_order,metadata
) values
('super_reaction','Super Reaction','Unlock an oversized animated premium reaction visible to people you interact with.','favorite','Social',35,'PERMANENT','APP',null,false,false,'{}',51,'{"public_collection":true}'),
('profile_banner','Premium Profile Banner','Unlock an animated premium banner behind your public profile header.','wallpaper','Profile',85,'PERMANENT','PROFILE',null,false,false,'{}',52,'{"public_collection":true}'),
('avatar_decoration','Avatar Decoration','Unlock a floating premium decoration around your profile picture, separate from your frame or ring.','auto_awesome','Profile',90,'PERMANENT','PROFILE',null,false,false,'{}',53,'{"public_collection":true}'),
('reel_frame_effect','Reel Frame Effect','Apply a premium animated border to one selected Reel.','smart_display','Reels',45,'CONTENT_SPECIFIC','REEL',null,true,false,'{}',54,'{"public_collection":true}'),
('post_entrance_animation','Post Entrance Animation','Give one selected post a premium entrance animation when it is opened.','animation','Posts',45,'CONTENT_SPECIFIC','POST',null,true,false,'{}',55,'{"public_collection":true}'),
('profile_particle_effect','Profile Particle Effect','Unlock subtle premium particles and motion on your public profile.','auto_awesome','Profile',95,'PERMANENT','PROFILE',null,false,false,'{}',56,'{"public_collection":true}'),
('comment_entrance_animation','Comment Entrance Animation','Give one selected comment a premium animated entrance treatment.','animation','Social',30,'CONTENT_SPECIFIC','COMMENT',null,true,false,'{}',57,'{"public_collection":true}'),
('follow_animation','Exclusive Follow Animation','Unlock a premium follow interaction effect recipients can notice.','person_search','Social',65,'PERMANENT','PROFILE',null,false,false,'{}',58,'{"public_collection":true}'),
('birthday_profile_theme','Birthday Profile Theme — 24 hours','Activate a celebration profile theme visible for 24 hours.','redeem','Profile',40,'TIMED','PROFILE',86400,true,false,'{}',59,'{"public_collection":true}'),
('limited_edition_badge','Limited Edition Badge','Own an equipable seasonal collectible badge that stays in your Blink Collection permanently.','workspace_premium','Collectibles',100,'PERMANENT','PROFILE',null,false,false,'{}',60,'{"public_collection":true,"limited_edition":true}'),
('gift_crown','Gift Crown Collectible','Own and display a premium crown collectible in your public Blink Collection.','redeem','Collectibles',75,'PERMANENT','PROFILE',null,false,false,'{}',61,'{"public_collection":true,"collectible":true}'),
('gift_rose','Gift Rose Collectible','Own and display an animated rose collectible in your public Blink Collection.','redeem','Collectibles',30,'PERMANENT','PROFILE',null,false,false,'{}',62,'{"public_collection":true,"collectible":true}'),
('gift_trophy','Gift Trophy Collectible','Own and display a premium trophy collectible in your public Blink Collection.','emoji_events','Collectibles',60,'PERMANENT','PROFILE',null,false,false,'{}',63,'{"public_collection":true,"collectible":true}'),
('gift_galaxy','Gift Galaxy Collectible','Own and display a high-tier animated galaxy collectible in your public Blink Collection.','auto_awesome','Collectibles',150,'PERMANENT','PROFILE',null,false,false,'{}',64,'{"public_collection":true,"collectible":true}'),
('profile_music_theme','Profile Music Theme','Unlock an optional short profile soundtrack visitors can choose to play; no forced autoplay.','music_note','Profile',100,'PERMANENT','PROFILE',null,false,false,'{}',65,'{"public_collection":true}'),
('creator_intro_card','Creator Intro Card','Unlock an animated creator introduction card for first-time profile visitors.','campaign','Creator',120,'PERMANENT','PROFILE',null,false,false,'{}',66,'{"public_collection":true}'),
('premium_poll_style','Premium Poll Style','Unlock premium poll backgrounds and motion styles for supported posts.','palette','Posts',65,'PERMANENT','APP',null,false,false,'{}',67,'{"public_collection":true}'),
('vip_comment_effect','VIP Comment Effect','Unlock an exclusive premium comment treatment while Blink VIP is active.','diamond','VIP',50,'PERMANENT','COMMENT',null,false,true,'{}',68,'{"public_collection":true}'),
('vip_reaction_pack','VIP Reaction Pack','Unlock exclusive VIP reactions while your Blink VIP pass is active.','diamond','VIP',70,'PERMANENT','CHAT',null,false,true,'{}',69,'{"public_collection":true}'),
('vip_profile_entrance','VIP Profile Entrance','Unlock an advanced premium profile entrance while Blink VIP is active.','diamond','VIP',140,'PERMANENT','PROFILE',null,false,true,'{}',70,'{"public_collection":true}')
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
  is_active=true,
  sort_order=excluded.sort_order,
  metadata=public.blink_store_catalog.metadata || excluded.metadata,
  updated_at=now();

-- Every Store purchase has public-safe ownership visibility, including private-utility products.
update public.blink_store_catalog
set metadata = metadata || '{"public_collection":true}'::jsonb,
    updated_at = now()
where is_active = true;

-- Canonical equipment slots for the new public cosmetics and mutually-exclusive badge/entrance styles.
create or replace function public.set_blink_item_equipped(p_inventory_id uuid,p_slot text,p_enabled boolean) returns jsonb
language plpgsql security definer set search_path=''
as $$
declare
  v_user uuid:=auth.uid();
  v_inv public.blink_inventory%rowtype;
  v_item public.blink_store_catalog%rowtype;
  v_slot text;
  v_conflicts text[];
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;

  select * into v_inv from public.blink_inventory where id=p_inventory_id and user_id=v_user;
  if not found then raise exception 'INVENTORY_NOT_FOUND'; end if;

  select * into v_item from public.blink_store_catalog where id=v_inv.catalog_id;
  if not found then raise exception 'ITEM_NOT_FOUND'; end if;
  if v_item.item_type<>'PERMANENT' then raise exception 'NOT_PERMANENT'; end if;
  if v_item.vip_only and not private.is_blink_vip_id(v_user,now()) then raise exception 'VIP_REQUIRED'; end if;

  v_slot:=case
    when v_item.id in ('profile_ring','animated_profile_ring','premium_profile_frame') then 'profile_frame'
    when v_item.id in ('profile_background','profile_theme_bundle') then 'profile_theme'
    when v_item.id in ('username_font','animated_name') then 'name_style'
    when v_item.id in ('custom_profile_badge','creator_badge','limited_edition_badge') then 'profile_badge'
    when v_item.id in ('profile_entrance_animation','vip_profile_entrance') then 'profile_entrance'
    when v_item.id in ('chat_bubble_theme','special_dm_theme') then 'chat_theme'
    when v_item.id='profile_banner' then 'profile_banner'
    when v_item.id='avatar_decoration' then 'avatar_decoration'
    when v_item.id='profile_particle_effect' then 'profile_particle_effect'
    when v_item.id='follow_animation' then 'follow_animation'
    when v_item.id='profile_music_theme' then 'profile_music'
    when v_item.id='creator_intro_card' then 'creator_intro'
    when v_item.id='premium_poll_style' then 'poll_style'
    when v_item.id='vip_comment_effect' then 'vip_comment_effect'
    when v_item.id='vip_reaction_pack' then 'vip_reaction_pack'
    else left(coalesce(nullif(trim(p_slot),''),v_item.id),80)
  end;

  v_conflicts:=case v_slot
    when 'profile_frame' then array['profile_ring','animated_profile_ring','premium_profile_frame']::text[]
    when 'profile_theme' then array['profile_background','profile_theme_bundle']::text[]
    when 'name_style' then array['username_font','animated_name']::text[]
    when 'profile_badge' then array['custom_profile_badge','creator_badge','limited_edition_badge']::text[]
    when 'profile_entrance' then array['profile_entrance_animation','vip_profile_entrance']::text[]
    when 'chat_theme' then array['chat_bubble_theme','special_dm_theme']::text[]
    else array[v_item.id]::text[]
  end;

  if p_enabled then
    delete from public.blink_equipped_items
    where user_id=v_user and catalog_id=any(v_conflicts) and inventory_id<>v_inv.id;

    delete from public.blink_equipped_items
    where user_id=v_user and inventory_id=v_inv.id and slot<>v_slot;

    insert into public.blink_equipped_items(user_id,slot,inventory_id,catalog_id)
    values(v_user,v_slot,v_inv.id,v_inv.catalog_id)
    on conflict(user_id,slot) do update
      set inventory_id=excluded.inventory_id,
          catalog_id=excluded.catalog_id,
          equipped_at=now();
  else
    delete from public.blink_equipped_items where user_id=v_user and inventory_id=v_inv.id;
  end if;

  return jsonb_build_object('success',true,'equipped',p_enabled,'slot',v_slot);
end $$;

revoke all on function public.set_blink_item_equipped(uuid,text,boolean) from public;
revoke all on function public.set_blink_item_equipped(uuid,text,boolean) from anon;
grant execute on function public.set_blink_item_equipped(uuid,text,boolean) to authenticated;

-- Full public-safe Collection list. This deliberately omits coin values, quantities,
-- exact acquisition timestamps, gift messages, inventory ids and target ids.
create or replace function public.get_blink_public_collection(p_username text)
returns jsonb
language plpgsql security definer set search_path=''
as $$
declare
  v_requester uuid:=auth.uid();
  v_user_id uuid;
  v_username text;
  v_is_vip boolean:=false;
  v_items jsonb:='[]'::jsonb;
  v_count integer:=0;
  v_level integer:=0;
begin
  if v_requester is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;

  select p.id,p.username,private.is_blink_vip_id(p.id,now())
    into v_user_id,v_username,v_is_vip
  from public.profiles p
  where lower(p.username)=lower(trim(leading '@' from coalesce(p_username,'')))
  limit 1;

  if v_user_id is null then
    return jsonb_build_object('found',false,'username',trim(leading '@' from coalesce(p_username,'')),'collection_count',0,'collection_level',0,'is_vip',false,'items','[]'::jsonb);
  end if;

  select coalesce(jsonb_agg(
    jsonb_build_object(
      'catalog_id',x.catalog_id,
      'name',x.name,
      'icon_key',x.icon_key,
      'category',x.category,
      'state',x.state,
      'equipped',x.equipped,
      'active',x.active,
      'limited_edition',x.limited_edition,
      'received_gift',x.received_gift
    ) order by x.sort_order,x.name
  ),'[]'::jsonb)
  into v_items
  from (
    select
      c.id as catalog_id,
      c.name,
      c.icon_key,
      c.category,
      c.sort_order,
      case
        when bool_or(s.active) then 'ACTIVE'
        when bool_or(s.equipped) then 'EQUIPPED'
        when bool_or(s.received_gift) then 'RECEIVED'
        else 'OWNED'
      end as state,
      bool_or(s.equipped) as equipped,
      bool_or(s.active) as active,
      coalesce((c.metadata->>'limited_edition')::boolean,false) as limited_edition,
      bool_or(s.received_gift) as received_gift
    from (
      select
        i.catalog_id,
        exists(select 1 from public.blink_equipped_items e where e.user_id=v_user_id and e.inventory_id=i.id) as equipped,
        (i.status='ACTIVE' and (i.expires_at is null or i.expires_at>now())) as active,
        false as received_gift
      from public.blink_inventory i
      where i.user_id=v_user_id

      union all

      select
        'digital_gift'::text as catalog_id,
        false as equipped,
        false as active,
        true as received_gift
      from public.blink_digital_gifts g
      where g.recipient_id=v_user_id
    ) s
    join public.blink_store_catalog c on c.id=s.catalog_id and c.is_active=true
    where coalesce((c.metadata->>'public_collection')::boolean,true)
    group by c.id,c.name,c.icon_key,c.category,c.sort_order,c.metadata
  ) x;

  v_count:=jsonb_array_length(v_items);
  v_level:=case when v_count=0 then 0 else 1+floor((v_count-1)/5.0)::integer end;

  return jsonb_build_object(
    'found',true,
    'username',v_username,
    'is_vip',v_is_vip,
    'collection_count',v_count,
    'collection_level',v_level,
    'items',v_items
  );
end $$;

revoke all on function public.get_blink_public_collection(text) from public;
revoke all on function public.get_blink_public_collection(text) from anon;
grant execute on function public.get_blink_public_collection(text) to authenticated;

-- Lightweight identity endpoint used on feed/chat/profile names. It returns only a
-- Collection count/level; the full Collection is fetched only when the chip is opened.
create or replace function public.get_blink_public_premium_style(p_username text)
returns jsonb
language plpgsql security definer set search_path=''
as $$
declare
  v_requester uuid:=auth.uid();
  v_user_id uuid;
  v_username text;
  v_is_vip boolean:=false;
  v_items jsonb:='[]'::jsonb;
  v_collection_count integer:=0;
  v_collection_level integer:=0;
begin
  if v_requester is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;

  select p.id,p.username,private.is_blink_vip_id(p.id,now())
    into v_user_id,v_username,v_is_vip
  from public.profiles p
  where lower(p.username)=lower(trim(leading '@' from coalesce(p_username,'')))
  limit 1;

  if v_user_id is null then
    return jsonb_build_object('found',false,'username',trim(leading '@' from coalesce(p_username,'')),'is_vip',false,'collection_count',0,'collection_level',0,'items','[]'::jsonb);
  end if;

  select count(*) into v_collection_count
  from (
    select i.catalog_id from public.blink_inventory i where i.user_id=v_user_id group by i.catalog_id
    union
    select 'digital_gift'::text from public.blink_digital_gifts g where g.recipient_id=v_user_id
  ) owned;
  v_collection_level:=case when v_collection_count=0 then 0 else 1+floor((v_collection_count-1)/5.0)::integer end;

  select coalesce(jsonb_agg(
    jsonb_build_object('catalog_id',s.catalog_id,'source',s.source,'expires_at',s.expires_at)
    order by s.sort_rank desc,s.expires_at desc nulls first
  ),'[]'::jsonb)
  into v_items
  from (
    select distinct on(q.catalog_id) q.catalog_id,q.source,q.expires_at,q.sort_rank
    from (
      select
        e.catalog_id,
        'equipped'::text as source,
        null::timestamptz as expires_at,
        extract(epoch from e.equipped_at)::bigint as sort_rank
      from public.blink_equipped_items e
      join public.blink_store_catalog c on c.id=e.catalog_id and c.is_active=true
      where e.user_id=v_user_id
        and e.catalog_id=any(array[
          'profile_ring','profile_background','animated_profile_ring','profile_entrance_animation',
          'username_font','custom_profile_badge','animated_name','premium_profile_frame','creator_badge',
          'profile_theme_bundle','profile_banner','avatar_decoration','profile_particle_effect','follow_animation',
          'limited_edition_badge','creator_intro_card','vip_profile_entrance'
        ]::text[])
        and (not c.vip_only or v_is_vip)

      union all

      select
        a.catalog_id,
        'active'::text as source,
        a.expires_at,
        extract(epoch from coalesce(a.started_at,now()))::bigint as sort_rank
      from public.blink_item_activations a
      join public.blink_store_catalog c on c.id=a.catalog_id and c.is_active=true
      where a.user_id=v_user_id
        and a.ended_at is null
        and a.expires_at is not null
        and a.expires_at>now()
        and a.catalog_id=any(array[
          'profile_highlight_1h','profile_glow_1h','username_glow_24h','profile_spotlight_1h',
          'profile_discovery_boost','profile_theme_3d','market_seller_spotlight','profile_spotlight_24h',
          'profile_glow_7d','discovery_boost_7d','birthday_profile_theme'
        ]::text[])
        and (not c.vip_only or v_is_vip)
    ) q
    order by q.catalog_id,q.sort_rank desc
  ) s;

  return jsonb_build_object(
    'found',true,
    'username',v_username,
    'is_vip',v_is_vip,
    'collection_count',v_collection_count,
    'collection_level',v_collection_level,
    'items',v_items
  );
end $$;

revoke all on function public.get_blink_public_premium_style(text) from public;
revoke all on function public.get_blink_public_premium_style(text) from anon;
grant execute on function public.get_blink_public_premium_style(text) to authenticated;
