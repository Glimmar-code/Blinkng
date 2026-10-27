import assert from "node:assert/strict";
import { pushPreferenceDecision, verifiedNotificationActor } from "../functions/send-activity-notification/push-gates.mjs";

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
