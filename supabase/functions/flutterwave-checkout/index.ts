import { createClient } from "npm:@supabase/supabase-js@2";
import { authenticatedUser, corsHeaders, env, flutterwaveRequest, json } from "../_shared/blink_flutterwave.ts";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "METHOD_NOT_ALLOWED" }, 405);
  const user = await authenticatedUser(req);
  if (!user) return json({ error: "AUTHENTICATION_REQUIRED" }, 401);
  if (!user.email) return json({ error: "VERIFIED_EMAIL_REQUIRED" }, 400);
  // Extra kill switch; cash_checkout_enabled in DB must also be true.
  if (Deno.env.get("BLINK_FLW_CHECKOUT_ENABLED") !== "true") {
    return json({ error: "CHECKOUT_NOT_ENABLED" }, 503);
  }
  try {
    const body = await req.json();
    const tier = String(body.tier || "").toUpperCase();
    if (!["BLUE", "GOLD"].includes(tier)) return json({ error: "INVALID_TIER" }, 400);

    // Run the RPC as the user so auth.uid() enforces ownership and server-side price.
    const caller = createClient(env("SUPABASE_URL"), env("SUPABASE_ANON_KEY"), {
      global: { headers: { Authorization: req.headers.get("Authorization")! } },
      auth: { autoRefreshToken: false, persistSession: false },
    });
    const { data: order, error: orderError } = await caller.rpc(
      "create_blink_flutterwave_verification_order", { p_tier: tier }
    );
    if (orderError || !order) {
      return json({ error: orderError?.message || "CHECKOUT_NOT_AVAILABLE" }, 400);
    }

    const payment = await flutterwaveRequest("payments", {
      method: "POST",
      body: JSON.stringify({
        tx_ref: order.tx_ref,
        amount: order.amount_ngn,
        currency: "NGN",
        redirect_url: "https://blink.com.ng/?blink_payment_return=1",
        customer: { email: user.email },
        customizations: {
          title: "BLINK Verification",
          description: `${tier === "GOLD" ? "Gold" : "Blue"} verification — ${order.duration_days} days`,
        },
      }),
    });
    if (!payment || typeof payment.link !== "string" ||
        !payment.link.startsWith("https://checkout.flutterwave.com/")) {
      return json({ error: "PROVIDER_CHECKOUT_UNAVAILABLE" }, 502);
    }
    return json({
      checkoutUrl: payment.link,
      reference: order.tx_ref,
      amountNgn: order.amount_ngn,
      currency: "NGN",
      tier,
    });
  } catch (error) {
    console.error("flutterwave-checkout:", String(error).slice(0,160));
    return json({ error: "CHECKOUT_UNAVAILABLE" }, 503);
  }
});
