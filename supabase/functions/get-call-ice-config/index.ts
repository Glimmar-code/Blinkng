import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const response = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });

const STUN_SERVERS = [
  { urls: ["stun:stun.l.google.com:19302"] },
  { urls: ["stun:stun1.l.google.com:19302"] },
];

const CALL_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return response({ error: "POST required" }, 405);

  const token = (request.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "").trim();
  if (!token) return response({ error: "Authentication required" }, 401);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const urls = (Deno.env.get("BLINK_TURN_URLS") ?? "")
    .split(",").map((value) => value.trim()).filter(Boolean);
  const sharedSecret = Deno.env.get("BLINK_TURN_SHARED_SECRET") ?? "";
  if (!supabaseUrl || !serviceKey) return response({ error: "Service unavailable" }, 503);
  if (!sharedSecret || !urls.length ||
      !urls.every((url) => /^turns?:[^\s]+$/i.test(url))) {
    return response({ error: "TURN relay is not configured" }, 503);
  }

  let callId = "";
  try {
    callId = String((await request.json())?.call_id ?? "").trim();
  } catch {
    return response({ error: "Invalid JSON" }, 400);
  }
  if (!CALL_ID_PATTERN.test(callId)) return response({ error: "Invalid call identifier" }, 400);

  const admin = createClient(supabaseUrl, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // Validate the actual session, not an unverified JWT payload.
  const { data: authData, error: authError } = await admin.auth.getUser(token);
  const userId = authData?.user?.id;
  if (authError || !userId) return response({ error: "Authentication failed" }, 401);

  const { data: call, error: callError } = await admin.from("calls")
    .select("caller_id,callee_id,status")
    .eq("id", callId)
    .maybeSingle();
  if (callError) return response({ error: "Call lookup failed" }, 503);
  if (!call || (call.caller_id !== userId && call.callee_id !== userId)) {
    return response({ error: "Call not found" }, 404);
  }
  if (!["ringing", "connecting", "connected"].includes(String(call.status))) {
    return response({ error: "Call is no longer active" }, 409);
  }

  // Coturn's use-auth-secret protocol: timestamp:user-id, HMAC-SHA1,
  // base64 credential. Master secret stays exclusively in the function's env.
  const expiresAt = Math.floor(Date.now() / 1000) + 3600;
  const username = `${expiresAt}:${userId}`;
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(sharedSecret),
    { name: "HMAC", hash: "SHA-1" }, false, ["sign"],
  );
  const signed = new Uint8Array(await crypto.subtle.sign(
    "HMAC", key, new TextEncoder().encode(username),
  ));
  const credential = btoa(String.fromCharCode(...signed));

  return response({
    ice_servers: [
      ...STUN_SERVERS,
      { urls, username, credential },
    ],
    expires_at: expiresAt,
  });
});
