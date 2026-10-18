import { createClient } from "npm:@supabase/supabase-js@2";
import { currentUser, json, paystackApi, preflight } from "../_shared/blink_paystack.ts";

Deno.serve(async req => {
  if(req.method==="OPTIONS") return preflight();
  if(req.method!=="POST") return json({error:"METHOD_NOT_ALLOWED"},405);
  const user=await currentUser(req);
  if(!user) return json({error:"SIGN_IN_REQUIRED"},401);
  if(!user.email) return json({error:"ACCOUNT_EMAIL_REQUIRED"},400);
  // Additional independent kill switch; database cash_checkout_enabled is also required.
  if(Deno.env.get("BLINK_PAYSTACK_CHECKOUT_ENABLED")!=="true")
    return json({error:"CHECKOUT_DISABLED"},503);
  try {
    const payload=await req.json();
    const kind=String(payload.kind||"").toLowerCase();
    const itemCode=String(payload.itemCode||"").trim();
    if(!["verification","coin_pack"].includes(kind) || !/^[a-zA-Z0-9_]{1,30}$/.test(itemCode))
      return json({error:"INVALID_CHECKOUT_SELECTION"},400);
    const auth=req.headers.get("authorization") || "";
    const caller=createClient(Deno.env.get("SUPABASE_URL")!,Deno.env.get("SUPABASE_ANON_KEY")!,{
      global:{headers:{Authorization:auth}},
      auth:{persistSession:false,autoRefreshToken:false},
    });
    const {data:order,error:orderError}=await caller.rpc("create_blink_paystack_order",{
      p_kind:kind,p_item_code:itemCode,
    });
    if(orderError || !order) return json({error:orderError?.message || "CHECKOUT_UNAVAILABLE"},400);
    const charge=await paystackApi("transaction/initialize","POST",{
      email:user.email,
      amount:order.amount_ngn*100, // Paystack charges in kobo, not naira.
      currency:"NGN",
      reference:order.reference,
      callback_url:"https://blink.com.ng/?blink_payment_return=1",
      metadata:{app:"BLINK",purchase_kind:kind,item_code:order.item_code},
    });
    const url=String(charge?.authorization_url || "");
    if(!url.startsWith("https://checkout.paystack.com/") || charge.reference!==order.reference)
      throw new Error("INVALID_PAYSTACK_CHECKOUT_URL");
    return json({ checkoutUrl:url, reference:order.reference, amountNgn:order.amount_ngn,
      currency:"NGN",kind,itemCode:order.item_code });
  } catch(error) {
    console.error("paystack-checkout:",String(error).slice(0,160));
    return json({error:"PAYMENT_UNAVAILABLE"},503);
  }
});
