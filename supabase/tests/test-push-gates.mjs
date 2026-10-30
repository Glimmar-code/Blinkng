import assert from "node:assert/strict";
import { pushPreferenceDecision, verifiedNotificationActor } from "../functions/send-activity-notification/push-gates.mjs";
import { chatPushPreview } from "../functions/_shared/chat-preview.mjs";

assert.equal(pushPreferenceDecision(true, null), "send", "explicit consent permits delivery");
assert.equal(pushPreferenceDecision(false, null), "skip", "opt-out suppresses delivery");
assert.equal(pushPreferenceDecision(null, null), "retry", "unexpected RPC null must not send");
assert.equal(pushPreferenceDecision(undefined, null), "retry", "missing RPC must not send");
assert.equal(pushPreferenceDecision("true", null), "retry", "untrusted string must not opt in");
assert.equal(pushPreferenceDecision(true, { message: "backend unavailable" }), "retry", "RPC errors must not bypass privacy");
assert.equal(pushPreferenceDecision(false, { message: "backend unavailable" }), "retry", "RPC errors are not successful opt-outs");
console.log("Push preference fail-closed policy: 7 cases passed.");

const id = "123e4567-e89b-42d3-a456-426614174000";
assert.equal(await verifiedNotificationActor("Bearer valid-token", async token => ({
  data: { user: { id: token === "valid-token" ? id : "" } }, error: null,
})), id);
assert.equal(await verifiedNotificationActor("Bearer forged-jwt", async () => ({
  data: { user: null }, error: new Error("bad JWT signature"),
})), null, "fabricated JWT subject must never authorize a push");
assert.equal(await verifiedNotificationActor("Bearer expired", async () => {
  throw Error("Auth service unavailable");
}), null, "Auth outage must fail closed");
assert.equal(await verifiedNotificationActor("Bearer invalid", async () => ({
  data: { user: { id: "not-a-uuid" } }, error: null,
})), null, "invalid subjects cannot be trusted");
assert.equal(await verifiedNotificationActor("Token no", async () => ({
  data: { user: { id } }, error: null,
})), null, "bearer token is required");
console.log("Notification actor JWT validation: 5 cases passed.");

import { readFileSync } from "node:fs";
const backendFiles = [
  "../functions/send-activity-notification/index.ts",
  "../functions/send-push-notification/index.ts",
  "../functions/send-call-notification/index.ts",
];
for (const file of backendFiles) {
  const source = readFileSync(new URL(file, import.meta.url), "utf8");
  assert.ok(source.includes("verifiedNotificationActor("),
    `Missing verified sender authorization in ${file}`);
  assert.ok(!source.includes("jwtSubject("),
    `Decode-only JWT subject remains in ${file}`);
  assert.ok(source.includes("pushPreferenceDecision(allowed, allowedError)"),
    `Push preferences can be bypassed in ${file}`);
  assert.ok(source.includes('error: "notification_preferences_unavailable"'),
    `Unknown preferences are not retried in ${file}`);
  assert.ok(source.includes('"dispatch_ledger_unavailable"') ||
      source.includes('"call_dispatch_ledger_unavailable"'),
    `Failed dispatch ledger must not send untracked push in ${file}`);
}
const directMessage = readFileSync(
  new URL("../functions/send-push-notification/index.ts", import.meta.url), "utf8");
assert.ok(directMessage.includes('"conversation_preferences_unavailable"'),
  "Message push must not bypass unavailable mute settings");
console.log("PASS message, call and activity push sender/consent/dedup regression checks");

const turnSource = readFileSync(
  new URL("../functions/call-turn-credentials/index.ts", import.meta.url), "utf8");
assert.ok(turnSource.includes("verifiedNotificationActor("),
  "Temporary TURN relay credentials must be issued only after verifying JWT");
assert.ok(!turnSource.includes("jwtSubject("),
  "Never mint TURN credentials from unverified JWT subjects");
assert.ok(turnSource.includes('if (!userId) return json({ error: "Unauthorized" }, 401)'),
  "TURN relay must deny unauthenticated callers");
console.log("PASS TURN relay credential JWT verification contract");

// Recipient privacy must be enforced in the server payload, even when a
// different device has a stale "full" preview preference in its local cache.
const hidden=chatPushPreview("hidden", "Alice", "A private message", "https://example.com/avatar.png");
assert.deepEqual(hidden, {
  title: "BLINK Messages", body: "New message", senderName: "", senderAvatar: "",
});
const senderOnly=chatPushPreview("sender_only", "Alice", "A private message", "avatar");
assert.equal(senderOnly.title, "Alice");
assert.equal(senderOnly.body, "New message");
const full=chatPushPreview("full", "Alice", "A private message", "avatar");
assert.equal(full.body, "A private message");
assert.equal(full.senderAvatar, "avatar");
assert.equal(chatPushPreview(undefined, "Alice", "Secret").body, "New message");
assert.equal(chatPushPreview("unsupported", "Alice", "Secret").senderName, "");
assert.equal(chatPushPreview("full", "Alice", "x".repeat(1500)).body.length, 1200);
console.log("Recipient-side chat preview privacy: hidden, sender-only, full, unknown and truncation passed.");

assert.ok(directMessage.includes('.from("user_chat_privacy")'),"Push must load authoritative recipient preview setting");
assert.ok(directMessage.includes('error: "chat_preview_preferences_unavailable"'),"Unknown server preview preferences must not disclose content");
assert.ok(directMessage.includes("chatPushPreview(previewMode, senderName, message.content, senderAvatar)"),
  "Firebase push must use server-sanitized content");
assert.ok(!directMessage.includes('body: notificationBody'),'Firebase must not send an unchecked raw message body');
