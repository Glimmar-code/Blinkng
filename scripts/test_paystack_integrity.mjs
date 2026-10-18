import test from "node:test";
import assert from "node:assert/strict";
import { paystackMatchesOrder } from "../supabase/functions/_shared/paystack_integrity.mjs";

const order={reference:"BLINK-PS-0123456789abcdef0123456789abcdef",amount_ngn:800,currency:"NGN"};
const good={status:"success",reference:order.reference,amount:80000,currency:"NGN",id:17482};
test("server-priced NGN payment is accepted",()=>{
  assert.equal(paystackMatchesOrder(good,order),true);
});
const invalid=[
 ["failed payment",{status:"failed"}],
 ["pending payment",{status:"pending"}],
 ["other reference",{reference:"BLINK-PS-deadbeef"}],
 ["underpay",{amount:79999}],
 ["overpay",{amount:80001}],
 ["wrong currency",{currency:"USD"}],
 ["negative provider transaction id",{id:-5}],
 ["missing provider transaction id",{id:null}],
 ["fractional kobo",{amount:80000.5}],
 ["zero kobo",{amount:0}],
 ["huge unsafe id",{id:Number.MAX_SAFE_INTEGER+2}],
];
for(const [title,change] of invalid){
 test("rejects "+title,()=>assert.equal(paystackMatchesOrder({...good,...change},order),false));
}
test("null receipts are rejected",()=>assert.equal(paystackMatchesOrder(null,order),false));
