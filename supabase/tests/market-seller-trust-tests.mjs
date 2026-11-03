import assert from 'node:assert/strict';

/** Exercises the actual Market verification / anti-spam SQL on a disposable DB. */
export async function marketSellerTrustTests(db) {
  const seller = '00000000-0000-4000-8000-000000042001';
  const other = '00000000-0000-4000-8000-000000042002';
  const exec = sql => db.exec(sql);
  const query = sql => db.query(sql);
  const as = async id => exec(`reset role; select set_config('request.jwt.claim.sub','${id}',false); set role authenticated;`);
  try {
    await exec(`reset role;
      insert into auth.users(id,email) values
        ('${seller}','market-test-seller@invalid.example'),
        ('${other}','market-test-other@invalid.example');
      insert into public.profiles(id,username,full_name,university,is_verified,verification_badge,verification_expires_at)
        values
        ('${seller}','market_test_seller','Market Test Seller','FUTA',true,'BLUE',now()+interval '20 days'),
        ('${other}','market_test_other','Market Test Other','FUTA',false,'NONE',null);
    `);
    await as(other);
    await assert.rejects(
      query(`select public.register_verified_market_seller('Unverified','08012345678','08012345678','Ondo','Akure','FUTA')`),
      /VERIFICATION_REQUIRED_OR_EXPIRED/);

    await as(seller);
    assert.equal((await query(`select public.register_verified_market_seller('Campus Hub','08012345678','08012345678','Ondo','Akure','FUTA') as ok`)).rows[0].ok, true);
    assert.equal((await query(`select is_seller_active,seller_status from public.profiles where id='${seller}'`)).rows[0].seller_status, 'active');

    const add = title => query(`insert into public.market_items(seller_id,title,description,price,category,image_url)
      values ('${seller}','${title}','A genuine product with useful details',2500,'Electronics','https://example.invalid/item.jpg') returning id`);
    await add('Market Unique Item 1');
    await assert.rejects(add('Market Unique Item 1'), /DUPLICATE_MARKET_LISTING/);
    await add('Market Unique Item 2');
    await add('Market Unique Item 3');
    await assert.rejects(add('Market Unique Item 4'), /DAILY_LISTING_LIMIT_REACHED/);

    await exec(`reset role; update public.profiles set verification_expires_at=now()-interval '1 hour' where id='${seller}';`);
    await as(seller);
    await assert.rejects(add('Different Expired Item'), /SELLER_NOT_VERIFIED_OR_EXPIRED|row-level security/);
    await exec(`reset role; update public.profiles set verification_expires_at=now()+interval '20 days' where id='${seller}';`);

    await as(seller);
    await assert.rejects(
      query(`insert into public.feed_posts(user_id,type,text,creator_post_number)
        values ('${seller}','text','Phone for sale ₦250000 DM to buy',1)`),
      /PRODUCT_ADS_MARKET_ONLY/);
    // Non-sales conversation should remain allowed by the routing predicate.
    const allowed = await query(`select
      'I bought a laptop for ₦250000 yesterday' ~* '(^|[^[:alpha:]])(for sale|selling this|available for purchase|dm to buy|buy now|order now|price is)([^[:alpha:]]|$)' as bad_ad`);
    assert.equal(allowed.rows[0].bad_ad,false);
    console.log('PASS Market seller verified enrollment, duplicate/rate checks, expiry and Feed boundary');
  } finally {
    await exec('reset role;');
  }
}
