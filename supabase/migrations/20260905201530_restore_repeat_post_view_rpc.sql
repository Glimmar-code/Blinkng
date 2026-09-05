-- Restore the mutating post-view RPC used by the Android client.
--
-- A later backend migration changed record_post_view(...) into a read-only
-- compatibility function while the shipped Android client continued calling it.
-- That made visible posts remain at 0 views. Each authenticated call now records
-- a repeat impression again (up to 100 impressions per viewer/post), preserving
-- the existing weighted-view rules for blue/gold verification.

begin;

create or replace function public.record_post_view(p_post_id uuid)
returns integer
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_viewer_id uuid := auth.uid();
    v_weight integer := 1;
    v_impression_count integer;
    v_new_views integer;
begin
    if v_viewer_id is null then
        raise exception 'AUTHENTICATION_REQUIRED';
    end if;

    select case
        when coalesce(p.is_verified, false)
             and upper(coalesce(p.verification_badge, 'NONE')) = 'GOLD' then 6
        when coalesce(p.is_verified, false)
             and upper(coalesce(p.verification_badge, 'NONE')) = 'BLUE' then 3
        else 1
    end
    into v_weight
    from public.profiles p
    where p.id = v_viewer_id;

    v_weight := coalesce(v_weight, 1);

    select fp.view_count
    into v_new_views
    from public.feed_posts fp
    where fp.id = p_post_id
      and fp.is_active = true
    for update;

    if not found then
        return 0;
    end if;

    insert into public.post_views as pv (
        post_id,
        viewer_id,
        view_weight,
        impression_count,
        weighted_view_count,
        last_viewed_at
    )
    values (
        p_post_id,
        v_viewer_id,
        v_weight,
        1,
        v_weight,
        now()
    )
    on conflict (post_id, viewer_id) do update
    set impression_count = pv.impression_count + 1,
        weighted_view_count = pv.weighted_view_count + excluded.view_weight,
        view_weight = excluded.view_weight,
        last_viewed_at = now()
    where pv.impression_count < 100
    returning impression_count into v_impression_count;

    if not found then
        return coalesce(v_new_views, 0);
    end if;

    update public.feed_posts
    set view_count = coalesce(view_count, 0) + v_weight,
        updated_at = now()
    where id = p_post_id
    returning view_count into v_new_views;

    if v_impression_count = 1 then
        begin
            perform public.award_points(v_viewer_id, 'view_post', p_post_id);
        exception
            when others then null;
        end;
    end if;

    begin
        perform public.award_repost_distribution_points(p_post_id, v_viewer_id, 'view');
    exception
        when others then null;
    end;

    return coalesce(v_new_views, 0);
end;
$$;

create or replace function public.record_post_view(
    p_post_id uuid,
    p_viewer_username text
)
returns integer
language sql
volatile
security invoker
set search_path = ''
as $$
    select public.record_post_view(p_post_id);
$$;

revoke execute on function public.record_post_view(uuid) from public, anon;
revoke execute on function public.record_post_view(uuid, text) from public, anon;
grant execute on function public.record_post_view(uuid) to authenticated;
grant execute on function public.record_post_view(uuid, text) to authenticated;

commit;
