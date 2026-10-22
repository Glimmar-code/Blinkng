# iOS Auth parity — Phase 2

- Existing account provider: same Supabase project and user IDs as Android/Windows/web.
- Implemented: email/password sign-in, email sign-up, existing-site recovery email, secure session restoration/refresh and device sign-out.
- Security: public Supabase publishable key only; user JWT for privileged session identity, iOS Keychain for refresh/access tokens, no service-role key.
- Unsupported for now: native Google OAuth, phone verification, Apple Sign in, password recovery deep links, profile editing, delete-account flow, backend feed/reels/market/conversations/calls and verification subscriptions.
- iOS UI discloses that screens await later backend integration, rather than rendering mock user content.
- Android and Windows features are unchanged; the new iOS app must catch up before it is publicly shipped.
- All automated phase-2 auth tests use stubs. Actual live Supabase test accounts and email confirmations need manual end-to-end verification.

No changes to production Supabase configurations, database or billing.
