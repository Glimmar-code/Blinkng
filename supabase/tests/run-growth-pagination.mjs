import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

const db = new PGlite();
const a = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const b = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const c = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
const d1 = '11111111-1111-4111-8111-111111111111';
const d2 = '22222222-2222-4222-8222-222222222222';
const post = '33333333-3333-4333-8333-333333333333';
const listing = '44444444-4444-4444-8444-444444444444';
const sold = '55555555-5555-4555-8555-555555555555';
const inactive = '66666666-6666-4666-8666-666666666666';

const read = (result) => {
  const raw = result.rows[0]?.result;
  return typeof raw === 'string' ? JSON.parse(raw) : raw;
};
const as = async (user) => {
  await db.query("select set_config('request.jwt.claim.sub',$1,false)",[user]);
};

try {
  await db.exec(`
    create role anon;
    create role authenticated;
    create schema auth;
    create schema private;
    create function auth.uid() returns uuid language sql stable
      as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    create table public.profiles(
      id uuid primary key,username text,full_name text,avatar_url text
    );
    create table public.feed_posts(
      id uuid primary key,user_id uuid,text text,caption text,
      image_url text,video_url text,images text[],is_reel boolean default false,
      is_active boolean default true,created_at timestamptz default now(),
      view_count integer default 0, like_count integer default 0, comment_count integer default 0
    );
    create table public.market_items(
      id uuid primary key,seller_id uuid,title text,image_url text,
      image_urls text[],status text,is_sold boolean default false,
      created_at timestamptz default now()
    );
    create table public.blink_drops(
      id uuid primary key,creator_id uuid,target_id uuid,target_type text,action text,
      reward_per_user integer,winner_count integer,claimed_count integer default 0,
      total_budget bigint,coin_distributed bigint default 0,coin_refunded bigint default 0,
      audience_scope text,target_university text,status text,
      ends_at timestamptz default (now()+interval '1 day'),
      created_at timestamptz default now()
    );
    create table public.blink_drop_eligible_followers(
      drop_id uuid,user_id uuid,primary key(drop_id,user_id)
    );
    create table public.blink_drop_claims(drop_id uuid,user_id uuid,primary key(drop_id,user_id));
    create function private.blink_drop_target_title(text,uuid) returns text
      language sql stable as $$ select 'Sample content'::text $$;
  `);
  await db.exec(`
    insert into public.profiles(id,username,full_name,avatar_url)
      values ('${a}','owner','Owner',''),('${b}','eligible','Eligible',''),('${c}','other','Other','');
    insert into public.feed_posts(id,user_id,text,is_reel,image_url,video_url)
      values ('${post}','${a}','First Reel',true,'','https://blink.com.ng/reel.mp4');
    insert into public.market_items(id,seller_id,title,status,is_sold)
      values ('${listing}','${a}','Available listing','active',false),
             ('${sold}','${a}','Sold listing','active',true),
             ('${inactive}','${a}','Inactive listing','paused',false);
    insert into public.blink_drops(id,creator_id,target_id,target_type,action,reward_per_user,winner_count,total_budget,audience_scope,status)
      values ('${d1}','${a}','${post}','REEL','LIKE',100,1,100,'ALL_CAMPUSES','ACTIVE'),
             ('${d2}','${a}','${post}','REEL','LIKE',100,1,100,'ALL_CAMPUSES','EXPIRED');
    insert into public.blink_drop_eligible_followers(drop_id,user_id) values ('${d1}','${b}');
  `);
  const migration = fs.readFileSync(
    new URL('../migrations/20261027225500_blink_growth_paginated_views.sql',import.meta.url),'utf8');
  await db.exec(migration);
  await db.exec(migration); // idempotent migration

  await as(a);
  const first = read(await db.query("select public.get_blink_drop_page('MINE',0,1) result"));
  assert.equal(first.items.length,1);
  assert.equal(first.has_more,true);
  const second = read(await db.query("select public.get_blink_drop_page('MINE',1,1) result"));
  assert.equal(second.items.length,1);
  assert.notEqual(first.items[0].id,second.items[0].id);
  assert.equal(read(await db.query("select public.get_blink_drop_by_id($1::uuid) result",[d1])).id,d1);

  const availableListings = read(await db.query(
    "select public.get_my_blink_boostable_content_page('LISTING',0,40) result"));
  assert.equal(availableListings.items.length,1);
  assert.equal(availableListings.items[0].id,listing);
  const reelContent = read(await db.query(
    "select public.get_my_blink_boostable_content_page('REEL',0,40) result"));
  assert.equal(reelContent.items.length,1);
  assert.equal(reelContent.items[0].video_url,'https://blink.com.ng/reel.mp4');

  await as(b);
  const eligible = read(await db.query("select public.get_blink_drop_page('AVAILABLE',0,30) result"));
  assert.equal(eligible.items.length,1);
  assert.equal(eligible.items[0].id,d1);
  assert.equal(read(await db.query("select public.get_blink_drop_by_id($1::uuid) result",[d1])).id,d1);
  assert.deepEqual(read(await db.query(
    "select public.get_blink_drop_by_id($1::uuid) result",[d2])),{not_found:true});
  assert.equal(read(await db.query(
    "select public.get_my_blink_boostable_content_page('LISTING',0,40) result")).items.length,0);

  await as(c);
  assert.deepEqual(read(await db.query(
    "select public.get_blink_drop_by_id($1::uuid) result",[d1])),{not_found:true});
  assert.equal(read(await db.query("select public.get_blink_drop_page('AVAILABLE',0,30) result")).items.length,0);
  const grants = await db.query(`select has_function_privilege('anon','public.get_blink_drop_page(text,integer,integer)','EXECUTE') as anon_allowed`);
  assert.equal(grants.rows[0].anon_allowed,false);
  console.log('PASS growth: owner pagination, exact eligible lookup, anon denial, sold/inactive exclusion and Reel media');
} finally {
  await db.close();
}
