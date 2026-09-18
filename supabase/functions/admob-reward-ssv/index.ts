import "jsr:@supabase/functions-js/edge-runtime.d.ts";

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

function base64UrlToBytes(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  const binary = atob(padded);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

function base64ToBytes(value: string): Uint8Array {
  const binary = atob(value.replace(/\s/g, ""));
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

function readDerLength(bytes: Uint8Array, offset: number): { length: number; next: number } {
  const first = bytes[offset];
  if (first < 0x80) return { length: first, next: offset + 1 };
  const count = first & 0x7f;
  if (count < 1 || count > 2) throw new Error("Unsupported DER length");
  let length = 0;
  for (let i = 0; i < count; i += 1) {
    length = (length << 8) | bytes[offset + 1 + i];
  }
  return { length, next: offset + 1 + count };
}

function normalizeInteger(bytes: Uint8Array): Uint8Array {
  let start = 0;
  while (start < bytes.length - 1 && bytes[start] === 0) start += 1;
  const trimmed = bytes.slice(start);
  if (trimmed.length > 32) throw new Error("ECDSA integer is too large");
  const out = new Uint8Array(32);
  out.set(trimmed, 32 - trimmed.length);
  return out;
}

// Google documents AdMob SSV signatures as DER-encoded ECDSA. WebCrypto expects P-256
// ECDSA signatures as the fixed-width r||s representation, so convert DER to 64 bytes.
function derEcdsaToRaw(signature: Uint8Array): Uint8Array {
  let offset = 0;
  if (signature[offset++] !== 0x30) throw new Error("Invalid DER sequence");
  const seq = readDerLength(signature, offset);
  offset = seq.next;
  const sequenceEnd = offset + seq.length;
  if (sequenceEnd !== signature.length) throw new Error("Invalid DER sequence length");

  if (signature[offset++] !== 0x02) throw new Error("Invalid DER r integer");
  const rLen = readDerLength(signature, offset);
  offset = rLen.next;
  const r = signature.slice(offset, offset + rLen.length);
  offset += rLen.length;

  if (signature[offset++] !== 0x02) throw new Error("Invalid DER s integer");
  const sLen = readDerLength(signature, offset);
  offset = sLen.next;
  const s = signature.slice(offset, offset + sLen.length);
  offset += sLen.length;

  if (offset !== sequenceEnd) throw new Error("Trailing DER data");

  const raw = new Uint8Array(64);
  raw.set(normalizeInteger(r), 0);
  raw.set(normalizeInteger(s), 32);
  return raw;
}

async function verifySignature(
  rawQuery: string,
  signatureValue: string,
  keyId: number,
): Promise<boolean> {
  const signatureMarker = "&signature=";
  const markerIndex = rawQuery.indexOf(signatureMarker);
  if (markerIndex <= 0) throw new Error("Missing ordered signature parameter");

  // Per Google's SSV spec, everything before &signature= is the exact signed payload.
  const signedContent = rawQuery.slice(0, markerIndex);

  const keysResponse = await fetch(ADMOB_KEYS_URL, {
    headers: { "cache-control": "no-cache" },
  });
  if (!keysResponse.ok) throw new Error(`Unable to fetch AdMob keys: ${keysResponse.status}`);

  const keyData = (await keysResponse.json()) as AdMobKeysResponse;
  const key = keyData.keys?.find((candidate) => candidate.keyId === keyId);
  if (!key) throw new Error("Unknown AdMob verification key");

  let spki: Uint8Array;
  if (key.base64) {
    spki = base64ToBytes(key.base64);
  } else if (key.pem) {
    spki = base64ToBytes(
      key.pem
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", ""),
    );
  } else {
    throw new Error("AdMob key has no public-key material");
  }

  const publicKey = await crypto.subtle.importKey(
    "spki",
    spki,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["verify"],
  );

  const derSignature = base64UrlToBytes(signatureValue);
  const rawSignature = derEcdsaToRaw(derSignature);
  return await crypto.subtle.verify(
    { name: "ECDSA", hash: "SHA-256" },
    publicKey,
    rawSignature,
    new TextEncoder().encode(signedContent),
  );
}

Deno.serve(async (req: Request) => {
  if (req.method !== "GET") {
    return json(405, { ok: false, error: "method_not_allowed" });
  }

  try {
    const url = new URL(req.url);
    const rawQuery = url.search.startsWith("?") ? url.search.slice(1) : url.search;
    const params = url.searchParams;

    const signature = params.get("signature") ?? "";
    const keyIdRaw = params.get("key_id") ?? "";
    const keyId = Number(keyIdRaw);
    const claimId = params.get("custom_data") ?? "";
    const userId = params.get("user_id") ?? "";
    const transactionId = params.get("transaction_id") ?? "";
    const adUnit = params.get("ad_unit") ?? "";
    const rewardAmount = Number(params.get("reward_amount") ?? "");
    const timestamp = Number(params.get("timestamp") ?? "");

    if (
      !signature ||
      !Number.isFinite(keyId) ||
      !transactionId ||
      !VALID_AD_UNITS.has(adUnit) ||
      rewardAmount !== EXPECTED_REWARD ||
      !Number.isFinite(timestamp)
    ) {
      return json(400, { ok: false, error: "invalid_payload" });
    }

    // Reject obviously stale/future callbacks while allowing normal retries and delivery delay.
    const now = Date.now();
    if (timestamp > now + 5 * 60_000 || timestamp < now - 24 * 60 * 60_000) {
      return json(400, { ok: false, error: "invalid_timestamp" });
    }

    const verified = await verifySignature(rawQuery, signature, keyId);
    if (!verified) {
      return json(403, { ok: false, error: "invalid_signature" });
    }

    // AdMob's dashboard URL-verification tool documents user_id and custom_data as
    // optional testing fields. A verification probe can therefore be correctly signed
    // while omitting one or both values. Accept that signed probe with HTTP 200, but
    // never write reward state unless both identifiers are present.
    if (!claimId || !userId) {
      return json(200, {
        ok: true,
        verification_probe: true,
        persisted: false,
      });
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
          p_key_id: keyIdRaw,
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
