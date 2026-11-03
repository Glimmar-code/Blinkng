import { currentUser, json, preflight, reconcile } from "../_shared/blink_paystack.ts";
Deno.serve(async req=>{
  if(req.method==="OPTIONS") return preflight();
  if(req.method!=="POST") return json({error:"METHOD_NOT_ALLOWED"},405);
  const user=await currentUser(req);
  if(!user) return json({error:"SIGN_IN_REQUIRED"},401);
  try {
    const body=await req.json();
    if(typeof body.reference!=="string") return json({error:"REFERENCE_REQUIRED"},400);
    return json(await reconcile(body.reference,user.id));
  } catch(error) {
    const detail=String(error);
    if(detail.includes("ORDER_NOT_FOUND") || detail.includes("REFERENCE_INVALID"))
      return json({error:"PAYMENT_NOT_FOUND"},404);
    console.error("paystack-confirm:",detail.slice(0,160));
    return json({error:"PAYMENT_STATUS_UNAVAILABLE"},503);
  }
});
