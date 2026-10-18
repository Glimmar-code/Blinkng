import { isAuthenticPaystackWebhook, json, reconcilePaystackOrder, responseHeaders } from "../_shared/blink_paystack.ts";

Deno.serve(async(req: Request)=>{
  if (req.method==="OPTIONS") return new Response(null,{headers:responseHeaders});
  if (req.method!=="POST") return json({error:"METHOD_NOT_ALLOWED"},405);
  const raw = await req.text();
  if (raw.length>65536) return json({error:"EVENT_TOO_LARGE"},413);
  const signature=req.headers.get("x-paystack-signature") || "";
  try {
    if (!(await isAuthenticPaystackWebhook(raw,signature))) {
      return json({error:"INVALID_SIGNATURE"},401);
    }
    const event=JSON.parse(raw);
    if (event.event!=="charge.success") return json({received:true});
    const ref=event?.data?.reference;
    if (typeof ref!=="string" || !/^blink-ps-[0-9a-f]{32}$/.test(ref)) {
      return json({received:true});
    }
    // Authenticated webhook event is still not payment proof: recheck with Paystack.
    const outcome=await reconcilePaystackOrder(ref);
    return json({received:true,status:outcome.status});
  } catch(error) {
    console.error("paystack-webhook",String(error).slice(0,140));
    // Return non-200 only when reconciliation needs a provider retry.
    return json({error:"RETRY_RECONCILIATION"},503);
  }
});
