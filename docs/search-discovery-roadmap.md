# Blink Search & Discovery roadmap

This document tracks the premium Blink search migration so risky discovery work can be validated in Testlab before production promotion.

## Phase 1 — Search experience foundation

Implemented in `feature/premium-search-discovery`:

- premium dark/light search shell using Blink design tokens
- rotating search prompts
- Android speech-to-text query entry
- clear search action
- 280 ms debounced live backend search
- partial username and full-name matching
- profile metadata matching across university, faculty, department, course, level, city and bio
- post/reel matching across author, caption/text, hashtag, faculty, category, location and alt text
- local typo correction suggestion using edit distance
- All / People / Posts / Reels / Hashtags / Campus category chips with live result counts
- remembered last category and sort choice on Android
- recent searches with timestamps, individual removal, pin/unpin, clear-all confirmation, local privacy toggle and 30-day expiry
- rich autocomplete for people, hashtags and campus metadata
- relevance, recency, view, like, comment, share and trend sorting where supported by the current models
- verified/VIP/university/faculty/department/level/media filters on Android
- verified/university filtering and major sort modes on Windows
- premium people cards, two-column reel results, existing interactive post cards, trending discovery, skeletons and no-result guidance
- Android ↔ Windows parity entry

## Phase 2A — Cross-platform discovery collections

Implemented in `feature/premium-search-phase2-collections` and staged for Testlab validation:

- dedicated Search / Trending / Places discovery collections
- 240 ms Android and 180 ms Windows collection transitions
- Android back navigation returns from Trending/Places to Search before leaving the screen
- trend ranking from real post/reel engagement signals rather than placeholder percentages
- trending hashtag/topic aggregation from live Blink content
- rising post/reel collection ordered by views, likes, comments and shares; Android additionally uses reposts already present in its model
- Android trending creators ranked from current profile follower/points/online signals
- Places discovery derived from real location-bearing data already available on each client
- Android Places uses post locations and profile city/state metadata
- Windows Places uses active Marketplace and Connect listing locations already exposed by the desktop data client
- compact K/M metric formatting shared conceptually across both experiences
- corrected Windows blank-search discovery so trending topics use the loaded discovery feed instead of an empty search result set
- no fake Saved collection on Windows and no Android-only Saved collection: Saved remains staged until a truthful cross-platform bookmark contract exists

## Phase 2B — Backend search expansion

Requires explicit backend/data-contract work before the UI can truthfully expose these categories:

- Communities / groups search
- Events search
- Marketplace search inside global Search
- Pages / brands search
- Saved-content search
- Following-only search
- mutual-connection filtering
- server-synced search history and filter presets
- cross-device recently viewed profiles/posts/hashtags/communities
- follower-range and geographic distance filters
- fastest-growing-account ranking
- dedicated trend velocity / percentage-change data
- search analytics with privacy controls

## Phase 3 — Media intelligence and navigation polish

Requires dedicated platform/media work:

- camera/image similarity search
- matched-moment reel playback based on transcript/caption timing
- shared-element/hero transitions into every destination
- muted reel auto-preview policy with lifecycle/data-saver controls
- advanced keyboard selection for autocomplete on desktop
- full search-result pagination contracts per category

## Safety rule

Do not merge these changes directly to `main`. Validate Android, Windows parity/build, and Supabase safety gates in Testlab first. Backend phases must use staging/preview data contracts before any production deployment.
