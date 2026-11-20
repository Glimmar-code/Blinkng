import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const migration = await readFile(new URL("../migrations/20261107130000_fix_blink_boost_reels_surface.sql", import.meta.url), "utf8");
const android = await readFile(new URL("../../app/src/main/java/com/example/data/supabase/BlinkEconomyService.kt", import.meta.url), "utf8");
const windows = await readFile(new URL("../../desktopApp/src/main/kotlin/com/blinkng/desktop/data/DesktopRpcActions.kt", import.meta.url), "utf8");

test("REELS is accepted by promoted Boost retrieval and remains constrained", () => {
  assert.match(migration, /v_surface not in \('HOME','SEARCH','DISCOVER','MISSIONS','REELS'\)/);
  assert.match(migration, /security definer[\s\S]*set search_path=''/);
  assert.match(migration, /revoke all on function public\.get_blink_promoted_slots\(text,integer\) from public, anon/);
  assert.match(migration, /grant execute on function public\.get_blink_promoted_slots\(text,integer\) to authenticated/);
  assert.doesNotMatch(migration, /'MARKET'\s*\)\s*then\s*raise exception 'INVALID_BOOST_SURFACE'/);
});

test("Android and Windows route MARKET placements through the Market RPC", () => {
  assert.match(android, /if \(normalized == "MARKET"\) "get_market_promoted_slots" else "get_blink_promoted_slots"/);
  assert.match(windows, /if \(normalizedSurface == "MARKET"\) "get_market_promoted_slots" else "get_blink_promoted_slots"/);
});

test("both clients reject unknown delivery surfaces before RPC", () => {
  const allowed = /setOf\("HOME", "SEARCH", "DISCOVER", "MISSIONS", "MARKET", "REELS"\)/;
  assert.match(android, allowed);
  assert.match(windows, allowed);
  assert.match(android, /Unknown Boost delivery surface/);
  assert.match(windows, /Unknown Boost delivery surface/);
});
