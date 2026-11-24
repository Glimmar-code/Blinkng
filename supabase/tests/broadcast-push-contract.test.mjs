import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const root = new URL("../../", import.meta.url);
const read = path => fs.readFileSync(new URL(path, root), "utf8");
const dispatcher = read("supabase/functions/send-broadcast/index.ts");
const testSender = read("supabase/functions/send-test/index.ts");
const service = read("app/src/main/java/com/example/notification/BlinkFirebaseMessagingService.kt");
const helper = read("app/src/main/java/com/example/notification/BlinkNotificationHelper.kt");
const center = read("app/src/main/java/com/example/notification/BlinkInAppNotificationCenter.kt");
const main = read("app/src/main/java/com/example/MainActivity.kt");

test("broadcast push is data-only and account scoped", () => {
  assert.match(dispatcher, /recipient_id:dUserId/);
  assert.match(dispatcher, /type:"broadcast"/);
  assert.match(dispatcher, /target_type:"broadcast"/);
  assert.match(dispatcher, /deep_link:b\.deep_link/);
  assert.doesNotMatch(dispatcher, /notification:\{title:b\.title/);
  assert.match(testSender, /recipient_id:user\.id/);
  assert.doesNotMatch(testSender, /notification:\{title,body\}/);
});

test("Android checks recipient account before routing and display", () => {
  assert.match(service, /isIntendedForCurrentAccount/);
  assert.match(service, /targetType\.equals\("broadcast".*BROADCASTS/s);
  assert.match(center, /BROADCASTS/);
  assert.match(main, /BlinkInAppNotificationDestination\.BROADCASTS.*pendingBroadcastRoute/s);
});

test("background notification tap carries the Broadcast deep link", () => {
  assert.match(helper, /showBroadcastNotification/);
  assert.match(helper, /EXTRA_BROADCAST_DEEP_LINK/);
  assert.match(main, /getStringExtra\(BlinkNotificationHelper\.EXTRA_BROADCAST_DEEP_LINK\)/);
});

test("Android token lifecycle registers and disables Broadcast device tokens", () => {
  assert.match(service, /register_broadcast_device_token/);
  assert.match(service, /device_tokens\?token=eq/);
  assert.match(service, /disabled_at/);
});
