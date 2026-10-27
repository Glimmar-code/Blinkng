import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

const now = '2026-10-27T10:00:00.000Z';
const next = '2026-10-27T10:00:00.555Z';
const db = new PGlite();
try {
  await db.exec(`
    create table activity_push (notification_id text primary key, status text not null, updated_at timestamptz not null);
    create table message_push (message_id text primary key, status text not null, updated_at timestamptz not null);
    create table call_push (call_id text not null, event text not null, target_user_id text not null,
      status text not null, updated_at timestamptz not null, primary key(call_id,event,target_user_id));
  `);
  const cases = [
    { table: 'activity_push', column: 'notification_id', keys: ["'n'"] },
    { table: 'message_push', column: 'message_id', keys: ["'m'"] },
    { table: 'call_push', column: 'call_id,event,target_user_id', keys: ["'c'","'invite'","'u'"] },
  ];
  for (const { table, column, keys } of cases) {
    const cols = column.split(',');
    await db.exec(`insert into ${table}(${column},status,updated_at)
      values (${keys.join(',')},'failed','${now}')`);
    const condition = cols.map((x,i)=>`${x}=${keys[i]}`).join(' and ');
    const claim = `update ${table} set status='sending',updated_at='${next}'
      where ${condition} and status='failed' and updated_at='${now}'
      returning status`;
    const winner = await db.query(claim);
    const loser = await db.query(claim);
    assert.equal(winner.rows.length, 1, `${table} must acquire original claim`);
    assert.equal(loser.rows.length, 0, `${table} must deny stale double claim`);
    await assert.rejects(db.exec(`insert into ${table}(${column},status,updated_at)
      values (${keys.join(',')},'sending','${next}')`),
      /duplicate key|unique constraint/i, `${table} must reject double first claim`);
  }
  // Source-level contract: these are the same PostgREST filters used in each
  // dispatcher; no unconditional upsert may silently override a newer claim.
  for (const name of ['send-activity-notification','send-push-notification','send-call-notification']) {
    const src = fs.readFileSync(new URL(`../functions/${name}/index.ts`, import.meta.url),'utf8');
    assert.match(src, /\.eq\("updated_at", (existing|prior)\.updated_at\)/,
      `${name} must CAS the previously read timestamp`);
    assert.match(src, /\.eq\("status", (existing|prior)\.status\)/,
      `${name} must CAS the previous claim state`);
    assert.ok(!/from\("(notification|call)_push_dispatches"\)\.upsert\(/.test(src),
      `${name} must not use unconditional upsert for push claims`);
  }
  console.log('PASS three SQL delivery ledgers: atomic claims, unique initial inserts and matching TypeScript filters');
} finally {
  await db.close();
}
