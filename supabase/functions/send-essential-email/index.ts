import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

// Internal worker only. Do not call this function from the Android, Windows or web client.
// Password reset and email-change links are issued solely by Supabase Auth.
type Kind = "welcome" | "new_device" | "suspicious_activity";
type QueueRow = {
  id: string;
  user_id: string;
  kind: Kind;
  attempts: number;
};

type Message = { subject: string; heading: string; intro: string; noteHeading: string; note: string };

const MESSAGES: Record<Kind, Message> = {
  welcome: {
    subject: "Welcome to BLINK",
    heading: "Welcome to BLINK",
    intro: "Your BLINK account is ready. Discover your community, connect with people, and share your moments.",
    noteHeading: "Your account, your rules",
    note: "Stay safe: never share passwords or sign-in codes. You can review privacy settings inside BLINK.",
  },
  new_device: {
    subject: "BLINK security: new sign-in",
    heading: "New sign-in to BLINK",
    intro: "A sign-in was detected from a device not previously recognized for your BLINK account.",
    noteHeading: "Don't recognize this?",
    note: "Open BLINK directly, change your password, and review your active sessions.",
  },
  suspicious_activity: {
    subject: "BLINK security: unusual activity",
    heading: "Unusual account activity",
    intro: "Our security systems detected activity on your BLINK account that may need your attention.",
    noteHeading: "Protect your account",
    note: "Open BLINK directly to review your account and update your password if necessary. Never share a recovery link.",
  },
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });

// Compare fixed-size hashes to avoid short-circuiting an internal secret check.
async function secretEquals(provided: string, expected: string): Promise<boolean> {
  const encoder = new TextEncoder();
  const [left, right] = await Promise.all([
    crypto.subtle.digest("SHA-256", encoder.encode(provided)),
    crypto.subtle.digest("SHA-256", encoder.encode(expected)),
  ]);
  const a = new Uint8Array(left);
  const b = new Uint8Array(right);
  let mismatch = 0;
  for (let i = 0; i < a.length; i++) mismatch |= a[i] ^ b[i];
  return mismatch === 0;
}

function render(message: Message): string {
  return `<!doctype html>
<html lang="en">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${message.subject}</title></head>
<body style="margin:0;background:#000000;color:#ffffff;font-family:Arial,Helvetica,sans-serif;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#000000;">
<tr><td align="center" style="padding:25px 12px;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:540px;background:#0a0a0a;border:1px solid #252525;border-radius:22px;">
<tr><td style="padding:32px 26px 16px;font-size:27px;line-height:35px;font-weight:800;color:#ffffff;">BLINK</td></tr>
<tr><td style="padding:14px 26px 10px;">
<h1 style="font-size:29px;line-height:38px;margin:0;color:#ffffff;">${message.heading}</h1>
<p style="font-size:15px;line-height:25px;color:#bcbcbc;margin:12px 0 0;">${message.intro}</p>
</td></tr>
<tr><td style="padding:16px 26px;">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#111111;border:1px solid #292929;border-radius:16px;">
<tr><td style="padding:19px;">
<strong style="font-size:14px;color:#ffffff;">${message.noteHeading}</strong>
<p style="font-size:14px;line-height:24px;color:#b8b8b8;margin:8px 0 0;">${message.note}</p>
</td></tr></table>
</td></tr>
<tr><td style="padding:10px 26px 30px;color:#999999;font-size:12px;line-height:20px;">
This is a BLINK account email. Never share your password, verification codes, or recovery links.<br>
Need help? <a href="mailto:blinksupport.ng@gmail.com" style="color:#ffffff;">BLINK Support</a>
</td></tr></table>
<p style="font-size:11px;color:#777777;">&copy; BLINK. All rights reserved.</p>
</td></tr></table>
</body></html>`;
}

async function setStatus(
  admin: ReturnType<typeof createClient>,
  row: QueueRow,
  status: "sent" | "pending" | "failed",
  error: string | null = null,
) {
  const nextAttempts = row.attempts + 1;
  const minutes = Math.min(240, 15 * 2 ** Math.max(0, nextAttempts - 1));
  await admin.from("blink_essential_email_outbox")
    .update({
      status,
      last_error: error,
      updated_at: new Date().toISOString(),
      available_at: status === "pending" ? new Date(Date.now() + minutes * 60_000).toISOString() : new Date().toISOString(),
      ...(status === "sent" ? { sent_at: new Date().toISOString() } : {}),
    })
    .eq("id", row.id)
    .eq("status", "sending");
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "POST required" }, 405);

  const internalSecret = Deno.env.get("BLINK_EMAIL_WORKER_SECRET") ?? "";
  const incomingSecret = req.headers.get("x-blink-email-worker-secret") ?? "";
  // Don't accept default/missing secrets or weaker untrusted client tokens.
  if (internalSecret.length < 32 || !(await secretEquals(incomingSecret, internalSecret))) {
    return json({ error: "Unauthorized" }, 401);
  }

  const url = Deno.env.get("SUPABASE_URL");
  const role = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const brevoKey = Deno.env.get("BREVO_API_KEY");
  const from = Deno.env.get("BLINK_EMAIL_SENDER_ADDRESS");
  if (!url || !role || !brevoKey || !from || !from.includes("@")) {
    return json({ error: "Transactional email server is not configured" }, 503);
  }

  const admin = createClient(url, role, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // Reserve the provider's free-tier capacity for Supabase Auth recovery emails.
  // Brevo counts built-in SMTP emails outside this queue too: monitor those separately.
  const startOfDay = new Date();
  startOfDay.setUTCHours(0, 0, 0, 0);
  const { count, error: countError } = await admin
    .from("blink_essential_email_outbox")
    .select("id", { head: true, count: "exact" })
    .eq("status", "sent")
    .gte("sent_at", startOfDay.toISOString());
  if (countError) return json({ error: "Email capacity check unavailable" }, 503);
  if ((count ?? 0) >= 75) return json({ ok: true, skipped: "daily_internal_cap" });

  const max = Math.max(0, Math.min(10, 75 - (count ?? 0)));
  const { data, error: dueError } = await admin.from("blink_essential_email_outbox")
    .select("id,user_id,kind,attempts")
    .eq("status", "pending")
    .lte("available_at", new Date().toISOString())
    .order("priority", { ascending: true })
    .order("created_at", { ascending: true })
    .limit(max);
  if (dueError) return json({ error: "Queue query failed" }, 503);

  let sent = 0, failed = 0, skipped = 0;
  for (const row of (data ?? []) as QueueRow[]) {
    // Conditional compare-and-set prevents multiple workers dispatching the same event.
    const { data: claimed, error: claimError } = await admin.from("blink_essential_email_outbox")
      .update({
        status: "sending",
        attempts: row.attempts + 1,
        updated_at: new Date().toISOString(),
      })
      .eq("id", row.id)
      .eq("status", "pending")
      .eq("attempts", row.attempts)
      .select("id")
      .maybeSingle();

    if (claimError || !claimed) { skipped++; continue; }
    const message = MESSAGES[row.kind];
    if (!message) {
      await setStatus(admin, row, "failed", "unsupported_email_kind");
      failed++;
      continue;
    }

    try {
      const { data: fetched, error: userError } = await admin.auth.admin.getUserById(row.user_id);
      const user = fetched?.user;
      const recipientEmail = user?.email;
      if (userError || !user || !recipientEmail || !user.email_confirmed_at || user.banned_until && new Date(user.banned_until) > new Date()) {
        await setStatus(admin, row, "failed", "recipient_not_eligible");
        failed++;
        continue;
      }

      const response = await fetch("https://api.brevo.com/v3/smtp/email", {
        method: "POST",
        headers: {
          "api-key": brevoKey,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          sender: { email: from, name: "BLINK" },
          to: [{ email: recipientEmail }],
          subject: message.subject,
          htmlContent: render(message),
          textContent: `${message.heading}\n\n${message.intro}\n\n${message.noteHeading}: ${message.note}\n\nBLINK Support: blinksupport.ng@gmail.com`,
          // Disable vendor click/open tracking for account security emails.
          params: { category: "blink_essential" },
        }),
      });
      if (response.ok) {
        await setStatus(admin, row, "sent");
        sent++;
      } else {
        // Brevo may reject invalid senders or credentials; don't flood/retry those.
        const retry = (response.status === 429 || response.status >= 500) && row.attempts + 1 < 4;
        await setStatus(admin, row, retry ? "pending" : "failed", `brevo_http_${response.status}`);
        failed++;
      }
    } catch (_err) {
      // Network errors may be ambiguous; stop automatic retries to avoid duplicate alerts.
      await setStatus(admin, row, "failed", "delivery_result_unknown");
      failed++;
    }
  }

  // No PII or authentication secrets are included in the response.
  return json({ ok: true, sent, failed, skipped });
});
