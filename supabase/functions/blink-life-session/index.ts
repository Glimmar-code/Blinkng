/**
 * BLINK LIFE dedicated server join ticket.
 * This function does not modify balances, houses, auth identities or wallets.
 * Only authenticated BLINK users may request 90-second server tickets.
 * Dedicated world traffic is TLS-encrypted WebSocket (WSS). Plaintext ENet is forbidden.
 * Deploy only after staging checks and a dedicated Godot host are configured.
 */
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "Authorization, apikey, Content-Type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Cache-Control": "no-store",
};
const answer = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });

function validEndpoint(host: string, port: number): boolean {
  if (!host || host.length > 253 || !Number.isInteger(port) || port < 1024 || port > 65535) return false;
  return /^(?!-)[a-zA-Z0-9.-]+(?<!-)$/.test(host) &&
    !host.includes("..") && !host.startsWith(".") && !host.endsWith(".");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });
  if (req.method !== "POST") return answer({ error: "METHOD_NOT_ALLOWED" }, 405);

  const host = Deno.env.get("BLINK_LIFE_PUBLIC_HOST")?.trim() ?? "";
  const port = Number(Deno.env.get("BLINK_LIFE_PUBLIC_PORT") ?? "0");
  const secret = Deno.env.get("BLINK_LIFE_AUTH_SECRET") ?? "";
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim() ?? "";
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

  // Refuse to mint tickets if the server was not explicitly provisioned.
  if (secret.length < 32 || !validEndpoint(host, port) || !supabaseUrl || !anonKey) {
    return answer({ error: "GAME_SERVER_NOT_READY" }, 503);
  }

  const bearer = req.headers.get("authorization") ?? "";
  if (!bearer.startsWith("Bearer ") || bearer.length > 4096) {
    return answer({ error: "AUTH_REQUIRED" }, 401);
  }

  try {
    // The Auth service validates user authenticity and session expiry.
    // A publishable/anon key is okay on this server, never embed service_role.
    const response = await fetch(`${supabaseUrl}/auth/v1/user`, {
      method: "GET",
      headers: {
        Authorization: bearer,
        apikey: anonKey,
      },
      signal: AbortSignal.timeout(4000),
    });
    if (!response.ok) return answer({ error: "INVALID_SESSION" }, 401);
    const account = await response.json();
    const id: string = typeof account.id === "string" ? account.id.toLowerCase() : "";
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id)) {
      return answer({ error: "INVALID_SESSION" }, 401);
    }

    // Ticket format: uuid.expires_at.16-byte-hex-nonce.sha256-hmac-hex
    // All fields are ASCII and the server rejects a reused nonce.
    const expires = Math.floor(Date.now() / 1000) + 90;
    const nonce = Array.from(crypto.getRandomValues(new Uint8Array(16)))
      .map((part) => part.toString(16).padStart(2, "0")).join("");
    const unsigned = `${id}.${expires}.${nonce}`;
    const cryptoKey = await crypto.subtle.importKey(
      "raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" },
      false, ["sign"],
    );
    const signature = Array.from(new Uint8Array(
      await crypto.subtle.sign("HMAC", cryptoKey, new TextEncoder().encode(unsigned)),
    )).map((part) => part.toString(16).padStart(2, "0")).join("");

    return answer({
      host,
      port,
      ticket: `${unsigned}.${signature}`,
      expires_at: expires,
      transport: "wss_tls",
    });
  } catch (_error) {
    return answer({ error: "SESSION_ISSUER_UNAVAILABLE" }, 503);
  }
});
