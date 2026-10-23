# BLINK — Google Play launch preparation (Testlab)

**As of 19 October 2026:** no Google Play Console account exists. Keep BLINK website/APK distribution unchanged. This change only prepares source, store listing drafts and a read-only audit; it does not upload or publish an app.

## Build already available
- Android package: `com.aistudio.blink.appvtwo` (do not casually rename).
- Android `targetSdk = 36` on inspected Testlab source (confirm again at upload).
- Signed release workflow produces both an APK (for the website) and a signed AAB (for Google Play); signing/Firebase secrets must remain in GitHub secrets.
- Google Play Update support in Android must be device-tested after store installation.
- Never upload a debug AAB to Google Play production.

## Automated source preflight
Run `python3 scripts/play_store_preflight.py --output build/reports/play/preflight.json`. CI also runs it for changes to Play-related files. A passing source preflight **does not** mean the app is compliant or accepted by Google.

## Critical publishing blockers
1. **Payments:** BLINK currently uses Paystack for website payments. Digital features offered inside a Play-distributed app (Blue/Gold verification, coins, boosts and subscriptions) generally need Google Play Billing unless enrolled in an applicable regional exception. No Play Billing purchase flow is verified. Do not try to route Play users to Paystack for in-app digital purchases as a workaround.
2. **Subscriptions:** show exact price, duration, auto-renewal, cancellation and billing method clearly. No obscured opt-out, preselected deceptive renewal or undisclosed recurring charge.
3. **Deletion:** test the Android in-app deletion feature and the public `https://www.blink.com.ng/delete-account/` email-request page with a real test account, including response and deletion of data; website source alone is not proof it is live. Verify the published Privacy Policy accurately describes data practices.
4. **User-generated content:** audit terms acceptance, in-app reporting/blocking, moderation response, abuse handling, prohibited content and age-appropriate protection for posts, reels, DMs and Market.
5. **Privacy and permissions:** prepare truthful Play Data safety answers for Supabase, Firebase, analytics, ads, camera, microphone, messaging, notifications and advertising identifiers. Verify permission prompts and disclosure before sensitive access.
6. **Store listing:** current text in `play-store/listing/en-NG/` is a draft. Take genuine device screenshots, confirm claims against released features, prepare the high-res app icon and feature graphic, select correct category, content rating, support contact and pricing disclosures. Never fabricate ratings or screenshots.
7. **Testing:** test account creation/sign-in, app account deletion, feed, messaging, calls, ads/consent, purchases, notifications, offline/resume, crashes and dynamic game delivery on devices. Check Android/Windows/web shared backend compatibility.
8. **Play Console:** an eligible adult account owner must register and complete Google's identity verification. Set up Play App Signing, create the app with the existing package, upload AAB to internal testing, and complete policy declarations. New personal accounts may need 12 continuously opted-in closed testers for 14 days, then production-access approval.

## Later: optional automation, never before account setup
When the owner has Play Console and authorizes publishing:
- Configure a dedicated Google Play service account with least privilege and store credentials **only in GitHub Actions secrets**.
- Add a separate manual-approval release workflow for the Google Play internal track first; gate it on successful Android tests, AAB verification and Play compliance review.
- Keep website APK publication independent; do not replace the working Paystack website flow.
- Move to staged production rollout only after tests, account-policy checks and owner approval. Preserve the same Supabase backend.

## Official policy references
- Payments: https://support.google.com/googleplay/android-developer/answer/9858738
- Subscriptions: https://support.google.com/googleplay/android-developer/answer/9900533
- Account deletion: https://support.google.com/googleplay/android-developer/answer/13327111
- User-generated content: https://support.google.com/googleplay/android-developer/answer/9876937
- New personal account testing: https://support.google.com/googleplay/android-developer/answer/14151465
