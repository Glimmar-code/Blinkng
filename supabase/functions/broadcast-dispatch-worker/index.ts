import { createClient } from "npm:@supabase/supabase-js@2";
import { cachedFcmAccessToken } from "../_shared/fcm-auth.mjs";
const cors={"access-control-allow-origin":"*","access-control-allow-headers":"content-type,x-broadcast-worker-secret","access-control-allow-methods":"POST,OPTIONS"};
const reply=(b:unknown,s=200)=>new Response(JSON.stringify(b),{status:s,headers:{...cors,"content-type":"application/json"}});
const sleep=(ms:number)=>new Promise(r=>setTimeout(r,ms));
const equalSecret=(a:string,b:string)=>{if(a.length!==b.length)return false;let x=0;for(let i=0;i<a.length;i++)x|=a.charCodeAt(i)^b.charCodeAt(i);return x===0;};
const esc=(s:string)=>s.replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]!));
function localMin(d:Date,tz:string){const p=new Intl.DateTimeFormat("en-GB",{timeZone:tz,hour:"2-digit",minute:"2-digit",hourCycle:"h23"}).formatToParts(d);return Number(p.find(x=>x.type==="hour")?.value)*60+Number(p.find(x=>x.type==="minute")?.value);}
function quiet(s:string|null,e:string|null,tz:string,d=new Date()){if(!s||!e||s===e)return false;const a=s.slice(0,5).split(":").map(Number),b=e.slice(0,5).split(":").map(Number),start=a[0]*60+a[1],end=b[0]*60+b[1],m=localMin(d,tz);return start<end?m>=start&&m<end:m>=start||m<end;}
function quietEnd(s:string|null,e:string|null,tz:string,now=new Date()){if(!s||!e||s===e)return null;for(let i=1;i<=36*60;i++){const d=new Date(now.getTime()+i*60000);if(!quiet(s,e,tz,d))return d.toISOString();}return new Date(now.getTime()+8*3600000).toISOString();}
function semver(a:string,b:string){const x=a.split(/[+-]/)[0].split(".").map(Number),y=b.split(/[+-]/)[0].split(".").map(Number);for(let i=0;i<3;i++){if((x[i]??0)!==(y[i]??0))return (x[i]??0)-(y[i]??0);}return 0;}
function versionOk(v:string,a:any){return !(a?.min_app_version&&semver(v,a.min_app_version)<0)&&!(a?.max_app_version&&semver(v,a.max_app_version)>0);}
function token(){return [...crypto.getRandomValues(new Uint8Array(32))].map(x=>x.toString(16).padStart(2,"0")).join("");}
async function hash(t:string){return [...new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(t)))].map(x=>x.toString(16).padStart(2,"0")).join("");}
async function sendFcm(project:string,t:string,data:Record<string,string>,access:string){const r=await fetch("https://fcm.googleapis.com/v1/projects/"+project+"/messages:send",{method:"POST",headers:{authorization:"Bearer "+access,"content-type":"application/json"},body:JSON.stringify({message:{token:t,data}})});const b=await r.json().catch(()=>({}));return {ok:r.ok,status:r.status,body:b,unregistered:(b?.error?.details??[]).some((x:any)=>x?.errorCode==="UNREGISTERED")};}
async function sendRetry(project:string,t:string,data:Record<string,string>,access:string){let last:any;for(let i=0;i<3;i++){try{last=await sendFcm(project,t,data,access);if(last.ok||last.unregistered||last.status<500)return last;}catch(e){last={ok:false,status:0,body:{error:{message:String(e)}}};}await sleep(200*(i+1));}return last;}
async function skip(admin:any,b:any,u:string,ch:string,reason:string){await admin.from("broadcast_deliveries").upsert({broadcast_id:b.id,user_id:u,channel:ch,status:"skipped",attempts:0,last_error:reason,updated_at:new Date().toISOString()},{onConflict:"broadcast_id,user_id,channel"});}
async function processChannel(admin:any,b:any,ch:"push"|"email",account:any,brevo:string,sender:string,baseUrl:string){
 if(!b.channels?.[ch])return {sent:0,skipped:0,deferred:0,failed:0,earliest:null,more:false};
 const cap=Math.max(1,Math.min(20,Number(b.channels?.frequency_cap??3))),respect=b.channels?.quiet_hours_respect!==false,access=ch==="push"?await cachedFcmAccessToken(account):null;
 let after:string|null=null,sent=0,skipped=0,deferred=0,failed=0,earliest:string|null=null,more=false;
 for(let pg=0;pg<5;pg++){
  const {data:users,error}=await admin.rpc("broadcast_dispatch_audience",{p_broadcast_id:b.id,p_channel:ch,p_after_user_id:after,p_limit:200});if(error)throw new Error("audience:"+error.message);if(!users?.length)break;
  for(const u of users){
   const tz=u.timezone||"Africa/Lagos";
   if(respect&&quiet(u.quiet_start,u.quiet_end,tz)){const until=quietEnd(u.quiet_start,u.quiet_end,tz)||new Date(Date.now()+8*3600000).toISOString();await admin.rpc("defer_broadcast_delivery",{p_broadcast_id:b.id,p_user_id:u.user_id,p_channel:ch,p_until:until});deferred++;if(!earliest||until<earliest)earliest=until;continue;}
   const {data:ok}=await admin.rpc("broadcast_user_frequency_ok",{p_user_id:u.user_id,p_channel:ch,p_cap:cap,p_window_days:7});if(ok===false){await skip(admin,b,u.user_id,ch,"frequency_cap");skipped++;continue;}
   const {data:id,error:claimError}=await admin.rpc("claim_broadcast_delivery",{p_broadcast_id:b.id,p_user_id:u.user_id,p_channel:ch});if(claimError)throw new Error("claim:"+claimError.message);if(!id)continue;
   if(ch==="push"){
    const {data:tokens,error:tokenError}=await admin.from("device_tokens").select("id,token,app_version").eq("user_id",u.user_id).is("disabled_at",null);if(tokenError)throw new Error("tokens:"+tokenError.message);
    const eligible=(tokens??[]).filter((x:any)=>versionOk(String(x.app_version||"0.0.0"),b.audience));if(!eligible.length){await skip(admin,b,u.user_id,ch,"no_eligible_device_tokens");skipped++;continue;}
    const data={type:"broadcast",target_type:"broadcast",recipient_id:String(u.user_id),broadcast_id:String(b.id),title:String(b.title||""),body:String(b.body||""),deep_link:String(b.deep_link||""),image_url:String(b.image_url||""),cta_label:String(b.cta_label||"")};
    let success=0,invalid=0,errors:string[]=[];for(const t of eligible){const r=await sendRetry(account.project_id,t.token,data,access!);if(r.ok){success++;continue;}if(r.unregistered){invalid++;await admin.from("device_tokens").update({disabled_at:new Date().toISOString()}).eq("id",t.id);}else errors.push(String(r.body?.error?.message??r.status));}
    if(success){await admin.rpc("complete_broadcast_delivery",{p_id:id,p_success:true,p_error:null});sent++;}
    else if(invalid===eligible.length){await skip(admin,b,u.user_id,ch,"all_tokens_unregistered");skipped++;}
    else{await admin.rpc("complete_broadcast_delivery",{p_id:id,p_success:false,p_error:errors.slice(0,3).join("; ")});failed++;}
   }else{
    if(!u.email){await skip(admin,b,u.user_id,ch,"email_missing");skipped++;continue;}
    const raw=token(),tokenHash=await hash(raw),expiry=new Date(Date.now()+365*86400000).toISOString();
    const {error:ins}=await admin.from("broadcast_email_unsubscribe_tokens").insert({user_id:u.user_id,token_hash:tokenHash,expires_at:expiry});if(ins){await admin.rpc("complete_broadcast_delivery",{p_id:id,p_success:false,p_error:"unsubscribe_token_failed"});failed++;continue;}
    const unsub=baseUrl+"/functions/v1/broadcast-unsubscribe?token="+raw,link="https://blink.com.ng/",subject=String(b.title||"BLINK update");
    const html="<main style='font-family:Arial;max-width:620px;margin:auto'><h2>"+esc(subject)+"</h2><p>"+esc(String(b.body||"")).replace(/\\n/g,"<br>")+"</p><p><a href='"+link+"'>"+esc(String(b.cta_label||"Open BLINK"))+"</a></p><small><a href='"+unsub+"'>Unsubscribe</a></small></main>";
    const textBody=subject+"\\n\\n"+String(b.body||"")+"\\n\\n"+link+"\\nUnsubscribe: "+unsub;
    const res=await fetch("https://api.brevo.com/v3/smtp/email",{method:"POST",headers:{"api-key":brevo,"content-type":"application/json"},body:JSON.stringify({sender:{name:"BLINK",email:sender},to:[{email:u.email}],subject,htmlContent:html,textContent:textBody,headers:{"List-Unsubscribe":"<"+unsub+">","List-Unsubscribe-Post":"List-Unsubscribe=One-Click"}})});
    if(res.ok){await admin.rpc("complete_broadcast_delivery",{p_id:id,p_success:true,p_error:null});sent++;}
    else{const detail=await res.text().catch(()=>"" );await admin.rpc("complete_broadcast_delivery",{p_id:id,p_success:false,p_error:"Brevo "+res.status+": "+detail.slice(0,250)});failed++;}
   }
  }
  after=users[users.length-1].user_id;if(users.length<200)break;if(pg===4){more=true;earliest=earliest??new Date(Date.now()+60000).toISOString();}
 }
 return {sent,skipped,deferred,failed,earliest,more};
}
async function processQueue(admin:any,row:any,account:any,brevo:string,sender:string,url:string){
 const {data:b}=await admin.from("broadcasts").select("*").eq("id",row.broadcast_id).maybeSingle();if(!b||b.status!=="live"||b.deleted_at){await admin.rpc("complete_broadcast_dispatch",{p_id:row.id,p_success:false,p_error:"broadcast_not_live"});return {id:row.id,error:"broadcast_not_live"};}
 if(row.channel==="push"&&!account)throw new Error("FCM_SERVICE_ACCOUNT_JSON missing");if(row.channel==="email"&&(!brevo||!sender))throw new Error("Brevo secret/sender missing");
 const r=await processChannel(admin,b,row.channel,account,brevo,sender,url);if(r.deferred||r.more)await admin.rpc("requeue_broadcast_dispatch",{p_id:row.id,p_until:r.earliest});else await admin.rpc("complete_broadcast_dispatch",{p_id:row.id,p_success:true,p_error:null});return {id:row.id,broadcast_id:b.id,channel:row.channel,...r};
}
Deno.serve(async(req)=>{
 if(req.method==="OPTIONS")return new Response("ok",{headers:cors});if(req.method!=="POST")return reply({error:"method_not_allowed"},405);
 try{
  const expected=Deno.env.get("BROADCAST_WORKER_SECRET")??"",provided=req.headers.get("x-broadcast-worker-secret")??"";if(expected.length<32||!equalSecret(expected,provided))return reply({error:"unauthorized"},401);
  const url=Deno.env.get("SUPABASE_URL")!,service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,admin=createClient(url,service),payload=await req.json().catch(()=>({}));
  const raw=Deno.env.get("FCM_SERVICE_ACCOUNT_JSON"),account=raw?JSON.parse(raw):null,brevo=Deno.env.get("BREVO_API_KEY")??"",sender=Deno.env.get("BLINK_EMAIL_SENDER_ADDRESS")??Deno.env.get("BROADCAST_EMAIL_FROM")??"";
  if(payload.broadcast_id){const ch=payload.channel==="email"?"email":"push",{data:b}=await admin.from("broadcasts").select("*").eq("id",payload.broadcast_id).maybeSingle();if(!b||b.status!=="live")return reply({error:"broadcast_not_live"},409);if(ch==="push"&&!account)return reply({error:"fcm_secret_missing"},503);if(ch==="email"&&(!brevo||!sender))return reply({error:"email_secret_missing"},503);
   const r=await processChannel(admin,b,ch,account,brevo,sender,url),{data:q}=await admin.from("broadcast_dispatch_queue").select("id").eq("broadcast_id",b.id).eq("channel",ch).maybeSingle();if(q?.id){if(r.deferred||r.more)await admin.rpc("requeue_broadcast_dispatch",{p_id:q.id,p_until:r.earliest});else await admin.rpc("complete_broadcast_dispatch",{p_id:q.id,p_success:true,p_error:null});}return reply({ok:true,broadcast_id:b.id,channel:ch,...r});}
  const {data:rows,error}=await admin.rpc("claim_broadcast_dispatch_batch",{p_limit:5});if(error)return reply({error:"queue_claim_failed"},500);const results=[];
  for(const row of rows??[]){try{results.push(await processQueue(admin,row,account,brevo,sender,url));}catch(e){await admin.rpc("complete_broadcast_dispatch",{p_id:row.id,p_success:false,p_error:String(e).slice(0,500)});results.push({id:row.id,error:String(e).slice(0,250)});}}
  return reply({ok:true,processed:results.length,results});
 }catch(e){return reply({error:"worker_failed",message:String(e).slice(0,400)},500);}
});
