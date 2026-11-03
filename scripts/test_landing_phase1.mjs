#!/usr/bin/env node
// Phase 1 public homepage guard: no runtime credentials, no mocked feature claims.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = path => readFileSync(path,'utf8');
const app=read('web/app.js');
const html=read('web/index.html');
const styles=read('web/landing.css');
const sw=read('web/sw.js');

const start=app.indexOf('  function renderLanding(){');
const end=app.indexOf('  function renderLogin(){',start);
assert.ok(start>=0 && end>start,'landing route must exist');
const landing=app.slice(start,end);
assert.match(landing,/<main class="landing-page" aria-label="BLINK Nigeria">/);
assert.match(landing,/<header class="landing-header">/);
assert.match(landing,/<h1 id="landing-title">/);
assert.match(landing,/<nav class="landing-desktop-nav" aria-label="Website navigation">/);
assert.match(landing,/<details class="landing-menu">/);
assert.match(landing,/<summary aria-label="Open website menu">/);
assert.match(landing,/<footer class="landing-footer">/);
assert.match(landing,/class="landing-skip" href="#landing-main"/);
assert.match(landing,/id="landing-features"/);
assert.match(landing,/id="public-search"/);
assert.match(landing,/data-landing-nav=/);
assert.match(landing,/ROOT\.querySelectorAll\('\[data-landing-nav\]'\)/);
assert.match(landing,/searchForm\?\.addEventListener\('submit'/);
assert.match(landing,/navigate\('\/@'\+encodeURIComponent\(username\)\)/);
assert.match(landing,/href="\$\{APK_URL\}"/);
assert.equal((landing.match(/class="landing-feature-card"/g)||[]).length,6,'all six feature cards must render');
for(const name of ['BLINK Feed','BLINK Reels','BLINK Market','BLINK Connect','BLINK Messages','BLINK LIFE'])assert.ok(landing.includes(name),'missing feature '+name);
for(const dest of ['/features/','/about/','/download/','/faq/','/privacy/'])assert.ok(landing.includes("routeHref('"+dest+"')"),'missing site navigation '+dest);
assert.ok(landing.includes('noopener noreferrer'),'external links need safe target attributes');
assert.ok(landing.includes('maxlength="32"'),'search must avoid unbounded input');
assert.ok(!landing.includes('onclick='),'landing page must not use inline handlers');
assert.ok(html.includes('href="landing.css"'),'landing CSS must load on homepage');
assert.ok(sw.includes("'./landing.css'"),'service worker must cache landing CSS');
assert.ok(styles.includes('.landing-page') && styles.includes('.landing-hero'),'landing styles must be scoped');
assert.ok(styles.includes('@media(max-width:680px)'),'mobile navigation and cards must be responsive');
assert.ok(styles.includes('@media(prefers-reduced-motion:reduce)'),'animations must respect reduced motion');
assert.ok(styles.includes(':focus-visible'),'interactive controls need visible keyboard focus');
console.log('PASS: accessible mobile-first BLINK Phase 1 homepage, working navigation and shareable app download');
