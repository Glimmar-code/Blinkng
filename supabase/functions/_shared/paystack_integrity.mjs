/** Defensive check shared by webhook and authenticated payment confirmation. */
export function transactionMatchesOrder(payment,order) {
  if (!payment || !order) return false;
  const transactionId = String(payment.id ?? "");
  return payment.status==="success" &&
    payment.reference===order.reference &&
    payment.currency==="NGN" && order.currency==="NGN" &&
    Number.isSafeInteger(payment.amount) && payment.amount>0 &&
    payment.amount===order.amount_ngn*100 &&
    /^[0-9]+$/.test(transactionId);
}

/** Never initialize a real charge with a live merchant key in staging. */
export function merchantKeyMatchesMode(key, mode) {
  return (mode === "test" || mode === "live") &&
    typeof key === "string" && key.startsWith(`sk_${mode}_`) && key.length > 12;
}

/** Verify Paystack HMAC-SHA512 over the exact raw body. Reusable in Deno and Node tests. */
export async function paystackWebhookSignatureMatches(rawBody, suppliedSignature, secret) {
  if (typeof rawBody !== "string" || typeof secret !== "string" ||
      !secret || typeof suppliedSignature !== "string" ||
      !/^[a-f0-9]{128}$/i.test(suppliedSignature)) return false;
  const encoder = new TextEncoder();
  const key = await globalThis.crypto.subtle.importKey(
    "raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-512" }, false, ["sign"],
  );
  const signed = new Uint8Array(await globalThis.crypto.subtle.sign("HMAC", key, encoder.encode(rawBody)));
  const expected = Array.from(signed, byte => byte.toString(16).padStart(2, "0")).join("");
  const normalized = suppliedSignature.toLowerCase();
  let diff = 0;
  for (let index = 0; index < 128; index++) diff |= normalized.charCodeAt(index) ^ expected.charCodeAt(index);
  return diff === 0;
}
