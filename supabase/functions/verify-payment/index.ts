import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Client-Info, Apikey",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 200, headers: corsHeaders });
  }

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "AUTHENTICATION_REQUIRED" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { data: { user }, error: authError } = await supabase.auth.getUser(authHeader.replace("Bearer ", ""));
    if (authError || !user) {
      return new Response(JSON.stringify({ error: "AUTHENTICATION_REQUIRED" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const body = await req.json();
    const { verificationRequestId, provider, providerReference, amount, currency } = body;

    if (!verificationRequestId || !provider || typeof amount !== "number") {
      return new Response(JSON.stringify({ error: "VALIDATION_ERROR", message: "verificationRequestId, provider, amount required" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { data: verificationRequest, error: vrError } = await supabase
      .from("verification_requests")
      .select("id, user_id, status")
      .eq("id", verificationRequestId)
      .eq("user_id", user.id)
      .maybeSingle();

    if (vrError || !verificationRequest) {
      return new Response(JSON.stringify({ error: "NOT_FOUND", message: "Verification request not found" }), {
        status: 404,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { data: payment, error: paymentError } = await supabase
      .from("verification_payments")
      .insert({
        verification_request_id: verificationRequestId,
        user_id: user.id,
        provider,
        provider_reference: providerReference || null,
        amount,
        currency: currency || "NGN",
        status: "pending",
      })
      .select()
      .single();

    if (paymentError) {
      return new Response(JSON.stringify({ error: "DATABASE_ERROR", message: paymentError.message }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Payment does not auto-verify the user. Verification approval remains a separate admin operation.
    return new Response(JSON.stringify({
      success: true,
      paymentId: payment.id,
      status: "pending",
      message: "Payment recorded. Verification approval is a separate admin review step.",
    }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: "INTERNAL_ERROR", message: err.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
