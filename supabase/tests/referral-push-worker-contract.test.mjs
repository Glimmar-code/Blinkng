import assert from "node:assert/strict";
import test from "node:test";
import {
  isSettledReferralDelivery,
  isPersistedOutboxFinish,
} from "../functions/blink-referral-push-worker/delivery-result.mjs";

test("only real sends and allowed permanent skips settle an outbox item", () => {
  assert.equal(isSettledReferralDelivery(true, { ok: true, delivered: 1 }), true);
  assert.equal(isSettledReferralDelivery(true, { delivered: 2 }), true);
  for (const reason of ["already_sent", "recipient_preferences", "self_or_missing_recipient"]) {
    assert.equal(isSettledReferralDelivery(true, { ok: true, skipped: reason }), true, reason);
  }
});

test("transient skips and failures do not mark a push sent", () => {
  for (const reason of [
    "recipient_has_no_push_token", "already_sending", "firebase_auth_failed",
    "message_pipeline", "rate_limited",
  ]) {
    assert.equal(isSettledReferralDelivery(true, { ok: true, skipped: reason }), false, reason);
  }
  assert.equal(isSettledReferralDelivery(false, { delivered: 1 }), false);
  assert.equal(isSettledReferralDelivery(true, { delivered: 1, ok: false }), false);
  assert.equal(isSettledReferralDelivery(true, { delivered: 1, error: "failed" }), false);
  assert.equal(isSettledReferralDelivery(true, { delivered: 0 }), false);
  assert.equal(isSettledReferralDelivery(true, {}), false);
  assert.equal(isSettledReferralDelivery(true, null), false);
});

test("an outbox acknowledgement requires a true database result, not only no error", () => {
  assert.equal(isPersistedOutboxFinish(true, null), true);
  assert.equal(isPersistedOutboxFinish(false, null), false);
  assert.equal(isPersistedOutboxFinish(null, null), false);
  assert.equal(isPersistedOutboxFinish(undefined, null), false);
  assert.equal(isPersistedOutboxFinish(true, { message: "connection failure" }), false);
});
