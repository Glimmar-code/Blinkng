# BLINK custom-domain rollout — staging checklist (16 October 2026)

The domain `blink.com.ng` was purchased through **GO54**. Target canonical
website: **https://blink.com.ng/** (verified GitHub Pages custom-domain setting; www is optional).

**Status:** This file describes a **proposed deployment on a Testlab-named
feature branch**. It does not confirm live DNS, dashboard approval, or a
production release. Keep production `main` unchanged until all applicable
checks below pass and the owner verifies the domain in a browser.

## 1. Registrar, DNS and website (previous list: 1–8, 45–48)

- At **GO54**, first verify the actual authoritative nameservers. Edit DNS
  at the provider that currently hosts the zone.
- For GitHub Pages hosting, set `@` A records to GitHub Pages' four documented
  addresses: `185.199.108.153`, `185.199.109.153`,
  `185.199.110.153`, `185.199.111.153`.
- **Optional**: Add `www` CNAME → `glimmar-code.github.io` (without `/Blinkng`) if you also want the www alias. Verify it redirects to the apex and does not compete with another host.
  Do not mix this with a competing website provider's A/CNAME records.
- **GitHub repository → Settings → Pages → Custom domain**:
  `blink.com.ng` (as shown in GitHub Pages settings). Keep Enforce HTTPS enabled.
- Publish the `web/` artifact containing `CNAME`. Verify that the apex serves
  the site directly over HTTPS, with working privacy, manifest, sitemap, robots,
  and 404 handling on mobile; browser smoke is mandatory.
- Existing GitHub Release `.../releases/latest/download/Blink-latest.apk`
  must remain the five website download buttons' canonical target; verify
  the **published and signed** file actually exists before advertising it.
- The application already has `web/privacy/index.html`; a separate Terms
  page must not be represented as published until it is actually created.

## 2. Android, Windows and stores (9–17)

- The Android manifest uses `https://blink.com.ng` as the production
  verified share host with profile/post/reel link patterns. The website
  serves `/.well-known/assetlinks.json`; the GitHub Pages deploy job
  regenerates it from the production signing keystore and compares with
  the authorized Android OAuth certificate. No signing secrets go in Git.
- Confirm the installed **released** APK's package name and signing
  fingerprint on an actual device. Test app handoff for post, reel and
  profile URLs. Test normal browser fallback if the app is not installed.
- Check the Windows application's browser OAuth handoff and shared-link
  paths. This need not create a new Windows-only subdomain.
- Play Console and any other actual store listing: enter the official
  developer website and privacy-policy URLs **when the listing is available**.
  Do not claim a store listing is active without checking it.

## 3. Supabase authentication and sign-in (18–25)

- Existing project: `jhwgifrlxwspoedxjaly` (`Blink`). Keep the
  existing `https://jhwgifrlxwspoedxjaly.supabase.co` URL and publishable
  key; website domain ownership does **not** imply changing the backend.
- Supabase → Authentication → URL Configuration: add
  `https://blink.com.ng/` and `https://blink.com.ng/settings/password`
  (actual login / recovery routes used by the website). Preserve earlier
  `https://www.blink.com.ng/` allowlist entries until the domain migration is stable. Consider changing Site URL to the canonical site
  only after Android recovery and Windows deep links are tested.
  Preserve existing Android deep-link redirects such as
  `blink://reset-password` and `blink://auth/reset-password`
  **only if** currently used/allowed by the app. Avoid wildcard redirects.
- If Google OAuth uses hosted Supabase, Google's OAuth redirect URI is
  normally `https://jhwgifrlxwspoedxjaly.supabase.co/auth/v1/callback`,
  not the BLINK website. Confirm against Supabase's current provider
  configuration before making any changes in Google Cloud Console.
- Optional custom API and Firebase Auth domains are **not** needed by
  this rollout and should not be enabled without a concrete requirement.

## 4. AdMob and email (26–39)

- AdMob: verify `https://blink.com.ng/app-ads.txt` is directly accessible
  over HTTPS and returns plaintext. Public `web/app-ads.txt` contains
  `google.com, pub-9152580730716304, DIRECT, f08c47fec0942fa0`.
  AdMob dashboard still must crawl and approve the file. Ads also depend
  on store/app/account reviews; file publication is not approval.
- Existing signed reward callback stays on the **Supabase** domain
  `https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/admob-reward-ssv`.
  Do not change this to `blink.com.ng` unless a reviewed proxy exists.
- GitHub repository variable `BLINK_DOMAIN_READY` may become `true`
  **only after DNS and URLs are actually reachable**, to enable AdMob
  readiness checks. Do not mark skipped tests as passing.
- Brevo or other chosen mail provider: add provider-supplied SPF,
  DKIM, and DMARC records **to authoritative DNS**, and verify status.
  Add MX records only if receiving email is desired and a mailbox exists.
  No made-up SPF/DKIM values; do not overwrite unrelated MX/SPF records.
- Adjust welcome-email, recovery-email, and notifications templates to
  link to the canonical domain only after the destination works.

## 5. Payments, discovery and optional future systems (40–50)

- Paystack: review merchant website and any applicable return URL;
  keep verified payment webhooks pointing to their working server-side
  endpoints, not a static GitHub Pages URL.
- Google Search Console: verify domain through its supplied DNS TXT,
  and submit `https://blink.com.ng/sitemap.xml` after deployment.
  Bing Webmaster Tools may also be configured.
- Analytics, Google Cloud OAuth origins, social profiles and support
  links may reference the canonical site where the provider uses them.
- Do not register `api.`, `game.`, `cdn.` or `testlab.` subdomains
  without servers, ownership protections, valid TLS and a real requirement.
  Adding arbitrary DNS records cannot create these services.

## Release gate

1. `python3 scripts/check_github_pages_site.py` (static host + asset tests).
2. Web parity and Android domain integration checks on the **exact** branch.
3. GitHub Actions **Verify BLINK live custom domain**, once hosted:
   confirms DNS, TLS, apex redirect, homepage, privacy, `app-ads.txt`,
   `assetlinks.json`, app config, sitemap, robots and web manifest.
4. Recheck real-device Android app links, login/reset, APK delivery and
   AdMob account status. Obtain provider-specific approvals separately.
5. Only then approve the small domain-only PR into `main`; do not merge
   outdated `Testlab` wholesale into `main` (it has diverged).
