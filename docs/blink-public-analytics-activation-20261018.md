# BLINK public website analytics — Activation 18 October 2026

**Owner approval:** "Activate analytics" on 18 October 2026.

**Official website:** https://blink.com.ng/ (domain registered through **GO54**).

## Enabled functionality
- The public homepage, About, Features, Download, FAQ and Privacy pages emit one approximate anonymous `view` event when loaded on the official HTTPS domain.
- A tap on `Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk` on those public pages emits an approximate anonymous `download` button event.
- Private user routes, profile searches, app messages, user activities and signed-in dashboard routes are excluded.
- A visitor's Global Privacy Control or Do Not Track browser signal disables all this collection.
- No cookie, browser fingerprint, user ID, username, chosen school, query string, private page URL, device information, or event log is transmitted into the metrics database.
- The backend receives only the allowed `page` and `kind` strings, and stores daily totals by category. Supabase infrastructure may transiently process ordinary network metadata in serving HTTPS requests.
- Admins can view totals at `/site-stats` after authenticating; the database verifies `private.require_blink_admin()`.

## Safeguards and limits
- The database enforces a maximum of 120 accepted event increments per minute **per page/event type**, not per visitor. Reports are estimates; bots and repeats can still influence counts.
- These counts do **not** represent distinct people, confirmed APK installations, paid advertising performance or income.
- The Edge Function accepts browser POSTs from the canonical domain and rejects unexpected attributes and oversized payloads. Spoofing the Origin header is possible; it is not an authentication check. The global database cap bounds event writes.
- Website code explicitly activates metrics; the Edge Function can be immediately disabled by setting `BLINK_SITE_ANALYTICS_ENABLED=false` in Supabase function environment settings, or by redeploying the prior disabled handler. To halt website collection independently, revert `const ENABLED = true` in `web/site-analytics.js` and redeploy the website.
- The Privacy Policy (effective 18 October 2026) explains this collection and the browser opt-outs.

## Live activation sequence
1. Merge only after all latest-head Testlab checks pass: web parity, privacy activation tests, browser smoke, domain integration, Supabase safety, secret leak, Android and Windows.
2. Verify the GitHub Pages production deployment succeeds and live custom-domain checks pass.
3. Deploy the Edge Function from that exact approved Git commit; use the no-JWT endpoint intentionally because this is public and only a service key can call the writer RPC.
4. Verify the live function accepts legitimate cross-origin POSTs; reject invalid origin, unknown event fields and methods; query the Supabase aggregates to confirm at least one actual accepted test event. Never log a user identifier.
5. Verify the page and APK click counts for normal site traffic eventually populate, and the admin RPC rejects ordinary accounts.
6. Monitor errors and rate-limit caps. The deployed website/backend could diverge if one deployment fails; do not claim full activation until both are verified.

## Emergency off
Revert website flag to false and deploy to stop browser events; revert Edge Function to an explicit-off handler or set `BLINK_SITE_ANALYTICS_ENABLED=false` to stop backend storage. Public site and login must continue functioning even if the analytics backend is unreachable.
