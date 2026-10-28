// Source-contract checks for sensitive password recovery UI.
// These checks are static; Robolectric/real-device auth tests remain required.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const reset = readFileSync(new URL('../../app/src/main/java/com/example/ui/screens/ResetPasswordScreen.kt', import.meta.url), 'utf8');
const recovery = readFileSync(new URL('../../app/src/main/java/com/example/auth/PasswordRecoveryClient.kt', import.meta.url), 'utf8');
const manifest = readFileSync(new URL('../../app/src/main/AndroidManifest.xml', import.meta.url), 'utf8');

test('password reset uses only transient in-memory credential fields', () => {
  assert.doesNotMatch(reset, /rememberPersistentTextState/);
  assert.match(reset, /var password by remember \{ mutableStateOf\(""\) \}/);
  assert.match(reset, /var confirmation by remember \{ mutableStateOf\(""\) \}/);
  assert.match(reset, /PasswordVisualTransformation\(\)/);
});

test('old encrypted recovery draft values are actively cleared without reading them', () => {
  assert.match(reset, /PersistentTextDraftStore\.clearValue\(context, "com\/example\/ui\/screens\/ResetPasswordScreen\.kt:password:1"\)/);
  assert.match(reset, /PersistentTextDraftStore\.clearValue\(context, "com\/example\/ui\/screens\/ResetPasswordScreen\.kt:confirmation:2"\)/);
  assert.doesNotMatch(reset, /readDraft|readValue|readStorageKey/);
});

test('password recovery emails target a dedicated exported recovery activity', () => {
  assert.match(recovery, /RESET_REDIRECT_URL = "blink:\/\/auth\/reset-password"/);
  assert.match(manifest, /android:name="\.auth\.PasswordResetActivity"[\s\S]*?<data android:scheme="blink" android:host="auth" android:path="\/reset-password"/);
  const dedicated = manifest.split('<activity').find(block =>
    block.includes('android:name=".auth.PasswordResetActivity"'));
  const main = manifest.split('<activity').find(block =>
    block.includes('android:name=".MainActivity"'));
  assert.ok(dedicated?.includes('android:host="reset-password"'));
  assert.ok(!main?.includes('android:host="reset-password"'),
    'legacy callback must not be intercepted by MainActivity');

});
