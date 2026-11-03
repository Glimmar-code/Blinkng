import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const read = (relative) => readFileSync(resolve(here, relative), "utf8");

const migration = read("../migrations/20261101120000_presence_session_lease_accuracy.sql");
const activity = read("../../app/src/main/java/com/example/MainActivity.kt");
const realtime = read("../../app/src/main/java/com/example/data/supabase/SupabaseRealtimeManager.kt");
const desktopShell = read("../../desktopApp/src/main/kotlin/com/blinkng/desktop/DesktopShell.kt");
const androidChat = read("../../app/src/main/java/com/example/data/repository/ChatRepository.kt");
const desktopPresence = read("../../desktopApp/src/main/kotlin/com/blinkng/desktop/ui/PresenceUi.kt");

test("presence leases are keyed by Supabase Auth session_id and revoked sessions cannot reconnect", () => {
  assert.match(migration, /auth\.jwt\(\)\s*->>\s*'session_id'/);
  assert.match(migration, /from auth\.sessions s[\s\S]*s\.id = v_session_id[\s\S]*s\.user_id = v_user_id/);
  assert.match(migration, /primary key \(user_id, session_id\)/);
});

test("one device ending its session leaves other active devices online", () => {
  assert.match(migration, /where user_id = v_user_id\s+and session_id = v_session_id/);
  assert.match(migration, /select exists \([\s\S]*s\.user_id = v_user_id[\s\S]*\) into v_any_online/);
  assert.match(migration, /when v_any_online then p\.last_seen_at/);
});

test("leases use server time, monotonic updates and a bounded expiry", () => {
  assert.match(migration, /statement_timestamp\(\)/);
  assert.match(migration, /greatest\([\s\S]*last_heartbeat_at[\s\S]*excluded\.last_heartbeat_at/);
  assert.match(migration, /interval '90 seconds'/);
  assert.match(migration, /interval '30 days'/);
});

test("message presence applies online, last-seen and profile visibility independently", () => {
  assert.match(migration, /public\.user_chat_privacy/);
  assert.match(migration, /cp\.show_online/);
  assert.match(migration, /cp\.show_last_seen/);
  assert.match(migration, /us\.show_online_status/);
  assert.match(migration, /presence_visibility/);
  assert.match(migration, /private\.get_chat_presence_for_viewer\(other\.user_id\)/);
});

test("Android backgrounding and Realtime socket loss do not prematurely offline the account", () => {
  assert.match(activity, /stopPresenceHeartbeat\(markOffline = false\)/);
  assert.match(activity, /delay\(30_000\)/);
  const disconnected = realtime.match(/private fun handleDisconnected\(\) \{[\s\S]*?\n    \}/)?.[0] ?? "";
  assert.ok(disconnected.length > 0);
  assert.doesNotMatch(disconnected, /setPresence\(false\)/);
  const heartbeat = realtime.match(/private fun startHeartbeat\(\) \{[\s\S]*?\n    \}/)?.[0] ?? "";
  assert.ok(heartbeat.length > 0);
  assert.doesNotMatch(heartbeat, /setPresence\(true\)/);
});

test("Windows uses the same session lease and clients avoid fabricated recent timestamps", () => {
  assert.match(desktopShell, /appState\.client\.setMyPresence\(true\)/);
  assert.match(androidChat, /Last seen unavailable/);
  assert.match(desktopPresence, /Last seen unavailable/);
});
