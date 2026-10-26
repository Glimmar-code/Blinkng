#!/usr/bin/env node
// BLINK: product listings must use Market, not ordinary Feed posting.
// Browser-only validation is a UX check; the server trigger is the security gate.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const web=readFileSync('web/app.js','utf8');
const start=web.indexOf('  function marketOnlyFeedWarning(');
const end=web.indexOf('  function openPostComposer()',start);
assert.ok(start>=0 && end>start,'Web Feed Market-only validator must exist');
const marketOnlyFeedWarning=runInNewContext(
  web.slice(start,end)+'\nmarketOnlyFeedWarning',
  {}
);
assert.equal(typeof marketOnlyFeedWarning,'function');

for(const category of ['Marketplace','Market',' Buy & Sell ','FOR SALE','Selling']){
  assert.ok(marketOnlyFeedWarning(category,'Campus update'),`Illegal Feed category accepted: ${category}`);
}
for(const copy of [
  'Selling this device for NGN 150000',
  'For sale ₦30000; DM to buy',
  'Buy now! NGN 2500',
  'Selling my book for NGN 1500',
  'Available for sale ₦2000'
]){
  assert.ok(marketOnlyFeedWarning('Campus Life',copy),`Sales copy accepted: ${copy}`);
}
for(const copy of [
  'What is the best laptop for a statistics student?',
  'Discussion: tuition prices have changed.',
  'Campus event tomorrow at 2 PM.'
]){
  assert.equal(marketOnlyFeedWarning('Campus Life',copy),'',`Blocked normal student discussion: ${copy}`);
}

const composer=web.slice(end,web.indexOf('  async function createStory()',end));
assert.ok(!composer.includes('<option>Marketplace</option>'),'Web Feed must not offer a Marketplace category');
assert.ok(composer.includes('Create a Market listing'),'Web composer must route sales to Market');
assert.ok(composer.includes("navigate('/market')"),'Market CTA must navigate to Market');
assert.ok(composer.includes('updateMarketWarning()'),'Show a warning while composing');
const guard=composer.indexOf('const restricted=marketOnlyFeedWarning(');
const upload=composer.indexOf("uploadPublicFile('post-media'");
const postInsert=composer.indexOf("insert('feed_posts'");
assert.ok(guard>=0 && upload>guard && postInsert>upload,'Validate sales before uploading or publishing');
assert.ok(composer.includes('if(restricted)throw new Error(restricted)'),'Do not silently ignore sales restrictions');

const shared=readFileSync('shared/src/main/kotlin/com/blinkng/shared/BlinkPostGrowthTips.kt','utf8');
const android=readFileSync('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt','utf8');
const desktop=readFileSync('desktopApp/src/main/kotlin/com/blinkng/desktop/ui/ContentScreens.kt','utf8');
assert.ok(shared.includes('fun marketOnlyReason('),'Android and Windows must share the same business rule');
assert.ok(android.includes('fun schedulePost(post: FeedPost') && android.includes('marketOnlyReason('),
  'Scheduled and immediate Android posts must be checked');
assert.ok(desktop.includes('BlinkPostGrowthTips.marketOnlyReason('),
  'Windows must check Market-only posts');

const sql=readFileSync('docs/integration/marketplace-feed-server-policy-staging-20261025.sql','utf8');
for(const marker of ['before insert or update of category, text, caption, alt_text, is_sponsored, ad_label, ad_cta',
  'feed_route_product_ads_trigger','BLINK_AD_REQUIRES_SERVER_APPROVAL','BLINK_MARKET_LISTING_ONLY']){
  assert.ok(sql.includes(marker),`Missing staging database protection: ${marker}`);
}
console.log('PASS BLINK Market-only Feed posting contracts (web, Android, Windows, staging SQL)');
