import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const migration = read("../migrations/20261101143000_blink_contest_launch_notifications.sql");
const pushWorker = read("../functions/blink-channel-push-worker/index.ts");
const activityWorker = read("../functions/send-activity-notification/index.ts");
const emailWorker = read("../functions/send-essential-email/index.ts");
const androidMain = read("../../app/src/main/java/com/example/MainActivity.kt");
const androidFcm = read("../../app/src/main/java/com/example/notification/BlinkFirebaseMessagingService.kt");
const windowsShell = read("../../desktopApp/src/main/kotlin/com/blinkng/desktop/DesktopShell.kt");
const windowsNotifications = read("../../desktopApp/src/main/kotlin/com/blinkng/desktop/ui/ContentScreens.kt");

test("Contest launch fan-out is only the first draft-to-published transition", () => {
  assert.match(migration, /old\.status\s*<>\s*'draft'/i);
  assert.match(migration, /new\.status\s*<>\s*'published'/i);
  assert.match(migration, /after update of status on public\.blink_contests/i);
  assert.match(migration, /unique\s*\(user_id,kind,event_key\)/i);
  assert.match(migration, /contest_launch:/i);
});

test("in-app fan-out only targets provisioned, active profiles and stores contest routing metadata", () => {
  assert.match(migration, /join public\.profiles p on p\.id\s*=\s*u\.id/i);
  assert.match(migration, /u\.deleted_at is null/i);
  assert.match(migration, /u\.banned_until is null or u\.banned_until <= now\(\)/i);
  assert.match(migration, /'blink_contest_launch'/i);
  assert.match(migration, /'contest',new\.id/i);
  assert.match(migration, /actor_id,type,target_type,target_id,text,sub_text,is_read,metadata/i);
});

test("email fan-out is verified-email-only and honours the email master switch", () => {
  assert.match(migration, /u\.email_confirmed_at is not null/i);
  assert.match(migration, /coalesce\(s\.email_notifs_enabled,true\)/i);
  assert.match(migration, /on conflict\(user_id,kind,event_key\) do nothing/i);
  assert.match(emailWorker, /row\.kind === "contest_launch"/);
  assert.match(emailWorker, /email_notifs_enabled/);
  assert.match(emailWorker, /user\.email_confirmed_at/);
  assert.match(emailWorker, /stale_delivery_recovered/);
  assert.match(emailWorker, /nextAttempts >= 4/);
  assert.match(emailWorker, /contest\.status !== "published"/);
});

test("push delivery is durable, preference-gated and validates the live contest server-side", () => {
  assert.match(migration, /blink_contest_launch/);
  assert.match(pushWorker, /send-activity-notification/);
  assert.match(pushWorker, /send-essential-email/);
  assert.match(migration, /status='sending'.*updated_at<now\(\)-interval '10 minutes'/s);
  assert.match(activityWorker, /kind === "blink_contest_launch"/);
  assert.match(activityWorker, /contest\.status !== "published"/);
  assert.match(activityWorker, /push_notifs_enabled/);
  assert.match(activityWorker, /return "admin"/);
  assert.match(activityWorker, /A New BLINK Contest Is LIVE!/);
});

test("Android and Windows notification taps route to Contest", () => {
  assert.match(androidFcm, /targetType\.equals\("contest".*BlinkInAppNotificationDestination\.CONTEST/s);
  assert.match(androidMain, /ACTION_OPEN_CONTEST/);
  assert.match(androidMain, /"contest"\s*->\s*\{/);
  assert.match(windowsShell, /item\.targetType\.equals\("contest".*-> "connect"/s);
  assert.match(windowsNotifications, /item\.targetType\.equals\("contest".*state\.selectedRoute = "connect"/s);
});
