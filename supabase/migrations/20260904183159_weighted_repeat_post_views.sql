begin;

-- Keep one atomic counter per viewer/post. A viewer may contribute at most
-- 100 qualified impressions, with the multiplier resolved from protected profile data.
alter table public.post_views
    add column if not exists impression_count integer,
    add column if not exists weighted_view_count integer,
    add column if not exists last_viewed_at timestamptz;

update public.post_views
set impression_count = coalesce(impression_count, 1),
    weighted_view_count = coalesce(weighted_view_count, greatest(view_weight, 1)),
    last_viewed_at = coalesce(last_viewed_at, created_at, now())
where impression_count is null
   or weighted_view_count is null
   or last_viewed_at is null;

alter table public.post_views
    alter column impression_count set default 1,
    alter column impression_count set not null,
    alter column weighted_view_count set default 1,
    alter column weighted_view_count set not null,
    alter column last_viewed_at set default now(),
    alter column last_viewed_at set not null;

alter table public.post_views
    drop constraint if exists post_views_impression_count_check,
    drop constraint if exists post_views_weighted_view_count_check;

alter table public.post_views
    add constraint post_views_impression_count_check
        check (impression_count between 1 and 100),
    add constraint post_views_weighted_view_count_check
        check (weighted_view_count >= impression_count);

create unique index if not exists uniq_post_view
    on public.post_views(post_id, viewer_id);

-- Direct writes could forge badge weights or skip the per-user cap. All writes go through RPC.
drop policy if exists post_views_insert_own on public.post_views;
revoke insert, update, delete on table public.post_views from anon, authenticated;

create or replace function public.record_post_view(p_post_id uuid)
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
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

    -- This lock makes the counter update and the public total one atomic operation.
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

    -- No row is returned when this viewer has already reached 100 impressions.
    if not found then
        return coalesce(v_new_views, 0);
    end if;

    update public.feed_posts
    set view_count = coalesce(view_count, 0) + v_weight,
        updated_at = now()
    where id = p_post_id
    returning view_count into v_new_views;

    -- Preserve the existing engagement reward without making repeat scrolling a points farm.
    if v_impression_count = 1 then
        begin
            perform public.award_points(v_viewer_id, 'view_post', p_post_id);
        exception
            when undefined_function then null;
        end;
    end if;

    return coalesce(v_new_views, 0);
end;
$$;

-- Compatibility for older app builds. The username argument is intentionally ignored.
create or replace function public.record_post_view(
    p_post_id uuid,
    p_viewer_username text
)
returns integer
language sql
security invoker
set search_path = public, pg_temp
as $$
    select public.record_post_view(p_post_id);
$$;

revoke execute on function public.record_post_view(uuid) from public, anon;
revoke execute on function public.record_post_view(uuid, text) from public, anon;
grant execute on function public.record_post_view(uuid) to authenticated;
grant execute on function public.record_post_view(uuid, text) to authenticated;

commit;
