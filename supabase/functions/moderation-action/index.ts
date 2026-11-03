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

    // Admin status is stored in raw_app_meta_data (user-immutable), set by an existing admin.
    await supabase
      .from("profiles")
      .select("verification_badge, is_seller_active")
      .eq("id", user.id)
      .maybeSingle();

    const { data: userData } = await supabase.auth.admin.getUserById(user.id);
    const isAdmin = userData?.user?.app_metadata?.admin === true;

    if (!isAdmin) {
      return new Response(JSON.stringify({ error: "NOT_AUTHORIZED", message: "Admin access required" }), {
        status: 403,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const body = await req.json();
    const { reportId, action, notes } = body;

    if (!reportId || !action) {
      return new Response(JSON.stringify({ error: "VALIDATION_ERROR", message: "reportId and action required" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const validActions = ["reviewed", "actioned", "dismissed"];
    if (!validActions.includes(action)) {
      return new Response(JSON.stringify({ error: "VALIDATION_ERROR", message: "Invalid action" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { data: report, error: reportError } = await supabase
      .from("reports")
      .update({
        status: action,
        reviewed_at: new Date().toISOString(),
        reviewed_by: user.id,
      })
      .eq("id", reportId)
      .select()
      .single();

    if (reportError) {
      return new Response(JSON.stringify({ error: "DATABASE_ERROR", message: reportError.message }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ success: true, report }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: "INTERNAL_ERROR", message: err.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
