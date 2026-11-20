import { PGlite } from '@electric-sql/pglite';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const seedSql = fs.readFileSync(
  new URL('../../docs/testlab/seed_2000_synthetic_campus_accounts.sql', import.meta.url),
  'utf8'
);
const migration = fs.readFileSync(
  new URL('../migrations/20261107110000_blink_test_bot_admin_controls.sql', import.meta.url),
  'utf8'
);
const actorId = '00000000-0000-4000-8000-000000000123';
const db = new PGlite();

try {
  await db.exec(`
    create role anon;
    create role authenticated;
    create role service_role;
    create schema private;
  `);
  await db.exec(seedSql);
  await db.exec(migration);

  const grants = (await db.query(`
    select
      has_schema_privilege('anon','private','USAGE') as anon_schema,
      has_schema_privilege('authenticated','private','USAGE') as auth_schema,
      has_table_privilege('service_role','private.blink_test_synthetic_accounts','SELECT') as service_table,
      has_function_privilege('anon','public.blink_test_bot_list()','EXECUTE') as anon_list,
      has_function_privilege('authenticated','public.blink_test_bot_list()','EXECUTE') as auth_list,
      has_function_privilege('service_role','public.blink_test_bot_list()','EXECUTE') as service_list
  `)).rows[0];
  assert.equal(grants.anon_schema, false);
  assert.equal(grants.auth_schema, false);
  assert.equal(grants.service_table, false, 'service role must use narrow audited RPCs');
  assert.equal(grants.anon_list, false);
  assert.equal(grants.auth_list, false);
  assert.equal(grants.service_list, true);

  await db.exec('set role service_role');
  const before = (await db.query('select count(*)::int as n from public.blink_test_bot_list()')).rows[0].n;
  assert.equal(before, 2000);

  await assert.rejects(
    db.query('select public.blink_test_bot_set_status(1, false, null, $1)', ['approved test pause']),
    /ACTOR_REQUIRED/
  );
  await assert.rejects(
    db.query('select public.blink_test_bot_set_status(1, false, $1, $2)', [actorId, 'no']),
    /REASON_REQUIRED/
  );

  const changed = (await db.query(
    'select public.blink_test_bot_set_status(1, false, $1, $2) as ok',
    [actorId, 'pause test account']
  )).rows[0].ok;
  assert.equal(changed, true);
  const paused = (await db.query(
    'select is_active from public.blink_test_bot_list() where id = 1'
  )).rows[0].is_active;
  assert.equal(paused, false);

  await db.exec('reset role');
  const audit = (await db.query(`
    select count(*)::int as n
    from private.blink_test_bot_admin_audit
    where account_id = 1 and actor_id = $1 and previous_active = true
      and new_active = false
  `, [actorId])).rows[0].n;
  assert.equal(audit, 1);

  await db.exec('set role service_role');
  const bulk = (await db.query(
    'select public.blink_test_bot_bulk_set_status(false, $1, $2) as n',
    [actorId, 'pause all test fixtures']
  )).rows[0].n;
  assert.equal(bulk, 1999, 'bulk pause should change only remaining active fixtures');

  const resumed = (await db.query(
    'select public.blink_test_bot_bulk_set_status(true, $1, $2) as n',
    [actorId, 'resume all test fixtures']
  )).rows[0].n;
  assert.equal(resumed, 2000);

  const finalState = (await db.query(`
    select count(*) filter (where is_active)::int as active,
           count(*) filter (where not is_test_only)::int as non_test
    from public.blink_test_bot_list()
  `)).rows[0];
  assert.equal(finalState.active, 2000);
  assert.equal(finalState.non_test, 0);
  await db.exec('reset role');
  console.log('PASS Testlab bot admin RPCs: private data, service-only grants, reason validation, lifecycle and audit');
} finally {
  await db.close();
}
