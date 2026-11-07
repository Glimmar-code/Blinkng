import { PGlite } from '@electric-sql/pglite';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const seedSql = fs.readFileSync(
  new URL('../../docs/testlab/seed_2000_synthetic_campus_accounts.sql', import.meta.url),
  'utf8'
);
const db = new PGlite();

try {
  await db.exec('create schema private;');
  await db.exec(seedSql);

  const summary = (await db.query(`
    select
      count(*)::int as total,
      count(distinct username)::int as unique_usernames,
      count(distinct full_name)::int as unique_names,
      count(distinct university)::int as universities,
      count(*) filter (where avatar_url is not null)::int as with_avatar,
      count(*) filter (where can_like and can_follow)::int as can_like_follow,
      count(*) filter (where can_post or can_comment or can_send_messages)::int as forbidden_actions,
      count(*) filter (where not can_receive_messages)::int as cannot_receive,
      count(*) filter (where leaderboard_points <> 0)::int as nonzero_points,
      count(*) filter (where not is_test_only)::int as non_test_accounts
    from private.blink_test_synthetic_accounts
  `)).rows[0];

  assert.equal(summary.total, 2000);
  assert.equal(summary.unique_usernames, 2000);
  assert.equal(summary.unique_names, 2000);
  assert.equal(summary.universities, 20);
  assert.equal(summary.with_avatar, 0);
  assert.equal(summary.can_like_follow, 2000);
  assert.equal(summary.forbidden_actions, 0);
  assert.equal(summary.cannot_receive, 0);
  assert.equal(summary.nonzero_points, 0);
  assert.equal(summary.non_test_accounts, 0);

  const interactionCount = (await db.query(
    'select count(*)::int as total from private.blink_test_synthetic_interactions'
  )).rows[0].total;
  assert.equal(interactionCount, 0, 'fixture creation must not fabricate engagement');

  // Re-running the seed is idempotent: it must not duplicate profiles.
  await db.exec(seedSql);
  const rerunCount = (await db.query(
    'select count(*)::int as total from private.blink_test_synthetic_accounts'
  )).rows[0].total;
  assert.equal(rerunCount, 2000);

  const privileges = (await db.query(`
    select
      has_schema_privilege('anon', 'private', 'USAGE') as anon_schema_access,
      has_schema_privilege('authenticated', 'private', 'USAGE') as auth_schema_access,
      has_table_privilege('authenticated', 'private.blink_test_synthetic_accounts', 'SELECT') as auth_table_access
  `)).rows[0];
  assert.equal(privileges.anon_schema_access, false);
  assert.equal(privileges.auth_schema_access, false);
  assert.equal(privileges.auth_table_access, false);

  console.log('PASS 2,000 isolated synthetic campus fixtures; policy, privacy, and idempotency verified');
} finally {
  await db.close();
}
