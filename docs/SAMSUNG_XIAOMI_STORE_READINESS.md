# BLINK — Samsung Galaxy Store + Xiaomi GetApps publication readiness

**Status:** Preparation only. No store account has been registered, no APK has been uploaded to either store, and BLINK has **not** been submitted, approved, or published there.

**Branch:** Testlab-store-publishing-20261015. Work must pass Testlab checks and physical-device validation before any production release or merge to `main`.

## Current application identity (must not change)

- Android package/applicationId: `com.aistudio.blink.appvtwo`.
- Production Android App ID (AdMob): `ca-app-pub-9152580730716304~4430294008`.
- APK and AAB are produced by `.github/workflows/signed-release.yml` from approved `main` commits. Preserve the existing release signing certificate and monotonically increasing versionCode.
- Website/GitHub APK distribution should remain available.
- **Observed at preparation time (2026-10-15):** latest public GitHub release tag `v1.0.298678866` contained `Blink-latest.apk`, **345,108,276 bytes**. This is an observed artifact, **not** a verified slimmer store submission build.
- BLINK LIFE native 3D/on-demand delivery remains under development/Testlab validation. Do not advertise it as generally playable until a tested release includes it.

## Publisher prerequisites — user action needed

1. **Samsung Galaxy Store:** establish a Samsung account, register for Seller Portal, and obtain **commercial seller status even for free apps**. Samsung lists no sign-up or annual publishing fee; merchant/payment rules may apply to paid content.
2. **Xiaomi GetApps:** register with Mi Developer / GetApps; review territory/account eligibility, complete application ownership verification and the required seller/developer agreement. Xiaomi's developer agreement requires someone of legal age, or an authorized eligible organization representative.
3. Use accurate legal/business identity, eligible adult account holder where required, and private account access. Never send passwords, government IDs, signing keys or financial documents through GitHub issues/PRs.
4. Provide the official publisher/business name, public support email, contact website, verified privacy-policy URL, target regions, and desired store category. Confirm the actual app users and audience accurately.
5. Confirm availability to publish in the desired countries, including Nigeria, using the respective portals.

## Submission package (to prepare from a tested signed build)

- Signed APK with **unchanged package name and signing identity**; ensure app-specific store format and size requirements are met before upload.
- Version name/code, category, app name, truthful short/full descriptions, high-quality screenshots, icon/feature artwork as required by each portal.
- Public privacy policy, data processing disclosures, permissions explanation, age rating, ads/in-app purchase declarations (as applicable), moderation/reporting information and accurate support contact.
- Test reviewer credentials for features behind login, without disclosing personal accounts.
- Copyright/branding rights for all text, images, sounds and 3D/game files.

## Testlab acceptance gates (do not skip)

- Android compilation, automated tests, lint, release-smoke checks and backend/security checks are green.
- Fresh install and update over prior website APK work without data loss or signature mismatch.
- Real Android devices confirm sign-in, feed, reels, messaging, marketplace, profile, notifications, content moderation/reporting, permissions, account deletion and offline/network errors.
- AdMob debug/test ads load successfully or fail gracefully; **live** AdMob approval/no-fill cannot be confirmed by automated builds. Rewarded ads never credit coins without validated completion and backend verification.
- Measure final download size. Verify BLINK is usable without launching BLINK LIFE; confirm optional game content is only downloaded after an explicit tap, with retry/resume and clear storage behavior, **before describing this feature as available**.
- Check store policy requirements and that screenshots/listing copy match the exact tested build.
- Do not create a production release just to satisfy store prep until the product owner approves it.

## Publication sequence

1. Testlab validation and optimized signed release candidate.
2. Eligible publisher completes Samsung commercial seller approval and Xiaomi developer/ownership verification.
3. Create BLINK listings, upload the tested signed artifact, and complete content/privacy questionnaires.
4. Submit each for review; **publication is only complete after each store approves and lists it**.
5. After BLINK appears publicly in a supported store, link the store listing to the existing BLINK AdMob app using the **identical package name** and follow AdMob app-readiness / app-ads.txt review instructions. Listing does not guarantee ad fill.
6. Maintain the GitHub Pages/website APK option; coordinate versionCode and signing across distribution channels.

## Official reference links (publisher must check up-to-date guidance)

- Samsung Seller Portal start: https://developer.samsung.com/galaxy-store/prepare.html
- Samsung app submission: https://developer.samsung.com/galaxy-store/launch.html
- Samsung fees: https://developer.samsung.com/galaxy-store/faq.html
- Xiaomi GetApps app submission: https://orig-global.developer.mi.com/document?doc=appManagement.createAndUpdate
- Xiaomi terms: https://global.developer.mi.com/document?doc=cookiePolicyTitle.releaseAgreement
- AdMob supported third-party Android stores: https://support.google.com/admob/answer/10037806

**This file is a readiness plan, not evidence of approval, release QA, store registration, or publication.**
