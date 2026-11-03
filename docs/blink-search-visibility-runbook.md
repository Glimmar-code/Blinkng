# BLINK search visibility: production verification runbook

Scope: `https://blink.com.ng/`. This checklist complements the repository's static SEO regression guard. Passing repository tests does **not** prove a Google ranking, crawler access through Cloudflare, or production deployment.

## Deployment and canonical host
1. Confirm the approved `main` web commit deployed. The GitHub Pages artifact stamps `/blink-build.json`, but the canonical domain may be served by **Cloudflare Pages** rather than that artifact; a 404 for this optional GitHub-only marker does not mean the production site is broken. Production smoke uses SHA-256 comparisons of seven public deployed assets against the exact workflow-run source checkout, including the homepage, sitemap, landing CSS, three new informational pages, and robots.txt. Cloudflare Pages injects an Insights beacon into HTML responses; the verifier strips **only that recognized script insertion** before hashing and rejects other HTML modifications. This proves those seven public asset bytes correspond to the approved checkout, after that strictly limited normalization; it does **not** establish every asset, internal backend state, or Google index status. Never equate a passing PR alone with publication.
2. From an external connection, check `https://blink.com.ng/`, `/robots.txt`, `/sitemap.xml`, `/features/`, `/download/`, `/students/`, `/marketplace/`, and `/safety/`. Each intended public URL must return a successful response with the expected HTML/XML.
3. Confirm that `https://www.blink.com.ng/` and HTTP variants permanently redirect to the canonical HTTPS apex host with no loop. GitHub Pages does not interpret `web/_redirects` or `web/vercel.json`. Check the actual DNS/Cloudflare/GitHub Pages setup instead.
4. Check `/this-page-does-not-exist` for an appropriate non-success HTTP status. The JavaScript SPA fallback should not become a discoverable soft-404.
5. Check that `/messages`, `/settings`, `/login` and other private routes are not indexed. The SPA changes the robots meta tag only after JavaScript runs; a server-side noindex header or separate private-route handling is preferable where hosting permits. Never make private data crawlable merely for SEO.

## Search Console and Bing
1. In **Google Search Console**, verify the domain property `blink.com.ng`; submit `https://blink.com.ng/sitemap.xml`.
2. Use URL Inspection for homepage, about, features, download, students, marketplace, and safety. Inspect live rendered HTML and canonical URLs; request indexing only where appropriate.
3. Investigate excluded pages, fetch failures, duplications, Soft 404 and "crawled – currently not indexed" reports without assuming that sitemap submission guarantees indexing.
4. Verify Bing Webmaster Tools ownership, submit the same sitemap, inspect crawler errors and decide later whether publication frequency justifies IndexNow.
5. Measure branded and non-branded queries, impressions, clicks, indexed pages and acquisition from search traffic. Separate search rankings from on-site visitor activity.

## AI search discovery
1. Confirm `robots.txt` is served correctly. Current `User-agent: *` / `Allow: /` permits ordinary public crawling.
2. Verify Cloudflare bot rules and allowlisted published crawler IP ranges for legitimate search bots, including Google's, Bing's and OpenAI's OAI-SearchBot when necessary. Do **not** disable security controls indiscriminately or assume local robots directives bypass edge security.
3. Ensure facts about BLINK are consistent across the About, Features, Download and new public pages. Do not invent reviews, ratings, users, university partnerships or unsupported payment protections.
4. Track AI referral traffic only with privacy-respecting aggregate analytics and applicable consent controls; do not fingerprint visitors.

## Performance and release safety
1. Run mobile Lighthouse / PageSpeed Insights and measure real-user Core Web Vitals where available, prioritizing slower mobile networks.
2. Check mobile at multiple viewport widths; accessibility, images, navigation and download links must work with and without JavaScript where the public information permits.
3. Run `python3 scripts/check_github_pages_site.py` and the relevant web CI checks on the candidate SHA. These tests verify repository code, not live crawlability.
4. Keep this change inside the `Testlab-web-*` branch while checks or hosting verification remain incomplete. Never fast-forward divergent `Testlab` over `main` or silently erase existing features. Reconcile the branches explicitly before promotion.

## Privacy and indexing eligibility
BLINK user profiles, posts, reels, private messages, account settings, activity and marketplace transactions must not be bulk-indexed by default. A future public-content SEO system needs **explicit user control**, privacy checks, moderation checks, deletions and opt-out behavior, correct per-URL HTML metadata, and no exposure of personal data. It is out of scope for this foundation patch.

## Bing / IndexNow notifications — not proof of indexing
1. The site includes a publicly hosted IndexNow key at `/c7a33ad1df24b04816761dc5dae7fbfa.txt`. It is intentionally **public** for host ownership verification, not an application credential.
2. After a successful deployment from `main`, `Notify Bing IndexNow of changed public BLINK pages` selects only approved canonical HTML pages that changed in that deployment. When the key is first published, it sends the nine approved public sitemap pages once. Private profiles, posts, inbox, settings, user content, and other app routes are explicitly disallowed.
3. The notification is blocked until the key file and every selected page are publicly available with HTTP 200 and matching canonical metadata. Accepted IndexNow responses (200, or 202 pending validation) prove **only receipt**, not crawling, indexing, or ranking.
4. When needed, an owner can manually dispatch the same GitHub workflow with `bootstrap_all_public_urls=true` to notify all nine approved URLs again. Do not repeatedly submit unchanged pages.
5. Use a verified **Bing Webmaster Tools** property to confirm receipt, last crawl, and actual per-page indexing via URL Inspection. IndexNow does not submit to Google.

## Google Search Console requires verified access
1. Sign in to Search Console, add the Domain property `blink.com.ng`, and verify its DNS TXT record at the DNS provider; alternatively use a URL-prefix property and the specific verification method presented in the UI. Do **not** invent a verification token or publish one from another account.
2. Under **Sitemaps**, submit `https://blink.com.ng/sitemap.xml` and check that all nine canonical public URLs were discovered without fetch errors.
3. Use **URL Inspection** on each URL, including `/students/`, `/marketplace/`, and `/safety/`, to check the **Google Index** view (not just the live test). Request indexing only for URLs that need it. Google may take days or weeks and does not guarantee indexing.
4. In Bing Webmaster Tools, verify ownership of the same canonical site (or use its Google Search Console import if offered), submit the sitemap, and inspect **Index Coverage**, **Site Explorer**, and **URL Inspection**. Manual inspection is still needed after an accepted IndexNow notification.
5. Record separately: (a) technical eligibility, (b) sitemap discovery, (c) notification accepted, (d) crawler visit, (e) URL actually indexed. Never report any of (b)–(e) as confirmed from static website tests alone.
