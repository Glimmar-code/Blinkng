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
// Before production publication, validate only currently published informational
// pages. On successful deployment of this candidate, require all new SEO pages.
const postDeploy=process.env.BLINK_SEO_POST_DEPLOY==='1';
const existingPublicPaths=['/','/about/','/features/','/download/','/faq/','/privacy/'];
const newPublicPaths=['/students/','/marketplace/','/safety/'];
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
 assert.equal(matches.length,new Set(matches).size,'Sitemap has duplicate locations');
 assert.ok(matches.every(url=>url.startsWith(site+'/')),'Sitemap contains external or noncanonical URLs');
 const required=existingPublicPaths.map(path=>site+path);
 if(postDeploy){
  assert.deepEqual(matches,[...existingPublicPaths,...newPublicPaths].map(path=>site+path),'Deployed sitemap must contain all nine public pages');
 }else{
  assert.ok(required.every(url=>matches.includes(url)),'Production sitemap is missing a baseline page');
 }
 assert.ok(robots.includes('User-agent: *'),'Missing public crawler directive');
 assert.ok(robots.includes('Allow: /'),'Public crawling not permitted');
 assert.ok(robots.includes('Sitemap: '+site+'/sitemap.xml'),'Sitemap discovery directive missing');
});
if(postDeploy){
 await check('New SEO pages are live with static content and correct canonicals',async()=>{
  for(const slug of ['students','marketplace','safety']){
   const {res,body}=await get('/'+slug+'/');
   assert.match(body,/<h1>[^<]+<\/h1>/,'Missing readable H1: '+slug);
   assert.ok(body.includes('<link rel="canonical" href="'+site+'/'+slug+'/">'),'Wrong canonical: '+slug);
   assert.ok(body.includes('<meta name="robots" content="index,follow'),'SEO page is not crawlable: '+slug);
   assert.ok(!/noindex/i.test(res.headers.get('x-robots-tag')||''),'Server marked public page noindex: '+slug);
  }
 });
 await check('HTML-first homepage visible to no-JavaScript crawlers',async()=>{
  const {body}=await get('/');
  assert.ok(body.includes('<main class="boot-static"'),'Published homepage lacks HTML-first discovery content');
  for(const path of newPublicPaths){
   assert.ok(body.includes('href=".'+path+'"'),'Missing initial HTML link: '+path);
  }
 });
}
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
// Advisory checks identify DNS/Cloudflare or Pages configuration that cannot
// be repaired by changing GitHub's static files alone. Never claim they passed.
const warnings=[];
async function diagnose(name,url,validate){
 try{
  const response=await fetch(url,{
   redirect:'manual',
   headers:{Accept:'text/html'},
   signal:AbortSignal.timeout(12000)
  });
  const location=response.headers.get('location')||'';
  if(!validate(response,location)){
   warnings.push(name+': HTTP '+response.status+'; Location '+location.slice(0,100));
   console.warn('WARN '+warnings.at(-1));
  }else console.log('PASS advisory '+name);
 }catch(e){
  warnings.push(name+': '+String(e.message).slice(0,100));
  console.warn('WARN '+warnings.at(-1));
 }
}
await diagnose('Canonical www host redirects to apex','https://www.blink.com.ng/',(res,loc)=>{
 try{
  return [301,302,307,308].includes(res.status)
   &&new URL(loc,'https://www.blink.com.ng/').origin===site;
 }catch{return false;}
});
await diagnose('HTTP redirects to canonical HTTPS','http://blink.com.ng/',(res,loc)=>{
 try{
  return [301,302,307,308].includes(res.status)
   &&new URL(loc,'http://blink.com.ng/').protocol==='https:';
 }catch{return false;}
});
await diagnose('Missing page returns actual HTTP 404',site+'/seo-verification-nonexistent-page-20261024',(res)=>res.status===404);
console.log(JSON.stringify({checksPassed:checks.length,checksFailed:fail.length,advisoryWarnings:warnings.length,publicOnly:true}));

if(fail.length){console.error('Public-integration failures:',fail.join(' | '));process.exitCode=1;}
