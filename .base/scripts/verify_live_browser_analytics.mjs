#!/usr/bin/env node
// Real Chrome network smoke, public homepage only; no sign-in or user data.
// Records one test homepage view and APK-link tap without requesting an APK.
import assert from 'node:assert/strict';
import {chromium} from 'playwright';

const endpoint='https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/blink-site-analytics';
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
try{
 const context=await browser.newContext({locale:'en-NG',viewport:{width:390,height:780},isMobile:true,hasTouch:true});
 const page=await context.newPage();
 const errors=[];
 page.on('pageerror',error=>errors.push(String(error.message).slice(0,100)));
 const metric=page.waitForResponse(response=>
   response.url()===endpoint && response.request().method()==='POST',
   {timeout:20000}
 );
 await page.goto('https://blink.com.ng/',{waitUntil:'domcontentloaded',timeout:25000});
 const response=await metric;
 assert.equal(response.status(),204,'BLINK homepage did not send an accepted live metric');
 const headers=await response.request().allHeaders();
 assert.equal(headers.origin,'https://blink.com.ng','Metrics must originate on official site');
 const body=response.request().postDataJSON();
 assert.deepEqual(body,{page:'home',kind:'view'},'No visitor details may be sent');
 const next=await page.evaluate(()=>{
   const script=[...document.scripts].find(x=>x.src.endsWith('/site-analytics.js'));
   return {analyticsScriptLoaded:!!script,canonical:document.querySelector('link[rel=canonical]')?.href};
 });
 assert.ok(next.analyticsScriptLoaded,'Anonymous metric script missing from public page');
 assert.equal(next.canonical,'https://blink.com.ng/','Public site canonical mismatch');
 // The click is synthetic. Cancel navigation before the event reaches
 // BLINK's analytics listener; no APK is fetched or installed by this test.
 await page.evaluate(()=>{
   document.addEventListener('click',event=>{
     if(event.target instanceof Element &&
        event.target.closest('a[href*="/releases/latest/download/Blink-latest.apk"]')){
       event.preventDefault();
     }
   },{capture:true});
 });
 const apkMetric=page.waitForResponse(response=>
   response.url()===endpoint &&
   response.request().method()==='POST' &&
   response.request().postDataJSON()?.kind==='download',
   {timeout:20000}
 );
 const apkLink=page.locator('a[href*="/releases/latest/download/Blink-latest.apk"]').first();
 assert.ok(await apkLink.isVisible(),'Official APK download link missing from mobile homepage');
 await apkLink.dispatchEvent('click');
 const apkResponse=await apkMetric;
 assert.equal(apkResponse.status(),204,'Official APK tap not counted');
 assert.deepEqual(apkResponse.request().postDataJSON(),{page:'home',kind:'download'},
   'APK event cannot carry user identifiers');
 assert.deepEqual(errors,[],'Public page script crashed');
 console.log('PASS real mobile Chrome: homepage view and APK-link tap recorded (both 204)');
 console.log('INFO This is a synthetic verification visit, not a unique human visitor.');
 await context.close();
}finally{await browser.close();}
