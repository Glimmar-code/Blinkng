'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const recovery = require('./ci_auto_recovery.cjs');

const sha = 'a'.repeat(40);
const base = (more = {}) => ({
  id: 654, name: 'Android quality gate', status: 'completed',
  conclusion: 'failure', run_attempt: 1, head_branch: 'Testlab-fix',
  head_sha: sha, head_repository: {full_name: 'Glimmar-code/Blinkng'},
  pull_requests: [], html_url: 'https://github.com/Glimmar-code/Blinkng/actions/runs/654',
  ...more,
});
const failedJobs = [{name: 'validate', conclusion: 'failure',
  html_url: 'https://github.com/Glimmar-code/Blinkng/actions/runs/654/job/10',
  steps: [{name: 'Android unit tests', conclusion: 'failure'}]}];
const ctx = run => ({eventName: 'workflow_run', repo: {
  owner: 'Glimmar-code', repo: 'Blinkng'
}, payload: {workflow_run: run}});
function harness(run, opts = {}) {
  const invoked = {retry: [], issue: [], get: []};
  const current = opts.sha || sha;
  const rest = {
    repos: {getBranch: async () => ({data: {commit: {sha: current}}})},
    actions: {
      getWorkflowRun: async () => ({data: {
        run_attempt: opts.attempt || run.run_attempt,
        status: 'completed', conclusion: run.conclusion,
      }}),
      listJobsForWorkflowRun: async () => ({data: {jobs: opts.jobs || failedJobs}}),
      reRunWorkflowFailedJobs: async args => {invoked.retry.push(args); return {status: 201};},
    },
    issues: {
      listLabelsForRepo: async () => ({data: [{name: 'ci-recovery-blocked'}]}),
      listForRepo: async () => ({data: opts.issues || []}),
      update: async args => {invoked.issue.push(['update', args]);},
      create: async args => {invoked.issue.push(['create', args]); return {data: {number: 701}};},
      createLabel: async args => {invoked.issue.push(['label', args]);},
    },
  };
  const messages = [];
  const core = {info: text => messages.push(text), warning: text => messages.push(text)};
  return {invoked, messages, github: {rest}, core};
}

test('ignore successes, other repositories and unrelated branches', () => {
  for (const run of [
    base({conclusion: 'success'}),
    base({head_repository: {full_name: 'attacker/Blinkng'}}),
    base({head_branch: 'feature'}),
    base({name: 'Unknown workflow'}),
    base({name: 'Android quality gate', head_branch: 'main'}),
  ]) assert.equal(recovery.qualify(run), null);
});

test('retry failed Testlab jobs, never mutate source or bypass checks', async () => {
  const run = base();
  const h = harness(run);
  await recovery({github: h.github, context: ctx(run), core: h.core});
  assert.equal(h.invoked.retry.length, 1);
  assert.equal(h.invoked.retry[0].run_id, 654);
  assert.equal(h.invoked.retry[0].enable_debug_logging, false);
  assert.equal(h.invoked.issue.length, 0);
});

test('second retry enables diagnostic logging, and final failure opens issue', async () => {
  const run2 = base({run_attempt: 2});
  const h2 = harness(run2);
  await recovery({github: h2.github, context: ctx(run2), core: h2.core});
  assert.equal(h2.invoked.retry.length, 1);
  assert.equal(h2.invoked.retry[0].enable_debug_logging, true);
  const run3 = base({run_attempt: 3});
  const h3 = harness(run3);
  await recovery({github: h3.github, context: ctx(run3), core: h3.core});
  assert.equal(h3.invoked.retry.length, 0);
  assert.equal(h3.invoked.issue[0][0], 'create');
  assert.match(h3.invoked.issue[0][1].body, /Android unit tests/);
  assert.match(h3.invoked.issue[0][1].body, /Testlab branch/);
});

test('never rerun detected security failures, even on first attempt', async () => {
  for (const item of [
    base({name: 'Secret leak guard'}),
    base(),
  ]) {
    const h = harness(item, {jobs: [{
      name: 'guard', conclusion: 'failure', steps: [{
        name: 'Verify signing certificate security', conclusion: 'failure',
      }],
    }]});
    await recovery({github: h.github, context: ctx(item), core: h.core});
    assert.equal(h.invoked.retry.length, 0);
    assert.equal(h.invoked.issue.length, 1);
  }
});

test('never act on stale branch SHAs or already restarted attempts', async () => {
  const run = base();
  for (const opts of [{sha: 'b'.repeat(40)}, {attempt: 2}]) {
    const h = harness(run, opts);
    await recovery({github: h.github, context: ctx(run), core: h.core});
    assert.equal(h.invoked.retry.length, 0);
    assert.equal(h.invoked.issue.length, 0);
  }
});

test('production retries are limited to signing and publishing, not main CI', async () => {
  assert.equal(recovery.qualify(base({head_branch: 'main'})), null);
  const run = base({name: 'Publish latest APK', head_branch: 'main'});
  const h = harness(run);
  await recovery({github: h.github, context: ctx(run), core: h.core});
  assert.equal(h.invoked.retry.length, 1);
});

test('repeated blocker updates the existing incident without duplicating issues', async () => {
  const run = base({run_attempt: 3});
  const h = harness(run, {issues: [{
    number: 63, state: 'open',
    body: recovery.issueMarker('Testlab-fix', 'Android quality gate'),
  }]});
  await recovery({github: h.github, context: ctx(run), core: h.core});
  assert.deepEqual(h.invoked.issue.map(x => x[0]), ['update']);
  assert.equal(h.invoked.issue[0][1].issue_number, 63);
});
