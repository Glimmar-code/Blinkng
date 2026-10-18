#!/usr/bin/env node
/**
 * BLINK production public-integration smoke. Never signs into an account, handles
 * credentials, inspects private data, or accesses Google Search Console.
 * When BLINK_VERIFY_SYNTHETIC_ANALYTICS=1, submit exactly ONE anonymous test
 * page view. This will increment the approximate live metrics by one.
 */
import assert from 'node:assert/strict';

const site='https://blink.com.ng';
const supabase='https://jhwgifrlxwspoedxjaly.supabase.co';
const apk='https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk';
const fail=[];
const checks=[];
const get=async(path)=>{
  const res=await fetch(site+path,{headers:{Accept:'text/html,application/xml,application/json,*/*'},signal:AbortSignal.timeout(18000),redirect:'follow'});
  if(!res.ok)throw new Error(path+': HTTP '+res.status);
  if(new URL(res.url).origin!==site)throw new Error(path+': unexpected redirect off canonical domain');
  return {res,body:await res.text()};
};
async function check(name,fn){
  try{await fn();checks.push(name);console.log('PASS '+name);}
  catch(e){fail.push(name+': '+String(e.message).slice(0,180));console.error('FAIL '+name+': '+String(e.message).slice(0,180));}
}

await check('HTTPS official homepage and canonical SEO',async()=>{
 const {body}=await get('/');
 assert.match(body,/<link rel="canonical" href="https:\/\/blink\.com\.ng\/"/);
 assert.match(body,/<meta name="robots" content="index,follow/);
 assert.match(body,/<script src="site-analytics\.js" defer><\/script>/);
});
await check('Public informational pages and canonical URLs',async()=>{
 for(const slug of ['about','features','download','faq','privacy']){
  const {body}=await get('/'+slug+'/');
  assert.ok(body.includes('<link rel="canonical" href="'+site+'/'+slug+'/"'),slug+' canonical mismatch');
  assert.ok(body.includes('site-analytics.js'),slug+' missing public analytics script');
 }
});
await check('Live sitemap and robots discovery',async()=>{
 const [{body:sitemap},{body:robots}]=await Promise.all([get('/sitemap.xml'),get('/robots.txt')]);
 const matches=[...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map(m=>m[1]);
 assert.deepEqual(matches,['/','/about/','/features/','/download/','/faq/','/privacy/'].map(s=>site+s));
 assert.ok(robots.includes('Sitemap: '+site+'/sitemap.xml'));
});
await check('Published assetlinks and Android package/certificate',async()=>{
 const {body}=await get('/.well-known/assetlinks.json');
 const links=JSON.parse(body);
 assert.ok(links.some(x=>x.relation?.includes('delegate_permission/common.handle_all_urls')
   &&x.target?.package_name==='com.aistudio.blink.appvtwo'
   &&x.target?.sha256_cert_fingerprints?.includes('88:B4:18:51:36:2B:F2:BC:B2:60:58:91:49:3B:B4:1C:40:F3:23:31:84:DF:77:35:69:F8:7D:B9:03:C5:26:64')));
});
await check('Production analytics enabled only for public pages',async()=>{
 const {body}=await get('/site-analytics.js');
 assert.ok(body.includes('const ENABLED = true;'),'Production JS has stale inactive analytics');
 assert.ok(body.includes("location.hostname!=='blink.com.ng'"));
 assert.ok(body.includes('navigator.globalPrivacyControl === true'));
 assert.ok(body.includes("navigator.doNotTrack === '1'"));
});
await check('Official APK link and release redirect',async()=>{
 const {body}=await get('/download/');
 assert.ok(body.includes(apk),'Public download page lost latest signed APK alias');
});
await check('Authentication provider public readiness (no sign-in)',async()=>{
 const resp=await fetch(supabase+'/auth/v1/settings',{
  headers:{apikey:'sb_publishable_2lfIyTW9SwiTOvafGXp10Q_I7DwUs-J',Accept:'application/json'},
  signal:AbortSignal.timeout(14000)
 });
 if(!resp.ok)throw new Error('Public Supabase Auth settings endpoint returned HTTP '+resp.status);
 const settings=await resp.json();
 if(settings.external?.google!==true)throw new Error('Google provider is not enabled in Supabase Auth settings; enable it in Supabase Auth > Providers');
 console.log('PASS Google identity provider enabled (no real user login attempted)');
});
if(process.env.BLINK_VERIFY_SYNTHETIC_ANALYTICS==='1'){
 await check('One synthetic anonymous metric accepted by live Edge handler',async()=>{
  const response=await fetch(supabase+'/functions/v1/blink-site-analytics',{
   method:'POST',
   headers:{Origin:site,'Content-Type':'application/json'},
   body:JSON.stringify({page:'faq',kind:'view'}),
   signal:AbortSignal.timeout(14000)
  });
  if(response.status!==204)throw new Error('Analytics backend returned HTTP '+response.status+', expected 204');
  console.log('INFO One synthetic FAQ view was sent for deployment diagnostics; not a unique visitor.');
 });
}
console.log(JSON.stringify({checksPassed:checks.length,checksFailed:fail.length,publicOnly:true}));
if(fail.length){console.error('Public-integration failures:',fail.join(' | '));process.exitCode=1;}
