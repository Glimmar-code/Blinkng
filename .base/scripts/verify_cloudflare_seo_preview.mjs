#!/usr/bin/env node
/**
 * BLINK public-only Cloudflare Pages preview smoke.
 * Validates the actual preview response, not the repository files or live main.
 * No authentication, private APIs, real user data, or production writes.
 *
 * Usage:
 * BLINK_SEO_PREVIEW_URL=https://<branch>.blinkng.pages.dev node scripts/verify_cloudflare_seo_preview.mjs
 */
import assert from 'node:assert/strict';
import {INDEXNOW_KEY} from './indexnow_public_urls.mjs';

const input=String(process.env.BLINK_SEO_PREVIEW_URL||'').trim();
assert.match(input,/^https:\/\/[a-z0-9-]+\.blinkng\.pages\.dev\/?$/,'Preview must be a BLINK Cloudflare Pages HTTPS deployment');
const base=input.replace(/\/$/,'');
const prod='https://blink.com.ng';
const slugs=['students','marketplace','safety'];
const approved=['/','/about/','/features/','/download/','/faq/','/privacy/',...slugs.map(s=>'/'+s+'/')];
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));

async function fetchPublic(path){
  const url=new URL(path,base+'/');
  const res=await fetch(url,{
    redirect:'manual',
    signal:AbortSignal.timeout(12000),
    headers:{'Accept':'text/html,application/xml,text/plain;q=0.9,*/*;q=0.8','Cache-Control':'no-cache'}
  });
  assert.equal(new URL(res.url).origin,base,'Unexpected off-preview response for '+path);
  return {res,html:await res.text()};
}
async function inspect(){
 const home=await fetchPublic('/');
 assert.equal(home.res.status,200,'Homepage HTTP status');
 assert.ok(home.html.includes('<main class="boot-static"'),'Homepage missing crawlable raw HTML summary');
 assert.ok(home.html.includes('class="boot-static-grid"'),'Homepage missing public feature summaries');
 assert.ok(home.html.includes('<link rel="canonical" href="'+prod+'/"'),'Homepage canonical is not the official domain');
 for(const slug of slugs){
   assert.ok(home.html.includes('href="./'+slug+'/"'),'Homepage missing raw link: '+slug);
 }
 const sitemap=await fetchPublic('/sitemap.xml');
 assert.equal(sitemap.res.status,200,'Sitemap HTTP status');
 const locations=[...sitemap.html.matchAll(/<loc>([^<]+)<\/loc>/g)].map(m=>m[1]);
 assert.deepEqual(locations,approved.map(p=>prod+p),'Unexpected sitemap URLs or order');
 const proof=await fetchPublic('/'+INDEXNOW_KEY+'.txt');
 assert.equal(proof.res.status,200,'IndexNow domain ownership key not served on Cloudflare Pages preview');
 assert.equal(proof.html.trim(),INDEXNOW_KEY,'IndexNow public key file content mismatch');
 const robots=await fetchPublic('/robots.txt');
 assert.equal(robots.res.status,200,'robots.txt HTTP status');
 assert.ok(robots.html.includes('Sitemap: '+prod+'/sitemap.xml'),'robots.txt missing production sitemap reference');
 for(const slug of slugs){
   const {res,html}=await fetchPublic('/'+slug+'/');
   assert.equal(res.status,200,'HTTP status for '+slug);
   assert.ok(/<h1>[^<]+<\/h1>/.test(html),'Missing static H1 for '+slug);
   assert.ok(html.includes('<link rel="canonical" href="'+prod+'/'+slug+'/">'),'Incorrect canonical for '+slug);
   assert.ok(html.includes('name="description"'),'Missing description for '+slug);
   assert.ok(html.includes('name="robots" content="index,follow'),'Public index metadata missing for '+slug);
   assert.ok(html.includes('href="../privacy/"'),'Missing privacy link on '+slug);
   const match=html.match(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/);
   assert.ok(match,'JSON-LD missing for '+slug);
   const structured=JSON.parse(match[1]);
   assert.equal(structured.url,prod+'/'+slug+'/','JSON-LD URL mismatch for '+slug);
 }
 const notFound=await fetchPublic('/seo-scan-this-route-does-not-exist-20261024');
 assert.equal(notFound.res.status,404,'Unknown route must return HTTP 404, not soft-404 200');
 assert.ok(notFound.html.includes('noindex,nofollow'),'404 must be excluded from search indexes');
 const download=await fetchPublic('/download/');
 assert.equal(download.res.status,200,'Download page status');
 assert.ok(download.html.includes('releases/latest/download/Blink-latest.apk'),'Official APK link changed in preview');
 console.log(JSON.stringify({result:'PASS',checks:'HTML-first home, public IndexNow key, nine canonical sitemap URLs, robots, three static pages, privacy, 404, download',preview:base,publicOnly:true}));
}
let error;
for(let attempt=1;attempt<=7;attempt++){
 try{await inspect();process.exit(0);}
 catch(e){
   error=e;
   console.warn('Preview check attempt '+attempt+'/7: '+String(e?.message||e).slice(0,320));
   if(attempt<7)await sleep(12000);
 }
}
console.error('FAIL Cloudflare preview:',String(error?.stack||error).slice(0,1600));
process.exitCode=1;
