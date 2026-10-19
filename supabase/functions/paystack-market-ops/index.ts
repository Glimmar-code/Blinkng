import { createClient } from "npm:@supabase/supabase-js@2";
import { currentUser, json, preflight, paystackApi, serviceClient } from "../_shared/blink_paystack.ts";

const uuid = (v:unknown) => typeof v==="string" &&
 /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v);
async function ownerAuthorized(req: Request): Promise<boolean> {
 const user=await currentUser(req);
 if(!user) return false;
 const jwt=req.headers.get("authorization")||"";
 const caller=createClient(Deno.env.get("SUPABASE_URL")!,Deno.env.get("SUPABASE_ANON_KEY")!,{
   global:{headers:{Authorization:jwt}},auth:{autoRefreshToken:false,persistSession:false},
 });
 const {data,error}=await caller.rpc("admin_get_capability");
 return !error && data?.is_owner===true;
}
function userRpc(req:Request) {
 return createClient(Deno.env.get("SUPABASE_URL")!,Deno.env.get("SUPABASE_ANON_KEY")!,{
   global:{headers:{Authorization:req.headers.get("authorization")||""}},
   auth:{autoRefreshToken:false,persistSession:false},
 });
}
function safeCustomerError(error:unknown) {
 const s=String(error);
 console.error("paystack-market-operation:",s.slice(0,150));
 return json({error:"REQUEST_COULD_NOT_BE_COMPLETED"},503);
}
async function checkMarketPayment(id:string) {
 const svc=serviceClient();
 const {data,error}=await svc.from("blink_paystack_payments")
  .select("id,reference,purchase_kind,status,amount_ngn,paystack_transaction_id")
  .eq("id",id).single();
 if(error||!data||data.status!=="fulfilled") throw new Error("PAYMENT_NOT_SETTLED");
 return data;
}
Deno.serve(async(req:Request)=>{
 if(req.method==="OPTIONS")return preflight();
 if(req.method!=="POST")return json({error:"METHOD_NOT_ALLOWED"},405);
 const user=await currentUser(req);
 if(!user)return json({error:"SIGN_IN_REQUIRED"},401);
 let body:Record<string,unknown>;
 try{body=await req.json()}catch{return json({error:"INVALID_JSON"},400)}
 const action=String(body.action||"");
 const svc=serviceClient();
 try{
   if(action==="request_refund") {
     if(typeof body.reference!=="string" || !/^blink-ps-[0-9a-f]{32}$/.test(body.reference)
       || typeof body.reason!=="string" || body.reason.trim().length<10 ||
       body.reason.length>1000) return json({error:"INVALID_REFUND_REQUEST"},400);
     const {data,error}=await userRpc(req).rpc("request_blink_paystack_refund",{
        p_reference:body.reference,p_reason:body.reason.trim(),
     });
     if(error)return json({error:error.message},400);
     return json(data);
   }
   if(action==="confirm_delivery") {
     if(typeof body.reference!=="string" ||
       !/^blink-ps-[0-9a-f]{32}$/.test(body.reference))
       return json({error:"INVALID_MARKET_REFERENCE"},400);
     const {data,error}=await userRpc(req).rpc("confirm_blink_paystack_market_delivery",{
       p_reference:body.reference,
     });
     if(error)return json({error:error.message},400);
     return json(data);
   }

   // ALL remaining actions change real financial state and require BLINK owner.
   if(!await ownerAuthorized(req))return json({error:"OWNER_REQUIRED"},403);

   if(action==="review_refund"){
     if(!uuid(body.requestId)||!["approved","rejected"].includes(String(body.decision)))
       return json({error:"INVALID_REVIEW"},400);
     const status=String(body.decision);
     const {data,error}=await svc.from("blink_paystack_refunds")
       .update({status,reviewed_by:user.id,reviewed_at:new Date().toISOString(),
         updated_at:new Date().toISOString()})
       .eq("id",body.requestId).eq("status","requested")
       .select("id,status").maybeSingle();
     if(error||!data)return json({error:"REQUEST_ALREADY_REVIEWED"},409);
     return json({requestId:data.id,status:data.status});
   }
   if(action==="execute_refund"){
     if(Deno.env.get("BLINK_PAYSTACK_REFUNDS_ENABLED")!=="true")
       return json({error:"REFUNDS_DISABLED"},503);
     if(!uuid(body.requestId))return json({error:"INVALID_REQUEST"},400);
     const {data:record,error:rErr}=await svc.from("blink_paystack_refunds")
       .select("id,status,payment_id,amount_kobo").eq("id",body.requestId).single();
     if(rErr||!record||record.status!=="approved")return json({error:"REFUND_NOT_APPROVED"},409);
     const payment=await checkMarketPayment(record.payment_id);
     if(payment.purchase_kind!=="marketplace" && payment.purchase_kind!=="gift")
       return json({error:"REFUND_MANUAL_REVIEW_REQUIRED"},403);
     if(payment.purchase_kind==="marketplace") {
       const {data:payout,error:payoutError}=await svc.from("blink_paystack_seller_payouts")
         .select("status").eq("payment_id",payment.id).maybeSingle();
       if(payoutError||!payout||payout.status!=="blocked")
         return json({error:"PAYOUT_RELEASE_BLOCKS_REFUND"},409);
     }
     const {data:locked,error:lockErr}=await svc.from("blink_paystack_refunds")
       .update({status:"submitted",updated_at:new Date().toISOString()})
       .eq("id",record.id).eq("status","approved")
       .select("id").maybeSingle();
     if(lockErr||!locked)return json({error:"REFUND_ALREADY_SUBMITTED"},409);
     // Network timeout after submission may mean Paystack accepted the refund.
     // Never resubmit blindly; reconcile via provider dashboard/webhook.
     const result=await paystackApi("refund","POST",{
       transaction:String(payment.paystack_transaction_id),
       amount:record.amount_kobo,currency:"NGN",
       customer_note:"BLINK marketplace refund",
       merchant_note:"Approved BLINK refund request "+record.id,
     });
     await svc.from("blink_paystack_refunds").update({
       provider_refund_id:String(result?.id||"")||null,
       status:"processing",updated_at:new Date().toISOString(),
     }).eq("id",record.id).eq("status","submitted");
     return json({status:"processing",requestId:record.id});
   }
   if(action==="register_recipient"){
     if(!uuid(body.sellerId) || typeof body.recipientCode!=="string" ||
         !/^RCP_[A-Za-z0-9]+$/.test(body.recipientCode))
       return json({error:"INVALID_VERIFIED_RECIPIENT"},400);
     // Verify against Paystack so an owner cannot accidentally bind another account.
     const verified=await paystackApi(
       `transferrecipient/${encodeURIComponent(body.recipientCode)}`);
     if(!verified || verified.recipient_code!==body.recipientCode ||
       verified.currency!=="NGN" || verified.active!==true)
       return json({error:"RECIPIENT_NOT_VERIFIED"},400);
     const {error}=await svc.rpc("set_blink_paystack_seller_destination_for_server",{
       p_seller:body.sellerId,p_recipient_code:body.recipientCode,p_owner:user.id,
     });
     if(error)return json({error:"RECIPIENT_APPROVAL_FAILED"},400);
     return json({status:"verified",sellerId:body.sellerId});
   }
   if(action==="approve_payout"){
     if(!uuid(body.payoutId))return json({error:"INVALID_PAYOUT_ID"},400);
     const {data:payout,error:pErr}=await svc.from("blink_paystack_seller_payouts")
       .select("id,payment_id,seller_id,status").eq("id",body.payoutId).single();
     if(pErr||!payout||payout.status!=="blocked")return json({error:"PAYOUT_NOT_ELIGIBLE"},409);
     const {data:market,error:mErr}=await svc.from("blink_paystack_market_orders")
       .select("marketplace_order_id,fulfilment_status,delivered_at")
       .eq("payment_id",payout.payment_id).single();
     if(mErr||market?.fulfilment_status!=="delivered" ||
       !market.delivered_at || Date.now()-Date.parse(market.delivered_at)<72*3600000)
       return json({error:"DELIVERY_HOLD_OR_CONFIRMATION_REQUIRED"},409);
     const {data:disputes,error:dErr}=await svc.from("market_disputes")
       .select("id").eq("order_id",market.marketplace_order_id)
       .in("status",["open","reviewing"]).limit(1);
     if(dErr||disputes?.length)return json({error:"OPEN_DISPUTE_BLOCKS_PAYOUT"},409);
     const {data:openRefunds,error:refundError}=await svc.from("blink_paystack_refunds")
       .select("id").eq("payment_id",payout.payment_id)
       .in("status",["requested","approved","submitted","processing","completed"]).limit(1);
     if(refundError||openRefunds?.length)
       return json({error:"REFUND_BLOCKS_PAYOUT"},409);
     const {data:recipient,error:recErr}=await svc.rpc(
       "get_blink_paystack_seller_destination_for_server",{p_seller:payout.seller_id});
     if(recErr||!recipient)return json({error:"SELLER_RECIPIENT_NOT_VERIFIED"},409);
     const {data, error}=await svc.from("blink_paystack_seller_payouts")
       .update({status:"approved",approved_by:user.id,approved_at:new Date().toISOString()})
       .eq("id",payout.id).eq("status","blocked").select("id").maybeSingle();
     if(error||!data)return json({error:"PAYOUT_ALREADY_REVIEWED"},409);
     return json({status:"approved",payoutId:payout.id});
   }
   if(action==="execute_payout"){
     if(Deno.env.get("BLINK_PAYSTACK_PAYOUTS_ENABLED")!=="true")
       return json({error:"PAYOUTS_DISABLED"},503);
     if(!uuid(body.payoutId))return json({error:"INVALID_PAYOUT_ID"},400);
     const {data:payout,error:pErr}=await svc.from("blink_paystack_seller_payouts")
       .select("id,payment_id,seller_id,amount_kobo,status")
       .eq("id",body.payoutId).single();
     if(pErr||payout?.status!=="approved")return json({error:"PAYOUT_NOT_APPROVED"},409);
     const {data:market}=await svc.from("blink_paystack_market_orders")
       .select("marketplace_order_id,fulfilment_status")
       .eq("payment_id",payout.payment_id).single();
     if(market?.fulfilment_status!=="delivered")return json({error:"PAYOUT_NOT_ELIGIBLE"},409);
     const {data:openDisputes}=await svc.from("market_disputes").select("id")
       .eq("order_id",market.marketplace_order_id).in("status",["open","reviewing"]).limit(1);
     if(openDisputes?.length)return json({error:"OPEN_DISPUTE_BLOCKS_PAYOUT"},409);
     const {data:openRefunds,error:refundError}=await svc.from("blink_paystack_refunds")
       .select("id").eq("payment_id",payout.payment_id)
       .in("status",["requested","approved","submitted","processing","completed"]).limit(1);
     if(refundError||openRefunds?.length)
       return json({error:"REFUND_BLOCKS_PAYOUT"},409);
     const {data:recipient,error:recErr}=await svc.rpc(
       "get_blink_paystack_seller_destination_for_server",{p_seller:payout.seller_id});
     if(recErr||typeof recipient!=="string"||!recipient.startsWith("RCP_"))
       return json({error:"RECIPIENT_NOT_VERIFIED"},409);
     const transferReference=`blink-seller-${payout.id.replaceAll("-","")}`;
     const {data:lock,error:lockErr}=await svc.from("blink_paystack_seller_payouts")
       .update({status:"submitted",provider_transfer_ref:transferReference,
         updated_at:new Date().toISOString()})
       .eq("id",payout.id).eq("status","approved").select("id").maybeSingle();
     if(lockErr||!lock)return json({error:"PAYOUT_ALREADY_SUBMITTED"},409);
     const transfer=await paystackApi("transfer","POST",{
       source:"balance",amount:payout.amount_kobo,currency:"NGN",
       recipient,reference:transferReference,reason:"BLINK verified seller settlement",
     });
     await svc.from("blink_paystack_seller_payouts")
       .update({status:"processing",provider_transfer_id:String(transfer?.id||""),
         updated_at:new Date().toISOString()})
       .eq("id",payout.id).eq("status","submitted");
     return json({status:"processing",payoutId:payout.id});
   }
   return json({error:"UNKNOWN_ACTION"},400);
 }catch(error){return safeCustomerError(error)}
});
