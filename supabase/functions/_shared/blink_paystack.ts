import { createClient } from "npm:@supabase/supabase-js@2";
import { matchesPaystackReceipt } from "./paystack_integrity.mjs";

export const responseHeaders = {
  "Content-Type": "application/json",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization,apikey,content-type,x-client-info",
  "Access-Control-Allow-Methods": "POST,OPTIONS",
};
export const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: responseHeaders });

export function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value || value === "DO_NOT_COMMIT_REAL_VALUE") throw Error("MISSING_SERVER_CONFIG");
  return value;
}

export function adminClient() {
  return createClient(requiredEnv("SUPABASE_URL"), requiredEnv("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

export async function authenticatedUser(req: Request) {
  const header = req.headers.get("authorization") || "";
  const found = /^Bearer\s+(\S+)$/i.exec(header);
  if (!found) return null;
  const { data, error } = await adminClient().auth.getUser(found[1]);
  return error ? null : data.user;
}

export async function paystackApi(path: string, method = "GET", body?: unknown) {
  const response = await fetch(`https://api.paystack.co/${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${requiredEnv("PAYSTACK_SECRET_KEY")}`,
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
    signal: AbortSignal.timeout(18000),
  });
  if (!response.ok) {
    if (response.status === 404) return null;
    throw Error(`PAYSTACK_UPSTREAM_${response.status}`);
  }
  const payload = await response.json();
  if (payload.status !== true) throw Error("PAYSTACK_API_RESPONSE_ERROR");
  return payload.data;
}

export async function reconcilePaystackOrder(reference: string, expectedUser?: string) {
  if (!/^blink-ps-[0-9a-f]{32}$/.test(reference)) throw Error("INVALID_REFERENCE");
  const admin = adminClient();
  const { data: order, error: orderError } = await admin
    .from("blink_paystack_orders")
    .select("user_id,reference,status,currency,customer_email,amount_kobo,transaction_id,kind")
    .eq("reference", reference).maybeSingle();
  if (orderError) throw Error("ORDER_LOOKUP_FAILED");
  if (!order || (expectedUser && order.user_id !== expectedUser)) throw Error("ORDER_NOT_FOUND");
  if (order.status === "fulfilled") return { status: "fulfilled", kind: order.kind };
  if (order.status !== "pending") return { status: order.status, kind: order.kind };

  // Untrusted browser callbacks and webhook bodies NEVER authorize fulfillment.
  const receipt = await paystackApi(`transaction/verify/${encodeURIComponent(reference)}`);
  if (receipt == null || receipt.status !== "success") {
    return { status: "pending", kind: order.kind };
  }
  if (!matchesPaystackReceipt(receipt, order)) {
    throw Error("PAYSTACK_RECEIPT_MISMATCH");
  }
  const { data, error } = await admin.rpc("fulfill_blink_paystack_order", {
    p_reference: reference,
    p_transaction_id: String(receipt.id),
    p_amount_kobo: Number(receipt.amount),
    p_currency: receipt.currency,
    p_customer_email: String(receipt.customer.email).toLowerCase(),
  });
  if (error || !data?.success) throw Error("PAYSTACK_FULFILMENT_FAILED");
  return { status: "fulfilled", kind: order.kind };
}

/** Verify HMAC-SHA512 over the RAW request body using Paystack's secret key. */
export async function isAuthenticPaystackWebhook(body: string, signature: string) {
  if (!/^[a-f0-9]{128}$/i.test(signature)) return false;
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(requiredEnv("PAYSTACK_SECRET_KEY")),
    { name: "HMAC", hash: "SHA-512" }, false, ["verify"]
  );
  const bytes = new Uint8Array(signature.match(/.{2}/g)!.map((chunk)=>parseInt(chunk, 16)));
  return crypto.subtle.verify("HMAC", key, bytes, new TextEncoder().encode(body));
}
