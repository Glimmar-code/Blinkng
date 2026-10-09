# BLINK

BLINK is a social and campus platform with Android, Windows, web, and Supabase-backed services.

## Supported clients

- Android: `app/`
- Windows desktop: `desktopApp/`
- Web/PWA: `web/`
- Shared product rules and models: `shared/`
- Supabase migrations and Edge Functions: `supabase/`

Android and Windows use the same BLINK account, Supabase backend, ranking rules, coins, verification, messaging, moderation, and permissions.

## Development workflow

`main` is the production / known-good branch.

Risky, large, database-affecting, or potentially breaking work must be validated in `Testlab` (or a feature branch targeting Testlab) before promotion to `main`. Supabase changes should be tested in a preview/staging environment before production whenever one is available.

See `AGENTS.md` for the complete engineering and platform-parity rules.

## Android development

Requirements:

- Android Studio / JDK 17
- Android SDK required by the Gradle project
- A local `.env` for developer-only configuration
- A local `app/google-services.json` when testing the real Firebase project

Useful checks:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Production APK/AAB files should be produced by the signed GitHub Actions release workflow, not with an ad-hoc local signing key.

If the signed release fails due to `MAPS_API_KEY` or `GOOGLE_SERVICES_JSON_BASE64`,
see [secure Android release configuration](docs/android-release-secrets.md). The
workflow now also supports `GOOGLE_SERVICES_JSON` containing the original
Firebase JSON directly, without requiring manual base64 conversion.

## Production configuration

Never commit production secrets or signing material.

Protected CI/backend configuration includes, as applicable:

- Android release keystore values
- `MAPS_API_KEY`
- production Firebase client configuration
- Supabase/backend service credentials
- Paystack secret credentials
- weather/provider server keys
- Firebase service-account credentials

Client-safe identifiers and server secrets are not interchangeable. Backend-only secrets must never be bundled in Android, Windows, or web clients.

## Supabase

Database changes live in `supabase/migrations/` and must remain versioned. Edge Functions live in `supabase/functions/`.

Run the repository Supabase safety gate for every backend change. Production must not be the first environment used to experiment with a risky schema, RLS, Auth, Storage, Realtime, RPC, cron, webhook, or Edge Function change.

## Release gates

A production promotion should keep the relevant gates green:

- Android quality gate
- Android runtime smoke
- Android release smoke
- Windows desktop build/parity
- Supabase migration safety
- web/domain checks when affected
- secret-leak guard when enabled

A failed required gate is a release blocker.
