import test from "node:test";
import { readFileSync } from "node:fs";
import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { merchantKeyMatchesMode, paystackWebhookSignatureMatches, transactionMatchesOrder } from "../supabase/functions/_shared/paystack_integrity.mjs";

const order={reference:"blink-ps-0123456789abcdef0123456789abcdef",amount_ngn:800,currency:"NGN"};
const successful={id:58223011,amount:80000,currency:"NGN",status:"success",reference:order.reference};
test("Paystack kobo receipt matches server-created NGN order",()=>{
  assert.equal(transactionMatchesOrder(successful,order),true);
});
for(const [title,change] of [
  ["pending",{status:"pending"}],
  ["failed",{status:"failed"}],
  ["reversed",{status:"reversed"}],
  ["claimed callback reference",{reference:"forged"}],
  ["undercount 1 kobo",{amount:79999}],
  ["overpayment",{amount:80001}],
  ["naira instead of kobo",{amount:800}],
  ["wrong currency",{currency:"USD"}],
  ["invalid ID",{id:null}],
  ["fractional kobo",{amount:80000.5}],
])test(title+" fails payment integrity",()=>{
  assert.equal(transactionMatchesOrder({...successful,...change},order),false);
});
test("missing Paystack verification never grants purchase",()=>{
  assert.equal(transactionMatchesOrder(null,order),false);
});

test("test/live merchant key mismatch blocks charges", () => {
  assert.equal(merchantKeyMatchesMode("sk_test_123456789000", "test"), true);
  assert.equal(merchantKeyMatchesMode("sk_live_123456789000", "live"), true);
  assert.equal(merchantKeyMatchesMode("sk_live_123456789000", "test"), false);
  assert.equal(merchantKeyMatchesMode("sk_test_123456789000", "live"), false);
  assert.equal(merchantKeyMatchesMode("pk_test_123456789000", "test"), false);
  assert.equal(merchantKeyMatchesMode("", "test"), false);
  assert.equal(merchantKeyMatchesMode("sk_live_123456789000", "production"), false);
});

test("Paystack signed webhook requires genuine HMAC of unmodified raw payload", async () => {
  const secret = "sk_test_only_a_fake_signing_key_for_unit_tests";
  const payload = JSON.stringify({ event: "charge.success", data: { reference: order.reference, amount: 80000 } });
  const signature = createHmac("sha512", secret).update(payload).digest("hex");
  assert.equal(await paystackWebhookSignatureMatches(payload, signature, secret), true);
  assert.equal(await paystackWebhookSignatureMatches(payload, signature.toUpperCase(), secret), true);
  assert.equal(await paystackWebhookSignatureMatches(payload + " ", signature, secret), false);
  assert.equal(await paystackWebhookSignatureMatches(payload.replace("80000", "1"), signature, secret), false);
  assert.equal(await paystackWebhookSignatureMatches(payload, "x".repeat(128), secret), false);
  assert.equal(await paystackWebhookSignatureMatches(payload, "", secret), false);
  assert.equal(await paystackWebhookSignatureMatches(payload, signature, "sk_test_wrong_key"), false);
});

// The prepaid boost rate alone must not unlock cash ad purchases when checkout is enabled.
test("cash boosts require a separate server-side feature flag", () => {
  const checkoutSource = readFileSync(new URL("../supabase/functions/paystack-checkout/index.ts", import.meta.url), "utf8");
  const gatingIndex = checkoutSource.indexOf('kind==="boost" && Deno.env.get("BLINK_PAYSTACK_BOOST_ENABLED")!=="true"');
  const providerIndex = checkoutSource.indexOf('await paystackApi("transaction/initialize"');
  assert.ok(gatingIndex >= 0 && providerIndex > gatingIndex, "boost gate must precede provider checkout");
  assert.match(checkoutSource.slice(gatingIndex, providerIndex), /CASH_BOOST_DISABLED/);
});


const coinPacks = [
  { code: "coins_100", naira: 100, coins: 100 },
  { code: "coins_500", naira: 500, coins: 550 },
  { code: "coins_1000", naira: 1000, coins: 1200 },
  { code: "coins_2000", naira: 2000, coins: 2600 },
  { code: "coins_5000", naira: 5000, coins: 7000 },
];
for (const pack of coinPacks) {
  test(`coin pack ${pack.code}: exact Paystack kobo value required`, () => {
    const order = { reference: `blink-ps-${"a".repeat(32)}`, amount_ngn: pack.naira, currency: "NGN" };
    const verified = { id: 100000 + pack.naira, amount: pack.naira * 100, currency: "NGN", status: "success", reference: order.reference };
    assert.equal(transactionMatchesOrder(verified, order), true);
    for (const wrong of [
      { amount: pack.naira * 100 - 1 },
      { amount: pack.naira * 100 + 1 },
      { status: "failed" },
      { status: "abandoned" },
      { status: "pending" },
      { currency: "USD" },
      { reference: `blink-ps-${"b".repeat(32)}` },
    ]) assert.equal(transactionMatchesOrder({ ...verified, ...wrong }, order), false);
    assert.ok(pack.coins >= pack.naira, "configured bonus must not reduce coin credit");
  });
}
