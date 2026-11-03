import assert from 'node:assert/strict';
export async function legacyHardeningTests(db) {
  const A='00000000-0000-4000-8000-000000000001', B='00000000-0000-4000-8000-000000000002';
  await db.exec('reset role;');
  await db.exec('select private.expire_blink_items_and_remind_vip(); select private.process_blink_vip_auto_renewals();');
  console.log('PASS legacy: notification maintenance compiles against current columns');
  const commands=(await db.query("select polname,polcmd from pg_policy where polrelid in ('public.fcm_tokens'::regclass,'public.statuses'::regclass,'public.user_devices'::regclass) and polname like '%_own'")).rows;
  assert.equal(commands.some(row=>row.polcmd==='*'),false);
  await db.exec(`select set_config('request.jwt.claim.sub','${A}',false); set role authenticated;`);
  await assert.rejects(db.query("select public.record_game_session('fixture',999999,999999)"),/permission denied/);
  await assert.rejects(db.query('truncate public.user_devices'),/permission denied/);
  await db.query(`insert into public.fcm_tokens(user_id,token) values ('${A}','synthetic-owner-token')`);
  await assert.rejects(db.query(`insert into public.fcm_tokens(user_id,token) values ('${B}','synthetic-other-token')`),/row-level security/);
  assert.equal((await db.query(`select count(*)::int as count from public.fcm_tokens where user_id='${A}'`)).rows[0].count,1);
  await db.exec('reset role;');
  console.log('PASS legacy: policy commands, owner token writes and disabled client rewards');
}
