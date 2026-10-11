import assert from 'node:assert/strict';
import fs from 'node:fs';

const A = '00000000-0000-4000-8000-000000000001';
const B = '00000000-0000-4000-8000-000000000002';
const C = '00000000-0000-4000-8000-000000000003';
const pending = '00000000-0000-4000-8000-000000000099';
const migration = fs.readFileSync(new URL('../migrations/20261010170000_repair_feed_permissions_and_post_counts.sql', import.meta.url), 'utf8');

export async function feedRecoveryTests(db) {
  const scalar = async q => Object.values((await db.query(q)).rows[0])[0];
  const reset = () => db.exec('reset role');
  const signIn = async id => {
    await db.exec(`reset role;
      select set_config('request.jwt.claim.sub','${id}',false);
      set role authenticated;`);
  };
  await reset();
  // Fixture has no old feed RPC, so provide the restricted owner check its
  // production definition already supplies. Auth has no private schema USAGE.
  await db.exec(`create or replace function private.is_blink_owner_id(p_user_id uuid)
    returns boolean language sql stable security definer set search_path to ''
    as $$ select false $$;
    revoke all on function private.is_blink_owner_id(uuid) from public,anon;
    grant execute on function private.is_blink_owner_id(uuid) to authenticated;
    revoke usage on schema private from authenticated;`);

  await db.exec(migration);
  console.log('PASS migration: restricted feed endpoint + profile counter backfill');

  const before = await scalar(`select count(*)::int from public.feed_posts
    where user_id='${A}' and is_active`);
  assert.equal(await scalar(`select posts_count from public.profiles where id='${A}'`), before);

  await signIn(B);
  const following = (await db.query("select id,user_id from public.get_feed_page(50,null,null,'following',null)")).rows;
  assert.ok(following.some(p => p.user_id === A), 'following must include followed author');
  assert.ok(!following.some(p => p.user_id === C), 'unfollowed authors stay out of following');
  await signIn(C);
  const blocked = (await db.query("select user_id from public.get_feed_page(50,null,null,'posts',null)")).rows;
  assert.ok(!blocked.some(p => p.user_id === A), 'mutual blocks must remain effective');
  await db.exec("reset role; set role anon;");
  await assert.rejects(db.query("select * from public.get_feed_page(5,null,null,'posts',null)"), /permission denied/);
  await db.exec("reset role; select set_config('request.jwt.claim.sub','',false); set role authenticated;");
  assert.equal((await db.query("select * from public.get_feed_page(5,null,null,'posts',null)")).rows.length,0);
  console.log('PASS permissions: authenticated feed works without private schema USAGE, blocks and anonymous denial');

  await reset();
  await db.exec(`insert into public.feed_posts(id,user_id,text,is_reel,is_active,creator_post_number)
    values ('${pending}','${A}','Recoverable post',false,false,9901)`);
  assert.equal(await scalar(`select posts_count from public.profiles where id='${A}'`),before);
  await db.exec(`update public.feed_posts set is_active=true where id='${pending}'`);
  assert.equal(await scalar(`select posts_count from public.profiles where id='${A}'`),before+1);
  await db.exec(`update public.feed_posts set is_active=true where id='${pending}'`);
  assert.equal(await scalar(`select posts_count from public.profiles where id='${A}'`),before+1);
  await db.exec(`update public.feed_posts set user_id='${B}' where id='${pending}'`);
  assert.equal(await scalar(`select posts_count from public.profiles where id='${A}'`),before);
  assert.equal(await scalar(`select posts_count from public.profiles where id='${B}'`),
    await scalar(`select count(*)::int from public.feed_posts where user_id='${B}' and is_active`));
  await db.exec(`delete from public.feed_posts where id='${pending}'`);
  assert.equal(await scalar(`select posts_count from public.profiles where id='${B}'`),
    await scalar(`select count(*)::int from public.feed_posts where user_id='${B}' and is_active`));
  await db.exec(migration);
  assert.equal(await scalar(`select count(*)::int from pg_trigger where tgrelid='public.feed_posts'::regclass and tgname='trg_blink_active_post_count'`),1);
  assert.equal(await scalar(`select posts_count from public.profiles where id='${A}'`), before);
  console.log('PASS counter: insert/update/owner change/delete and repeated migration preserve active post counts');
  await reset();
}
