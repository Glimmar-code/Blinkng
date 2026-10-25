#!/usr/bin/env node
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {APPROVED_PUBLIC_PATHS,INDEXNOW_KEY,INDEXNOW_KEY_LOCATION,SITE,
 selectChangedPublicUrls,validateIndexNowUrls} from './indexnow_public_urls.mjs';

const all=APPROVED_PUBLIC_PATHS.map(p=>SITE+p);
const key=readFileSync('web/'+INDEXNOW_KEY+'.txt','utf8').trim();
assert.match(INDEXNOW_KEY,/^[a-f0-9]{32}$/);
assert.equal(key,INDEXNOW_KEY);
assert.equal(INDEXNOW_KEY_LOCATION,SITE+'/'+INDEXNOW_KEY+'.txt');
assert.equal(all.length,9);
assert.equal(new Set(all).size,all.length);
assert.deepEqual(
 selectChangedPublicUrls(['web/'+INDEXNOW_KEY+'.txt']),all,
 'First publication must notify all approved public URLs exactly once');
assert.deepEqual(selectChangedPublicUrls([],{bootstrap:true}),all);
assert.deepEqual(selectChangedPublicUrls([
 'web/index.html','web/students/index.html','web/marketplace/index.html',
 'web/app.js','app/src/main/java/com/example/MainActivity.kt'
]),[SITE+'/',SITE+'/students/',SITE+'/marketplace/']);
assert.deepEqual(selectChangedPublicUrls([
 'web/sitemap.xml','web/404.html','web/app.js','web/paystack-return.js'
]),[],'Sitemap-only and private SPA changes must not notify unrelated pages');
assert.deepEqual(selectChangedPublicUrls(['web/privacy/index.html']),[SITE+'/privacy/']);
assert.throws(()=>validateIndexNowUrls([SITE+'/messages/']),/Unapproved/);
assert.throws(()=>validateIndexNowUrls([SITE+'/settings/']),/Unapproved/);
assert.throws(()=>validateIndexNowUrls([SITE+'/students/?p=private']),/Unapproved/);
assert.throws(()=>validateIndexNowUrls([SITE+'/students/',SITE+'/students/']),/Unapproved/);
assert.throws(()=>validateIndexNowUrls(['https://evil.example/students/']),/Unapproved/);
validateIndexNowUrls(all);

const sitemap=readFileSync('web/sitemap.xml','utf8');
const locations=[...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map(m=>m[1]);
assert.deepEqual(locations,all,'Sitemap and approved IndexNow URL inventories must remain identical');
const workflow=readFileSync('.github/workflows/submit-blink-indexnow.yml','utf8');
const script=readFileSync('scripts/submit_blink_indexnow.mjs','utf8');
assert.match(workflow,/workflow_dispatch:/);
assert.doesNotMatch(workflow,/workflow_run:/);
assert.match(workflow,/github\.actor == 'Glimmar-code'/);
assert.match(workflow,/github\.ref == 'refs\/heads\/main'/);
assert.match(script,/checkLivePages\(\)/);
assert.match(script,/\[200,202\]/);
assert.match(script,/api\.indexnow\.org\/indexnow/);
assert.ok(!script.includes('google.com/ping'),'Google deprecated anonymous sitemap pings');
console.log('PASS: IndexNow safe nine-URL allowlist, key file, changed-file selection and owner-controlled manual deployment hook');
