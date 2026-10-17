#!/usr/bin/env bash
# Test the exact published production APK, not a disposable-key CI build.
# android-emulator-runner executes each YAML script line separately, so keep
# all stateful shell logic in this single file.
set -euo pipefail
exec > >(tee install-output.txt) 2>&1

package="com.aistudio.blink.appvtwo"
current="production/current/Blink-latest.apk"
previous="production/previous/Blink-latest.apk"

echo "Android OS: $(adb shell getprop ro.build.version.release)"
echo "Android ABIs: $(adb shell getprop ro.product.cpu.abilist)"

install_checked() {
  local label="$1" path="$2" expected_code="$3" output
  echo "=== $label ==="
  if ! output="$(adb install -r "$path" 2>&1)"; then
    echo "$output"
    echo "::error::Android PackageManager rejected published BLINK APK during $label."
    adb logcat -d -s PackageManager:* PackageInstaller:* installd:* | tail -n 150 || true
    exit 1
  fi
  echo "$output"
  grep -Fx "Success" <<< "$output"
  adb shell pm path "$package" | grep -F "package:"
  adb shell dumpsys package "$package" | grep -F "versionCode=$expected_code"
}

install_checked "Clean install of published v1.0.298604769" "$current" "298604769"
echo "=== Uninstall to prepare real upgrade test ==="
adb uninstall "$package"
install_checked "Install previous published v1.0.298604418" "$previous" "298604418"
install_checked "In-place upgrade to published v1.0.298604769" "$current" "298604769"
echo "PASS: Exact published production APK clean-install and upgrade work on Android 15."
