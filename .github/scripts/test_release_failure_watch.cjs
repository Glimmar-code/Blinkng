'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const monitor = require('./release_failure_watch.cjs');
const RUN = {
  name: 'Publish latest APK',
  id: 39177412601,
  status: 'completed',
  conclusion: 'failure',
  head_branch: 'main',
  head_sha: 'a'.repeat(40),
  head_repository: {full_name: 'Glimmar-code/Blinkng'},
};

test('watch only official, completed, failing production release pipelines', () => {
  assert.ok(monitor.classify(RUN));
  for (const override of [
    {status: 'in_progress'}, {conclusion: 'success'}, {conclusion: 'cancelled'},
    {head_branch: 'Testlab'}, {head_repository: {full_name: 'attacker/repo'}},
    {name: 'Untrusted build'}, {id: 'wrong'},
  ]) assert.equal(monitor.classify({...RUN, ...override}), null);
});

test('all monitored workflows yield concise actionable incidents', () => {
  for (const name of ['Signed Android release','Publish latest APK','Verify published BLINK APK']) {
    const alert = monitor.classify({...RUN, name});
    assert.ok(alert, name);
    assert.equal(alert.url, 'https://github.com/Glimmar-code/Blinkng/actions/runs/39177412601');
    assert.match(monitor.incidentMessage(alert), /did \*\*not\*\* merge/);
    assert.match(monitor.incidentMessage(alert), /Inspect the failing GitHub job logs/);
  }
});

test('duplicate failures update existing incident, rather than opening spam issues', async () => {
  const writes = {created:0,comments:0};
  const mock = {
    rest: {issues: {
      async getLabel() {return {data:{name:'blink-release-incident'}}},
      async createLabel(){throw Error('label unexpectedly created')},
      async listForRepo(){return {data:[]}},
      async createComment(){writes.comments++},
      async create(){writes.created++;return{data:{number:3}}},
    }},
    paginate: async ()=>[{number:2,title:'[BLINK release] Publish latest APK failed on main'}],
  };
  await monitor({github:mock,context:{payload:{workflow_run:RUN}},core:{info(){},warning(){}}});
  assert.equal(writes.created,0);
  assert.equal(writes.comments,1);
});

test('first failure creates an issue and never triggers rebuild', async () => {
  let created;
  const github={rest:{issues:{
    async getLabel(){return{data:{}}},
    async create({title,body,labels}){created={title,body,labels};return{data:{number:5}}},
  }},paginate:async()=>[]};
  await monitor({github,context:{payload:{workflow_run:RUN}},core:{info(){},warning(){}}});
  assert.match(created.title,/Publish latest APK/);
  assert.deepEqual(created.labels,['blink-release-incident']);
  assert.match(created.body,/previously verified release/);
});
