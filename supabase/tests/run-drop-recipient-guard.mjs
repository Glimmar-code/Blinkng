import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

const db = new PGlite();
const migration = fs.readFileSync(
  new URL('../migrations/20261027211500_blink_drop_require_recipients.sql', import.meta.url),
  'utf8',
);
const start = migration.indexOf('-- Also protect the legacy public.create_blink_drop RPC');
if (start < 0) throw new Error('Recipient guard migration section is missing.');

const userId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const failedDropId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const validDropId = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
const canClaim = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';

try {
  await db.exec(`
    create role anon;
    create role authenticated;
    create schema private;
    create table public.blink_drops(id uuid primary key, creator_id uuid not null);
    create table public.blink_drop_eligible_followers(
      drop_id uuid references public.blink_drops(id), user_id uuid not null,
      primary key(drop_id,user_id)
    );
    create table public.user_balances(user_id uuid primary key, coins bigint not null);
    insert into public.user_balances(user_id, coins) values ('${userId}',500);
  `);

  // Execute the exact committed deferred trigger definition; no production credentials.
  const guardSql = migration.slice(start);
  await db.exec(guardSql);
  await db.exec(guardSql); // Migration must be safe to reapply.

  await db.exec('begin');
  await db.exec(`
    update public.user_balances set coins=400 where user_id='${userId}';
    insert into public.blink_drops(id,creator_id) values ('${failedDropId}','${userId}');
  `);
  await assert.rejects(() => db.exec('commit'), /DROP_NO_ELIGIBLE_FOLLOWERS/);
  // A failed deferred trigger at commit rolls back the wallet debit and Drop row.
  const missing = await db.query('select count(*)::int as count from public.blink_drops');
  const wallet = await db.query(`select coins from public.user_balances where user_id='${userId}'`);
  assert.equal(missing.rows[0].count, 0, 'Unclaimable Drop must roll back');
  assert.equal(Number(wallet.rows[0].coins), 500, 'Reserved Coins must be restored');

  await db.exec('begin');
  await db.exec(`
    update public.user_balances set coins=400 where user_id='${userId}';
    insert into public.blink_drops(id,creator_id) values ('${validDropId}','${userId}');
    insert into public.blink_drop_eligible_followers(drop_id,user_id)
      values ('${validDropId}','${canClaim}');
  `);
  await db.exec('commit');
  const kept = await db.query('select count(*)::int as count from public.blink_drops');
  const finalWallet = await db.query(`select coins from public.user_balances where user_id='${userId}'`);
  assert.equal(kept.rows[0].count, 1, 'Eligible Drop should commit');
  assert.equal(Number(finalWallet.rows[0].coins), 400, 'Successful reserve persists');
  console.log('PASS Drops: zero-recipient coin rollback, eligible completion, idempotent guard migration');
} finally {
  await db.close();
}
