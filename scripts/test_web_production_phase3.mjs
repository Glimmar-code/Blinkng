#!/usr/bin/env node
// BLINK Phase 3: service-worker privacy, upgrade and offline regression tests.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const source=readFileSync('web/sw.js','utf8');
const page=readFileSync('web/index.html','utf8');
const handlers={};
const putCalls=[];
const deleted=[];
const waited=[];
let networkCalls=[];
let responseMode='ok';

function reply(status=200){
  return {
    ok:status>=200 && status<300,status,type:'basic',
    headers:{get:()=>null},
    clone(){return reply(status);}
  };
}
const offline=reply(200);
const stored=new Map([['./index.html',offline],['https://blink.com.ng/blink-logo-v2.png',reply(200)]]);
const cache={
  addAll:async paths=>{ assert.ok(paths.includes('./discovery.js') && paths.includes('./campuses.json') && paths.includes('./contest-invitation.js') && paths.includes('./contest-share.css') && paths.includes('./landing-relaunch.js')); },
  match:async key=>stored.get(typeof key==='string'?key:key.url),
  put:async(key,value)=>{putCalls.push(typeof key==='string'?key:key.url);stored.set(typeof key==='string'?key:key.url,value);}
};
const cacheNames=['blink-web-v23-homepage-phase1','blink-web-v25-production-safe-cache','other-website-assets','blink-web-v26-mobile-lean-shell','blink-web-v28-link-previews','blink-web-v29-download-landing'];
const caches={
  open:async name=>{assert.ok(name==='blink-web-v30-landing-contest');return cache;},
  keys:async()=>cacheNames,
  delete:async name=>{deleted.push(name);return true;}
};
const self={
  registration:{scope:'https://blink.com.ng/'},
  location:{origin:'https://blink.com.ng'},
  addEventListener:(name,handler)=>{handlers[name]=handler;},
  skipWaiting:async()=>{},
  clients:{claim:async()=>{}}
};
const fetch=async req=>{
  networkCalls.push(req.url || String(req));
  if(responseMode==='offline')throw new Error('Offline');
  if(responseMode==='notfound')return reply(404);
  return reply(200);
};
const Response={error:()=>reply(0)};
runInNewContext(source,{self,caches,fetch,Response,URL,console});

function dispatch(url,{mode='cors',method='GET',authorized=false}={}){
  let response;const wait=[];
  const request={url,mode,method,headers:{has:key=>key==='Authorization'&&authorized}};
  handlers.fetch({request,respondWith:promise=>{response=promise;},waitUntil:p=>wait.push(Promise.resolve(p))});
  return {response,wait};
}
const installTasks=[];
handlers.install({waitUntil:p=>installTasks.push(Promise.resolve(p))});
await Promise.all(installTasks);
const activation=[];
handlers.activate({waitUntil:p=>activation.push(Promise.resolve(p))});
await Promise.all(activation);
assert.deepEqual(deleted,['blink-web-v23-homepage-phase1','blink-web-v25-production-safe-cache','blink-web-v26-mobile-lean-shell','blink-web-v28-link-previews','blink-web-v29-download-landing'],'Only old BLINK caches may be removed; other apps are untouched');

networkCalls=[];putCalls.length=0;
const profile=dispatch('https://blink.com.ng/@person',{mode:'navigate'});
assert.ok(profile.response,'Navigation must be handled');
assert.equal((await profile.response).status,200);
await Promise.all(profile.wait);
assert.equal(putCalls.length,0,'Never cache personal profile or authenticated routes');

responseMode='notfound';
const fallback=dispatch('https://blink.com.ng/post/somepost',{mode:'navigate'});
assert.equal(await fallback.response,offline,'404 SPA navigation should use cached public shell');
responseMode='offline';
const offroute=dispatch('https://blink.com.ng/messages',{mode:'navigate'});
assert.equal(await offroute.response,offline,'Offline app routes may use the static shell without caching private route URLs');
responseMode='ok';

assert.equal(dispatch('https://blink.com.ng/api/private-data').response,undefined,'Never intercept unlisted API routes');
assert.equal(dispatch('https://blink.com.ng/app.js?session=secret').response,undefined,'Never cache query-string requests');
assert.equal(dispatch('https://blink.com.ng/app.js',{authorized:true}).response,undefined,'Never cache authorization-bearing requests');
assert.equal(dispatch('https://other.site/app.js').response,undefined,'Never intercept external origins');
assert.equal(dispatch('https://blink.com.ng/app.js',{method:'POST'}).response,undefined,'Never intercept write methods');

putCalls.length=0;networkCalls=[];
const script=dispatch('https://blink.com.ng/app.js');
assert.ok(script.response,'Current static assets must be handled');
assert.equal((await script.response).status,200);
await Promise.all(script.wait);
assert.deepEqual(putCalls,['https://blink.com.ng/app.js'],'Only whitelisted public asset may be refreshed');
assert.deepEqual(networkCalls,['https://blink.com.ng/app.js']);

putCalls.length=0;networkCalls=[];
const image=dispatch('https://blink.com.ng/blink-logo-v2.png');
assert.ok(image.response);
assert.equal((await image.response).status,200);
await Promise.all(image.wait);
assert.equal(networkCalls.length,1,'Cached logo should refresh in background');

assert.ok(!source.includes('caches.match(request)'), 'Never store or retrieve arbitrary request URLs from global caches');
assert.ok(!source.includes('cache.put(request,copy)'), 'No unrestricted dynamic response caching');
assert.ok(page.includes('<meta name="referrer" content="strict-origin-when-cross-origin"'), 'Do not share private page paths in third-party referral headers');
assert.match(readFileSync('web/_worker.js','utf8'), /contest/, 'Cloudflare handles contest routes');
assert.ok(page.includes('contest-invitation.js'),'Contest invitation JS is loaded');
assert.match(readFileSync('web/contest-invitation.js','utf8'), /get_blink_contest_candidate/,
    'Contest invite resolves approved candidate only');
console.log('PASS: phase 3 private-route caching, offline fallback, scoped cache cleanup and fresh static assets');
