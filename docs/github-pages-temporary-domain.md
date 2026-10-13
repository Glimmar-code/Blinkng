# BLINK temporary website: GitHub Pages

**Current public website:** https://glimmar-code.github.io/Blinkng/

Until the developer purchases and configures their own domain, treat GitHub Pages as the canonical **website**, not `www.blink.com.ng`.

## What this change does

- Removes `web/CNAME` (so the published Pages artifact does not request the unowned custom domain).
- Points site metadata, privacy policy, sitemap, and web-client `siteUrl` to the Pages project URL, including the required **/Blinkng** path.
- Preserves in-site SPA navigation and `web/404.html` routing under `/Blinkng`.
- Increments the web service worker cache version to avoid stale URLs.
- Keeps every Android APK download button pointed to the moving GitHub Releases URL:
  `https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk`.
- Changes the Pages workflow validation and adds a PR/CI test to prevent accidental custom-domain regressions.
- Keeps the same Supabase project, published Android app, signing identity, and Android/Windows features.

## External configuration checks

1. **GitHub repository → Settings → Pages:** Under **Custom domain**, remove `www.blink.com.ng` if it is still present in GitHub Pages settings; the site source alone may not clear a repository-level setting. Leave the custom-domain box blank. The deploy uses GitHub Actions and uploads the `web/` directory. Once a deploy finishes, confirm the displayed live URL is `https://glimmar-code.github.io/Blinkng/`.
2. **Supabase → Authentication → URL Configuration:** Allow `https://glimmar-code.github.io/Blinkng/` as a redirect URL for Google OAuth and email links. If password resets use the web, also allow `https://glimmar-code.github.io/Blinkng/settings/password` (or a narrow /Blinkng/* redirect pattern). Change the Site URL only if BLINK's Android and other auth clients do not rely on the existing value. Avoid broad redirect wildcards.
3. **Verify with an actual browser:** Open the Pages URL, sign in, check password-reset and public profile deep links, and press Download Android App. The button should resolve to the latest **published, verified** GitHub APK. Production GitHub Releases remains the single source of signed builds.
4. **Android App Links:** Existing app manifest/signature configuration targets `www.blink.com.ng`. Merely moving the website does **not** change Android App Links or make GitHub Pages links verifiable in the installed APK. A separate reviewed Android + assetlinks update is needed if verified app handoff from the GitHub Pages host is required before domain purchase.
5. **AdMob app-ads.txt:** Publishing `app-ads.txt` beneath a GitHub Pages *project subpath* does not prove ownership of a publisher domain for AdMob. Leave publisher-domain verification pending until a real domain is bought and configured.

## Validation and rollback

`python3 scripts/check_github_pages_site.py` checks the project-path URL, CNAME absence, sharing metadata, sitemap, auth redirect base, 404 routes, service-worker version, and latest signed APK link. The Web Parity Testlab Gate runs this on PRs and deploy uses the same check.

When a domain is later purchased, create a new Testlab PR to restore `web/CNAME`, `web/config.js` URL, canonical links, sitemap, Auth redirect allowlist, Android App Links and relevant checks **together**. Do not prematurely reroute visitors to a domain that is not configured.
