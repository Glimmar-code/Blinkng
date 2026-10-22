# BLINK iOS — Phase 4: Market and Connect

**Development only.** This branch is based on the passing Phase 3 candidate, not on production `main`. PRs #416 → #417 → #419 must be reviewed/promoted in order before Phase 4 can be retargeted to Testlab. Do not bypass test or deployment gates.

## Delivered iOS functionality

- Market tab fetches actual approved/visible listings from BLINK's existing `get_ranked_market_page` RPC with search, real image URLs, price, condition, location and seller username when returned.
- Seller contact opens the **existing BLINK web messages route**; native seller chat has **not** been built yet.
- Connect tab fetches actual `get_ranked_connect_opportunities` RPC with All, Roommates, Mentors, Reading mates, Housing filtering.
- Sends a connection request only after explicit user tap, via the existing `send_connection_request` RPC. Buttons are disabled on successful server response, when already pending, when receiver ID is invalid, or for own account ID.
- Every request uses the Supabase **user** JWT from secure Phase 2 session storage, not a service-role secret or a client-trusted role.
- Server ranking, RLS, blocked users, approved sellers, expired verification and purchase permissions remain entirely server-authoritative.
- Proper empty/loading/error handling. No fake listings, users, houses, roommate matches or transaction history.
- Mock network XCTest checks for RPC names, expected filters, authentication header, listing formats, Connect mode, connection request safety, handling 401 and safe media URLs.

## Still not implemented / NOT verified

- **Market posting, seller registration, seller approval, offers, disputes, private messaging and checkout** are not shipped on iOS. Never enable unaudited seller posting or Paystack for iOS digital purchases.
- Advanced search category filters and cursor pagination for Market/Connect remain pending. This implementation intentionally requests the first 40 results matching the current web app's initial-page contract.
- Native profile links and inbox will be delivered later. Website "Message seller" requires the user to authenticate on web if not already signed in.
- Verify with multiple eligible and blocked/expired/unverified accounts against a safe backend environment; test real photo URLs, large prices, Unicode, privacy, request duplication and error/status behavior.
- Confirm `get_ranked_market_page`, `get_ranked_connect_opportunities` and `send_connection_request` are **deployed to the intended Supabase environment** and security-reviewed before making release claims. These checks are not performed by GitHub static CI.
- Device testing, App Store policies and Apple Developer enrollment are still required for any public iOS launch.

## Cross-platform parity

The iPhone client reads BLINK's existing server RPC contracts from the Android/Windows/web stack. No Android, Windows, Supabase schema, website or production features are changed by this phase. This is not full parity yet.

## Verification

The iOS macOS runner must pass Xcode Simulator build + isolated XCTest; Android and Windows quality gates must also pass on the exact feature SHA. Keep this branch in review when any gate is pending or failing.
