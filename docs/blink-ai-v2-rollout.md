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
