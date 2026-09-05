-- Preserve repeat-exposure counting and the 100-exposure cap while restoring
-- verification-weighted public views: normal=1, blue=3, gold=6.
alter table private.content_view_events
    add column if not exists view_weight smallint not null default 1;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'content_view_events_view_weight_check'
          and conrelid = 'private.content_view_events'::regclass
    ) then
        alter table private.content_view_events
            add constraint content_view_events_view_weight_check
            check (view_weight in (1, 3, 6));
    end if;
end;
$$;

create or replace function public.record_content_view(
    p_post_id uuid,
    p_event_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_viewer_id uuid := auth.uid();
    v_view_count integer := 0;
    v_user_view_count integer := 0;
    v_is_reel boolean := false;
    v_content_type text;
    v_existing_event boolean := false;
    v_new_user_view_count integer;
    v_view_weight integer := 1;
begin
    if v_viewer_id is null then
        raise exception 'AUTHENTICATION_REQUIRED' using errcode = '42501';
    end if;

    if p_event_id is null then
        raise exception 'EVENT_ID_REQUIRED' using errcode = '22004';
    end if;

    select case
        when upper(coalesce(p.verification_badge::text, '')) = 'GOLD'
          or upper(coalesce(p.verification_tier::text, '')) = 'GOLD' then 6
        when upper(coalesce(p.verification_badge::text, '')) = 'BLUE'
          or upper(coalesce(p.verification_tier::text, '')) = 'STANDARD'
          or coalesce(p.is_verified, false) then 3
        else 1
    end
    into v_view_weight
    from public.profiles p
    where p.id = v_viewer_id;

    v_view_weight := coalesce(v_view_weight, 1);

    -- Serialize per-content increments. This prevents concurrent requests from
    -- pushing one viewer beyond the 100-exposure cap.
    select coalesce(fp.view_count, 0), coalesce(fp.is_reel, false)
      into v_view_count, v_is_reel
      from public.feed_posts fp
     where fp.id = p_post_id
       and (fp.is_active = true or fp.user_id = v_viewer_id)
     for update;

    if not found then
        raise exception 'CONTENT_NOT_FOUND_OR_INACTIVE' using errcode = 'P0002';
    end if;

    v_content_type := case when v_is_reel then 'reel' else 'post' end;

    select exists (
        select 1
          from private.content_view_events e
         where e.event_id = p_event_id
           and e.viewer_id = v_viewer_id
           and e.post_id = p_post_id
    ) into v_existing_event;

    select coalesce(pv.impression_count, 0)
      into v_user_view_count
      from public.post_views pv
     where pv.post_id = p_post_id
       and pv.viewer_id = v_viewer_id;

    v_user_view_count := coalesce(v_user_view_count, 0);

    if v_existing_event then
        return jsonb_build_object(
            'accepted', false,
            'duplicate', true,
            'cap_reached', v_user_view_count >= 100,
            'view_count', v_view_count,
            'user_view_count', v_user_view_count,
            'view_weight', v_view_weight,
            'content_type', v_content_type,
            'event_id', p_event_id
        );
    end if;

    if v_user_view_count >= 100 then
        return jsonb_build_object(
            'accepted', false,
            'duplicate', false,
            'cap_reached', true,
            'view_count', v_view_count,
            'user_view_count', v_user_view_count,
            'view_weight', v_view_weight,
            'content_type', v_content_type,
            'event_id', p_event_id
        );
    end if;

    insert into private.content_view_events (
        event_id,
        viewer_id,
        post_id,
        content_type,
        view_weight
    ) values (
        p_event_id,
        v_viewer_id,
        p_post_id,
        v_content_type,
        v_view_weight
    )
    on conflict (event_id) do nothing;

    if not found then
        return jsonb_build_object(
            'accepted', false,
            'duplicate', true,
            'cap_reached', v_user_view_count >= 100,
            'view_count', v_view_count,
            'user_view_count', v_user_view_count,
            'view_weight', v_view_weight,
            'content_type', v_content_type,
            'event_id', p_event_id
        );
    end if;

    insert into public.post_views as pv (
        post_id,
        viewer_id,
        view_weight,
        impression_count,
        weighted_view_count,
        last_viewed_at
    ) values (
        p_post_id,
        v_viewer_id,
        v_view_weight,
        1,
        v_view_weight,
        now()
    )
    on conflict (post_id, viewer_id) do update
       set impression_count = pv.impression_count + 1,
           weighted_view_count = pv.weighted_view_count + v_view_weight,
           view_weight = v_view_weight,
           last_viewed_at = now()
     where pv.impression_count < 100
    returning impression_count into v_new_user_view_count;

    if v_new_user_view_count is null then
        select coalesce(pv.impression_count, 0)
          into v_user_view_count
          from public.post_views pv
         where pv.post_id = p_post_id
           and pv.viewer_id = v_viewer_id;

        return jsonb_build_object(
            'accepted', false,
            'duplicate', false,
            'cap_reached', coalesce(v_user_view_count, 0) >= 100,
            'view_count', v_view_count,
            'user_view_count', coalesce(v_user_view_count, 0),
            'view_weight', v_view_weight,
            'content_type', v_content_type,
            'event_id', p_event_id
        );
    end if;

    v_user_view_count := v_new_user_view_count;

    update public.feed_posts
       set view_count = coalesce(view_count, 0) + v_view_weight,
           updated_at = now()
     where id = p_post_id
    returning view_count into v_view_count;

    if v_user_view_count = 1 then
        begin
            perform public.award_points(v_viewer_id, 'view_post', p_post_id);
        exception when others then
            null;
        end;
    end if;

    begin
        perform public.award_repost_distribution_points(p_post_id, v_viewer_id, 'view');
    exception when others then
        null;
    end;

    return jsonb_build_object(
        'accepted', true,
        'duplicate', false,
        'cap_reached', v_user_view_count >= 100,
        'view_count', v_view_count,
        'user_view_count', v_user_view_count,
        'view_weight', v_view_weight,
        'content_type', v_content_type,
        'event_id', p_event_id
    );
end;
$$;

revoke all on function public.record_content_view(uuid, uuid) from public, anon;
grant execute on function public.record_content_view(uuid, uuid) to authenticated;
