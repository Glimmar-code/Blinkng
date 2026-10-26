#!/usr/bin/env node
// Phase 2 website regression tests. University catalogue must mirror the shared cross-platform source.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=p=>readFileSync(p,'utf8');
const kotlin=read('shared/src/main/kotlin/com/blinkng/shared/discovery/BlinkNigerianUniversities.kt');
const exported=JSON.parse(read('web/campuses.json'));
assert.equal(exported.source,'shared/src/main/kotlin/com/blinkng/shared/discovery/BlinkNigerianUniversities.kt','Campus export must identify its shared Kotlin source');
const extract=kind=>{
 const m=kotlin.match(new RegExp('private val '+kind+' = listOf\\(([\\s\\S]*?)\\n    \\)'));
 assert.ok(m,'Missing Android university list: '+kind);
 return [...m[1].matchAll(/"((?:[^"\\]|\\.)*)"/g)].map(v=>v[1]);
};
const source=[...new Set(['federal','state','private','federalAdditional','stateAdditional','privateAdditional'].flatMap(extract))].sort((a,b)=>a.localeCompare(b,'en',{sensitivity:'base'}));
assert.deepEqual(exported.universities,source,'Website university choices must stay synced with Android');
assert.ok(source.includes('Federal University of Technology, Akure'),'FUTA must be available');
assert.equal(source.length,new Set(source).size,'No duplicate universities allowed');
const js=read('web/discovery.js'),app=read('web/app.js');
const index=read('web/index.html'),download=read('web/download/index.html'),sw=read('web/sw.js');
for(const text of ['get_public_web_profile','p_username:username','public-search-status','campus-search','campus-results','campus-selection','data-tour-tab','data-selected-campus'])assert.ok(app.includes(text),'Missing landing phase 2 component: '+text);
assert.ok(app.includes('BLINK_DISCOVERY?.initLanding(ROOT)'), 'Discovery lifecycle not initialized after guest route');
assert.ok(js.includes('fetch(prefix + \'campuses.json\''), 'Campus list not fetched by website');
assert.ok(js.includes('initReleaseInfo'), 'Release info lookup unavailable');
assert.ok(js.includes("data.tag_name") && js.includes("Blink-latest.apk"), 'Release metadata must come from official release asset');
assert.ok(js.includes('textContent'), 'Dynamic text must be rendered safely');
assert.ok(js.includes("n o u n")&&js.includes('futa'), 'University abbreviations are missing');
assert.ok(index.includes('src="discovery.js" defer') && index.indexOf('src="discovery.js"')<index.indexOf('src="app.js"'), 'Discovery handlers must load before main app');
assert.ok(download.includes('id="blink-release-info"') && download.includes('src="../discovery.js" defer'), 'Official download screen must show release version and size');
assert.ok(sw.includes("'./discovery.js'") && sw.includes("'./campuses.json'"), 'Offline shell should cache public discovery assets');
assert.ok(app.includes('Sign in to connect'), 'Campus discovery must not expose authenticated connect data anonymously');
const boundaries=['BLINK Feed','BLINK Reels','BLINK Market','BLINK Connect'];
boundaries.forEach(v=>assert.ok(app.includes(v),'Feature tour missing '+v));
new Function(js);new Function(app);
console.log('PASS: Phase 2 anonymous-safe profile search, synchronized universities, feature tour and verified release metadata');
