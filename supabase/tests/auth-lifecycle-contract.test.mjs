// Static regression for Android authentication Activity lifecycle contracts.
// No OAuth account, recovery email, or hosted backend is involved.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const android = (path) => readFileSync(new URL('../../app/src/main/java/com/example/' + path, import.meta.url), 'utf8');
const google = android('auth/GoogleAuthCallbackActivity.kt');
const recovery = android('auth/PasswordResetActivity.kt');
const recoveryParser = android('auth/PasswordRecoveryClient.kt');

test('Google OAuth callback does not strand recreated Activity on an empty screen', () => {
  assert.match(google, /if \(savedInstanceState == null\) \{[\s\S]*startNativeGoogleSignIn\(\)/);
  assert.match(google, /\} else \{[\s\S]*?failAndReturnToSignIn\([\s\S]*?Google sign-in was interrupted/);
  assert.match(google, /setContent \{/);
  assert.match(google, /BlinkTheme \{/);
  assert.match(google, /CircularProgressIndicator\(color = Color.White\)/);
  assert.match(google, /Connecting to Google/);
  assert.match(google, /AccountSessionStore\.setSignInRequired\(applicationContext, !hasDurableSession\)/);
  assert.match(google, /if \(isFinishing\) GoogleAuthLaunchGate\.end\(\)/);
});

test('second singleTask recovery deep link invalidates previous screen session', () => {
  const method = recovery.match(/override fun onNewIntent\(intent: Intent\) \{([\s\S]*?)\n    \}/)?.[1];
  assert.ok(method, 'handle subsequent password-reset links');
  assert.match(method, /super\.onNewIntent\(intent\)/);
  assert.match(method, /setIntent\(intent\)/);
  assert.match(method, /recreate\(\)/);
  assert.match(recovery, /PasswordRecoveryClient\.parseRecoveryLink\(intent\?\.data\)/);
});

test('recovery parser still requires recovery-type token, not arbitrary deep links', () => {
  assert.match(recoveryParser, /params\["type"\]\.equals\("recovery", ignoreCase = true\)/);
  assert.match(recoveryParser, /val accessToken = params\["access_token"\]/);
  assert.match(recoveryParser, /if \(accessToken\.isBlank\(\)\)/);
});
