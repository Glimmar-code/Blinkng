# BLINK Web & PWA audit remediation plan

## Direction

Apply the supplied static audit as a web-only hardening and polish pass across the existing static SPA and Supabase contracts. Security and privacy rules remain server-authoritative; the browser only presents states and handles navigation. The visual direction is **classic premium editorial**: near-black surfaces, restrained lavender/rose accents, strong typographic hierarchy, quiet glass borders, and short spring-like transitions that never delay action.

Three candidate styles considered: **classic premium editorial** (0.06), **neo-campus utility** (0.08), **soft glass social** (0.07). Chosen: classic premium editorial.

### Design system
- **Design movement:** premium editorial product design with quiet luxury and magazine-like spacing.
- **Core principles:** clarity before decoration; strong contrast; progressive disclosure; motion confirms state rather than entertaining.
- **Color philosophy:** charcoal-black provides focus, lavender signals trusted action, rose is reserved for social emphasis and errors.
- **Layout paradigm:** asymmetric app shell with a focused reading column, contextual rails, and edge-to-edge media on mobile.
- **Signature elements:** 1px hairline borders, soft radial light behind key surfaces, pill tabs with a single active underline/glow.
- **Interaction philosophy:** every control has visible state, focus, disabled, and retry feedback; no optimistic success for money/privacy operations.
- **Animation:** 160–260ms ease-out entrances, 120ms press response, opacity/translate only for route and card changes, all disabled under `prefers-reduced-motion`.
- **Typography:** existing system stack for performance, with display weight 800–950 for titles and 12–14px muted metadata.
- **Brand essence:** a composed campus-social layer for discovering people, stories, and opportunities; calm, social, trustworthy.
- **Voice:** direct and warm. “Find your people, without the noise.” “Your privacy settings travel with you.”
- **Wordmark:** existing BLINK mark retained; use the logo asset rather than recreating it in text.
- **Signature color:** lavender `#b9a7ff`, used sparingly for primary action and focus.

## Implementation scope

1. Patch shared web renderers and route handling so privacy preferences, authored alt text, reactions, comments, async freshness, errors, and return paths are honored consistently.
2. Restore reachable feed stories and mobile creation affordances; add canonical marketplace detail and server-backed order entry points where supported by existing RPCs.
3. Add safe Supabase migrations for private-account search filtering, Store pause enforcement, and storage access contracts without applying production DDL blindly.
4. Repair landing/SEO/accessibility/PWA defects that are source-confirmed and add regression coverage for the affected contracts.
5. Add a shared classic-premium motion layer with reduced-motion and data-saver safeguards.

## Project structure

- `web/app.js`: SPA routes, auth, feed, search, messaging, market and shared renderers.
- `web/*.css` / `web/mobile-redesign.js`: responsive shell, visual system and interaction polish.
- `web/discovery.js` / `web/landing-relaunch.*`: public marketing and campus discovery.
- `web/sw.js` / `web/manifest.webmanifest`: offline shell and install metadata.
- `supabase/migrations/`: versioned server-side contracts and security enforcement.
- `scripts/`: focused static and runtime-contract regression checks.

## Delivery

Run the relevant web regression suite and syntax checks, inspect the diff for secrets and accidental native-surface changes, commit to a review branch, merge/push the verified result to `main` without force-pushing, and report any backend/provider verification that remains unavailable.
