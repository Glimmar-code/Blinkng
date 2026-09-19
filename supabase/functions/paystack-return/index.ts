const escapeHtml = (value: string) =>
  value.replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  }[c] ?? c));

Deno.serve((req: Request) => {
  if (req.method !== "GET" && req.method !== "HEAD") {
    return new Response("Method not allowed", { status: 405, headers: { Allow: "GET, HEAD" } });
  }

  const url = new URL(req.url);
  const reference = escapeHtml(url.searchParams.get("reference") ?? url.searchParams.get("trxref") ?? "");
  const body = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="dark">
<title>BLINK payment</title>
<style>
body{margin:0;min-height:100vh;display:grid;place-items:center;background:#050505;color:#fff;font-family:Inter,system-ui,sans-serif}
main{width:min(92vw,520px);padding:32px;border:1px solid #27272a;border-radius:24px;background:#0b0b0d;box-shadow:0 24px 80px #0008}
.mark{width:52px;height:52px;border-radius:16px;display:grid;place-items:center;background:#fff;color:#000;font-weight:900;font-size:24px}
h1{margin:20px 0 8px;font-size:28px}p{color:#b4b4bd;line-height:1.55}.ref{word-break:break-all;font-size:12px;color:#85858f}
a{display:inline-block;margin-top:18px;padding:13px 18px;border-radius:999px;background:#fff;color:#000;text-decoration:none;font-weight:800}
</style>
</head>
<body><main><div class="mark">B</div><h1>Payment submitted</h1>
<p>Return to BLINK. The app will confirm the transaction with Paystack before any coins or verification are delivered.</p>
${reference ? `<p class="ref">Reference: ${reference}</p>` : ""}
<a href="https://www.blink.com.ng">Back to BLINK</a></main></body></html>`;

  return new Response(req.method === "HEAD" ? null : body, {
    status: 200,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "no-referrer",
    },
  });
});
