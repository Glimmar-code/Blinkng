#!/usr/bin/env node
// BLINK public web browser E2E. Runs against a local web/ server with ALL
// user/auth APIs mocked; never reads real accounts or sends analytics.
import assert from 'node:assert/strict';
import {mkdirSync} from 'node:fs';
import {chromium} from 'playwright';

const base=process.env.BLINK_TEST_WEB_ORIGIN||'http://127.0.0.1:8765';
const widths=[360,390,430,768,1280];
const failures=[];
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});

try {
  for(const width of widths){
    const mobile=width<600;
    const context=await browser.newContext({
      viewport:{width,height:800},
      deviceScaleFactor:mobile?2:1,
      isMobile:mobile,hasTouch:mobile,
      reducedMotion:'reduce',
      locale:'en-NG',
    });
    const page=await context.newPage();
    const jsErrors=[];
    page.on('pageerror',error=>jsErrors.push(error.message));
    await page.route(/https:\/\/.*\.supabase\.co\//,async route=>{
      await route.fulfill({
        status:200,
        contentType:'application/json',
        headers:{'access-control-allow-origin':'*'},
        body:JSON.stringify({available:false,profile:null})
      });
    });
    await page.route('https://api.github.com/repos/Glimmar-code/Blinkng/releases/latest',async route=>{
      await route.fulfill({
        status:200,
        contentType:'application/json',
        headers:{'access-control-allow-origin':'*'},
        body:JSON.stringify({
          draft:false,prerelease:false,tag_name:'v1.0-smoke',
          name:'BLINK test release',
          assets:[{name:'Blink-latest.apk',size:81400000}]
        })
      });
    });

    try{
      await page.goto(base+'/',{waitUntil:'domcontentloaded',timeout:25000});
      await page.locator('main.br-page').waitFor({timeout:12000});
      assert.ok(await page.locator('#br-title').isVisible(),'Homepage heading missing');
      assert.equal(await page.locator('#app').evaluate(node=>node.classList.contains('boot')),false,
        'Loading screen grid must not center the branded homepage');
      const design=await page.evaluate(()=>{
        const heading=document.querySelector('#br-title');
        const download=document.querySelector('.br-button-main');
        const phones=document.querySelector('.br-showcase');
        const pill=document.querySelector('#parity-network-pill');
        return {
          headingAlignment:getComputedStyle(heading).textAlign,
          headingSize:parseFloat(getComputedStyle(heading).fontSize),
          downloadWidth:download.getBoundingClientRect().width,
          showcaseTop:phones.getBoundingClientRect().top,
          showcaseHeight:phones.getBoundingClientRect().height,
          floatingBadgeVisible:!!pill && getComputedStyle(pill).display!=='none'
        };
      });
      if(width<=480){
        assert.equal(design.headingAlignment,'left','Mobile hero should have one consistent text alignment');
        assert.ok(design.headingSize<=44,'Mobile headline should not overwhelm the screen');
        assert.ok(design.downloadWidth>=width-60,'APK download must have a comfortable full-width tap target');
        assert.ok(design.showcaseTop<700,'App previews must appear soon after download buttons');
        assert.ok(design.showcaseHeight<=390,'App previews must not push page content excessively far down');
        assert.equal(design.floatingBadgeVisible,false,'Network badge must not overlap app previews');
      }
      assert.equal(await page.locator('.landing-feature-card').count(),6,'Expected six BLINK feature cards');
      assert.ok(await page.locator('a[href*="/releases/latest/download/Blink-latest.apk"]').first().isVisible(),'Android download CTA missing');
      const layout=await page.evaluate(()=>({
        viewport:window.innerWidth,
        document:document.documentElement.scrollWidth,
        body:document.body.scrollWidth,
      }));
      assert.ok(layout.document<=layout.viewport+2&&layout.body<=layout.viewport+2,
        `Horizontal overflow on ${width}px: ${JSON.stringify(layout)}`);

      assert.ok(await page.locator('.br-brand img[src$="blink-logo-v2.png"]').isVisible(),'Approved BLINK logo missing');
      assert.ok(await page.locator('.br-header-download').isVisible(),'Header APK download missing');
      assert.equal(await page.locator('a[href*="play.google.com"]').count(),0,'Play Store CTA must not appear');
      if(width>800){
        assert.ok(await page.locator('.br-nav').isVisible(),'Desktop marketing navigation missing');
      }
      await page.getByRole('button',{name:/Read more about BLINK/}).click();
      assert.ok(await page.locator('#blink-read-more').evaluate(element=>element.open),
        'Read more must reveal preserved website information and tools');
      assert.equal(await page.locator('.landing-feature-card').count(),6,
        'Existing features must remain accessible behind Read more');

      await page.getByRole('tab',{name:'Market'}).click();
      assert.ok(await page.getByText('Discover the BLINK marketplace',{exact:true}).isVisible(),
        'Interactive tour did not switch to Market');
      await page.locator('#campus-search').fill('FUTA');
      const futa=page.locator('button.campus-option').filter({hasText:'Federal University of Technology, Akure'});
      await futa.waitFor({timeout:10000});
      await futa.click();
      assert.ok(await page.locator('#campus-selection').isVisible(),'Chosen campus not displayed');

      await page.locator('#landing-username').fill('!notvalid');
      await page.locator('#public-search button[type=submit]').click();
      assert.ok((await page.locator('#public-search-status').textContent()).includes('valid BLINK username'),
        'Invalid username validation missing');

      await page.locator('#landing-username').fill('missing-smoke-profile');
      await page.locator('#public-search button[type=submit]').click();
      await page.getByText('No public BLINK profile found for that username.').waitFor({timeout:10000});

      const loginLink=page.locator('.br-info-links a[href="./login"]');
      await loginLink.click();
      await page.locator('#login-form').waitFor({timeout:12000});
      assert.ok(await page.locator('#google-auth').isVisible(),'Google login button missing');
      assert.ok(await page.locator('#forgot').isVisible(),'Password recovery action missing');

      await page.goto(base+'/download/',{waitUntil:'domcontentloaded',timeout:20000});
      await page.getByText(/Latest published APK: BLINK test release/).waitFor({timeout:12000});
      assert.ok(await page.getByRole('link',{name:'Download latest Android APK'}).getAttribute('href')
        .then(x=>x.includes('/releases/latest/download/Blink-latest.apk')),
        'The official APK latest download alias was changed');

      assert.deepEqual(jsErrors,[],`Browser JS errors at ${width}px`);
      console.log(`PASS: BLINK browser ${width}px — homepage, menu, layout, campus, tour, profile search, auth and APK`);
    }catch(error){
      mkdirSync('tmp/blink-browser-smoke',{recursive:true});
      const path=`tmp/blink-browser-smoke/failed-${width}px.png`;
      try{await page.screenshot({path,fullPage:true,timeout:7000});}catch{}
      failures.push(`${width}px: ${error.stack||error.message}`+'; screenshot: '+path);
    }finally{
      await context.close();
    }
  }
}finally{
  await browser.close();
}
if(failures.length){
  console.error('FAIL: BLINK browser/mobile smoke tests:\n'+failures.join('\n\n'));
  process.exitCode=1;
}else{
  console.log('PASS: all 5 browser viewports, no live user API calls and no private tracking');
}
