import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const RAW_FRONTEND = "https://raw.githubusercontent.com/Glimmar-code/Blinkng/main/web/index.html";
const EDGE_BASE = "/functions/v1/blink-web";

Deno.serve(async (req: Request) => {
  if (req.method !== "GET" && req.method !== "HEAD") {
    return new Response("Method not allowed", {
      status: 405,
      headers: { "Allow": "GET, HEAD" },
    });
  }

  try {
    const upstream = await fetch(RAW_FRONTEND, {
      headers: {
        "Accept": "text/html",
        "User-Agent": "Blink-Web-Preview",
      },
      cache: "no-store",
    });
    if (!upstream.ok) throw new Error(`GitHub frontend returned ${upstream.status}`);

    let html = await upstream.text();
    html = html.replace(
      "const PREVIEW_BASE = location.hostname.endsWith('github.io') ? '/Blinkng' : '';",
      `const PREVIEW_BASE = location.hostname.endsWith('github.io') ? '/Blinkng' : (location.pathname.startsWith('${EDGE_BASE}') ? '${EDGE_BASE}' : '');`,
    );

    const headers = new Headers({
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store, max-age=0",
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "strict-origin-when-cross-origin",
      "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
    });

    return new Response(req.method === "HEAD" ? null : html, {
      status: 200,
      headers,
    });
  } catch (error) {
    console.error("blink-web preview failed", error);
    return new Response(
      "<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width'><body style='background:#07070a;color:white;font-family:system-ui;padding:32px'><h1>Blink</h1><p>The preview is temporarily unavailable. Please refresh.</p></body>",
      {
        status: 503,
        headers: {
          "Content-Type": "text/html; charset=utf-8",
          "Cache-Control": "no-store",
        },
      },
    );
  }
});
