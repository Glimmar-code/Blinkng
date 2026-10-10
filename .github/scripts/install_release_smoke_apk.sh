#!/usr/bin/env bash
# Run in one shell: android-emulator-runner invokes each YAML "script:" line
# as a separate /bin/sh -c command, so shell variables cannot span YAML lines.
set -euo pipefail
APK="$(find app/build/outputs/apk/release -name '*.apk' -type f | head -n 1)"
if [[ -z "$APK" || ! -s "$APK" ]]; then
  echo "::error::Signed release smoke APK is missing"
  exit 1
fi
echo "Installing signed (disposable CI key) release APK on Android 15..."
adb install -r "$APK"
adb shell pm path com.aistudio.blink.appvtwo | grep -F 'package:'
adb shell dumpsys package com.aistudio.blink.appvtwo | grep -F 'versionCode=1999000000'
echo "Android 15 Package Manager installed signed release smoke APK successfully."
