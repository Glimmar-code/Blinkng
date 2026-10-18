import test from "node:test";
import assert from "node:assert/strict";
import { merchantKeyMatchesMode, transactionMatchesOrder } from "../supabase/functions/_shared/paystack_integrity.mjs";

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
