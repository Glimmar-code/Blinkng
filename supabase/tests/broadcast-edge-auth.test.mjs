import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import {
  base64UrlEncodeBytes,
  normalizePrivateKeyPem,
  fcmAccessToken,
} from "../functions/_shared/fcm-auth.mjs";

if (!globalThis.crypto) globalThis.crypto = webcrypto;

function decodeBase64Url(value) {
  return Buffer.from(value.replace(/-/g, "+").replace(/_/g, "/"), "base64");
}

test("base64url encoding replaces plus/slash and omits padding", () => {
  assert.equal(base64UrlEncodeBytes(new Uint8Array([251, 255])), "-_8");
});

test("PEM normalizer strips key markers and whitespace", () => {
  const bytes = new Uint8Array([1, 2, 3, 4]);
  const pem = "-----BEGIN PRIVATE KEY-----\n" +
    Buffer.from(bytes).toString("base64").slice(0, 4) + " \n" +
    Buffer.from(bytes).toString("base64").slice(4) +
    "\n-----END PRIVATE KEY-----";
  assert.deepEqual(normalizePrivateKeyPem(pem), bytes);
});

test("OAuth JWT is correctly signed and exchanged through injected fetch", async () => {
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  );
  const privateDer = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
  const privatePem = "-----BEGIN PRIVATE KEY-----\n" +
    Buffer.from(privateDer).toString("base64").match(/.{1,64}/g).join("\n") +
    "\n-----END PRIVATE KEY-----";
  let captured;
  const fakeFetch = async (url, init) => {
    assert.equal(url, "https://oauth2.googleapis.com/token");
    assert.equal(init.method, "POST");
    captured = new URLSearchParams(init.body).get("assertion");
    return new Response(JSON.stringify({ access_token: "test-access-token" }), { status: 200 });
  };
  const token = await fcmAccessToken(
    { client_email: "test-service@example.invalid", private_key: privatePem },
    fakeFetch,
    1_700_000_000,
  );
  assert.equal(token, "test-access-token");
  const [header, claims, signature] = captured.split(".");
  assert.deepEqual(JSON.parse(decodeBase64Url(header)), { alg: "RS256", typ: "JWT" });
  const payload = JSON.parse(decodeBase64Url(claims));
  assert.equal(payload.iss, "test-service@example.invalid");
  assert.equal(payload.scope, "https://www.googleapis.com/auth/firebase.messaging");
  assert.equal(payload.aud, "https://oauth2.googleapis.com/token");
  assert.equal(payload.iat, 1_700_000_000);
  assert.equal(payload.exp, 1_700_003_600);
  assert.equal(await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5", pair.publicKey,
    decodeBase64Url(signature), new TextEncoder().encode(header + "." + claims),
  ), true);
});

test("OAuth exchange errors and missing tokens fail closed", async () => {
  await assert.rejects(
    fcmAccessToken({ client_email: "a", private_key: "bad" }, async () => new Response("", { status: 401 })),
    /FCM_OAUTH_FAILED/,
  );
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  );
  const der = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
  const pem = "-----BEGIN PRIVATE KEY-----\n" + Buffer.from(der).toString("base64") + "\n-----END PRIVATE KEY-----";
  await assert.rejects(
    fcmAccessToken({ client_email: "a", private_key: pem }, async () => new Response("{}", { status: 200 })),
    /FCM_OAUTH_TOKEN_MISSING/,
  );
});
