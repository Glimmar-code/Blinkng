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

  // Fallback after a ranked-RPC timeout must be restricted by the same
  // moderation/audience/expiry checks as ranked discovery.
  const privacyFix = fs.readFileSync(new URL('../migrations/20261011065000_guard_paginated_fallback_visibility.sql', import.meta.url), 'utf8');
  await db.exec(privacyFix);
  const publicPost = '00000000-0000-4000-8000-000000000131';
  const flaggedPost = '00000000-0000-4000-8000-000000000132';
  const expiredPost = '00000000-0000-4000-8000-000000000133';
  const privatePost = '00000000-0000-4000-8000-000000000134';
  const followersPost = '00000000-0000-4000-8000-000000000135';
  await reset();
  await db.exec(`insert into public.feed_posts
    (id,user_id,text,is_reel,is_active,is_flagged,audience,created_at,creator_post_number,expires_at)
    values
    ('${publicPost}','${A}','An older public post',false,true,false,'Everyone',now()-interval '20 days',9121,null),
    ('${flaggedPost}','${A}','Flagged content',false,true,true,'Everyone',now(),9122,null),
    ('${expiredPost}','${A}','Expired content',false,true,false,'Everyone',now(),9123,now()-interval '1 day'),
    ('${privatePost}','${A}','Private content',false,true,false,'Private',now(),9124,null),
    ('${followersPost}','${A}','Follower content',false,true,false,'Followers',now(),9125,null)`);
  await signIn(B);
  const visibleIds = (await db.query("select id from public.get_feed_page(60,null,null,'posts',null)")).rows.map(r => r.id);
  assert.ok(visibleIds.includes(publicPost), 'older public posts remain available for fallback');
  assert.ok(visibleIds.includes(followersPost), 'legitimate followers may see follower-only posts');
  for (const blockedId of [flaggedPost, expiredPost, privatePost]) {
    assert.ok(!visibleIds.includes(blockedId), 'fallback must not reveal flagged, expired or private content');
  }
  await signIn(A);
  const ownIds = (await db.query("select id from public.get_feed_page(60,null,null,'posts',null)")).rows.map(r => r.id);
  assert.ok(ownIds.includes(privatePost), 'authors retain access to own private content');
  assert.ok(!ownIds.includes(flaggedPost), 'moderation still applies to authors');
  console.log('PASS fallback: legacy pagination enforces audience, expiry and moderation');

  // Exercise the migration's guarded SQL rewriting inside disposable PostgreSQL.
  // The production scorer needs PostGIS and the full live schema, so this
  // compact scoring fixture tests the exact seven-day exclusion being changed.
  await reset();
  await db.exec(`create or replace function private_ranking.get_discovery_feed(
    p_limit integer default 40, p_offset integer default 0,
    p_as_of timestamptz default now(), p_gravity numeric default 1.5,
    p_w1 numeric default 2.0, p_w2 numeric default 1.0,
    p_w3 numeric default 250.0, p_w4 numeric default 4.0)
    returns table(item jsonb,feed_score numeric,ranking_components jsonb,
      feed_position integer,as_of timestamptz,next_offset integer)
    language plpgsql security definer set search_path to ''
    as $ranked$
    begin
      return query select to_jsonb(fp), 1::numeric,'{}'::jsonb,1,p_as_of,1
        from public.feed_posts fp
        where fp.is_active=true
          and fp.created_at > p_as_of - interval '7 days'
        order by fp.created_at desc limit p_limit;
    end;
    $ranked$;`);
  const getRanked = async () => (await db.query(
    "select item->>'id' as id from private_ranking.get_discovery_feed(60,0,now(),1.5,2,1,250,4)"
  )).rows.map(row => row.id);
  assert.ok(!(await getRanked()).includes(publicPost), 'seven-day scorer excludes 20-day post');
  const historyFix = fs.readFileSync(new URL('../migrations/20261011064000_expand_discovery_feed_history.sql', import.meta.url), 'utf8');
  await db.exec(historyFix);
  assert.ok((await getRanked()).includes(publicPost), '90-day scorer now includes existing older posts');
  console.log('PASS ranked history: guarded migration restores older eligible posts');

  // A compact version of the live view-count function checks migration syntax,
  // row-lock order and counter correctness. Production has additional caps
  // and rewards, retained byte-for-byte by the source rewriting migration.
  await db.exec(`create or replace function public.record_content_view(p_post_id uuid,p_event_id uuid)
    returns jsonb language plpgsql security definer set search_path to ''
    as $view$
    declare
      v_viewer_id uuid := auth.uid();
      v_view_count integer := 0;
      v_is_reel boolean := false;
      v_view_weight integer := 1;
    begin
    select coalesce(fp.view_count, 0), coalesce(fp.is_reel, false)
      into v_view_count, v_is_reel
      from public.feed_posts fp
     where fp.id = p_post_id
       and (fp.is_active = true or fp.user_id = v_viewer_id)
     for update;
    update public.feed_posts
       set view_count = coalesce(view_count, 0) + v_view_weight,
           updated_at = now()
     where id = p_post_id
    returning view_count into v_view_count;
    begin
        perform public.award_repost_distribution_points(p_post_id, v_viewer_id, 'view');
    exception when others then
        null;
    end;
    return jsonb_build_object('view_count',v_view_count);
    end;
    $view$;`);
  const lockFix = fs.readFileSync(new URL('../migrations/20261011064500_release_feed_view_row_lock_early.sql', import.meta.url), 'utf8');
  await db.exec(lockFix);
  const viewSource = await scalar("select pg_get_functiondef('public.record_content_view(uuid,uuid)'::regprocedure)");
  assert.ok(viewSource.includes('for key share;'), 'nonexclusive key lock protects post integrity');
  assert.ok(viewSource.indexOf('returning view_count into v_view_count;') >
    viewSource.indexOf("perform public.award_repost_distribution_points("),
    'exclusive counter update happens after expensive reward bookkeeping');
  await signIn(B);
  const countBefore = await scalar(`select view_count from public.feed_posts where id='${publicPost}'`);
  const counted = await scalar(`select (public.record_content_view('${publicPost}',gen_random_uuid())->>'view_count')::int`);
  assert.equal(counted, countBefore + 1);
  assert.equal(await scalar(`select view_count from public.feed_posts where id='${publicPost}'`), counted);
  console.log('PASS view counter: guarded lock migration preserves atomic weighted increment');
  await reset();
}
