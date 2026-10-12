import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const sql = readFileSync(
  new URL("../supabase/migrations/20261012170000_require_admob_ssv_before_reward_credit.sql", import.meta.url),
  "utf8",
);

test("the completed-ad RPC requires a recorded signed SSV transaction before minting", () => {
  const completion = sql.slice(sql.indexOf("create or replace function public.complete_blink_rewarded_ad_claim"));
  const verifiedGate = completion.indexOf("REWARDED_AD_VERIFICATION_PENDING");
  const balanceUpdate = completion.indexOf("update public.user_balances");
  assert.ok(verifiedGate > 0 && balanceUpdate > verifiedGate);
  assert.match(completion, /v_claim\.ssv_verified_at is null/);
  assert.match(completion, /v_claim\.ssv_transaction_id is null/);
  assert.match(completion, /v_claim\.ssv_reward_amount is distinct from 10/);
});

test("previously credited claims remain idempotent without retroactive SSV", () => {
  const completion = sql.slice(sql.indexOf("create or replace function public.complete_blink_rewarded_ad_claim"));
  assert.ok(completion.indexOf("if v_claim.rewarded_at is not null") <
            completion.indexOf("REWARDED_AD_VERIFICATION_PENDING"));
});

test("a verified delayed reward is preserved across retries and app restarts", () => {
  const begin = sql.slice(
    sql.indexOf("create or replace function public.begin_blink_rewarded_ad_claim"),
    sql.indexOf("create or replace function public.complete_blink_rewarded_ad_claim"),
  );
  assert.match(begin, /and ssv_verified_at is null/);
  assert.match(sql, /and v_claim\.ssv_verified_at is null then/);
});
