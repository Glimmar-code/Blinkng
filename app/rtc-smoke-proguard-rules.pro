# ONLY used by the debug-signed, R8-minified rtcSmoke CI variant.
# Instrumentation starts AndroidJUnitRunner in the app process. Its tracing
# calls reference the target APK's class loader. R8 cannot see this test-only
# reference and otherwise removes androidx.tracing.Trace, crashing before
# the WebRTC JNI test starts. Keep this class in the target smoke APK only;
# the production release retains its normal optimization rules.
-keep class androidx.tracing.Trace { *; }
