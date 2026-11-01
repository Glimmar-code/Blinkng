// Regression tests for the trusted verified feature-PR -> main release gate.
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const policy = require('./testlab_auto_promotion.cjs');

const main = 'a'.repeat(40);
const head = 'b'.repeat(40);
const ready = (more = {}) => ({
  number: 300,
  state: 'open',
  draft: false,
  base: {ref: 'main', sha: main},
  head: {ref: 'feature/feed-20261025', sha: head, repo: {full_name: 'Glimmar-code/Blinkng'}},
  mergeable: true,
  mergeable_state: 'clean',
  changed_files: 1,
  labels: [{name: 'auto-promote'}, {name: 'visual-verified'}],
  body: '- [x] Build\n- [x] Verify device\n<!-- BLINK_AGENT_VERIFIED_SHA: ' + head + ' -->\n<!-- BLINK_VISUAL_VERIFIED_SHA: ' + head + ' -->',
  ...more,
});
const check = (name, status = 'completed', conclusion = 'success', id = 1, number = 300) => ({
  name, status, conclusion, id,
  app: {slug: 'github-actions'},
  pull_requests: [{number, base: {sha: main}, head: {sha: head}}],
});

test('a verified, clean, opted-in feature change is eligible', () => {
  assert.equal(policy.preflight(ready(), ['app/src/Foo.kt']), null);
});
test('no opt-in, draft, fork, wrong base or unchecked task never promote', () => {
  assert.match(policy.preflight(ready({labels: []}), ['readme.md']), /opt-in/);
  assert.match(policy.preflight(ready({draft: true}), ['readme.md']), /ready/);
  assert.match(policy.preflight(ready({head: {...ready().head, repo: {full_name: 'attacker/repo'}}}), ['readme.md']), /internal/);
  assert.match(policy.preflight(ready({base: {ref: 'Testlab'}}), ['readme.md']), /main/);
  assert.match(policy.preflight(ready({body: '- [ ] Device testing'}), ['readme.md']), /Unfinished/);
  assert.match(policy.preflight(ready({body: 'Do not auto-merge until validated.'}), ['readme.md']), /prohibits/);
});
test('retired Testlab branches and main itself cannot be promoted as feature PRs', () => {
  for (const ref of ['Testlab', 'Testlab-old-work', 'main']) {
    assert.match(policy.preflight(ready({head: {...ready().head, ref}}), ['README.md']),
      /non-main/, ref);
  }
  assert.equal(policy.preflight(ready({head: {...ready().head, ref: 'fix/feed-ui'}}), ['README.md']), null);
});
test('backend, release and JNI-sensitive changes need staging attestation', () => {
  for (const filename of [
    'supabase/migrations/20261010_fix.sql',
    'supabase/functions/payments/index.ts',
    'app/proguard-rules.pro',
    '.github/workflows/signed-release.yml',
    '.github/workflows/auto-promote-testlab.yml',
    '.github/workflows/retire-testlab-branch.yml',
    '.github/scripts/install_release_smoke_apk.sh',
  ]) {
    assert.match(policy.preflight(ready(), [filename]), /Staging/, filename);
    assert.equal(policy.preflight(ready({labels: [{name: 'auto-promote'}, {name: 'staging-verified'}]}), [filename]), null);
  }
});
test('hardware and OEM-specific Android changes require independent device evidence', () => {
  for (const path of [
    'app/src/main/java/com/example/call/CallManager.kt',
    'app/src/main/java/com/example/TelecomService.kt',
    'app/src/main/java/com/example/ApkUpdater.kt',
    'app/src/main/java/com/example/notifications/PushReceiver.kt',
    'app/src/main/jniLibs/arm64-v8a/libwebrtc.so',
  ]) {
    assert.equal(policy.requiresDeviceVerification([path]), true, path);
    assert.match(policy.preflight(ready(), [path]), /physical-device/, path);
    const approved = ready({labels: [{name: 'auto-promote'}, {name: 'device-verified'}]});
    assert.equal(policy.preflight(approved, [path]), null, path);
  }
  assert.equal(policy.requiresDeviceVerification(['web/styles.css']), false);
  const flagged = ready({labels: [{name: 'auto-promote'}, {name: 'needs-device-verification'}]});
  assert.match(policy.preflight(flagged, ['web/styles.css']), /physical-device/);
});

test('incomplete file list or unmergeable PR fail closed', () => {
  assert.match(policy.preflight(ready({changed_files: 2}), ['app/src/Foo.kt']), /every/);
  assert.match(policy.preflight(ready({mergeable: false}), ['app/src/Foo.kt']), /mergeable/);
});
test('Android, web and backend jobs have explicit required gates', () => {
  const android = policy.requiredChecks(['app/src/main/java/Foo.kt']);
  for (const c of ['validate','guard','migration-safety','parity','runtime-smoke','release-smoke']) {
    assert.ok(android.includes(c), c);
  }
  assert.ok(!android.includes('windows-build'), 'Android-only changes skip unrelated Windows installer build');
  const shared = policy.requiredChecks(['shared/src/Foo.kt']);
  assert.ok(shared.includes('windows-build'), 'shared code requires Windows packaging');
  assert.ok(policy.requiredChecks(['desktopApp/src/Windows.kt']).includes('windows-build'));
  assert.ok(policy.requiredChecks(['scripts/ci_change_scope.py']).includes('windows-build'));
  const web = policy.requiredChecks(['web/app.js']);
  assert.ok(web.includes('web-parity'));
  assert.ok(web.includes('verify'));
  const signed = policy.requiredChecks(['.github/scripts/install_release_smoke_apk.sh']);
  assert.ok(signed.includes('release-smoke'));
  const backend = policy.requiredChecks(['supabase/migrations/foo.sql']);
  assert.ok(backend.includes('migration-safety'));
});
test('release integrity is mandatory on even documentation-only production PRs', () => {
  const checks = policy.requiredChecks(['README.md']);
  assert.ok(checks.includes('integrity'), 'integrity must always run');
  assert.ok(checks.includes('validate'), 'base Android/build policy validation must always run');
});
test('release and website workflows are dispatched for relevant merges', () => {
  assert.deepEqual(policy.releaseWorkflows(['app/src/main/Foo.kt']), ['signed-release.yml']);
  assert.deepEqual(policy.releaseWorkflows(['web/app.js']), ['deploy-web-preview.yml']);
  assert.deepEqual(policy.releaseWorkflows(['README.md']), []);
  assert.deepEqual(policy.releaseWorkflows(['.github/workflows/signed-release.yml']), ['signed-release.yml']);
  assert.deepEqual(policy.releaseWorkflows(['.github/scripts/install_release_smoke_apk.sh']), ['signed-release.yml']);
});
test('authorized AI opt-in on the latest feature-branch revision requires no GitHub owner comment', () => {
  assert.equal(policy.preflight(ready(), ['web/app.js']), null);
  assert.equal(policy.preflight(ready({labels: []}), ['web/app.js']), 'No auto-promote opt-in label');
  assert.equal(policy.preflight(ready({draft: true}), ['web/app.js']),
    'PR is not open and ready for review');
});

test('promotion requires a verified marker matching the latest head', () => {
  assert.equal(policy.preflight(ready(), ['web/app.js']), null);
  assert.match(policy.preflight(ready({body: '- [x] verified'}), ['web/app.js']), /missing or stale/);
  assert.match(policy.preflight(ready({head: {...ready().head, sha: 'c'.repeat(40)}}), ['web/app.js']), /missing or stale/);
});

test('synchronize always revokes stale agent promotion and verification attestations', async () => {
  const removed = [];
  const github = {rest: {issues: {removeLabel: async ({name, issue_number}) => {
    removed.push([name, issue_number]);
    if (name === 'staging-verified') throw Object.assign(new Error('absent'), {status: 404});
  }}}};
  const context = {eventName: 'pull_request_target', payload: {
    action: 'synchronize',
    pull_request: {number: 300, base: {ref: 'main'}, head: {
      repo: {full_name: 'Glimmar-code/Blinkng'},
    }},
  }};
  const msgs = [];
  const core = {info: msg => msgs.push(msg)};
  assert.equal(await policy.revokeStaleOptIn(github, context, core), true);
  assert.deepEqual(removed, [
    ['auto-promote', 300],
    ['staging-verified', 300],
    ['device-verified', 300],
    ['visual-verified', 300],
  ]);
  assert.equal(await policy.revokeStaleOptIn(github, {
    ...context, payload: {...context.payload, action: 'labeled'},
  }, core), false);
  assert.equal(await policy.revokeStaleOptIn(github, {
    ...context, payload: {...context.payload,
      pull_request: {...context.payload.pull_request, head: {
        repo: {full_name: 'unauthorized/fork'},
      }}},
  }, core), false);
  assert.equal(removed.length, 4);
});

test('synchronize fails closed if GitHub cannot revoke existing opt-in', async () => {
  const github = {rest: {issues: {removeLabel: async () => {
    throw Object.assign(new Error('permission denied'), {status: 403});
  }}}};
  await assert.rejects(
    policy.revokeStaleOptIn(github, {eventName: 'pull_request_target', payload: {
      action: 'synchronize',
      pull_request: {number: 300, base: {ref: 'main'}, head: {
        repo: {full_name: 'Glimmar-code/Blinkng'},
      }},
    }}, {info: () => {}}),
    /permission denied/
  );
});

test('end-to-end trusted controller merges fully verified feature PR without owner comments', async () => {
  const names = policy.requiredChecks(['README.md']);
  const labels = [{name: 'auto-promote'}];
  const pr = ready({labels, changed_files: 1});
  let merges = 0;
  let commentsFetched = 0;
  const rest = {
    issues: {
      getLabel: async () => ({data: {}}),
      createLabel: async () => { throw new Error('should not be creating labels'); },
      listComments: async () => { commentsFetched++; throw new Error('owner approval must not be requested'); },
    },
    pulls: {
      get: async () => ({data: pr}),
      listFiles: async () => [{filename: 'README.md'}],
      listReviews: async () => [],
      merge: async x => {
        assert.equal(x.sha, head);
        merges++;
        return {data: {merged: true}};
      },
    },
    repos: {
      getBranch: async () => ({data: {commit: {sha: main}}}),
      compareCommits: async () => ({data: {behind_by: 0, ahead_by: 1}}),
    },
    checks: {
      listForRef: async () => names.map((name, i) => check(name, 'completed', 'success', i+1)),
    },
    actions: {
      createWorkflowDispatch: async () => { throw new Error('Docs-only PR must not trigger build'); },
    },
  };
  const github = {rest, paginate: async (fn, args) => fn(args)};
  const logs = [], failed = [];
  await policy({github, context: {
    eventName: 'pull_request_target',
    payload: {action: 'labeled', pull_request: {number: 300}},
  }, core: {info: m => logs.push(m), setFailed: m => failed.push(m)}});
  assert.equal(merges, 1);
  assert.equal(commentsFetched, 0);
  assert.deepEqual(failed, []);
  assert.ok(logs.some(m => m.includes('Merged verified BLINK PR #300')));
});

test('CI fails closed on missing, pending, failed or stale checks', () => {
  const required = ['validate','guard'];
  assert.equal(policy.checkGate([check('validate', 'completed', 'success', 1), check('guard', 'completed', 'success', 2)],required,300,main,head), null);
  assert.match(policy.checkGate([check('validate')],required,300,main,head), /guard/);
  assert.match(policy.checkGate([check('validate','in_progress',null),check('guard')],required,300,main,head), /validate/);
  assert.match(policy.checkGate([check('validate','completed','failure'),check('guard')],required,300,main,head), /validate/);
  assert.match(policy.checkGate([check('validate','completed','success',1,301),check('guard')],required,300,main,head), /validate/);
});
test('latest check result wins and optional failures also block promotion', () => {
  const required = ['validate'];
  assert.match(policy.checkGate([check('validate','completed','success',1),check('validate','completed','failure',2)],required,300,main,head), /validate/);
  assert.equal(policy.checkGate([check('validate','completed','failure',1),check('validate','completed','success',2)],required,300,main,head), null);
  assert.match(policy.checkGate([check('validate'),check('web-parity','completed','failure',2)],required,300,main,head), /web-parity/);
});

test('isolated Testlab batches may use exact-head GitHub Actions dispatch checks', () => {
  const checks = [
    {...check('validate'), head_sha: head, pull_requests: []},
    {...check('guard'), head_sha: head, pull_requests: []},
  ];
  assert.match(policy.checkGate(checks, ['validate', 'guard'], 300, main, head), /validate/);
  assert.equal(policy.checkGate(checks, ['validate', 'guard'], 300, main, head,
    {allowDispatchForBatch: true}), null);
  assert.match(policy.checkGate([
    {...checks[0], head_sha: 'c'.repeat(40)}, checks[1]
  ], ['validate', 'guard'], 300, main, head,
    {allowDispatchForBatch: true}), /validate/);
  assert.match(policy.checkGate([
    {...checks[0], conclusion: 'failure'}, checks[1]
  ], ['validate', 'guard'], 300, main, head,
    {allowDispatchForBatch: true}), /validate/);
});


test('visual UI changes fail closed without exact-head screenshot evidence', () => {
  const path = 'app/src/main/java/com/example/ui/screens/ChatScreen.kt';
  assert.equal(policy.requiresVisualVerification([path]), true);
  assert.equal(policy.requiresVisualVerification(['desktopApp/src/main/kotlin/Chat.kt']), true);
  assert.equal(policy.requiresVisualVerification(['web/app.css']), true);
  assert.equal(policy.requiresVisualVerification(['README.md']), false);
  assert.equal(policy.preflight(ready(), [path]), null);
  assert.match(policy.preflight(ready({labels: [{name: 'auto-promote'}]}), [path]), /Screenshot reference/);
  const stale = ready({body: ready().body.replace('BLINK_VISUAL_VERIFIED_SHA: ' + head, 'BLINK_VISUAL_VERIFIED_SHA: ' + 'c'.repeat(40))});
  assert.match(policy.preflight(stale, [path]), /Visual approval/);
  assert.equal(policy.preflight(ready(), ['README.md']), null);
});
