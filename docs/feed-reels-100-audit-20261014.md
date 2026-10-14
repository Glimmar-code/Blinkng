# BLINK Feed + Reels: 100-item quality gate

**Source:** User-requested professional one-by-one assessment (2026-10-14)  
**Staging:** `Testlab-feed-reels-ux-20261014` / PR #275  
**Production:** Do not merge unless automated checks AND real-device smoke tests pass.

These are **100 hypotheses/acceptance checks**, **not** 100 independently reproduced defects. The status "Patch staged" means relevant code changed, NOT that the item has been validated on real hardware.

## Merge acceptance
1. Android Kotlin compilation, unit tests, lint and release smoke all pass on the exact PR head.
2. Windows build/parity and Supabase safety checks pass; no new migrations or privacy shortcuts.
3. Redmi 15C / Android 15 check: images and verification, follow/auth, caption+navigation overlap, paused/restarted videos, touch seeking, background audio, slow/offline conditions.
4. Regression-free Feed & Reels in black/white and light/dark modes; preserve verified/coin/rank/ads business logic.
5. Only individually verified rows may be marked PASS. Failed/unverified rows stay OPEN; no broad claim that all 100 are fixed.

| ID | Domain | Problem to reproduce | Status |
|---:|---|---|---|
| 1 | Feed layout | Avatar looks blurry | Open: audit required |
| 2 | Feed layout | Long author name clips other controls | Patch staged; verify |
| 3 | Feed layout | Incorrect verification colour | Patch staged; verify |
| 4 | Feed layout | Timestamp misalignment | Patch staged; verify |
| 5 | Feed layout | Excessive post spacing | Open: audit required |
| 6 | Feed layout | Inconsistent image height | Patch staged; verify |
| 7 | Feed layout | Extreme portrait image letterboxing | Patch staged; verify |
| 8 | Feed layout | Broken multiple-image carousel | Patch staged; verify |
| 9 | Feed layout | Carousel counter obstructs picture | Open: audit required |
| 10 | Feed layout | Stretched profile avatar | Patch staged; verify |
| 11 | Feed layout | Post text overflows | Open: audit required |
| 12 | Feed layout | Hashtag wraps incorrectly | Open: audit required |
| 13 | Feed layout | Mention styling missing | Open: audit required |
| 14 | Feed layout | Links not recognisable | Open: audit required |
| 15 | Feed layout | Text-only post too tall | Open: audit required |
| 16 | Feed layout | Uneven action icon spacing | Open: audit required |
| 17 | Feed layout | Dark surfaces inconsistent | Patch staged; verify |
| 18 | Feed layout | Light contrast insufficient | Patch staged; verify |
| 19 | Feed layout | Post borders too thick | Open: audit required |
| 20 | Feed layout | Typography differs between cards | Patch staged; verify |
| 21 | Feed interactions | Duplicate Like requests | Open: audit required |
| 22 | Feed interactions | Like count desynchronises | Open: audit required |
| 23 | Feed interactions | Bookmark resets after refresh | Open: audit required |
| 24 | Feed interactions | Follow not updating | Patch staged; verify |
| 25 | Feed interactions | Unfollow requires repeated attempts | Open: audit required |
| 26 | Feed interactions | Comment count stale | Open: audit required |
| 27 | Feed interactions | Deleted comments remain | Open: audit required |
| 28 | Feed interactions | Share link incorrect | Open: audit required |
| 29 | Feed interactions | Duplicate repost entry | Open: audit required |
| 30 | Feed interactions | Post menu mispositioned | Open: audit required |
| 31 | Feed interactions | Report submission fails | Open: audit required |
| 32 | Feed interactions | Blocked creator persists in feed | Open: audit required |
| 33 | Feed interactions | Muted creator persists | Open: audit required |
| 34 | Feed interactions | Returning from post resets scroll | Open: audit required |
| 35 | Feed interactions | Returning from comments reloads feed | Open: audit required |
| 36 | Feed interactions | Refresh omits new posts | Open: audit required |
| 37 | Feed interactions | Following feed includes unrelated authors | Open: audit required |
| 38 | Feed interactions | Pagination duplicates posts | Open: audit required |
| 39 | Feed interactions | Bookmarks differ across devices | Open: audit required |
| 40 | Feed interactions | Private posts visible to wrong audience | Open: audit required |
| 41 | Feed performance | Long scrolling becomes janky | Open: audit required |
| 42 | Feed performance | Images redownload on scroll back | Open: audit required |
| 43 | Feed performance | Image decoder memory increases | Open: audit required |
| 44 | Feed performance | Fast scroll flickers | Open: audit required |
| 45 | Feed performance | Skeleton remains too long | Open: audit required |
| 46 | Feed performance | Slow network leaves blank post | Open: audit required |
| 47 | Feed performance | Network failure lacks retry | Open: audit required |
| 48 | Feed performance | Offline cached posts disappear | Open: audit required |
| 49 | Feed performance | Reconnection leaves stale data | Open: audit required |
| 50 | Feed performance | Background requests drain battery | Open: audit required |
| 51 | Feed performance | New posts not ranked promptly | Open: audit required |
| 52 | Feed performance | Relevant older posts are absent | Open: audit required |
| 53 | Feed performance | Recommendations repeat creators | Open: audit required |
| 54 | Feed performance | Hashtag ranking ignores relevance | Open: audit required |
| 55 | Feed performance | Following suggestions stale | Open: audit required |
| 56 | Feed performance | Duplicate impression credit | Open: audit required |
| 57 | Feed performance | View count disagreement | Open: audit required |
| 58 | Feed performance | Sponsored posts inserted too often | Open: audit required |
| 59 | Feed performance | Unfilled ads leave blank content | Open: audit required |
| 60 | Feed performance | Feed pagination stops early | Open: audit required |
| 61 | Reels layout | Caption overlaps engagement rail | Patch staged; verify |
| 62 | Reels layout | Caption covers too much video | Patch staged; verify |
| 63 | Reels layout | Expanded caption blocks navigation | Open: audit required |
| 64 | Reels layout | Creator name overlaps Follow | Patch staged; verify |
| 65 | Reels layout | Badge shifts on long names | Patch staged; verify |
| 66 | Reels layout | Caption invisible over bright clip | Patch staged; verify |
| 67 | Reels layout | Icons blend into video | Patch staged; verify |
| 68 | Reels layout | Action icons have inconsistent sizes | Patch staged; verify |
| 69 | Reels layout | Counters jump in width | Patch staged; verify |
| 70 | Reels layout | Like animation delayed | Open: audit required |
| 71 | Reels layout | Comment sheet blocks navigation | Open: audit required |
| 72 | Reels layout | Share sheet opens behind overlay | Open: audit required |
| 73 | Reels layout | Saved icon stale | Open: audit required |
| 74 | Reels layout | Video stretches to wrong ratio | Patch staged; verify |
| 75 | Reels layout | Landscape has excessive dead space | Open: audit required |
| 76 | Reels layout | Low resolution preview | Open: audit required |
| 77 | Reels layout | Sponsored badge obstructs subject | Open: audit required |
| 78 | Reels layout | Duplicate creator labels | Open: audit required |
| 79 | Reels layout | Seek rail invisible on bright video | Patch staged; verify |
| 80 | Reels layout | Controls clip on rotation | Open: audit required |
| 81 | Reels gestures and playback | Single tap interpreted as double tap | Patch staged; verify |
| 82 | Reels gestures and playback | Long press triggers page navigation | Open: audit required |
| 83 | Reels gestures and playback | 2x speed sticks after release | Patch staged; verify |
| 84 | Reels gestures and playback | Long press misses near edges | Patch staged; verify |
| 85 | Reels gestures and playback | Drag seek switches reel | Patch staged; verify |
| 86 | Reels gestures and playback | Seek picks wrong timestamp | Patch staged; verify |
| 87 | Reels gestures and playback | Video resets after comments | Open: audit required |
| 88 | Reels gestures and playback | Returns paused from overlay incorrectly | Open: audit required |
| 89 | Reels gestures and playback | Two players audible at once | Patch staged; verify |
| 90 | Reels gestures and playback | Background reel remains audible | Patch staged; verify |
| 91 | Reels gestures and playback | Sound ignores device controls | Open: audit required |
| 92 | Reels gestures and playback | Bluetooth audio sync lag | Open: audit required |
| 93 | Reels gestures and playback | Network handover freezes playback | Open: audit required |
| 94 | Reels gestures and playback | Video retries endlessly | Patch staged; verify |
| 95 | Reels gestures and playback | Fast swipe uses too many decoders | Patch staged; verify |
| 96 | Reels gestures and playback | Cellular preloading uses excessive data | Open: audit required |
| 97 | Reels gestures and playback | Rotate restarts video | Open: audit required |
| 98 | Reels gestures and playback | Screen lock leaves reel playing | Patch staged; verify |
| 99 | Reels gestures and playback | Buffering progress moves incorrectly | Patch staged; verify |
| 100 | Reels gestures and playback | Long reel browsing crashes | Open: audit required |

## Group-specific acceptance

### Feed layout: #1–20
Check long names, landscape and tall pictures on a 360dp Android phone and tablet, in light and dark mode.

### Feed interactions: #21–40
Verify with two signed-in accounts, account switching, network failures and database RLS rules.

### Feed performance: #41–60
Profile 100-post scroll under slow mobile data and offline/reconnect; compare memory, frame rate and queries.

### Reels layout: #61–80
Play portrait and landscape clips with captions, follow, sponsored badges and expanded overlays.

### Reels gestures and playback: #81–100
Check a real Android 15 handset: tap/double-tap/hold/seek, app background/lock, rotation, slow network, audio routes and 10-minute swipe run.

## Verified source-code findings before this PR
- Original X-style Home card explicitly suppressed the Follow component with `!xFeedStyle`.
- Feed forced a 4:5 crop, then an initial patch used Fit but retained the 4:5 frame; new decoding logic measures full-image aspect ratio after load and avoids relayout during active scroll.
- Reel rail previously contained extra Views, Sound, More and a duplicate avatar disc. Four engagement actions are now separated from independent sound/options.
- Progress was pinned to the very bottom behind the persistent navigation bar, with tap-only seeking and a narrow hit target.
- ExoPlayer was controlled by page visibility but did not respond to app START/STOP lifecycle.

## Implementation slices
- **Slice A (PR #275 initial):** Author name/badge and compact Follow, image fit, 4-action reel rail, pause/hold-2×, seek control above navigation.
- **Slice B (this revision):** Public author UUID fallback, true ratio measuring and scrolling deferment, lifecycle pause/resume, safe seek metadata handling, transport policy regression tests.
- **Future slices:** Only implement another group after reproducing its issue; add a narrow unit/instrumentation test and track results here.

## Safety
- Never fabricate verification/follow state; use actual authorised server data.
- Never make private posts visible by weakening row-level access controls.
- Never credit views, clicks, coins or rewards from a client-only assumption.
- Never merge an unrelated historical Testlab branch wholesale into main.


## Focused identity and interaction hardening (follow-up, 2026-10-14)
- A server-provided post creator UUID now takes precedence over a username and is never overridden by a same-name profile.
- Feed and Reels use the same verified badge policy: when a current profile is loaded, its current NONE/BLUE/GOLD status is authoritative, including when a badge has been revoked.
- Profile avatars shown in Feed cards now prefer the resolved profile's up-to-date avatar URL instead of the stale post snapshot.
- Following state uses the resolved author UUID even if the profile directory is incomplete; concurrent Follow/Unfollow taps are disabled while a server operation is in flight.
- Video PlayerView no longer holds the screen awake when explicitly paused or backgrounded.
- Added identity regression tests covering duplicate names, mismatched IDs, badge revocation, avatar and Follow fallback.
- All changes are **staged** until the new commit's CI and real-device review. Passing earlier CI does not validate a new commit.
