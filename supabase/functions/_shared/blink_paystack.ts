import { createClient } from "npm:@supabase/supabase-js@2";
import { transactionMatchesOrder } from "./paystack_integrity.mjs";

const responseHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};
export function preflight() { return new Response(null, { status: 204, headers: responseHeaders }); }
export function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status, headers: responseHeaders });
}
function requiredSecret(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`MISSING_SERVER_CONFIG_${name}`);
  return value;
}
export function serviceClient() {
  return createClient(requiredSecret("SUPABASE_URL"), requiredSecret("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}
export async function currentUser(req: Request) {
  const match = /^Bearer\s+(\S+)$/i.exec(req.headers.get("authorization") || "");
  if (!match) return null;
  const { data, error } = await serviceClient().auth.getUser(match[1]);
  return error ? null : data.user;
}
export async function paystackApi(path: string, method = "GET", payload?: unknown) {
  const mode = Deno.env.get("PAYSTACK_EXPECTED_DOMAIN") || "test";
  const secret = requiredSecret("PAYSTACK_SECRET_KEY");
  // Fail closed before *any* Paystack API call: staging must never charge live money
  // when an operator accidentally configures a live secret in the test environment.
  if ((mode !== "test" && mode !== "live") || !secret.startsWith(`sk_${mode}_`)) {
    throw new Error("PAYSTACK_MERCHANT_MODE_MISMATCH");
  }
  const res = await fetch(`https://api.paystack.co/${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${secret}`,
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: payload === undefined ? undefined : JSON.stringify(payload),
    signal: AbortSignal.timeout(16000),
  });
  if (!res.ok) throw new Error(`PAYSTACK_HTTP_${res.status}`);
  const body = await res.json();
  if (body.status !== true) throw new Error("PAYSTACK_API_REJECTED");
  return body.data;
}
export async function validSignature(req: Request, rawBody: string): Promise<boolean> {
  const supplied = req.headers.get("x-paystack-signature")?.toLowerCase() || "";
  if (!/^[a-f0-9]{128}$/.test(supplied)) return false;
  const keyBytes = new TextEncoder().encode(requiredSecret("PAYSTACK_SECRET_KEY"));
  const key = await crypto.subtle.importKey("raw", keyBytes, { name: "HMAC", hash: "SHA-512" }, false, ["sign"]);
  const digest = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(rawBody)));
  const expected = Array.from(digest, x=>x.toString(16).padStart(2,"0")).join("");
  let difference = 0;
  for (let i = 0; i < 128; i++) difference |= supplied.charCodeAt(i) ^ expected.charCodeAt(i);
  return difference === 0;
}
export async function reconcile(reference: string, expectedUser?: string) {
  if (!/^blink-ps-[0-9a-f]{32}$/.test(reference)) throw new Error("REFERENCE_INVALID");
  const svc = serviceClient();
  const { data: order, error } = await svc.from("blink_paystack_payments")
    .select("id,user_id,reference,purchase_kind,amount_ngn,currency,status,paystack_transaction_id")
    .eq("reference",reference).maybeSingle();
  if (error) throw new Error("ORDER_LOOKUP_ERROR");
  if (!order || (expectedUser && order.user_id !== expectedUser)) throw new Error("ORDER_NOT_FOUND");
  if (order.status === "fulfilled") return { status:"fulfilled", alreadyFulfilled:true, kind:order.purchase_kind };
  if (order.status !== "pending") return { status:order.status };

  // Never trust the browser return URL or claimed webhook payload.
  const payment = await paystackApi(`transaction/verify/${encodeURIComponent(reference)}`);
  if (!payment || payment.status !== "success") return { status:"pending" };

  const expectedMode = Deno.env.get("PAYSTACK_EXPECTED_DOMAIN") || "test";
  if (!["test","live"].includes(expectedMode) || payment.domain !== expectedMode) {
    throw new Error("PAYSTACK_ENVIRONMENT_MISMATCH");
  }
  if (!transactionMatchesOrder(payment,order)) throw new Error("PAYMENT_DATA_MISMATCH");

  const { data: fulfilled, error: fulfillmentError } = await svc.rpc(
    "fulfill_blink_paystack_order", {
      p_reference: reference,
      p_transaction_id: String(payment.id),
      p_amount_kobo: payment.amount,
      p_currency: payment.currency,
    }
  );
  if (fulfillmentError) throw new Error(`FULFILLMENT_ERROR_${fulfillmentError.code || "UNKNOWN"}`);
  return { status:"fulfilled", alreadyFulfilled:Boolean(fulfilled?.already_fulfilled), kind:order.purchase_kind };
}
