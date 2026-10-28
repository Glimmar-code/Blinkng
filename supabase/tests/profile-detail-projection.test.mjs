import { PGlite } from '@electric-sql/pglite';
import fs from 'node:fs';
import assert from 'node:assert/strict';

const sql = fs.readFileSync(new URL('../migrations/20261027224500_harden_get_profile_detail_public_projection.sql', import.meta.url), 'utf8');
const referenced = [...sql.matchAll(/v_profile\.([a-z_]+)/g)].map(match => match[1]);
const columns = [...new Set(referenced)].filter(name => name !== 'id');
const bool = new Set(['is_online', 'online_now', 'is_verified', 'is_seller_active', 'onboarding_completed']);
const numeric = new Set(['follower_count', 'following_count', 'posts_count', 'daily_streak',
  'world_rank', 'campus_rank', 'points', 'total_xp', 'xp_level',
  'profile_views_this_week', 'current_wallet_balance', 'onboarding_step']);
const dates = new Set(['verified_at', 'verification_expires_at', 'blink_vip_until',
  'created_at', 'last_seen_at', 'last_seen']);
const arrays = new Set(['core_skills','hobbies','languages','interests']);
const jsonb = new Set(['skill_endorsements', 'badges']);
const columnType = (name) => bool.has(name) ? 'boolean' :
  numeric.has(name) ? 'bigint' : dates.has(name) ? 'timestamptz' :
  arrays.has(name) ? 'text[]' : jsonb.has(name) ? 'jsonb' : 'text';
const alice='00000000-0000-4000-8000-000000000001';
const bob='00000000-0000-4000-8000-000000000002';
const db = new PGlite();
try {
  await db.exec(`
    create role anon;
    create role authenticated;
    create role service_role;
    create schema auth;
    create function auth.uid() returns uuid language sql stable
      as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
    create table public.profiles (
      id uuid primary key,
      ${columns.map(c => c + ' ' + columnType(c)).join(',\n      ')},
      seller_payment_reference text, onboarding_secret text
    );
    create table public.blocks(blocker_id uuid, blocked_id uuid);
    create table public.follows(follower_id uuid, following_id uuid);
    create table public.user_settings(user_id uuid,show_online_status boolean);
    create table public.feed_posts(
      user_id uuid, is_reel boolean, view_count integer,
      like_count integer, comment_count integer, share_count integer,
      is_active boolean, is_flagged boolean
    );
  `);
  await db.query('insert into public.profiles(id,username,full_name,email,phone,whatsapp,email_visibility,phone_visibility,whatsapp_visibility,presence_visibility,seller_payment_reference,onboarding_secret) values ($1,\'alice\',\'Alice\',\'alice@example.test\',\'123456789\',\'123456789\',\'PUBLIC\',\'FOLLOWERS\',\'PRIVATE\',\'PRIVATE\',\'payment-secret\',\'hidden\'),($2,\'bob\',\'Bob\',\'bob@example.test\',\'987654321\',\'987654321\',\'PUBLIC\',\'PUBLIC\',\'PUBLIC\',\'PUBLIC\',\'secret-b\',\'other-secret\')', [alice,bob]);
  await db.query('insert into public.feed_posts(user_id,is_reel,view_count,like_count,comment_count,share_count,is_active,is_flagged) values ($1,false,20,5,3,2,true,false),($1,true,8,2,0,1,true,false),($1,false,100,100,100,100,false,false)',[alice]);
  await db.exec(sql);
  await db.query("select set_config('request.jwt.claim.sub',$1,false)",[bob]);
  const viewer = (await db.query("select public.get_profile_detail('alice') as profile")).rows[0].profile;
  assert.equal(viewer.full_name,'Alice');
  assert.equal(viewer.email,'alice@example.test');
  assert.equal(viewer.phone,'');
  assert.equal(viewer.whatsapp,'');
  assert.equal(viewer.relationship_status,null);
  assert.equal(viewer.email_visibility,'PUBLIC');
  assert.equal(viewer.phone_visibility,'PRIVATE');
  assert.equal(viewer.current_wallet_balance,0);
  for(const field of ['seller_payment_reference','onboarding_secret','seller_payment_provider_id','fcm_token']) {
    assert.equal(Object.hasOwn(viewer,field),false,'Internal profile property exposed: '+field);
  }
  await db.query('insert into public.follows(follower_id,following_id) values($1,$2)',[bob,alice]);
  const follower=(await db.query("select public.get_profile_detail('alice') as profile")).rows[0].profile;
  assert.equal(follower.phone,'123456789');
  assert.equal(follower.phone_visibility,'FOLLOWERS');
  const metrics=(await db.query('select public.get_profile_content_metrics($1) as metrics',[alice])).rows[0].metrics;
  assert.equal(Number(metrics.posts),1);
  assert.equal(Number(metrics.reels),1);
  assert.equal(Number(metrics.views),28);
  assert.equal(Number(metrics.likes),7);
  await db.query('insert into public.blocks(blocker_id,blocked_id) values($1,$2)',[alice,bob]);
  assert.equal((await db.query("select public.get_profile_detail('alice') as profile")).rows[0].profile,null);
  assert.equal((await db.query('select public.get_profile_content_metrics($1) as metrics',[alice])).rows[0].metrics,null);
  const permission=(await db.query(`select has_function_privilege('anon','public.get_profile_detail(text)','execute') as anon_exec,
    has_function_privilege('authenticated','public.get_profile_detail(text)','execute') as auth_exec`)).rows[0];
  assert.equal(permission.anon_exec,false);
  assert.equal(permission.auth_exec,true);
  console.log('PASS isolated public-profile allowlist, contact privacy, owner-block check, server content metrics and grants');
} finally {
  await db.close();
}
