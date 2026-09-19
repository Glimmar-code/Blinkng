import assert from "node:assert/strict";
import { createPrivateKey, sign } from "node:crypto";
import test from "node:test";

import {
  parseAdMobSignedQuery,
  verifyAdMobSignedQuery,
} from "../supabase/functions/admob-reward-ssv/admob_ssv_verifier.mjs";

// Published by Google's Tink RewardedAdsVerifier test suite.
const GOOGLE_TEST_KEYS = {
  keys: [
    {
      keyId: 1234,
      base64:
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPYnHwS8uegWAewQtlxizmLFynwHcxRT1PK07cDA6/C4sXrVI1SzZCUx8U8S0LjMrT6ird/VW7be3Mz6t/srtRQ==",
    },
  ],
};
const GOOGLE_TEST_PRIVATE_KEY =
  "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgZj/Dldxz8fvKVF5OTeAtK6tY3G1McmvhMppe6ayW6GahRANCAAQ9icfBLy56BYB7BC2XGLOYsXKfAdzFFPU8rTtwMDr8LixetUjVLNkJTHxTxLQuMytPqKt39Vbtt7czPq3+yu1F";

function signedQuery(encodedSignedContent) {
  const privateKey = createPrivateKey({
    key: Buffer.from(GOOGLE_TEST_PRIVATE_KEY, "base64"),
    format: "der",
    type: "pkcs8",
  });
  const signature = sign(
    "sha256",
    Buffer.from(decodeURIComponent(encodedSignedContent), "utf8"),
    { key: privateKey, dsaEncoding: "der" },
  ).toString("base64url");
  return `${encodedSignedContent}&signature=${signature}&key_id=1234`;
}

test("verifies Google's decoded-query SSV contract", async () => {
  const content =
    "ad_network=5450213213286189855&ad_unit=4343111201&custom_data=claim%2F123&reward_amount=10&reward_item=Blink%20Coins&timestamp=1507770365237823&transaction_id=abc123&user_id=user%40blink.com";
  const result = await verifyAdMobSignedQuery(signedQuery(content), GOOGLE_TEST_KEYS);

  assert.equal(result.verified, true);
  assert.equal(result.params.get("reward_item"), "Blink Coins");
  assert.equal(result.params.get("custom_data"), "claim/123");
  assert.equal(result.params.get("user_id"), "user@blink.com");
});

test("rejects a changed signed reward value", async () => {
  const content = "reward_amount=10&reward_item=Blink%20Coins";
  const callback = signedQuery(content).replace("reward_amount=10", "reward_amount=1000");
  const result = await verifyAdMobSignedQuery(callback, GOOGLE_TEST_KEYS);
  assert.equal(result.verified, false);
});

test("rejects unsigned parameters appended after key_id", () => {
  const callback = `${signedQuery("reward_amount=10")}&custom_data=forged`;
  assert.throws(
    () => parseAdMobSignedQuery(callback),
    /signature and key_id must be the final two parameters/,
  );
});

test("rejects duplicate signature envelopes", () => {
  const callback = signedQuery("reward_amount=10&signature=forged");
  assert.throws(() => parseAdMobSignedQuery(callback), /Duplicate AdMob signature envelope/);
});
