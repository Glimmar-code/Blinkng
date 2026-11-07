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
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!url || !anonKey || !serviceKey) {
      return new Response(JSON.stringify({ error: "SERVER_CONFIGURATION_ERROR" }), { status: 500, headers });
    }

    // Match the Admin Control Center's authoritative admin_roles capability check.
    // The user's own JWT is used for this check; user-editable metadata is never trusted.
    const userClient = createClient(url, anonKey, {
      global: { headers: { Authorization: `Bearer ${token}` } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: { user }, error: authError } = await userClient.auth.getUser(token);
    if (authError || !user) {
      return new Response(JSON.stringify({ error: "AUTHENTICATION_REQUIRED" }), { status: 401, headers });
    }
    const { data: capability, error: capabilityError } = await userClient.rpc("admin_get_capability");
    if (capabilityError || capability?.is_admin !== true) {
      return new Response(JSON.stringify({ error: "NOT_AUTHORIZED" }), { status: 403, headers });
    }

    // This service-role client is created only after the caller passes the admin capability gate.
    const adminClient = createClient(url, serviceKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });

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
