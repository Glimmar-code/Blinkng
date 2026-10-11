#!/usr/bin/env bash
# Run in one shell: android-emulator-runner invokes each YAML "script:" line
# as a separate /bin/sh -c command, so shell variables cannot span YAML lines.
set -euo pipefail
PACKAGE="com.aistudio.blink.appvtwo"
APK="$(find app/build/outputs/apk/release -name '*.apk' -type f | head -n 1)"
if [[ -z "$APK" || ! -s "$APK" ]]; then
  echo "::error::Signed release smoke APK is missing"
  exit 1
fi

echo "Installing R8-minified, signed (disposable CI key) release APK on Android 15..."
adb install -r "$APK"
adb shell pm path "$PACKAGE" | grep -F 'package:'
adb shell dumpsys package "$PACKAGE" | grep -F 'versionCode=1999000000'
echo "Release APK installed; validating actual MainActivity startup (not just installability)."

# The previous gate verified only PackageManager installation. A release could
# pass every check but crash on first launch due to R8-renamed Moshi enum fields.
adb logcat -c || true
launch_output="$(adb shell am start -W -n "$PACKAGE/com.example.MainActivity" 2>&1)" || {
  echo "$launch_output"
  echo "::error::Release MainActivity could not be started."
  exit 1
}
echo "$launch_output"
sleep 6

# am start -W can exit successfully even when onCreate() crashes immediately.
# Inspect the app process's fatal exception rather than trusting am's exit code.
runtime_errors="$(adb logcat -d -s AndroidRuntime:E || true)"
if grep -Fq "Process: $PACKAGE" <<< "$runtime_errors"; then
  echo "$runtime_errors" | tail -n 160
  echo "::error::R8-minified release APK crashed on startup. Do not publish."
  exit 1
fi
if ! adb shell pidof "$PACKAGE" | grep -Eq '[0-9]'; then
  echo "$runtime_errors" | tail -n 160
  echo "::error::Release MainActivity process did not survive the first six seconds."
  exit 1
fi

echo "PASS: Android 15 installed and launched the R8-minified release without an onCreate crash."
