#!/usr/bin/env bash
# Install a matching editor and HTML5 export templates for BLINK LIFE.
# Production releases must use the exact same Godot version as CI.
# GitHub release assets occasionally return transient 5xx/504 responses;
# retry those failures fully and validate each archive before installing it.
set -euo pipefail

GODOT_VERSION=4.3-stable
TEMPLATES="$HOME/.local/share/godot/export_templates/4.3.stable"
BASE_URL="https://github.com/godotengine/godot/releases/download/$GODOT_VERSION"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

download_asset() {
  local url="$1"
  local destination="$2"

  # --retry-all-errors is intentional: GitHub's asset CDN can return 5xx
  # responses, including 504, which must not abort a production release on
  # the first transient outage. Remove partial files before every retry.
  curl --fail --location --silent --show-error \
    --connect-timeout 30 --max-time 900 \
    --retry 8 --retry-all-errors --retry-delay 3 --retry-max-time 600 \
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
