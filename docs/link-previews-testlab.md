# BLINK rich link previews — staging candidate

This change integrates one shared URL detection policy (up to 3 distinct URLs per text surface) with Android, Windows and Web visual adapters.

## Privacy and security contract

- BLINK only generates preview cards for explicit HTTP(S) URLs. Clicking the card asks the OS/browser to handle the original URL.
- Metadata GET is limited to the public, no-secrets `link-metadata` Supabase Edge Function.
- The Edge Function restricts outgoing HTML requests to a fixed list of known public hostnames, uses HTTPS only, rejects redirects and custom ports, caps reads at 128 KiB and times out. Unrecognized sites use hostname/URL fallback cards, not external fetches.
- Do not scrape or follow unknown user-supplied destinations in the client. No remote scripts, auto-play or iframe HTML is inserted.
- Chat respects the existing **Link previews** privacy toggle. No media is downloaded for view-once messages.
- Staging must validate private-network protection, malicious HTML, image MIME/type, long captions, deleted/private posts, performance, font sizes, screen readers, links embedded in chat media captions, and genuine Android ↔ Windows ↔ Web parity.

## Deployment and release hold

The new function must be deployed and exercised in a free disposable/staging Supabase environment first. If no safe staging is available, retain the draft branch and keep production untouched. An undeployed function must fail gracefully to a clickable hostname fallback; this is not proof of fully working title/image previews.

All Android debug/unit/lint + Windows test/compile/package + web link checks and device UI tests must pass on the exact PR SHA. Do not merge into main or publish an APK from code review alone.

## Additional verification findings (October 26, 2026)

- Production Supabase currently does **not** contain `public.user_chat_privacy` or `public.update_chat_privacy`, although the previous repository migration includes both. This existing backend drift blocks chat preference synchronization and needs a staged, non-destructive deployment. Do NOT silently turn the preference on in clients when its state cannot be fetched.
- Production also does not have the new `link-metadata` Edge Function; it remains source-only. A custom CI workflow now parses the browser scripts, runs Node render/privacy/escaping contract tests and checks the new Deno backend with offline tests.
- Shared URL detection renders at most three unique links per text area, including the dedicated post `link_url` field where available. Unsupported hosts remain safely clickable with hostname/URL fallbacks.
- Rich metadata for public BLINK profiles, posts, reels and approved Market listings is deliberately filtered to public content. No private or removed items should be presented as public preview metadata.
- The main web bundle remains below the existing 160 KiB mobile limit by moving card rendering and metadata upgrades into `web/link-previews.js`, loaded before `web/app.js` and added to the service worker shell.
- Actual authenticated cross-device chat preference change, behind-login real content preview, production Edge deployment, physical-device layout, shared-link unfurling by external apps, and signed APK publication still require validation. Source and CI tests alone are not evidence of completion.
