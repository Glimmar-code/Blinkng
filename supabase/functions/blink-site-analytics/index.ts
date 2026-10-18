// BLINK anonymous public website analytics — opt-in at deployment, OFF by default.
// No cookies, identifying event fields, IP logs, user agents, or raw event storage.
const OFFICIAL_ORIGIN = "https://blink.com.ng";
const ALLOWED_PAGES = new Set(["home", "about", "features", "download", "faq", "privacy"]);
const ALLOWED_KINDS = new Set(["view", "download"]);
const CORS = {
  "Access-Control-Allow-Origin": OFFICIAL_ORIGIN,
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Vary": "Origin",
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
};
function secretKey(): string {
  const raw = Deno.env.get("SUPABASE_SECRET_KEYS");
  if(raw) {
    try {
      const parsed = JSON.parse(raw) as Record<string, string>;
      if(typeof parsed.default === "string" && parsed.default) return parsed.default;
    }catch{ /* Legacy key env fallback below */ }
  }
  return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
}
function reply(status:number): Response {
  return new Response(null, { status, headers: CORS });
}
Deno.serve(async (request: Request): Promise<Response> => {
  // Only BLINK's official public marketing origin may submit browser events.
  // Origin is not an authentication mechanism: use the database minute cap too.
  if(request.headers.get("origin")!==OFFICIAL_ORIGIN)return reply(403);
  if(request.method==="OPTIONS")return reply(204);
  if(request.method!=="POST")return reply(405);
  if(Deno.env.get("BLINK_SITE_ANALYTICS_ENABLED")!=="true")return reply(204);
  if(!(request.headers.get("content-type")||"").toLowerCase().startsWith("application/json"))return reply(415);
  const declared=Number(request.headers.get("content-length")||"0");
  if(declared>160)return reply(413);
  try {
    const raw=await request.text();
    if(raw.length>160)return reply(413);
    const parsed=JSON.parse(raw) as Record<string,unknown>;
    if(!parsed || typeof parsed!=="object" || Array.isArray(parsed))return reply(400);
    if(Object.keys(parsed).some(k=>k!=="page"&&k!=="kind"))return reply(400);
    if(typeof parsed.page!=="string"||!ALLOWED_PAGES.has(parsed.page))return reply(400);
    if(typeof parsed.kind!=="string"||!ALLOWED_KINDS.has(parsed.kind))return reply(400);
    const key=secretKey();
    const base=Deno.env.get("SUPABASE_URL");
    if(!key || !base)return reply(503);
    const headers:Record<string,string>={
      apikey:key,
      "Content-Type":"application/json",
      Accept:"application/json",
    };
    if(!key.startsWith("sb_"))headers.Authorization="Bearer "+key;
    const response=await fetch(base+"/rest/v1/rpc/record_blink_site_metric",{
      method:"POST",headers,
      body:JSON.stringify({p_page:parsed.page,p_kind:parsed.kind}),
      signal:AbortSignal.timeout(5000),
    });
    // An accepted or cap-exceeded event both return 204: never echo data.
    return reply(response.ok ? 204 : 503);
  }catch{
    // Avoid logging public request bodies, origins, cookies, IPs or user agents.
    return reply(400);
  }
});
