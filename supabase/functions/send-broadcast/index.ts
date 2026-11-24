import { createClient } from "npm:@supabase/supabase-js@2";
const cors={"access-control-allow-origin":"*","access-control-allow-headers":"authorization,apikey,content-type","access-control-allow-methods":"POST,OPTIONS"};
const reply=(b:unknown,s=200)=>new Response(JSON.stringify(b),{status:s,headers:{...cors,"content-type":"application/json"}});
Deno.serve(async(req)=>{
 if(req.method==="OPTIONS")return new Response("ok",{headers:cors});if(req.method!=="POST")return reply({error:"method_not_allowed"},405);
 try{
  const url=Deno.env.get("SUPABASE_URL")!,anon=Deno.env.get("SUPABASE_ANON_KEY")!,service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,bearer=req.headers.get("authorization");if(!bearer)return reply({error:"auth_required"},401);
  const userClient=createClient(url,anon,{global:{headers:{authorization:bearer}}}),{data:user,error:userErr}=await userClient.auth.getUser();if(userErr||!user.user)return reply({error:"invalid_session"},401);
  const {data:admin}=await userClient.rpc("broadcast_admin_check");if(admin!==true)return reply({error:"admin_required"},403);
  const {broadcast_id}=await req.json();if(!broadcast_id)return reply({error:"broadcast_id_required"},400);
  const serviceClient=createClient(url,service),{data:b,error}=await serviceClient.from("broadcasts").select("id,status,channels,deleted_at").eq("id",broadcast_id).maybeSingle();
  if(error||!b)return reply({error:"broadcast_not_found"},404);if(b.status!=="live"||b.deleted_at)return reply({error:"broadcast_not_live"},409);if(!b.channels?.push)return reply({error:"push_channel_disabled"},400);
  let after=null,count=0;for(let page=0;page<30;page++){const {data:rows,error:audErr}=await serviceClient.rpc("broadcast_dispatch_audience",{p_broadcast_id:broadcast_id,p_channel:"push",p_after_user_id:after,p_limit:500});if(audErr)return reply({error:"audience_failed"},500);if(!rows?.length)break;count+=rows.length;after=rows[rows.length-1].user_id;if(count>10000)break;if(rows.length<500)break;}
  const {data:reserved,error:capErr}=await userClient.rpc("broadcast_push_cap_reserve",{p_count:count});if(capErr||reserved!==true)return reply({error:"push_hourly_cap",audience_count:count},429);
  const secret=Deno.env.get("BROADCAST_WORKER_SECRET");if(!secret)return reply({error:"worker_secret_missing"},503);
  const worker=await fetch(url+"/functions/v1/broadcast-dispatch-worker",{method:"POST",headers:{"content-type":"application/json","x-broadcast-worker-secret":secret},body:JSON.stringify({broadcast_id,channel:"push",source:"admin_manual"})});
  const result=await worker.json().catch(()=>({}));return reply({ok:worker.ok,audience_count:count,result},worker.ok?200:worker.status);
 }catch(e){return reply({error:"send_broadcast_failed",message:String(e).slice(0,250)},500);}
});
