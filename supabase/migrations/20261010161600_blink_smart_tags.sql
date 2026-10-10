-- BLINK Smart Tags: privacy-preserving searches and bounded trend discovery.
-- Preview/staging migration. Never apply directly to production without validation.
create table if not exists public.blink_tag_search_events (
  user_id uuid not null references auth.users(id) on delete cascade,
  tag text not null check (tag ~ '^[a-z0-9_]{2,32}$'),
  searched_hour timestamptz not null,
  primary key (user_id, tag, searched_hour)
);
create index if not exists blink_tag_search_recent_idx
  on public.blink_tag_search_events (searched_hour desc, tag);
alter table public.blink_tag_search_events enable row level security;
revoke all on public.blink_tag_search_events from public, anon, authenticated;
-- Only explicit, authenticated RPCs can read aggregate activity or write deduplicated events.

create or replace function public.blink_record_tag_search(p_tag text)
returns void language plpgsql security definer
set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_tag text := lower(trim(leading '#' from btrim(coalesce(p_tag, ''))));
begin
  if v_user is null then raise exception 'Authentication required' using errcode = '42501'; end if;
  if v_tag !~ '^[a-z0-9_]{2,32}$' then return; end if;
  insert into public.blink_tag_search_events(user_id, tag, searched_hour)
  values (v_user, v_tag, date_trunc('hour', now()))
  on conflict do nothing;
end;
$$;
revoke all on function public.blink_record_tag_search(text) from public, anon;
grant execute on function public.blink_record_tag_search(text) to authenticated;

create or replace function public.blink_trending_tags(
  p_limit integer default 7,
  p_university text default null
)
returns table (
  tag text,
  trend_score numeric,
  post_count bigint,
  search_users bigint,
  campus_posts bigint
)
language sql stable security definer
set search_path = ''
as $$
  with recent_posts as (
    select fp.id, fp.user_id, fp.tags, fp.hashtags, fp.created_at,
           greatest(coalesce(fp.like_count, 0), 0) likes,
           greatest(coalesce(fp.comment_count, 0), 0) comments,
           greatest(coalesce(fp.share_count, 0), 0) shares,
           greatest(coalesce(fp.view_count, 0), 0) views,
           pr.university
      from public.feed_posts fp
      left join public.profiles pr on pr.id = fp.user_id
     where fp.is_active is true
       and coalesce(fp.is_flagged, false) is false
       and fp.created_at >= now() - interval '7 days'
       and (select auth.uid()) is not null
  ),
  normalized_posts as (
    select rp.*, tag.tag
      from recent_posts rp
      cross join lateral (
        select distinct lower(trim(leading '#' from btrim(raw.value))) as tag
          from unnest(coalesce(rp.tags, '{}'::text[]) || coalesce(rp.hashtags, '{}'::text[])) raw(value)
      ) tag
     where tag.tag ~ '^[a-z0-9_]{2,32}$'
  ),
  activity as (
    select np.tag,
           count(distinct np.id) as post_count,
           count(distinct np.user_id) as creators,
           count(*) filter(where np.created_at >= now() - interval '24 hours') as new_posts,
           count(*) filter(where np.created_at < now() - interval '24 hours' and np.created_at >= now() - interval '48 hours') as older_posts,
           count(*) filter(where np.created_at >= now() - interval '1 hour') as fresh_posts,
           coalesce(sum(
             least(np.likes, 100) + least(np.comments * 2, 100) +
             least(np.shares * 3, 100) + least(np.views / 10, 50)
           ) filter(where np.created_at >= now() - interval '24 hours'), 0) as engagement,
           count(*) filter(
             where p_university is not null and length(btrim(p_university)) > 0
               and lower(np.university) = lower(btrim(p_university))
           ) as campus_posts
      from normalized_posts np group by np.tag
  ),
  searches as (
    select tse.tag, count(distinct tse.user_id) as search_users
      from public.blink_tag_search_events tse
     where tse.searched_hour >= now() - interval '24 hours'
     group by tse.tag
  )
  select a.tag,
         round((
            30 * least(1.0, ((a.new_posts + 1)::numeric / (a.older_posts + 1)::numeric) / 8.0) +
            25 * least(1.0, coalesce(s.search_users, 0)::numeric / 150.0) +
            25 * least(1.0, a.engagement::numeric / 500.0) +
            15 * least(1.0, a.creators::numeric / 25.0) +
             5 * least(1.0, a.fresh_posts::numeric / 10.0) +
             5 * least(1.0, a.campus_posts::numeric / 10.0)
         ), 2) as trend_score,
         a.post_count, coalesce(s.search_users, 0) as search_users, a.campus_posts
    from activity a left join searches s on s.tag = a.tag
   where a.post_count > 0
   order by trend_score desc, a.new_posts desc, a.creators desc, a.tag
   limit least(greatest(coalesce(p_limit, 7), 1), 50);
$$;
revoke all on function public.blink_trending_tags(integer, text) from public, anon;
grant execute on function public.blink_trending_tags(integer, text) to authenticated;
comment on function public.blink_trending_tags(integer, text)
  is 'Aggregate-only trending score; never returns per-user search events. Requires an authenticated account.';
