import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
});
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const HEX_TOKEN = /^[a-f0-9]{64,512}$/;
const MAX_BYTES = 2048;

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "POST required" }, 405);
  // Server feature gate is independent from the signed-app feature gate.
  // Never accept or store tokens until the owner configures Apple delivery.
  if (Deno.env.get("BLINK_IOS_VOIP_SERVER_ENABLED") !== "true")
    return json({ error: "iOS VoIP registration is not enabled" }, 503);

  const url = Deno.env.get("SUPABASE_URL");
  const secret = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const bundle = Deno.env.get("BLINK_IOS_APNS_BUNDLE_ID");
  const environment = Deno.env.get("BLINK_IOS_APNS_ENVIRONMENT");
  if (!url || !secret || !bundle || !["sandbox", "production"].includes(environment ?? "")) {
    return json({ error: "iOS VoIP backend is not configured" }, 503);
  }

  const authorization = request.headers.get("Authorization") ?? "";
  const jwt = authorization.match(/^Bearer\s+([^\s]+)$/i)?.[1];
  if (!jwt || jwt.length > 8192) return json({ error: "Authentication required" }, 401);

  // Auth user is verified against Supabase Auth, not derived by decoding an
  // unverified JWT claim or trusting user_id in a client-supplied JSON body.
  const admin = createClient(url, secret, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: { user }, error: authError } = await admin.auth.getUser(jwt);
  if (authError || !user || user.is_anonymous === true)
    return json({ error: "Authentication required" }, 401);

  const contentLength = Number(request.headers.get("Content-Length") ?? "0");
  if (contentLength > MAX_BYTES) return json({ error: "Invalid request" }, 413);
  let payload: Record<string, unknown>;
  try {
    const raw = await request.text();
    if (raw.length > MAX_BYTES) return json({ error: "Invalid request" }, 413);
    payload = JSON.parse(raw);
    if (!payload || typeof payload !== "object" || Array.isArray(payload))
      return json({ error: "Invalid request" }, 400);
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }
  const deviceId = String(payload.device_id ?? "");
  const channel = String(payload.channel ?? "");
  const platform = String(payload.platform ?? "");
  const submittedBundle = String(payload.bundle_id ?? "");
  const submittedEnvironment = String(payload.environment ?? "");
  const action = String(payload.action ?? "register");
  if (!UUID.test(deviceId) || channel !== "voip" || platform !== "ios" ||
      submittedBundle !== bundle || submittedEnvironment !== environment ||
      !["register", "revoke"].includes(action)) {
    return json({ error: "Invalid registration" }, 400);
  }

  if (action === "revoke") {
    // A user can revoke only their own device token. Never revoke by token
    // supplied by a different user, and never revoke another user's device.
    const { error } = await admin.from("ios_voip_tokens").update({
      is_active: false, revoked_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }).eq("user_id", user.id)
      .eq("device_id", deviceId).eq("bundle_id", bundle)
      .eq("environment", environment);
    if (error) return json({ error: "Revocation unavailable" }, 503);
    return json({ ok: true, revoked: true });
  }

  const token = String(payload.token ?? "").toLowerCase();
  if (!HEX_TOKEN.test(token)) return json({ error: "Invalid APNs token" }, 400);

  const { data: existing, error: lookupError } = await admin
    .from("ios_voip_tokens")
    .select("id,user_id")
    .eq("device_id", deviceId)
    .eq("bundle_id", bundle)
    .eq("environment", environment)
    .maybeSingle();
  if (lookupError) return json({ error: "Registration unavailable" }, 503);
  if (existing && existing.user_id !== user.id) {
    // Require the previous owner to revoke before account switching.
    return json({ error: "Device account switching requires token revocation" }, 409);
  }

  // Replaying a stolen token cannot move another user's APNs registration.
  const { data: tokenOwner, error: ownerError } = await admin
    .from("ios_voip_tokens")
    .select("user_id,device_id")
    .eq("bundle_id", bundle)
    .eq("environment", environment)
    .eq("token", token)
    .maybeSingle();
  if (ownerError) return json({ error: "Registration unavailable" }, 503);
  if (tokenOwner && (tokenOwner.user_id !== user.id || tokenOwner.device_id !== deviceId)) {
    return json({ error: "APNs registration conflict" }, 409);
  }
  // Restrict registered devices per user to prevent unbounded APNs fanout.
  const { count, error: countError } = await admin
    .from("ios_voip_tokens")
    .select("id", { head: true, count: "exact" })
    .eq("user_id", user.id).eq("bundle_id", bundle)
    .eq("environment", environment).eq("is_active", true);
  if (countError) return json({ error: "Registration unavailable" }, 503);
  if (!existing && (count ?? 0) >= 6) return json({ error: "Device limit reached" }, 429);

  const now = new Date().toISOString();
  const { error: writeError } = await admin.from("ios_voip_tokens").upsert({
    device_id: deviceId, user_id: user.id, bundle_id: bundle,
    environment, token, is_active: true, revoked_at: null,
    updated_at: now, last_registered_at: now,
  }, { onConflict: "device_id,bundle_id,environment" });
  if (writeError) return json({ error: "Registration unavailable" }, 503);
  return json({ ok: true, registered: true });
});
