# Chat and Create Post reference integration

The user requested implementation of the supplied Chat and ten-panel Create Post designs. The PDF was treated as reference context; its unrelated Contest completion and production release mission were not invoked. The user's subsequent instruction to implement authorized the local code changes.

Local branch: `Testlab/chat-create-post-reference-20261029`, based on main `3f0610ef6b6112e43e63d1d61998e52e6ba5ef41`. Integrated histories:

- Create Post PR #639: `96f0f2d05c83698a0db3ade66fc1fc5fc723c87f`.
- Chat PR #644: `0300879e20a4897b3a086013607d59e15ecdf82a`.

No remote PR status, CI success, APK release or deployment is implied by these fetched commit IDs. Original PRs were not closed, updated or merged remotely.

## Reference mapping

| Reference | Actual implementation |
| --- | --- |
| Chat thread | `PremiumMessagesScreen.kt`: `ChatConversationView`, charcoal header/pin/composer and message bubbles; existing album, native poll, voice, PDF and video components |
| 1. Create options | `BlinkCreatePostLanding` |
| 2. Text composer | `CreatePostSheet`, `BlinkReferenceComposerToolbar` |
| 3. Mentions | `BlinkPostMentionSuggestions` with real search/loading/empty states |
| 4. Gallery | Permission-safe native picker followed by `BlinkReferenceMediaReview`; selected items/order only, no fabricated device gallery |
| 5. Video editor | Existing `PremiumReelEditor` and `BlinkReelClipSequence` with native Media3 actions |
| 6. Image editor | Existing `BlinkUniversalPhotoEditor` |
| 7. Poll | Validated single-answer poll editor, duration and expiry guard |
| 8. Details | `BlinkReferencePostDetails` |
| 9. Preview | `PostPreviewDialog` with the current profile and actual selected media |
| 10. Post options | `BlinkCreatePostPublishOptions`, existing submission pipeline and truthful pre-publication sharing |
| Windows | `DesktopCreatePostDialog`, `DesktopFeedPollCard`, shared posting/style policy; Classic composer remains accessible |

## Validation

Validated with JDK 17.0.21, Gradle 9.3.1 for Android, Gradle 8.12.1 for the separate desktop project and official Android SDK 37. Gradle/JDK/SDK downloads retained their official checksum and TLS validation. These tools were installed in the current machine; environment draft settings were not changed.

Initial Android compilation failed because AGP 9.1.1 requires Gradle 9.3.1. After the pinned distribution correction and SDK installation, `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` passed, including the existing instrumentation fixtures. The two invalid Compose imports were removed; the corresponding assertions/provider methods are members.

Desktop tests exercise the real request serializer against an in-process HTTP ledger. This verifies acknowledgement recovery and completion of partial poll inserts; it is not hosted Supabase integration. Draft tests use an injected test codec for persistence, isolation and failed-protection behavior; Windows DPAPI itself needs Windows validation.

The standard Supabase test chain includes `posting-guards.test.mjs`, which runs the imported SQL fixture and actual migrations/assertions in isolated PGlite. The private-audience `ADD CONSTRAINT` migration is intentionally one-time: manual replay raises duplicate-object `42710`; the test confirms the guard remains intact. No recorded production migration was rewritten to make it replayable.

Executed checks:

- Android application and instrumentation Kotlin compilation: passed.
- Android `:app:lintDebug`: passed. Final combined Gradle run completed successfully in 10m 47s (110 actionable tasks).
- Focused Android unit/layout tests: 23 passed, zero skipped. Includes publishing rules, poll expiry, back gestures, theme modes and the new real Compose capture/interaction fixtures. The final charcoal-glow refinement was also revalidated, and both captures were inspected.
- Android `:app:assembleDebug`: passed; generated a 90,777,333-byte debug APK. APK Signature Scheme v2 verification passed. This is a development build with placeholder Firebase configuration, not a production release.
- Windows/desktop `test`: 20 passed, zero skipped. Includes existing desktop tests and new draft isolation/protection failure, publication-response loss, partial-poll recovery, storage path and authorization-failure tests.
- Full `npm test --prefix supabase/tests`: passed, including the new isolated posting guard tests.
- `scripts/check_windows_parity.py`: passed; this structural check does not prove native feature/backend parity.
- Supabase safety gate against main `3f0610ef`: passed.
- `git diff --check`: passed.

The first Robolectric invocation could not fetch its Android instrumented JAR because the test JVM did not inherit Gradle's proxy properties. A local Gradle init script passed the configured proxy, system CA trust store and official Maven repository to the test JVM. The rerun passed without disabling TLS or artifact checksums. This machine-specific helper is outside the checkout.

Actual isolated Compose captures (Robolectric, API 35, Pixel 8 qualifiers):

- `app/build/outputs/reference-design/create-post-landing-robolectric.png`
- `app/build/outputs/reference-design/chat-charcoal-robolectric.png`

The initial capture revealed an inherited blue background glow. The final code removes it from the dark conversation and uses outlined call/emoji affordances. The capture fixtures verify real callbacks and screen elements, not the illustrative people/media in the reference.

## Visual and release limits

The phone frames, illustrative gallery photos, fake accounts and iPhone status bars in the reference are not application content. Production continues to use the current account, selected files and server-authoritative data.

Robolectric captures, when successful, render real Android Compose components with isolated synthetic fixtures. They do not replace instrumentation screenshots on Android 15, two-account delivery/receipt tests, Redmi 15C performance testing or pixel comparison against original reference image files. The existing emulator screenshot-export workflow retains both feature capture sets.

This Linux machine has no `/dev/kvm`, attached Android handset or Windows installer host. Full device visual comparison and Windows EXE/MSI packaging are not established here. The original image files are not present in the repository for a reproducible pixel diff. No exact visual match is claimed.

Remaining Windows capability gaps are native photo editing/markup and multi-clip/video effects/export. Desktop accepts already edited photos/videos; these gaps are not described as completed parity. Hosted storage, timed polls, DPAPI and two-account messaging still require safe integration validation. Private posting remains disabled. No new production credentials or paid infrastructure were requested.

Do not promote this branch to production until these release gates and the applicable repository checks have passed. Main and production backend remain unchanged.

## GitHub application follow-up

The owner subsequently authorized fixing remaining issues and applying the work to GitHub. The follow-up fixes canonicalize poll questions/answers before writing them (including trimming/removing empty optional answers), prevent text-post backgrounds leaking into photo/video previews, and make Android quality/runtime and Windows packaging workflows run on `Testlab/**` pushes. The separate `Testlab` branch does not exist remotely; the integration remains isolated on its existing Testlab-prefixed branch.

The follow-up desktop suite passed 21 tests, including the new real-client normalization regression. Workflow YAML parsing and branch-pattern checks passed. Earlier Android and isolated Supabase results above remain historical evidence for their unchanged application/backend inputs; new GitHub CI must establish the remote candidate's checks.

GitHub API access is currently blocked by the environment proxy with HTTP CONNECT 403. The custom network draft now adds only `api.github.com`, preserving the package-manager presets and other settings. Saving that draft did not apply or publish it. PR creation, CI inspection and any promotion remain pending until API access works and the required checks pass. Original PRs and main must be preserved. Windows native editing, physical-device comparison and hosted integration gaps listed above remain unresolved; this follow-up does not claim all feature/release gates are complete.
