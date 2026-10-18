import { authenticatedUser, json, reconcilePaystackOrder, responseHeaders } from "../_shared/blink_paystack.ts";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { headers:responseHeaders });
  if (req.method !== "POST") return json({ error:"METHOD_NOT_ALLOWED" },405);
  const user = await authenticatedUser(req);
  if (!user) return json({ error:"AUTH_REQUIRED" },401);
  try {
    const { reference } = await req.json();
    if (typeof reference !== "string") return json({ error:"REFERENCE_REQUIRED" },400);
    return json(await reconcilePaystackOrder(reference,user.id));
  } catch(err) {
    const message=String(err);
    if (message.includes("ORDER_NOT_FOUND") || message.includes("INVALID_REFERENCE")) {
      return json({ error:"PAYMENT_NOT_FOUND" },404);
    }
    console.error("paystack-confirm",message.slice(0,140));
    return json({ error:"CONFIRMATION_UNAVAILABLE" },503);
  }
});
