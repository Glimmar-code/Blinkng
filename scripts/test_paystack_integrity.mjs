import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
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
  ["abandoned or cancelled by customer",{status:"abandoned"}],
  ["provider-cancelled",{status:"cancelled"}],
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


// Server-side checkout eligibility must be enforced before provider initialization.
test("public checkout requires signed-in confirmed email and independent merchant controls", () => {
  const checkout = readFileSync(new URL("../supabase/functions/paystack-checkout/index.ts", import.meta.url), "utf8");
  assert.doesNotMatch(checkout, /CHECKOUT_OWNER_PILOT_ONLY|golowosile@gmail\\.com/i);
  const signIn = checkout.indexOf('if(!user) return json({error:"SIGN_IN_REQUIRED"},401)');
  const confirmed = checkout.indexOf('if(!user.email_confirmed_at) return json({error:"EMAIL_CONFIRMATION_REQUIRED"},403)');
  const killSwitch = checkout.indexOf('BLINK_PAYSTACK_CHECKOUT_ENABLED');
  const boostGate = checkout.indexOf('BLINK_PAYSTACK_BOOST_ENABLED');
  const provider = checkout.indexOf('await paystackApi("transaction/initialize"');
  assert.ok(signIn >= 0 && confirmed > signIn && killSwitch > confirmed);
  assert.ok(boostGate > killSwitch && provider > boostGate);
});
