import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const reply = (body: unknown, status = 200) => new Response(
  JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  }
);

/**
 * Server-to-server only. A random secret lives ONLY inside PostgreSQL
 * and is passed by the private pg_cron function over HTTPS (pg_net).
 * No Android/Windows/website user ever receives this token.
 *
 * The database RPC rejects unknown tokens and is service-role only.
 * The existing activity dispatcher revalidates every channel recipient,
 * honours notification preferences, and deduplicates FCM deliveries.
 */
Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return reply({ error: "POST required" }, 405);
  const token = request.headers.get("x-blink-channel-worker") ?? "";
  if (!/^[0-9a-f]{64}$/.test(token)) return reply({ error: "Unauthorized" }, 401);

  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!url || !serviceRole) return reply({ error: "Service unavailable" }, 503);
  const admin = createClient(url, serviceRole, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { data: authorized, error: verifyError } = await admin.rpc(
    "blink_verify_channel_worker", { p_token: token },
  );
  if (verifyError || authorized !== true) {
    return reply({ error: "Unauthorized" }, 401);
  }

  const { data: pending, error: claimError } = await admin.rpc(
    "blink_claim_channel_push_notifications", { p_limit: 40 },
  );
  if (claimError || !Array.isArray(pending)) {
    return reply({ error: "Unable to query channel delivery backlog" }, 503);
  }
  const stats = { checked: 0, delivered: 0, settled: 0, failed: 0 };
  // Keep short worker invocations, bounded load and existing idempotency.
  for (const item of pending as Array<{ notification_id: string }>) {
    stats.checked++;
    const id = String(item?.notification_id ?? "");
    if (!/^[a-f0-9-]{36}$/i.test(id)) {
      stats.failed++;
      continue;
    }
    try {
      const response = await fetch(`${url}/functions/v1/send-activity-notification`, {
        method: "POST",
        headers: {
          apikey: serviceRole,
          Authorization: `Bearer ${serviceRole}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ notification_id: id }),
        signal: AbortSignal.timeout(15000),
      });
      const output = await response.json() as Record<string, unknown>;
      const skip = String(output.skipped ?? "");
      const delivered = Number(output.delivered ?? 0) > 0;
      const terminal = [
        "channel_access_or_preferences_revoked",
        "recipient_preferences",
        "self_or_missing_recipient",
        "already_sent",
      ].includes(skip);
      if (delivered) stats.delivered++;
      else if (terminal) {
        const { error: stateError } = await admin.from("notification_push_dispatches")
          .upsert({
            notification_id: id,
            status: "sent",
            attempts: 1,
            last_error: null,
            updated_at: new Date().toISOString(),
          }, { onConflict: "notification_id" });
        if (stateError) stats.failed++;
        else stats.settled++;
      } else {
        stats.failed++;
      }
    } catch {
      stats.failed++;
    }
  }
  // The same minute worker drains Contest launch email outbox rows through
  // the existing Brevo worker. Service-role auth is server-only; clients never hold it.
  let email: Record<string, unknown> = { skipped: "not_attempted" };
  try {
    const response = await fetch(`${url}/functions/v1/send-essential-email`, {
      method: "POST",
      headers: {
        apikey: serviceRole,
        Authorization: `Bearer ${serviceRole}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({}),
      signal: AbortSignal.timeout(20000),
    });
    email = await response.json() as Record<string, unknown>;
    if (!response.ok) stats.failed++;
  } catch {
    stats.failed++;
    email = { error: "email_worker_unavailable" };
  }
  // A retry is eligible only when the existing dispatch ledger allows it.
  return reply({ ...stats, email }, stats.failed && !stats.delivered && !stats.settled ? 503 : 200);
});
