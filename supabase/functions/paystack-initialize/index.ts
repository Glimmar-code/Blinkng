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

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const paystackSecret = Deno.env.get("PAYSTACK_SECRET_KEY")?.trim();

  if (!paystackSecret || !/^sk_(test|live)_/.test(paystackSecret)) {
    return json(503, { error: "PAYSTACK_NOT_CONFIGURED" });
  }

  const authHeader = req.headers.get("Authorization")?.trim();
  if (!authHeader?.startsWith("Bearer ")) {
    return json(401, { error: "AUTHENTICATION_REQUIRED" });
  }

  const service = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });
  const token = authHeader.slice("Bearer ".length);
  const { data: authData, error: authError } = await service.auth.getUser(token);
  const user = authData.user;
  if (authError || !user?.id || !user.email) {
    return json(401, { error: "AUTHENTICATION_REQUIRED" });
  }

  const userClient = createClient(supabaseUrl, anonKey, {
    auth: { persistSession: false },
    global: { headers: { Authorization: authHeader } },
  });

  const { data: economy, error: economyError } = await userClient.rpc("get_blink_economy_status");
  if (economyError) return json(500, { error: "ECONOMY_UNAVAILABLE" });
  if (!economy?.cash_checkout_enabled) {
    return json(503, { error: "CASH_CHECKOUT_DISABLED" });
  }

  const body = await req.json().catch(() => ({}));
  const kind = String(body?.kind ?? "").trim().toUpperCase();
  const packId = String(body?.pack_id ?? body?.packId ?? "").trim();

  let orderId = "";
  let amountNgn = 0;
  let currency = "NGN";
  let table = "";
  let product = "";

  if (kind === "COIN_PACK") {
    if (!packId) return json(400, { error: "PACK_REQUIRED" });
    const { data, error } = await userClient.rpc("create_blink_coin_purchase_order", { p_pack_id: packId });
    if (error || !data?.order_id) {
      return json(400, { error: "ORDER_CREATE_FAILED", message: error?.message ?? "Invalid coin pack." });
    }
    orderId = data.order_id;
    amountNgn = Number(data.amount_ngn);
    table = "blink_coin_purchase_orders";
    product = String(data.pack_id ?? packId);
  } else if (kind === "BLUE_VERIFICATION") {
    const { data, error } = await userClient.rpc("create_blink_verification_purchase_order");
    if (error || !data?.order_id) {
      return json(400, { error: "ORDER_CREATE_FAILED", message: error?.message ?? "Verification order could not be created." });
    }
    orderId = data.order_id;
    amountNgn = Number(data.amount_ngn);
    currency = String(data.currency ?? "NGN");
    table = "blink_verification_purchase_orders";
    product = "BLINK_VERIFIED";
  } else {
    return json(400, { error: "INVALID_PRODUCT_KIND" });
  }

  if (!Number.isInteger(amountNgn) || amountNgn <= 0 || currency !== "NGN") {
    return json(500, { error: "INVALID_SERVER_PRICE" });
  }

  const reference = `BLINK-${kind === "COIN_PACK" ? "COIN" : "VERIFY"}-${orderId.replaceAll("-", "")}`;
  const metadata = {
    blink_order_id: orderId,
    blink_kind: kind,
    blink_product: product,
  };

  const { error: reserveError } = await service
    .from(table)
    .update({
      provider_reference: reference,
      metadata,
      updated_at: new Date().toISOString(),
    })
    .eq("id", orderId)
    .eq("user_id", user.id);

  if (reserveError) {
    return json(500, { error: "REFERENCE_RESERVATION_FAILED" });
  }

  const callbackUrl =
    Deno.env.get("PAYSTACK_CALLBACK_URL")?.trim() ||
    `${supabaseUrl.replace(/\/$/, "")}/functions/v1/paystack-return`;

  const paystackBody = {
    email: user.email,
    amount: amountNgn * 100,
    currency,
    reference,
    callback_url: callbackUrl,
    metadata,
  };

  const response = await fetch("https://api.paystack.co/transaction/initialize", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${paystackSecret}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(paystackBody),
  });

  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload?.status !== true || !payload?.data?.authorization_url) {
    await service
      .from(table)
      .update({
        status: "failed",
        metadata: { ...metadata, initialize_error: payload?.message ?? `HTTP ${response.status}` },
        updated_at: new Date().toISOString(),
      })
      .eq("id", orderId);
    return json(502, { error: "PAYSTACK_INITIALIZE_FAILED", message: payload?.message ?? "Checkout could not be started." });
  }

  return json(200, {
    success: true,
    order_id: orderId,
    kind,
    amount_ngn: amountNgn,
    currency,
    provider: "paystack",
    reference,
    authorization_url: payload.data.authorization_url,
    access_code: payload.data.access_code,
  });
});
