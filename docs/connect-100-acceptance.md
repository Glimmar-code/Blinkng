# BLINK Connect — 100-point premium upgrade acceptance backlog

This is a tracked implementation program, not a claim that all 100 items have been delivered. Large product changes must be split into verified Testlab PRs; do not merge unfinished functionality into production.

## Implementation slices and acceptance gates

| Items | Area | Required acceptance evidence |
| --- | --- | --- |
| 1–10 | Design system, headers, cards, spacing, themes | Android and Windows screenshots at phone, tablet, desktop and large-text sizes; accessibility contrast |
| 11–20 | Navigation, gestures, tabs, search, saved UI state | Back/forward, swipe threshold, deep links, process recreation and keyboard tests |
| 21–30 | Live people discovery, profiles, follows, duplicate control | Cross-account visibility/RLS, real identities, avatar loading, no fabricated presence |
| 31–40 | Match ranking, fairness, visibility, opt-outs | Deterministic shared tests, blocked/privacy exclusions enforced server-side, fairness metrics |
| 41–50 | Spin and personalized matchmaking | RPC idempotency, consent, correct wallet debit, animation lifecycle, cancelled and failed requests |
| 51–60 | Roommates, housing, agent applications | Filters, listing lifecycle, scam reports, approved agents, safe storage and RLS |
| 61–70 | Mentor, reading mate, game challenges | Application state machine, duplicate prevention, integrity of game outcomes, parity |
| 71–80 | Inbox, realtime, notifications, request feedback | Idempotent acceptance/rejection, background/dedupe, navigation and offline recovery |
| 81–90 | Controls, abuse reporting, moderation, privacy | RLS/abuse regression suite, block tests, retention, data deletion and admin auditability |
| 91–100 | Offline/cache, pagination, performance, tests | 60 fps target measured on representative devices, startup reliability, Android and desktop build |

## Current foundation work (in Testlab, not production)

- Shared ranked discovery and identical Android/Windows sorting.
- Explanation text for suggestions; stable ID handling.
- Android preserves successful/cached Connect sections during partial outages and reports total outage.
- Windows retains old results, offers Retry, and labels stale presence.
- Smart Match no longer runs an infinite animated loop while idle and respects reduced-motion settings.
- Unit tests for pure ranking rules and platform parity note.

## Outstanding work and release policy

All other acceptance conditions above remain open until code, automated tests, backend staging checks and real client validation demonstrate they work. Never label a feature complete solely because it appears in a design. Any Supabase migrations must be previewed and checked for RLS/rollback before production. Require Android and Windows parity for user-facing changes, and use Testlab-to-main promotion only with green release gates.
