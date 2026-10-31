// Disposable Supabase HTTP/Auth/Storage/Realtime integration. Never accepts a hosted URL.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';

const project = resolve(process.argv[2] || '/workspace/scratch/blink-isolated');
const cli = process.env.BLINK_TEST_SUPABASE_CLI || 'supabase';
const config = readFileSync(resolve(project, 'supabase/config.toml'), 'utf8');
assert.match(config, /project_id\s*=\s*"blink-feature-isolated"/, 'Only the disposable feature project is allowed');
const env = { ...process.env };
for (const key of ['DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH']) delete env[key];
const status = JSON.parse(execFileSync(cli, ['--workdir', project, 'status', '-o', 'json'], { env, stdio: ['ignore', 'pipe', 'pipe'] }));
const root = new URL(status.API_URL);
assert.equal(root.hostname, '127.0.0.1');
assert.equal(root.port, '54331');
const anon = status.ANON_KEY, service = status.SERVICE_ROLE_KEY;
assert.ok(anon && service, 'Disposable local keys are required');
const db = 'supabase_db_blink-feature-isolated';
const docker = ['--host=unix:///var/run/docker.sock'];
const sql = statement => execFileSync('docker', [...docker, 'exec', '-i', db, 'psql', '-U', 'postgres', '-XqAt', '-v', 'ON_ERROR_STOP=1'], { env, input: statement, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }).trim();
assert.equal(sql('select count(*) from auth.users;'), '0', 'Use a fresh disposable database');
sql(readFileSync(new URL('./prerequisites.pgfixture', import.meta.url), 'utf8'));
for (const name of [
  '20261028185000_blink_contests_free_entry.sql',
  '20261028200000_blink_contest_growth_v1.sql',
  '20261028200903_blink_poll_vote_expiration_guard_20261028.sql',
  '20261028200938_blink_block_unprotected_private_audiences_20261028.sql',
  '20261028212000_blink_contest_media_bucket.sql',
  '20261029190000_blink_contest_media_owner_select.sql',
  '20261031060000_blink_contest_require_entry_media.sql',
  '20261031070000_blink_contest_private_submissions.sql',
  '20261031080000_blink_contest_candidate_search.sql',
  '20261028215500_chat_video_albums.sql',
  '20261028220000_blink_chat_native_polls.sql',
]) sql(readFileSync(new URL(`../../migrations/${name}`, import.meta.url), 'utf8'));
sql("notify pgrst, 'reload schema';");

async function request(path, token = anon, body, method = 'POST', extra = {}) {
  const response = await fetch(new URL(path, root), {
    method, headers: { apikey: anon, Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...extra },
    body: body === undefined ? undefined : typeof body === 'string' || body instanceof Uint8Array ? body : JSON.stringify(body),
    signal: AbortSignal.timeout(20000),
  });
  const text = await response.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  return { status: response.status, data };
}
function ok(result, label) {
  assert.ok(result.status >= 200 && result.status < 300,
    `${label}: HTTP ${result.status} ${result.data?.message || result.data?.error || ''}`);
  return result.data;
}
async function rejected(path, token, body, message) {
  const result = await request(path, token, body);
  assert.ok(result.status >= 400, `Expected rejection: ${message}`);
  assert.match(JSON.stringify(result.data), message);
}
async function account(name) {
  const password = `Local-${randomUUID()}!`;
  const email = `${name}-${randomUUID()}@example.test`;
  const created = ok(await request('/auth/v1/admin/users', service, { email, password, email_confirm: true }), 'Create local account');
  const session = ok(await request('/auth/v1/token?grant_type=password', anon, { email, password }), 'Password grant');
  assert.equal(session.user.id, created.id);
  assert.ok(session.access_token);
  sql(`insert into public.profiles(id,username,full_name,university) values('${created.id}','${name}','${name}','FUTA');`);
  return { id: created.id, token: session.access_token };
}
const [admin, creator, voter, outsider] = await Promise.all(['admin', 'creator', 'voter', 'outsider'].map(account));
sql(`insert into private.local_fixture_admins values('${admin.id}');`);
console.log('PASS local GoTrue: four real password-grant sessions with distinct verified identities');
const rpc = async (name, actor, body) => ok(await request(`/rest/v1/rpc/${name}`, actor.token, body), name);
const later = days => new Date(Date.now() + days * 86400000).toISOString();
const contest = await rpc('admin_save_blink_contest', admin, { p_id: null, p_data: {
  title: 'Local authenticated contest', description: 'Disposable integration fixture', rules: 'One free vote', category: 'Creators',
  prize_first: 30000, prize_second: 15000, prize_third: 5000,
  registration_opens_at: later(-1), registration_closes_at: later(1), voting_opens_at: later(2), voting_closes_at: later(3), results_at: later(4), auto_approve: false,
} });
await rejected('/rest/v1/rpc/admin_transition_blink_contest', creator.token, { p_contest_id: contest.id, p_action: 'publish' }, /ADMIN_REQUIRED/);
await rpc('admin_transition_blink_contest', admin, { p_contest_id: contest.id, p_action: 'publish' });
await rejected('/rest/v1/rpc/apply_blink_contest', creator.token,
  { p_contest_id: contest.id, p_statement: 'Real local authenticated entry', p_media_url: null },
  /PRIVATE_CONTEST_MEDIA_REQUIRED|blink_contest_entry_media_required/);
await rejected('/rest/v1/rpc/apply_blink_contest', creator.token,
  { p_contest_id: contest.id, p_statement: 'Public media should be rejected',
    p_media_url: 'https://example.com/public.jpg' }, /PRIVATE_CONTEST_MEDIA_REQUIRED/);

const picture = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j5r0AAAAASUVORK5CYII=', 'base64');
const privatePath = `${creator.id}/${randomUUID()}.png`;
const privateUpload = await request(`/storage/v1/object/blink-contest-private/${privatePath}`,
  creator.token, picture, 'POST', { 'Content-Type': 'image/png' });
ok(privateUpload, 'Owner private media upload');
const privatePublic = await fetch(new URL(`/storage/v1/object/public/blink-contest-private/${privatePath}`, root));
assert.ok(privatePublic.status >= 400, 'Pending contestant media must not be publicly downloadable');
const outsiderRead = await request(`/storage/v1/object/authenticated/blink-contest-private/${privatePath}`,
  outsider.token, undefined, 'GET');
assert.ok(outsiderRead.status >= 400, 'Other users must not see pending media');
const ownerRead = await request(`/storage/v1/object/authenticated/blink-contest-private/${privatePath}`,
  creator.token, undefined, 'GET');
assert.equal(ownerRead.status, 200, 'Owner can review their private submission');
const privateMediaUrl = `https://blink-local-fixture.example/functions/v1/blink-contest-private-media/${privatePath}`;
const entry = await rpc('apply_blink_contest', creator, {
  p_contest_id: contest.id, p_statement: 'Real local authenticated entry',
  p_media_url: privateMediaUrl
});
const beforeApproval = await rpc('search_blink_contest_candidates', voter, {
  p_contest_id: contest.id, p_query: 'creator', p_limit: 20, p_offset: 0
});
assert.equal(beforeApproval.total, 0, 'Pending applications must never appear in search');
await rpc('admin_review_blink_contest_entry', admin, { p_entry_id: entry.entry_id, p_action: 'approve' });
const found = await rpc('search_blink_contest_candidates', outsider, {
  p_contest_id: contest.id, p_query: 'creator', p_limit: 20, p_offset: 0
});
assert.equal(found.total, 1);
assert.equal(found.entries[0].id, entry.entry_id);
const pastEnd = await rpc('search_blink_contest_candidates', outsider, {
  p_contest_id: contest.id, p_query: '', p_limit: 20, p_offset: 20
});
assert.equal(pastEnd.entries.length,0);
// Android must receive a complete approved candidate before creating its flyer;
// compact leaderboard rows deliberately omit contest_id.
const flyerCandidate = await rpc('get_blink_contest_candidate', voter, {
  p_contest_id: contest.id, p_entry_id: entry.entry_id
});
assert.equal(flyerCandidate.id, entry.entry_id);
assert.equal(flyerCandidate.contest_id, contest.id);
assert.equal(flyerCandidate.contest_title, 'Local authenticated contest');
assert.equal(flyerCandidate.rank, 1);
sql(`update public.blink_contests set registration_opens_at=now()-interval '3 days',registration_closes_at=now()-interval '2 days',voting_opens_at=now()-interval '1 day' where id='${contest.id}';`);

let socket;
try {
  const realtime = new URL('/realtime/v1/websocket', root);
  realtime.protocol = 'ws:'; realtime.searchParams.set('apikey', anon); realtime.searchParams.set('vsn', '1.0.0');
  socket = new WebSocket(realtime);
  let receive;
  const notification = new Promise(resolve => { receive = resolve; });
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Realtime subscription timed out')), 30000);
    socket.addEventListener('error', () => { clearTimeout(timer); reject(new Error('Local Realtime connection failed')); });
    socket.addEventListener('open', () => socket.send(JSON.stringify({ topic: 'realtime:contest-local', event: 'phx_join', ref: '1', payload: {
      access_token: creator.token, config: { broadcast: { self: false }, presence: { key: '' }, postgres_changes: [{ event: 'INSERT', schema: 'public', table: 'notifications', filter: `user_id=eq.${creator.id}` }] },
    } })));
    socket.addEventListener('message', message => {
      const packet = JSON.parse(message.data);
      if (packet.event === 'system' && packet.payload?.status === 'ok') { clearTimeout(timer); resolve(); }
      if (packet.event === 'phx_reply' && packet.payload?.status === 'error') { clearTimeout(timer); reject(new Error('Local Realtime subscription rejected')); }
      if (packet.event === 'postgres_changes') receive(packet.payload.data.record);
    });
  });
  const votes = await Promise.all(Array.from({ length: 8 }, () => request('/rest/v1/rpc/vote_blink_contest', voter.token, { p_contest_id: contest.id, p_entry_id: entry.entry_id })));
  assert.equal(votes.filter(result => result.status === 200).length, 1);
  assert.equal(sql('select count(*) from public.blink_contest_votes;'), '1');
  const delivered = await Promise.race([notification, new Promise((_, reject) => setTimeout(() => reject(new Error('Realtime vote notification not delivered')), 20000))]);
  assert.equal(delivered.user_id, creator.id);
  const hidden = ok(await request('/rest/v1/notifications?select=id', outsider.token, undefined, 'GET'), 'Notification RLS');
  assert.equal(hidden.length, 0);
  console.log('PASS local Contest: authenticated publish/apply/approve, one concurrent vote, real Realtime notification and recipient-only reads');
} finally { socket?.close(); }

const mediaPath = `${creator.id}/fixture.png`;
const upload = (path, actor) => request(`/storage/v1/object/blink-contest-media/${path}`, actor.token, picture, 'POST', { 'Content-Type': 'image/png' });
ok(await upload(mediaPath, creator), 'Owned Storage upload');
assert.ok((await upload(`${creator.id}/forged.png`, outsider)).status >= 400);
const publicFile = await fetch(new URL(`/storage/v1/object/public/blink-contest-media/${mediaPath}`, root));
assert.equal(publicFile.status, 200); assert.deepEqual(Buffer.from(await publicFile.arrayBuffer()), picture);
await request('/storage/v1/object/blink-contest-media', outsider.token, { prefixes: [mediaPath] }, 'DELETE');
assert.equal((await fetch(new URL(`/storage/v1/object/public/blink-contest-media/${mediaPath}`, root))).status, 200);
ok(await request('/storage/v1/object/blink-contest-media', creator.token, { prefixes: [mediaPath] }, 'DELETE'), 'Owner delete');
assert.notEqual((await fetch(new URL(`/storage/v1/object/public/blink-contest-media/${mediaPath}`, root))).status, 200);
console.log('PASS local Storage: public admin cover, private contestant submissions, owner-only preview and cross-account denial');

const conversation = randomUUID(), message = randomUUID();
sql(`insert into public.conversations(id) values('${conversation}'); insert into public.conversation_participants(conversation_id,user_id) values('${conversation}','${creator.id}'),('${conversation}','${voter.id}'); insert into public.messages(id,conversation_id,sender_id,content) values('${message}','${conversation}','${creator.id}','Design choice?');`);
await rpc('blink_chat_attach_poll', creator, { p_message_id: message, p_question: 'Design choice?', p_options: ['One', 'Two'] });
await rejected('/rest/v1/rpc/blink_chat_vote_poll', outsider.token, { p_message_id: message, p_option: 0 }, /NOT_PARTICIPANT/);
await rpc('blink_chat_vote_poll', voter, { p_message_id: message, p_option: 1 });
await rpc('blink_chat_vote_poll', voter, { p_message_id: message, p_option: 1 });
assert.equal((await rpc('blink_chat_poll_results', voter, { p_message_id: message })).total_votes, 1);
sql(`insert into public.blocks values('${creator.id}','${voter.id}');`);
await rejected('/rest/v1/rpc/blink_chat_poll_results', voter.token, { p_message_id: message }, /BLOCKED/);
console.log('PASS local Chat: authenticated poll attachment, membership, idempotent votes and blocked-user privacy');

const poll = randomUUID(), option = randomUUID();
sql(`insert into public.polls values('${poll}',now()+interval '1 day'); insert into public.poll_options values('${option}','${poll}');`);
const vote = { id: randomUUID(), poll_id: poll, option_id: option, user_id: voter.id };
ok(await request('/rest/v1/poll_votes', voter.token, vote), 'Authenticated feed vote');
await rejected('/rest/v1/poll_votes', outsider.token, { ...vote, id: randomUUID() }, /Vote owner/);
sql(`update public.polls set expires_at=now()-interval '1 second' where id='${poll}';`);
await rejected('/rest/v1/poll_votes', voter.token, { ...vote, id: randomUUID() }, /Poll has closed/);
await rejected('/rest/v1/feed_posts', creator.token, { id: randomUUID(), audience: 'Only me' }, /blink_no_unprotected_private_audience/);
console.log('PASS local posting guards: real JWT identity, poll expiry and private-audience rejection');
console.log('PASS disposable Supabase feature HTTP suite. Synthetic prerequisite/admin schema; not full deployed-schema or device E2E verification.');
