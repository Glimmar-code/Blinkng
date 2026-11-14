#!/usr/bin/env bash
# Install the exact Godot editor and HTML5 export templates used by BLINK CI.
# GitHub's release CDN can intermittently return 429/5xx (especially 504).
# Retry all transfer failures with bounded backoff and validate both archives.
# The release-smoke workflow uses this same helper as the signed production release.
set -euo pipefail

GODOT_VERSION=4.3-stable
TEMPLATES="$HOME/.local/share/godot/export_templates/4.3.stable"
BASE_URL="https://github.com/godotengine/godot/releases/download/$GODOT_VERSION"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

download_asset() {
  local url="$1"
  local destination="$2"

  # Remove any partial/stale file before retrying a transfer.
  rm -f "$destination"
  curl --fail --location --silent --show-error \
    --connect-timeout 30 --max-time 1800 \
    --retry 12 --retry-all-errors --retry-delay 5 --retry-max-time 1200 \
    "$url" --output "$destination"

  test -s "$destination"
}

download_asset "$BASE_URL/Godot_v4.3-stable_linux.x86_64.zip" "$work/godot.zip"
unzip -tq "$work/godot.zip"
unzip -q "$work/godot.zip" -d "$work"
test -x "$work/Godot_v4.3-stable_linux.x86_64"
sudo install -m 0755 "$work/Godot_v4.3-stable_linux.x86_64" /usr/local/bin/godot

download_asset "$BASE_URL/Godot_v4.3-stable_export_templates.tpz" "$work/templates.tpz"
unzip -tq "$work/templates.tpz"
mkdir -p "$TEMPLATES"
unzip -q "$work/templates.tpz" 'templates/*' -d "$work"
test -d "$work/templates"
cp -r "$work/templates/." "$TEMPLATES/"

godot --version
