begin;

-- BLINK XP is progression/reputation only. It never participates in leaderboard ordering.
alter table public.profiles
    add column if not exists total_xp bigint not null default 0,
    add column if not exists xp_level integer not null default 1;

alter table public.profiles
    drop constraint if exists profiles_total_xp_nonnegative,
    add constraint profiles_total_xp_nonnegative check (total_xp >= 0),
    drop constraint if exists profiles_xp_level_range,
    add constraint profiles_xp_level_range check (xp_level between 1 and 100);

create table if not exists public.xp_level_thresholds (
    level integer primary key check (level between 1 and 100),
    min_xp bigint not null unique check (min_xp >= 0),
    tier_label text not null,
    created_at timestamptz not null default now()
);

insert into public.xp_level_thresholds(level, min_xp, tier_label)
select
    l,
    case
        when l <= 10 then ((l - 1)::bigint * (l - 1)::bigint * 31)
        when l <= 25 then 2500 + ((l - 10)::bigint * (l - 10)::bigint * 56)
        when l <= 50 then 15000 + ((l - 25)::bigint * (l - 25)::bigint * 72)
        when l <= 75 then 60000 + ((l - 50)::bigint * (l - 50)::bigint * 184)
        else 175000 + ((l - 75)::bigint * (l - 75)::bigint * 520)
    end,
    case
        when l <= 10 then 'New'
        when l <= 25 then 'Active'
        when l <= 50 then 'Established'
        when l <= 75 then 'Highly Active'
        else 'Long-term'
    end
from generate_series(1, 100) as gs(l)
on conflict (level) do update
set min_xp = excluded.min_xp,
    tier_label = excluded.tier_label;

alter table public.xp_level_thresholds enable row level security;
drop policy if exists xp_level_thresholds_read on public.xp_level_thresholds;
create policy xp_level_thresholds_read
on public.xp_level_thresholds for select
to authenticated
using (true);
grant select on public.xp_level_thresholds to authenticated;
revoke insert, update, delete, truncate on public.xp_level_thresholds from anon, authenticated;

create table if not exists public.xp_rules (
    event_type text primary key,
    xp_amount integer not null check (xp_amount >= 0),
    repeatable boolean not null default true,
    daily_event_limit integer,
    enabled boolean not null default true,
    description text not null default '',
    updated_at timestamptz not null default now()
);

insert into public.xp_rules(event_type, xp_amount, repeatable, daily_event_limit, description) values
('qualified_view', 1, true, 100, 'First accepted qualified view per viewer/content'),
('like_post', 1, true, 100, 'Like a post or reel'),
('comment', 3, true, 40, 'Write a genuine top-level comment'),
('reply_comment', 2, true, 50, 'Reply to a comment'),
('like_comment', 1, true, 100, 'Like a comment'),
('save_post', 1, true, 50, 'Save a post or reel'),
('share_post', 2, true, 40, 'Share a post or reel'),
('follow_user', 2, true, 30, 'Follow a user'),
('create_post', 15, true, 8, 'Create a post or reel'),
('daily_mission', 20, false, null, 'Complete a daily mission'),
('weekly_mission', 100, false, null, 'Complete a weekly mission'),
('achievement', 100, false, null, 'Unlock an achievement'),
('connect_activity', 10, true, 10, 'Complete a meaningful Connect activity'),
('connect_match', 20, true, 5, 'Complete a Connect match or challenge'),
('streak_milestone', 50, false, null, 'Reach an activity streak milestone'),
('creator_milestone', 75, false, null, 'Reach a genuine creator milestone')
on conflict (event_type) do update
set xp_amount = excluded.xp_amount,
    repeatable = excluded.repeatable,
    daily_event_limit = excluded.daily_event_limit,
    description = excluded.description,
    updated_at = now();

alter table public.xp_rules enable row level security;
drop policy if exists xp_rules_read on public.xp_rules;
create policy xp_rules_read on public.xp_rules for select to authenticated using (true);
grant select on public.xp_rules to authenticated;
revoke insert, update, delete, truncate on public.xp_rules from anon, authenticated;

create table if not exists public.xp_transactions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete cascade,
    event_type text not null references public.xp_rules(event_type),
    xp_delta integer not null check (xp_delta > 0),
    source_type text not null default '',
    source_id text,
    event_key text not null,
    created_at timestamptz not null default now(),
    unique(user_id, event_key)
);
create index if not exists xp_transactions_user_created_idx
    on public.xp_transactions(user_id, created_at desc);
create index if not exists xp_transactions_daily_idx
    on public.xp_transactions(user_id, event_type, created_at desc);

alter table public.xp_transactions enable row level security;
drop policy if exists xp_transactions_read_own on public.xp_transactions;
create policy xp_transactions_read_own
on public.xp_transactions for select
to authenticated
using ((select auth.uid()) = user_id);
grant select on public.xp_transactions to authenticated;
revoke insert, update, delete, truncate on public.xp_transactions from anon, authenticated;

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create or replace function private.level_for_xp(p_total_xp bigint)
returns integer
language sql
stable
security invoker
set search_path = ''
as $$
    select coalesce(max(t.level), 1)
    from public.xp_level_thresholds t
    where t.min_xp <= greatest(coalesce(p_total_xp, 0), 0)
$$;

revoke all on function private.level_for_xp(bigint) from public, anon, authenticated;

create or replace function private.award_xp(
    p_user_id uuid,
    p_event_type text,
    p_event_key text,
    p_source_type text default '',
    p_source_id text default null,
    p_override_xp integer default null
)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_rule public.xp_rules%rowtype;
    v_amount integer;
    v_today_count integer := 0;
    v_new_total bigint;
    v_new_level integer;
begin
    if p_user_id is null or coalesce(trim(p_event_key), '') = '' then
        return 0;
    end if;

    select * into v_rule
    from public.xp_rules
    where event_type = p_event_type
      and enabled = true;

    if not found then return 0; end if;

    if exists (
        select 1 from public.xp_transactions
        where user_id = p_user_id and event_key = p_event_key
    ) then
        return 0;
    end if;

    if v_rule.repeatable and v_rule.daily_event_limit is not null then
        select count(*) into v_today_count
        from public.xp_transactions
        where user_id = p_user_id
          and event_type = p_event_type
          and created_at >= date_trunc('day', now() at time zone 'UTC') at time zone 'UTC';

        if v_today_count >= v_rule.daily_event_limit then
            return 0;
        end if;
    end if;

    v_amount := case
        when p_override_xp is not null and not v_rule.repeatable then greatest(p_override_xp, 0)
        else v_rule.xp_amount
    end;

    if v_amount <= 0 then return 0; end if;

    insert into public.xp_transactions(
        user_id, event_type, xp_delta, source_type, source_id, event_key
    ) values (
        p_user_id, p_event_type, v_amount, coalesce(p_source_type, ''),
        p_source_id, p_event_key
    )
    on conflict (user_id, event_key) do nothing;

    if not found then return 0; end if;

    update public.profiles
    set total_xp = total_xp + v_amount,
        updated_at = now()
    where id = p_user_id
    returning total_xp into v_new_total;

    if v_new_total is null then return 0; end if;

    v_new_level := private.level_for_xp(v_new_total);

    update public.profiles
    set xp_level = v_new_level
    where id = p_user_id
      and xp_level is distinct from v_new_level;

    return v_amount;
end;
$$;

revoke all on function private.award_xp(uuid,text,text,text,text,integer)
from public, anon, authenticated;

create or replace function private.award_xp_from_activity()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_row jsonb := to_jsonb(new);
    v_user uuid;
    v_source text;
    v_event text;
    v_key text;
    v_parent text;
begin
    v_user := coalesce(
        nullif(v_row->>'user_id','')::uuid,
        nullif(v_row->>'follower_id','')::uuid,
        nullif(v_row->>'sender_id','')::uuid,
        nullif(v_row->>'viewer_id','')::uuid
    );

    if v_user is null then return new; end if;

    v_source := coalesce(v_row->>'id', v_row->>'post_id', v_row->>'comment_id', v_row->>'following_id', '');
    v_parent := coalesce(v_row->>'parent_comment_id', v_row->>'parent_id', '');

    if tg_table_name = 'feed_posts' then
        v_event := 'create_post';
    elsif tg_table_name = 'post_likes' then
        v_event := 'like_post';
    elsif tg_table_name = 'comments' then
        v_event := case when v_parent <> '' then 'reply_comment' else 'comment' end;
    elsif tg_table_name = 'comment_likes' then
        v_event := 'like_comment';
    elsif tg_table_name = 'post_bookmarks' then
        v_event := 'save_post';
    elsif tg_table_name = 'post_shares' then
        v_event := 'share_post';
    elsif tg_table_name = 'follows' then
        v_event := 'follow_user';
    elsif tg_table_name = 'post_views' then
        v_event := 'qualified_view';
    else
        return new;
    end if;

    v_key := 'activity:' || tg_table_name || ':' || coalesce(v_source, '') || ':' || p_user_id::text;
    -- Replace placeholder with the resolved actor id; event-key uniqueness blocks replay.
    v_key := replace(v_key, p_user_id::text, v_user::text);

    perform private.award_xp(v_user, v_event, v_key, tg_table_name, nullif(v_source, ''), null);
    return new;
end;
$$;

revoke all on function private.award_xp_from_activity() from public, anon, authenticated;

drop trigger if exists trg_xp_create_post on public.feed_posts;
create trigger trg_xp_create_post after insert on public.feed_posts
for each row execute function private.award_xp_from_activity();

drop trigger if exists trg_xp_post_like on public.post_likes;
create trigger trg_xp_post_like after insert on public.post_likes
for each row execute function private.award_xp_from_activity();

drop trigger if exists trg_xp_comment on public.comments;
create trigger trg_xp_comment after insert on public.comments
for each row execute function private.award_xp_from_activity();

drop trigger if exists trg_xp_comment_like on public.comment_likes;
create trigger trg_xp_comment_like after insert on public.comment_likes
for each row execute function private.award_xp_from_activity();

drop trigger if exists trg_xp_bookmark on public.post_bookmarks;
create trigger trg_xp_bookmark after insert on public.post_bookmarks
for each row execute function private.award_xp_from_activity();

drop trigger if exists trg_xp_share on public.post_shares;
create trigger trg_xp_share after insert on public.post_shares
for each row execute function private.award_xp_from_activity();

drop trigger if exists trg_xp_follow on public.follows;
create trigger trg_xp_follow after insert on public.follows
for each row execute function private.award_xp_from_activity();

drop trigger if exists trg_xp_first_qualified_view on public.post_views;
create trigger trg_xp_first_qualified_view after insert on public.post_views
for each row execute function private.award_xp_from_activity();

create or replace function public.get_my_xp_progress()
returns table(
    total_xp bigint,
    xp_level integer,
    tier_label text,
    current_level_min_xp bigint,
    next_level_min_xp bigint
)
language sql
stable
security invoker
set search_path = ''
as $$
    select
        p.total_xp,
        p.xp_level,
        cur.tier_label,
        cur.min_xp,
        coalesce(nxt.min_xp, cur.min_xp)
    from public.profiles p
    join public.xp_level_thresholds cur on cur.level = p.xp_level
    left join public.xp_level_thresholds nxt on nxt.level = p.xp_level + 1
    where p.id = (select auth.uid())
$$;

revoke all on function public.get_my_xp_progress() from public, anon;
grant execute on function public.get_my_xp_progress() to authenticated;

-- Keep leaderboard semantics explicit: points remain the only ranking score.
comment on column public.profiles.total_xp is
'BLINK progression XP. Never use for leaderboard ordering.';
comment on column public.profiles.xp_level is
'BLINK progression level derived from total_xp. Never use for leaderboard ordering.';

commit;
