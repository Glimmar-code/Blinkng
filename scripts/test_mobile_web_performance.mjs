#!/usr/bin/env node
// BLINK Phase 3 mobile performance budget & private local-only diagnostics.
import assert from 'node:assert/strict';
import {readFileSync,statSync} from 'node:fs';

const read=p=>readFileSync(p,'utf8');
const sizes={
  'web/app.js':160_000,
  'web/link-previews.js':10_000,
  'web/profile-theme.js':10_000,
  'web/parity.js':115_000,
  'web/discovery.js':20_000,
  'web/styles.css':35_000,
  'web/landing.css':25_000,
  'web/parity.css':35_000,
  'web/campuses.json':25_000,
  'web/index.html':15_000
};
for(const [path,maxBytes] of Object.entries(sizes)){
  const bytes=statSync(path).size;
  assert.ok(bytes<=maxBytes,`${path} grew to ${bytes} bytes; mobile budget is ${maxBytes} bytes`);
}
const initialBytes=['web/app.js','web/link-previews.js','web/profile-theme.js','web/parity.js','web/discovery.js','web/styles.css','web/landing.css','web/parity.css']
  .reduce((sum,path)=>sum+statSync(path).size,0);
assert.ok(initialBytes<=360_000,'Mobile first-party JS+CSS exceeds 360 KB uncompressed budget');

const sw=read('web/sw.js');
const diagnostics=read('web/performance-diagnostics.js');
const discovery=read('web/discovery.js');
const manifest=JSON.parse(read('web/manifest.webmanifest'));
const html=read('web/index.html');
assert.ok(sw.includes("blink-web-v30-landing-contest"),'Mobile service worker cache version mismatch');
assert.ok(!sw.includes("'./blink-logo.png'"),'Do not precache large 512px PWA icon on each phone');
assert.ok(manifest.icons.some(icon=>icon.src==='pwa-512.png'),'PWA icon must remain available on demand');
assert.ok(sw.includes("'./blink-logo-v2.png'"),'Lightweight BLINK logo should remain in offline shell');
assert.ok(sw.includes("'./link-previews.js'")&&sw.includes("'./discovery.js'")&&sw.includes("'./campuses.json'"),'Discovery should be available offline');
assert.ok(sw.includes("url.search")&&sw.includes("request.headers.has('Authorization')"),'User-specific navigation URLs must not be cached');
assert.ok(discovery.includes("get('blink_perf')==='1'"),'Diagnostics must require explicit user opt-in');
assert.ok(discovery.includes("import(prefix+'performance-diagnostics.js')"),'Diagnostics must load lazily, not for every visitor');
assert.ok(!html.includes('performance-diagnostics.js'),'Diagnostics must not load on ordinary homepage visits');
for(const forbidden of [/\bfetch\s*\(/, /\bXMLHttpRequest\b/, /\bsendBeacon\s*\(/, /\blocalStorage\b/, /\bsessionStorage\b/, /\bdocument\.cookie\b/]){
  assert.ok(!forbidden.test(diagnostics),`Opt-in performance diagnostics must not collect/send/store private information: ${forbidden}`);
}
for(const metric of ['largest-contentful-paint','layout-shift','first-contentful-paint','event','transferSize']){
  assert.ok(diagnostics.includes(metric),`Missing local-only mobile metric: ${metric}`);
}
for(const metric of ['fcp','lcp','cls','inp','ttfb']){
  assert.ok(diagnostics.includes(metric),'Missing field '+metric);
}
assert.ok(diagnostics.includes("console.info('[BLINK performance diagnostics — local only]'"),'Report should stay within browser developer console');
assert.ok(read('web/landing.css').includes('@media(max-width:680px)'),'Mobile landing breakpoints missing');
assert.ok(read('web/landing.css').includes('prefers-reduced-motion'),'Site must respect reduced-motion');
new Function(discovery);
new Function(diagnostics);
new Function(sw);
console.log(`PASS: mobile HTML/CSS/JS budget ${Math.ceil(initialBytes/1024)} KiB, privacy-safe opt-in web vitals, lean PWA cache`);
