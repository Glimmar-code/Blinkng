import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
});

/**
 * Server-only, idempotent referral outbox drainer.
 * Set BLINK_REFERRAL_WORKER_TOKEN as an Edge Function secret and in the trusted
 * scheduler; NEVER ship this credential inside any BLINK app.
 * Deploy with verify_jwt=false: access is enforced using this secret.
 */
Deno.serve(async (request) => {
  if (request.method !== "POST") return reply({ error: "POST required" }, 405);
  const key = Deno.env.get("BLINK_REFERRAL_WORKER_TOKEN") ?? "";
  const provided = request.headers.get("x-blink-referral-worker") ?? "";
  if (key.length < 32 || provided.length !== key.length) return reply({ error: "Unauthorized" }, 401);
  const x = new TextEncoder().encode(key);
  const y = new TextEncoder().encode(provided);
  if (x.length !== y.length) return reply({ error: "Unauthorized" }, 401);
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  if (diff !== 0) return reply({ error: "Unauthorized" }, 401);

  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!url || !serviceKey) return reply({ error: "Missing server config" }, 503);

  const admin = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: items, error: claimError } = await admin.rpc(
    "blink_claim_referral_pushes", { p_limit: 10 },
  );
  if (claimError) return reply({ error: "Unable to claim referral outbox" }, 503);

  const results = await Promise.all((items ?? []).map(async (
    item: { notification_id: string; attempt_count: number },
  ) => {
    let success = false;
    let reason = "Unknown push error";
    try {
      const response = await fetch(`${url}/functions/v1/send-activity-notification`, {
        method: "POST",
        headers: {
          apikey: serviceKey,
          Authorization: `Bearer ${serviceKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ notification_id: item.notification_id }),
        signal: AbortSignal.timeout(18_000),
      });
      const responseBody = await response.json() as Record<string, unknown>;
      const skip = String(responseBody.skipped ?? "");
      // Honor recipient opt-out permanently; transient missing tokens still retry.
      success = response.ok && (
        Number(responseBody.delivered ?? 0) > 0 ||
        skip === "already_sent" ||
        skip === "recipient_preferences" ||
        skip === "self_or_missing_recipient"
      );
      reason = success ? "" : String(responseBody.error ?? responseBody.skipped ?? response.status);
    } catch (e) {
      reason = e instanceof Error ? e.message : "Push request failed";
    }
    const { error: finishError } = await admin.rpc("blink_finish_referral_push", {
      p_notification_id: item.notification_id,
      p_succeeded: success,
      p_error: reason.slice(0, 400),
      p_attempt_count: item.attempt_count,
    });
    return { success, persisted: !finishError };
  }));

  return reply({
    claimed: (items ?? []).length,
    delivered: results.filter((x) => x.success && x.persisted).length,
    retryable: results.filter((x) => !x.success).length,
    stateUpdateErrors: results.filter((x) => !x.persisted).length,
  });
});
