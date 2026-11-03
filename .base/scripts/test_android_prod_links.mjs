#!/usr/bin/env node
// Prevent BLINK Android share URL, intent filter and signed domain drift.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=p=>readFileSync(p,'utf8');
const gradle=read('app/build.gradle.kts');
const manifest=read('app/src/main/AndroidManifest.xml');
const router=read('app/src/main/java/com/example/sharing/DeepLinkRouter.kt');
const association=JSON.parse(read('web/.well-known/assetlinks.json'));
assert.ok(gradle.includes('?: "https://blink.com.ng"'),'Production app shares must use apex canonical domain by default');
assert.ok(gradle.includes('manifestPlaceholders["shareHost"] = resolvedShareHost'));
assert.ok(gradle.includes('manifestPlaceholders["sharePathPrefix"] = resolvedSharePathPrefix'));
assert.ok(manifest.includes('android:autoVerify="true"'),'Verified Android App Links filter required');
assert.ok(manifest.includes('android:host="${shareHost}"'),'Verified host must derive from share URL');
for(const route of ['/@','/profile/','/post/','/reel/']){
  assert.ok(manifest.includes('android:pathPrefix="${sharePathPrefix}'+route+'"'),'Missing verified path '+route);
}
for(const kind of ['PROFILE','POST','REEL','MARKET']){
  assert.ok(router.includes('ShareContentType.'+kind),'Shared '+kind+' app routes must be recognized');
}
assert.ok(router.includes('Uri.parse("https://blink.com.ng")'),'Canonical domain must be accepted by app link router');
assert.ok(router.includes('uri.scheme.equals("https"'),'Reject untrusted non-HTTPS site schemes');
const expected='88:B4:18:51:36:2B:F2:BC:B2:60:58:91:49:3B:B4:1C:40:F3:23:31:84:DF:77:35:69:F8:7D:B9:03:C5:26:64';
assert.ok(association.some(item=>
  item.relation?.includes('delegate_permission/common.handle_all_urls')
  && item.target?.package_name==='com.aistudio.blink.appvtwo'
  && item.target?.sha256_cert_fingerprints?.includes(expected)
),'Production Android Digital Asset Links signature/package mismatch');
const fallback=read('web/404.html');
assert.ok(fallback.includes('location.hash'),'SPA fallback must preserve Android and email callback fragments');
console.log('PASS: production apex domain, Android verified profile/post/reel/market deep links, signed assetlinks and fallback');
