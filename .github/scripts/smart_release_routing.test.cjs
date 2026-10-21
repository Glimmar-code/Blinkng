'use strict';
/**
 * BLINK release-routing contract.
 * Android executable changes require a signed APK; web, backend and docs changes
 * never claim to hot-update Kotlin/native code on already installed devices.
 */
const fs = require('node:fs');
const assert = require('node:assert/strict');
const test = require('node:test');
const signed = fs.readFileSync('.github/workflows/signed-release.yml', 'utf8');
const publish = fs.readFileSync('.github/workflows/publish-latest-apk.yml', 'utf8');
const requiredAndroid = [
  'app/**', 'shared/**', 'blinkLifeFeature/**', 'game3d/**', 'gradle/**',
  'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
  'gradlew', 'gradlew.bat', 'scripts/sync_android_launcher_assets.py',
  '.github/workflows/signed-release.yml',
  '.github/scripts/publish_verified_apk.py'
];
const excludedRemote = ['web/**', 'supabase/**', 'desktopApp/**', 'docs/**'];
function signedPaths() {
  const match = signed.match(/\n    paths:\n([\s\S]*?)\npermissions:/);
  assert.ok(match, 'Signed release must use push path filters');
  return [...match[1].matchAll(/^\s+- '([^']+)'/gm)].map(x => x[1]);
}
test('Android, shared and release-security edits trigger a signed release', () => {
  const paths = signedPaths();
  for (const path of requiredAndroid) assert.ok(paths.includes(path), 'Missing signed release trigger: ' + path);
});
test('Remote-only and documentation edits do not trigger APK builds', () => {
  const paths = signedPaths();
  for (const path of excludedRemote) assert.ok(!paths.includes(path), 'Unexpected APK trigger: ' + path);
});
test('Signed releases only originate from approved main history', () => {
  assert.match(signed, /git merge-base --is-ancestor "\$GITHUB_SHA" origin\/main/);
  assert.match(signed, /Verify signing key matches the last published production APK/);
  assert.match(signed, /Verify production package, version and APK signature/);
});
test('Publisher only publishes successful main signed-build results', () => {
  assert.match(publish, /workflow_run\.conclusion == 'success'/);
  assert.match(publish, /workflow_run\.head_branch == 'main'/);
  assert.match(publish, /publish_verified_apk\.py/);
});
