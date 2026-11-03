# Blink AI v2 rollout

This rollout keeps `blink-ai` as the rollback path while Android prefers `blink-ai-v2` and falls back automatically if v2 is unavailable.

## Implemented in this release

- Fast, Deep, Code, Research, Write and Study modes
- Short, Medium and Long response controls
- Balanced, Concise, Friendly and Professional tone controls
- Custom instructions with server-side length and control-character limits
- Web-search toggle using the current Gemini Interactions API revision
- Personal Blink context toggle with private DMs excluded
- Temporary chats that skip saved AI history/context
- Multiple image attachments (up to 6) plus one voice note
- Stop/cancel generation and retry last request
- Voice dictation and response read-aloud on Android
- Copy and share response actions
- Saved conversation history browsing, search, restore, rename and delete
- Model, mode, web-use and latency response metadata
- Server-side Gemini model fallback and retry for rate-limit/server failures
- Existing confirmation-gated profile updates preserved
- Existing `blink-ai` function preserved as a rollback/fallback path

## Promotion gate

Run the Android pull-request quality gate before merging to `main`. Deploy `blink-ai-v2` separately with JWT verification enabled so the existing `blink-ai` endpoint remains untouched.

## 2026-09-22 experience redesign — Testlab

This phase rebuilds the BLINK AI presentation around the supplied dark mobile reference while keeping BLINK's own identity and the existing secure AI backend.

- Android now uses a full-screen Welcome → Explore → Chat flow instead of presenting the primary AI experience as a bottom sheet.
- Windows receives the same product journey with a desktop-native dialog rather than stretching the phone layout.
- Welcome includes the BLINK mark, a restrained voice-orb treatment, direct Start chatting and Explore entry points.
- Explore is driven by one shared catalog used by Android and Windows. Current categories are Coding, Education, Ideas, Information, Writing, Research, Campus, Mathematics, Career, Smart Shopping, BLINK Help and Deep Thinking.
- Every Explore category maps to one of the existing server-supported modes: fast, deep, code, research, write or study.
- Selecting a category starts a fresh conversation, selects the matching mode and pre-fills an editable starter prompt.
- Chat keeps saved history, settings, web/context controls, temporary chat, attachments, retry/stop, copy/share, Android dictation/read-aloud and confirmation-gated BLINK profile actions.
- No new Supabase schema or production backend deployment is required for this presentation phase.
- Token-by-token response streaming is not introduced in this phase; the existing request/reply lifecycle remains intact until a separately tested streaming transport is ready.

### Promotion gate for this redesign

Keep this change on Testlab until the Android unit/lint/build checks and Windows desktop compile/package checks pass. Fix failures in Testlab and promote to `main` only after those gates are green.

