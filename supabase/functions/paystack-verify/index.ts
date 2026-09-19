import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Client-Info, Apikey",
};

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json", "Cache-Control": "no-store" },
  });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });
  if (req.method !== "POST") return json(405, { error: "METHOD_NOT_ALLOWED" });

  const secret = Deno.env.get("PAYSTACK_SECRET_KEY")?.trim();
  if (!secret) return json(503, { error: "PAYSTACK_NOT_CONFIGURED" });

  const authHeader = req.headers.get("Authorization")?.trim();
  if (!authHeader?.startsWith("Bearer ")) {
    return json(401, { error: "AUTHENTICATION_REQUIRED" });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const service = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });
  const { data: authData, error: authError } = await service.auth.getUser(authHeader.slice(7));
  const user = authData.user;
  if (authError || !user?.id) return json(401, { error: "AUTHENTICATION_REQUIRED" });

  const body = await req.json().catch(() => ({}));
  const orderId = String(body?.order_id ?? body?.orderId ?? "").trim();
  if (!orderId) return json(400, { error: "ORDER_REQUIRED" });

  let kind: "COIN_PACK" | "BLUE_VERIFICATION" | null = null;
  let order: any = null;

  const coin = await service
    .from("blink_coin_purchase_orders")
    .select("id,user_id,amount_ngn,status,provider_reference")
    .eq("id", orderId)
    .eq("user_id", user.id)
    .maybeSingle();

  if (coin.data) {
    kind = "COIN_PACK";
    order = coin.data;
  } else {
    const verification = await service
      .from("blink_verification_purchase_orders")
      .select("id,user_id,amount_ngn,currency,status,provider_reference")
      .eq("id", orderId)
      .eq("user_id", user.id)
      .maybeSingle();
    if (verification.data) {
      kind = "BLUE_VERIFICATION";
      order = verification.data;
    }
  }

  if (!kind || !order) return json(404, { error: "CASH_ORDER_NOT_FOUND" });
  if (order.status === "fulfilled") {
    return json(200, { success: true, kind, status: "fulfilled", already_fulfilled: true });
  }

  const reference = String(order.provider_reference ?? "").trim();
  if (!reference) return json(409, { error: "PAYMENT_NOT_INITIALIZED" });

  const response = await fetch(
    `https://api.paystack.co/transaction/verify/${encodeURIComponent(reference)}`,
    { headers: { Authorization: `Bearer ${secret}` } },
  );
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload?.status !== true) {
    return json(502, { error: "PAYSTACK_VERIFY_FAILED", message: payload?.message ?? "Unable to verify payment." });
  }

  const tx = payload.data;
  if (
    String(tx?.status ?? "") !== "success" ||
    Number(tx?.amount ?? 0) !== Number(order.amount_ngn) * 100 ||
    String(tx?.currency ?? "") !== "NGN" ||
    String(tx?.reference ?? "") !== reference
  ) {
    return json(409, { success: false, kind, status: String(tx?.status ?? "pending") });
  }

  const rpcName =
    kind === "COIN_PACK"
      ? "fulfill_blink_coin_purchase_order"
      : "fulfill_blink_verification_purchase_order";

  const { data, error } = await service.rpc(rpcName, {
    p_order_id: order.id,
    p_provider_reference: reference,
  });

  if (error) return json(500, { error: "FULFILLMENT_FAILED", message: error.message });

  return json(200, { success: true, kind, status: "fulfilled", result: data });
});
