#!/usr/bin/env node
// BLINK regression checks for canonical Supabase redirect URLs and Pages fallback.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';

const web = readFileSync('web/app.js', 'utf8');
const config = readFileSync('web/config.js', 'utf8');
const html = readFileSync('web/404.html', 'utf8');
const fallback = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
assert.ok(fallback, '404.html must retain a route fallback script');

assert.ok(config.includes('siteUrl: "https://blink.com.ng"'), "Canonical BLINK domain not configured");
assert.ok(web.includes("const redirectBase=(SITE_URL||location.origin).replace(/\\/+$/,'');"), 'Google login must derive its callback from the canonical configured site');
assert.ok(web.includes("redirect_to:`${redirectBase}/`"), 'Google login must send redirect_to to the canonical domain');
assert.ok(web.includes("const redirect=SITE_URL+'/settings/password'"), 'Password reset email callback must use the canonical domain');
assert.ok(web.includes("type==='recovery'?'/settings/password'"), 'Recovery tokens must lead to password settings');

function check404(original) {
  let redirected = null;
  const u = new URL(original);
  const location = {
    hostname: u.hostname,
    pathname: u.pathname,
    search: u.search,
    hash: u.hash,
    replace: next => { redirected = new URL(next, u).toString(); },
  };
  runInNewContext(fallback, { location, encodeURIComponent });
  assert.ok(redirected, '404 routing must execute a replacement');
  const result = new URL(redirected);
  const path = result.searchParams.get('p');
  assert.ok(path, '404 route parameter required');
  assert.ok(!path.includes('access_token'), 'Never put the access token in a query or route');
  assert.ok(!path.includes('refresh_token'), 'Never put the refresh token in a query or route');
  assert.ok(!result.search.includes('access_token'), 'Recovery token leaked to URL query');
  assert.ok(!result.search.includes('refresh_token'), 'Refresh token leaked to URL query');
  assert.equal(result.hash, u.hash, 'OAuth/recovery fragment must survive redirects');
  return { result, path };
}

const tokenFragment='#access_token=EXAMPLE_ACCESS&refresh_token=EXAMPLE_REFRESH&type=recovery';
const prod = check404('https://blink.com.ng/settings/password?source=mail'+tokenFragment);
assert.equal(prod.result.pathname, '/');
assert.equal(prod.path, '/settings/password?source=mail');

const preview = check404('https://glimmar-code.github.io/Blinkng/settings/password'+tokenFragment);
assert.equal(preview.result.pathname, '/Blinkng/');
assert.equal(preview.path, '/settings/password');

console.log('PASS: canonical Google OAuth/password recovery and token-safe route fallback');
