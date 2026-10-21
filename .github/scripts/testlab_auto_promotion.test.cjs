// Regression tests for the trusted Testlab -> main release gate.
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
  head: {ref: 'Testlab-feature-20261011', sha: head, repo: {full_name: 'Glimmar-code/Blinkng'}},
  mergeable: true,
  mergeable_state: 'clean',
  changed_files: 1,
  labels: [{name: 'auto-promote'}],
  body: '- [x] Build\n- [x] Verify device',
  ...more,
});
const check = (name, status = 'completed', conclusion = 'success', id = 1, number = 300) => ({
  name, status, conclusion, id,
  app: {slug: 'github-actions'},
  pull_requests: [{number, base: {sha: main}, head: {sha: head}}],
});

test('a verified, clean, opted-in Testlab change is eligible', () => {
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
test('backend, release and JNI-sensitive changes need staging attestation', () => {
  for (const filename of [
    'supabase/migrations/20261010_fix.sql',
    'supabase/functions/payments/index.ts',
    'app/proguard-rules.pro',
    '.github/workflows/signed-release.yml',
    '.github/workflows/auto-promote-testlab.yml',
  ]) {
    assert.match(policy.preflight(ready(), [filename]), /Staging/, filename);
    assert.equal(policy.preflight(ready({labels: [{name: 'auto-promote'}, {name: 'staging-verified'}]}), [filename]), null);
  }
});
test('routine assets and copy can promote with opt-in and full CI but without owner comment', () => {
  const routine = [
    'README.md',
    'docs/testlab-auto-promotion.md',
    'web/styles.css',
    'web/icons/home.png',
    'app/src/main/res/drawable-hdpi/blink.png',
  ];
  for (const filename of routine) {
    assert.equal(policy.requiresOwnerApproval([filename]), false, filename);
    assert.equal(policy.preflight(ready(), [filename]), null, filename);
  }
  assert.equal(policy.requiresOwnerApproval(['docs/help.md', 'web/styles.css']), false);
});
test('sensitive, ambiguous, mixed and executable changes always need owner authorization', () => {
  for (const filename of [
    'web/app.js',
    'web/index.html',
    'web/blink-icon.svg',
    'app/src/main/java/com/example/ui/screens/HomeScreen.kt',
    'app/src/main/AndroidManifest.xml',
    'supabase/functions/verify/index.ts',
    'supabase/migrations/20261021.sql',
    '.github/workflows/publish-latest-apk.yml',
    '.github/scripts/testlab_auto_promotion.cjs',
    '.github/scripts/testlab_auto_promotion.test.cjs',
  ]) {
    assert.equal(policy.requiresOwnerApproval([filename]), true, filename);
  }
  assert.equal(policy.requiresOwnerApproval(['docs/readme.md', 'web/app.js']), true);
  assert.equal(policy.requiresOwnerApproval([]), true);
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
  for (const c of ['validate','guard','migration-safety','parity','windows-build','runtime-smoke','release-smoke']) {
    assert.ok(android.includes(c), c);
  }
  const web = policy.requiredChecks(['web/app.js']);
  assert.ok(web.includes('web-parity'));
  assert.ok(web.includes('verify'));
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
});
test('explicit owner approval must match the current PR head SHA', () => {
  const owner = {login: 'Glimmar-code'};
  const fake = {login: 'untrusted-user'};
  const oldHead = 'c'.repeat(40);
  assert.match(policy.approvalGate([], head), /missing/);
  assert.match(policy.approvalGate([{id: 1, user: fake, body: 'BLINK APPROVE ' + head}], head), /missing/);
  assert.match(policy.approvalGate([{id: 1, user: owner, body: 'BLINK APPROVE ' + oldHead}], head), /missing/);
  assert.match(policy.approvalGate([{id: 1, user: owner, body: 'BLINK APPROVE ' + head + ' extra'}], head), /missing/);
  assert.equal(policy.approvalGate([{id: 1, user: owner, body: 'BLINK APPROVE ' + head}], head), null);
});

test('an owner can revoke an approval and reapprove the same exact SHA', () => {
  const owner = {login: 'Glimmar-code'};
  const approve = {id: 1, user: owner, body: 'BLINK APPROVE ' + head};
  const revoke = {id: 2, user: owner, body: 'BLINK REVOKE ' + head};
  assert.match(policy.approvalGate([revoke, approve], head), /revoked/);
  assert.equal(policy.approvalGate([revoke, approve, {id: 3, user: owner, body: 'BLINK APPROVE ' + head}], head), null);
  assert.match(policy.approvalGate([approve], 'not-a-commit-sha'), /Invalid/);
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
