-- BLINK Rank Points integrity hardening.
--
-- This migration composes the intended behavior from:
--   * BLINK Boost Growth v2: view_listing (+1) and insert-before-update idempotency.
--   * BLINK Drops v1: incentivized giveaway actions must not award Rank Points/XP.
--
-- It also removes direct client execution of award_points. Android/Windows features
-- earn points through validated writes/RPCs whose trusted SECURITY DEFINER
-- trigger/service functions call this internal primitive.
--
-- Existing point values are preserved. The only restored value is view_listing=1,
-- which Boost Growth v2 already defines and its Market mission flow expects.

create or replace function public.award_points(
    p_user_id uuid,
    p_action_type text,
    p_reference_id uuid default null
) returns integer
language plpgsql
security definer
set search_path=''
as $$
declare
    v_delta integer := case p_action_type
        when 'view_post' then 1
        when 'view_listing' then 1
        when 'like_post' then 1
        when 'comment' then 2
        when 'save_post' then 2
        when 'share_post' then 2
        when 'like_comment' then 2
        when 'reply_comment' then 2
        when 'like_status' then 3
        when 'create_status' then 5
        when 'message_user' then 5
        when 'reply_status' then 5
        when 'create_post' then 15
        when 'follow_user' then 3
        else 0
    end;
    v_new_points bigint;
    v_tx_id uuid;
begin
    -- BLINK Drops: paid/incentivized engagement must never create Rank Points or
    -- downstream XP. Preserve the current balance and skip the transaction ledger.
    if current_setting('blink.incentivized_action', true) = 'on' then
        return coalesce(
            (select p.points::integer from public.profiles p where p.id=p_user_id),
            0
        );
    end if;

    if v_delta=0 then
        raise exception 'Unknown action_type: %',p_action_type;
    end if;

    -- Reserve the reference-backed transaction first. This makes duplicate events
    -- idempotent under concurrent devices instead of incrementing points before the
    -- uniqueness check wins.
    if p_reference_id is not null then
        insert into public.point_transactions(
            user_id,action_type,points_delta,reference_id
        )
        values(p_user_id,p_action_type,v_delta,p_reference_id)
        on conflict (user_id,action_type,reference_id)
        where reference_id is not null
        do nothing
        returning id into v_tx_id;

        if v_tx_id is null then
            select coalesce(p.points,0)
              into v_new_points
              from public.profiles p
             where p.id=p_user_id;
            return coalesce(v_new_points,0)::integer;
        end if;
    else
        insert into public.point_transactions(
            user_id,action_type,points_delta,reference_id
        )
        values(p_user_id,p_action_type,v_delta,null)
        returning id into v_tx_id;
    end if;

    update public.profiles
       set points=coalesce(points,0)+v_delta,
           updated_at=now()
     where id=p_user_id
    returning points into v_new_points;

    if v_new_points is null then
        raise exception 'USER_NOT_FOUND';
    end if;

    return v_new_points::integer;
end;
$$;

-- Internal-only primitive. Function-owner SECURITY DEFINER trigger/service callers
-- remain able to execute it; service_role keeps explicit server-side access.
revoke all on function public.award_points(uuid, text, uuid) from public;
revoke execute on function public.award_points(uuid, text, uuid) from anon;
revoke execute on function public.award_points(uuid, text, uuid) from authenticated;
grant execute on function public.award_points(uuid, text, uuid) to service_role;
