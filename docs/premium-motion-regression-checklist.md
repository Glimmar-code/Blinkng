# Premium motion acceptance checklist (PR #637)

No screen counts, frame-rate claims, or release outcomes should be reported as complete
until they have been exercised on the latest PR head in Testlab.

## Automated gates

- GitHub Android quality: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`.
- Android native APK build, splash visual gate, runtime smoke and release smoke.
- Windows parity/build, backend safety, secret scan and release integrity.
- Compose UI regression: five bottom destinations, rapid tab changes, Reel reference chrome,
  and newest animated-state transition test.
- Verify GitHub Actions checks against the **latest** PR SHA; old green runs do not count.

## Android 15 / Redmi 15C smoke matrix

1. Fresh install cold launch: Beyond LINK -> BLINK -> valid destination, no black/empty frame.
2. Sign in, sign up, validation errors, keyboard, reset-password and back.
3. Home -> Reels -> Market -> Connect -> Messages: fling rapidly; no double page or blank seam.
4. Feed: multiple post types, like/save, scrolling back, profile, comments and creator.
5. Reels: pause/play, full-screen expand/collapse, feed return, captions and seek.
6. Stories and channel photos: hold/zoom, swipe, viewer dismissal and media release.
7. Chat: inbox row press, open/close, keyboard resizing, reply swipe, send/receive, attachments.
8. Native calls: start, incoming, answer/decline, microphone/speaker/camera, hang up.
9. Profile, Search, Connect, Channels, Market listing and seller details.
10. Wallet, Verification, Boost, Drops: compare visible amount/status against backend
    response; never animate to a success before confirmation.
11. Menu, Items, study game, profile editing, admin actions and settings.
12. Repeat with animator duration OFF, font scale 1.5+, low memory/network, dark/light.

## Performance

- Capture frame timing and jank around pager flings and Reel fullscreen transitions.
- Check no extra video players on page switches and no additional background fetches.
- Check frame timing with high refresh mode and power-save/reduced-motion mode.
- Build sizes and network traffic must not increase due to decorative assets.

**Release rule:** Do not promote a draft PR or merge while required checks are failing,
in progress, cancelled, skipped without documented rationale, or not run.
Physical-device performance and payments/calls must be explicitly recorded, not inferred
from a successful compile.
