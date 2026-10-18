#!/usr/bin/env node
// Phase 3 step 2: behavioral tests for timeouts, OAuth safety and private SEO.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const app=readFileSync('web/app.js','utf8');
const index=readFileSync('web/index.html','utf8');
const getBlock=(start,end)=>{
 const a=app.indexOf(start),b=app.indexOf(end,a+start.length);
 assert.ok(a>=0&&b>a,'Missing web implementation section: '+start);
 return app.slice(a,b);
};
const fetchBlock=getBlock('  async function fetchWithTimeout(', '\n  function toast(');
assert.ok(fetchBlock.includes('signal: controller.signal'),'All fetches need combined caller+timeout cancellation');
assert.ok(fetchBlock.includes("upstream?.removeEventListener('abort',cancelFromCaller)"),'Event listeners must be cleaned up');
let currentFetch;
const context={
  API_TIMEOUT_MS:35,
  AbortController,
  setTimeout,
  clearTimeout,
  fetch:(url,options)=>currentFetch(url,options),
};
runInNewContext(fetchBlock+'\nglobalThis.exposedFetch = fetchWithTimeout;',context);
const fn=context.exposedFetch;
const abortErr=()=>Object.assign(new Error('aborted'),{name:'AbortError'});
currentFetch=async (url,options)=>{
 assert.ok(options.signal,'Missing timeout signal');
 return {ok:true,url};
};
assert.equal((await fn('/ok')).url,'/ok','Successful requests must pass through');
currentFetch=(url,options)=>new Promise((resolve,reject)=>{
 if(options.signal.aborted){reject(abortErr());return;}
 options.signal.addEventListener('abort',()=>reject(abortErr()),{once:true});
});
await assert.rejects(fn('/slow',{},12),/Request timed out/,'Hanging requests must be bounded');
const stop=new AbortController();
const pending=fn('/cancel',{signal:stop.signal},500);
stop.abort();
await assert.rejects(pending,err=>err?.name==='AbortError','Caller cancellation must not be misreported as timeout');
const aborted=new AbortController();aborted.abort();
await assert.rejects(fn('/already-cancelled',{signal:aborted.signal},100),err=>err?.name==='AbortError');

const privacyBlock=getBlock('  function updateRoutePrivacy(path){','\n  async function render(');
const attrs={robots:'index,follow,max-image-preview:large',referrer:'strict-origin-when-cross-origin'};
const document={querySelector:selector=>({
 setAttribute:(key,value)=>{attrs[selector.includes('robots')?'robots':'referrer']=value;}
})};
const seo={document};
runInNewContext(privacyBlock+'\nglobalThis.updateRoutePrivacy = updateRoutePrivacy;',seo);
for(const route of ['/login','/feed','/settings/password','/@publicname','/messages']){
 seo.updateRoutePrivacy(route);
 assert.equal(attrs.robots,'noindex,nofollow',`Private BLINK web route must not be indexed: ${route}`);
 assert.equal(attrs.referrer,'no-referrer',`Private BLINK route should not send full referrers: ${route}`);
}
seo.updateRoutePrivacy('/');
assert.equal(attrs.robots,'index,follow,max-image-preview:large');
assert.equal(attrs.referrer,'strict-origin-when-cross-origin');
const authBlock=getBlock('  async function bootstrapSession(){','\n  async function loadMyProfile()');
assert.ok(authBlock.includes("!refresh_token || !Number.isFinite(expires_in) || expires_in<=0"),'Incomplete OAuth tokens must be rejected');
assert.ok(authBlock.includes("history.replaceState({},'',routeHref('/login'))"),'Failed OAuth must clear URL fragment');
assert.ok(authBlock.includes('Google sign-in could not be completed. Please try again.'),'OAuth error must not echo arbitrary error description');
assert.ok(authBlock.includes("const next=type==='recovery'?'/settings/password'"),'Recovery must target password update screen');
assert.ok(app.includes("const redirect=SITE_URL+'/settings/password'"),'Password reset uses configured canonical domain');
assert.ok(app.includes("redirect_to:`\${redirectBase}/`"),'Google OAuth uses canonical redirect');
assert.ok(index.includes('<link rel="canonical" href="https://blink.com.ng/" />'),'Public homepage SEO canonical is preserved');
assert.ok(index.includes('<meta name="referrer" content="strict-origin-when-cross-origin" />'),'Website needs a referrer policy');
console.log('PASS: bounded API timeouts, external aborts, safe OAuth errors and route-specific SEO privacy');
