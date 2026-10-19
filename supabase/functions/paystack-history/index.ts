import { currentUser, json, preflight, serviceClient } from "../_shared/blink_paystack.ts";

// Customer-only receipts; service credentials NEVER leave Supabase.
// Works only after the Paystack migration is deployed and approved.
Deno.serve(async req => {
  if (req.method === "OPTIONS") return preflight();
  if (req.method !== "POST") return json({error:"METHOD_NOT_ALLOWED"},405);
  const user=await currentUser(req);
  if (!user) return json({error:"SIGN_IN_REQUIRED"},401);
  try {
    const service=serviceClient();
    const {data,error}=await service.from("blink_paystack_payments")
      .select("reference,purchase_kind,item_code,amount_ngn,currency,duration_days,status,created_at,paid_at")
      .eq("user_id",user.id).order("created_at",{ascending:false}).limit(50);
    if (error) throw new Error("RECEIPTS_QUERY_FAILED");
    return json({receipts:data||[]});
  } catch(error) {
    console.error("paystack-history:",String(error).slice(0,120));
    return json({error:"PAYMENT_HISTORY_UNAVAILABLE"},503);
  }
});
