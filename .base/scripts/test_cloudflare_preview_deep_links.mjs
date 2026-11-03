#!/usr/bin/env node
// Real HTTP routing smoke for the Cloudflare Pages preview. No login or data access.
import assert from 'node:assert/strict';

const branch = process.env.BLINK_PREVIEW_BRANCH || '';
assert.match(branch, /^(?:Testlab-web-[A-Za-z0-9_-]+|Testlab\/[A-Za-z0-9_-]+)$/, 'Preview smoke is Testlab-only');
const slug = branch.toLowerCase().replace(/[^a-z0-9-]/g, '-').slice(0, 28);
const origin = 'https://' + slug + '.blinkng.pages.dev';
const routes = [
  '/post/00000000-0000-4000-8000-000000000001',
  '/reel/00000000-0000-4000-8000-000000000001',
  '/@blinkuser',
  '/profile/blinkuser',
  '/settings/password',
];

const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
async function verify() {
  for (const path of routes) {
    const response = await fetch(origin + path + '?smoke=' + Date.now(), {
      signal: AbortSignal.timeout(10000), redirect: 'follow'
    });
    assert.equal(response.status, 200, path + ': expected HTML 200, got ' + response.status);
    assert.equal(new URL(response.url).pathname, path, path + ': shared URL must be preserved');
    const body = await response.text();
    assert.match(body, /<base href="\/" \/>/, path + ': HTML base needed for nested JS/CSS');
    assert.match(body, /<script src="app\.js" defer><\/script>/, path + ': app shell is missing');
    assert.match(response.headers.get('x-robots-tag') || '', /noindex/i, path + ': must stay nonindexable');
    assert.match(response.headers.get('cache-control') || '', /no-store/i, path + ': avoid route cache');
  }
  for (const path of ['/about/', '/robots.txt', '/.well-known/assetlinks.json']) {
    const response = await fetch(origin + path, {signal: AbortSignal.timeout(10000)});
    assert.equal(response.status, 200, path + ': static page must not be broken');
  }
  const unknown = await fetch(origin + '/not-a-valid-blink-route-test', {signal: AbortSignal.timeout(10000)});
  assert.equal(unknown.status, 404, 'Unknown pages must retain real HTTP 404 for SEO');
}

let lastError;
for (let attempt = 0; attempt < 24; attempt++) {
  try {
    await verify();
    console.log('PASS: actual Cloudflare Pages preview serves direct shared BLINK URLs: ' + origin);
    process.exit(0);
  } catch (error) {
    lastError = error;
    if (attempt < 23) await pause(5000);
  }
}
console.error('FAIL: Cloudflare Pages preview '+origin+': '+String(lastError?.message || lastError));
process.exit(1);
