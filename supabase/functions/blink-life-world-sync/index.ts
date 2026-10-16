/**
 * STAGING ONLY: server-to-server sync for fictional BLINK LIFE property/XP.
 * Never called by an Android client or a browser. Database RPCs are restricted
 * to service_role and accept no caller-submitted amount or score.
 *
 * Request authentication: HMAC-SHA256 over "<unix_seconds>.<raw_json_body>".
 * HMAC secret and service-role key stay ONLY in this private Edge Function and
 * the trusted Godot server. TLS is required end-to-end.
 */
const JSON_HEADERS = { "Content-Type": "application/json", "Cache-Control": "no-store" };
const reply = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: JSON_HEADERS });
const uuid = /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i;
const kinds = new Set(["depot_shift", "buy_house", "buy_vehicle", "buy_furniture", "duel_win"]);

function equalBytes(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let value = 0;
  for (let i = 0; i < a.length; i++) value |= a[i] ^ b[i];
  return value === 0;
}

function fromHex(hex: string): Uint8Array | null {
  if (!/^[0-9a-f]{64}$/i.test(hex)) return null;
  const data = new Uint8Array(32);
  for (let i = 0; i < 32; i++) data[i] = Number.parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  return data;
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return reply({ error: "METHOD_NOT_ALLOWED" }, 405);
  const secret = Deno.env.get("BLINK_LIFE_WORLD_SYNC_HMAC_KEY") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const url = (Deno.env.get("SUPABASE_URL") ?? "").replace(/\/$/, "");
  if (secret.length < 48 || !serviceKey || !url.startsWith("https://")) {
    return reply({ error: "WORLD_SYNC_NOT_READY" }, 503);
  }
  const sent = request.headers.get("x-blink-sent-at") ?? "";
  const signature = fromHex(request.headers.get("x-blink-signature") ?? "");
  if (!/^\d{10,11}$/.test(sent) || !signature ||
      Math.abs(Math.floor(Date.now() / 1000) - Number(sent)) > 30) {
    return reply({ error: "INVALID_SIGNATURE" }, 401);
  }
  if (Number(request.headers.get("content-length") ?? "0") > 4096) {
    return reply({ error: "PAYLOAD_TOO_LARGE" }, 413);
  }
  let body = "";
  try {
    body = await request.text();
    if (body.length > 4096) return reply({ error: "PAYLOAD_TOO_LARGE" }, 413);
    const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret),
      { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
    const digest = new Uint8Array(await crypto.subtle.sign(
      "HMAC", key, new TextEncoder().encode(`${sent}.${body}`)));
    if (!equalBytes(digest, signature)) return reply({ error: "INVALID_SIGNATURE" }, 401);
  } catch {
    return reply({ error: "INVALID_SIGNATURE" }, 401);
  }

  let action: string, args: Record<string, string>;
  try {
    const parsed: unknown = JSON.parse(body);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw Error("object");
    const input = parsed as Record<string, unknown>;
    action = input.action as string;
    if (action === "profile" && typeof input.user_id === "string" && uuid.test(input.user_id)) {
      args = { p_user_id: input.user_id };
    } else if (action === "event" && typeof input.user_id === "string" &&
        typeof input.event_id === "string" && uuid.test(input.user_id) && uuid.test(input.event_id) &&
        typeof input.kind === "string" && kinds.has(input.kind)) {
      args = { p_event_id: input.event_id, p_user_id: input.user_id, p_kind: input.kind };
    } else if (action === "rankings") {
      args = {};
    } else {
      return reply({ error: "INVALID_EVENT" }, 422);
    }
  } catch {
    return reply({ error: "INVALID_JSON" }, 400);
  }

  const rpc = action === "profile" ? "blink_life_stage_profile"
    : action === "event" ? "blink_life_stage_event" : "blink_life_stage_campus_rankings";
  try {
    const response = await fetch(`${url}/rest/v1/rpc/${rpc}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "Authorization": `Bearer ${serviceKey}`,
        apikey: serviceKey, "Cache-Control": "no-store" },
      body: JSON.stringify(args),
      signal: AbortSignal.timeout(5000),
    });
    if (!response.ok) {
      // Never leak internal Postgres function messages, DB schema or credentials.
      return reply({ error: response.status === 404 ? "WORLD_SCHEMA_NOT_DEPLOYED" : "WORLD_STATE_FAILED" },
        response.status === 404 ? 503 : 409);
    }
    return reply({ ok: true, data: await response.json() });
  } catch {
    return reply({ error: "WORLD_STATE_UNAVAILABLE" }, 503);
  }
});
