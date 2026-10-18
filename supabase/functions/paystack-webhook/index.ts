import {reconcile,reply,signedWebhook} from "../_shared/blink_paystack.ts";

Deno.serve(async(req:Request)=>{
 if(req.method!=="POST") return reply(405,{error:"METHOD_NOT_ALLOWED"});
 try {
   const raw=await req.text();
   if(raw.length>250_000) return reply(413,{error:"PAYLOAD_TOO_LARGE"});
   if(!await signedWebhook(req,raw)) return reply(401,{error:"INVALID_WEBHOOK_SIGNATURE"});
   const event=JSON.parse(raw);
   if(event?.event!=="charge.success") return reply(200,{received:true});
   const reference=event?.data?.reference;
   if(typeof reference!=="string" || !/^BLINK-PS-[0-9a-f]{32}$/.test(reference)){
     return reply(200,{received:true,ignored:true});
   }
   // Re-fetch transaction from Paystack; the webhook body alone is never proof of payment.
   const result=await reconcile(reference);
   return reply(200,{received:true,status:result.status});
 } catch(e) {
   const message=String(e);
   if(message.includes("ORDER_NOT_FOUND")) return reply(200,{received:true,ignored:true});
   console.error("paystack-webhook:",message.slice(0,120));
   return reply(503,{error:"WEBHOOK_RETRY_REQUIRED"});
 }
});
