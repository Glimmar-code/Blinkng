const SHARE_ORIGIN = "https://blink.com.ng";
const PLAY_STORE_URL = "https://blink.com.ng/download/";
const ALLOWED_TYPES = new Set(["profile", "post", "reel"]);

function firstKeyFromJsonEnv(name: string): string {
  const raw = Deno.env.get(name);
  if (!raw) return "";
  try {
    const parsed = JSON.parse(raw) as Record<string, string>;
    return parsed.default || Object.values(parsed).find(Boolean) || "";
  } catch {
    return "";
  }
}

function serviceKey(): string {
  return firstKeyFromJsonEnv("SUPABASE_SECRET_KEYS") || Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
}

function escapeHtml(value: unknown): string {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function cleanText(value: unknown, max = 180): string {
  return String(value ?? "").replace(/\s+/g, " ").trim().slice(0, max);
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function gatewayHeaders(key: string): HeadersInit {
  const headers: Record<string, string> = { apikey: key, Accept: "application/json" };
  if (key && !key.startsWith("sb_")) headers.Authorization = `Bearer ${key}`;
  return headers;
}

async function rest(path: string): Promise<unknown[]> {
  const base = Deno.env.get("SUPABASE_URL") || "";
  const key = serviceKey();
  if (!base || !key) throw new Error("Supabase runtime credentials are unavailable");
  const response = await fetch(`${base}/rest/v1/${path}`, { headers: gatewayHeaders(key) });
  if (!response.ok) throw new Error(`REST lookup failed (${response.status})`);
  return await response.json() as unknown[];
}

function findTypeAndId(url: URL): { type: string; id: string } | null {
  const qType = (url.searchParams.get("type") || "").toLowerCase();
  const qId = (url.searchParams.get("id") || "").trim();
  if (ALLOWED_TYPES.has(qType) && qId) return { type: qType, id: qId };

  // Public profile share links use /@username, not /profile/username.
  if (/^\/@[^/]+\/?$/.test(url.pathname)) {
    return { type: "profile", id: decodeURIComponent(url.pathname.slice(2).replace(/\/$/, "")) };
  }
  const segments = url.pathname.split("/").filter(Boolean).map((v) => decodeURIComponent(v));
  for (let i = 0; i < segments.length - 1; i++) {
    const type = segments[i].toLowerCase();
    const id = segments[i + 1]?.trim() || "";
    if (ALLOWED_TYPES.has(type) && id) return { type, id };
  }
  return null;
}

type Preview = {
  type: string;
  id: string;
  title: string;
  description: string;
  image: string;
  video?: string;
  canonicalUrl: string;
};

async function publicContent(id: string, type: string): Promise<Record<string, unknown> | null> {
  const base = Deno.env.get("SUPABASE_URL") || "";
  const key = serviceKey();
  if (!base || !key) throw new Error("Supabase runtime credentials are unavailable");
  // Reuse the existing authenticated-policy-independent PUBLIC content projection:
  // this RPC excludes inactive, non-Everyone posts AND private authors.
  const response = await fetch(`${base}/rest/v1/rpc/get_public_web_content`, {
    method: "POST",
    headers: { ...gatewayHeaders(key), "Content-Type": "application/json" },
    body: JSON.stringify({ p_content_id: id, p_content_type: type }),
    signal: AbortSignal.timeout(3000),
  });
  if (!response.ok) throw new Error(`Public content lookup failed (${response.status})`);
  const record = await response.json() as Record<string, unknown>;
  return record.available === true ? record : null;
}

async function buildPreview(type: string, id: string): Promise<Preview | null> {
  if (id.length > 128) return null;
  const canonicalUrl = type === "profile"
    ? `${SHARE_ORIGIN}/@${encodeURIComponent(id.replace(/^@+/, ""))}`
    : `${SHARE_ORIGIN}/${type}/${encodeURIComponent(id)}`;

  if (type === "profile") {
    const selector = isUuid(id)
      ? `id=eq.${encodeURIComponent(id)}`
      : `username=eq.${encodeURIComponent(id.replace(/^@+/, ""))}`;
    const rows = await rest(`profiles?${selector}&select=id,username,full_name,avatar_url,bio,professional_headline,university&limit=1`);
    const p = rows[0] as Record<string, unknown> | undefined;
    if (!p) return null;
    // A public link must not reveal private account biography/avatar.
    const privateRows = await rest(`user_settings?user_id=eq.${encodeURIComponent(String(p.id))}&select=private_account&limit=1`);
    if ((privateRows[0] as Record<string, unknown> | undefined)?.private_account === true) return null;
    const username = cleanText(p.username, 60);
    const fullName = cleanText(p.full_name, 90) || username;
    const detail = cleanText(p.professional_headline, 180) || cleanText(p.bio, 180) || cleanText(p.university, 180);
    return {
      type, id: String(p.id || id),
      title: `${fullName}${username ? ` (@${username})` : ""} on Blink`,
      description: detail || `View ${fullName}'s profile on Blink.`,
      image: String(p.avatar_url || ""),
      canonicalUrl,
    };
  }

  if (!isUuid(id)) return null;
  const result = await publicContent(id, type);
  if (!result) return null;
  const content = (result.content || {}) as Record<string, unknown>;
  const author = (content.author || {}) as Record<string, unknown>;
  const username = cleanText(author.username, 60);
  const caption = cleanText(content.text, 180) || cleanText(content.caption, 180);
  const images = Array.isArray(content.imageUrls) ? content.imageUrls.filter(Boolean).map(String) : [];
  const video = type === "reel" ? String(content.videoUrl || "") : "";
  return {
    type, id,
    title: `${type === "reel" ? "Reel" : "Post"}${username ? ` by @${username}` : ""} on Blink`,
    description: caption || `Open this ${type} on Blink.`,
    image: String(images[0] || author.avatarUrl || ""),
    video: video || undefined,
    canonicalUrl,
  };
}

function html(preview: Preview | null, requestedUrl: string): string {
  const p = preview || {
    type: "content",
    id: "",
    title: "Open on Blink",
    description: "See this content in the Blink app.",
    image: "",
    canonicalUrl: requestedUrl,
  };
  const imageMeta = p.image ? `<meta property="og:image" content="${escapeHtml(p.image)}"><meta name="twitter:image" content="${escapeHtml(p.image)}">` : "";
  const videoMeta = p.video ? `<meta property="og:video" content="${escapeHtml(p.video)}"><meta property="og:video:type" content="video/mp4">` : "";
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escapeHtml(p.title)}</title>
<meta name="description" content="${escapeHtml(p.description)}">
<meta property="og:type" content="website"><meta property="og:site_name" content="Blink">
<meta property="og:title" content="${escapeHtml(p.title)}"><meta property="og:description" content="${escapeHtml(p.description)}">
<meta property="og:url" content="${escapeHtml(p.canonicalUrl)}">${imageMeta}${videoMeta}
<meta name="twitter:card" content="summary_large_image"><meta name="twitter:title" content="${escapeHtml(p.title)}"><meta name="twitter:description" content="${escapeHtml(p.description)}">
<style>body{font-family:system-ui,-apple-system,sans-serif;margin:0;background:#0d0d12;color:#fff;display:grid;place-items:center;min-height:100vh}.card{width:min(92vw,520px);background:#181820;border:1px solid #2d2d38;border-radius:24px;overflow:hidden;box-shadow:0 20px 70px #0008}.hero{width:100%;aspect-ratio:16/9;object-fit:cover;background:#24242f}.body{padding:24px}.muted{color:#b8b8c7;line-height:1.5}.actions{display:flex;gap:10px;flex-wrap:wrap;margin-top:20px}a{padding:12px 16px;border-radius:14px;text-decoration:none;font-weight:700}.primary{background:#8b5cf6;color:white}.secondary{background:#2a2a35;color:white}</style></head>
<body><main class="card">${p.image ? `<img class="hero" src="${escapeHtml(p.image)}" alt="Preview">` : ""}<section class="body"><h1>${escapeHtml(p.title)}</h1><p class="muted">${escapeHtml(p.description)}</p><div class="actions"><a class="primary" href="${escapeHtml(p.canonicalUrl)}">Open in Blink</a><a class="secondary" href="${PLAY_STORE_URL}">Get Blink</a></div></section></main></body></html>`;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "GET" && req.method !== "HEAD") return new Response("Method not allowed", { status: 405 });
  const url = new URL(req.url);
  const parsed = findTypeAndId(url);
  if (!parsed) return new Response("Invalid Blink share link", { status: 400 });

  try {
    const preview = await buildPreview(parsed.type, parsed.id);
    if (url.searchParams.get("format") === "json") {
      return Response.json(preview || { available: false }, {
        status: preview ? 200 : 404,
        headers: { "Cache-Control": "public, max-age=60, s-maxage=300" },
      });
    }
    return new Response(html(preview, `${SHARE_ORIGIN}/${parsed.type}/${encodeURIComponent(parsed.id)}`), {
      status: preview ? 200 : 404,
      headers: {
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "public, max-age=60, s-maxage=300",
        "X-Content-Type-Options": "nosniff",
      },
    });
  } catch (error) {
    console.error("share-preview error", error);
    if (url.searchParams.get("format") === "json") {
      return Response.json({ error: "Preview temporarily unavailable" }, { status: 503 });
    }
    return new Response(html(null, `${SHARE_ORIGIN}/${parsed.type}/${encodeURIComponent(parsed.id)}`), {
      status: 503,
      headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" },
    });
  }
});
