#!/usr/bin/env bash
# Produce a production-signed full standalone APK from the signed AAB.
# Google Play uses the *same AAB* to deliver BLINK LIFE separately on demand.
# Fail closed: never publish an APK missing the Godot game runtime.
set -euo pipefail
umask 077

: "${KEYSTORE_PATH:?Release keystore path is required}"
: "${STORE_PASSWORD:?Keystore password is required}"
: "${KEY_ALIAS:?Signing alias is required}"
: "${KEY_PASSWORD:?Key password is required}"
: "${ANDROID_HOME:?Android SDK is required}"

BUNDLE="$(find app/build/outputs/bundle/release -name '*.aab' -type f | head -n 1)"
APK="$(find app/build/outputs/apk/release -name '*.apk' -type f | head -n 1)"
test -s "$BUNDLE"
test -s "$APK"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
TOOL="$WORK/bundletool-all-1.18.2.jar"
curl --fail --location --retry 3 --silent --show-error \
  https://github.com/google/bundletool/releases/download/1.18.2/bundletool-all-1.18.2.jar \
  --output "$TOOL"
echo "378b5434cd1378bef6b2bc527b8c7f0ff2584b273830335bce54d6d0813c8584  $TOOL" | sha256sum --check

printf '%s' "$STORE_PASSWORD" > "$WORK/store-pass"
printf '%s' "$KEY_PASSWORD" > "$WORK/key-pass"
java -jar "$TOOL" build-apks \
  --bundle="$BUNDLE" \
  --output="$WORK/universal.apks" \
  --mode=universal \
  --ks="$KEYSTORE_PATH" \
  --ks-pass="file:$WORK/store-pass" \
  --ks-key-alias="$KEY_ALIAS" \
  --key-pass="file:$WORK/key-pass"

unzip -p "$WORK/universal.apks" universal.apk > "$WORK/full-blink.apk"
test -s "$WORK/full-blink.apk"
unzip -Z1 "$WORK/full-blink.apk" > "$WORK/universal-files.txt"
for required in \
  assets/project.godot \
  assets/scenes/main.tscn \
  lib/arm64-v8a/libgodot_android.so; do
  grep -Fxq "$required" "$WORK/universal-files.txt" || {
    echo "::error::Standalone APK is missing the BLINK LIFE game: $required"
    exit 1
  }
done

BUILD_TOOLS="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$WORK/full-blink.apk"
"$BUILD_TOOLS/aapt" dump xmltree "$WORK/full-blink.apk" AndroidManifest.xml | \
  grep -F BlinkLifeGodotActivity >/dev/null || {
    echo "::error::Standalone APK is missing the game Activity."
    exit 1
  }
mv "$WORK/full-blink.apk" "$APK"
echo "PASS: Website standalone APK includes full Godot game; Play AAB stays on-demand."
du -h "$APK" "$BUNDLE"
