-- Search/trending RPC connectivity, separate from Search Phase 3 capability RPCs.
-- Preserve the existing one-UUID toggle and 30-day trend contracts used by Android
-- and Windows. The legacy blink_trending_tags(integer,text) remains untouched as
-- the client's explicitly labelled seven-day fallback.

create or replace function public.blink_trending_tags_30d(
  p_limit integer default 5,
  p_university text default null
)
returns table (
  tag text,
  trend_score numeric,
  post_count bigint,
  search_users bigint,
  campus_posts bigint
)
language sql
stable
security definer
set search_path = ''
as $function$
  with eligible as (
    select fp.id,
           fp.user_id,
           fp.created_at,
           greatest(coalesce(fp.like_count, 0), 0) as likes,
           greatest(coalesce(fp.comment_count, 0), 0) as comments,
           greatest(coalesce(fp.share_count, 0), 0) as shares,
           greatest(coalesce(fp.view_count, 0), 0) as views,
           fp.tags,
           fp.hashtags,
           pr.university
      from public.feed_posts fp
      join public.profiles pr on pr.id = fp.user_id
     where (select auth.uid()) is not null
       and fp.is_active is true
       and coalesce(fp.is_flagged, false) is false
       and lower(coalesce(fp.audience, 'everyone')) in ('everyone', 'public')
       and fp.created_at >= now() - interval '30 days'
       and (fp.expires_at is null or fp.expires_at > now())
       and not exists (
         select 1
           from public.blocks b
          where (b.blocker_id = (select auth.uid()) and b.blocked_id = fp.user_id)
             or (b.blocked_id = (select auth.uid()) and b.blocker_id = fp.user_id)
       )
       and not exists (
         select 1
           from public.muted_users mu
          where mu.user_id = (select auth.uid())
            and mu.muted_id = fp.user_id
       )
  ), normalized as (
    select e.id,
           e.user_id,
           e.created_at,
           e.likes,
           e.comments,
           e.shares,
           e.views,
           e.university,
           tag.tag
      from eligible e
      cross join lateral (
        select distinct lower(trim(leading '#' from btrim(raw.value))) as tag
          from unnest(coalesce(e.tags, '{}'::text[]) || coalesce(e.hashtags, '{}'::text[])) as raw(value)
      ) tag
     where tag.tag ~ '^[a-z0-9_]{2,32}$'
  ), activity as (
    select n.tag,
           count(distinct n.id) as post_count,
           count(distinct n.user_id) as creators,
           count(*) filter (where n.created_at >= now() - interval '24 hours') as new_posts,
           count(*) filter (where n.created_at >= now() - interval '1 hour') as fresh_posts,
           coalesce(sum(
             least(n.likes, 100)
             + least(n.comments * 2, 100)
             + least(n.shares * 3, 100)
             + least(n.views / 10, 50)
           ), 0) as engagement,
           count(*) filter (
             where nullif(btrim(p_university), '') is not null
               and lower(n.university) = lower(btrim(p_university))
           ) as campus_posts
      from normalized n
     group by n.tag
  ), searches as (
    select tse.tag,
           count(distinct tse.user_id) as search_users
      from public.blink_tag_search_events tse
     where tse.searched_hour >= now() - interval '30 days'
       and (select auth.uid()) is not null
       and not exists (
         select 1
           from public.blocks b
          where (b.blocker_id = (select auth.uid()) and b.blocked_id = tse.user_id)
             or (b.blocked_id = (select auth.uid()) and b.blocker_id = tse.user_id)
       )
     group by tse.tag
  )
  select a.tag,
         round(
           30 * least(1.0, a.new_posts::numeric / 10)
           + 25 * least(1.0, coalesce(s.search_users, 0)::numeric / 150)
           + 20 * least(1.0, a.engagement::numeric / 1200)
           + 15 * least(1.0, a.creators::numeric / 25)
           + 5 * least(1.0, a.fresh_posts::numeric / 10)
           + 5 * least(1.0, a.campus_posts::numeric / 10),
           2
         ) as trend_score,
         a.post_count,
         coalesce(s.search_users, 0) as search_users,
         a.campus_posts
    from activity a
    left join searches s on s.tag = a.tag
   where a.post_count > 0
   order by trend_score desc, a.post_count desc, a.tag
   limit least(greatest(coalesce(p_limit, 5), 1), 50);
$function$;
revoke all on function public.blink_trending_tags_30d(integer, text) from public, anon, authenticated;
grant execute on function public.blink_trending_tags_30d(integer, text) to authenticated;

create or replace function public.blink_trending_creators_30d(
  p_limit integer default 3,
  p_university text default null
)
returns table (
  id uuid,
  username text,
  full_name text,
  avatar_url text,
  university text,
  is_verified boolean,
  trend_score numeric,
  post_count bigint
)
language sql
stable
security definer
set search_path = ''
as $function$
  with eligible_posts as (
    select fp.user_id,
           greatest(coalesce(fp.view_count, 0), 0) as views,
           greatest(coalesce(fp.like_count, 0), 0) as likes,
           greatest(coalesce(fp.comment_count, 0), 0) as comments,
           greatest(coalesce(fp.share_count, 0), 0) as shares
      from public.feed_posts fp
     where (select auth.uid()) is not null
       and fp.is_active is true
       and coalesce(fp.is_flagged, false) is false
       and lower(coalesce(fp.audience, 'everyone')) in ('everyone', 'public')
       and fp.created_at >= now() - interval '30 days'
       and (fp.expires_at is null or fp.expires_at > now())
       and fp.user_id <> (select auth.uid())
       and not exists (
         select 1
           from public.blocks b
          where (b.blocker_id = (select auth.uid()) and b.blocked_id = fp.user_id)
             or (b.blocked_id = (select auth.uid()) and b.blocker_id = fp.user_id)
       )
       and not exists (
         select 1
           from public.muted_users mu
          where mu.user_id = (select auth.uid())
            and mu.muted_id = fp.user_id
       )
  )
  select p.id,
         p.username,
         p.full_name,
         p.avatar_url,
         p.university,
         (coalesce(p.is_verified, false)
           and (p.verification_expires_at is null or p.verification_expires_at > now())) as is_verified,
         round(sum(
           least(ep.views, 10000) * 0.01
           + least(ep.likes, 200) * 2
           + least(ep.comments, 100) * 3
           + least(ep.shares, 100) * 4
         )::numeric, 2) as trend_score,
         count(*) as post_count
    from eligible_posts ep
    join public.profiles p on p.id = ep.user_id
   where p.username is not null
     and length(btrim(p.username)) > 0
     and coalesce(p.onboarding_completed, false) is true
     and (nullif(btrim(p_university), '') is null
       or lower(p.university) = lower(btrim(p_university)))
   group by p.id, p.username, p.full_name, p.avatar_url, p.university,
            p.is_verified, p.verification_expires_at
   order by trend_score desc, post_count desc, p.username
   limit least(greatest(coalesce(p_limit, 3), 1), 3);
$function$;
revoke all on function public.blink_trending_creators_30d(integer, text) from public, anon, authenticated;
grant execute on function public.blink_trending_creators_30d(integer, text) to authenticated;

-- These legacy toggle signatures are consumed by SearchDiscoveryRepository. Each
-- call remains a toggle and returns the resulting state. Entity-row locks serialize
-- concurrent RPC toggles; unique membership keys plus ON CONFLICT protect against
-- duplicate writes. Use a desired-state API with a request key if retry-safe replay
-- across a lost HTTP response is needed; the existing one-ID contract cannot express it.
create or replace function public.toggle_community_membership_v2(p_community_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_uid uuid := (select auth.uid());
  v_owner uuid;
  v_private boolean;
  v_role text;
  v_status text;
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if p_community_id is null then return false; end if;

  select c.owner_id, c.is_private
    into v_owner, v_private
    from public.communities c
   where c.id = p_community_id
   for update;
  if not found then return false; end if;

  select cm.role, cm.status
    into v_role, v_status
    from public.community_members cm
   where cm.community_id = p_community_id
     and cm.user_id = v_uid
   for update;

  if found then
    if v_status = 'active' then
      if v_role = 'owner' or v_owner = v_uid then return true; end if;
      delete from public.community_members cm
       where cm.community_id = p_community_id and cm.user_id = v_uid;
      return false;
    end if;
    -- A pre-existing pending row represents an invitation/request; never turn a
    -- blocked membership into an active one through this discovery action.
    if v_status = 'blocked' then return false; end if;
    update public.community_members cm
       set status = 'active', joined_at = now()
     where cm.community_id = p_community_id and cm.user_id = v_uid;
    return true;
  end if;

  if v_owner = v_uid then return true; end if;
  if coalesce(v_private, true) then return false; end if;

  insert into public.community_members(community_id, user_id, role, status)
  values (p_community_id, v_uid, 'member', 'active')
  on conflict (community_id, user_id) do nothing;
  return exists (
    select 1 from public.community_members cm
     where cm.community_id = p_community_id
       and cm.user_id = v_uid
       and cm.status = 'active'
  );
end;
$function$;
revoke all on function public.toggle_community_membership_v2(uuid) from public, anon, authenticated;
grant execute on function public.toggle_community_membership_v2(uuid) to authenticated;

create or replace function public.toggle_event_attendance_v2(p_event_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_uid uuid := (select auth.uid());
  v_creator uuid;
  v_community uuid;
  v_university text;
  v_visibility text;
  v_status text;
  v_starts_at timestamptz;
  v_ends_at timestamptz;
  v_attendance text;
  v_allowed boolean := false;
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if p_event_id is null then return false; end if;

  select e.creator_id, e.community_id, e.university, e.visibility, e.status,
         e.starts_at, e.ends_at
    into v_creator, v_community, v_university, v_visibility, v_status,
         v_starts_at, v_ends_at
    from public.events e
   where e.id = p_event_id
   for update;
  if not found then return false; end if;

  select ea.status
    into v_attendance
    from public.event_attendees ea
   where ea.event_id = p_event_id and ea.user_id = v_uid
   for update;

  if v_attendance = 'going' then
    delete from public.event_attendees ea
     where ea.event_id = p_event_id and ea.user_id = v_uid;
    return false;
  end if;

  -- Do not create or promote attendance for events which have closed. A stale
  -- interested row may be removed by invoking the same action after closure.
  if v_status not in ('scheduled', 'live')
     or coalesce(v_ends_at, v_starts_at) < now() then
    if v_attendance is not null then
      delete from public.event_attendees ea
       where ea.event_id = p_event_id and ea.user_id = v_uid;
    end if;
    return false;
  end if;

  v_allowed := v_creator = v_uid
    or v_visibility = 'public'
    or (
      v_visibility = 'campus'
      and exists (
        select 1 from public.profiles pr
         where pr.id = v_uid
           and nullif(btrim(pr.university), '') is not null
           and lower(btrim(pr.university)) = lower(btrim(coalesce(v_university, '')))
      )
    )
    or (
      v_visibility = 'community'
      and v_community is not null
      and exists (
        select 1 from public.community_members cm
         where cm.community_id = v_community
           and cm.user_id = v_uid
           and cm.status = 'active'
      )
    );
  if not v_allowed then return false; end if;

  insert into public.event_attendees(event_id, user_id, status)
  values (p_event_id, v_uid, 'going')
  on conflict (event_id, user_id) do update set status = 'going';
  return true;
end;
$function$;
revoke all on function public.toggle_event_attendance_v2(uuid) from public, anon, authenticated;
grant execute on function public.toggle_event_attendance_v2(uuid) to authenticated;

create or replace function public.toggle_page_follow_v2(p_page_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_uid uuid := (select auth.uid());
begin
  if v_uid is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if p_page_id is null then return false; end if;

  perform 1 from public.pages p where p.id = p_page_id for update;
  if not found then return false; end if;

  if exists (
    select 1 from public.page_followers pf
     where pf.page_id = p_page_id and pf.user_id = v_uid
  ) then
    delete from public.page_followers pf
     where pf.page_id = p_page_id and pf.user_id = v_uid;
    return false;
  end if;

  insert into public.page_followers(page_id, user_id)
  values (p_page_id, v_uid)
  on conflict (page_id, user_id) do nothing;
  return exists (
    select 1 from public.page_followers pf
     where pf.page_id = p_page_id and pf.user_id = v_uid
  );
end;
$function$;
revoke all on function public.toggle_page_follow_v2(uuid) from public, anon, authenticated;
grant execute on function public.toggle_page_follow_v2(uuid) to authenticated;
