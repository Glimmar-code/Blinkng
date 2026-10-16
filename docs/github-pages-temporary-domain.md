# BLINK production domain cutover — 16 October 2026

**Domain:** `blink.com.ng`, purchased through **GO54 (Whogohost)**.
**Canonical HTTPS website:** https://blink.com.ng/

## Deployment and rollback
- DNS at GO54 has the four GitHub Pages apex A addresses and a `www` CNAME to `glimmar-code.github.io`. Keep the registrar nameservers consistent with the GO54 DNS zone.
- GitHub Pages (Settings > Pages) is configured for `blink.com.ng`, with successful DNS check and Enforce HTTPS enabled (user confirmed in screenshot).
- The Pages GitHub Actions workflow uploads `web/`; `web/CNAME` must contain `blink.com.ng`. Do not delete this record, or switch back to an old project URL.
- Every website Android APK button uses `releases/latest/download/Blink-latest.apk`. Check a real signed release exists and that its size and signature are expected before promoting or advertising it.
- To roll back unverified code without discarding ownership, revert the domain cutover commit and restore all website metadata together; do not change GO54 DNS or GitHub Pages settings without an explicit rollback plan.

## External dashboard actions that code cannot perform
1. **Supabase Auth > URL Configuration:** Add `https://blink.com.ng/` and the relevant narrow callback paths (`https://blink.com.ng/**` only if route changes require it). Keep existing Android `blink://reset-password`, Windows loopback callbacks and former web routes during migration. Do not change Supabase's Site URL unless all account flows have been tested.
2. **Supabase Auth / Google:** Complete Google OAuth authorization for the existing Supabase callback and test login from `https://blink.com.ng/`. No new OAuth client secret belongs in web assets.
3. **AdMob:** Set the official developer website for the app to `https://blink.com.ng` wherever the app's publishing details allow it. Verify `https://blink.com.ng/app-ads.txt` responds 200 with `google.com, pub-9152580730716304, DIRECT, f08c47fec0942fa0` and wait for the AdMob crawler status. Ads appearing or earning revenue cannot be inferred from the DNS change.
4. **Android App Links:** Verify `https://blink.com.ng/.well-known/assetlinks.json` is served as JSON over HTTPS without redirect and matches the installed production signer and package `com.aistudio.blink.appvtwo`. A future signed Android release will be required for any new share-host defaults. Previous installed releases do not automatically change.
5. **Google Search Console:** Verify `blink.com.ng` using the existing GO54 DNS, submit `https://blink.com.ng/sitemap.xml`, and check indexing. Google indexing is not instantaneous.

## Verification steps
- In Testlab: run `python3 scripts/check_github_pages_site.py`, web syntax/parity checks, Android unit tests, and the domain integration CI workflow before merging.
- After production deploy: verify HTTPS home, HTTPS www redirect, privacy route, app-ads.txt, robots.txt, sitemap.xml, assetlinks.json, authentication callbacks and Android download on a physical device.
- Avoid touching DNS, signer keys, the Supabase backend or Google developer dashboard from unrelated feature changes.
