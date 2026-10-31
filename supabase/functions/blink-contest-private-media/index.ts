/**
 * Contest entry-media gate.
 * Public media redirects ONLY after server-side approval. Pending/rejected
 * entries require the applicant's session or a verified Contest administrator.
 * No object bytes, JWT or privileged key are emitted by this function.
 */
function firstJsonKey(name: string): string {
  try {
    const values = JSON.parse(Deno.env.get(name) || "{}") as Record<string, string>;
    return values.default || Object.values(values).find(Boolean) || "";
  } catch { return ""; }
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const FILE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|jpeg|png|webp|mp4)$/i;
const securityHeaders = {
  "Cache-Control": "private, no-store, max-age=0",
  "Referrer-Policy": "no-referrer",
  "X-Content-Type-Options": "nosniff",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "Authorization, apikey",
};

function jsonError(status: number): Response {
  // Deliberately indistinguishable responses for private/nonexistent objects.
  return new Response(JSON.stringify({ error: status === 404 ? "Media unavailable" : "Forbidden" }), {
    status, headers: { ...securityHeaders, "Content-Type": "application/json" },
  });
}

async function api(url: string, key: string, userToken?: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set("apikey", key);
  headers.set("Accept", "application/json");
  if (userToken) headers.set("Authorization", "Bearer " + userToken);
  else if (!key.startsWith("sb_")) headers.set("Authorization", "Bearer " + key);
  return fetch(url, { ...init, headers, redirect: "manual" });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: securityHeaders });
  if (req.method !== "GET") return jsonError(404);
  const base = (Deno.env.get("SUPABASE_URL") || "").replace(/\/$/, "");
  const secret = firstJsonKey("SUPABASE_SECRET_KEYS") || Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  const publishable = firstJsonKey("SUPABASE_PUBLISHABLE_KEYS") || Deno.env.get("SUPABASE_ANON_KEY") || "";
  if (!base || !secret || !publishable) return jsonError(404);

  const url = new URL(req.url);
  const marker = "/functions/v1/blink-contest-private-media/";
  if (!url.pathname.startsWith(marker)) return jsonError(404);
  const chunks = url.pathname.slice(marker.length).split("/");
  if (chunks.length !== 2 || !UUID.test(chunks[0]) || !FILE.test(chunks[1])) return jsonError(404);
  const path = chunks.join("/");
  const canonical = base + marker + path;
  const lookupUrl = base + "/rest/v1/blink_contest_entries?select=id,user_id,status&media_url=eq." +
    encodeURIComponent(canonical) + "&limit=1";
  try {
    const record = await api(lookupUrl, secret);
    if (!record.ok) return jsonError(404);
    const entries = await record.json() as Array<{ id: string; user_id: string; status: string }>;
    const entry = entries[0];
    if (!entry || entry.user_id.toLowerCase() !== chunks[0].toLowerCase()) return jsonError(404);

    if (entry.status !== "approved") {
      const authorization = req.headers.get("Authorization") || "";
      const jwt = /^Bearer\s+([^\s]+)$/i.exec(authorization)?.[1] || "";
      if (!jwt || jwt.startsWith("sb_")) return jsonError(404);
      const userResponse = await api(base + "/auth/v1/user", publishable, jwt);
      if (!userResponse.ok) return jsonError(404);
      const user = await userResponse.json() as { id?: string };
      if (!user.id || (user.id !== entry.user_id)) {
        // This RPC checks private.require_blink_admin() using the USER JWT.
        // A successful service-role lookup alone never authorizes moderation.
        const authorized = await api(base + "/rest/v1/rpc/admin_list_blink_contests",
          publishable, jwt, { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" });
        if (!authorized.ok) return jsonError(404);
      }
    }

    // Signed links expire quickly. Never store or log the signed URL.
    const signedResponse = await api(base + "/storage/v1/object/sign/blink-contest-private/" + path,
      secret, undefined, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ expiresIn: 60 }),
      });
    if (!signedResponse.ok) return jsonError(404);
    const signed = await signedResponse.json() as { signedURL?: string; signedUrl?: string };
    const relative = signed.signedURL || signed.signedUrl;
    if (!relative) return jsonError(404);
    // Storage returns a path relative to /storage/v1 on common deployments,
    // and may return an absolute URL. Normalize all variants without rewriting
    // the token, and reject anything outside this project's signed-object path.
    const absoluteSignedUrl = /^https:\/\//i.test(relative) ? relative :
      relative.startsWith("/storage/v1/") ? base + relative :
      base + "/storage/v1/" + relative.replace(/^\//, "");
    const downloadUrl = new URL(absoluteSignedUrl);
    // No open redirect: only this project's signed object endpoint is permitted.
    if (downloadUrl.origin !== new URL(base).origin ||
        !downloadUrl.pathname.includes("/storage/v1/object/sign/blink-contest-private/"))
      return jsonError(404);

    if (url.searchParams.get("format") === "json") {
      return Response.json({ url: downloadUrl.toString(), expires_in: 60 },
        { headers: securityHeaders });
    }
    return new Response(null, { status: 302,
      headers: { ...securityHeaders, Location: downloadUrl.toString() } });
  } catch {
    return jsonError(404);
  }
});
