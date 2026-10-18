#!/usr/bin/env node
// Real Chrome network smoke, public homepage only; no sign-in or user data.
// Records one anonymous test homepage view and inspects the request body.
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
 assert.deepEqual(errors,[],'Public page script crashed');
 console.log('PASS real mobile Chrome: official homepage sent one cookie-free aggregate page view (204)');
 console.log('INFO This is a synthetic verification visit, not a unique human visitor.');
 await context.close();
}finally{await browser.close();}
