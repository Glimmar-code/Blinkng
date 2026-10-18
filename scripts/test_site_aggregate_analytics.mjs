#!/usr/bin/env node
// Website statistics are opt-in, aggregate-only and admin gated.
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
const count=[];
const fakeDocument={addEventListener:(...args)=>count.push(args)};
runInNewContext(web,{
  window:{},
  location:{hostname:'blink.com.ng',pathname:'/',search:''},
  navigator:{doNotTrack:'0',globalPrivacyControl:false},
  URLSearchParams,
  document:fakeDocument,
  fetch:()=>{throw new Error('Collection must not run until explicit activation');}
});
assert.equal(count.length,0,'No analytics event listeners or requests when collection is disabled');
assert.ok(web.includes('const ENABLED = false'),'Client-side collection must remain disabled by default');
assert.ok(edge.includes('Deno.env.get("BLINK_SITE_ANALYTICS_ENABLED")!=="true"'),'Edge collection must be disabled by default');
assert.ok(web.includes("navigator.globalPrivacyControl === true")&&web.includes("navigator.doNotTrack === '1'"),'Respect GPC/DNT');
assert.ok(web.includes("credentials:'omit'")&&web.includes("referrerPolicy:'no-referrer'"),'Do not transmit session cookies/referrer');
assert.ok(!/(?:localStorage|sessionStorage|document\.cookie|navigator\.userAgent)/.test(web),'Analytics must not persist identifiers');
assert.ok(!/\bconsole\.log\(|\bconsole\.error\(/.test(edge),'Public Edge handler must not log request data');
assert.ok(edge.includes('request.headers.get("origin")!==OFFICIAL_ORIGIN'),'Only canonical BLINK browser origin should be permitted');
assert.ok(edge.includes('raw.length>160'),'Limit analytics request size');
assert.ok(edge.includes('Object.keys(parsed).some(k=>k!=="page"&&k!=="kind")'),'Reject added identifying request fields');
assert.ok(edge.includes('record_blink_site_metric'),'Edge metrics must use capped, service-only Postgres writer');
assert.ok(config.includes('[functions.blink-site-analytics]')&&config.includes('verify_jwt = false'),'Anonymous endpoint configured explicitly');
assert.ok(migration.includes('create table if not exists public.blink_site_metric_daily'),'Need aggregate daily table');
assert.ok(!/(?:ip_address|user_id|device_id|session_id|fingerprint|email|user_agent)\s+(?:text|uuid|varchar|inet|jsonb)/i.test(migration),'No visitor identification columns permitted');
assert.ok(migration.includes('limiter.total < 120'),'Server-side rate limit is mandatory');
assert.ok(migration.includes('revoke all on function public.record_blink_site_metric(text,text)'),'Metrics writer must be private');
assert.ok(migration.includes('to service_role;'),'Only trusted server may write metrics');
assert.ok(migration.includes('private.require_blink_admin()'),'Admin reader must enforce authoritative DB permissions');
assert.ok(migration.includes('grant execute on function public.get_blink_site_metrics(integer)'),'Admin RPC grants must be explicit');
assert.ok(app.includes("if(path==='/site-stats')return renderSiteAnalytics()"),'Admin analytics route must exist');
assert.ok(app.includes("rpc('get_blink_site_metrics',{p_days:30})"),'Admin page must read aggregate RPC');
assert.ok(app.includes("app_metadata?.admin===true"),'Non-admins should not see stats link');
assert.ok(privacy.includes('currently disabled system'),'Privacy policy must disclose launch-disabled collection');
for(const file of ['web/index.html','web/about/index.html','web/features/index.html','web/download/index.html','web/faq/index.html','web/privacy/index.html']){
 assert.ok(read(file).includes('site-analytics.js'),'Missing optional aggregate analytics library on '+file);
}
new Function(web);
new Function(app);
console.log('PASS: no collection by default, anonymous daily totals, rate limits, admin-only stats and privacy disclosure');
