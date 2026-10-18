# BLINK final website acceptance checks (18 October 2026)

Canonical public website: **https://blink.com.ng/**. Your domain was purchased through **GO54**.

## Verified without a user account
- The live homepage and five marketing pages return successful HTTPS responses and canonical URLs.
- The submitted sitemap at `/sitemap.xml` lists only six public pages and matches `/robots.txt`.
- Android Digital Asset Links at `/.well-known/assetlinks.json` contains the production BLINK package and pinned signing fingerprint.
- Supabase Auth's public provider settings report that Google sign-in is enabled; that does not prove a real account can complete Google sign-in.
- The official Android APK download link points to the latest release alias.
- Anonymous analytics Edge Function accepts the approved two-field public event and stores a daily aggregate. The automated check creates a **synthetic** FAQ page view, not a unique real visitor.
- Real headless mobile Chrome is also used to verify that visiting the official public homepage triggers a cookie-free browser analytics request with a 204 response, without accessing an account.

## Checks that require a real Android phone or the site owner's accounts
### Google sign-in (browser)
1. On a phone open `https://blink.com.ng/` and select Sign in → Continue with Google.
2. With **your own account**, finish the authentication prompt. Confirm you return to `blink.com.ng` and reach your actual BLINK profile without seeing a callback or redirect error.
3. If the redirect fails, check Supabase Dashboard → Authentication → URL Configuration: Site URL should be `https://blink.com.ng/`, and permitted redirects should include `https://blink.com.ng/` and `https://blink.com.ng/settings/password`. You must verify these actual settings in the dashboard; repository tests cannot read or change them.
4. Supabase → Authentication → Providers → Google must be enabled (public API check passed). A real provider/OAuth callback still needs this phone test.

### Password recovery
1. Open `https://blink.com.ng/login` → Forgot password; use only an email account **you control**.
2. Follow the newest reset email and confirm the browser reaches the BLINK password-update form. Choose a new password, then sign out and test it.
3. Never upload or share reset emails containing tokens, callback URLs, passwords, Google auth codes or session credentials.
4. On failure, verify the Supabase redirect allowlist above and the configured email sender. Static code and CI cannot guarantee email delivery.

### Native Android App Links
1. Install the **latest signed BLINK APK** from the official Download page.
2. Android Settings → Apps → BLINK → Open by default / Opening links: ensure supported links are enabled.
3. Tap a real `https://blink.com.ng/@<existing-public-username>` link from a message or a different app. Test public post, reel and market links too; they should open BLINK rather than only the browser.
4. If verification still fails, developers can inspect `adb shell pm get-app-links com.aistudio.blink.appvtwo` and request re-verification with `adb shell pm verify-app-links --re-verify com.aistudio.blink.appvtwo`. Android's installed app association cannot be proven just by a valid JSON file.

### Google Search Console (Google controls indexing)
1. In the Google Search Console property for `blink.com.ng` open Sitemaps and confirm `https://blink.com.ng/sitemap.xml` was received successfully.
2. Use URL Inspection for `https://blink.com.ng/`, About, Features, Download, FAQ and Privacy. Request indexing if Google reports not indexed and the live inspection says the page is accessible.
3. Check Page indexing errors and whether Google selected the canonical URL. A valid sitemap and successful crawling **do not guarantee indexing or first-place ranking**.
4. Private social/account pages should not be indexed. Avoid submitting private, token-bearing or signed-in URLs for indexing.

## CI and release criteria
The `BLINK production integrations smoke` workflow runs after successful web deployments, checks live public SEO/App Links/Auth provider readiness and sends exactly one anonymous test FAQ event. It then verifies actual browser analytics with one additional test homepage view. Tests do not require credentials or read private app data. Test events are included in aggregate counters and must not be mistaken for human visits.

Manual account/device/Search Console checks remain necessary and cannot be performed using only GitHub test runners.
