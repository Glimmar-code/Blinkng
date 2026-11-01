# Contest repair — 29 October 2026

Repairs build on PR #638 at `9f270f74076d8d422d8d0eedb5ee9cafded3e6e8`, preserving that PR's implementation/history independently of the Chat/Create Post branch. Main was fetched and is already an ancestor. The original remote Contest branch is `Testlab/blink-contest-free-entry-20261028`.

## Changes

- Shared voting rules accept a JSON-null vote as unused, fixing Android's disabled Vote action, while still rejecting existing votes, self-votes, missing candidates and closed voting.
- Android and Windows use Ongoing, Upcoming and Completed tabs. Completed history includes cancelled contests without claiming they have winners.
- Windows detail-load failures show an error and retry action instead of an endless spinner.
- Windows applicants can select and explicitly upload JPG/PNG/WebP/MP4 files using the same 8 MB image / 25 MB video limits as Android. The upload action discloses that media is publicly accessible before approval; it does not automatically submit an application. Storage uses the existing owner-prefix policy, and file bodies stream from disk.
- Android Contest RPC calls propagate coroutine cancellation, preventing cancelled loads from being reported as new errors.
- Contest invitation requests time out after 15 seconds. Temporary failures retain the explicit Open BLINK action; confirmed invalid/mismatched entries disable it. Website code never casts a vote automatically.
- Android Gradle matches AGP 9.1.1's required Gradle 9.3.1 with the official distribution SHA256. Native quality workflows include Testlab branch pushes. Web CI includes invitation regressions.

## Verification and merge hold

The actual Contest SQL migrations passed the existing disposable PGlite suite for admin authorization, application approval, one free vote, ranking, deadlines, share analytics, vote moderation, winners and prize ledger. This does not establish hosted deployment or real payment receipt. Supabase safety checks against main passed.

Web domain/SEO, private-route caching, mobile budgets and new invitation regressions passed. Tests cover transient outages, invalid candidates, safe text rendering, explicit deep links and absence of automatic voting. Final combined native results are recorded below.

GitHub API requests still fail with proxy CONNECT 403; the saved `api.github.com` network addition is not active in the current runtime. Consequently current PR checks, branch protection, review state and merge eligibility cannot be verified here. Do not merge by direct push to main or bypass required checks.

Hosted/staging credentials, Android handset/emulator validation and Windows installer execution are not available in this Linux task. These checks remain required before claiming the whole Contest feature is release-ready. Production SQL, storage settings and main have not been changed by this repair.

## Combined integration

At the owner’s request, the Contest repair was merged locally with Chat/Create Post commit `ef14bf5f`, preserving all three PR histories. Consolidated code commit: `1a4885adf599a270d036493221ed6318f8c8ae93`; remote branch: `Testlab/contest-create-post-chat-20261029`. Runtime screenshot exports and both posting/contest SQL suites were retained when resolving conflicts. Original PR branches were not closed or replaced.

Combined desktop build and all 25 tests passed. Full combined Supabase suite passed, including posting guards and Contest lifecycle/security assertions. Workflow YAML, invitation recovery tests, Supabase safety and structural parity checks passed. Combined Android validation passed: debug APK, lint, instrumentation compilation and 26 focused unit/layout tests, with zero failures or skips. APK signature verification passed. The final Gradle command took 27m 10s; Android CI now allows 60 minutes for its larger full-suite workload, preserving every check. These results validate application code at `1a4885ad`; the follow-up changes only documentation and CI time allowance. Main remains at `3f0610ef6b6112e43e63d1d61998e52e6ba5ef41` as last verified. No production promotion or hosted SQL deployment has occurred.
