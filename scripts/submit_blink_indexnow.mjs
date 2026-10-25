#!/usr/bin/env node
/**
 * Notify Bing/IndexNow participants about changed BLINK public pages.
 * Safe by default: URLs are chosen from an explicit public allowlist, and
 * IndexNow is only called after the key and every page are served on the
 * canonical HTTPS production host. Submission is NOT index confirmation.
 */
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {APPROVED_PUBLIC_PATHS,INDEXNOW_KEY,INDEXNOW_KEY_LOCATION,
  SITE,selectChangedPublicUrls,validateIndexNowUrls} from './indexnow_public_urls.mjs';

const repoRoot=fileURLToPath(new URL('../',import.meta.url));
const bootstrap=process.env.BLINK_INDEXNOW_BOOTSTRAP_ALL==='1';
const dryRun=process.env.BLINK_INDEXNOW_DRY_RUN==='1';
const getChangedFiles=()=>execFileSync('git',['diff','--name-only','HEAD^1','HEAD','--','web/'],{
 cwd:repoRoot,encoding:'utf8',timeout:15000
}).split(/\r?\n/).filter(Boolean);
const changedFiles=getChangedFiles();
const urls=validateIndexNowUrls(selectChangedPublicUrls(changedFiles,{bootstrap}));

const sitemap=readFileSync(new URL('../web/sitemap.xml',import.meta.url),'utf8');
const sitemapUrls=[...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map(m=>m[1]);
assert.deepEqual(sitemapUrls,APPROVED_PUBLIC_PATHS.map(p=>SITE+p),
 'IndexNow allowlist must match the exact nine intentionally public canonical sitemap URLs');
if(!urls.length){
 console.log('SKIP IndexNow: no changed public HTML pages in the main deployment');
 process.exit(0);
}
if(dryRun){
 console.log(JSON.stringify({mode:'dry-run',urls,changedFiles,publicOnly:true}));
 process.exit(0);
}
const fetchTimeout=async(url,options={})=>fetch(url,{...options,signal:AbortSignal.timeout(12000)});
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const keyPath='/'+INDEXNOW_KEY+'.txt';

async function checkLivePages(){
 const keyRes=await fetchTimeout(INDEXNOW_KEY_LOCATION+'?verify=1',{headers:{'Cache-Control':'no-cache'},redirect:'follow'});
 assert.equal(keyRes.status,200,'IndexNow ownership key is not published on the live host');
 assert.equal(new URL(keyRes.url).origin,SITE,'Key redirected away from BLINK');
 assert.equal((await keyRes.text()).trim(),INDEXNOW_KEY,'IndexNow key content mismatch');
 for(const url of urls){
  const result=await fetchTimeout(url+'?indexnow-check=1',{headers:{Accept:'text/html','Cache-Control':'no-cache'},redirect:'follow'});
  assert.equal(result.status,200,url+' is not live');
  assert.equal(new URL(result.url).origin,SITE,'Offsite page redirect: '+url);
  const html=await result.text();
  assert.ok(html.includes('<link rel="canonical" href="'+url+'"'),'Canonical mismatch: '+url);
  assert.ok(/<h1(?:\s|>)/.test(html),'No public page heading: '+url);
  assert.ok(!/<meta\s+name=["']robots["']\s+content=["'][^"']*noindex/i.test(html),
    'Noindex page must never be submitted: '+url);
 }
}

let verified=false, lastError;
for(let attempt=1;attempt<=8;attempt++){
 try{await checkLivePages();verified=true;break;}
 catch(error){
  lastError=error;
  console.warn('Live public indexing readiness '+attempt+'/8: '+String(error.message).slice(0,240));
  if(attempt<8)await pause(15000);
 }
}
if(!verified)throw new Error('IndexNow withheld: '+String(lastError?.message));

const payload={
 host:new URL(SITE).hostname,
 key:INDEXNOW_KEY,
 keyLocation:INDEXNOW_KEY_LOCATION,
 urlList:urls
};
const response=await fetchTimeout('https://api.indexnow.org/indexnow',{
 method:'POST',headers:{'Content-Type':'application/json; charset=utf-8'},
 body:JSON.stringify(payload),redirect:'manual'
});
const responseBody=(await response.text()).slice(0,500);
if(![200,202].includes(response.status)){
 throw new Error('IndexNow URL notification failed: HTTP '+response.status+' '+responseBody);
}
console.log(JSON.stringify({
 service:'IndexNow',status:response.status,
 receipt:response.status===200?'received':'received-key-verification-pending',
 urlsSubmitted:urls.length,
 publicOnly:true,
 note:'Receipt acknowledges URL notification, not Bing indexing. Google Search Console requires separate verified ownership.'
}));
