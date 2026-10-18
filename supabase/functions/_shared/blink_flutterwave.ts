import { createClient } from "npm:@supabase/supabase-js@2";
import { transactionMatchesOrder } from "./flutterwave_integrity.mjs";

export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

export function env(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`CONFIG_MISSING_${name}`);
  return value;
}

export function adminClient() {
  return createClient(env("SUPABASE_URL"), env("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { autoRefreshToken: false, persistSession: false },
  });
}

export async function authenticatedUser(req: Request) {
  const match = /^Bearer (\S+)$/i.exec(req.headers.get("Authorization") || "");
  if (!match) return null;
  const { data, error } = await adminClient().auth.getUser(match[1]);
  return error ? null : data.user;
}

export function flutterwaveKey(): string {
  return env("FLW_SECRET_KEY"); // NEVER sent to Android or a browser.
}

export async function flutterwaveRequest(path: string, options: RequestInit = {}) {
  const res = await fetch(`https://api.flutterwave.com/v3/${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${flutterwaveKey()}`,
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {}),
    },
    signal: AbortSignal.timeout(15000),
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`FLUTTERWAVE_UPSTREAM_${res.status}`);
  const body = await res.json();
  if (body.status !== "success") throw new Error("FLUTTERWAVE_API_ERROR");
  return body.data;
}

export async function reconcilePayment(reference: string, expectedUserId?: string) {
  if (!/^blink-flw-[0-9a-f]{32}$/.test(reference)) throw new Error("INVALID_REFERENCE");
  const admin = adminClient();
  const { data: order, error: dbError } = await admin
    .from("blink_flutterwave_payments")
    .select("tx_ref,user_id,status,currency,amount_ngn,provider_transaction_id")
    .eq("tx_ref", reference)
    .maybeSingle();
  if (dbError) throw new Error("PAYMENT_LOOKUP_UNAVAILABLE");
  if (!order || (expectedUserId && order.user_id !== expectedUserId)) {
    throw new Error("PAYMENT_NOT_FOUND");
  }
  if (order.status === "fulfilled") return { status: "fulfilled", alreadyFulfilled: true };
  if (order.status !== "pending") return { status: order.status };

  // Always verify with Flutterwave servers: never trust a redirect or webhook's claimed status.
  const verified = await flutterwaveRequest(
    `transactions/verify_by_reference?tx_ref=${encodeURIComponent(reference)}`
  );
  if (!verified || verified.status !== "successful") {
    return { status: "pending" };
  }
  if (!transactionMatchesOrder(verified, order)) {
    throw new Error("PAYMENT_VERIFICATION_MISMATCH");
  }
  const amount = Number(verified.amount);
  const id = String(verified.id);

  const { data, error } = await admin.rpc("fulfill_blink_flutterwave_verification", {
    p_tx_ref: reference,
    p_provider_transaction_id: id,
    p_amount_ngn: amount,
    p_currency: "NGN",
  });
  if (error) throw new Error(`PAYMENT_FULFILMENT_FAILED: ${error.code || "database"}`);
  return { status: "fulfilled", alreadyFulfilled: Boolean(data?.already_fulfilled) };
}

/** Constant-time comparison of configured webhook secret and verif-hash header. */
export function validWebhookHash(req: Request): boolean {
  const supplied = req.headers.get("verif-hash") || "";
  const expected = Deno.env.get("FLW_WEBHOOK_HASH") || "";
  if (!expected || !supplied) return false;
  const encoder = new TextEncoder();
  const a = encoder.encode(supplied);
  const b = encoder.encode(expected);
  let different = a.length ^ b.length;
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    different |= (a[i] || 0) ^ (b[i] || 0);
  }
  return different === 0;
}
