# ONLY used by the R8-minified, debug-signed rtcSmoke CI target build.
# Instrumentation runs in the app process but is compiled separately. R8
# cannot see all of AndroidJUnitRunner's cross-APK runtime dependencies.
# Keep the tracing and Kotlin runtime used by the AndroidX test harness to
# prevent runner crashes before WebRTC's JNI_OnLoad can be exercised.
# These rules are NOT part of the signed production release.
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }
