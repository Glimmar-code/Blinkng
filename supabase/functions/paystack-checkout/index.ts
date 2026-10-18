import { createClient } from "npm:@supabase/supabase-js@2";
import { authenticatedUser, json, paystackApi, requiredEnv, responseHeaders } from "../_shared/blink_paystack.ts";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: responseHeaders });
  if (req.method !== "POST") return json({ error:"METHOD_NOT_ALLOWED" },405);
  const user = await authenticatedUser(req);
  if (!user) return json({ error:"AUTH_REQUIRED" },401);
  if (!user.email) return json({ error:"ACCOUNT_EMAIL_REQUIRED" },400);
  if (Deno.env.get("BLINK_PAYSTACK_CHECKOUT_ENABLED") !== "true") {
    return json({ error:"CHECKOUT_NOT_ENABLED" },503);
  }
  try {
    const payload = await req.json();
    const kind = String(payload?.kind || "").toUpperCase();
    const tier = String(payload?.tier || "").toUpperCase();
    const packId = String(payload?.packId || "");
    if (!["VERIFICATION","COINS"].includes(kind) ||
        (kind==="VERIFICATION" && !["BLUE","GOLD"].includes(tier)) ||
        (kind==="COINS" && !/^coins_(100|500|1000|2000|5000)$/.test(packId))) {
      return json({ error:"INVALID_PRODUCT" },400);
    }
    const bearer = req.headers.get("authorization")!;
    const caller = createClient(requiredEnv("SUPABASE_URL"), requiredEnv("SUPABASE_ANON_KEY"),{
      global:{ headers:{ Authorization:bearer } },
      auth:{ persistSession:false, autoRefreshToken:false },
    });
    const { data:order,error } = await caller.rpc("create_blink_paystack_order",{
      p_kind:kind,
      p_tier:kind==="VERIFICATION"?tier:null,
      p_pack_id:kind==="COINS"?packId:null,
    });
    if (error || !order) {
      const allowed = ["CASH_CHECKOUT_DISABLED","GOLD_REQUIRES_1000_FOLLOWERS",
        "INVALID_COIN_PACK","PAYMENT_RATE_LIMIT","ALREADY_GOLD_VERIFIED"];
      const userCode = allowed.find(code=>error?.message?.includes(code));
      return json({ error:userCode || "ORDER_UNAVAILABLE" },400);
    }
    // Paystack expects the amount in kobo. The database determines the price.
    const result = await paystackApi("transaction/initialize","POST",{
      amount:String(order.amount_kobo),
      currency:"NGN",
      reference:order.reference,
      email:user.email.trim().toLowerCase(),
      callback_url:"https://blink.com.ng/?blink_payment_return=1",
      metadata:{ app:"BLINK", kind, order_id:order.id },
    });
    const url = String(result?.authorization_url || "");
    if (result?.reference!==order.reference ||
        !/^https:\/\/checkout\.paystack\.com\/[a-zA-Z0-9_-]+$/.test(url)) {
      throw Error("CHECKOUT_LINK_INVALID");
    }
    return json({
      checkoutUrl:url,reference:order.reference,kind,
      tier:order.tier,amountKobo:order.amount_kobo,currency:"NGN",
    });
  } catch(error) {
    console.error("paystack-checkout",String(error).slice(0,160));
    return json({ error:"CHECKOUT_TEMPORARILY_UNAVAILABLE" },503);
  }
});
