'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const queue = require('./testlab_batch_queue.cjs');

const base = {number: 222, state: 'open', draft: false,
  base: {ref: 'main'}, head: {ref: 'feature-a',
    repo: {full_name: 'Glimmar-code/Blinkng'}},
  labels: [{name: 'batch-testlab'}], body: ''};

test('only explicitly opted-in same-repository main PRs are batched', () => {
  assert.equal(queue.isCandidate(base), true);
  assert.equal(queue.isCandidate({...base, labels: []}), false);
  assert.equal(queue.isCandidate({...base, draft: true}), false);
  assert.equal(queue.isCandidate({...base, base: {ref: 'Testlab'}}), false);
  assert.equal(queue.isCandidate({...base, body: 'do not auto-merge'}), false);
  assert.equal(queue.isCandidate({...base, head: {...base.head,
    repo: {full_name: 'someone-else/untrusted'}}}), false);
  assert.equal(queue.isCandidate({...base, head: {...base.head,
    ref: 'Testlab-batch-1234'}}), false);
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

test('empty queue cannot create branches or merge', async () => {
  const github = {rest: {
    issues: {getLabel: async () => ({data: {}})},
    pulls: {list: async () => []}
  }, paginate: async fn => fn()};
  let message = '';
  await queue({github, context: {eventName: 'schedule', runId: 777},
    core: {info: s => { message = s; }}});
  assert.match(message, /No opted-in/);
});
