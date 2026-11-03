# BLINK website: production authentication, indexing and performance verification

Domain: **https://blink.com.ng/** (purchased through **Go54**).
GitHub Pages is the website origin; Supabase is the authentication/backend origin.

## What the website regression suite verifies

- Google OAuth starts with the canonical `https://blink.com.ng/` callback.
- Password-recovery email uses `https://blink.com.ng/settings/password`.
- GitHub Pages deep links preserve recovery tokens in the fragment, never in a URL query.
- Incomplete OAuth sessions are rejected; errors do not echo provider-provided strings.
- Private browser routes and auth pages use `noindex,nofollow` and `no-referrer`.
- The public home has an indexable canonical, six informational pages remain in `sitemap.xml`, and privileged Supabase credentials are not included in static web assets.
- Network requests have an actual timeout even when callers supply an `AbortSignal`; caller cancellation is not labelled a network timeout.

## Manual production steps — not achievable by GitHub tests alone

1. **Supabase → Authentication → URL Configuration:** verify that **Site URL** is `https://blink.com.ng/` **only after** the website is deployed and callbacks are tested. Historically the dashboard still had the old GitHub Pages project URL. Allowed Redirect URLs should include the canonical root and `/settings/password` (both apex and www may remain for backward compatibility). Do not switch until production validation is ready.
2. **Google sign-in:** test on a fresh Android Chrome session. Confirm the Google consent flow returns to `blink.com.ng`, no credentials remain in the browser URL and BLINK opens the correct account. Check the Google provider OAuth callback settings if it fails.
3. **Password recovery:** request a reset to an email address you control; open the newest email, confirm password settings opens and a new password works. Never share the token URL, password or recovery email with anyone.
4. **Android App Links:** test a real `https://blink.com.ng/@public-username` link in the installed signed Android app. Website `/.well-known/assetlinks.json` verification alone cannot prove device association succeeded.
5. **Google Search Console:** inspect the submitted `https://blink.com.ng/sitemap.xml`, URL inspection for each six static pages and indexing errors. Indexing and ranking cannot be guaranteed by code.
6. **Performance:** run Lighthouse on a mobile device or Chrome DevTools after deployment and confirm the service worker serves current JS, CSS and campus data while private content is never retained in offline caches.
7. **Analytics:** if adding measurement later, choose a privacy-respecting service, avoid tracking private messages/profile searches, and document any analytics or consent requirements in the privacy policy.

Do not paste production tokens, keystores, API secrets or private data into GitHub issues or logs.
