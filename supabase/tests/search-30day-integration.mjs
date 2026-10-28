// Isolated PostgreSQL integration coverage for staged Search endpoints.
// Never connects to a hosted project or uses real user records.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

const migration = readFileSync(
  new URL('../../docs/integration/blink-search-30day-trends-staging-20261027.sql', import.meta.url),
  'utf8',
);
const db = new PGlite();
const viewer = '11111111-1111-4111-8111-111111111111';
const active = '22222222-2222-4222-8222-222222222222';
const older = '33333333-3333-4333-8333-333333333333';
const blocked = '44444444-4444-4444-8444-444444444444';
const outsider = '55555555-5555-4555-8555-555555555555';
const postId = n => 'aaaaaaaa-aaaa-4aaa-8aaa-' + String(n).padStart(12, '0');
const ago = days => new Date(Date.now() - days * 86_400_000).toISOString();

async function addPost(n, author, age, tags, extras = {}) {
  await db.query(
    "insert into public.feed_posts (id,user_id,created_at,tags,hashtags,audience,is_active,is_flagged,expires_at,like_count,comment_count,share_count,view_count) values ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)",
    [postId(n), author, ago(age), tags, extras.hashtags || [],
      extras.audience || 'everyone', extras.active ?? true, extras.flagged ?? false,
      extras.expired ? ago(1) : null, extras.likes ?? 5, 0, 0, extras.views ?? 10],
  );
}

try {
  await db.exec([
    'create role authenticated;',
    'create role anon;',
    'create schema auth;',
    "create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;",
    'create table public.profiles (id uuid primary key, username text, full_name text, avatar_url text, university text, is_verified boolean, verification_expires_at timestamptz, onboarding_completed boolean);',
    'create table public.feed_posts (id uuid primary key, user_id uuid, created_at timestamptz, tags text[], hashtags text[], audience text, is_active boolean, is_flagged boolean, expires_at timestamptz, like_count integer, comment_count integer, share_count integer, view_count integer);',
    'create table public.blocks (blocker_id uuid, blocked_id uuid);',
    'create table public.blink_tag_search_events (user_id uuid, tag text, searched_hour timestamptz);',
  ].join('\n'));
  await db.query(
    'insert into public.profiles(id,username,full_name,university,is_verified,verification_expires_at,onboarding_completed) values ($1,$2,$3,$4,$5,$6,$7),($8,$9,$10,$11,$12,$13,$14),($15,$16,$17,$18,$19,$20,$21),($22,$23,$24,$25,$26,$27,$28),($29,$30,$31,$32,$33,$34,$35)',
    [viewer,'viewer','Viewing user','FUTA',false,null,true,
      active,'active','Active writer','FUTA',true,ago(1),true,
      older,'older','Older writer','FUTA',true,'2099-01-01T00:00:00Z',true,
      blocked,'blocked','Blocked writer','FUTA',false,null,true,
      outsider,'outside','Different campus','UNILAG',false,null,true],
  );
  await db.query('insert into public.blocks(blocker_id,blocked_id) values ($1,$2)',[viewer,blocked]);

  await addPost(1,active,2,['#current','#current'],{hashtags:['#current']});
  await addPost(2,older,15,['#older30']);
  await addPost(3,older,40,['#too_old']);
  await addPost(4,blocked,2,['#blocked_tag']);
  await addPost(5,active,2,['#private_tag'],{audience:'following'});
  await addPost(6,active,2,['#expired_tag'],{expired:true});
  await addPost(7,active,2,['#inactive_tag'],{active:false});
  await addPost(8,active,2,['#flagged_tag'],{flagged:true});
  await addPost(9,outsider,2,['#other_campus']);
  await addPost(10,viewer,2,['#viewer_post']);
  await db.query('insert into public.blink_tag_search_events(user_id,tag,searched_hour) values ($1,$2,$3)',[viewer,'older30',ago(2)]);

  // Use the SQL in source control unchanged; no reimplementation of ranking logic.
  await db.exec(migration);
  const permissions = (await db.query(
    "select has_function_privilege('anon','public.blink_trending_tags_30d(integer,text)','execute') as anonymous_tags, " +
    "has_function_privilege('authenticated','public.blink_trending_tags_30d(integer,text)','execute') as user_tags, " +
    "has_function_privilege('anon','public.blink_trending_creators_30d(integer,text)','execute') as anonymous_people, " +
    "has_function_privilege('authenticated','public.blink_trending_creators_30d(integer,text)','execute') as user_people",
  )).rows[0];
  assert.deepEqual(Object.values(permissions),[false,true,false,true],
    'RPCs must not allow anonymous access');

  await db.query("select set_config('request.jwt.claim.sub',$1,false)",[viewer]);
  const tags = (await db.query('select tag,post_count from public.blink_trending_tags_30d(50,null)')).rows;
  const tagNames = tags.map(row => row.tag);
  assert.ok(tagNames.includes('older30'),'posts from 15 days ago belong in the 30-day window');
  assert.ok(tagNames.includes('current'),'recent public tags are included');
  for (const hidden of ['too_old','blocked_tag','private_tag','expired_tag','inactive_tag','flagged_tag']) {
    assert.ok(!tagNames.includes(hidden),'tag ' + hidden + ' must not leak');
  }
  assert.equal(Number(tags.find(row => row.tag === 'current').post_count),1,
    'duplicate hashtags in the same post must count once');

  const people = (await db.query('select id,is_verified from public.blink_trending_creators_30d(3,null)')).rows;
  assert.ok(people.some(row=>row.id===older),'older eligible creators must be eligible');
  assert.ok(!people.some(row=>row.id===blocked),'blocked creators must be hidden');
  assert.ok(!people.some(row=>row.id===viewer),'the viewer must not trend to themselves');
  assert.equal(people.find(row=>row.id===active)?.is_verified,false,
    'expired verification must not grant a visible badge');
  assert.equal(people.find(row=>row.id===older)?.is_verified,true,
    'active verification must be preserved');

  const campus = (await db.query("select id from public.blink_trending_creators_30d(3,'FUTA')")).rows;
  assert.ok(!campus.some(row=>row.id===outsider),'campus selection excludes another university');
  await db.query("select set_config('request.jwt.claim.sub','',false)");
  assert.equal((await db.query('select * from public.blink_trending_tags_30d(50,null)')).rows.length,0,
    'RPC must fail closed without a user identity');
  assert.equal((await db.query('select * from public.blink_trending_creators_30d(3,null)')).rows.length,0,
    'creator RPC must fail closed without a user identity');
  console.log('PASS Search 30-day PGlite integration: privacy, ranking window, verification, permissions');
} finally {
  await db.close();
}
