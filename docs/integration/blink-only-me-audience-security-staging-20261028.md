**Production safety update (28 October 2026):** The server now rejects `Only me` and `private` audiences through the validated `blink_no_unprotected_private_audience` check constraint. See deployed migration `20261028200938_blink_block_unprotected_private_audiences_20261028.sql`. This **prevents** silent exposure from newly submitted private posts; it does **not** enable private posting. Do not enable until all secure visibility and storage conditions below pass.

# BLINK "Only me" posts — security rollout design (Testlab only)

**Status: DO NOT ENABLE OR DEPLOY.** A visually selected "Only me" audience is not equivalent to privacy. Until the backend and storage pass the checks below, the Android reference composer intentionally shows a disabled "Only me" option. Drafts remain local/account-scoped.

## Existing production observations (read-only audit, 28 Oct 2026)

- `public.feed_posts.audience` is a text column. The currently visible permissive SELECT policy `feed_posts_select_active` admits every active post **or** the author, without checking audience. Setting `audience='Only me'` on an active post alone would expose it.
- The `feed_posts` model includes public-facing image and video URLs, which must be considered discoverable if the underlying media bucket is public or the URLs are reused in unrestricted routes.
- Server-ranked Feed/Reels/Search, profile content, direct post lookup, shares, notifications, embedded previews, and Windows/web APIs must be independently checked; `SECURITY DEFINER` functions can bypass RLS.

## Required implementation gates

1. Define the definitive audience matrix for `Everyone`, `Campus`, `Followers`, and `Only me` across profiles and content. Audit **all** SELECT policies, permissive vs restrictive policy combinations, and every RPC/view with access to `feed_posts`.
2. Enforce author-only `Only me` visibility at the SQL boundary for every active and archived post, including storage access and side tables (polls, options, votes, comments, likes, reports, push, content recommendation and metrics).
3. Place private media in owner-restricted storage with short-lived signed links. Never reuse a public object URL for an `Only me` post. Check automatic cover image and media derivatives as well as videos, audio and album photos.
4. Reject attempts to change author or privacy status using untrusted client-supplied roles. Server-authoritative ownership must use `auth.uid()` and consistent RLS/GRANT on newly created objects.
5. Provide a rollback-safe, idempotent migration only after isolated PostgreSQL-17 auth/anon/owner/nonowner/blocked role tests. Do not apply from an Android build or against the live project first.
6. Implement matching Android and Windows creation, edit, profile visibility and deletion behavior. Verify old APKs/web clients cannot read or leak private posts.
7. Run signed-in end-to-end tests across two real users: only the author can fetch the post by id, feed, profile, tag search, story/channel forwarding and direct media URL. A changed audience must revoke old shares/signed URLs as feasible.
8. Deploy only when a safe no-fee environment and all independent checks exist. Confirm API behavior after deployment before enabling the option via a server-authoritative feature flag.

## Rollback guidance

Disable the `Only me` selection first (fail closed), then revert migration with a verified rollback plan. Do not change an existing private post to public as a rollback shortcut. Retain ownership and row history.
