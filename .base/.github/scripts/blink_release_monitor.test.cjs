'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const policy = require('./blink_release_monitor.cjs');

const run = (more = {}) => ({
  id: 123456, name: 'Signed Android release',
  head_branch: 'main',
  head_repository: {full_name: 'Glimmar-code/Blinkng'},
  head_sha: 'a'.repeat(40),
  status: 'completed', conclusion: 'failure',
  html_url: 'https://github.com/Glimmar-code/Blinkng/actions/runs/123456',
  ...more,
});

test('alerts only exact trusted BLINK main release workflows', () => {
  const alert = policy.qualify(run());
  assert.equal(alert?.failed, true);
  assert.equal(alert?.runId, 123456);
  assert.equal(policy.qualify(run({head_branch:'Testlab-bug'})), null);
  assert.equal(policy.qualify(run({head_repository:{full_name:'someone/Blinkng'}})), null);
  assert.equal(policy.qualify(run({name:'Android quality gate'})), null);
  assert.equal(policy.qualify(run({status:'in_progress',conclusion:null})), null);
  assert.equal(policy.qualify(run({id:0})), null);
});

test('successful run clears a prior actionable failure', () => {
  assert.equal(policy.qualify(run({conclusion:'success'}))?.failed,false);
  assert.equal(policy.qualify(run({conclusion:'cancelled'})), null);
  assert.equal(policy.qualify(run({conclusion:'timed_out'}))?.failed,true);
  assert.equal(policy.qualify(run({conclusion:'action_required'}))?.failed,true);
});

test('new app update notification delivery failures are monitored', () => {
  const alert = policy.qualify(run({name: 'Notify BLINK website release'}));
  assert.equal(alert?.failed, true);
  assert.match(policy.details(alert), /Notify BLINK website release/);
});

test('release issue body preserves exact diagnostics and recovery safety', () => {
  const item = policy.qualify(run());
  const body = policy.details(item);
  assert.match(body,/\*\*Run ID:\*\* 123456/);
  assert.match(body,/previous verified latest APK public/);
  assert.match(body,/no automatic merge, rollback, restart, or publication/);
  const issue = {title:'[BLINK release] Signed Android release failed', body};
  assert.equal(policy.issueMatches(issue,item.workflow),true);
  assert.equal(policy.issueMatches(issue,'Publish latest APK'),false);
});
