import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const webScript = fs.readFileSync(new URL("../../web/broadcasts.js", import.meta.url), "utf8");
const html = fs.readFileSync(new URL("../../web/index.html", import.meta.url), "utf8");

test("website broadcast client uses the public website RPC and configured publishable key", () => {
  assert.match(webScript, /get_website_broadcasts/);
  assert.match(webScript, /BLINK_WEB_CONFIG/);
  assert.match(webScript, /apikey: key/);
  assert.match(webScript, /credentials: "omit"/);
});

test("website broadcast content is rendered as text, not HTML", () => {
  assert.match(webScript, /title\.textContent/);
  assert.match(webScript, /body\.textContent/);
  assert.doesNotMatch(webScript, /\.innerHTML\s*=/);
});

test("website banner filters CTA and image URLs and supports local dismissal", () => {
  assert.match(webScript, /function safeCtaUrl/);
  assert.match(webScript, /function safeImageUrl/);
  assert.match(webScript, /localStorage\.setItem/);
  assert.match(webScript, /close\.addEventListener\("click"/);
});

test("website page loads the broadcast banner assets", () => {
  assert.match(html, /broadcasts\.css/);
  assert.match(html, /broadcasts\.js/);
});
