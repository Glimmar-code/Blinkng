# BLINK temporary GitHub Pages URL (historical)

BLINK originally used `https://glimmar-code.github.io/Blinkng/` as its canonical website. Following the owner's purchase of **blink.com.ng through GO54**, the intended official site is `https://www.blink.com.ng/`.

The move back to the purchased domain is staged in Testlab before main. **Code-level metadata does not prove the domain is live, indexed or verified in Google Search Console.**

Production activation checklist:
1. GO54 DNS: confirm `www` points to the configured GitHub Pages host and the apex `blink.com.ng` redirects to the canonical `www` website via supported DNS/hosting configuration.
2. GitHub Pages Settings: confirm custom domain `www.blink.com.ng`, enforced HTTPS, and reachable `/robots.txt`, `/sitemap.xml`, `/privacy/`, and `/.well-known/assetlinks.json`.
3. Supabase Auth URL Configuration: allow exactly the needed official-domain login/password reset redirects; do not replace Android app callback URLs or enable signup confirmation as part of SEO.
4. Google Search Console: verify the Domain property via a GO54 TXT record and submit `https://www.blink.com.ng/sitemap.xml`. Request indexing of the homepage.
5. Smoke-test OAuth sign-in, password reset, APK download, Android App Links and mobile rendering on the official domain before publishing/merging.
6. Confirm release-workflow CI, quality gates and production GitHub Pages deployment are successful.

Historical GitHub Pages links remain supported by the client router where possible; canonical search signals now point to the official domain. Ranking for the generic term 'blink' or autocomplete for 'bli' cannot be guaranteed.

Automated static check: `python3 scripts/check_github_pages_site.py` (legacy script path).
