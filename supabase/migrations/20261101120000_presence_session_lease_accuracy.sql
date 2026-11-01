-- Session-scoped presence leases: one device cannot offline another device.
-- Supabase Auth JWTs carry a stable session_id claim; it identifies auth.sessions.id.
begin;

create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated, service_role;

create table if not exists private.user_presence_sessions (
    user_id uuid not null references public.profiles(id) on delete cascade,
    session_id uuid not null,
    last_heartbeat_at timestamptz not null default statement_timestamp(),
    is_online boolean not null default true,
    created_at timestamptz not null default statement_timestamp(),
    primary key (user_id, session_id)
);

create index if not exists user_presence_sessions_active_lease_idx
    on private.user_presence_sessions (last_heartbeat_at)
    where is_online = true;

alter table private.user_presence_sessions enable row level security;
revoke all on private.user_presence_sessions from public, anon, authenticated, service_role;

create or replace function private.set_my_presence(p_online boolean)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, private
as $function$
declare
    v_user_id uuid := auth.uid();
    v_session_id uuid;
    v_now timestamptz := statement_timestamp();
    v_any_online boolean := false;
begin
    if v_user_id is null then
        raise exception 'AUTHENTICATION_REQUIRED';
    end if;

    v_session_id := nullif(auth.jwt() ->> 'session_id', '')::uuid;
    if v_session_id is null then
        raise exception 'AUTH_SESSION_ID_REQUIRED';
    end if;

    -- A valid-looking JWT can outlive a revoked Auth session. Never let a
    -- delayed heartbeat from a signed-out device recreate an online lease.
    if coalesce(p_online, false) and not exists (
        select 1
        from auth.sessions s
        where s.id = v_session_id
          and s.user_id = v_user_id
    ) then
        raise exception 'AUTH_SESSION_REVOKED';
    end if;

    if coalesce(p_online, false) then
        insert into private.user_presence_sessions (
            user_id, session_id, last_heartbeat_at, is_online
        )
        values (v_user_id, v_session_id, v_now, true)
        on conflict (user_id, session_id) do update
        set last_heartbeat_at = greatest(
                private.user_presence_sessions.last_heartbeat_at,
                excluded.last_heartbeat_at
            ),
            is_online = true;
    else
        update private.user_presence_sessions
        set is_online = false,
            last_heartbeat_at = greatest(last_heartbeat_at, v_now)
        where user_id = v_user_id
          and session_id = v_session_id;
    end if;

    -- Reconcile any other device whose heartbeat lease has expired before
    -- calculating the account-level online projection.
    update private.user_presence_sessions
    set is_online = false
    where user_id = v_user_id
      and is_online = true
      and last_heartbeat_at < v_now - interval '90 seconds';

    select exists (
        select 1
        from private.user_presence_sessions s
        where s.user_id = v_user_id
          and s.is_online = true
          and s.last_heartbeat_at >= v_now - interval '90 seconds'
    ) into v_any_online;

    update public.profiles p
    set online_now = v_any_online,
        is_online = v_any_online,
        last_seen_at = case
            when v_any_online then p.last_seen_at
            when coalesce(p_online, false) then coalesce(
                (select max(s.last_heartbeat_at)
                 from private.user_presence_sessions s
                 where s.user_id = v_user_id),
                p.last_seen_at
            )
            else v_now
        end,
        last_seen = case
            when v_any_online then p.last_seen
            when coalesce(p_online, false) then coalesce(
                (select max(s.last_heartbeat_at)
                 from private.user_presence_sessions s
                 where s.user_id = v_user_id),
                p.last_seen
            )
            else v_now
        end,
        updated_at = v_now
    where p.id = v_user_id;
end;
$function$;

revoke all on function private.set_my_presence(boolean) from public, anon;
grant execute on function private.set_my_presence(boolean) to authenticated, service_role;

-- Keep the existing public RPC signature so already-released Android builds
-- continue to call the same endpoint. The session identity comes from the JWT.
create or replace function public.set_my_presence(p_online boolean)
returns void
language sql
security invoker
set search_path = public, pg_temp
as $function$
    select private.set_my_presence(p_online);
$function$;

revoke all on function public.set_my_presence(boolean) from public, anon;
grant execute on function public.set_my_presence(boolean) to authenticated;

create or replace function private.get_chat_presence_for_viewer(p_user_id uuid)
returns table (
    partner_online boolean,
    partner_last_seen timestamptz
)
language plpgsql
stable
security definer
set search_path = pg_catalog, public, private
as $function$
declare
    v_visibility text;
    v_last_seen timestamptz;
    v_global_visible boolean := true;
    v_show_online boolean := true;
    v_show_last_seen boolean := true;
    v_is_follower boolean := false;
    v_is_online boolean := false;
begin
    if auth.uid() is null or p_user_id is null then
        return query select false, null::timestamptz;
        return;
    end if;

    select coalesce(p.presence_visibility, 'PUBLIC'),
           coalesce(p.last_seen_at, p.last_seen)
    into v_visibility, v_last_seen
    from public.profiles p
    where p.id = p_user_id;

    if not found then
        return query select false, null::timestamptz;
        return;
    end if;

    if p_user_id = auth.uid() then
        select exists (
            select 1 from private.user_presence_sessions s
            where s.user_id = p_user_id
              and s.is_online = true
              and s.last_heartbeat_at >= statement_timestamp() - interval '90 seconds'
        ) into v_is_online;
        return query select v_is_online, v_last_seen;
        return;
    end if;

    -- Presence is only projected to an active conversation partner.
    if not exists (
        select 1
        from public.conversation_participants mine
        join public.conversation_participants other
          on other.conversation_id = mine.conversation_id
        where mine.user_id = auth.uid()
          and mine.left_at is null
          and other.user_id = p_user_id
          and other.left_at is null
    ) then
        return query select false, null::timestamptz;
        return;
    end if;

    select coalesce(us.show_online_status, true)
    into v_global_visible
    from public.user_settings us
    where us.user_id = p_user_id;
    if not found then
        v_global_visible := true;
    end if;

    select coalesce(cp.show_online, true),
           coalesce(cp.show_last_seen, true)
    into v_show_online, v_show_last_seen
    from public.user_chat_privacy cp
    where cp.user_id = p_user_id;
    if not found then
        v_show_online := true;
        v_show_last_seen := true;
    end if;

    select exists (
        select 1
        from public.follows f
        where f.follower_id = auth.uid()
          and f.following_id = p_user_id
    ) into v_is_follower;

    v_global_visible := v_global_visible and (
        upper(coalesce(v_visibility, 'PUBLIC')) = 'PUBLIC'
        or (
            upper(coalesce(v_visibility, 'PUBLIC')) = 'FOLLOWERS'
            and v_is_follower
        )
    );

    select exists (
        select 1
        from private.user_presence_sessions s
        where s.user_id = p_user_id
          and s.is_online = true
          and s.last_heartbeat_at >= statement_timestamp() - interval '90 seconds'
    ) into v_is_online;

    select greatest(v_last_seen, max(s.last_heartbeat_at))
    into v_last_seen
    from private.user_presence_sessions s
    where s.user_id = p_user_id;

    return query
    select
        (v_global_visible and v_show_online and v_is_online),
        case
            when v_global_visible and v_show_last_seen then v_last_seen
            else null::timestamptz
        end;
end;
$function$;

revoke all on function private.get_chat_presence_for_viewer(uuid) from public, anon;
grant execute on function private.get_chat_presence_for_viewer(uuid) to authenticated, service_role;

create or replace function public.get_conversation_summaries_page(
    p_limit integer default 100,
    p_before timestamptz default null,
    p_before_id uuid default null
)
returns table(
    conversation_id uuid,
    partner_id uuid,
    partner_username text,
    partner_name text,
    partner_avatar text,
    partner_online boolean,
    partner_last_seen timestamptz,
    last_message text,
    last_message_at timestamptz,
    unread_count bigint,
    cursor_at timestamptz
)
language sql
stable
security invoker
set search_path = public, private, pg_temp
as $function$
    with summaries as (
        select
            c.id as conversation_id,
            other.user_id as partner_id,
            p.username as partner_username,
            p.full_name as partner_name,
            p.avatar_url as partner_avatar,
            presence.partner_online,
            presence.partner_last_seen,
            lm.content as last_message,
            lm.created_at as last_message_at,
            coalesce(uc.unread_count, 0)::bigint as unread_count,
            coalesce(lm.created_at, c.updated_at, c.created_at) as cursor_at
        from public.conversation_participants mine
        join public.conversations c on c.id = mine.conversation_id
        join public.conversation_participants other
          on other.conversation_id = c.id
         and other.user_id <> (select auth.uid())
        left join public.profiles p on p.id = other.user_id
        left join lateral private.get_chat_presence_for_viewer(other.user_id) presence on true
        left join lateral (
            select m.content, m.created_at
            from public.messages m
            where m.conversation_id = c.id
              and coalesce(m.deleted_for_everyone, false) = false
            order by m.created_at desc, m.id desc
            limit 1
        ) lm on true
        left join lateral (
            select count(*) as unread_count
            from public.messages m
            where m.conversation_id = c.id
              and m.sender_id <> (select auth.uid())
              and coalesce(m.deleted_for_everyone, false) = false
              and m.created_at > coalesce(mine.last_read_at, 'epoch'::timestamptz)
        ) uc on true
        where mine.user_id = (select auth.uid())
    )
    select
        s.conversation_id,
        s.partner_id,
        s.partner_username,
        s.partner_name,
        s.partner_avatar,
        s.partner_online,
        s.partner_last_seen,
        s.last_message,
        s.last_message_at,
        s.unread_count,
        s.cursor_at
    from summaries s
    where p_before is null
       or s.cursor_at < p_before
       or (s.cursor_at = p_before and (p_before_id is null or s.conversation_id < p_before_id))
    order by s.cursor_at desc, s.conversation_id desc
    limit greatest(1, least(coalesce(p_limit, 100), 100));
$function$;

revoke all on function public.get_conversation_summaries_page(integer, timestamptz, uuid) from public, anon;
grant execute on function public.get_conversation_summaries_page(integer, timestamptz, uuid) to authenticated, service_role;

-- Keep the legacy summary RPC aligned with the same privacy and presence source.
create or replace function public.get_conversation_summaries(p_limit integer default 50)
returns table(
    conversation_id uuid,
    partner_id uuid,
    partner_username text,
    partner_name text,
    partner_avatar text,
    partner_online boolean,
    partner_last_seen timestamptz,
    last_message text,
    last_message_at timestamptz,
    unread_count bigint
)
language sql
stable
security invoker
set search_path = public, private, pg_temp
as $function$
    select
        s.conversation_id,
        s.partner_id,
        s.partner_username,
        s.partner_name,
        s.partner_avatar,
        s.partner_online,
        s.partner_last_seen,
        s.last_message,
        s.last_message_at,
        s.unread_count
    from public.get_conversation_summaries_page(p_limit, null, null) s;
$function$;

revoke all on function public.get_conversation_summaries(integer) from public, anon;
grant execute on function public.get_conversation_summaries(integer) to authenticated, service_role;

create or replace function private.reconcile_presence_lease()
returns integer
language plpgsql
security definer
set search_path = pg_catalog, public, private
as $function$
declare
    v_now timestamptz := statement_timestamp();
    v_expired_count integer := 0;
    v_affected_users uuid[];
    v_user_id uuid;
    v_any_online boolean;
    v_last_active timestamptz;
begin
    with expired as (
        update private.user_presence_sessions
        set is_online = false
        where is_online = true
          and last_heartbeat_at < v_now - interval '90 seconds'
        returning user_id
    )
    select count(*)::integer, array_agg(distinct user_id)
    into v_expired_count, v_affected_users
    from expired;

    foreach v_user_id in array coalesce(v_affected_users, array[]::uuid[])
    loop
        select exists (
            select 1
            from private.user_presence_sessions s
            where s.user_id = v_user_id
              and s.is_online = true
              and s.last_heartbeat_at >= v_now - interval '90 seconds'
        ) into v_any_online;

        select max(s.last_heartbeat_at)
        into v_last_active
        from private.user_presence_sessions s
        where s.user_id = v_user_id;

        update public.profiles p
        set online_now = v_any_online,
            is_online = v_any_online,
            last_seen_at = case
                when v_any_online then p.last_seen_at
                else greatest(v_last_active, p.last_seen_at, p.last_seen)
            end,
            last_seen = case
                when v_any_online then p.last_seen
                else greatest(v_last_active, p.last_seen_at, p.last_seen)
            end,
            updated_at = v_now
        where p.id = v_user_id;
    end loop;

    delete from private.user_presence_sessions
    where is_online = false
      and last_heartbeat_at < v_now - interval '30 days';

    return v_expired_count;
end;
$function$;

revoke all on function private.reconcile_presence_lease() from public, anon, authenticated, service_role;

-- Keep the existing scheduler entry point, but move its implementation into
-- the non-exposed private schema.
create or replace function public.reconcile_presence_lease()
returns integer
language sql
security definer
set search_path = pg_catalog, public, private
as $function$
    select private.reconcile_presence_lease();
$function$;

revoke all on function public.reconcile_presence_lease() from public, anon, authenticated, service_role;

-- Existing clients immediately start creating session leases on their next
-- foreground heartbeat. Clear legacy booleans that have no live lease.
update public.profiles p
set online_now = false,
    is_online = false,
    updated_at = statement_timestamp()
where (coalesce(p.online_now, false) = true or coalesce(p.is_online, false) = true)
  and not exists (
      select 1
      from private.user_presence_sessions s
      where s.user_id = p.id
        and s.is_online = true
        and s.last_heartbeat_at >= statement_timestamp() - interval '90 seconds'
  );

do $function$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        perform cron.unschedule(jobid)
        from cron.job
        where jobname = 'blink-presence-lease-reconcile';

        perform cron.schedule(
            'blink-presence-lease-reconcile',
            '* * * * *',
            'select public.reconcile_presence_lease();'
        );
    end if;
end;
$function$;

select public.reconcile_presence_lease();

commit;
