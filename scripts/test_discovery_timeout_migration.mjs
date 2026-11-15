import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const migration = readFileSync(
  "supabase/migrations/20261116090000_reduce_discovery_timeout_pressure.sql",
  "utf8",
);

test("adds only targeted composite indexes for observed ranking predicates", () => {
  for (const index of [
    "blocks_blocked_blocker_idx",
    "post_views_viewer_post_last_viewed_idx",
    "post_likes_user_created_post_idx",
    "post_bookmarks_user_created_post_idx",
    "comments_author_created_post_idx",
  ]) {
    assert.match(migration, new RegExp("create index if not exists " + index, "i"));
  }
  assert.match(migration, /set local lock_timeout\s*=\s*'5s'/i);
  assert.match(migration, /set local statement_timeout\s*=\s*'90s'/i);
});

test("guards the feed function rewrite and preserves existing scoring behavior", () => {
  assert.match(migration, /pg_get_functiondef\(v_oid\)/i);
  assert.match(migration, /DISCOVERY_FEED_SELECTION_SOURCE_DRIFT/);
  assert.match(migration, /BLINK_TIMEOUT_RANKED_TEMP_INDEXES/);
  assert.match(migration, /discovery_candidates_score_idx/);
  assert.match(migration, /discovery_candidates_lane_idx/);
  assert.match(migration, /analyze pg_temp\.discovery_candidates/i);
  assert.doesNotMatch(migration, /create or replace function private_ranking\.get_discovery_feed/i);
  assert.doesNotMatch(migration, /update public\.(profiles|feed_posts|messages)/i);
});

test("revokes client RPC execution only from trigger-only seller guards", () => {
  assert.match(migration, /revoke execute on function public\\.market_require_approved_seller\\(\\)/i);
  assert.match(migration, /revoke execute on function public\\.market_require_approved_seller_for_order\\(\\)/i);
});
