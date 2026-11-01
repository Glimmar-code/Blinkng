import {PGlite} from '@electric-sql/pglite';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';

// Run the real SQL fixture/assertions in a disposable database. No hosted credentials.
const db = new PGlite();
const migrations = [
  '20261028200903_blink_poll_vote_expiration_guard_20261028.sql',
  '20261028200938_blink_block_unprotected_private_audiences_20261028.sql',
];
try {
  await db.exec(readFileSync(new URL('./blink_posting_guards_fixture.pgfixture', import.meta.url), 'utf8'));
  for (const name of migrations) await db.exec(readFileSync(new URL('../migrations/' + name, import.meta.url), 'utf8'));
  await db.exec(readFileSync(new URL('./blink_posting_guards_assertions.sql', import.meta.url), 'utf8'));
  await db.exec('reset role;');
  await db.exec(readFileSync(new URL('../migrations/' + migrations[0], import.meta.url), 'utf8'));
  // The recorded ADD CONSTRAINT migration is a one-time migration, not a setup script.
  // A duplicate-object error on manual replay must leave the existing guard intact.
  const constraint = async () => (await db.query("select pg_get_constraintdef(oid) as definition from pg_constraint where conname = 'blink_no_unprotected_private_audience'" )).rows;
  const before = await constraint();
  assert.equal(before.length, 1);
  await assert.rejects(db.exec(readFileSync(new URL('../migrations/' + migrations[1], import.meta.url), 'utf8')), error => error.code === '42710');
  assert.deepEqual(await constraint(), before);
  console.log('PASS isolated posting guards: deadline, cross-poll option, actor identity, private audience, repeatable trigger and intact one-time constraint');
} finally {
  await db.close();
}
