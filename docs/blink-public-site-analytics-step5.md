# BLINK public website analytics — Step 5 staging and release controls

Canonical domain: **https://blink.com.ng/** — domain purchased through **GO54**.

## What the planned feature measures

Only aggregated counts of **public informational page views** and **official Android APK button clicks**, for the homepage, About, Features, Download, FAQ and Privacy pages.

The database stores only `day`, one of six `page` categories, one of two `kind` values, and `total`. It never stores IP addresses, user agents, usernames, profile IDs, emails, cookies, search input, visited profile URLs, selected campuses, private messages or session identifiers. Neither unique visitors nor actual app installations can be inferred from these counts. Like any unauthenticated counter, totals can be affected by robots or repeated clicks.

If enabled, the Edge Function verifies the official website origin, rejects unexpected fields and large bodies, and applies a global database cap of 120 accepted increments per minute per page and event. **An Origin header and global cap reduce accidental abuse, but cannot defeat deliberate spoofing. Never use these metrics for financial accounting.**

## Activation is OFF until a separate explicit release

There are two independent activation controls:
- `web/site-analytics.js`: `const ENABLED = false;` (no web events for normal visitors).
- Supabase Edge environment `BLINK_SITE_ANALYTICS_ENABLED`: any value other than exactly `true` yields HTTP 204 without storage.

No one should flip either switch during this PR. Stage and validate SQL, Edge function and RLS/admin tests first. Production activation later requires the website owner's decision, an updated privacy/legal review, a deployed and verified Edge Function and migration, then a separate Testlab PR that enables the client after the backend is ready.

The browser respects Global Privacy Control and Do Not Track and sends only `{"page":"home","kind":"view"}` (or another allowed public page / the download kind) with `credentials:omit` and `referrerPolicy:no-referrer`. Infrastructure may process transient networking information like IP addresses to serve requests, but the BLINK metrics schema does not persist it.

## Administration

The authenticated BLINK web route `/site-stats` shows 30-day aggregate views and APK taps, broken down by public page. It calls `get_blink_site_metrics`, which delegates authorization to the existing `private.require_blink_admin()` database gate. Non-admins cannot read the table; the writer RPC is callable only by `service_role`.

The admin page shows zero/empty records while collection is inactive; that is **not evidence of zero visitors**.

## Safe deployment and verification

1. Testlab static/SQL/security checks must pass. Do not merge a migration solely because browser tests pass; check database grants in a disposable environment or a staging Supabase project.
2. On deployment, leave `BLINK_SITE_ANALYTICS_ENABLED` unset/false. Confirm a request yields no writes.
3. Verify `anon` and `authenticated` cannot call `record_blink_site_metric` or select raw daily/minute counters. Test `get_blink_site_metrics` for both admin and non-admin accounts.
4. If the owner later requests actual activation, review the privacy policy, add an explicit consent/GPC plan if required, and deploy the Edge endpoint and migrations before enabling the frontend.
5. Verify cross-origin CORS, a capped test burst, global counter integrity, user permissions, 30-day aggregate query, and deletion/retention policy.
6. Do not transmit credentials, access tokens, raw browser traffic or private app content to diagnostic dashboards.

This Step 5 implementation does **not** provide identifiable audiences, unique-visitor reporting, earnings or paid-ad attribution.
