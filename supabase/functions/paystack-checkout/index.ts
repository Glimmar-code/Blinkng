import { createClient } from "npm:@supabase/supabase-js@2";
import { currentUser, json, paystackApi, preflight } from "../_shared/blink_paystack.ts";

Deno.serve(async req => {
  if(req.method==="OPTIONS") return preflight();
  if(req.method!=="POST") return json({error:"METHOD_NOT_ALLOWED"},405);
  const user=await currentUser(req);
  if(!user) return json({error:"SIGN_IN_REQUIRED"},401);
  if(!user.email) return json({error:"ACCOUNT_EMAIL_REQUIRED"},400);
  // Checkout requires a verified, authenticated account; prices and delivery remain server-controlled.
  if(!user.email_confirmed_at) return json({error:"EMAIL_CONFIRMATION_REQUIRED"},403);
  // Additional independent kill switch; database cash_checkout_enabled is also required.
  if(Deno.env.get("BLINK_PAYSTACK_CHECKOUT_ENABLED")!=="true")
    return json({error:"CHECKOUT_DISABLED"},503);
  try {
    const payload=await req.json();
    const kind=String(payload.kind||"").toLowerCase();
    const itemCode=String(payload.itemCode||"").trim();
    const months=payload.months === undefined ? 1 : payload.months;
    if(!["verification","coin_pack","boost","marketplace","gift"].includes(kind)
      || !/^[a-zA-Z0-9_]{1,30}$/.test(itemCode)
      || !Number.isInteger(months) || ![1,3,6,12].includes(months)
      || (kind!=="verification" && months!==1))
      return json({error:"INVALID_CHECKOUT_SELECTION"},400);
    // Extra independent product-type kill switches. These must be explicitly
    // enabled by the approved merchant *after* successful staging tests.
    if(kind==="boost" && Deno.env.get("BLINK_PAYSTACK_BOOST_ENABLED")!=="true")
      return json({error:"CASH_BOOST_DISABLED"},503);
    if(kind==="marketplace" && Deno.env.get("BLINK_PAYSTACK_MARKET_ENABLED")!=="true")
      return json({error:"MARKET_CHECKOUT_DISABLED"},503);
    if(kind==="gift" && Deno.env.get("BLINK_PAYSTACK_GIFTS_ENABLED")!=="true")
      return json({error:"GIFT_PURCHASE_DISABLED"},503);
    const auth=req.headers.get("authorization") || "";
    const caller=createClient(Deno.env.get("SUPABASE_URL")!,Deno.env.get("SUPABASE_ANON_KEY")!,{
      global:{headers:{Authorization:auth}},
      auth:{persistSession:false,autoRefreshToken:false},
    });
    const orderCall=kind==="boost"
      ? {name:"create_blink_paystack_boost_order",args:{
          p_target_type:payload.targetType,p_target_id:payload.targetId,
          p_boost_power:payload.boostPower,p_objective:payload.objective,
          p_audience_scope:payload.audienceScope,
          p_duration_days:payload.durationDays,
          p_target_university:payload.targetUniversity||null,
        }}
      : kind==="marketplace"
      ? {name:"create_blink_paystack_market_order",args:{
          p_listing:payload.listingId,
        }}
      : kind==="gift"
      ? {name:"create_blink_paystack_gift_order",args:{
          p_username:payload.recipientUsername,p_pack_id:payload.packId,
          p_message:payload.message||null,
        }}
      : {name:"create_blink_paystack_order_v2",args:{
          p_kind:kind,p_item_code:itemCode,p_months:months,
        }};
    if((kind==="boost" && itemCode!=="BOOST") ||
       (kind==="marketplace" && (itemCode!=="MARKET_ITEM" ||
         typeof payload.listingId!=="string" ||
         !/^[0-9a-f-]{36}$/i.test(payload.listingId))) ||
       (kind==="gift" && (itemCode!=="GIFT_COINS" ||
         typeof payload.recipientUsername!=="string" ||
         !/^[a-zA-Z0-9_.]{2,40}$/.test(payload.recipientUsername) ||
         typeof payload.packId!=="string" ||
         !/^coins_[0-9]+$/.test(payload.packId) ||
         (payload.message && (typeof payload.message!=="string" ||
         payload.message.length>160)))))
      return json({error:"INVALID_CHECKOUT_SELECTION"},400);
    const {data:order,error:orderError}=await caller.rpc(orderCall.name,orderCall.args);
    if(orderError || !order) return json({error:orderError?.message || "CHECKOUT_UNAVAILABLE"},400);
    const charge=await paystackApi("transaction/initialize","POST",{
      email:user.email,
      amount:order.amount_ngn*100, // Paystack charges in kobo, not naira.
      currency:"NGN",
      reference:order.reference,
      callback_url:"https://blink.com.ng/?blink_payment_return=1",
      metadata:{app:"BLINK",purchase_kind:kind,item_code:order.item_code,months},
    });
    const url=String(charge?.authorization_url || "");
    if(!url.startsWith("https://checkout.paystack.com/") || charge.reference!==order.reference)
      throw new Error("INVALID_PAYSTACK_CHECKOUT_URL");
    return json({ checkoutUrl:url, reference:order.reference, amountNgn:order.amount_ngn,
      currency:"NGN",kind,itemCode:order.item_code,months,
      discountPercent:order.discount_percent||0,autoRenew:false });
  } catch(error) {
    console.error("paystack-checkout:",String(error).slice(0,160));
    return json({error:"PAYMENT_UNAVAILABLE"},503);
  }
});
