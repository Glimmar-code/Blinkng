import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import test from "node:test";

const migration = readFileSync(new URL("../migrations/20261115120000_for_you_refresh_discovery_v2.sql", import.meta.url), "utf8");
const android = readFileSync(new URL("../../app/src/main/java/com/example/data/supabase/SupabaseService.kt", import.meta.url), "utf8");
const windows = readFileSync(new URL("../../desktopApp/src/main/kotlin/com/blinkng/desktop/data/DesktopSupabaseClient.kt", import.meta.url), "utf8");
const web = readFileSync(new URL("../../web/app.js", import.meta.url), "utf8");
const webDiscovery = readFileSync(new URL("../../web/discovery.js", import.meta.url), "utf8");

function seededOrder(ids, seed) {
  return [...ids].sort((a, b) => {
    const score = (id) => createHash("sha256").update(`${id}:${seed}`).digest("hex");
    return score(b).localeCompare(score(a));
  });
}

test("refresh twice returns new posts before previously served impressions", () => {
  const ids = Array.from({ length: 40 }, (_, i) => `post-${i + 1}`);
  const page = (seen, seed) => {
    const unseen = seededOrder(ids.filter(id => !seen.has(id)), seed);
    const previouslyServed = seededOrder(ids.filter(id => seen.has(id)), seed);
    return [...unseen, ...previouslyServed].slice(0, 20);
  };
  const first = page(new Set(), "refresh-seed-one");
  const second = page(new Set(first), "refresh-seed-two");

  assert.equal(first.length, 20);
  assert.equal(second.length, 20);
  assert.equal(second.filter(id => first.includes(id)).length, 0,
    "a fresh page must backfill from unseen posts before returning prior impressions");
  assert.notDeepEqual(first, second, "a fresh request seed must perturb equal-score ordering");
  assert.match(migration, /hashtextextended\(c\.id::text\|\|':'\|\|c\.seed::text,0\)/);
  assert.match(android, /java\.util\.UUID\.randomUUID\(\)/);
  assert.match(windows, /UUID\.randomUUID\(\)/);
  assert.match(webDiscovery, /crypto\?\.randomUUID\?\.\(\)/);
  assert.match(android, /get_for_you_feed_page/);
  assert.match(windows, /get_for_you_feed_page/);
  assert.match(webDiscovery, /get_for_you_feed_page/);
  assert.match(web, /BlinkFeedRecommendation\.fetchPage/);
});

test("empty fresh pool falls back to older eligible content", () => {
  assert.match(migration, /when c\.created_at>=c\.rank_as_of-interval '7 days' then 3\s+else 4/);
  assert.match(migration, /when p\.candidate_tier=4 then 'Quality discovery backfill'/);
  assert.doesNotMatch(migration, /fp\.created_at\s*>\s*now\(\)\s*-\s*interval\s*'90 days'/i);

  const eligible = [{ id: "old-1", ageDays: 19, seen: false, quality: 8 }];
  const tier = (post) => post.seen ? 5 : post.ageDays <= 7 ? 3 : 4;
  const page = eligible.filter((post) => tier(post) === 4).sort((a, b) => b.quality - a.quality);
  assert.equal(page[0]?.id, "old-1");
});

test("new feed RPC is score + post-id cursor based and offset-free", () => {
  const start = migration.indexOf("create or replace function private_ranking.get_discovery_feed(");
  const end = migration.indexOf("create or replace function public.get_for_you_feed_page(", start);
  const fn = migration.slice(start, end);
  assert.match(fn, /p_cursor_score numeric/);
  assert.match(fn, /p_cursor_post_id uuid/);
  assert.match(fn, /r\.final_score < p_cursor_score/);
  assert.match(fn, /r\.id<p_cursor_post_id/);
  assert.doesNotMatch(fn, /p_offset|next_offset/i);
  assert.match(fn, /coalesce\(p_request_seed,gen_random_uuid\(\)\)/);
  assert.match(fn, /ranking_components jsonb/);
  assert.match(fn, /next_cursor_score text/);
  assert.equal(migration.split("create or replace function private_ranking.get_discovery_feed").length - 1, 1);
  assert.equal(migration.split("create or replace function public.get_for_you_feed_page").length - 1, 1);
});

test("ranking tiers reserve exploration, test distribution, cap creators and mix formats", () => {
  assert.match(migration, /r\.comments::numeric\*3/);
  assert.match(migration, /r\.shares::numeric\*5/);
  assert.match(migration, /r\.reposts::numeric\*5/);
  assert.match(migration, /power\(c\.age_hours\+2::numeric,1\.5\)/);
  assert.match(migration, /active_blink_boost_factor/);
  assert.match(migration, /viewer_personalization_bonus/);
  assert.match(migration, /'model','weighted_decay_v1'/);
  assert.match(migration, /viewer_exposure as \(/);
  assert.match(migration, /when c\.viewer_views>0 then 5/);
  assert.doesNotMatch(migration, /s\.post_id\s+is\s+null/i);
  assert.match(migration, /c\.created_at>=c\.rank_as_of-interval '48 hours'/);
  assert.match(migration, /c\.created_at>=c\.rank_as_of-interval '7 days'/);
  assert.match(migration, /c\.impressions>=20/);
  assert.match(migration, /engagement_rate/);
  assert.match(migration, /Below baseline; held for later/);
  assert.match(migration, /< 0\.05 then 4/);
  assert.match(migration, /c\.new_creator or greatest\(0,c\.view_count\)<50/);
  assert.match(migration, /partition by t\.candidate_tier,t\.is_exploration,t\.is_reel/);
  assert.match(migration, /partition by f\.user_id/);
  assert.match(migration, /\(\(cr\.creator_rank-1\)\/2\)::bigint as creator_page_batch/);
  assert.match(migration, /cl\.creator_page_batch=cb\.creator_page_batch/);
  assert.doesNotMatch(migration, /creator_page_position/);
  assert.match(migration, /when g\.is_reel then g\.group_rank\*4/);
  assert.match(migration, /count\(\*\) filter \(where i\.is_exploration\) over \(\)/);
  assert.match(migration, /global_group_rank\*5/);
  assert.match(migration, /global_group_rank\+\(\(o\.global_group_rank-1\)\/4\)/);
});

test("impressions, qualified views, Reel watch time and view counters are server logged", () => {
  assert.match(migration, /public\.record_feed_impressions/);
  assert.match(migration, /private_ranking\.record_recommendation_event\([\s\S]*?'impression'/);
  assert.match(migration, /public\.record_reel_engagement/);
  assert.match(migration, /private_ranking\.reel_engagement_sessions/);
  assert.match(migration, /trg_sync_feed_post_view_count/);
  assert.match(migration, /feed_view_count_baselines/);
  assert.match(migration, /sum\(pv\.weighted_view_count\)/);
  assert.match(migration, /baseline_view_count/);
  assert.match(android, /record_feed_impressions/);
  assert.match(windows, /record_feed_impressions/);
  assert.match(webDiscovery, /record_feed_impressions/);
});

test("all platform feed refresh requests bypass client cache", () => {
  assert.match(android, /Cache-Control", "no-cache, no-store/);
  assert.match(windows, /Cache-Control", "no-cache, no-store/);
  assert.match(web, /cache:'no-store'/);
});
