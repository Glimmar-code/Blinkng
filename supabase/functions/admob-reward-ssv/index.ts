import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  isAdMobDashboardProbe,
  parseAdMobSignedQuery,
  verifyAdMobSignedQuery,
} from "./admob_ssv_verifier.mjs";

const ADMOB_KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";
const EXPECTED_REWARD = 10;
const VALID_AD_UNITS = new Set([
  "4343111201",
  "ca-app-pub-9152580730716304/4343111201",
]);

type AdMobKey = {
  keyId: number;
  pem?: string;
  base64?: string;
};

type AdMobKeysResponse = {
  keys?: AdMobKey[];
};

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

async function fetchAdMobKeys(): Promise<AdMobKeysResponse> {
  const keysResponse = await fetch(ADMOB_KEYS_URL, {
    headers: { "cache-control": "no-cache" },
  });
  if (!keysResponse.ok) throw new Error(`Unable to fetch AdMob keys: ${keysResponse.status}`);
  const keyData = (await keysResponse.json()) as AdMobKeysResponse;
  if (!Array.isArray(keyData.keys) || keyData.keys.length === 0) {
    throw new Error("AdMob returned no verification keys");
  }
  return keyData;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "GET") {
    return json(405, { ok: false, error: "method_not_allowed" });
  }

  try {
    // Preserve the received encoding and ordering until the verifier applies Google's
    // documented URI-query decoding behavior.
    const queryStart = req.url.indexOf("?");
    const rawQuery = queryStart >= 0 ? req.url.slice(queryStart + 1) : "";

    // The AdMob dashboard's reachability test omits both optional identity fields when
    // its User ID and Custom Data boxes are blank. It still includes the signature
    // envelope, but it is not a reward claim and cannot identify any Blink user or claim.
    // Acknowledge this narrow, structurally valid probe without touching reward state.
    // Complete callbacks always continue to Google's cryptographic verification below.
    const envelope = parseAdMobSignedQuery(rawQuery);
    if (isAdMobDashboardProbe(envelope.params)) {
      return json(200, {
        ok: true,
        verification_probe: true,
        persisted: false,
      });
    }

    const verification = await verifyAdMobSignedQuery(rawQuery, await fetchAdMobKeys());
    if (!verification.verified) {
      return json(403, { ok: false, error: "invalid_signature" });
    }

    // Read business fields only from the cryptographically signed part of the query.
    // Unsigned parameters after key_id are rejected by the verifier.
    const params = verification.params;
    const claimId = params.get("custom_data") ?? "";
    const userId = params.get("user_id") ?? "";
    const transactionId = params.get("transaction_id") ?? "";
    const adUnit = params.get("ad_unit") ?? "";
    const rewardAmount = Number(params.get("reward_amount") ?? "");
    const timestamp = Number(params.get("timestamp") ?? "");

    const hasCompleteRewardPayload =
      Boolean(claimId) &&
      Boolean(userId) &&
      Boolean(transactionId) &&
      VALID_AD_UNITS.has(adUnit) &&
      rewardAmount === EXPECTED_REWARD &&
      Number.isFinite(timestamp);

    // A correctly signed callback may still be incomplete. Acknowledge it without ever
    // persisting or granting reward state.
    if (!hasCompleteRewardPayload) {
      return json(200, {
        ok: true,
        verification_probe: true,
        persisted: false,
      });
    }

    // Real reward callbacks must have a fresh timestamp and the exact Blink rewarded unit.
    const now = Date.now();
    if (timestamp > now + 5 * 60_000 || timestamp < now - 24 * 60 * 60_000) {
      return json(400, { ok: false, error: "invalid_timestamp" });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!supabaseUrl || !serviceRoleKey) {
      throw new Error("Supabase service configuration is unavailable");
    }

    const rpcResponse = await fetch(
      `${supabaseUrl}/rest/v1/rpc/record_blink_rewarded_ad_ssv`,
      {
        method: "POST",
        headers: {
          "apikey": serviceRoleKey,
          "authorization": `Bearer ${serviceRoleKey}`,
          "content-type": "application/json",
          "accept": "application/json",
        },
        body: JSON.stringify({
          p_claim_id: claimId,
          p_user_id: userId,
          p_transaction_id: transactionId,
          p_ad_unit: adUnit,
          p_reward_amount: rewardAmount,
          p_key_id: verification.keyIdRaw,
        }),
      },
    );

    if (!rpcResponse.ok) {
      const detail = await rpcResponse.text();
      console.error("AdMob SSV RPC failed", rpcResponse.status, detail);
      return json(500, { ok: false, error: "persistence_failed" });
    }

    // Google expects HTTP 200 for successfully processed SSV callbacks.
    return json(200, { ok: true });
  } catch (error) {
    console.error("AdMob SSV verification error", error);
    return json(400, { ok: false, error: "verification_failed" });
  }
});
