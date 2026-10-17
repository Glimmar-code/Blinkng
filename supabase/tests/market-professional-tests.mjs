import assert from 'node:assert/strict';

export async function marketProfessionalTests(db) {
  const owner='00000000-0000-4000-8000-000000052001';
  const buyer='00000000-0000-4000-8000-000000052002';
  const exec = text => db.exec(text);
  const q = text => db.query(text);
  const as = async id => exec(`reset role;select set_config('request.jwt.claim.sub','${id}',false);set role authenticated;`);
  try {
    await exec(`reset role;
      insert into auth.users(id,email) values
        ('${owner}','pro-owner@invalid.example'),('${buyer}','pro-buyer@invalid.example');
      insert into public.profiles(id,username,full_name,university,is_verified,verification_badge,
        verification_expires_at,is_seller_active,seller_status,seller_store_name)
      values
        ('${owner}','pro_owner','Professional Owner','FUTA',true,'BLUE',
          now()+interval '20 days',true,'active','FUTA Campus Shop'),
        ('${buyer}','pro_buyer','Professional Buyer','FUTA',false,'NONE',
          null,false,'inactive','');
    `);
    await as(owner);
    const details=await q(`select public.complete_market_seller_profile(
        'business','Campus shop offering quality student essentials',
        array['Books & Study Materials'],true,true,null) as completed`);
    assert.equal(details.rows[0].completed,true);
    const profile=await q(`select seller_kind from public.market_seller_profiles where user_id='${owner}'`);
    assert.equal(profile.rows[0].seller_kind,'business');
    const sellerCard=await q(`select public.get_market_public_seller_card('${owner}') as card`);
    assert.equal(sellerCard.rows[0].card.verified_seller,false,'approval is independent from blue badge');

    await as(buyer);
    await assert.rejects(
      q(`select public.complete_market_seller_profile(
       'individual','Selling my reliable student equipment',
       array['Books & Study Materials'],true,true,null)`),
      /SELLER_REGISTRATION_REQUIRED/);
    await as(owner);
    const listing=await q(`insert into public.market_items(
      seller_id,title,description,price,category,image_url,university,location)
      values('${owner}','Professional Textbooks','Genuine university textbooks in good condition',
       4500,'Books & Study Materials','https://example.invalid/book.png','FUTA','Akure')
      returning id`);
    const id=listing.rows[0].id;

    await as(buyer);
    const offer=await q(`select public.make_market_offer('${id}',4000,'Can meet on campus') as id`);
    assert.ok(offer.rows[0].id);
    await assert.rejects(
      q(`select public.make_market_offer('${id}',3000,'Repeated offer')`),
      /OFFER_ALREADY_OPEN/);
    await assert.rejects(
      q(`select public.respond_market_offer('${offer.rows[0].id}','accepted')`),
      /BUYER_MAY_ONLY_CANCEL/);
    await as(owner);
    const response=await q(`select public.respond_market_offer('${offer.rows[0].id}',
        'countered',4200,'Could do this price') as ok`);
    assert.equal(response.rows[0].ok,true);

    await exec(`reset role;insert into public.marketplace_orders(
      item_id,buyer_id,seller_id,quantity,unit_price,total_price,status)
      values('${id}','${buyer}','${owner}',1,4500,4500,'completed');`);
    const order=await q(`select id from public.marketplace_orders where item_id='${id}' limit 1`);
    await as(buyer);
    const dispute=await q(`select public.open_market_dispute('${order.rows[0].id}',
       'Item condition differs','A private complaint with no payment claims') as id`);
    assert.ok(dispute.rows[0].id);

    const ranked=await q(`select id from public.discover_market_items_v2(
       'Textbooks','FUTA',null,20,0,null,null)`);
    assert.ok(ranked.rows.some(row=>row.id===id));

    await exec('reset role;');
    const maintenance=await q('select private.market_daily_maintenance() as summary');
    assert.ok(maintenance.rows[0].summary.paused_stale_listings>=0);
    console.log('PASS professional Market seller badge, offers, dispute, ranking and daily maintenance');
  } finally {
    await exec('reset role;');
  }
}
