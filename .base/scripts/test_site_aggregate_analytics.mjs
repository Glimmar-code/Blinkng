#!/usr/bin/env node
// Explicitly activated public-only analytics. Test behavior, opt-outs and privacy.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const read=p=>readFileSync(p,'utf8');
const web=read('web/site-analytics.js');
const edge=read('supabase/functions/blink-site-analytics/index.ts');
const migration=read('supabase/migrations/20261018090100_blink_site_aggregate_metrics.sql');
const config=read('supabase/config.toml');
const app=read('web/app.js');
const privacy=read('web/privacy/index.html');

function simulate({path='/',host='blink.com.ng',protocol='https:',gpc=false,dnt='0',search=''}={}){
  const requests=[],listeners=new Map();
  const location={hostname:host,pathname:path,protocol,search,href:protocol+'//'+host+path};
  const Element=class {
    constructor(href){this.href=href;}
    closest(selector){return selector==='a[href]'?this:null;}
  };
  const doc={
    readyState:'complete',
    addEventListener:(event,callback)=>listeners.set(event,callback)
  };
  runInNewContext(web,{
    window:{},location,
    navigator:{globalPrivacyControl:gpc,doNotTrack:dnt},
    document:doc,Element,
    URL,URLSearchParams,
    fetch:(url,options)=>{requests.push({url,options});return Promise.resolve({status:204});}
  });
  return {requests,listeners,Element};
}
const home=simulate();
assert.equal(home.requests.length,1,'BLINK public homepage should send one view event');
assert.deepEqual(JSON.parse(home.requests[0].options.body),{page:'home',kind:'view'});
const download=simulate({path:'/download/'});
assert.equal(download.requests.length,1);
assert.deepEqual(JSON.parse(download.requests[0].options.body),{page:'download',kind:'view'});
assert.ok(download.listeners.has('click'),'Download click listener missing');
const asset='https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk';
download.listeners.get('click')({target:new download.Element(asset)});
assert.equal(download.requests.length,2,'Official Android APK tap should increment once');
assert.deepEqual(JSON.parse(download.requests[1].options.body),{page:'download',kind:'download'});
download.listeners.get('click')({target:new download.Element('https://github.com/other/project/releases/latest/download/Blink-latest.apk')});
assert.equal(download.requests.length,2,'External APK links must not count');
for(const value of ['/login','/feed','/messages','/site-stats','/@private-username']){
  assert.equal(simulate({path:value}).requests.length,0,'Private/auth route cannot be tracked: '+value);
}
for(const settings of [{gpc:true},{dnt:'1'},{host:'evil-example.com'},{host:'glimmar-code.github.io'},{protocol:'http:'}]){
  assert.equal(simulate(settings).requests.length,0,'Respect browser privacy setting or official HTTPS origin');
}
for(const req of [...home.requests,...download.requests]){
  assert.equal(req.options.method,'POST');
  assert.equal(req.options.credentials,'omit');
  assert.equal(req.options.referrerPolicy,'no-referrer');
  assert.deepEqual(Object.keys(JSON.parse(req.options.body)).sort(),['kind','page'],'Only two public aggregate fields permitted');
  assert.ok(!req.options.headers.Authorization&&!req.options.headers.Cookie,'No user credentials may be sent');
}
assert.ok(web.includes('const ENABLED = true'),'Owner-approved activation must be explicit');
assert.ok(edge.includes('Deno.env.get("BLINK_SITE_ANALYTICS_ENABLED")==="false"'),'Emergency backend kill switch must remain');
assert.ok(web.includes("navigator.globalPrivacyControl === true")&&web.includes("navigator.doNotTrack === '1'"),'Respect GPC/DNT');
assert.ok(!/(?:localStorage|sessionStorage|document\.cookie|navigator\.userAgent)/.test(web),'Do not persist identifiers');
assert.ok(!/\bconsole\.log\(|\bconsole\.error\(/.test(edge),'Edge handler must not log requests');
assert.ok(edge.includes('request.headers.get("origin")!==OFFICIAL_ORIGIN'),'Require canonical origin');
assert.ok(edge.includes('raw.length>160'),'Limit request size');
assert.ok(edge.includes('Object.keys(parsed).some(k=>k!=="page"&&k!=="kind")'),'No extra identifying fields');
assert.ok(config.includes('[functions.blink-site-analytics]')&&config.includes('verify_jwt = false'),'Endpoint configuration missing');
assert.ok(migration.includes('create table if not exists public.blink_site_metric_daily'),'Aggregate data table missing');
assert.ok(!/(?:ip_address|user_id|device_id|session_id|fingerprint|email|user_agent)\s+(?:text|uuid|varchar|inet|jsonb)/i.test(migration),'No visitor ID columns');
assert.ok(migration.includes('limiter.total < 120'),'Rate cap required');
assert.ok(migration.includes('revoke all on function public.record_blink_site_metric(text,text)'),'Writer must not be public');
assert.ok(migration.includes('to service_role;'),'Server-only writer permission required');
assert.ok(migration.includes('private.require_blink_admin()'),'Database admin guard required');
assert.ok(app.includes("if(path==='/site-stats')return renderSiteAnalytics()"),'Admin dashboard route missing');
assert.ok(app.includes("rpc('get_blink_site_metrics',{p_days:30})"),'Admin metrics RPC missing');
assert.ok(privacy.includes('BLINK counts visits to six public information pages'),'Privacy policy must disclose activation');
for(const file of ['web/index.html','web/about/index.html','web/features/index.html','web/download/index.html','web/faq/index.html','web/privacy/index.html'])
  assert.ok(read(file).includes('site-analytics.js'),'Missing analytics on '+file);
new Function(web);new Function(app);
console.log('PASS: activated aggregate pageviews, official APK taps, privacy opt-outs, admin RLS and zero personal data');
