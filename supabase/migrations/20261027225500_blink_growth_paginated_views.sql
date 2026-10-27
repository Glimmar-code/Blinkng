-- BLINK Growth: isolated authenticated pagination and exact Drop notification lookup.
-- Ownership and snapshotted eligibility are checked inside the privileged RPC.
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
    'audience_scope',p_drop.audience_scope,
    'target_university',coalesce(p_drop.target_university,''),
    'status',p_drop.status,
    'created_at',p_drop.created_at,
    'ends_at',p_drop.ends_at,
    'eligible',exists(select 1 from public.blink_drop_eligible_followers e
                       where e.drop_id=p_drop.id and e.user_id=auth.uid())
  );
$$;
revoke all on function private.blink_drop_safe_card(public.blink_drops) from public,anon,authenticated;

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
           and exists(select 1 from public.blink_drop_eligible_followers e
                      where e.drop_id=d.id and e.user_id=v_user)
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
revoke all on function public.get_blink_drop_page(text,integer,integer) from public,anon;
grant execute on function public.get_blink_drop_page(text,integer,integer) to authenticated;

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
    and (d.creator_id=v_user or exists(
      select 1 from public.blink_drop_eligible_followers e
      where e.drop_id=d.id and e.user_id=v_user));
  return coalesce(v_item,jsonb_build_object('not_found',true));
end;
$$;
revoke all on function public.get_blink_drop_by_id(uuid) from public,anon;
grant execute on function public.get_blink_drop_by_id(uuid) to authenticated;

-- Promote only active unsold inventory belonging to the signed-in seller.
-- Include media and trustworthy metrics for otherwise uncached owned content.
create or replace function public.get_my_blink_boostable_content_page(
  p_target_type text default 'POST',
  p_offset integer default 0,
  p_limit integer default 40
) returns jsonb language plpgsql stable security definer set search_path=''
as $$
declare
  v_user uuid := auth.uid();
  v_type text := upper(coalesce(p_target_type,'POST'));
  v_limit integer := greatest(1,least(coalesce(p_limit,40),50));
  v_offset integer := greatest(0,least(coalesce(p_offset,0),100000));
  v_items jsonb;
  v_more boolean;
begin
  if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
  if v_type not in ('POST','REEL','LISTING') then raise exception 'INVALID_BOOST_TYPE'; end if;
  if v_type='LISTING' then
    with rows as (
      select m.id,m.title,m.created_at,coalesce(nullif(m.image_url,''),nullif(m.image_urls[1],'')) as preview_url
      from public.market_items m
      where m.seller_id=v_user and lower(coalesce(m.status,'active'))='active'
        and coalesce(m.is_sold,false)=false
      order by m.created_at desc,m.id desc limit v_limit+1 offset v_offset
    )
    select coalesce((select jsonb_agg(jsonb_build_object(
      'id',r.id,'type','LISTING','text',r.title,'created_at',r.created_at,'image_url',coalesce(r.preview_url,'')
    ) order by r.created_at desc,r.id desc) from
    (select * from rows order by created_at desc,id desc limit v_limit) r),'[]'::jsonb),
    (select count(*)>v_limit from rows) into v_items,v_more;
  else
    with rows as (
      select f.id,f.is_reel,coalesce(nullif(f.text,''),f.caption,'') as body,
        f.created_at,f.view_count,f.like_count,f.comment_count,
        coalesce(nullif(f.image_url,''),nullif(f.images[1],'')) as preview_url
      from public.feed_posts f
      where f.user_id=v_user and f.is_active=true
        and coalesce(f.is_reel,false)=(v_type='REEL')
      order by f.created_at desc,f.id desc limit v_limit+1 offset v_offset
    )
    select coalesce((select jsonb_agg(jsonb_build_object(
      'id',r.id,'type',v_type,'text',left(r.body,180),'created_at',r.created_at,
      'image_url',coalesce(r.preview_url,''),'view_count',r.view_count,
      'like_count',r.like_count,'comment_count',r.comment_count
    ) order by r.created_at desc,r.id desc) from
    (select * from rows order by created_at desc,id desc limit v_limit) r),'[]'::jsonb),
    (select count(*)>v_limit from rows) into v_items,v_more;
  end if;
  return jsonb_build_object('items',v_items,'has_more',coalesce(v_more,false),'next_offset',v_offset+v_limit);
end;
$$;
revoke all on function public.get_my_blink_boostable_content_page(text,integer,integer) from public,anon;
grant execute on function public.get_my_blink_boostable_content_page(text,integer,integer) to authenticated;
