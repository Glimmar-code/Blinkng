// Read-only production infrastructure diagnostic. NEVER forges a rewarded-ad view
// or calls the Supabase reward-credit RPC. An unsigned probe must not pay coins.
import assert from "node:assert/strict";
import test from "node:test";

const SSV_URL =
  "https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/admob-reward-ssv";
const APP_ADS_URL = "https://blink.com.ng/app-ads.txt";
const DOMAIN_READY = process.env.BLINK_DOMAIN_READY === "true";
if (!DOMAIN_READY) console.log(
  "DOMAIN DEFERRED: no developer domain has been purchased; app-ads.txt checks are skipped. " +
  "This does NOT establish AdMob app ownership verification or live ad eligibility."
);
const EXPECTED_SELLER =
  "google.com, pub-9152580730716304, DIRECT, f08c47fec0942fa0";

async function get(url) {
  const response = await fetch(url, {
    method: "GET",
    redirect: "follow",
    signal: AbortSignal.timeout(15000),
    headers: { "user-agent": "BLINK-readiness-check/1.0" },
  });
  const content = (await response.text()).slice(0, 2048);
  return { status: response.status, content, finalUrl: response.url, type: response.headers.get("content-type") ?? "" };
}

test("AdMob signed SSV endpoint is publicly reachable without a user JWT", async () => {
  // No signature, user_id or custom_data: zero possible rewards.
  const r = await get(SSV_URL);
  assert.notEqual(r.status, 401,
    "Supabase JWT auth is blocking Google's server-to-server SSV callback; verify_jwt must be false.");
  assert.notEqual(r.status, 404,
    "Supabase deployed SSV Edge Function is missing or at the wrong callback URL.");
  assert.equal(r.status, 400,
    `Unsigned empty SSV requests must receive HTTP 400; got ${r.status}. Check Edge Function logs/deployment.`);
  assert.match(r.content, /verification_failed|invalid/i,
    "Expected the verifier to refuse an unsigned request rather than silently accept it.");
  console.log("SSV endpoint reachable; unsigned requests are rejected.");
});


test("AdMob dashboard-shaped URL check is acknowledged without minting a reward", async () => {
  // Google tests callback reachability with no user_id or custom_data.
  // This is NOT proof of a signed reward. Check that the server marks the
  // request as a non-persisted probe; do not create credits or claims.
  const params = new URLSearchParams({
    ad_network: "5450213213286189855",
    ad_unit: "4343111201",
    reward_amount: "10",
    reward_item: "Blink Coins",
    timestamp: String(Date.now()),
    transaction_id: "non-crediting-url-check",
    signature: "invalid_test_signature",
    key_id: "1234",
  });
  const response = await get(`${SSV_URL}?${params.toString()}`);
  assert.equal(response.status, 200, "SSV dashboard reachability probe was not accepted.");
  const body = JSON.parse(response.content);
  assert.equal(body.verification_probe, true);
  assert.equal(body.persisted, false);
  assert.equal(body.ok, true);
  console.log("Dashboard-shaped probe acknowledged without reward persistence.");
});

test("Identifiable forged reward callback is rejected", async () => {
  // An invalid signature MUST NOT get the dashboard-probe exception when user
  // or claim information is supplied. This does not contact the credit RPC.
  const params = new URLSearchParams({
    ad_network: "5450213213286189855",
    ad_unit: "4343111201",
    custom_data: "00000000-0000-0000-0000-000000000000",
    reward_amount: "10",
    reward_item: "Blink Coins",
    timestamp: String(Date.now()),
    transaction_id: "invalid-signature-test",
    user_id: "00000000-0000-0000-0000-000000000000",
    signature: "invalid_test_signature",
    key_id: "1234",
  });
  const response = await get(`${SSV_URL}?${params.toString()}`);
  assert.notEqual(response.status, 200,
    "An identifiable reward callback was accepted without cryptographic verification.");
  console.log("Forged identifiable callbacks rejected.");
});

test("AdMob publisher authorization file is available from the public root domain", { skip: !DOMAIN_READY }, async () => {
  const r = await get(APP_ADS_URL);
  assert.equal(r.status, 200,
    `AdMob cannot crawl ${APP_ADS_URL} (HTTP ${r.status}). Fix site routing/deployment.`);
  const lines = r.content.split(/\r?\n/).map(line => line.trim());
  assert.ok(lines.includes(EXPECTED_SELLER),
    "The publicly served app-ads.txt does not contain BLINK's AdMob DIRECT publisher authorization.");
  assert.ok(!r.type.includes("text/html"),
    "The web server returned HTML instead of the plain-text app-ads.txt.");
  console.log("AdMob publisher authorization file is publicly reachable.");
});

test("BLINK configured www developer hostname serves the same app-ads.txt", { skip: !DOMAIN_READY }, async () => {
  const r = await get("https://www.blink.com.ng/app-ads.txt");
  assert.equal(r.status, 200, `www.blink.com.ng/app-ads.txt responded with HTTP ${r.status}`);
  assert.ok(r.content.split(/\r?\n/).some(line => line.trim() === EXPECTED_SELLER),
    "www.blink.com.ng does not serve the required Google publisher record.");
  console.log("www developer site serves the expected AdMob authorization line.");
});
