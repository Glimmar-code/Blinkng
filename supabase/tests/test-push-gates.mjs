import assert from "node:assert/strict";
import { pushPreferenceDecision } from "../functions/send-activity-notification/push-gates.mjs";

assert.equal(pushPreferenceDecision(true, null), "send", "explicit consent permits delivery");
assert.equal(pushPreferenceDecision(false, null), "skip", "opt-out suppresses delivery");
assert.equal(pushPreferenceDecision(null, null), "retry", "unexpected RPC null must not send");
assert.equal(pushPreferenceDecision(undefined, null), "retry", "missing RPC must not send");
assert.equal(pushPreferenceDecision("true", null), "retry", "untrusted string must not opt in");
assert.equal(pushPreferenceDecision(true, { message: "backend unavailable" }), "retry", "RPC errors must not bypass privacy");
assert.equal(pushPreferenceDecision(false, { message: "backend unavailable" }), "retry", "RPC errors are not successful opt-outs");
console.log("Push preference fail-closed policy: 7 cases passed.");
