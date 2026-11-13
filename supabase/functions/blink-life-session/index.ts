/**
 * BLINK LIFE dedicated-server join ticket issuer.
 *
 * Identity comes only from a live Supabase Auth access token; request data never
 * chooses the account ID. The ticket contains no access token and does not
 * change balances, houses, auth identities, wallets, or game ownership records.
 * Keep verify_jwt=true in supabase/config.toml and deploy only with the
 * dedicated Godot host and matching server secret configured.
 */
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "Authorization, apikey, Content-Type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Cache-Control": "no-store, max-age=0",
};
const encoder = new TextEncoder();
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

function answer(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function validEndpoint(host: string, port: number): boolean {
  if (!host || host.length > 253 || !Number.isInteger(port) || port < 1024 || port > 65535) {
    return false;
  }
  // Reject malformed DNS labels (including leading/trailing hyphens) instead
  // of returning a ticket for a host clients cannot safely resolve.
  return host.split(".").every((label) =>
    label.length >= 1 && label.length <= 63 &&
    /^[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?$/.test(label)
  );
}

function authUserEndpoint(rawUrl: string): URL | null {
  try {
    const base = new URL(rawUrl);
    if (base.protocol !== "https:" || base.username || base.password ||
        base.search || base.hash || base.pathname !== "/") {
      return null;
    }
    return new URL("/auth/v1/user", base.origin);
  } catch {
    return null;
  }
}

function toLowerHex(bytes: Uint8Array): string {
  return Array.from(bytes, (part) => part.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders });
  }
  if (req.method !== "POST") return answer({ error: "METHOD_NOT_ALLOWED" }, 405);

  const host = Deno.env.get("BLINK_LIFE_PUBLIC_HOST")?.trim() ?? "";
  const rawPort = Deno.env.get("BLINK_LIFE_PUBLIC_PORT")?.trim() ?? "";
  const port = /^[0-9]{1,5}$/.test(rawPort) ? Number(rawPort) : 0;
  const secret = Deno.env.get("BLINK_LIFE_AUTH_SECRET") ?? "";
  const authEndpoint = authUserEndpoint(Deno.env.get("SUPABASE_URL")?.trim() ?? "");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

  // Fail closed: never fabricate a demo/offline ticket when infrastructure or
  // the signing key is absent. The signing secret is never included in output.
  if (encoder.encode(secret).length < 32 || !validEndpoint(host, port) ||
      !authEndpoint || !anonKey) {
    return answer({ error: "GAME_SERVER_NOT_READY" }, 503);
  }

  const authorization = req.headers.get("authorization") ?? "";
  const bearer = authorization.match(/^Bearer ([^\s]+)$/i)?.[1] ?? "";
  if (!bearer || bearer.length > 4096) {
    return answer({ error: "AUTH_REQUIRED" }, 401);
  }

  try {
    // Ask Supabase Auth to validate signature, expiry, revocation and account
    // status. Only the publishable/anon key is used here; never use service_role.
    const response = await fetch(authEndpoint, {
      method: "GET",
      headers: { Authorization: `Bearer ${bearer}`, apikey: anonKey },
      signal: AbortSignal.timeout(4000),
    });
    if (response.status === 401 || response.status === 403) {
      return answer({ error: "INVALID_SESSION" }, 401);
    }
    if (!response.ok) return answer({ error: "SESSION_ISSUER_UNAVAILABLE" }, 503);

    const account: unknown = await response.json();
    const rawId = account && typeof account === "object" && !Array.isArray(account)
      ? (account as Record<string, unknown>).id
      : null;
    const userId = typeof rawId === "string" ? rawId.toLowerCase() : "";
    if (!uuidPattern.test(userId)) return answer({ error: "INVALID_SESSION" }, 401);

    // Contract consumed by game3d/scripts/ticket_auth.gd:
    // uuid.expires_at_epoch_seconds.16-byte-lowercase-hex-nonce.sha256-hmac-hex
    // The dedicated host checks HMAC, expiry, nonce replay and duplicate users.
    const expiresAt = Math.floor(Date.now() / 1000) + 90;
    const nonce = toLowerHex(crypto.getRandomValues(new Uint8Array(16)));
    const unsigned = `${userId}.${expiresAt}.${nonce}`;
    const key = await crypto.subtle.importKey(
      "raw",
      encoder.encode(secret),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["sign"],
    );
    const signature = toLowerHex(new Uint8Array(
      await crypto.subtle.sign("HMAC", key, encoder.encode(unsigned)),
    ));

    return answer({
      host,
      port,
      ticket: `${unsigned}.${signature}`,
      expires_at: expiresAt,
      transport: "enet_udp",
    });
  } catch {
    // Do not log credentials, tokens, ticket contents, or account identifiers.
    return answer({ error: "SESSION_ISSUER_UNAVAILABLE" }, 503);
  }
});
