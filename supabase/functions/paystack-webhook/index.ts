import { createClient } from "npm:@supabase/supabase-js@2";

const encoder = new TextEncoder();

function hex(bytes: ArrayBuffer): string {
  return Array.from(new Uint8Array(bytes))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

async function signatureFor(secret: string, rawBody: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-512" },
    false,
    ["sign"],
  );
  return hex(await crypto.subtle.sign("HMAC", key, encoder.encode(rawBody)));
}

const reply = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });

async function verifyPaystack(secret: string, reference: string) {
  const response = await fetch(
    `https://api.paystack.co/transaction/verify/${encodeURIComponent(reference)}`,
    { headers: { Authorization: `Bearer ${secret}` } },
  );
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload?.status !== true) {
    throw new Error(payload?.message ?? `Paystack verify HTTP ${response.status}`);
  }
  return payload.data;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply(405, { error: "METHOD_NOT_ALLOWED" });

  const secret = Deno.env.get("PAYSTACK_SECRET_KEY")?.trim();
  if (!secret) return reply(503, { error: "PAYSTACK_NOT_CONFIGURED" });

  const rawBody = await req.text();
  const supplied = req.headers.get("x-paystack-signature")?.trim().toLowerCase() ?? "";
  const expected = await signatureFor(secret, rawBody);
  if (!supplied || !constantTimeEqual(supplied, expected)) {
    return reply(401, { error: "INVALID_PAYSTACK_SIGNATURE" });
  }

  const event = JSON.parse(rawBody);
  if (event?.event !== "charge.success") {
    return reply(200, { received: true, ignored: true });
  }

  const reference = String(event?.data?.reference ?? "").trim();
  if (!reference.startsWith("BLINK-")) {
    return reply(200, { received: true, ignored: true });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const service = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });

  let orderKind: "COIN_PACK" | "BLUE_VERIFICATION" | null = null;
  let order: any = null;

  const coinResult = await service
    .from("blink_coin_purchase_orders")
    .select("id,user_id,amount_ngn,status,provider_reference,metadata")
    .eq("provider_reference", reference)
    .maybeSingle();

  if (coinResult.data) {
    orderKind = "COIN_PACK";
    order = coinResult.data;
  } else {
    const verificationResult = await service
      .from("blink_verification_purchase_orders")
      .select("id,user_id,amount_ngn,currency,status,provider_reference,metadata")
      .eq("provider_reference", reference)
      .maybeSingle();
    if (verificationResult.data) {
      orderKind = "BLUE_VERIFICATION";
      order = verificationResult.data;
    }
  }

  if (!orderKind || !order) {
    return reply(200, { received: true, unknown_reference: true });
  }

  if (order.status === "fulfilled") {
    return reply(200, { received: true, already_fulfilled: true });
  }

  let verified: any;
  try {
    verified = await verifyPaystack(secret, reference);
  } catch (error) {
    console.error("Paystack verification failed", error);
    return reply(503, { error: "PAYSTACK_VERIFY_FAILED" });
  }

  const expectedAmount = Number(order.amount_ngn) * 100;
  const paidAmount = Number(verified?.amount ?? 0);
  const currency = String(verified?.currency ?? "");
  const paymentStatus = String(verified?.status ?? "");

  if (
    paymentStatus !== "success" ||
    paidAmount !== expectedAmount ||
    currency !== "NGN" ||
    String(verified?.reference ?? "") !== reference
  ) {
    await service
      .from(orderKind === "COIN_PACK" ? "blink_coin_purchase_orders" : "blink_verification_purchase_orders")
      .update({
        status: "failed",
        metadata: {
          ...(order.metadata ?? {}),
          verification_error: "AMOUNT_CURRENCY_OR_STATUS_MISMATCH",
          verified_amount_subunit: paidAmount,
          verified_currency: currency,
          verified_status: paymentStatus,
        },
        updated_at: new Date().toISOString(),
      })
      .eq("id", order.id);

    return reply(400, { error: "PAYMENT_MISMATCH" });
  }

  const rpcName =
    orderKind === "COIN_PACK"
      ? "fulfill_blink_coin_purchase_order"
      : "fulfill_blink_verification_purchase_order";

  const rpcArgs =
    orderKind === "COIN_PACK"
      ? { p_order_id: order.id, p_provider_reference: reference }
      : { p_order_id: order.id, p_provider_reference: reference };

  const { data, error } = await service.rpc(rpcName, rpcArgs);
  if (error) {
    console.error("BLINK fulfillment failed", error);
    return reply(503, { error: "FULFILLMENT_FAILED" });
  }

  return reply(200, { received: true, fulfilled: true, kind: orderKind, result: data });
});
