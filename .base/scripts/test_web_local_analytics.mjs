#!/usr/bin/env node
// Site-owner QA-only, privacy-safe performance and funnel analytics tests.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const source=readFileSync('web/performance-diagnostics.js','utf8');
assert.ok(!/\bfetch\s*\(|\bsendBeacon\s*\(|\bXMLHttpRequest\b/.test(source),'Telemetry must never leave the browser');
assert.ok(!/\blocalStorage\b|\bsessionStorage\b|\bdocument\.cookie\b/.test(source),'Diagnostics cannot persist user data');
assert.ok(!/\b(?:input|form|event\.target|node)\.value\b|\b(?:input|form|event\.target|node)\.textContent\b/.test(source),'No input value or personal text may be captured');
const simulate=search=>{
 const listeners=new Map();
 const logs=[];
 const Element=class{
   constructor({matches=[],id=''}){this.matches=new Set(matches);this.id=id;}
   closest(selector){return this.matches.has(selector)?this:null;}
 };
 const document={
   visibilityState:'visible',
   addEventListener:(type,fn)=>listeners.set(type,fn),
   removeEventListener:type=>listeners.delete(type)
 };
 const window={addEventListener:(type,fn)=>listeners.set(type,fn)};
 const performance={getEntriesByType:()=>[]};
 runInNewContext(source,{
   location:{search},URLSearchParams,document,Element,window,performance,
   PerformanceObserver:{supportedEntryTypes:[]},
   console:{info:(...args)=>logs.push(args)},
   setTimeout:()=>1,clearTimeout:()=>{}
 });
 return {document,listeners,logs,Element};
};
const off=simulate('');
assert.equal(off.listeners.size,0,'No analytics or diagnostics listeners should be registered for ordinary visitors');
const on=simulate('?blink_perf=1');
assert.ok(on.listeners.has('click')&&on.listeners.has('submit'),'QA-only counters require opt-in');
const tap=selector=>on.listeners.get('click')({target:new on.Element({matches:[selector]})});
tap('a[href*="releases/latest/download/Blink-latest.apk"]');
tap('button[data-tour-tab]');
tap('button.campus-option');
tap('button[data-tour-tab]');
on.listeners.get('submit')({target:new on.Element({id:'public-search'})});
on.document.visibilityState='hidden';
on.listeners.get('visibilitychange')();
assert.equal(on.logs.length,1,'Diagnostics should only report when asked to do so');
const snapshot=on.logs[0][1];
assert.equal(snapshot.interactions.apkDownloads,1);
assert.equal(snapshot.interactions.featureTabs,2);
assert.equal(snapshot.interactions.campusSelections,1);
assert.equal(snapshot.interactions.profileSearches,1);
for(const key of ['username','email','profile','query','url','ip','phone','university']){
 assert.ok(!(key in snapshot.interactions),'No identifying field may be collected: '+key);
}
assert.equal(Object.keys(snapshot.interactions).length,4,'Do not silently expand analytics fields');
console.log('PASS: QA-only anonymous click funnel counts, disabled by default, no network, cookies or persistence');
