// Server-side Brevo payment alerts. No customer-controlled recipients,
// no SMTP credentials in Android, and no background sends without authorization.
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const reply=(body:unknown,status=200)=>new Response(JSON.stringify(body),{
 status,headers:{"Content-Type":"application/json","Cache-Control":"no-store"}
});
async function matchesSecret(given:string,want:string){
 if(want.length<32 || given.length<32)return false;
 const d=async (x:string)=>new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(x)));
 const [a,b]=await Promise.all([d(given),d(want)]);
 return a.reduce((diff,v,i)=>diff|(v^b[i]),0)===0;
}
function escaped(value:unknown){
 return String(value??"").replaceAll("&","&amp;").replaceAll("<","&lt;")
   .replaceAll(">","&gt;").replaceAll('"',"&quot;").slice(0,180);
}
const emails:Record<string,{subject:string;title:string;message:string}>={
 payment_success:{
  subject:"BLINK payment receipt",title:"Payment confirmed",
  message:"Your BLINK purchase is confirmed and your account has been updated.",
 },
 renewal_reminder:{
  subject:"BLINK subscription renewal approaching",title:"Renewal reminder",
  message:"Your BLINK verification subscription will renew soon. Open BLINK to review or cancel future charges.",
 },
 renewal_failed:{
  subject:"BLINK automatic renewal failed",title:"Payment not completed",
  message:"Your recent renewal payment did not complete. No unpaid benefit has been granted. Open BLINK to check your subscription.",
 },
 renewal_cancelled:{
  subject:"BLINK renewal stopped",title:"Future renewal cancelled",
  message:"Your subscription is set to stop renewing. Your existing paid access remains valid until its expiration.",
 }
};
Deno.serve(async req=>{
 if(req.method!=="POST")return reply({error:"METHOD_NOT_ALLOWED"},405);
 const expected=Deno.env.get("BLINK_EMAIL_WORKER_SECRET")||"";
 const supplied=req.headers.get("x-blink-email-worker-secret")||"";
 if(!await matchesSecret(supplied,expected))return reply({error:"UNAUTHORIZED"},401);
 if(Deno.env.get("BLINK_PAYMENT_EMAIL_ENABLED")!=="true")
   return reply({error:"DELIVERY_DISABLED"},503);
 const url=Deno.env.get("SUPABASE_URL");
 const secret=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
 const apiKey=Deno.env.get("BREVO_API_KEY");
 const from=Deno.env.get("BLINK_EMAIL_SENDER_ADDRESS");
 if(!url||!secret||!apiKey||!from || !from.includes("@"))
   return reply({error:"SERVER_CONFIG_INCOMPLETE"},503);
 const db=createClient(url,secret,{auth:{persistSession:false,autoRefreshToken:false}});
 const {data, error}=await db.rpc("claim_blink_paystack_payment_notices",{p_batch:10});
 if(error)return reply({error:"QUEUE_UNAVAILABLE"},503);
 let sent=0,failed=0,skipped=0;
 for(const row of Array.isArray(data)?data:[]){
   const message=emails[row.kind];
   let state="failed",reason="unknown";
   if(message){
    try{
      const {data:profile,error:userError}=await db.auth.admin.getUserById(row.user_id);
      const user=profile?.user;
      if(userError || !user?.email || !user.email_confirmed_at) {
        state="failed";reason="RECIPIENT_NOT_VERIFIED";
      }else{
        const amount=Number(row.amount_ngn||0);
        const amountLine=Number.isFinite(amount) && amount>0 ?
          `Amount: NGN ${Math.round(amount).toLocaleString("en-NG")}`:"";
        const reference=row.reference? `Reference: ${escaped(row.reference)}`:"";
        const simpleText=[message.title,message.message,
          amountLine,reference,"BLINK Support: blinksupport.ng@gmail.com"].filter(Boolean).join("\n\n");
        const body=`<!doctype html><html lang="en"><body style="margin:0;padding:26px;background:#000;color:#fff;font-family:Arial,sans-serif">
          <div style="max-width:580px;margin:auto;padding:24px;border:1px solid #444;border-radius:18px">
          <b>BLINK</b><h1>${escaped(message.title)}</h1><p>${escaped(message.message)}</p>
          <p>${escaped(amountLine)}</p><p>${reference}</p>
          <p style="color:#aaa;font-size:12px">Never share passwords or bank PINs. For help contact blinksupport.ng@gmail.com.</p></div>
          </body></html>`;
        const result=await fetch("https://api.brevo.com/v3/smtp/email",{
          method:"POST",headers:{"api-key":apiKey,"Content-Type":"application/json"},
          body:JSON.stringify({sender:{email:from,name:"BLINK"},to:[{email:user.email}],
            subject:message.subject,htmlContent:body,textContent:simpleText,
            tags:["blink_payments"]}),
          signal:AbortSignal.timeout(14000),
        });
        state=result.ok?"sent":
          (result.status===429 || result.status>=500) && row.attempts<3?"pending":"failed";
        reason=result.ok?"":`BREVO_HTTP_${result.status}`;
      }
    }catch(_err){
      // Ambiguous network response: do not repeat and risk multiple receipts.
      state="failed";reason="AMBIGUOUS_PROVIDER_RESPONSE";
    }
   }
   const {error:writeError}=await db.rpc("complete_blink_paystack_payment_notice",{
     p_id:row.id,p_status:state,p_error:reason,
   });
   if(writeError)failed++; else if(state==="sent")sent++;
   else if(state==="pending")skipped++; else failed++;
 }
 return reply({ok:true,sent,failed,skipped});
});