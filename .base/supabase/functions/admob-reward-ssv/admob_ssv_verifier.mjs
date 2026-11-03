const textEncoder = new TextEncoder();

function decodeQueryComponent(value, label) {
  try {
    return decodeURIComponent(value);
  } catch {
    throw new Error(`Invalid percent encoding in ${label}`);
  }
}

function readNamedParameter(segment, expectedName) {
  const separatorIndex = segment.indexOf("=");
  if (separatorIndex < 1) {
    throw new Error(`Missing ${expectedName} parameter`);
  }

  const name = decodeQueryComponent(segment.slice(0, separatorIndex), "parameter name");
  if (name !== expectedName) {
    throw new Error("signature and key_id must be the final two parameters");
  }

  const value = decodeQueryComponent(segment.slice(separatorIndex + 1), expectedName);
  if (!value) throw new Error(`Missing ${expectedName} value`);
  return value;
}

/**
 * Splits an AdMob callback without reordering or re-encoding its signed query.
 *
 * Google's reference verifier decodes percent escapes before checking the ECDSA
 * signature. URL.search exposes the encoded representation, so verifying it
 * directly rejects valid callbacks whenever a value contains an escaped byte.
 */
export function parseAdMobSignedQuery(rawQuery) {
  if (typeof rawQuery !== "string" || !rawQuery || rawQuery.length > 16_384) {
    throw new Error("Invalid AdMob query");
  }

  const segments = rawQuery.split("&");
  if (segments.length < 3) {
    throw new Error("signature and key_id must be the final two parameters");
  }

  const keyIdSegment = segments.pop();
  const signatureSegment = segments.pop();
  const signature = readNamedParameter(signatureSegment, "signature");
  const keyIdRaw = readNamedParameter(keyIdSegment, "key_id");

  if (!/^\d{1,20}$/.test(keyIdRaw)) throw new Error("Invalid AdMob key_id");
  const keyId = Number(keyIdRaw);
  if (!Number.isSafeInteger(keyId) || keyId <= 0) {
    throw new Error("Invalid AdMob key_id");
  }

  for (const segment of segments) {
    const separatorIndex = segment.indexOf("=");
    const encodedName = separatorIndex < 0 ? segment : segment.slice(0, separatorIndex);
    const name = decodeQueryComponent(encodedName, "parameter name");
    if (name === "signature" || name === "key_id") {
      throw new Error("Duplicate AdMob signature envelope");
    }
  }

  const encodedSignedContent = segments.join("&");
  if (!encodedSignedContent) throw new Error("Missing signed AdMob content");

  return {
    signature,
    keyId,
    keyIdRaw,
    encodedSignedContent,
    // Match java.net.URI.getQuery(), which Google's reference verifier signs/verifies.
    signedContent: decodeQueryComponent(encodedSignedContent, "signed content"),
    // Parse only signed parameters. Anything after key_id is rejected above.
    params: new URLSearchParams(encodedSignedContent),
  };
}

/**
 * AdMob's dashboard URL check omits both optional identity fields when the
 * tester leaves User ID and Custom Data blank. Such a request cannot identify
 * a Blink claim or user, so acknowledging it cannot grant or mutate rewards.
 *
 * Keep this deliberately narrow: callbacks containing either identity field
 * continue through normal signature verification.
 */
export function isAdMobDashboardProbe(params) {
  return !params.has("custom_data") && !params.has("user_id");
}

function base64UrlToBytes(value) {
  if (!/^[A-Za-z0-9_-]+={0,2}$/.test(value)) {
    throw new Error("Invalid AdMob signature encoding");
  }
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  const binary = atob(padded);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

function base64ToBytes(value) {
  const binary = atob(value.replace(/\s/g, ""));
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

function readDerLength(bytes, offset) {
  if (offset >= bytes.length) throw new Error("Truncated DER length");
  const first = bytes[offset];
  if (first < 0x80) return { length: first, next: offset + 1 };

  const count = first & 0x7f;
  if (count < 1 || count > 2 || offset + count >= bytes.length) {
    throw new Error("Unsupported DER length");
  }

  let length = 0;
  for (let i = 0; i < count; i += 1) {
    length = (length << 8) | bytes[offset + 1 + i];
  }
  return { length, next: offset + 1 + count };
}

function normalizeInteger(bytes) {
  if (!bytes.length) throw new Error("Empty ECDSA integer");
  let start = 0;
  while (start < bytes.length - 1 && bytes[start] === 0) start += 1;
  const trimmed = bytes.slice(start);
  if (trimmed.length > 32) throw new Error("ECDSA integer is too large");
  const out = new Uint8Array(32);
  out.set(trimmed, 32 - trimmed.length);
  return out;
}

// WebCrypto uses the fixed-width IEEE P1363 r||s representation for P-256.
// AdMob sends ASN.1 DER ECDSA signatures, so convert them before verification.
export function derEcdsaToRaw(signature) {
  let offset = 0;
  if (signature[offset++] !== 0x30) throw new Error("Invalid DER sequence");
  const sequenceLength = readDerLength(signature, offset);
  offset = sequenceLength.next;
  const sequenceEnd = offset + sequenceLength.length;
  if (sequenceEnd !== signature.length) throw new Error("Invalid DER sequence length");

  if (signature[offset++] !== 0x02) throw new Error("Invalid DER r integer");
  const rLength = readDerLength(signature, offset);
  offset = rLength.next;
  if (offset + rLength.length > sequenceEnd) throw new Error("Truncated DER r integer");
  const r = signature.slice(offset, offset + rLength.length);
  offset += rLength.length;

  if (signature[offset++] !== 0x02) throw new Error("Invalid DER s integer");
  const sLength = readDerLength(signature, offset);
  offset = sLength.next;
  if (offset + sLength.length > sequenceEnd) throw new Error("Truncated DER s integer");
  const s = signature.slice(offset, offset + sLength.length);
  offset += sLength.length;

  if (offset !== sequenceEnd) throw new Error("Trailing DER data");

  const raw = new Uint8Array(64);
  raw.set(normalizeInteger(r), 0);
  raw.set(normalizeInteger(s), 32);
  return raw;
}

export async function verifyAdMobSignedQuery(rawQuery, keyData) {
  const envelope = parseAdMobSignedQuery(rawQuery);
  const keys = Array.isArray(keyData?.keys) ? keyData.keys : [];
  const key = keys.find((candidate) => Number(candidate?.keyId) === envelope.keyId);
  if (!key) throw new Error("Unknown AdMob verification key");

  let spki;
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
  const signature = derEcdsaToRaw(base64UrlToBytes(envelope.signature));
  const verified = await crypto.subtle.verify(
    { name: "ECDSA", hash: "SHA-256" },
    publicKey,
    signature,
    textEncoder.encode(envelope.signedContent),
  );

  return { ...envelope, verified };
}
