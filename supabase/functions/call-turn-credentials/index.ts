import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";
import { verifiedNotificationActor } from "../_shared/push-gates.mjs";

const encoder = new TextEncoder();

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store, max-age=0",
    },
  });
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

async function pseudonymousUserKey(userId: string): Promise<string> {
  const digest = new Uint8Array(
    await crypto.subtle.digest("SHA-256", encoder.encode(userId)),
  );
  return Array.from(digest.slice(0, 12))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

async function coturnCredential(secret: string, username: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-1" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(
    await crypto.subtle.sign("HMAC", key, encoder.encode(username)),
  );
  return bytesToBase64(signature);
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "POST required" }, 405);

  // Defense in depth: credential issuance MUST verify the token's signature and
  // expiry even if a gateway's verify_jwt setting is changed accidentally.
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRole) return json({ error: "Authentication unavailable" }, 503);
  const admin = createClient(supabaseUrl, serviceRole, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const userId = await verifiedNotificationActor(
    req.headers.get("Authorization") ?? "",
    token => admin.auth.getUser(token),
  );
  if (!userId) return json({ error: "Unauthorized" }, 401);

  const sharedSecret = Deno.env.get("TURN_SHARED_SECRET")?.trim() ?? "";
  const urlsRaw = Deno.env.get("TURN_URLS")?.trim() ?? "";
  if (!sharedSecret || !urlsRaw) {
    return json({ error: "TURN is not configured" }, 503);
  }

  const urls = urlsRaw
    .split(",")
    .map((value) => value.trim())
    .filter((value) => value.startsWith("turn:") || value.startsWith("turns:"));
  if (urls.length === 0) return json({ error: "No valid TURN URL is configured" }, 503);

  const configuredTtl = Number(Deno.env.get("TURN_TTL_SECONDS") ?? "600");
  const ttlSeconds = Number.isFinite(configuredTtl)
    ? Math.min(3600, Math.max(120, Math.floor(configuredTtl)))
    : 600;
  const expiresAt = Math.floor(Date.now() / 1000) + ttlSeconds;
  // Do not expose the Supabase profile UUID to the TURN service/logs.
  const relayUserKey = await pseudonymousUserKey(userId);
  const username = `${expiresAt}:${relayUserKey}`;
  const credential = await coturnCredential(sharedSecret, username);

  return json({
    urls,
    username,
    credential,
    expires_at: expiresAt,
    ttl_seconds: ttlSeconds,
  });
});
