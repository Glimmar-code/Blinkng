import test from "node:test";
import assert from "node:assert/strict";
import { transactionMatchesOrder } from "../supabase/functions/_shared/flutterwave_integrity.mjs";

const order = { tx_ref: "blink-flw-0123456789abcdef0123456789abcdef", amount_ngn: 800, currency: "NGN" };
const payment = { tx_ref: order.tx_ref, amount: 800, currency: "NGN", status: "successful", id: 14232 };

test("matching server-verified Flutterwave receipt passes", () => {
  assert.equal(transactionMatchesOrder(payment, order), true);
});
for (const [name, modification] of [
  ["client-only success callback", { status: "pending" }],
  ["failed transaction", { status: "failed" }],
  ["swapped reference", { tx_ref: "blink-flw-other" }],
  ["underpayment", { amount: 799 }],
  ["overpayment", { amount: 801 }],
  ["incorrect currency", { currency: "USD" }],
  ["missing transaction id", { id: null }],
  ["fractional amount", { amount: 800.5 }],
  ["invalid transaction id", { id: "not-an-id" }],
]) {
  test(name + " is rejected", () => {
    assert.equal(transactionMatchesOrder({ ...payment, ...modification }, order), false);
  });
}
test("missing provider response is not a payment", () => {
  assert.equal(transactionMatchesOrder(null, order), false);
});
