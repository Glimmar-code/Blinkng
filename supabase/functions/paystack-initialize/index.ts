import { createClient } from "npm:@supabase/supabase-js@2";
import { admin, cors, paystack, reply, secret, userFrom } from "../_shared/blink_paystack.ts";

Deno.serve(async (req:Request) => {
  if (req.method==="OPTIONS") return new Response(null,{status:204,headers:cors});
  if (req.method!=="POST") return reply(405,{error:"METHOD_NOT_ALLOWED"});
  const user=await userFrom(req);
  if (!user?.id || !user.email) return reply(401,{error:"AUTHENTICATION_REQUIRED"});
  // Default disabled in every environment until merchant ownership and test checkout are verified.
  if (Deno.env.get("BLINK_PAYSTACK_CHECKOUT_ENABLED")!=="true") {
    return reply(503,{error:"CASH_CHECKOUT_DISABLED"});
  }
  try {
    secret(); // Fail BEFORE creating any order when merchant credentials are missing.
    const body=await req.json().catch(()=>({}));
    const kind=String(body.kind||"").toUpperCase();
    const packId=typeof body.pack_id==="string" ? body.pack_id : null;
    if (!["BLUE_VERIFICATION","GOLD_VERIFICATION","COIN_PACK"].includes(kind)) {
      return reply(400,{error:"PRODUCT_NOT_SUPPORTED"});
    }
    const caller=createClient(Deno.env.get("SUPABASE_URL")!,Deno.env.get("SUPABASE_ANON_KEY")!,{
      global:{headers:{Authorization:req.headers.get("Authorization")!}},
      auth:{autoRefreshToken:false,persistSession:false},
    });
    const {data:order,error} = await caller.rpc("create_blink_paystack_order",{
      p_kind:kind,p_pack_id:packId,
    });
    if(error || !order?.reference) {
      return reply(400,{error:error?.message||"ORDER_UNAVAILABLE"});
    }
    const data=await paystack("transaction/initialize",{
      method:"POST",
      body:JSON.stringify({
        email:user.email,
        amount:String(Number(order.amount_ngn)*100), // Paystack expects kobo, not naira.
        currency:"NGN",
        reference:order.reference,
        callback_url:"https://blink.com.ng/?blink_payment_return=paystack",
        metadata:{blink_order_id:order.order_id,blink_kind:kind},
      }),
    });
    // Allow only Paystack's hosted HTTPS payment origin, never arbitrary URLs.
    const url=new URL(data?.authorization_url);
    if(url.protocol!=="https:" || url.hostname!=="checkout.paystack.com" ||
      url.username || url.password || url.port) throw new Error("UNTRUSTED_CHECKOUT_URL");
    if(data.reference!==order.reference) throw new Error("CHECKOUT_REFERENCE_MISMATCH");
    return reply(200,{
      checkoutUrl:url.toString(),orderId:order.order_id,
      reference:order.reference,kind,amountNgn:order.amount_ngn,currency:"NGN",
    });
  } catch(e) {
    console.error("paystack-init:",String(e).slice(0,120));
    return reply(503,{error:"CHECKOUT_UNAVAILABLE"});
  }
});
