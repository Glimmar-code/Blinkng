#!/usr/bin/env node
// BLINK search visibility: raw HTML and privacy regression tests, no network required.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=path=>readFileSync(path,'utf8');
const html=read('web/index.html');
const staticStart=html.indexOf('<main class="boot-static"');
const staticEnd=html.indexOf('</main>',staticStart);
assert.ok(staticStart>=0&&staticEnd>staticStart,'Missing raw HTML-first homepage');
const initial=html.slice(staticStart,staticEnd);
assert.match(initial,/BLINK Market/);
assert.match(initial,/Campus connections/);
assert.match(initial,/Public posts and reels/);
for(const slug of ['students','marketplace','features','download','safety']){
  assert.ok(initial.includes('href="./'+slug+'/"'),'Missing semantic crawlable link: '+slug);
}
const headings=[...initial.matchAll(/<h2>[^<]+<\/h2>/g)];
assert.equal(headings.length,3,'Expected three concise, raw-HTML homepage feature summaries');
assert.match(html,/<meta name="robots" content="index,follow,max-image-preview:large"/);
assert.match(html,/new URLSearchParams\(location.search\).has\('p'\)/);
assert.ok(html.indexOf('noindex,nofollow')<html.indexOf('<script src="app.js"'),'SPA redirects must be protected before deferred scripts run');
for (const slug of ['students','marketplace','safety']) {
  const page=read('web/'+slug+'/index.html');
  assert.ok(page.includes('href="../privacy/"'),'Public feature page must link to privacy: '+slug);
  const m=page.match(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/);
  assert.ok(m,'Missing valid JSON-LD: '+slug);
  const schema=JSON.parse(m[1]);
  assert.equal(schema.url,'https://blink.com.ng/'+slug+'/');
}
const app=read('web/app.js');
const start=app.indexOf('  function updateRoutePrivacy(path){');
const end=app.indexOf('  async function renderSiteAnalytics()',start);
assert.ok(start>=0&&end>start,'Private-route metadata guard missing');
const guard=app.slice(start,end);
assert.match(guard,/noindex,nofollow/,'Private and profile app routes must remain noindex');
console.log('PASS: HTML-first public discovery and early noindex for SPA recovery URLs');
