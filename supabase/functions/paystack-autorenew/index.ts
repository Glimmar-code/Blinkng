import { currentUser, json, paystackApi, preflight, serviceClient } from "../_shared/blink_paystack.ts";

const monthInterval: Record<number,string> = {
  1:"monthly",3:"quarterly",6:"biannually",12:"annually",
};
function planCode(tier:string, months:number):string {
  const config=JSON.parse(Deno.env.get("BLINK_PAYSTACK_RECURRING_PLANS")||"{}");
  const code=config[`${tier}_${months}`];
  if(typeof code!=="string" || !/^PLN_[a-zA-Z0-9]+$/.test(code))
    throw new Error("RECURRING_PLAN_UNCONFIGURED");
  return code;
}
Deno.serve(async req=>{
  if(req.method==="OPTIONS") return preflight();
  if(req.method!=="POST") return json({error:"METHOD_NOT_ALLOWED"},405);
  const user=await currentUser(req);
  if(!user) return json({error:"SIGN_IN_REQUIRED"},401);
  try {
    const input=await req.json().catch(()=>({}));
    const action=String(input.action||"");
    const service=serviceClient();
    if(action==="status"){
      const {data,error}=await service.rpc("get_blink_paystack_renewal_for_server",{p_user:user.id});
      if(error) throw new Error("SUBSCRIPTION_LOOKUP_FAILED");
      return json({status:data?.status||"none",tier:data?.tier||null,
        months:data?.months||null,amountNgn:data?.amount_ngn||null,
        autoRenew:data?.status==="active"});
    }
    if(action==="cancel"){
      // Cancellation is available regardless of the checkout feature flag.
      const {data:sub,error}=await service.rpc("get_blink_paystack_renewal_for_server",{p_user:user.id});
      if(error) throw new Error("SUBSCRIPTION_LOOKUP_FAILED");
      if(!sub) return json({status:"none",autoRenew:false});
      if(sub.status==="creating" || !sub.subscription_code || !sub.email_token)
        return json({error:"ENROLLMENT_REQUIRES_PROVIDER_RECONCILIATION"},409);
      // Never show this sensitive provider email_token to the mobile client.
      await paystackApi("subscription/disable","POST",{
        code:sub.subscription_code,token:sub.email_token
      });
      const {data:stopped,error:dbError}=await service.rpc("stop_blink_paystack_renewal",{
        p_id:sub.id,p_user:user.id,
      });
      if(dbError || !stopped) throw new Error("CANCELLATION_RECORD_PENDING");
      return json({status:"cancelled",autoRenew:false});
    }
    if(action!=="enable") return json({error:"INVALID_ACTION"},400);
    // Never turn on recurring charges based on a hidden default/on switch.
    if(input.agreeToRecurring!==true || input.consentVersion!=="v1.2026-10")
      return json({error:"EXPLICIT_RECURRING_CONSENT_REQUIRED"},400);
    if(Deno.env.get("BLINK_PAYSTACK_RECURRING_ENABLED")!=="true" ||
       Deno.env.get("BLINK_PAYSTACK_CHECKOUT_ENABLED")!=="true")
      return json({error:"RECURRING_NOT_AVAILABLE"},503);
    const reference=String(input.reference||"");
    if(!/^blink-ps-[0-9a-f]{32}$/.test(reference))
      return json({error:"INVALID_PURCHASE_REFERENCE"},400);
    const {data:payment,error:readError}=await service
      .from("blink_paystack_payments")
      .select("id,user_id,reference,purchase_kind,item_code,duration_months,amount_ngn,status")
      .eq("reference",reference).eq("user_id",user.id).maybeSingle();
    if(readError || !payment || payment.status!=="fulfilled" ||
       payment.purchase_kind!=="verification") return json({error:"PAID_PLAN_REQUIRED"},409);
    const tier=String(payment.item_code);
    const months=Number(payment.duration_months||1);
    if(!["BLUE","GOLD"].includes(tier) || !monthInterval[months])
      return json({error:"PLAN_NOT_ELIGIBLE"},409);
    const {data:profile,error:profileError}=await service
      .from("profiles").select("verification_expires_at,verification_badge")
      .eq("id",user.id).single();
    const expireAt=profile?.verification_expires_at;
    if(profileError || !expireAt || Date.parse(expireAt)<=Date.now()+60_000)
      return json({error:"ACTIVE_EXPIRING_MEMBERSHIP_REQUIRED"},409);
    if(profile.verification_badge!==tier)
      return json({error:"ACTIVE_TIER_CHANGED"},409);
    const plan=planCode(tier,months);
    const providerPlan=await paystackApi(`plan/${encodeURIComponent(plan)}`);
    const expectedMode=Deno.env.get("PAYSTACK_EXPECTED_DOMAIN")||"test";
    if(providerPlan.plan_code!==plan || providerPlan.domain!==expectedMode ||
       providerPlan.currency!=="NGN" || providerPlan.interval!==monthInterval[months] ||
       Number(providerPlan.amount)!==payment.amount_ngn*100)
      return json({error:"MERCHANT_PLAN_PRICE_MISMATCH"},409);
    // Reverify the original transaction, authorization and provider identity.
    const original=await paystackApi(
      `transaction/verify/${encodeURIComponent(reference)}`);
    const auth=original.authorization;
    const customer=original.customer;
    if(original.domain!==expectedMode || original.status!=="success" ||
      original.reference!==reference || original.amount!==payment.amount_ngn*100 ||
      original.currency!=="NGN" || !auth?.reusable ||
      !/^AUTH_[a-zA-Z0-9]+$/.test(String(auth.authorization_code||"")) ||
      !/^CUS_[a-zA-Z0-9]+$/.test(String(customer?.customer_code||"")) ||
      !customer.email || customer.email.toLowerCase()!==user.email?.toLowerCase())
      return json({error:"REUSABLE_AUTHORIZATION_UNAVAILABLE"},409);
    // Persist consent *before* contacting Paystack, so errors cannot cause silent retries.
    const {data:prepared,error:prepareError}=await service.rpc("prepare_blink_paystack_renewal",{
      p_user:user.id,p_reference:reference,p_plan_code:plan,
      p_customer_code:customer.customer_code,p_consent_version:"v1.2026-10",
    });
    if(prepareError || !prepared?.id)
      return json({error:prepareError?.message||"RECURRING_ENROLLMENT_FAILED"},409);
    const created=await paystackApi("subscription","POST",{
      customer:customer.customer_code,
      plan,
      authorization:auth.authorization_code,
      start_date:expireAt,
    });
    if(!/^SUB_[a-zA-Z0-9]+$/.test(String(created?.subscription_code||"")) ||
       !created?.email_token)
      throw new Error("SUBSCRIPTION_PROVIDER_RESPONSE_INCOMPLETE");
    const {error:finishError}=await service.rpc("finish_blink_paystack_renewal",{
      p_id:prepared.id,p_subscription_code:created.subscription_code,
      p_email_token:created.email_token,
    });
    if(finishError) throw new Error("SUBSCRIPTION_RECONCILIATION_REQUIRED");
    return json({status:"active",autoRenew:true,
      message:"Renewal is enabled. You can cancel future charges any time."});
  } catch(error) {
    console.error("paystack-autorenew:",String(error).slice(0,120));
    return json({error:"SUBSCRIPTION_UNAVAILABLE_OR_REQUIRES_REVIEW"},503);
  }
});