import "jsr:@supabase/functions-js/edge-runtime.d.ts";

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

function jwtSubject(authHeader: string): string | null {
  try {
    const token = authHeader.replace(/^Bearer\s+/i, "");
    const payload = token.split(".")[1];
    if (!payload) return null;
    const normalized = payload.replaceAll("-", "+").replaceAll("_", "/");
    const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
    return JSON.parse(atob(padded)).sub ?? null;
  } catch {
    return null;
  }
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
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

  // Supabase's function gateway verifies the JWT for this function. The subject is only
  // used to bind temporary TURN credentials to one authenticated BLINK account.
  const userId = jwtSubject(req.headers.get("Authorization") ?? "");
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
  const username = `${expiresAt}:${userId}`;
  const credential = await coturnCredential(sharedSecret, username);

  return json({
    urls,
    username,
    credential,
    expires_at: expiresAt,
    ttl_seconds: ttlSeconds,
  });
});
