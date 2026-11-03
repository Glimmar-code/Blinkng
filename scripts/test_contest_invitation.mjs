import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const source = fs.readFileSync(new URL('../web/contest-invitation.js', import.meta.url), 'utf8');
const contest = '11111111-1111-4111-8111-111111111111';
const entry = '22222222-2222-4222-8222-222222222222';
async function render(response) {
  const elements = new Map();
  const node = id => elements.get(id) || elements.set(id, {
    textContent: '', href: '', setAttribute() {}, removeAttribute(key) { delete this[key]; },
    addEventListener() {}, replaceChildren() {},
  }).get(id);
  const requests = [];
  const context = {window: {BLINK_WEB_CONFIG: {supabaseUrl: 'https://fixture.invalid', publishableKey: 'fixture'}},
    document: {title: '', createElement: () => node('image')}, URL, AbortController,
    setTimeout, clearTimeout, navigator: {}, fetch: async (url, options) => {
      requests.push([url, options]);
      if (response instanceof Error) throw response;
      return response;
    }};
  vm.runInNewContext(source, context);
  const root = {innerHTML: '', querySelector: node};
  assert.equal(context.window.BlinkContestInvite.render(root, contest, entry), true);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(requests.length, 1);
  assert.ok(requests[0][0].endsWith('/get_blink_contest_candidate'));
  assert.ok(requests[0][1].signal instanceof AbortSignal);
  return {node, context};
}
const valid = await render({ok: true, json: async () => ({id: entry, name: '<script>bad()</script>', votes: 12, rank: 2, contest_status: 'voting'})});
assert.equal(valid.node('#contest-invite-name').textContent, '<script>bad()</script>');
assert.ok(valid.node('#contest-invite-open').href.startsWith('intent://contest/'));
for (const response of [new Error('offline'), {ok: false, status: 503}]) {
  const result = await render(response);
  assert.ok(result.node('#contest-invite-open').href.startsWith('intent://contest/'));
}
for (const response of [{ok: false, status: 404}, {ok: true, json: async () => ({id: contest})}]) {
  const result = await render(response);
  assert.equal(result.node('#contest-invite-open').href, undefined);
}
console.log('PASS contest invitation: verified identity, safe text, bounded request, outage fallback, invalid entries and no automatic voting');
