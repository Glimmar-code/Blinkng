# BLINK native iOS — Phase 2: existing Supabase account integration

The iOS Phase 2 branch builds **on top of iOS Phase 1** and targets the Phase 1 feature branch for review; no Testlab/main promotion until checks pass.

## What is implemented

- Native email/password login and email registration using the **same Supabase Auth project** as BLINK Web.
- Client-side public project URL and `sb_publishable_` key taken from the repository's existing `web/config.js` (public app keys, not administrator credentials). The URL and publishable key are embedded as build config.
- Supabase account validation via `GET /auth/v1/user`; empty/invalid or unverified local tokens do not grant access to authenticated screens.
- Refresh token rotation using `POST /auth/v1/token?grant_type=refresh_token`. Save rotated tokens in iOS Keychain with `WhenUnlockedThisDeviceOnly` protection.
- Local-device Supabase logout and Keychain clearing, with honest message if backend logout cannot be confirmed.
- Password recovery email request through existing Supabase Auth; reset takes place through the project's configured **website** URL. Native deep-link recovery is deliberately deferred.
- Signup correctly treats email-confirmation-required responses (no access token) as *not signed in*. Confirmed users can sign in afterward.
- Protocol-stubbed XCTest cases for token payloads, publishable-key headers, signup confirmation, refresh, invalid token and local sign-out.
- Screens are real native UI, but feed/reels/market/connect/messages are intentionally still disconnected; no fake user content is shipped.

## Verification still required before TestFlight or release

1. On a test iOS device/simulator, confirm the configured Supabase public project URL/key are correct and the app's actual email/password flows work.
2. Test an existing verified BLINK account and a **new disposable test account**. Verify confirmation email delivery (do **not** modify production Auth email settings for this feature).
3. Test token rotation after expiry, app restart, signed-out sessions, network loss, Keychain failure, account deletion and repeated sign-in/out.
4. Check Google/other social login parity and decide which Apple Sign in requirements apply before App Store submission. **Google OAuth is not included in Phase 2.**
5. Respect existing Auth and RLS rules. No service-role secret, Paystack secret, production migration, or auth policy changes should be introduced through this iOS branch.
6. Account creation should apply appropriate privacy, age and content-safety rules before a public iOS launch.
7. Configure App Store bundle ID, push notifications, Universal Links and Apple Developer signing only after enrollment.

## Build / test

The existing iOS quality gate runs `xcodegen generate`, Xcode Simulator build, and XCTest. It does not prove hosted end-to-end authentication or TestFlight readiness.

**Production untouched:** The website, Windows, Android and live Supabase project keep their current behavior.
