import { createClient } from "npm:@supabase/supabase-js@2";
import { currentUser, json, preflight, serviceClient } from "../_shared/blink_paystack.ts";

// Read-only, strictly OWNER-authorized cash ledger; no secret keys, bank details,
// recipient accounts or personally identifying user information sent to Android.
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return preflight();
  if (req.method !== "POST") return json({error:"METHOD_NOT_ALLOWED"},405);
  const user = await currentUser(req);
  if (!user) return json({error:"SIGN_IN_REQUIRED"},401);
  try {
    const authorization=req.headers.get("Authorization") || "";
    const client = createClient(Deno.env.get("SUPABASE_URL")!,Deno.env.get("SUPABASE_ANON_KEY")!,{
      global:{headers:{Authorization:authorization}},
      auth:{autoRefreshToken:false,persistSession:false},
    });
    // Server authenticates owner against the existing privileged BLINK Admin RPC.
    // A client-supplied isOwner property is NEVER trusted.
    const {data:capability,error:capError}=await client.rpc("admin_get_capability");
    if (capError) return json({error:"ADMIN_LOOKUP_FAILED"},503);
    if (capability?.is_owner !== true) return json({error:"OWNER_REQUIRED"},403);

    const svc=serviceClient();
    const end = new Date();
    const start = new Date(end.getTime()-30*86400000);
    const {data:report,error:reportError}=await svc.rpc("blink_paystack_financial_report",{
      p_start:start.toISOString(),p_end:end.toISOString(),
    });
    if (reportError) throw new Error("FINANCE_AGGREGATE_NOT_AVAILABLE");
    const {data:items,error:itemsError}=await svc
      .from("blink_paystack_payments")
      .select("reference,purchase_kind,item_code,amount_ngn,status,created_at")
      .gte("created_at",start.toISOString())
      .order("created_at",{ascending:false}).limit(50);
    if(itemsError) throw new Error("FINANCE_LEDGER_NOT_AVAILABLE");
    const totals: Record<string,number>={verification:0,coin_pack:0,boost:0,gift:0,marketplace:0};
    for (const x of items||[]) {
      if(x.status==="fulfilled" && x.purchase_kind in totals)
        totals[x.purchase_kind]+=Number(x.amount_ngn)||0;
    }
    return json({
      period:"last_30_days",
      generatedAt:end.toISOString(),
      summary:report,
      recent:items||[],
      // Category totals from latest 50 transactions are not full period totals.
      recent50RevenueByCategoryNgn:totals,
      coverageNote:"Category subtotals cover up to the latest 50 payments only.",
    });
  } catch(error) {
    console.error("paystack-finance:",String(error).slice(0,140));
    return json({error:"FINANCE_REPORT_UNAVAILABLE"},503);
  }
});
