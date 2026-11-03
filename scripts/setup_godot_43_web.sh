#!/usr/bin/env bash
# Install a matching editor and HTML5 export templates for BLINK LIFE.
# Production releases must use the exact same Godot version as CI.
set -euo pipefail
GODOT_VERSION=4.3-stable
TEMPLATES="$HOME/.local/share/godot/export_templates/4.3.stable"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
curl -fLsS --retry 3 "https://github.com/godotengine/godot/releases/download/$GODOT_VERSION/Godot_v4.3-stable_linux.x86_64.zip" -o "$work/godot.zip"
unzip -q "$work/godot.zip" -d "$work"
sudo install -m 0755 "$work/Godot_v4.3-stable_linux.x86_64" /usr/local/bin/godot
curl -fLsS --retry 3 "https://github.com/godotengine/godot/releases/download/$GODOT_VERSION/Godot_v4.3-stable_export_templates.tpz" -o "$work/templates.tpz"
mkdir -p "$TEMPLATES"
unzip -q "$work/templates.tpz" 'templates/*' -d "$work"
cp -r "$work/templates/." "$TEMPLATES/"
godot --version
