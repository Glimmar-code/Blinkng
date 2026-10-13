#!/usr/bin/env bash
# Verify the EXACT signed production artifact, with production Firebase config,
# installs and opens on Android 15 before it can become a public GitHub release.
set -euo pipefail

PACKAGE="com.aistudio.blink.appvtwo"
APK="$(find app/build/outputs/apk/release -name '*.apk' -type f | head -n 1)"
if [[ -z "$APK" || ! -s "$APK" ]]; then
  echo "::error::Signed production APK missing from build outputs"
  exit 1
fi

echo "Installing the actual signed production APK on Android 15..."
adb install -r "$APK"
adb shell pm path "$PACKAGE" | grep -F 'package:'
adb logcat -c || true
launch_output="$(adb shell am start -W -n "$PACKAGE/com.example.MainActivity" 2>&1)" || {
  echo "$launch_output"
  echo "::error::Production MainActivity could not be started"
  exit 1
}
echo "$launch_output"
sleep 7

if ! adb shell pidof "$PACKAGE" | grep -Eq '[0-9]'; then
  adb logcat -d -s AndroidRuntime:E libc:F DEBUG:E | tail -n 180 || true
  echo "::error::Signed production APK did not survive Android 15 startup; refusing release"
  exit 1
fi
errors="$(adb logcat -d -s AndroidRuntime:E || true)"
if grep -Fq "Process: $PACKAGE" <<< "$errors"; then
  echo "$errors" | tail -n 180
  echo "::error::Production APK crashed on Android 15; refusing release"
  exit 1
fi

echo "PASS: Actual signed production APK installed and opened on Android 15."
