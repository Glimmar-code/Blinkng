import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

// This endpoint only disables social email consent. It never authenticates users.
const FIELDS: Record<string, string> = {
  new_follower: "new_follower_enabled",
  unread_messages: "unread_messages_enabled",
  post_engagement: "post_engagement_enabled",
  reel_milestone: "reel_milestone_enabled",
  leaderboard: "leaderboard_enabled",
};

const securityHeaders = {
  "Content-Type": "text/html; charset=utf-8",
  "Cache-Control": "no-store, max-age=0",
  "Content-Security-Policy": "default-src 'none'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
  "Referrer-Policy": "no-referrer",
  "X-Content-Type-Options": "nosniff",
};

function page(title: string, content: string, status = 200) {
  return new Response(`<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>BLINK | ${title}</title></head>
<body style="background:#000;color:#fff;font-family:Arial,sans-serif;padding:30px">
<main style="max-width:480px;margin:24px auto;padding:24px;border:1px solid #333;border-radius:20px">
<h1 style="margin:0">BLINK</h1><h2>${title}</h2>${content}
<p style="color:#aaa;font-size:13px">Need help? blinksupport.ng@gmail.com</p></main></body></html>`, {
    status, headers: securityHeaders
  });
}

function b64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

async function verify(raw: string): Promise<{ userId: string; kind: string } | null> {
  const m = /^([0-9a-f]{8}-[0-9a-f-]{27,36})\.(new_follower|unread_messages|post_engagement|reel_milestone|leaderboard)\.(\d{10})\.([A-Za-z0-9_-]{43})$/.exec(raw);
  if (!m) return null;
  const [, userId, kind, expiry, signature] = m;
  const secret = Deno.env.get("BLINK_EMAIL_UNSUBSCRIBE_SECRET") ?? "";
  if (secret.length < 32) return null;
  const exp = Number(expiry);
  const now = Math.floor(Date.now() / 1000);
  if (!Number.isSafeInteger(exp) || exp < now || exp > now + 60 * 60 * 24 * 91) return null;
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["verify"]
  );
  const payload = `${userId}.${kind}.${expiry}`;
  const decoded = signature.replaceAll("-", "+").replaceAll("_", "/");
  const bytes = Uint8Array.from(atob(decoded + "=".repeat((4 - decoded.length % 4) % 4)), x => x.charCodeAt(0));
  const okay = await crypto.subtle.verify("HMAC", key, bytes, new TextEncoder().encode(payload));
  return okay ? { userId, kind } : null;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "GET" && req.method !== "POST") return page("Method not allowed", "Please use the unsubscribe link from your email.", 405);
  const current = new URL(req.url);
  let token = current.searchParams.get("token") ?? "";
  if (req.method === "POST" && !token) {
    const ct = req.headers.get("content-type") ?? "";
    if (ct.startsWith("application/x-www-form-urlencoded")) {
      const form = await req.formData().catch(() => null);
      token = String(form?.get("token") ?? "");
    }
  }
  const valid = await verify(token);
  if (!valid) return page("Link expired", "This unsubscribe link is invalid or expired. You can still disable social email reminders in your BLINK notification settings.", 400);

  if (req.method === "GET") {
    // Many email scanners prefetch links: NEVER unsubscribe on GET.
    const safeToken = token.replaceAll("&", "&amp;").replaceAll('"', "&quot;");
    return page("Manage social emails", `<p>Stop emails for: <strong>${valid.kind.replaceAll("_", " ")}</strong>?</p>
<form method="post" action="/functions/v1/unsubscribe-social-email">
<input type="hidden" name="token" value="${safeToken}">
<button style="border:0;border-radius:12px;padding:14px 18px;font-size:16px;background:#fff;color:#000;font-weight:bold" type="submit">Unsubscribe</button>
</form>`);
  }

  const url = Deno.env.get("SUPABASE_URL");
  const role = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !role) return page("Please try again", "Subscription settings are temporarily unavailable.", 503);
  const admin = createClient(url, role, { auth: { autoRefreshToken: false, persistSession: false } });
  const { error } = await admin.from("blink_social_email_preferences")
    .update({ [FIELDS[valid.kind]]: false })
    .eq("user_id", valid.userId);
  if (error) return page("Please try again", "We could not update your email preferences. Please use BLINK notification settings.", 503);
  return page("You're unsubscribed", "You won't receive this category of BLINK social emails again unless you opt back in. Essential account-security emails are unaffected.");
});
