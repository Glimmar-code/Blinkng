import { createClient } from "npm:@supabase/supabase-js@2";
Deno.serve(async(req)=>{
 if(req.method!=="GET"&&req.method!=="POST")return new Response("Method not allowed",{status:405});
 const token=new URL(req.url).searchParams.get("token")??"",client=createClient(Deno.env.get("SUPABASE_URL")!,Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
 const {data,error}=await client.rpc("broadcast_email_unsubscribe",{p_token:token}),ok=!error&&data?.ok===true;
 const html=ok?"<h1>You're unsubscribed</h1><p>You will no longer receive BLINK Broadcast emails.</p>":"<h1>Unsubscribe link invalid</h1><p>Please contact BLINK support if you need help.</p>";
 return new Response(html,{status:ok?200:400,headers:{"content-type":"text/html; charset=utf-8","cache-control":"no-store","x-content-type-options":"nosniff"}});
});
