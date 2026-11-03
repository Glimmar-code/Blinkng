import { json, paystackApi, preflight, reconcile, serviceClient, validSignature } from "../_shared/blink_paystack.ts";

const reviewedEvents = new Set([
  "refund.pending","refund.processing","refund.failed","refund.processed",
  "charge.dispute.create","charge.dispute.remind","charge.dispute.resolve",
]);
Deno.serve(async req=>{
  if(req.method==="OPTIONS") return preflight();
  if(req.method!=="POST") return json({error:"METHOD_NOT_ALLOWED"},405);
  try {
    const raw=await req.text();
    if(raw.length>200000) return json({error:"BODY_TOO_LARGE"},413);
    // An untrusted callback can never create a purchase, grant an entitlement
    // or add a reversal record without the merchant's HMAC-SHA512 signature.
    if(!await validSignature(req,raw)) return json({error:"INVALID_SIGNATURE"},401);
    const event=JSON.parse(raw);
    if(event.event==="charge.success"){
      const reference=event.data?.reference;
      if(typeof reference!=="string" || !/^blink-ps-[0-9a-f]{32}$/.test(reference))
        return json({received:true});
      const result=await reconcile(reference);
      return json({received:true,status:result.status});
    }
    // Signed invoice notices never grant benefits or initiate charges.
    if(["invoice.create","invoice.payment_failed","subscription.not_renew"].includes(event.event)){
      const invoice=event.data||{};
      const subCode=invoice.subscription?.subscription_code ||
        (typeof invoice.subscription==="string" ? invoice.subscription :
        typeof invoice.subscription_code==="string" ? invoice.subscription_code : "");
      if(!/^SUB_[a-zA-Z0-9]+$/.test(String(subCode))) return json({received:true});
      const invoiceId=String(invoice.id||invoice.invoice_code||invoice.reference||"");
      if(!invoiceId || invoiceId.length>90) return json({received:true});
      const kind=event.event==="invoice.create" ? "renewal_reminder" :
        event.event==="invoice.payment_failed" ? "renewal_failed" : "renewal_cancelled";
      const {error:noticeError}=await serviceClient().rpc(
        "queue_blink_paystack_renewal_notice",{
         p_subscription_code:subCode,p_kind:kind,
         p_event_key:`${event.event}:${subCode}:${invoiceId}`,
      });
      if(noticeError) throw new Error("RENEWAL_NOTICE_FAILED");
      return json({received:true});
    }
    // Paystack bills subscriptions itself. Never treat an unsigned invoice as payment.
    // Subsequent references are provider-generated, not BLINK checkout references.
    if(event.event==="invoice.update"){
      const invoice=event.data||{};
      if(invoice.paid!==true && invoice.status!=="success")
        return json({received:true});
      const code=invoice.subscription?.subscription_code ||
        (typeof invoice.subscription==="string" ? invoice.subscription : "");
      const txReference=invoice.transaction?.reference || invoice.reference;
      if(!/^SUB_[a-zA-Z0-9]+$/.test(String(code)) ||
         typeof txReference!=="string" || txReference.length>140)
        return json({received:true});
      const svc=serviceClient();
      const {data:sub,error:subErr}=await svc.rpc(
        "get_blink_paystack_renewal_by_code_for_server",{p_code:code});
      if(subErr) throw new Error("SUBSCRIPTION_LOOKUP_FAILED");
      if(!sub || !["active","cancel_pending"].includes(sub.status))
        return json({received:true});
      const payment=await paystackApi(
        `transaction/verify/${encodeURIComponent(txReference)}`);
      const expectedMode=Deno.env.get("PAYSTACK_EXPECTED_DOMAIN")||"test";
      if(payment?.status!=="success" || payment.domain!==expectedMode ||
         payment.reference!==txReference || payment.currency!=="NGN" ||
         Number(payment.amount)!==sub.amount_ngn*100 ||
         String(payment.customer?.customer_code)!==sub.customer_code)
        throw new Error("SUBSCRIPTION_INVOICE_MISMATCH");
      const invoicePlan=invoice.plan?.plan_code||invoice.subscription?.plan?.plan_code;
      if(invoicePlan && invoicePlan!==sub.plan_code)
        throw new Error("SUBSCRIPTION_PLAN_MISMATCH");
      const {error:fulfillError}=await svc.rpc("fulfill_blink_paystack_renewal",{
        p_code:code,p_reference:txReference,p_transaction_id:String(payment.id),
        p_amount_kobo:payment.amount,p_currency:"NGN",
      });
      if(fulfillError) throw new Error("SUBSCRIPTION_FULFILLMENT_FAILED");
      return json({received:true,status:"fulfilled"});
    }
    if(["transfer.success","transfer.failed","transfer.reversed"].includes(event.event)) {
      const ref=String(event.data?.reference||"");
      if(!/^blink-seller-[0-9a-f]{32}$/.test(ref)) return json({received:true});
      const svc=serviceClient();
      const {data:payout,error:payoutError}=await svc.from("blink_paystack_seller_payouts")
        .select("id,payment_id,amount_kobo,status")
        .eq("provider_transfer_ref",ref).maybeSingle();
      if(payoutError || !payout) throw new Error("TRANSFER_LEDGER_NOT_FOUND");
      if(payout.status==="completed" || payout.status==="cancelled")
        return json({received:true,status:payout.status});
      const verified=await paystackApi(`transfer/verify/${encodeURIComponent(ref)}`);
      if(!verified || verified.reference!==ref ||
        verified.domain!==(Deno.env.get("PAYSTACK_EXPECTED_DOMAIN")||"test") ||
        Number(verified.amount)!==payout.amount_kobo ||
        verified.currency!=="NGN") throw new Error("TRANSFER_PROVIDER_MISMATCH");
      const resultStatus=String(verified.status||"").toLowerCase();
      if(resultStatus!=="success" && resultStatus!=="failed")
        return json({received:true,status:"processing"});
      // Only a Paystack-verified terminal transfer can settle the order.
      const desired=resultStatus==="success"?"completed":"failed";
      const {error:updateError}=await svc.from("blink_paystack_seller_payouts")
        .update({status:desired,updated_at:new Date().toISOString()})
        .eq("id",payout.id).in("status",["submitted","processing"]);
      if(updateError) throw new Error("TRANSFER_LEDGER_UPDATE_FAILED");
      if(desired==="completed") {
        const {error:marketError}=await svc.from("blink_paystack_market_orders")
          .update({fulfilment_status:"settled"})
          .eq("payment_id",payout.payment_id).eq("fulfilment_status","delivered");
        if(marketError) throw new Error("MARKET_SETTLEMENT_UPDATE_FAILED");
      }
      return json({received:true,status:desired});
    }
    if(!reviewedEvents.has(event.event)) return json({received:true});
    const data=event.data||{};
    const reference=data.transaction_reference ||
      (typeof data.transaction?.reference==="string" ? data.transaction.reference : null);
    if(typeof reference!=="string" || !/^blink-ps-[0-9a-f]{32}$/.test(reference))
      return json({received:true});
    // Do not cross-post test-mode disputes into the live ledger or vice versa.
    const domain=Deno.env.get("PAYSTACK_EXPECTED_DOMAIN")||"test";
    if(data.domain!==domain) return json({error:"PROVIDER_MODE_MISMATCH"},409);
    const amount=Number(data.amount);
    if(!Number.isSafeInteger(amount)||amount<=0 || data.currency!=="NGN")
      return json({error:"INVALID_PROVIDER_AMOUNT"},400);
    const eventId=data.refund_reference??data.id??data.dispute_reference??"";
    const eventKey=`${event.event}:${reference}:${String(eventId).slice(0,90)}`;
    const {error}=await serviceClient().rpc("record_blink_paystack_provider_event",{
      p_reference:reference,
      p_event_type:event.event,
      p_event_key:eventKey,
      p_amount_kobo:amount,
      p_currency:"NGN",
    });
    if(error) throw new Error("PROVIDER_EVENT_LEDGER_FAILED");
    if(event.event==="refund.processed") {
      const {data:paid,error:paymentError}=await serviceClient()
        .from("blink_paystack_payments").select("id,amount_ngn,status")
        .eq("reference",reference).maybeSingle();
      if(paymentError||!paid||paid.amount_ngn*100!==amount || paid.status!=="refunded")
        throw new Error("REFUND_RECORD_MISMATCH");
      const {error:refundError}=await serviceClient().from("blink_paystack_refunds")
        .update({status:"completed",updated_at:new Date().toISOString()})
        .eq("payment_id",paid.id).in("status",["submitted","processing"]);
      if(refundError) throw new Error("REFUND_STATUS_RECONCILIATION_FAILED");
      const {error:marketError}=await serviceClient().from("blink_paystack_market_orders")
        .update({fulfilment_status:"refunded"})
        .eq("payment_id",paid.id)
        .in("fulfilment_status",["paid_hold","delivered","review_required","refunding"]);
      if(marketError) throw new Error("MARKET_REFUND_RECONCILIATION_FAILED");
      const {error:payoutError}=await serviceClient().from("blink_paystack_seller_payouts")
        .update({status:"cancelled",updated_at:new Date().toISOString()})
        .eq("payment_id",paid.id).in("status",["blocked","eligible"]);
      if(payoutError) throw new Error("SELLER_PAYOUT_REFUND_CANCELLATION_FAILED");
    }
    return json({received:true});
  } catch(error) {
    console.error("paystack-webhook:",String(error).slice(0,160));
    return json({error:"WEBHOOK_RETRY_REQUIRED"},503);
  }
});
