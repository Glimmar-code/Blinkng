import { corsHeaders, json, reconcilePayment, validWebhookHash } from "../_shared/blink_flutterwave.ts";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "METHOD_NOT_ALLOWED" }, 405);
  if (!validWebhookHash(req)) return json({ error: "INVALID_SIGNATURE" }, 401);
  try {
    const payload = await req.json();
    if (payload.event !== "charge.completed") return json({ received: true });
    const reference = payload?.data?.tx_ref;
    if (typeof reference !== "string" || !/^blink-flw-[0-9a-f]{32}$/.test(reference)) {
      return json({ received: true }); // not a BLINK verification transaction
    }
    // Webhook data is merely a notification; always check payment with Flutterwave.
    const result = await reconcilePayment(reference);
    return json({ received: true, paymentStatus: result.status });
  } catch (error) {
    console.error("flutterwave-webhook:", String(error).slice(0,160));
    // 5xx allows provider retries after transient API or database failures.
    return json({ error: "RECONCILIATION_RETRY_REQUIRED" }, 503);
  }
});
