import { PGlite } from '@electric-sql/pglite';
import fs from 'node:fs';
import assert from 'node:assert/strict';

async function rejects(action, code) {
  let error = null;
  try { await action(); } catch (cause) { error = cause; }
  assert.ok(error, 'Expected security rule to reject the operation');
  assert.match(error.message, new RegExp(code));
}

export async function stagedMarketCommunityContractTests() {
  const db = new PGlite();
  try {
    await db.exec(`
      create role anon;
      create role authenticated;
      create role service_role bypassrls;
      create schema auth;
      create table auth.users(id uuid primary key, email text);
      create function auth.uid() returns uuid language sql stable as
        $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
      grant usage on schema auth, public to authenticated, service_role;
      grant execute on function auth.uid() to authenticated, service_role;
      create table public.feed_posts (
        id uuid primary key default gen_random_uuid(),
        category text not null default 'Campus Life',
        is_sponsored boolean not null default false,
        ad_label text,
        text text
      );
      grant select, insert, update, delete on public.feed_posts to authenticated, service_role;
      create table public.conversations (
        id uuid primary key, title text, description text, avatar_url text,
        is_group boolean not null default false, updated_at timestamptz not null default now()
      );
      create table public.conversation_participants (
        conversation_id uuid references public.conversations(id), user_id uuid,
        left_at timestamptz
      );
    `);

    const market = fs.readFileSync(
      new URL('../../docs/integration/marketplace-feed-server-policy-staging-20261025.sql', import.meta.url),
      'utf8'
    );
    await db.exec(market);

    await db.exec(`set role authenticated`);
    await rejects(() => db.exec(`
      insert into public.feed_posts(category,text) values ('Marketplace','For sale')`),
      'BLINK_MARKET_LISTING_ONLY');
    await rejects(() => db.exec(`
      insert into public.feed_posts(category,is_sponsored,ad_label)
      values ('Campus Life',true,'Sponsored')`), 'BLINK_AD_REQUIRES_SERVER_APPROVAL');
    await db.exec(`
      insert into public.feed_posts(category,text) values ('Campus Life','Discussion of a product')`);
    await rejects(() => db.exec(`
      update public.feed_posts set category='Market' where category='Campus Life'`),
      'BLINK_MARKET_LISTING_ONLY');
    await rejects(() => db.exec(`
      update public.feed_posts set is_sponsored=true where category='Campus Life'`),
      'BLINK_AD_REQUIRES_SERVER_APPROVAL');
    await db.exec('reset role');
    await db.exec('set role service_role');
    await db.exec(`
      insert into public.feed_posts(category,is_sponsored,ad_label)
      values ('Marketplace',true,'Sponsored')`);
    await db.exec('reset role');
    console.log('PASS staged feed category and server-only paid-promotion enforcement');

    const owner = '11111111-1111-4111-8111-111111111111';
    const other = '22222222-2222-4222-8222-222222222222';
    await db.exec(`
      insert into auth.users(id,email) values
      ('${owner}','owner@example.test'),('${other}','reader@example.test')
    `);
    const groupCommunity = fs.readFileSync(
      new URL('../../docs/integration/groups-communities-staging-20261025.sql', import.meta.url),
      'utf8'
    );
    await db.exec(groupCommunity);
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    await db.exec(`
      insert into public.blink_communities
        (id,owner_id,name,university,category,visibility)
      values
        ('33333333-3333-4333-8333-333333333333','${owner}','Study Circle','FUTA','Academic','private'),
        ('44444444-4444-4444-8444-444444444444','${owner}','Open Campus','FUTA','Campus','public')`);
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    const count = await db.query('select count(*)::integer as n from public.blink_communities');
    assert.equal(count.rows[0].n, 1, 'Other users must not read private owner-only groups');
    await rejects(() => db.exec(`
      insert into public.blink_communities(owner_id,name,university,category)
      values ('${owner}','Fake Community','FUTA','Campus')`),
      'row-level security');
    await db.exec('reset role');
    console.log('PASS staged private-community RLS and non-owner creation denial');

    // A group is listed exactly once for an active member even when 2+
    // participants exist. Leaving removes it from that person's list.
    const group = '55555555-5555-4555-8555-555555555555';
    await db.exec(`
      insert into public.conversations(id,title,is_group)
        values ('${group}','FUTA Readers',true);
      insert into public.conversation_participants(conversation_id,user_id)
        values ('${group}','${owner}'),('${group}','${other}');
    `);
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${owner}',false)`);
    const mine = await db.query('select group_id,title,member_count from public.blink_my_group_conversations(50)');
    assert.equal(mine.rows.length, 1);
    assert.equal(mine.rows[0].title, 'FUTA Readers');
    assert.equal(Number(mine.rows[0].member_count), 2);
    await db.exec('reset role');
    await db.exec(`update public.conversation_participants
      set left_at=now() where conversation_id='${group}' and user_id='${other}'`);
    await db.exec('set role authenticated');
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    const departed = await db.query('select group_id from public.blink_my_group_conversations(50)');
    assert.equal(departed.rows.length, 0, 'Departed members cannot list groups');
    await db.exec('reset role');
    console.log('PASS staged one-row group inbox and departed-member suppression');
    console.log('PASS independent offline staged Marketplace + Community contracts');
  } finally {
    await db.close();
  }
}
