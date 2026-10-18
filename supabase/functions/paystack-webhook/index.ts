import { json, preflight, reconcile, validSignature } from "../_shared/blink_paystack.ts";
Deno.serve(async req=>{
  if(req.method==="OPTIONS") return preflight();
  if(req.method!=="POST") return json({error:"METHOD_NOT_ALLOWED"},405);
  try {
    const raw=await req.text();
    if(raw.length>200000) return json({error:"BODY_TOO_LARGE"},413);
    if(!await validSignature(req,raw)) return json({error:"INVALID_SIGNATURE"},401);
    const event=JSON.parse(raw);
    if(event.event!=="charge.success") return json({received:true});
    const reference=event.data?.reference;
    if(typeof reference!=="string" || !/^blink-ps-[0-9a-f]{32}$/.test(reference))
      return json({received:true});
    const result=await reconcile(reference);
    return json({received:true,status:result.status});
  } catch(error) {
    console.error("paystack-webhook:",String(error).slice(0,160));
    return json({error:"WEBHOOK_RETRY_REQUIRED"},503);
  }
});
