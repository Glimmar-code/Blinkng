'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const queue = require('./testlab_batch_queue.cjs');

const NOW = Date.parse('2026-10-25T17:00:00Z');
const base = {number: 222, state: 'open', draft: false,
  title: 'Fix verified BLINK feed', body: '', updated_at: '2026-10-25T16:00:00Z',
  base: {ref: 'main'}, head: {ref: 'feature-a',
    repo: {full_name: 'Glimmar-code/Blinkng'}},
  labels: []};

test('automatically discovers safe recent ready PRs WITHOUT any labels', () => {
  assert.equal(queue.isCandidate(base, NOW), true);
  assert.equal(queue.isCandidate({...base, draft: true}, NOW), false);
  assert.equal(queue.isCandidate({...base, base: {ref: 'Testlab'}}, NOW), false);
  assert.equal(queue.isCandidate({...base, body: 'do not auto-merge'}, NOW), false);
  assert.equal(queue.isCandidate({...base, body: 'This is draft and must not be merged'}, NOW), false);
  assert.equal(queue.isCandidate({...base, body: 'Release: keep this draft until testing'}, NOW), false);
  assert.equal(queue.isCandidate({...base, title: '[DRAFT] unfinished'}, NOW), false);
  assert.equal(queue.isCandidate({...base, title: '[Superseded by #541] old'}, NOW), false);
  assert.equal(queue.isCandidate({...base, labels: [{name: 'do-not-merge'}]}, NOW), false);
  assert.equal(queue.isCandidate({...base, labels: [{name: 'needs-staging'}]}, NOW), false);
  assert.equal(queue.isCandidate({...base, head: {...base.head,
    repo: {full_name: 'someone-else/untrusted'}}}, NOW), false);
  assert.equal(queue.isCandidate({...base, head: {...base.head,
    ref: 'Testlab-batch-1234'}}, NOW), false);
  assert.equal(queue.isCandidate({...base,
    updated_at: '2026-08-01T00:00:00Z'}, NOW), false);
});


test('only internal Testlab-batch numeric heads are final candidates', () => {
  assert.equal(queue.isBatch({...base, head: {...base.head,
    ref: 'Testlab-batch-1234'}}), true);
  assert.equal(queue.isBatch(base), false);
  assert.equal(queue.isBatch({...base, head: {...base.head,
    ref: 'Testlab-batch-manual'}}), false);
});

test('dispatch every mandatory applicable combined quality gate', () => {
  const docs = queue.relevantWorkflows(['README.md']);
  for (const expected of [
    'android-build-check.yml', 'windows-desktop-check.yml',
    'supabase-safety-check.yml', 'secret-leak-guard.yml',
    'apk-delivery-check.yml'
  ]) assert.ok(docs.includes(expected), expected);
  const android = queue.relevantWorkflows(['app/src/main/Foo.kt']);
  assert.ok(android.includes('android-runtime-smoke.yml'));
  assert.ok(android.includes('release-smoke-test.yml'));
  const multi = queue.relevantWorkflows(['shared/src/Thing.kt', 'web/app.js',
    'game3d/project.godot']);
  for (const expected of ['android-runtime-smoke.yml', 'release-smoke-test.yml',
    'windows-desktop-check.yml', 'web-parity-test.yml',
    'domain-integration-check.yml', 'blink-life-3d-check.yml']) {
    assert.ok(multi.includes(expected), expected);
  }
  assert.deepEqual(queue.relevantWorkflows(['README.md']), docs,
    'dispatch order must be deterministic');
});

test('updating integration SHA invalidates both CI and release authorization', () => {
  const oldSha = 'a'.repeat(40);
  const body = 'Batch info\n\n<!-- BLINK_AGENT_VERIFIED_SHA: ' +
    oldSha + ' -->\n<!-- BLINK_BATCH_DISPATCH_SHA: ' + oldSha + ' -->';
  assert.equal(queue.bodyWithoutStaleMarkers(body), 'Batch info');
});

test('require exact source head SHA and PR association, without requiring latest main update', () => {
  const head = 'b'.repeat(40);
  const pr = {...base, head: {...base.head, sha: head}};
  const required = ['validate', 'guard', 'migration-safety', 'parity', 'integrity'];
  const checks = required.map((name, i) => ({
    id: i + 1, name, head_sha: head, app: {slug: 'github-actions'},
    status: 'completed', conclusion: 'success', pull_requests: [{number: pr.number}]
  }));
  assert.equal(queue.sourceChecksGreen(checks, pr, ['README.md']), null);
  assert.match(queue.sourceChecksGreen(
    [{...checks[0], head_sha: 'c'.repeat(40)}, ...checks.slice(1)],
    pr, ['README.md']), /validate/);
  assert.match(queue.sourceChecksGreen(
    [{...checks[0], pull_requests: [{number: 987}]}, ...checks.slice(1)],
    pr, ['README.md']), /validate/);
  assert.match(queue.sourceChecksGreen(
    [{...checks[0], conclusion: 'failure'}, ...checks.slice(1)],
    pr, ['README.md']), /validate/);
  assert.match(queue.sourceChecksGreen(checks.slice(1), pr, ['README.md']), /validate/);
  assert.match(queue.sourceChecksGreen(
    [...checks, {id: 99, name: 'optional-visual', head_sha: head,
      app: {slug: 'github-actions'}, status: 'in_progress',
      conclusion: null, pull_requests: [{number: pr.number}]}],
    pr, ['README.md']), /optional-visual/);
});

test('change requests block source promotion', () => {
  assert.equal(queue.hasBlockingReview([{id: 1, state: 'CHANGES_REQUESTED',
    user: {login: 'reviewer'}}]), true);
  assert.equal(queue.hasBlockingReview([
    {id: 1, state: 'CHANGES_REQUESTED', user: {login: 'reviewer'}},
    {id: 2, state: 'APPROVED', user: {login: 'reviewer'}}
  ]), false);
});

test('source scanning holds missing CI without creating a batch branch', async () => {
  const p = {...base, number: 222, updated_at: new Date().toISOString(),
    head: {...base.head, sha: 'b'.repeat(40)}, changed_files: 1};
  const github = {rest: {
    pulls: {
      list: async () => [p],
      get: async () => ({data: p}),
      listFiles: async () => [{filename: 'README.md'}],
    },
    checks: {listForRef: async () => []},
  }, paginate: async (fn, args) => fn(args)};
  const messages = [];
  const selected = await queue.selectVerified(github, [p],
    {info: x => messages.push(x)});
  assert.deepEqual(selected, []);
  assert.ok(messages.some(m => m.includes('No verifiable current-head CI')));
});

test('empty queue cannot create branches or merge', async () => {
  const github = {rest: {
    issues: {getLabel: async () => ({data: {}})},
    pulls: {list: async () => []}
  }, paginate: async fn => fn()};
  let message = '';
  await queue({github, context: {eventName: 'schedule', runId: 777},
    core: {info: s => { message = s; }}});
  assert.match(message, /No recent/);
});

test('the scheduled scanner evaluates unlabeled PRs rather than treating array index as time', async () => {
  const p = {...base, updated_at: new Date().toISOString(), changed_files: 1,
    head: {...base.head, sha: 'e'.repeat(40)}};
  const logs = [];
  const github = {rest: {
    pulls: {
      list: async () => [p],
      get: async () => ({data: p}),
      listFiles: async () => [{filename: 'README.md'}],
    },
    checks: {listForRef: async () => []},
  }, paginate: async (fn, args) => fn(args)};
  await queue({github, context: {eventName: 'schedule', runId: 100},
    core: {info: message => logs.push(message)}});
  assert.ok(logs.some(m => m.includes('Skipping #' + p.number) &&
    m.includes('No verifiable current-head CI checks')));
});
