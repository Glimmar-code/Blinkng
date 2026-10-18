import {cors,reconcile,reply,userFrom} from "../_shared/blink_paystack.ts";

Deno.serve(async(req:Request)=>{
 if(req.method==="OPTIONS") return new Response(null,{status:204,headers:cors});
 if(req.method!=="POST") return reply(405,{error:"METHOD_NOT_ALLOWED"});
 const user=await userFrom(req);
 if(!user?.id) return reply(401,{error:"AUTHENTICATION_REQUIRED"});
 const body=await req.json().catch(()=>({}));
 if(typeof body.reference!=="string") return reply(400,{error:"REFERENCE_REQUIRED"});
 try {
   return reply(200,await reconcile(body.reference,user.id));
 } catch(e) {
   const error=String(e);
   if(error.includes("ORDER_NOT_FOUND")||error.includes("INVALID_REFERENCE")){
     return reply(404,{error:"ORDER_NOT_FOUND"});
   }
   console.error("paystack-verify:",error.slice(0,120));
   return reply(503,{error:"PAYMENT_VERIFY_UNAVAILABLE"});
 }
});
