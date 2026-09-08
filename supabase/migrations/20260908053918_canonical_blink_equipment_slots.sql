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

  select * into v_inv
  from public.blink_inventory
  where id=p_inventory_id and user_id=v_user;
  if not found then raise exception 'INVENTORY_NOT_FOUND'; end if;

  select * into v_item
  from public.blink_store_catalog
  where id=v_inv.catalog_id;
  if not found then raise exception 'ITEM_NOT_FOUND'; end if;
  if v_item.item_type<>'PERMANENT' then raise exception 'NOT_PERMANENT'; end if;
  if v_item.vip_only and not private.is_blink_vip_id(v_user,now()) then raise exception 'VIP_REQUIRED'; end if;

  v_slot:=case
    when v_item.id in ('profile_ring','animated_profile_ring','premium_profile_frame') then 'profile_frame'
    when v_item.id in ('profile_background','profile_theme_bundle') then 'profile_theme'
    when v_item.id in ('username_font','animated_name') then 'name_style'
    when v_item.id in ('custom_profile_badge','creator_badge') then 'profile_badge'
    when v_item.id in ('chat_bubble_theme','special_dm_theme') then 'chat_theme'
    else left(coalesce(nullif(trim(p_slot),''),v_item.id),80)
  end;

  v_conflicts:=case v_slot
    when 'profile_frame' then array['profile_ring','animated_profile_ring','premium_profile_frame']::text[]
    when 'profile_theme' then array['profile_background','profile_theme_bundle']::text[]
    when 'name_style' then array['username_font','animated_name']::text[]
    when 'profile_badge' then array['custom_profile_badge','creator_badge']::text[]
    when 'chat_theme' then array['chat_bubble_theme','special_dm_theme']::text[]
    else array[v_item.id]::text[]
  end;

  if p_enabled then
    delete from public.blink_equipped_items
    where user_id=v_user
      and catalog_id=any(v_conflicts)
      and inventory_id<>v_inv.id;

    delete from public.blink_equipped_items
    where user_id=v_user
      and inventory_id=v_inv.id
      and slot<>v_slot;

    insert into public.blink_equipped_items(user_id,slot,inventory_id,catalog_id)
    values(v_user,v_slot,v_inv.id,v_inv.catalog_id)
    on conflict(user_id,slot) do update
      set inventory_id=excluded.inventory_id,
          catalog_id=excluded.catalog_id,
          equipped_at=now();
  else
    delete from public.blink_equipped_items
    where user_id=v_user and inventory_id=v_inv.id;
  end if;

  return jsonb_build_object('success',true,'equipped',p_enabled,'slot',v_slot);
end $$;

revoke all on function public.set_blink_item_equipped(uuid,text,boolean) from public;
revoke all on function public.set_blink_item_equipped(uuid,text,boolean) from anon;
grant execute on function public.set_blink_item_equipped(uuid,text,boolean) to authenticated;
