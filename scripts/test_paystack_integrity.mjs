import test from "node:test";
import assert from "node:assert/strict";
import { matchesPaystackReceipt } from "../supabase/functions/_shared/paystack_integrity.mjs";
const order = {
  reference:"blink-ps-0123456789abcdef0123456789abcdef",
  amount_kobo:80000,
  currency:"NGN",
  customer_email:"user@example.test",
};
const ok = {
  reference:order.reference,
  id:234982,
  status:"success",
  amount:80000,
  currency:"NGN",
  customer:{email:"USER@example.test"},
};
test("exact server-verified ₦800 receipt passes",()=>assert.equal(matchesPaystackReceipt(ok,order),true));
for(const [description,update] of [
  ["forged callback", {status:"pending"}],
  ["failed charge", {status:"failed"}],
  ["wrong reference", {reference:"other"}],
  ["underpayment", {amount:79900}],
  ["overpayment", {amount:80100}],
  ["wrong currency", {currency:"USD"}],
  ["wrong customer", {customer:{email:"attacker@example.test"}}],
  ["missing provider transaction id", {id:null}],
  ["fractional kobo", {amount:80000.5}],
]) test(description+" rejected",()=>assert.equal(matchesPaystackReceipt({...ok,...update},order),false));
test("no provider response rejected",()=>assert.equal(matchesPaystackReceipt(null,order),false));
