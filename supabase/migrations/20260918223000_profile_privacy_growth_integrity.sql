begin;

-- Keep the user's active-status preference alongside the public profile row so
-- every client can suppress presence consistently without reading private settings.
alter table public.profiles
    add column if not exists show_online_status boolean not null default true;

update public.profiles p
set show_online_status = coalesce(s.show_online_status, true)
from public.user_settings s
where s.user_id = p.id;

create or replace function public.sync_profile_presence_privacy()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.profiles
    set show_online_status = coalesce(new.show_online_status, true),
        online_now = case when coalesce(new.show_online_status, true) then online_now else false end,
        is_online = case when coalesce(new.show_online_status, true) then is_online else false end,
        updated_at = now()
    where id = new.user_id;
    return new;
end;
$$;

drop trigger if exists trg_sync_profile_presence_privacy on public.user_settings;
create trigger trg_sync_profile_presence_privacy
after insert or update of show_online_status on public.user_settings
for each row execute function public.sync_profile_presence_privacy();

-- Presence writes now fail closed when a user has hidden active status.
create or replace function public.set_my_presence(p_online boolean)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_share boolean := true;
    v_effective boolean := false;
begin
    if v_uid is null then
        raise exception 'AUTHENTICATION_REQUIRED';
    end if;

    select coalesce(us.show_online_status, true)
    into v_share
    from public.user_settings us
    where us.user_id = v_uid;

    v_share := coalesce(v_share, true);
    v_effective := coalesce(p_online, false) and v_share;

    update public.profiles
    set show_online_status = v_share,
        online_now = v_effective,
        is_online = v_effective,
        last_seen = case when not v_effective and v_share then now() else last_seen end,
        last_seen_at = case when not v_effective and v_share then now() else last_seen_at end,
        updated_at = now()
    where id = v_uid;

    return v_effective;
end;
$$;

grant execute on function public.set_my_presence(boolean) to authenticated;

-- Conversation summaries must not leak last-seen or active status after a user
-- disables presence sharing.
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
security definer
set search_path to ''
as $
  with current_user_id as (
    select auth.uid() as uid
  ), summaries as (
    select
      c.id as conversation_id,
      other.user_id as partner_id,
      p.username as partner_username,
      p.full_name as partner_name,
      p.avatar_url as partner_avatar,
      case
        when coalesce(p.show_online_status, true)
          then coalesce(p.online_now, p.is_online, false)
        else false
      end as partner_online,
      case
        when coalesce(p.show_online_status, true)
          then coalesce(p.last_seen_at, p.last_seen)
        else null
      end as partner_last_seen,
      lm.content as last_message,
      lm.created_at as last_message_at,
      coalesce(uc.unread_count, 0)::bigint as unread_count,
      coalesce(lm.created_at, c.updated_at, c.created_at) as cursor_at
    from current_user_id me
    join public.conversation_participants mine
      on mine.user_id = me.uid
    join public.conversations c
      on c.id = mine.conversation_id
    join public.conversation_participants other
      on other.conversation_id = c.id
     and other.user_id <> me.uid
    left join public.profiles p
      on p.id = other.user_id
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
        and m.sender_id <> me.uid
        and coalesce(m.deleted_for_everyone, false) = false
        and m.created_at > coalesce(mine.last_read_at, 'epoch'::timestamptz)
    ) uc on true
    where me.uid is not null
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
     or (
       s.cursor_at = p_before
       and (p_before_id is null or s.conversation_id < p_before_id)
     )
  order by s.cursor_at desc, s.conversation_id desc
  limit greatest(1, least(coalesce(p_limit, 100), 100));
$;

grant execute on function public.get_conversation_summaries_page(integer, timestamptz, uuid)
to authenticated;

-- Store truthful daily follower totals. We do not invent historical values:
-- existing users receive today's real count and future follow/unfollow changes
-- update that day's snapshot.
create table if not exists public.follower_count_snapshots (
    user_id uuid not null references public.profiles(id) on delete cascade,
    captured_on date not null default current_date,
    follower_count integer not null default 0 check (follower_count >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, captured_on)
);

create index if not exists idx_follower_count_snapshots_user_date
    on public.follower_count_snapshots(user_id, captured_on desc);

alter table public.follower_count_snapshots enable row level security;

do $$
begin
    if not exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'follower_count_snapshots'
          and policyname = 'Users can read own follower snapshots'
    ) then
        create policy "Users can read own follower snapshots"
            on public.follower_count_snapshots
            for select
            to authenticated
            using (user_id = auth.uid());
    end if;
end
$$;

grant select on public.follower_count_snapshots to authenticated;

create or replace function public.capture_follower_count_snapshot(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_count integer;
begin
    if p_user_id is null then
        return;
    end if;

    select count(*)::integer
    into v_count
    from public.follows
    where following_id = p_user_id;

    insert into public.follower_count_snapshots(
        user_id,
        captured_on,
        follower_count,
        created_at,
        updated_at
    )
    values (
        p_user_id,
        current_date,
        coalesce(v_count, 0),
        now(),
        now()
    )
    on conflict (user_id, captured_on)
    do update set
        follower_count = excluded.follower_count,
        updated_at = now();
end;
$$;

create or replace function public.capture_following_snapshot_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    perform public.capture_follower_count_snapshot(
        case when tg_op = 'DELETE' then old.following_id else new.following_id end
    );
    return coalesce(new, old);
end;
$$;

drop trigger if exists trg_capture_following_snapshot_change on public.follows;
create trigger trg_capture_following_snapshot_change
after insert or delete on public.follows
for each row execute function public.capture_following_snapshot_change();

insert into public.follower_count_snapshots(user_id, captured_on, follower_count, created_at, updated_at)
select p.id, current_date, coalesce(p.follower_count, 0), now(), now()
from public.profiles p
on conflict (user_id, captured_on)
do update set
    follower_count = excluded.follower_count,
    updated_at = now();

commit;
