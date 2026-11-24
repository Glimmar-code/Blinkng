/** Shared FCM OAuth helper, runtime-neutral for Deno Edge Functions and Node tests. */
export function base64UrlEncodeBytes(data) {
  return btoa(String.fromCharCode(...data))
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

export function normalizePrivateKeyPem(pem) {
  const normalized = pem
    .replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----/g, "")
    .replace(/\s/g, "");
  return Uint8Array.from(atob(normalized), (char) => char.charCodeAt(0));
}

export async function fcmAccessToken(
  serviceAccount,
  fetcher = fetch,
  nowSeconds = Math.floor(Date.now() / 1000),
) {
  const header = base64UrlEncodeBytes(new TextEncoder().encode(JSON.stringify({
    alg: "RS256", typ: "JWT",
  })));
  const claims = base64UrlEncodeBytes(new TextEncoder().encode(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: nowSeconds,
    exp: nowSeconds + 3600,
  })));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    normalizePrivateKeyPem(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = base64UrlEncodeBytes(new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(header + "." + claims)),
  ));
  const response = await fetcher("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: header + "." + claims + "." + signature,
    }),
  });
  if (!response.ok) throw new Error("FCM_OAUTH_FAILED");
  const result = await response.json();
  if (!result.access_token) throw new Error("FCM_OAUTH_TOKEN_MISSING");
  return result.access_token;
}
