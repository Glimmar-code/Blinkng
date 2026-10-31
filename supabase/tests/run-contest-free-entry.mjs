// Disposable PostgreSQL validation for BLINK Contest; never connects to live Supabase.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

const db = new PGlite();
const migration = readFileSync(
  new URL('../migrations/20261028185000_blink_contests_free_entry.sql', import.meta.url), 'utf8'
);
const growthMigration = readFileSync(
  new URL('../migrations/20261028200000_blink_contest_growth_v1.sql', import.meta.url), 'utf8'
);
const mediaRequirement = readFileSync(
  new URL('../migrations/20261031060000_blink_contest_require_entry_media.sql', import.meta.url), 'utf8'
);
const privateMedia = readFileSync(
  new URL('../migrations/20261031070000_blink_contest_private_submissions.sql', import.meta.url), 'utf8'
);
const mediaOne = `https://example.test/functions/v1/blink-contest-private-media/${creator1}/11111111-1111-4111-8111-111111111111.jpg`;
const mediaTwo = `https://example.test/functions/v1/blink-contest-private-media/${creator2}/22222222-2222-4222-8222-222222222222.jpg`;
const admin = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const creator1 = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const creator2 = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
const voter = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
const later = (days) => new Date(Date.now() + days * 86400000).toISOString();
const payload = {
  title: 'BLINK Creator Challenge',
  description: 'Show your campus creativity',
  rules: 'One entry per person. Winners are chosen by valid votes.',
  category: 'Creators',
  prize_first: 30000,
  prize_second: 15000,
  prize_third: 5000,
  registration_opens_at: later(-1),
  registration_closes_at: later(1),
  voting_opens_at: later(2),
  voting_closes_at: later(3),
  results_at: later(4),
  auto_approve: false,
};
const q = async (sql, params=[]) => db.query(sql, params);
const setUser = async (user) => db.exec("select set_config('request.jwt.claim.sub','" + user + "',false);");
const rpc = async (name, params, args) => q('select public.' + name + '(' + params + ') as result', args);
const json = (v) => typeof v === 'string' ? JSON.parse(v) : v;

try {
  await db.exec(readFileSync(new URL('./contest-fixture.pgfixture', import.meta.url), 'utf8'));
  for (const [id,username] of [
    [admin,'contestadmin'],[creator1,'creatorone'],[creator2,'creatortwo'],[voter,'voterthree']
  ]) {
    await q('insert into auth.users(id) values ($1)', [id]);
    await q('insert into public.profiles(id,username,full_name,university) values($1,$2,$3,$4)',
      [id,username,username.toUpperCase(),'FUTA']);
  }
  await db.exec(migration);
  await db.exec(growthMigration);
  await db.exec(mediaRequirement);
  await db.exec(privateMedia);
  await q('insert into storage.objects(bucket_id,name) values ($1,$2),($1,$3)', [
    'blink-contest-private',
    creator1 + '/11111111-1111-4111-8111-111111111111.jpg',
    creator2 + '/22222222-2222-4222-8222-222222222222.jpg'
  ]);
  await setUser(creator1);
  await assert.rejects(() => rpc('admin_save_blink_contest', '$1::uuid,$2::jsonb',
    [null,JSON.stringify(payload)]), /ADMIN_REQUIRED/);

  await setUser(admin);
  const created = json((await rpc('admin_save_blink_contest', '$1::uuid,$2::jsonb',
    [null,JSON.stringify(payload)])).rows[0].result);
  assert.equal(created.status,'draft');
  const id = created.id;
  assert.equal(created.prize_first, 30000);
  await assert.rejects(() => rpc('admin_finalize_blink_contest','$1::uuid',[created.id]),
    /RESULTS_NOT_READY/);
  await rpc('admin_transition_blink_contest', '$1::uuid,$2::text',[id,'publish']);

  await setUser(creator1);
  await assert.rejects(() => rpc('apply_blink_contest', '$1::uuid,$2::text,$3::text',
    [id,'I have a contest entry but no media',null]), /blink_contest_entry_media_required/);
  await assert.rejects(() => rpc('apply_blink_contest', '$1::uuid,$2::text,$3::text',
    [id,'I have a contest entry with public media','https://example.com/public.jpg']),
    /PRIVATE_CONTEST_MEDIA_REQUIRED/);
  await assert.rejects(() => rpc('apply_blink_contest', '$1::uuid,$2::text,$3::text',
    [id,'I have another user private media',mediaTwo]), /PRIVATE_CONTEST_MEDIA_REQUIRED/);
  const first = json((await rpc('apply_blink_contest', '$1::uuid,$2::text,$3::text',
    [id,'I make creative campus videos',mediaOne])).rows[0].result);
  assert.equal(first.status,'pending');
  await assert.rejects(() => rpc('apply_blink_contest', '$1::uuid,$2::text,$3::text',
    [id,'Another duplicated application',mediaOne]), /duplicate|unique|CONTEST_FULL/i);

  await setUser(creator2);
  const second = json((await rpc('apply_blink_contest', '$1::uuid,$2::text,$3::text',
    [id,'I create photography stories',mediaTwo])).rows[0].result);
  assert.notEqual(first.entry_id, second.entry_id);

  await setUser(admin);
  const entries = json((await rpc('admin_list_blink_contest_entries', '$1::uuid', [id])).rows[0].result);
  assert.equal(entries.length, 2);
  await rpc('admin_review_blink_contest_entry', '$1::uuid,$2::text', [first.entry_id,'approve']);
  await rpc('admin_review_blink_contest_entry', '$1::uuid,$2::text', [second.entry_id,'approve']);

  // Every approved contestant has a validated public share destination.
  await setUser(voter);
  const approved = json((await rpc('get_blink_contest_candidate', '$1::uuid,$2::uuid',
    [id,first.entry_id])).rows[0].result);
  assert.equal(approved.id, first.entry_id);
  assert.equal(approved.username, 'creatorone');
  assert.equal(approved.name, 'CREATORONE');
  assert.equal(approved.rank, 1);
  assert.equal(approved.votes, 0);
  await assert.rejects(() => rpc('record_blink_contest_share','$1::uuid,$2::uuid,$3::text',
    [id,'99999999-9999-4999-8999-999999999999','copy']), /CANDIDATE_NOT_AVAILABLE/);
  const shared = json((await rpc('record_blink_contest_share','$1::uuid,$2::uuid,$3::text',
    [id,first.entry_id,'native'])).rows[0].result);
  assert.equal(shared.recorded,true);
  await assert.rejects(() => rpc('get_blink_contest_candidate', '$1::uuid,$2::uuid',
    [id,'99999999-9999-4999-8999-999999999999']), /CANDIDATE_NOT_AVAILABLE/);

  await assert.rejects(() => rpc('vote_blink_contest','$1::uuid,$2::uuid',
    [id, first.entry_id]), /VOTING_CLOSED/);

  // Move the disposable fixture clock-window forward without waiting days.
  await q(`update public.blink_contests
    set registration_opens_at=now()-interval '3 days',
        registration_closes_at=now()-interval '1 day',
        voting_opens_at=now()-interval '12 hours',
        voting_closes_at=now()+interval '1 day',
        results_at=now()+interval '2 days' where id=$1`,[id]);
  const voted = json((await rpc('vote_blink_contest', '$1::uuid,$2::uuid',
    [id,first.entry_id])).rows[0].result);
  assert.equal(voted.success,true);
  await assert.rejects(() => rpc('vote_blink_contest', '$1::uuid,$2::uuid',
    [id,second.entry_id]), /duplicate|unique/i);

  await setUser(creator1);
  await assert.rejects(() => rpc('vote_blink_contest', '$1::uuid,$2::uuid',
    [id,first.entry_id]), /SELF_VOTE_NOT_ALLOWED/);
  const details = json((await rpc('get_blink_contest', '$1::uuid',[id])).rows[0].result);
  assert.equal(details.leaderboard[0].id, first.entry_id);
  assert.equal(details.leaderboard[0].votes, 1);
  assert.equal(details.my_rank, 1);
  assert.equal(details.total_ranked, 2);

  // Rank movement comes from a persisted baseline. No baseline => no arrows.
  const baseline=(await q('select entry_id,rank from public.blink_contest_rank_baselines where contest_id=$1',
    [id])).rows;
  assert.equal(baseline.length,2);
  await q('update public.blink_contest_rank_baselines set rank=2 where entry_id=$1',
    [first.entry_id]);
  const moved=json((await rpc('get_blink_contest','$1::uuid',[id])).rows[0].result);
  assert.equal(moved.leaderboard[0].rank_delta,1);
  assert.equal(moved.my_rank_delta,1);
  await q('update public.blink_contest_rank_baselines set rank=1 where entry_id=$1',
    [first.entry_id]);
  const voteRow=(await q('select id from public.blink_contest_votes where voter_id=$1',[voter])).rows[0].id;
  await setUser(admin);
  const stats1=json((await rpc('admin_blink_contest_insights','$1::uuid',[id])).rows[0].result);
  assert.equal(Number(stats1.shares),1);
  assert.equal(Number(stats1.unique_sharers),1);
  assert.equal(Number(stats1.counted_votes),1);
  const held=json((await rpc('admin_review_blink_contest_vote','$1::uuid,$2::text,$3::text',
    [voteRow,'hold','Suspicious coordinated pattern'])).rows[0].result);
  assert.equal(held.status,'under_review');
  const stats2=json((await rpc('admin_blink_contest_insights','$1::uuid',[id])).rows[0].result);
  assert.equal(Number(stats2.review_votes),1);
  assert.equal(Number(stats2.counted_votes),0);
  const approvedVote=json((await rpc('admin_review_blink_contest_vote','$1::uuid,$2::text,$3::text',
    [voteRow,'approve','Manual investigation completed'])).rows[0].result);
  assert.equal(approvedVote.status,'valid');


  const updated = json((await rpc('get_blink_contest_candidate', '$1::uuid,$2::uuid',
    [id,first.entry_id])).rows[0].result);
  assert.equal(updated.rank, 1);
  assert.equal(updated.votes, 1);
  assert.equal(updated.contest_title, 'BLINK Creator Challenge');

  await setUser(admin);
  await rpc('admin_transition_blink_contest', '$1::uuid,$2::text',[id,'pause']);
  await setUser(creator2);
  await assert.rejects(() => rpc('vote_blink_contest', '$1::uuid,$2::uuid',
    [id,first.entry_id]), /VOTING_CLOSED/);
  await setUser(admin);
  await assert.rejects(() => rpc('admin_transition_blink_contest', '$1::uuid,$2::text',
    [id,'complete']), /INVALID_CONTEST_TRANSITION/);

  // Close the disposable contest and snapshot winners by trustworthy counted votes.
  await q(`update public.blink_contests set voting_closes_at=now()-interval '1 hour',
    results_at=now()-interval '30 minutes' where id=$1`,[id]);
  const final=json((await rpc('admin_finalize_blink_contest','$1::uuid',[id])).rows[0].result);
  assert.equal(final.status,'completed');
  assert.equal(final.winner_count,2);
  const finalDetail=json((await rpc('get_blink_contest','$1::uuid',[id])).rows[0].result);
  assert.equal(finalDetail.winners.length,2);
  assert.equal(finalDetail.winners[0].prize_amount,30000);
  assert.equal(finalDetail.winners[0].payment_status,'pending');
  const prizes=json((await rpc('admin_blink_contest_insights','$1::uuid',[id])).rows[0].result).prizes;
  assert.equal(prizes.length,2);
  await assert.rejects(() => rpc('admin_record_blink_contest_prize',
    '$1::uuid,$2::text,$3::text,$4::text',[prizes[0].id,'paid','',null]),
    /RECEIPT_REFERENCE_REQUIRED/);
  const paid=json((await rpc('admin_record_blink_contest_prize',
    '$1::uuid,$2::text,$3::text,$4::text',
    [prizes[0].id,'paid','external-receipt-123','Manually verified receipt'])).rows[0].result);
  assert.equal(paid.status,'paid');
  await assert.rejects(() => rpc('admin_record_blink_contest_prize',
    '$1::uuid,$2::text,$3::text,$4::text',
    [prizes[0].id,'processing',null,null]), /PAID_PRIZE_LOCKED/);
  const rows=(await q(`select text from public.notifications where metadata->>'kind'='blink_contest_winner'`)).rows;
  assert.equal(rows.length,2);
  await setUser(creator1);
  await assert.rejects(() => rpc('admin_blink_contest_insights','$1::uuid',[id]), /ADMIN_REQUIRED/);
  console.log('PASS BLINK Contest growth: secure share analytics, vote alerts, review queue, winners and payment ledger');
  console.log('PASS BLINK Contest: SQL migration, admin role, entry approval, one free vote, ranking and timing');
} finally {
  await db.close();
}
