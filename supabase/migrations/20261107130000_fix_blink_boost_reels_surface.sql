-- Keep Boost placement and delivery surfaces consistent with the Android and Windows clients.
-- REELS is already an allowed delivery-event surface; promoted-slot retrieval must accept it too.
begin;

create or replace function public.get_blink_promoted_slots(
    p_surface text default 'HOME',
    p_limit integer default 3
) returns jsonb
language plpgsql
stable
security definer
set search_path=''
as $$
declare
    v_user uuid := auth.uid();
    v_surface text := upper(coalesce(p_surface,'HOME'));
    v_limit integer := greatest(1,least(coalesce(p_limit,3),10));
    v_items jsonb := '[]'::jsonb;
begin
    if v_user is null then raise exception 'AUTH_REQUIRED'; end if;
    if v_surface not in ('HOME','SEARCH','DISCOVER','MISSIONS','REELS') then
        raise exception 'INVALID_BOOST_SURFACE';
    end if;

    select coalesce(jsonb_agg(x.payload order by x.delivery_score desc,x.starts_at asc),'[]'::jsonb)
      into v_items
    from (
        select
            c.starts_at,
            (c.boost_power::numeric + least(25,extract(epoch from (now()-c.starts_at))/21600)) as delivery_score,
            jsonb_build_object(
                'campaign_id',c.id,
                'target_type',c.target_type,
                'target_id',c.target_id,
                'objective',c.objective,
                'boost_power',c.boost_power,
                'ad_label','Promoted',
                'surface',v_surface,
                'owner',jsonb_build_object(
                    'id',owner.id,
                    'username',owner.username,
                    'full_name',owner.full_name,
                    'avatar_url',owner.avatar_url,
                    'verification_badge',owner.verification_badge,
                    'university',owner.university
                ),
                'post',case when c.target_type in ('POST','REEL') then (
                    select to_jsonb(fp) from public.feed_posts fp where fp.id=c.target_id and fp.is_active=true
                ) else null end,
                'profile',case when c.target_type='PROFILE' then (
                    select jsonb_build_object(
                        'id',p.id,'username',p.username,'full_name',p.full_name,
                        'avatar_url',p.avatar_url,'verification_badge',p.verification_badge,
                        'university',p.university,'faculty',p.faculty,'department',p.department,
                        'follower_count',p.follower_count,'bio',p.bio
                    ) from public.profiles p where p.id=c.target_id
                ) else null end,
                'listing',case when c.target_type='LISTING' then (
                    select to_jsonb(m) from public.market_items m
                    where m.id=c.target_id and coalesce(m.is_sold,false)=false
                ) else null end
            ) as payload
        from public.blink_boost_campaigns_v2 c
        join public.profiles owner on owner.id=c.user_id
        where private.blink_boost_viewer_matches(c,v_user)
          and (
            (c.target_type in ('POST','REEL') and exists(
                select 1 from public.feed_posts fp where fp.id=c.target_id and fp.is_active=true
            ))
            or (c.target_type='PROFILE' and exists(
                select 1 from public.profiles p where p.id=c.target_id
            ))
            or (c.target_type='LISTING' and exists(
                select 1 from public.market_items m where m.id=c.target_id and coalesce(m.is_sold,false)=false
            ))
          )
        order by delivery_score desc,c.starts_at asc
        limit v_limit
    ) x;

    return jsonb_build_object('surface',v_surface,'items',v_items);
end;
$$;

revoke all on function public.get_blink_promoted_slots(text,integer) from public, anon;
grant execute on function public.get_blink_promoted_slots(text,integer) to authenticated;

commit;
