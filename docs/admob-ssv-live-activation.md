# BLINK AdMob: make signed reward verification live

## Domain purchase intentionally deferred (2026-10-13)

The owner confirmed the domain has not yet been purchased, so NXDOMAIN is
expected. The independent Supabase SSV service is online. Live readiness CI
tests the SSV endpoint now, and skips domain probes until repository variable
BLINK_DOMAIN_READY=true is set after domain purchase and DNS configuration.
Skipped checks do not establish AdMob app verification or guaranteed ad serving.
Google may limit ad serving until ownership/review prerequisites are met.

## Previously observed domain status (2026-10-13)

The read-only GitHub Actions probe confirmed:
- The Supabase SSV callback is **online**, unauthenticated Google GET requests reach the handler, and invalid unsigned requests correctly get HTTP 400. This **does not** verify Google-signed callback delivery.
- Both `blink.com.ng` and `www.blink.com.ng` returned DNS **NXDOMAIN** from the public GitHub Actions runner. Thus neither `/app-ads.txt` URL could be reached. This is **outside Android/Supabase code**: check the domain's registration/renewal status, delegated nameservers and active DNS zone with the actual DNS provider first.

Once the domain registration and nameservers are correct, configure GitHub Pages custom-domain DNS as follows (if GitHub Pages remains the authoritative site; do not replace a different hosting provider's DNS):

- Apex `@` **A** records: `185.199.108.153`, `185.199.109.153`, `185.199.110.153`, `185.199.111.153`. Alternatively use an `ALIAS`/`ANAME` for `@` pointing at `glimmar-code.github.io`; don't configure both overlapping apex methods.
- `www` **CNAME**: `glimmar-code.github.io`.
- Keep `web/CNAME` set to `www.blink.com.ng`; GitHub Pages should redirect apex requests to the `www` site so Google's crawler can follow `https://blink.com.ng/app-ads.txt`. In the GitHub Pages settings verify the custom domain and HTTPS certificate.
- Re-run `AdMob live SSV readiness` after public DNS has propagated. The probe must pass both publisher-file URL checks.
- Only after that, confirm app-ads.txt approval in AdMob and save the real Google SSV callback URL inside the rewarded unit.

Google explicitly crawls the **apex/root domain** for `app-ads.txt`, even if the developer website in the store is `www.blink.com.ng`. A valid GitHub source file alone is insufficient while the DNS name is NXDOMAIN.

## What is already deployed
- Production rewarded ad unit: `ca-app-pub-9152580730716304/4343111201`.
- Google SSV callback URL (enter this **exactly** in the rewarded unit settings):
  `https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/admob-reward-ssv`
- The Supabase `admob-reward-ssv` function is deployed with `verify_jwt = false` for Google's signed GET callbacks. This does **not** bypass signature verification for real identified claims.
- Android populates `user_id` and `custom_data` from the authenticated user and claim ID before displaying the ad.
- The BLINK app awards 10 coins per completed ad, with server-authoritative daily limits and milestones. The **AdMob ad-unit reward amount must also currently be set to 10** to match the webhook and RPC checks.
- `web/app-ads.txt` authorizes Google as the direct seller for `pub-9152580730716304`. Its *public root-domain URL* must also serve the text, not an HTML app page.

## Required Google AdMob account configuration

1. Open AdMob, choose **Apps → BLINK → Ad units**, then choose the **rewarded** unit with ID ending `4343111201`.
2. Confirm the app's **App readiness**, account standing, app-ads.txt verification and any ads-serving restrictions. Android code cannot override a Google account hold or no-fill.
3. Edit the rewarded unit's **reward settings** so the reward amount is **10** and the reward item is **Blink Coins**. If this is not 10, the production SSV verifier does not currently accept the callback as a valid coin reward.
4. Open **Advanced settings → Server-side verification → Edit → Set up and verify callback URL**. Paste exactly:
   `https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/admob-reward-ssv`
5. **Leave optional User ID and Custom Data test boxes blank** during AdMob's URL reachability test: Google cannot supply a real BLINK UUID or real reward claim in the dashboard test. Tap **Verify URL**, **Use verified URL**, and **Save**. For mediated networks, enable **Apply to all networks in mediation groups** where relevant. Android supplies real values dynamically.
6. Verify the app's Google Play listing and developer website settings where AdMob requires app-ads.txt / readiness approval. The publisher file needs to be publicly reachable at `https://blink.com.ng/app-ads.txt` (with valid redirects), and Google must crawl it. Publishing a GitHub file alone does not establish public reachability.
7. On a device with an approved app/ad unit, **watch one real rewarded ad completely**, then verify Supabase has a new row with `ssv_verified_at` populated and `ssv_transaction_id` unique. Confirm the user received the exact server-approved coins once, and a duplicate callback cannot pay twice. Do not create fake claim rows or backdate rewards to simulate this.
8. Only after an authentic signed callback is confirmed in Supabase staging should the stricter SSV-required credit migration in draft PR #249 be applied and retested. Applying it before signed callbacks arrive would block legitimate rewards.

## Read-only automated preflight
Run **Actions → AdMob live SSV readiness → Run workflow** after the workflow has merged to main. It performs two non-crediting checks:
- the public Supabase callback rejects an unsigned GET with HTTP 400 (instead of requiring a Supabase user JWT or returning 404);
- the root-domain `app-ads.txt` returns HTTP 200 and contains the expected Google publisher entry.

A green preflight verifies **only** network reachability and publisher-file routing. It does not establish that the Google AdMob dashboard is configured, that the app is approved, that inventory is available, or that a real signed callback has reached Supabase.
