-- Keep one fast like-count trigger and remove duplicate full-count work.
-- post_likes is the source of truth; normalize existing counters before
-- relying on the remaining delta-based trg_post_like_count trigger.

with exact_like_counts as (
  select fp.id,
         count(pl.id)::int as like_count
  from public.feed_posts fp
  left join public.post_likes pl on pl.post_id = fp.id
  group by fp.id
)
update public.feed_posts fp
set like_count = counts.like_count
from exact_like_counts counts
where counts.id = fp.id
  and fp.like_count is distinct from counts.like_count;

-- These two triggers both perform a full COUNT(*) and duplicate the work of
-- trg_post_like_count. Keeping only the delta trigger reduces lock duration
-- and database work for every like/unlike without changing ranking,
-- activity, points, or repost-credit triggers.
drop trigger if exists trg_recalc_feed_like_count on public.post_likes;
drop trigger if exists trg_sync_post_like_count on public.post_likes;
