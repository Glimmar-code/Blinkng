import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = path => readFileSync(new URL('../../'+path, import.meta.url), 'utf8');
const onboarding = source('app/src/main/java/com/example/ui/screens/AccountOnboardingScreen.kt');
const main = source('app/src/main/java/com/example/MainActivity.kt');
const service = source('app/src/main/java/com/example/data/supabase/SupabaseService.kt');
const contacts = source('app/src/main/java/com/example/ui/components/BlinkContactDiscoveryCard.kt');
const prompt = source('app/src/main/java/com/example/ui/components/BlinkContactSyncLoginPrompt.kt');
const client = source('app/src/main/java/com/example/data/repository/BlinkContactsDiscoveryClient.kt');

test('no SMS verification is rendered in signup or contacts', () => {
  for (const text of [onboarding,contacts]) {
    assert.doesNotMatch(text, /BlinkPhoneVerificationClient|Send SMS code|Six-digit SMS code|phoneCodeSent|phoneVerified/);
  }
  assert.match(onboarding, /Phone number \(optional\)/);
  assert.match(onboarding, /No SMS verification/);
});

test('self-declared profile number is owner scoped and private until owner changes visibility', () => {
  assert.match(main, /onSavePhone = \{ number, onResult ->/);
  assert.match(service, /suspend fun updateMyProfilePhone\(rawPhone: String\)/);
  assert.match(service, /newRequestBuilder\("\/rest\/v1\/profiles\?id=eq\.\$\{encodeValue\(uid\)\}", true\)/);
  assert.match(service, /put\("phone_visibility", "PRIVATE"\)/);
  assert.match(onboarding, /BlinkPhonePolicy\.normalize\(phoneNumber\)/);
});

test('login and signup contact invitation is optional and respects platform permissions', () => {
  assert.match(main, /BlinkContactSyncLoginPrompt\(/);
  assert.match(prompt, /Choose to sync contacts and receive possible-contact suggestions/);
  assert.match(prompt, /ActivityResultContracts\.RequestPermission\(\)/);
  assert.match(prompt, /Manifest\.permission\.READ_CONTACTS/);
  assert.match(prompt, /Button\([\s\S]*?Sync contacts and notify me/);
  assert.match(prompt, /Text\("Not now"\)/);
  assert.match(prompt, /14L \* 24L \* 60L \* 60L \* 1000L/);
});

test('contact prompt fails closed when backend missing and never silently grants consent', () => {
  assert.match(prompt, /val snapshot = runCatching \{ client\.get\(\) \}\.getOrNull\(\)/);
  assert.match(prompt, /return@LaunchedEffect/);
  assert.match(prompt, /client\.set\(existing\.copy\(syncEnabled = true, joinAlerts = true,/);
  assert.match(client, /Contacts permission was not granted/);
  assert.match(client, /action", "sync"/);
  assert.doesNotMatch(prompt, /set\([^)]*discoverable = true/);
});

test('unverified matches are gated by server policy and explicit mutual consent', () => {
  assert.match(contacts, /mutualMatchingReady && serviceReady/);
  assert.match(contacts, /A possible match appears only when both members explicitly opt in/);
  assert.match(prompt, /allowMutualDiscovery/);
  assert.match(prompt, /snapshot\.mutualMatchingReady/);
  assert.match(client, /match_policy/);
});
