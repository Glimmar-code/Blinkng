import { createClient } from "npm:@supabase/supabase-js@2";
import { cachedFcmAccessToken } from "../_shared/fcm-auth.mjs";
const cors={"access-control-allow-origin":"*","access-control-allow-headers":"authorization,apikey,content-type","access-control-allow-methods":"POST,OPTIONS"};
const reply=(b:unknown,s=200)=>new Response(JSON.stringify(b),{status:s,headers:{...cors,"content-type":"application/json"}});
async function sendToken(project:string,token:string,data:Record<string,string>,access:string){return fetch("https://fcm.googleapis.com/v1/projects/"+project+"/messages:send",{method:"POST",headers:{authorization:"Bearer "+access,"content-type":"application/json"},body:JSON.stringify({message:{token,data}})});}
Deno.serve(async(req)=>{
 if(req.method==="OPTIONS")return new Response("ok",{headers:cors});if(req.method!=="POST")return reply({error:"method_not_allowed"},405);
 try{
  const url=Deno.env.get("SUPABASE_URL")!,anon=Deno.env.get("SUPABASE_ANON_KEY")!,service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,bearer=req.headers.get("authorization");if(!bearer)return reply({error:"auth_required"},401);
  const userClient=createClient(url,anon,{global:{headers:{authorization:bearer}}}),{data:user,error:userErr}=await userClient.auth.getUser();if(userErr||!user.user)return reply({error:"invalid_session"},401);
  const {data:admin}=await userClient.rpc("broadcast_admin_check");if(admin!==true)return reply({error:"admin_required"},403);
  const {data:cap}=await userClient.rpc("broadcast_push_cap_reserve",{p_count:1});if(cap!==true)return reply({error:"push_hourly_cap"},429);
  const payload=await req.json().catch(()=>({})),serviceClient=createClient(url,service);let b:any=null;
  if(payload.broadcast_id){const r=await serviceClient.from("broadcasts").select("id,title,body,deep_link,image_url,cta_label").eq("id",payload.broadcast_id).maybeSingle();if(r.error||!r.data)return reply({error:"broadcast_not_found"},404);b=r.data;}
  const {data:tokens,error}=await serviceClient.from("device_tokens").select("id,token").eq("user_id",user.user.id).is("disabled_at",null);if(error)return reply({error:"device_lookup_failed"},500);if(!tokens?.length)return reply({error:"no_registered_devices"},404);
  const raw=Deno.env.get("FCM_SERVICE_ACCOUNT_JSON");if(!raw)return reply({error:"fcm_secret_missing"},503);const account=JSON.parse(raw),access=await cachedFcmAccessToken(account);
  const title=String(payload.title??b?.title??"BLINK Broadcast test").slice(0,60),body=String(payload.body??b?.body??"Test message").slice(0,240);
  const data={type:"broadcast",target_type:"broadcast",recipient_id:user.user.id,broadcast_id:String(b?.id??"test"),title,body,deep_link:String(payload.deep_link??b?.deep_link??""),image_url:String(b?.image_url??""),cta_label:String(b?.cta_label??""),test:"true"};
  let sent=0,invalid=0;for(const t of tokens){const res=await sendToken(account.project_id,t.token,data,access);if(res.ok){sent++;continue;}const e=await res.json().catch(()=>({}));if((e?.error?.details??[]).some((x:any)=>x?.errorCode==="UNREGISTERED")){invalid++;await serviceClient.from("device_tokens").update({disabled_at:new Date().toISOString()}).eq("id",t.id);}}
  return reply({ok:sent>0,sent,invalid_tokens:invalid,recipient:user.user.id,scope:"caller_devices_only"});
 }catch(e){return reply({error:"send_test_failed",message:String(e).slice(0,250)},500);}
});
