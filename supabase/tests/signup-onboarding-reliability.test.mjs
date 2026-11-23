// Wiring contracts complement the executable shared-policy and mocked-HTTP JVM tests.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const source = p => readFileSync(new URL('../../' + p, import.meta.url), 'utf8');
const forms = source('app/src/main/java/com/example/ui/screens/BlinkAuthReferenceForms.kt')
  .split('fun BlinkReferenceCreateAccountScreen(')[1];
const onboarding = source('app/src/main/java/com/example/ui/screens/AccountOnboardingScreen.kt');
const vm = source('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt');
const service = source('app/src/main/java/com/example/data/supabase/SupabaseService.kt');
const desktopState = source('desktopApp/src/main/kotlin/com/blinkng/desktop/DesktopAppState.kt');
const desktopClient = source('desktopApp/src/main/kotlin/com/blinkng/desktop/data/DesktopSupabaseClient.kt');
const desktopUi = source('desktopApp/src/main/kotlin/com/blinkng/desktop/ui/OnboardingScreen.kt');

test('signup permits deferring university and locks both submit paths until completion', () => {
  assert.doesNotMatch(forms, /university\.isBlank\(\)\s*->/);
  assert.match(forms, /if \(submitting \|\| googleLoading\) return/);
  assert.match(forms, /submitting = true[\s\S]*onCreateAccount\([\s\S]*submitting = false/);
  assert.equal((forms.match(/submitAccount\(\)/g) || []).length, 3); // declaration + both buttons
  assert.match(forms, /working = submitting/);
  assert.match(vm, /signupInFlight\.compareAndSet\(false, true\)/);
  assert.match(vm, /finally \{\s*signupInFlight\.set\(false\)/);
});

test('Android discards cancelled username checks and fails closed on service errors', () => {
  assert.match(onboarding, /suspendCancellableCoroutine<Pair<Boolean, String\?>>/);
  assert.match(onboarding, /if \(pending\.isActive\) pending\.resume/);
  const check = vm.split('fun checkOnboardingUsername(')[1].split('fun saveOnboardingUsername(')[0];
  assert.match(check, /isOnboardingUsernameAvailable\(clean\)/);
  assert.match(check, /onResult\(false, "Unable to check username/);
  assert.doesNotMatch(check, /existing == null|getOrNull/);
  assert.match(service, /response\.isSuccessful && raw in setOf\("true", "false"\)/);
});

test('Android saves all basics before step progress and keeps the saved phone in memory', () => {
  const basics = vm.split('fun saveOnboardingBasics(')[1].split('fun saveOnboardingInterests(')[0];
  const birthday = basics.indexOf('supabaseService.updatePrivateBirthDate(');
  const phone = basics.indexOf('supabaseService.updateMyProfilePhone(');
  const referral = basics.indexOf('claimGoogleOnboardingReferrer(');
  const progress = basics.indexOf('supabaseService.updateProfile(updated)');
  assert.ok(birthday >= 0 && birthday < phone && phone < referral && referral < progress);
  assert.match(basics, /phone = ContactField\(number, false\)/);
  assert.match(basics, /phoneVisibility = com\.blinkng\.shared\.ProfileVisibilityScope\.PRIVATE/);
  assert.match(basics, /if \(!phoneSaved\)[\s\S]*?return@launch/);
  assert.match(basics, /if \(!referralSaved\)[\s\S]*?return@launch/);
  assert.match(basics, /saveLocalProfile\(updated\)/);
  const laterSteps = vm.split('fun saveOnboardingInterests(')[1].split('fun completeProfileOnboarding(')[0];
  assert.doesNotMatch(laterSteps, /supabaseService\.updateProfile\(/);
  assert.match(laterSteps, /updateOnboardingProgress\(updated\.onboardingStep, false, cleanInterests\)/);
  assert.match(laterSteps, /updateOnboardingProgress\(4, true\)/);
  const progressPatch = service.split('suspend fun updateOnboardingProgress(')[1].split('suspend fun isOnboardingUsernameAvailable(')[0];
  assert.doesNotMatch(progressPatch, /\.put\("(?:phone|phone_visibility|birth_date|full_name)"/);
});

test('a failed avatar upload can be skipped without uploading again', () => {
  assert.match(onboarding, /onClick = \{ pendingAvatarUri = null; avatarUrl = ""; error = null \}/);
  assert.match(onboarding, /Remove picture and skip/);
});

test('both clients restore owner-only birthday data instead of substituting blanks on outages', () => {
  assert.match(onboarding, /birthDate = .*fetchMyPrivateBirthDate\(\)/);
  assert.match(onboarding, /birthdayValid &&\s*privateBirthdayLoaded/);
  assert.match(onboarding, /Retry loading birthday/);
  assert.match(service, /profile_private_details\?user_id=eq\.\$\{encodeValue\(uid\)\}&select=birth_date/);
  assert.match(desktopClient, /if \(userId == ownId\) parsed\.copy\(birthDate = fetchMyPrivateBirthDate\(\)\)/);
  assert.match(desktopClient, /profile_private_details\?user_id=eq\.\$\{encode\(userId\)\}&select=birth_date/);
});

test('Windows uses optional private phone saving, no SMS, and shared strict basics validation', () => {
  assert.doesNotMatch(desktopUi, /Send SMS code|phoneVerified|phoneCodeSent|requestOnboardingPhoneCode/);
  assert.match(desktopUi, /Phone number \(optional\)/);
  assert.match(desktopUi, /Birthday \(required\)/);
  assert.match(desktopUi, /BlinkOnboardingPolicy\.basicsValidationMessage/);
  const basics = desktopState.split('suspend fun saveOnboardingBasics(')[1].split('suspend fun saveOnboardingInterests(')[0];
  assert.match(basics, /basicsValidationMessage/);
  assert.ok(basics.indexOf('client.savePrivateBirthDate') < basics.indexOf('client.saveMyProfilePhone'));
  assert.ok(basics.indexOf('client.saveMyProfilePhone') < basics.indexOf('client.saveOnboardingProfile'));
  assert.match(desktopClient, /phone = row\.optNullableString\("phone"\)/);
  assert.match(desktopClient, /JSONObject\(\)\.put\("phone", number\)\.put\("phone_visibility", "PRIVATE"\)/);
});
