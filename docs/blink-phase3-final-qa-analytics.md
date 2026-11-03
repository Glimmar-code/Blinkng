# BLINK Phase 3: mobile browser QA and privacy-safe analytics

Domain: https://blink.com.ng/ — purchased through GO54.

## Browser smoke tests
GitHub Actions runs a local static copy of BLINK in headless Chromium at 360, 390, 430, 768 and 1280 CSS pixels. It tests landing page feature cards, mobile navigation, horizontal overflow, interactive tour, FUTA catalogue search, public profile lookup, sign-in actions and official latest-APK release details.

Supabase responses and GitHub release data are **mocked**; CI never fetches real user accounts or accesses real profile data. Failure screenshots use synthetic data and expire after three days.

## Opt-in local-only funnel and performance metrics
Open `https://blink.com.ng/?blink_perf=1` and inspect Chrome DevTools Console. The page records approximate FCP, LCP, CLS, INP, TTFB, transfer size and four numeric click counters: apkDownloads, featureTabs, campusSelections and profileSearches.

These metrics exist only in that browser tab's memory, with no visitor identifier, username, selected school, IP, query, URL, cookie or network upload. Ordinary visitors receive no analytics. **This is not sitewide analytics and does not provide visitor totals.** Actual cross-visitor counts would require a separately designed, privacy-reviewed backend, appropriate disclosures and retention controls.

## Manual production checks
- On an Android phone, inspect 360/390/430px portrait, landscape, large text and reduced-motion settings. Confirm back button, login, recovery, app links and latest signed APK install.
- Test Google OAuth and password reset with your own email/account only; never share the callback token or password.
- Verify live `/.well-known/assetlinks.json`, `/app-ads.txt`, sitemap, six indexable pages, and Google Search Console coverage.
- Run Chrome Lighthouse mobile on the deployed site. Browser emulation and source-byte limits do **not** prove any Lighthouse score.
- Verify Supabase Site URL with its dashboard owner before changing it. Website code checks alone cannot update or verify dashboard settings.

## Safe publication
Run Testlab's browser smoke, web parity, domain integration, Supabase, secret guard, Android and Windows checks against the exact latest head. Merge only after green checks and verify the new GitHub Pages deployment plus live domain verification.
