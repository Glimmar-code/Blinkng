/**
 * Public, read-only metadata for links shown inside BLINK.
 * SSRF policy: outbound HTML fetches are restricted to exact, curated public
 * origins. Unknown hosts return a safe hostname card WITHOUT a network fetch.
 * Never forward cookies, Authorization headers, redirects or arbitrary ports.
 */
const allowedHosts = new Set([
  "blink.com.ng", "www.blink.com.ng", "youtube.com", "www.youtube.com",
  "m.youtube.com", "youtu.be", "github.com", "www.github.com",
  "en.wikipedia.org", "wikipedia.org", "vimeo.com", "www.vimeo.com",
  "reddit.com", "www.reddit.com", "open.spotify.com", "soundcloud.com",
  "www.soundcloud.com", "x.com", "twitter.com", "www.instagram.com",
  "www.tiktok.com", "medium.com"
]);
const cache = new Map<string, { expires: number; value: Preview }>();
const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "content-type", "X-Content-Type-Options": "nosniff" };
type Preview = { url: string; provider: string; title: string; description: string; image: string };
const clean = (value: string, max: number) =>
  value.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim().slice(0, max);
const decode = (s: string) => s
  .replace(/&amp;/gi, "&").replace(/&quot;/gi, '"').replace(/&#39;|&apos;/gi, "'")
  .replace(/&lt;/gi, "<").replace(/&gt;/gi, ">")
  .replace(/&#(\d+);/g, (_, code) => String.fromCodePoint(Math.min(0x10ffff, Number(code))));
export const allowedUrl = (raw: string): URL | null => {
  if (raw.length < 10 || raw.length > 2048) return null;
  try {
    const url = new URL(raw);
    if (url.protocol !== "https:" || url.username || url.password || url.port) return null;
    const host = url.hostname.toLowerCase();
    if (!host || !host.includes(".") || /[\[\]]/.test(host)) return null;
    return url;
  } catch { return null; }
};
function extract(html: string, page: URL): Partial<Preview> {
  const metas = new Map<string, string>();
  for (const tag of html.match(/<meta\b[^>]*>/gi) || []) {
    const attributes: Record<string, string> = {};
    const attr = /([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/g;
    for (const match of tag.matchAll(attr))
      attributes[match[1].toLowerCase()] = match[2] ?? match[3] ?? match[4] ?? "";
    const key = (attributes.property || attributes.name || "").toLowerCase();
    if (key && attributes.content) metas.set(key, decode(attributes.content));
  }
  const rawImage = metas.get("og:image") || metas.get("twitter:image") || "";
  const resolvedImage = rawImage ? allowedUrl(new URL(rawImage, page).href) : null;
  const documentTitle = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1] || "";
  return {
    title: clean(decode(metas.get("og:title") || metas.get("twitter:title") || documentTitle), 160),
    description: clean(decode(metas.get("og:description") || metas.get("twitter:description") ||
      metas.get("description") || ""), 240),
    image: resolvedImage?.href || ""
  };
}
export async function publicPreview(url: URL): Promise<Preview> {
  const host = url.hostname.toLowerCase();
  const fallback: Preview = { url: url.href, provider: host, title: host, description: url.href.slice(0, 220), image: "" };
  if (!allowedHosts.has(host)) return fallback;
  // BLINK URLs use the existing visibility-filtered public content lookup.
  // Do not scrape private, removed or followers-only posts from the database.
  if (host === "blink.com.ng" || host === "www.blink.com.ng") {
    const post = url.pathname.match(/^\/(post|reel|market)\/([0-9a-f-]{36})\/?$/i);
    const profile = url.pathname.match(/^\/@([a-z0-9._-]{2,30})\/?$/i);
    const type = post?.[1]?.toLowerCase() || (profile ? "profile" : "");
    const id = post?.[2] || profile?.[1] || "";
    const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
    if (type && id && supabaseUrl) {
      try {
        const api = new URL("/functions/v1/share-preview", supabaseUrl);
        api.searchParams.set("format", "json");
        api.searchParams.set("type", type);
        api.searchParams.set("id", id);
        const result = await fetch(api, { redirect: "manual", signal: AbortSignal.timeout(3000) });
        if (result.ok) {
          const preview = await result.json() as Record<string, unknown>;
          return {
            ...fallback, title: clean(String(preview.title || host), 160),
            description: clean(String(preview.description || ""), 240),
            image: allowedUrl(String(preview.image || ""))?.href || ""
          };
        }
        // A private or removed BLINK entity never falls through to public page scraping.
        return fallback;
      } catch { return fallback; }
    }
  }
  try {
    const response = await fetch(url, {
      method: "GET", redirect: "manual", signal: AbortSignal.timeout(3500),
      headers: { "Accept": "text/html", "User-Agent": "BLINK-Link-Preview/1.0" }
    });
    if (!response.ok || !response.headers.get("content-type")?.toLowerCase().includes("text/html")) {
      response.body?.cancel().catch(() => {});
      return fallback;
    }
    const reader = response.body?.getReader();
    if (!reader) return fallback;
    const bytes: Uint8Array[] = [];
    let size = 0;
    try {
      while (size < 128 * 1024) {
        const next = await reader.read();
        if (next.done) break;
        const chunk = next.value.slice(0, Math.max(0, 128 * 1024 - size));
        bytes.push(chunk);
        size += chunk.byteLength;
      }
    } finally { await reader.cancel().catch(() => {}); }
    const buffer = new Uint8Array(size);
    let offset = 0;
    for (const chunk of bytes) { buffer.set(chunk, offset); offset += chunk.length; }
    const metadata = extract(new TextDecoder().decode(buffer), url);
    return { ...fallback, ...metadata, title: metadata.title || host, description: metadata.description || fallback.description };
  } catch { return fallback; }
}
export const handleLinkMetadata = async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
  if (req.method !== "GET") return new Response("Method not allowed", { status: 405, headers: cors });
  const query = new URL(req.url).searchParams.get("url") || "";
  const url = allowedUrl(query);
  if (!url) return Response.json({ error: "Invalid HTTPS URL" }, { status: 400, headers: cors });
  // Do not fetch or disclose signed private resources, access tokens or fragments.
  if ([...url.searchParams.keys()].some(key => /token|secret|key|auth|code|signature|session/i.test(key))) {
    return Response.json({ url: url.origin, provider: url.hostname, title: url.hostname,
      description: "Private link", image: "" }, { headers: cors });
  }
  url.hash = "";
  const now = Date.now();
  const found = cache.get(url.href);
  if (found && found.expires > now) return Response.json(found.value,
    { headers: { ...cors, "Cache-Control": "public, max-age=600" } });
  const value = await publicPreview(url);
  if (cache.size > 200) cache.delete(cache.keys().next().value!);
  cache.set(url.href, { expires: now + 600000, value });
  return Response.json(value, { headers: { ...cors, "Cache-Control": "public, max-age=600" } });
};

if (Deno.env.get("BLINK_LINK_METADATA_TEST") !== "1") {
  Deno.serve(handleLinkMetadata);
}
