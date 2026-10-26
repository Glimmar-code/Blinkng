# BLINK rich link previews — staging candidate

This change integrates one shared URL detection policy with Android, Windows and Web visual adapters.

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
