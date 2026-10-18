import { authenticatedUser, corsHeaders, json, reconcilePayment } from "../_shared/blink_flutterwave.ts";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "METHOD_NOT_ALLOWED" }, 405);
  const user = await authenticatedUser(req);
  if (!user) return json({ error: "AUTHENTICATION_REQUIRED" }, 401);
  try {
    const { reference } = await req.json();
    if (typeof reference !== "string") return json({ error: "REFERENCE_REQUIRED" }, 400);
    const result = await reconcilePayment(reference, user.id);
    return json(result);
  } catch (error) {
    const reason = String(error);
    if (reason.includes("PAYMENT_NOT_FOUND") || reason.includes("INVALID_REFERENCE")) {
      return json({ error: "PAYMENT_NOT_FOUND" }, 404);
    }
    console.error("flutterwave-confirm:", reason.slice(0,160));
    return json({ error: "PAYMENT_CONFIRMATION_UNAVAILABLE" }, 503);
  }
});
