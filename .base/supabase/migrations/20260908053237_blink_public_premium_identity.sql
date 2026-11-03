create or replace function public.get_blink_public_premium_style(p_username text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_requester uuid := auth.uid();
  v_user_id uuid;
  v_username text;
  v_is_vip boolean := false;
  v_items jsonb := '[]'::jsonb;
begin
  if v_requester is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  select p.id, p.username, private.is_blink_vip_id(p.id, now())
    into v_user_id, v_username, v_is_vip
  from public.profiles p
  where lower(p.username) = lower(trim(leading '@' from coalesce(p_username, '')))
  limit 1;

  if v_user_id is null then
    return jsonb_build_object(
      'found', false,
      'username', trim(leading '@' from coalesce(p_username, '')),
      'is_vip', false,
      'items', '[]'::jsonb
    );
  end if;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'catalog_id', s.catalog_id,
        'source', s.source,
        'expires_at', s.expires_at
      )
      order by s.sort_rank desc, s.expires_at desc nulls first
    ),
    '[]'::jsonb
  )
  into v_items
  from (
    select distinct on (q.catalog_id)
      q.catalog_id,
      q.source,
      q.expires_at,
      q.sort_rank
    from (
      select
        e.catalog_id,
        'equipped'::text as source,
        null::timestamptz as expires_at,
        extract(epoch from e.equipped_at)::bigint as sort_rank
      from public.blink_equipped_items e
      join public.blink_store_catalog c on c.id = e.catalog_id and c.is_active = true
      where e.user_id = v_user_id
        and e.catalog_id = any(array[
          'profile_ring','profile_background','animated_profile_ring',
          'profile_entrance_animation','username_font','custom_profile_badge',
          'animated_name','premium_profile_frame','creator_badge','profile_theme_bundle'
        ]::text[])
        and (not c.vip_only or v_is_vip)

      union all

      select
        a.catalog_id,
        'active'::text as source,
        a.expires_at,
        extract(epoch from coalesce(a.started_at, now()))::bigint as sort_rank
      from public.blink_item_activations a
      join public.blink_store_catalog c on c.id = a.catalog_id and c.is_active = true
      where a.user_id = v_user_id
        and a.ended_at is null
        and a.expires_at is not null
        and a.expires_at > now()
        and a.catalog_id = any(array[
          'profile_highlight_1h','profile_glow_1h','username_glow_24h',
          'profile_spotlight_1h','profile_discovery_boost','profile_theme_3d',
          'market_seller_spotlight','profile_spotlight_24h','profile_glow_7d',
          'discovery_boost_7d'
        ]::text[])
        and (not c.vip_only or v_is_vip)
    ) q
    order by q.catalog_id, q.sort_rank desc
  ) s;

  return jsonb_build_object(
    'found', true,
    'username', v_username,
    'is_vip', v_is_vip,
    'items', v_items
  );
end
$$;

revoke all on function public.get_blink_public_premium_style(text) from public;
revoke all on function public.get_blink_public_premium_style(text) from anon;
grant execute on function public.get_blink_public_premium_style(text) to authenticated;
