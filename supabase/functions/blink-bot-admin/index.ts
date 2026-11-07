import { createClient } from "npm:@supabase/supabase-js@2";

const allowedOrigins = new Set(["https://blink.com.ng", "https://www.blink.com.ng"]);
const jsonHeaders = (origin: string | null) => ({
  "Content-Type": "application/json",
  "Vary": "Origin",
  ...(origin && allowedOrigins.has(origin) ? { "Access-Control-Allow-Origin": origin } : {}),
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, Apikey, X-Client-Info",
});

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("Origin");
  const headers = jsonHeaders(origin);
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers });
  }
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "METHOD_NOT_ALLOWED" }), { status: 405, headers });
  }

  try {
    const authorization = req.headers.get("Authorization") ?? "";
    const token = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
    if (!token) {
      return new Response(JSON.stringify({ error: "AUTHENTICATION_REQUIRED" }), { status: 401, headers });
    }

    const url = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!url || !serviceKey) {
      return new Response(JSON.stringify({ error: "SERVER_CONFIGURATION_ERROR" }), { status: 500, headers });
    }

    const adminClient = createClient(url, serviceKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: { user }, error: authError } = await adminClient.auth.getUser(token);
    if (authError || !user) {
      return new Response(JSON.stringify({ error: "AUTHENTICATION_REQUIRED" }), { status: 401, headers });
    }

    // Only trusted app_metadata is accepted; user-editable user_metadata is never authorization.
    const isAdmin = user.app_metadata?.admin === true ||
      user.app_metadata?.role === "admin" ||
      user.app_metadata?.role === "developer";
    if (!isAdmin) {
      return new Response(JSON.stringify({ error: "NOT_AUTHORIZED" }), { status: 403, headers });
    }

    const body = await req.json();
    if (!body || typeof body !== "object" || Array.isArray(body)) {
      return new Response(JSON.stringify({ error: "INVALID_INPUT" }), { status: 400, headers });
    }
    const actorId = user.id;
    const reason = typeof body.reason === "string" ? body.reason.trim() : "";
    if (body.action !== "list" && (reason.length < 5 || reason.length > 500)) {
      return new Response(JSON.stringify({ error: "INVALID_REASON", message: "Provide a reason of 5–500 characters." }), { status: 400, headers });
    }

    let result;
    if (body.action === "list") {
      const { data, error } = await adminClient.rpc("blink_test_bot_list");
      if (error) throw error;
      result = { accounts: data ?? [] };
    } else if (body.action === "set_status") {
      if (!Number.isSafeInteger(body.accountId) || typeof body.isActive !== "boolean") {
        return new Response(JSON.stringify({ error: "INVALID_INPUT" }), { status: 400, headers });
      }
      const { data, error } = await adminClient.rpc("blink_test_bot_set_status", {
        p_account_id: body.accountId,
        p_is_active: body.isActive,
        p_actor_id: actorId,
        p_reason: reason,
      });
      if (error) throw error;
      result = { updated: data === true };
    } else if (body.action === "bulk_set_status") {
      if (typeof body.isActive !== "boolean") {
        return new Response(JSON.stringify({ error: "INVALID_INPUT" }), { status: 400, headers });
      }
      const { data, error } = await adminClient.rpc("blink_test_bot_bulk_set_status", {
        p_is_active: body.isActive,
        p_actor_id: actorId,
        p_reason: reason,
      });
      if (error) throw error;
      result = { updatedCount: data ?? 0 };
    } else {
      return new Response(JSON.stringify({ error: "INVALID_ACTION" }), { status: 400, headers });
    }

    return new Response(JSON.stringify({ success: true, ...result }), { status: 200, headers });
  } catch (error) {
    console.error("blink-bot-admin failed", error);
    return new Response(JSON.stringify({ error: "BOT_ADMIN_OPERATION_FAILED" }), { status: 500, headers });
  }
});
