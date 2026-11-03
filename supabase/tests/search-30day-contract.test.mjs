// Read-only contract tests. No database connections or production writes.
// Run: node --test supabase/tests/search-30day-contract.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve, dirname } from "node:path";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const read = path => readFileSync(resolve(root, path), "utf8");
const sql = read("docs/integration/blink-search-30day-trends-staging-20261027.sql");
const android = read("app/src/main/java/com/example/data/repository/SearchDiscoveryRepository.kt");
const windows = read("desktopApp/src/main/kotlin/com/blinkng/desktop/data/DesktopSearchPhase3Repository.kt");
const shared = read("shared/src/main/kotlin/com/blinkng/shared/BlinkTrendingDiscovery.kt");

test("30-day tag and creator endpoints remain isolated from the live legacy endpoint", () => {
  assert.match(sql, /function public\.blink_trending_tags_30d\(/);
  assert.match(sql, /function public\.blink_trending_creators_30d\(/);
  assert.doesNotMatch(sql, /create or replace function public\.blink_trending_tags\(/);
  assert.ok((sql.match(/interval '30 days'/g) || []).length >= 3);
  assert.match(sql, /p_limit integer default 5/);
  assert.match(sql, /p_limit integer default 3/);
});

test("trending uses only public, active, unexpired, unblocked posts", () => {
  assert.ok((sql.match(/fp\.is_active is true/g) || []).length >= 2);
  assert.ok((sql.match(/fp\.is_flagged,false/g) || []).length >= 2);
  assert.ok((sql.match(/'everyone','public'/g) || []).length >= 2);
  assert.ok((sql.match(/fp\.expires_at is null or fp\.expires_at > now\(\)/g) || []).length >= 2);
  assert.ok((sql.match(/from public\.blocks b/g) || []).length >= 2);
  assert.ok((sql.match(/\(select auth\.uid\(\)\) is not null/g) || []).length >= 2);
});

test("client contract prefers the 30-day RPC with honest fallback on both platforms", () => {
  for (const source of [android, windows]) {
    assert.match(source, /BlinkTrendingDiscovery\.THIRTY_DAY_TAGS_RPC/);
    assert.match(source, /BlinkTrendingDiscovery\.CURRENT_TAGS_RPC/);
    assert.match(source, /BlinkTrendingDiscovery\.THIRTY_DAY_CREATORS_RPC/);
    assert.match(source, /BlinkTrendingDiscovery\.windowDays\(thirty != null\)/);
  }
  assert.match(android, /trendingTagsWithPeriod\(university, limit\)\.tags/);
  assert.match(windows, /trendingTagsWithPeriod\(university, limit\)\.tags/);
  assert.match(shared, /if \(fromThirtyDayRpc\) 30 else 7/);
});

test("staging-only SQL limits privileged function exposure and expired creator badges", () => {
  assert.ok((sql.match(/revoke all on function/g) || []).length >= 2);
  assert.ok((sql.match(/to authenticated;/g) || []).length >= 2);
  assert.ok((sql.match(/security definer set search_path = ''/g) || []).length >= 2);
  assert.match(sql, /p\.verification_expires_at is null or p\.verification_expires_at > now\(\)/);
  assert.match(sql, /STAGING-ONLY/);
});
