import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const load = path => readFileSync(new URL('../../' + path, import.meta.url), 'utf8');
const android = load('app/src/main/java/com/example/ui/components/BlinkContactDiscoveryCard.kt');
const connect = load('app/src/main/java/com/example/ui/screens/ConnectSection.kt');
const notifications = load('app/src/main/java/com/example/ui/screens/ActivityScreen.kt');
const windows = load('desktopApp/src/main/kotlin/com/blinkng/desktop/ui/ContentScreens.kt');
const edge = load('supabase/functions/blink-contact-discovery/index.ts');
const sql = load('supabase/migrations/20261027002000_private_contact_discovery.sql');
const integration = load('supabase/tests/run-contact-discovery-integration.mjs');

test('Connect exposes opt-in Find Friends and actionable account suggestions', () => {
  assert.match(connect, /BlinkContactDiscoveryCard\(/);
  assert.match(connect, /FollowStateStore\.setFollowing\(person\.id, true\)/);
  assert.match(android, /AsyncImage\(model = person\.avatarUrl/);
  assert.match(android, /onProfile\(person\.username\)/);
  assert.match(android, /onFollow\(person\)/);
  assert.match(android, /Last synced:/);
  assert.match(android, /Delete synced contacts/);
});

test('Windows and Android have no SMS controls and feature gate matching', () => {
  for (const ui of [windows, android]) {
    assert.doesNotMatch(ui, /Send SMS code|Six-digit SMS code|Text\("Verify phone"\)/);
  }
  assert.match(windows, /contactMutualReady/);
  assert.match(android, /mutualMatchingReady && serviceReady/);
  assert.match(edge, /match_policy: matchPolicy/);
});

test('joined-contact notification is explicitly a possible, unverified match', () => {
  assert.match(notifications, /View possible contact/);
  assert.match(notifications, /isContactMatch/);
  assert.match(sql, /A possible contact joined BLINK/);
  assert.match(sql, /mutual_unverified/);
  assert.doesNotMatch(sql, /Someone from your contacts joined BLINK/);
});

test('no-SMS matching requires reciprocal consent and server-side HMAC', () => {
  assert.match(sql, /private\.blink_contact_imports reciprocal/);
  assert.match(sql, /own\.discoverable=true/);
  assert.match(sql, /target\.sync_enabled=true and target\.discoverable=true/);
  assert.match(sql, /private\.blink_contact_directory owner_phone/);
  assert.match(sql, /blink_contact_notify_my_join/);
  assert.match(sql, /delete from private\.blink_contact_join_alerts/);
  assert.match(edge, /auth\.getUser\(jwt\)/);
  assert.match(edge, /crypto\.subtle\.sign\("HMAC"/);
  assert.doesNotMatch(edge, /phone_confirmed_at/);
  assert.match(integration, /one-sided imports must never expose/);
  assert.match(integration, /blink_contact_notify_my_join/);
});
