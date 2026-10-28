import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const read = p => readFileSync(new URL('../../'+p, import.meta.url), 'utf8');
const onboarding = read('app/src/main/java/com/example/ui/screens/AccountOnboardingScreen.kt');
const birthday = read('shared/src/main/kotlin/com/blinkng/shared/discovery/BlinkBirthdayPolicy.kt');
const policy = read('shared/src/main/kotlin/com/blinkng/shared/BlinkOnboardingPolicy.kt');

test('onboarding MaterialTheme error cannot return', () => {
  assert.match(onboarding, /^import androidx\.compose\.material3\.MaterialTheme$/m);
  assert.match(onboarding, /MaterialTheme\.colorScheme\.error/);
});
test('SMS OTP is transient, not saved in Activity state', () => {
  assert.match(onboarding, /var phoneCode by remember\(profile\.id\) \{ mutableStateOf\(""\) \}/);
  assert.doesNotMatch(onboarding, /var phoneCode by rememberSaveable/);
  assert.match(onboarding, /var phoneNumber by remember\(profile\.id\) \{ mutableStateOf\(""\) \}/);
  assert.match(onboarding, /var phoneCodeSent by remember\(profile\.id\) \{ mutableStateOf\(false\) \}/);
  assert.match(onboarding, /var phoneVerified by remember\(profile\.id\) \{ mutableStateOf\(false\) \}/);
  assert.doesNotMatch(onboarding, /var (?:phoneNumber|phoneCodeSent|phoneVerified) by rememberSaveable/);
  assert.match(onboarding, /BlinkPhonePolicy\.otpIsValid\(phoneCode\)/);
});
test('both signup paths use the same strict real birthday policy', () => {
  assert.match(onboarding, /BlinkBirthdayPolicy\.isValid\(birthDate\)/);
  assert.match(onboarding, /Birthday \(required\)/);
  assert.doesNotMatch(onboarding, /Birthday \(optional\)/);
  assert.match(birthday, /ResolverStyle\.STRICT/);
  assert.match(birthday, /!date\.isAfter\(today\) && !date\.isBefore\(today\.minusYears\(120\)\)/);
  assert.doesNotMatch(policy, /validOptionalBirthday/);
});
