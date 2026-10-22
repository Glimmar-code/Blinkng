# BLINK for iPhone and iPad — Phase 1

This is a **native SwiftUI iOS foundation**, staged separately from the production Android/Windows app. It is not a shipped iOS client and does not currently claim production authentication, messaging, or live feed support.

## Current features

- XcodeGen project configuration for a native BLINK iOS app (iOS 17+).
- Five native tabs in existing BLINK order: Home → Reels → Market → Connect → Message.
- Monochrome light/dark UI, accessible tab labels and live link to [blink.com.ng](https://www.blink.com.ng).
- No invented user content and no production API calls.
- Public Supabase configuration validation with safeguards against embedding service-role keys.
- Unit tests and an unsigned iOS Simulator GitHub Actions build.
- Android, Windows, website APK and Supabase production unchanged.

## Build and run (Mac/Xcode)

1. Install a compatible Xcode and [XcodeGen](https://github.com/yonaskolb/XcodeGen) (e.g. `brew install xcodegen`).
2. From the repository: `cd ios && xcodegen generate`.
3. Open `BLINKiOS.xcodeproj` in Xcode.
4. Select an iPhone Simulator, then Run.
5. For CI, run `xcodebuild -project BLINKiOS.xcodeproj -scheme BLINKiOS -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`.
6. Tests are under `BLINKiOS/Tests` and are run in GitHub Actions on a macOS simulator.

This source uses an **iOS app bundle ID proposal** (`ng.com.blink.ios`). Confirm it is available and correct before Apple Developer enrollment or TestFlight. Do not create a new production backend or change Android's existing `com.aistudio.blink.appvtwo` package.

## Phase 2 — existing Supabase accounts

Connect BLINK iOS to the **existing** BLINK Supabase Auth/database, respecting session rotation, Keychain credentials, account deletion and RLS. Backend configuration may contain only a public anon/publishable key and HTTPS project URL; never bundle the service-role or Paystack/Firebase server-side secrets. Real feed, marketplace and messaging must be server-backed.

## Later phases

- Native sign-in and secure account/session state.
- Shared backend account data, feed, reels and media permissions.
- Market, Connect, messaging, notifications and calls.
- Apple-appropriate billing, purchase disclosure and privacy permissions.
- TestFlight, signing and App Store submission after required Apple Developer account setup.

**Safety:** this PR targets `Testlab`, and production promotion must be explicit and tested. The iOS target must not be claimed feature-complete before the later integrations work.
