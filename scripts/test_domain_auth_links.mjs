#!/usr/bin/env node
// BLINK regression checks for canonical Supabase redirect URLs and Pages fallback.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import cloudflareWorker, {isBlinkAppRoute} from '../web/_worker.js';

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

// Cloudflare Pages serves a custom 404.html, which disables its implicit SPA
// fallback. Verify that direct post/reel/profile links have explicit 200
// rewrites instead of incorrectly relying on Vercel rewrites or a 404 redirect.
const redirects = readFileSync('web/_redirects', 'utf8');
const redirectRules = redirects.split(/\r?\n/).map(line => line.trim())
  .filter(line => line && !line.startsWith('#'))
  .map(line => line.split(/\s+/));
const spaRewrites = new Map(redirectRules.filter(parts => parts.length === 3 && parts[2] === '200')
  .map(parts => [parts[0], parts[1]]));
for(const source of [
  '/@*', '/profile/*', '/post/*', '/reel/*', '/invite/*',
  '/feed', '/reels', '/search', '/messages', '/messages/*',
  '/activity', '/market', '/connect', '/settings', '/settings/*', '/login'
]) {
  assert.equal(spaRewrites.get(source), '/index.html',
    'Cloudflare Pages must internally serve the SPA for direct '+source+' visits');
}
assert.ok(!spaRewrites.has('/*'), 'Do not rewrite public marketing pages or static assets');
assert.ok(readFileSync('web/404.html','utf8').includes('location.replace(base'),
  'Keep the independent GitHub Pages 404 fallback for legacy links');

console.log('PASS: canonical Google OAuth/password recovery and token-safe route fallback');
console.log('PASS: Cloudflare Pages shared post, reel and profile URLs serve the SPA shell');

const index = readFileSync('web/index.html','utf8');
assert.match(index, /<base href="\/" \/>/, 'Nested SPA routes must fetch JS/CSS from the root');
assert.ok(index.includes("location.hostname.endsWith('github.io')"),
  'Legacy GitHub Pages previews must keep their own base path');

// Exercise the real Cloudflare Pages advanced worker with a mocked ASSETS binding.
const fetched = [];
const env = {ASSETS: {fetch: async request => {
  const path = new URL(request.url).pathname;
  fetched.push(path);
  return path === '/'
    ? new Response('<!doctype html><html><head><base href="/"></head></html>', {
        headers:{'Content-Type':'text/html', 'Cache-Control':'public, max-age=0'}
      })
    : new Response('Not found',{status:404});
}}};
for(const path of [
  '/post/a0a72f53-417f-4b88-997b-06faef40049e',
  '/reel/00000000-0000-4000-8000-000000000001',
  '/@blinkuser',
  '/profile/blinkuser',
  '/settings/password'
]){
  assert.ok(isBlinkAppRoute(path), 'Recognize known SPA route '+path);
  const original = 'https://blink.com.ng'+path+'?share=1';
  const result = await cloudflareWorker.fetch(new Request(original),env);
  assert.equal(result.status,200,'Serve application HTML on '+path);
  assert.match(await result.text(),/<base href="\/"/);
  assert.equal(result.headers.get('x-robots-tag'),'noindex, nofollow',
    'Private/link routes must never be indexed');
  assert.equal(result.headers.get('cache-control'),'no-store','Shared routes must not cache user content');
  assert.equal(fetched.at(-1),'/', 'Serve root shell rather than redirect to /index.html');
}
for(const path of ['/','/about/','/robots.txt','/some-missing-url','/app.js','/.well-known/assetlinks.json']){
  assert.equal(isBlinkAppRoute(path),false,'Never rewrite static content '+path);
  const response = await cloudflareWorker.fetch(new Request('https://blink.com.ng'+path),env);
  assert.equal(fetched.at(-1),path,'Pass through original asset path '+path);
  assert.equal(response.status,path==='/'?200:404,'Preserve static origin response '+path);
}
const postResponse = await cloudflareWorker.fetch(new Request('https://blink.com.ng/post/a0a72f53-417f-4b88-997b-06faef40049e',{method:'POST'}),env);
assert.equal(postResponse.status,404,'Never rewrite POST requests');
console.log('PASS: Cloudflare Pages asset worker serves real shared routes without breaking SEO or assets');
