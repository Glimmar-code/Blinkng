import { json, preflight, reconcile, serviceClient, validSignature } from "../_shared/blink_paystack.ts";

const reviewedEvents = new Set([
  "refund.pending","refund.processing","refund.failed","refund.processed",
  "charge.dispute.create","charge.dispute.remind","charge.dispute.resolve",
]);
Deno.serve(async req=>{
  if(req.method==="OPTIONS") return preflight();
  if(req.method!=="POST") return json({error:"METHOD_NOT_ALLOWED"},405);
  try {
    const raw=await req.text();
    if(raw.length>200000) return json({error:"BODY_TOO_LARGE"},413);
    // An untrusted callback can never create a purchase, grant an entitlement
    // or add a reversal record without the merchant's HMAC-SHA512 signature.
    if(!await validSignature(req,raw)) return json({error:"INVALID_SIGNATURE"},401);
    const event=JSON.parse(raw);
    if(event.event==="charge.success"){
      const reference=event.data?.reference;
      if(typeof reference!=="string" || !/^blink-ps-[0-9a-f]{32}$/.test(reference))
        return json({received:true});
      const result=await reconcile(reference);
      return json({received:true,status:result.status});
    }
    if(!reviewedEvents.has(event.event)) return json({received:true});
    const data=event.data||{};
    const reference=data.transaction_reference ||
      (typeof data.transaction?.reference==="string" ? data.transaction.reference : null);
    if(typeof reference!=="string" || !/^blink-ps-[0-9a-f]{32}$/.test(reference))
      return json({received:true});
    // Do not cross-post test-mode disputes into the live ledger or vice versa.
    const domain=Deno.env.get("PAYSTACK_EXPECTED_DOMAIN")||"test";
    if(data.domain!==domain) return json({error:"PROVIDER_MODE_MISMATCH"},409);
    const amount=Number(data.amount);
    if(!Number.isSafeInteger(amount)||amount<=0 || data.currency!=="NGN")
      return json({error:"INVALID_PROVIDER_AMOUNT"},400);
    const eventId=data.refund_reference??data.id??data.dispute_reference??"";
    const eventKey=`${event.event}:${reference}:${String(eventId).slice(0,90)}`;
    const {error}=await serviceClient().rpc("record_blink_paystack_provider_event",{
      p_reference:reference,
      p_event_type:event.event,
      p_event_key:eventKey,
      p_amount_kobo:amount,
      p_currency:"NGN",
    });
    if(error) throw new Error("PROVIDER_EVENT_LEDGER_FAILED");
    return json({received:true});
  } catch(error) {
    console.error("paystack-webhook:",String(error).slice(0,160));
    return json({error:"WEBHOOK_RETRY_REQUIRED"},503);
  }
});
