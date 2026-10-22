import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";
import { SignJWT, importPKCS8 } from "npm:jose@6.1.0";

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
});
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

// Called ONLY by a trusted server after call creation. Do not make this
// function callable by the Android/iPhone user token, or by an anon key.
async function validInternalSecret(request: Request, expected: string): Promise<boolean> {
  const supplied = request.headers.get("x-blink-ios-dispatch-secret") ?? "";
  if (!expected || !supplied || supplied.length !== expected.length) return false;
  const encode = (s: string) => new TextEncoder().encode(s);
  const [a, b] = await Promise.all([
    crypto.subtle.digest("SHA-256", encode(supplied)),
    crypto.subtle.digest("SHA-256", encode(expected)),
  ]);
  const x = new Uint8Array(a);
  const y = new Uint8Array(b);
  return x.reduce((v, byte, i) => v | (byte ^ y[i]), 0) === 0;
}

async function createAPNsJWT(team: string, keyID: string, pem: string): Promise<string> {
  const key = await importPKCS8(pem.replaceAll("\\n", "\n"), "ES256");
  return await new SignJWT({})
    .setProtectedHeader({ alg: "ES256", kid: keyID })
    .setIssuer(team)
    .setIssuedAt()
    .sign(key);
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "POST required" }, 405);
  if (Deno.env.get("BLINK_IOS_VOIP_SERVER_ENABLED") !== "true" ||
      Deno.env.get("BLINK_IOS_VOIP_DISPATCH_ENABLED") !== "true") {
    return json({ error: "iOS call delivery disabled" }, 503);
  }

  const secret = Deno.env.get("BLINK_IOS_DISPATCH_SECRET") ?? "";
  if (!(await validInternalSecret(request, secret))) {
    return json({ error: "Forbidden" }, 403);
  }
  const supabaseURL = Deno.env.get("SUPABASE_URL");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const bundleID = Deno.env.get("BLINK_IOS_APNS_BUNDLE_ID");
  const environment = Deno.env.get("BLINK_IOS_APNS_ENVIRONMENT");
  const teamID = Deno.env.get("BLINK_IOS_APNS_TEAM_ID");
  const keyID = Deno.env.get("BLINK_IOS_APNS_KEY_ID");
  const privateKey = Deno.env.get("BLINK_IOS_APNS_PRIVATE_KEY");

  if (!supabaseURL || !serviceRole || !bundleID ||
      !teamID || !keyID || !privateKey ||
      !["sandbox", "production"].includes(environment ?? "")) {
    return json({ error: "APNs delivery not configured" }, 503);
  }
  let callID = "";
  try {
    const raw = await request.text();
    if (raw.length > 512) return json({ error: "Invalid request" }, 413);
    const body = JSON.parse(raw);
    callID = String(body?.call_id ?? "");
  } catch {
    return json({ error: "Invalid request" }, 400);
  }
  if (!UUID.test(callID)) return json({ error: "Invalid call identifier" }, 400);

  const admin = createClient(supabaseURL, serviceRole, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: call, error: readError } = await admin
    .from("calls")
    .select("id,caller_id,callee_id,call_type,status,timeout_at")
    .eq("id", callID)
    .maybeSingle();
  if (readError) return json({ error: "Call service unavailable" }, 503);
  if (!call) return json({ error: "Call not found" }, 404);
  if (call.status !== "ringing" ||
      !["audio", "video"].includes(String(call.call_type)) ||
      !call.timeout_at ||
      Date.parse(String(call.timeout_at)) <= Date.now()) {
    return json({ ok: true, skipped: "call_not_ringable" });
  }

  // Re-evaluate privacy even if it was valid when start_call ran.
  const { data: blockRows, error: blockError } = await admin
    .from("blocks")
    .select("blocker_id")
    .or(
      `and(blocker_id.eq.${call.callee_id},blocked_id.eq.${call.caller_id}),` +
      `and(blocker_id.eq.${call.caller_id},blocked_id.eq.${call.callee_id})`
    ).limit(1);
  if (blockError) return json({ error: "Recipient privacy check unavailable" }, 503);
  if (blockRows?.length) return json({ ok: true, skipped: "blocked" });

  const { data: tokens, error: tokenError } = await admin
    .from("ios_voip_tokens")
    .select("id,token")
    .eq("user_id", call.callee_id)
    .eq("bundle_id", bundleID)
    .eq("environment", environment)
    .eq("is_active", true)
    .gte("last_registered_at",
         new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString())
    .order("last_registered_at", { ascending: false })
    .limit(6);
  if (tokenError) return json({ error: "Device lookup unavailable" }, 503);
  if (!tokens?.length) return json({ ok: true, skipped: "no_active_ios_devices" });

  let jwt: string;
  try {
    jwt = await createAPNsJWT(teamID, keyID, privateKey);
  } catch {
    return json({ error: "Apple signing unavailable" }, 503);
  }
  const host = environment === "sandbox" ? "https://api.sandbox.push.apple.com" :
    "https://api.push.apple.com";
  let delivered = 0;
  let failed = 0;
  let skipped = 0;
  for (const device of tokens) {
    // Unique (call_id,token_id) claim: concurrent retries/duplicate
    // triggering can't send multiple call alerts to the same device.
    const { error: claimError } = await admin.from("ios_voip_dispatches").insert({
      call_id: callID, token_id: device.id, status: "claimed", attempts: 1,
    });
    if (claimError?.code === "23505") {
      skipped++;
      continue;
    }
    if (claimError) {
      failed++;
      continue; // Fail CLOSED. Never send when dedupe could not be recorded.
    }

    // A rapid answer or hangup invalidates delivery even if dispatch started.
    const { data: fresh, error: refreshedError } = await admin
      .from("calls").select("status,timeout_at").eq("id", callID).maybeSingle();
    if (refreshedError || fresh?.status !== "ringing" ||
        !fresh.timeout_at || Date.parse(String(fresh.timeout_at)) <= Date.now()) {
      await admin.from("ios_voip_dispatches").update({
        status: "failed", last_error_code: "no_longer_ringable",
        updated_at: new Date().toISOString(),
      }).eq("call_id", callID).eq("token_id", device.id);
      skipped++;
      continue;
    }
    let code = "unknown";
    let success = false;
    try {
      const response = await fetch(
        `${host}/3/device/${encodeURIComponent(String(device.token))}`,
        {
          method: "POST",
          headers: {
            authorization: `bearer ${jwt}`,
            "apns-topic": `${bundleID}.voip`,
            "apns-push-type": "voip",
            "apns-priority": "10",
            "apns-expiration": "0",
            "content-type": "application/json",
          },
          // No caller name/email/phone is disclosed to PushKit payload.
          body: JSON.stringify({
            aps: {},
            call_id: String(call.id),
            call_type: String(call.call_type),
          }),
        },
      );
      success = response.ok;
      code = response.ok ? "" : `apns_http_${response.status}`;
      // Invalid/unregistered tokens are removed from delivery eligibility.
      if ([400, 410].includes(response.status)) {
        let reason = "";
        try {
          const error = await response.json();
          reason = String(error?.reason ?? "");
        } catch { /* Generic response. */ }
        if (["BadDeviceToken", "Unregistered", "DeviceTokenNotForTopic"].includes(reason)) {
          await admin.from("ios_voip_tokens").update({
            is_active: false, revoked_at: new Date().toISOString(),
          }).eq("id", device.id);
        }
      }
    } catch { code = "apns_network_failure"; }
    const { error: updateError } = await admin.from("ios_voip_dispatches").update({
      status: success ? "sent" : "failed",
      last_error_code: code || null,
      updated_at: new Date().toISOString(),
    }).eq("call_id", callID).eq("token_id", device.id);
    // Do not treat unsuccessful accounting as proof of successful delivery.
    if (success && !updateError) delivered++;
    else failed++;
  }
  return json({ ok: delivered > 0, accepted_by_apns: delivered,
                failed, skipped, recipients: tokens.length });
});
