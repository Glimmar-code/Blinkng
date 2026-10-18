import { createClient } from "npm:@supabase/supabase-js@2";
import { paystackMatchesOrder } from "./paystack_integrity.mjs";

const encoder = new TextEncoder();
export const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
  "Cache-Control": "no-store",
};

export const reply = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: cors });

export const secret = () => {
  const key = Deno.env.get("PAYSTACK_SECRET_KEY") || "";
  if (!/^sk_(test|live)_/.test(key)) throw new Error("PAYSTACK_NOT_CONFIGURED");
  return key;
};

const required = (name: string) => {
  const value = Deno.env.get(name);
  if (!value) throw new Error("SERVER_CONFIG_MISSING");
  return value;
};

export const admin = () => createClient(required("SUPABASE_URL"), required("SUPABASE_SERVICE_ROLE_KEY"), {
  auth: { autoRefreshToken: false, persistSession: false },
});

export async function userFrom(req: Request) {
  const auth = req.headers.get("authorization") || "";
  if (!/^Bearer \S+$/i.test(auth)) return null;
  const { data, error } = await admin().auth.getUser(auth.substring(7));
  return error ? null : data.user;
}

export async function paystack(path: string, init: RequestInit = {}) {
  const response = await fetch(`https://api.paystack.co/${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${secret()}`,
      ...(init.body ? { "Content-Type": "application/json" } : {}),
    },
    signal: AbortSignal.timeout(15000),
  });
  const payload = await response.json().catch(() => null);
  if (!response.ok || !payload?.status) throw new Error("PAYSTACK_UPSTREAM_UNAVAILABLE");
  return payload.data;
}

export async function signedWebhook(req: Request, raw: string): Promise<boolean> {
  const signature = req.headers.get("x-paystack-signature")?.toLowerCase() || "";
  if (!/^[0-9a-f]{128}$/.test(signature)) return false;
  const key = await crypto.subtle.importKey("raw", encoder.encode(secret()), {
    name: "HMAC", hash: "SHA-512",
  }, false, ["sign"]);
  const bytes = new Uint8Array(await crypto.subtle.sign("HMAC", key, encoder.encode(raw)));
  const expected = [...bytes].map((x) => x.toString(16).padStart(2, "0")).join("");
  let mismatch = 0;
  for (let i=0; i<128; i++) mismatch |= expected.charCodeAt(i)^signature.charCodeAt(i);
  return mismatch===0;
}

export function properReference(value: unknown): value is string {
  return typeof value==="string" && /^BLINK-PS-[0-9a-f]{32}$/.test(value);
}

export async function reconcile(reference: string, userId?: string) {
  if (!properReference(reference)) throw new Error("INVALID_REFERENCE");
  const client = admin();
  const { data: order, error: lookupError } = await client
    .from("blink_paystack_orders")
    .select("id,user_id,reference,kind,amount_ngn,currency,status,provider_transaction_id")
    .eq("reference",reference).maybeSingle();
  if (lookupError) throw new Error("PAYMENT_LOOKUP_UNAVAILABLE");
  if (!order || (userId && userId!==order.user_id)) throw new Error("ORDER_NOT_FOUND");
  if (order.status==="fulfilled") return {status:"fulfilled",kind:order.kind,alreadyFulfilled:true};
  if (order.status!=="pending") return {status:order.status,kind:order.kind};

  // Neither an app callback nor webhook fields may grant money or badges.
  const verified = await paystack(`transaction/verify/${encodeURIComponent(reference)}`);
  if (verified?.status!=="success") return {status:"pending",kind:order.kind};
  if (!paystackMatchesOrder(verified, order)) throw new Error("PAYMENT_AMOUNT_REFERENCE_MISMATCH");
  // Paystack may return integer IDs larger than 32-bit; use a string representation.
  const providerId = String(verified.id);
  const { data, error } = await client.rpc("fulfill_blink_paystack_order", {
    p_reference:reference,
    p_provider_transaction_id:providerId,
    p_paid_kobo:Number(verified.amount),
    p_currency:verified.currency,
  });
  if (error) throw new Error("PAYMENT_FULFILMENT_UNAVAILABLE");
  return { status:"fulfilled",kind:order.kind,alreadyFulfilled:Boolean(data?.already_fulfilled) };
}
