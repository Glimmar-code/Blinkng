import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = path => readFileSync(path, 'utf8');
const gradle = read('app/build.gradle.kts');
const manifest = read('app/src/main/AndroidManifest.xml');
const playManifest = read('app/src/playStoreRelease/AndroidManifest.xml');
const extraction = read('app/src/main/res/xml/data_extraction_rules.xml');
const account = read('app/src/main/java/com/example/ProfessionalCenterActivity.kt');
const repository = read('app/src/main/java/com/example/data/repository/ProfessionalRepository.kt');
const deletionPage = read('web/delete-account/index.html');
const privacy = read('web/privacy/index.html');
const store = read('app/src/main/java/com/example/BlinkStoreActivity.kt');

assert.match(gradle, /targetSdk\s*=\s*36/, 'release target SDK must meet the current Play target');
assert.match(gradle, /compileSdk\s*\{\s*version\s*=\s*release\(37\)/, 'compile SDK must remain current');
assert.match(manifest, /android:allowBackup="false"/, 'account-bearing app must not enable Android backup');
assert.match(extraction, /<cloud-backup>[\s\S]*<exclude domain="root" path="\." \/>[\s\S]*<\/cloud-backup>/, 'cloud backup exclusions must remain explicit');
assert.match(playManifest, /android\.permission\.REQUEST_INSTALL_PACKAGES/, 'Play overlay must explicitly remove restricted installer permission');
assert.match(playManifest, /tools:node="remove"/, 'Play overlay must remove restricted installer permission');
assert.doesNotMatch(store, /cash_checkout_enabled[\s\S]{0,500}purchase/i, 'Play Android build must not re-enable digital Paystack purchase');
assert.match(account, /Delete account/, 'Android settings must expose account deletion');
assert.match(account, /permanently deletes your BLINK account/i, 'Android deletion dialog must explain permanence');
assert.match(account, /security, fraud-prevention, transaction, or legal records/i, 'Android deletion dialog must disclose limited retention');
assert.match(repository, /delete_my_account/, 'Android deletion must call the authenticated server workflow');
assert.match(deletionPage, /Settings and privacy/, 'Public deletion resource must document Android path');
assert.match(deletionPage, /Settings.*Your data|account settings/i, 'Public deletion resource must document web path');
assert.match(privacy, /delete-account\//, 'Privacy policy must link to the public deletion resource');
console.log('PASS: Play target, backup, installer, digital-goods, deletion, and public-policy contracts are guarded.');
