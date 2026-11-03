-- Premium Store presentation contract. Purchases and activation remain server-authoritative;
-- only public-safe cosmetic ids are exposed to authenticated viewers.

update public.blink_store_catalog
set name = 'Profile Aura — 1 hour',
    description = 'Transform your complete public profile header with an animated aura, avatar light and coordinated identity accents for one hour.',
    metadata = metadata || jsonb_build_object(
      'presentation_name', 'Profile Aura',
      'presentation_surface', 'PROFILE_AURA',
      'presentation_motion', 'LIGHT_SWEEP',
      'full_surface', true
    ),
    updated_at = now()
where id = 'profile_highlight_1h';

update public.blink_store_catalog
set name = 'Comment Spotlight',
    description = 'Transform one selected comment into a complete premium card with an animated edge, avatar accent and reaction glow.',
    metadata = metadata || jsonb_build_object(
      'presentation_name', 'Comment Spotlight',
      'presentation_surface', 'COMMENT_SPOTLIGHT',
      'presentation_motion', 'EDGE_REVEAL',
      'full_surface', true
    ),
    updated_at = now()
where id = 'comment_highlight';

update public.blink_store_catalog
set name = 'Aurora Comment Style',
    description = 'Apply a coordinated Aurora surface to your comments and replies instead of a small cosmetic label.',
    metadata = metadata || jsonb_build_object(
      'presentation_name', 'Aurora Comment Style',
      'presentation_surface', 'COMMENT_SPOTLIGHT',
      'presentation_motion', 'GRADIENT_DRIFT',
      'full_surface', true
    ),
    updated_at = now()
where id = 'comment_color';

update public.blink_store_catalog
set name = 'Comment Premiere',
    description = 'Give one selected comment a polished premium entrance and complete highlighted surface.',
    metadata = metadata || jsonb_build_object(
      'presentation_name', 'Comment Premiere',
      'presentation_surface', 'COMMENT_SPOTLIGHT',
      'presentation_motion', 'SPRING_REVEAL',
      'full_surface', true
    ),
    updated_at = now()
where id = 'comment_entrance_animation';

-- get_post_comments is the existing protected batch endpoint. Returning the resolved style here
-- avoids an N+1 request for every comment and never exposes inventory ids, balances or purchases.
drop function if exists public.get_post_comments(uuid);

create function public.get_post_comments(p_post_id uuid)
returns table (
  id uuid,
  post_id uuid,
  parent_comment_id uuid,
  author_id uuid,
  content text,
  likes_count integer,
  created_at timestamptz,
  username text,
  display_name text,
  avatar_url text,
  verification_badge text,
  is_liked boolean,
  premium_style_id text,
  premium_style_source text
)
language plpgsql
stable
security definer
set search_path = ''
as $function$
declare
  v_actor uuid := auth.uid();
begin
  if v_actor is null then
    raise exception 'AUTHENTICATION_REQUIRED';
  end if;

  return query
  select
    c.id,
    c.post_id,
    c.parent_comment_id,
    c.author_id,
    c.content,
    c.likes_count,
    c.created_at,
    p.username,
    coalesce(
      nullif(btrim(p.full_name), ''),
      nullif(btrim(p.name), ''),
      nullif(btrim(p.username), ''),
      'Blink user'
    ) as display_name,
    coalesce(p.avatar_url, '') as avatar_url,
    case
      when upper(coalesce(p.verification_badge, '')) in ('BLUE', 'GOLD')
        then upper(p.verification_badge)
      when coalesce(p.is_verified, false) then 'BLUE'
      else 'NONE'
    end as verification_badge,
    exists (
      select 1
      from public.comment_likes cl
      where cl.comment_id = c.id
        and cl.user_id = v_actor
    ) as is_liked,
    coalesce(target_style.catalog_id, author_style.catalog_id) as premium_style_id,
    case
      when target_style.catalog_id is not null then 'TARGETED'
      when author_style.catalog_id is not null then 'EQUIPPED'
      else null
    end as premium_style_source
  from public.comments c
  join public.profiles p on p.id = c.author_id
  left join lateral (
    select a.catalog_id
    from public.blink_item_activations a
    join public.blink_store_catalog store_item
      on store_item.id = a.catalog_id
     and store_item.is_active = true
    where a.target_type = 'COMMENT'
      and a.target_id = c.id
      and a.ended_at is null
      and (a.expires_at is null or a.expires_at > now())
      and a.catalog_id in ('comment_highlight', 'comment_entrance_animation')
    order by
      case a.catalog_id
        when 'comment_highlight' then 2
        when 'comment_entrance_animation' then 1
        else 0
      end desc,
      a.started_at desc
    limit 1
  ) target_style on true
  left join lateral (
    select equipped.catalog_id
    from public.blink_equipped_items equipped
    join public.blink_store_catalog store_item
      on store_item.id = equipped.catalog_id
     and store_item.is_active = true
    where equipped.user_id = c.author_id
      and equipped.catalog_id in ('comment_color', 'vip_comment_effect')
      and (
        not store_item.vip_only
        or private.is_blink_vip_id(c.author_id, now())
      )
    order by
      case equipped.catalog_id
        when 'vip_comment_effect' then 2
        when 'comment_color' then 1
        else 0
      end desc,
      equipped.equipped_at desc
    limit 1
  ) author_style on true
  where c.post_id = p_post_id
    and not exists (
      select 1
      from public.blocks b
      where (b.blocker_id = v_actor and b.blocked_id = c.author_id)
         or (b.blocker_id = c.author_id and b.blocked_id = v_actor)
    )
  order by c.created_at asc, c.id asc;
end;
$function$;

revoke all on function public.get_post_comments(uuid) from public;
revoke all on function public.get_post_comments(uuid) from anon;
grant execute on function public.get_post_comments(uuid) to authenticated;
