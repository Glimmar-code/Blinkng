# BLINK Google Play compliance audit

**Audit date:** 14 November 2026  
**Repository:** `Glimmar-code/Blinkng`  
**Branch:** `testlab` → pushed to `origin/testlab/website-refresh-20261114`  
**Scope:** Android/Kotlin/Jetpack Compose, Supabase migrations, public website, privacy/account controls, and release-readiness evidence.

This audit uses the statuses **Pass**, **Fixed**, and **Needs me**. “Needs me” means the repository cannot safely decide the item without product facts, Play Console access, credentials, a truthful audience declaration, or a live deployment verification.

## 1. Developer Program, deceptive behavior, and truthful functionality — **Pass**

The code and website audit did not find a fabricated claim that can be safely corrected without product-owner confirmation. The website keeps the Google Play control as “Coming soon,” and the public website language is being kept separate from an approved Play listing. The store and privacy copy avoid claiming that an APK install is a Google Play release. Final listing copy still needs a human truthfulness review before submission. [1] [10]

## 2. Chance features, contests, prizes, and gambling wording — **Needs me**

The repository contains contest and game/reward features, but the audit cannot infer the intended prize economics, whether any user-funded entry exists, or the final real-world prize policy. Before Play submission, confirm that chance-based features do not award real-world cash or physical prizes, disclose any remaining odds, and remove gambling-like wording. Do not enable a feature that accepts money or in-app items for an opportunity to win real-world value unless it has been reviewed against the applicable Play rules. [1]

## 3. Digital goods payment method and purchase verification — **Fixed / Needs me**

The Android store previously exposed a Paystack coin-pack flow when the backend flag was enabled outside Google Play. That was not a safe digital-goods path. `BlinkStoreActivity.kt` now hard-disables Android coin-pack checkout and explains that Google Play Billing is required. Migration `20261114150000_disable_paystack_digital_goods.sql` rejects the legacy digital coin and gift RPC paths without changing the separate physical-marketplace flag. Physical marketplace checkout was not disabled by this change.

Full Google Play Billing integration remains **Needs me**. It requires confirmed Play product IDs, Billing Library implementation, purchase acknowledgement/restore behavior, server-side purchase-token verification, entitlement idempotency, refund/revocation handling, and Play Console configuration. I did not invent those values or claim that a live migration was applied. [4]

## 4. User-generated content safety — **Pass in code; Needs me for operational evidence**

The existing implementation includes terms/community-rule acceptance, report and block flows, moderation/takedown records, authenticated server functions, and RLS protections. The remaining owner action is to verify the operational moderation queue, response targets, reviewer access, and the default handling of incidental sexual content and minors in a live closed test. [2]

## 5. Account deletion and associated data — **Fixed / Needs me for live verification**

In-app deletion already calls the server-side `delete_my_account` workflow. I added `web/delete-account/index.html`, linked it from the privacy policy, and added it to the sitemap. The page identifies BLINK, gives Android and web instructions, explains deletion versus limited legal/security retention, and avoids inventing an email address or credentials.

Before submission, verify that the production web route serves this file and that the deployed deletion workflow actually deletes or anonymizes all associated data, including storage objects and third-party records where applicable. [3]

## 6. Privacy policy and Data Safety accuracy — **Needs me**

The policy is substantially aligned with verified dependencies: Supabase, Firebase Messaging/Analytics/Crashlytics, Google Sign-In/platform services, AdMob, UMP, payment providers, device data, user content, communications, location-related features, and account deletion are described. The final Data Safety form still requires owner confirmation of actual production collection, sharing, retention, encrypted transport, deletion behavior, and every enabled SDK. The policy effective date should be updated when the new deletion page and any material payment changes are deployed. [5]

## 7. Target audience, age screening, and minors — **Needs me**

The code audit did not establish a final Play target-audience declaration or a neutral, product-approved age-screening policy. BLINK has social, messaging, UGC, calls, marketplace, and location-related features, so the audience choice materially changes Families obligations. Decide whether children are excluded, whether the app is mixed-audience, and what neutral age gate and minor safeguards apply. Do not submit a guessed declaration. [6]

## 8. Permissions and privacy-preserving media selection — **Needs me**

The manifest declares contacts, coarse/fine location, camera, microphone, notifications, call/foreground-service, installer, and media permissions. Camera/microphone/call/location/contacts usages are present in code. The app also supports Android Photo Picker, but `BlinkDevicePhotoLibrary.kt` still enumerates a recent `MediaStore` photo grid after media permission, so removing media permissions blindly could break an advertised feature. A picker-only refactor and UX decision are needed before broad media permissions can be removed. Review restricted photo/video permissions and provide declaration evidence if they remain. [9]

## 9. Target API and compile API — **Pass**

The build configuration reports `compileSdk = 37` and `targetSdk = 36`, satisfying the August 31, 2026 target requirement for new apps and updates, subject to building the intended release variant. [7]

## 10. AAB, Play App Signing, unique ID, versioning, 64-bit, R8, and 16 KB pages — **Needs me**

The repository has R8/resource shrinking enabled for release, a unique application ID, release-controlled version fields, and the WebRTC Android native dependency. A release AAB, Play App Signing, final versionCode, 64-bit contents, and 16 KB page-size compatibility must be verified from the actual signed bundle with the release secrets and Play Console. Native dependency compatibility must be checked with bundletool/Play tooling; source configuration alone is not proof. [8]

## 11. Backend security, HTTPS, secrets, RLS, and flagged tables — **Pass in repository; Needs me for live drift**

The audit found HTTPS Supabase access, client-side exclusion of service-role credentials, authenticated RPC usage, and RLS/service-role restrictions on `blink_store_settings`, `blink_contest_topic_follows`, and `blink_contest_history_participants`. Live Supabase migration history and grants must still be checked in the intended Testlab project. Do not run production-data tests against shared tables.

## 12. Startup reliability, dead controls, offline/rotation/network behavior, and ANRs — **Needs me**

Static review found existing loading/error handling, safe hosted-checkout URL validation, durable call termination retry, and paused-store behavior. A complete launch/crash, slow-network, rotation, offline, ANR, and dead-button validation requires emulator/device execution and representative accounts. No test account or device matrix was provided, so this item is not claimed as complete.

## 13. Reviewer access and demo credentials — **Needs me**

No reusable Play Console reviewer account, password, location-independent test path, or final English reviewer instructions were supplied. Create a dedicated non-production reviewer account and document how to reach restricted UGC, moderation, calls, marketplace, and account-deletion flows. Do not place credentials in the repository. [10]

## 14. Store listing, screenshots, icon, feature graphic, copyright, and licenses — **Needs me**

The repository contains an Android icon/build configuration and public website assets, but Play screenshots, feature graphic, final listing text, copyright ownership, and complete third-party license inventory require owner-provided source material and a final signed build. Do not publish screenshots or claims that show disabled/unapproved digital checkout.

## 15. Website and public policy consistency — **Fixed / Needs me for deployment**

The website already has privacy, safety, marketplace, download, sitemap, redirects, and a deliberately disabled “Coming soon” Play control. I added the public deletion resource and linked it from privacy and the sitemap. Deploy and verify `/delete-account/`, `/privacy/`, and the sitemap on the public domain, then keep website, app, and final Play listing copy synchronized. [10]

## Changes made in this branch

The Android store no longer uses the backend `cash_checkout_enabled` value to expose Paystack coin packs. Digital coin packs remain viewable as catalog references, but the purchase action is disabled with a Google Play Billing prerequisite message. The Supabase migration fail-closes the legacy digital coin/gift Paystack RPCs while leaving physical marketplace checkout under its separate control. The public deletion page is at `/delete-account/`, is linked from the privacy policy, and is included in `sitemap.xml`.

## Top five remaining risks

1. Google Play Billing is not implemented yet, so digital coin purchases must remain disabled.
2. The final target-audience/minor-safety decision is not known.
3. Broad media permissions remain because the app still has a permission-gated MediaStore gallery; this needs a picker-only product decision.
4. Signed AAB, Play App Signing, 16 KB page-size, 64-bit, and live Supabase drift checks remain unverified.
5. Reviewer credentials, operational moderation evidence, and final listing assets are not available in the repository.

## Closed-testing recommendation

**No-go for a public or broad closed test that advertises coin purchases, gifts, or other Paystack-funded digital entitlements.** A narrow internal/closed test is reasonable only with the store paused or digital checkout disabled, a clearly identified reviewer account, and explicit test coverage for account deletion, UGC reporting/blocking, moderation, permissions, and offline/error paths. Do not enable the Paystack digital flags until the Google Play Billing implementation and server verification have been completed and reviewed.

## References

[1]: https://support.google.com/googleplay/android-developer/answer/18265346?hl=en "Google Play Developer Program Policy"
[2]: https://support.google.com/googleplay/android-developer/answer/9876937?hl=en "User Generated Content policy"
[3]: https://support.google.com/googleplay/android-developer/answer/10144311?hl=en "Account deletion requirements"
[4]: https://developer.android.com/google/play/billing "Google Play Billing"
[5]: https://support.google.com/googleplay/android-developer/answer/10787469?hl=en "Data Safety section"
[6]: https://support.google.com/googleplay/android-developer/answer/9893335?hl=en "Families Policy"
[7]: https://support.google.com/googleplay/android-developer/answer/11926878?hl=en "Target API level requirements"
[8]: https://developer.android.com/guide/practices/page-sizes "Support 16 KB page sizes"
[9]: https://support.google.com/googleplay/android-developer/answer/14115180?hl=en "Photo and video permissions"
[10]: https://support.google.com/googleplay/android-developer/answer/15748846?hl=en "App access for Play review"
