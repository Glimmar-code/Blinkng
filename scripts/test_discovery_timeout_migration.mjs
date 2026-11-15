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
  assert.doesNotMatch(migration, /update public\.(feed_posts|messages)/i);
});

test("adds full indexes where existing partial indexes do not cover every foreign-key row", () => {
  for (const index of [
    "blink_paystack_renewals_user_id_fk_idx",
    "blink_boosts_content_id_fk_idx",
    "blink_contest_entry_appeals_contest_id_fk_idx",
    "blink_referrals_referrer_user_id_fk_idx",
    "blink_vip_claims_user_id_fk_idx",
    "connect_listings_category_slug_fk_idx",
    "mentor_requests_mentor_profile_id_fk_idx",
    "reading_mate_requests_reading_profile_id_fk_idx",
  ]) assert.match(migration, new RegExp("create index if not exists " + index, "i"));
});

test("adds guarded foreign-key-side indexes for uncovered constraints", () => {
  assert.match(migration, /from pg_constraint c/i);
  assert.match(migration, /c.contype='f'/i);
  assert.match(migration, /i.indnkeyatts >= cardinality\(c.conkey\)/i);
  assert.match(migration, /create index if not exists/i);
});

test("queues message rewards for the existing minute worker", () => {
  assert.ok(migration.includes("create table if not exists private.blink_message_rank_reward_outbox"));
  assert.ok(migration.includes("insert into private.blink_message_rank_reward_outbox(message_id, sender_id)"));
  assert.ok(migration.includes("public.process_message_rank_reward_outbox"));
  assert.ok(migration.includes("grant execute on function public.process_message_rank_reward_outbox(integer)"));
  assert.ok(migration.includes("set_config('lock_timeout', '100ms', true)"));
});

test("repairs VIP expiry reminder to use the live notification schema", () => {
  assert.match(migration, /notifications\(user_id, type, text, sub_text, is_read\)/i);
  assert.doesNotMatch(migration, /notifications\(user_id,\s*type,\s*comment\)/i);
  assert.match(migration, /private\.expire_blink_items_and_remind_vip\(\)/i);
});

test("revokes client RPC execution only from trigger-only seller guards", () => {
  assert.match(migration, /revoke execute on function public\.market_require_approved_seller\(\)/i);
  assert.match(migration, /revoke execute on function public\.market_require_approved_seller_for_order\(\)/i);
});
