#!/usr/bin/env bash
# Release-only conversion of BLINK's existing Android launcher art.
# Does not invent a replacement logo or modify Android's source.
set -euo pipefail
cd "$(dirname "$0")/.."
SOURCE="app/src/main/res/drawable/app_icon.png"
OUT="ios/BLINKiOS/Assets.xcassets/AppIcon.appiconset"
if [[ ! -s "$SOURCE" ]]; then
  echo "::error::Approved existing BLINK app icon missing." >&2
  exit 1
fi
command -v sips >/dev/null || { echo "::error::macOS sips is required"; exit 1; }
mkdir -p "$OUT"
TMP_JPEG="$(mktemp "${RUNNER_TEMP:-/tmp}/blink-icon.XXXXXX")"
trap 'rm -f "$TMP_JPEG"' EXIT
# Flatten any alpha via JPEG before converting back to opaque PNG.
sips -s format jpeg -z 1024 1024 "$SOURCE" --out "$TMP_JPEG" >/dev/null
sips -s format png "$TMP_JPEG" --out "$OUT/Icon-1024.png" >/dev/null
cat > "$OUT/Contents.json" <<'JSON'
{
  "images": [
    { "idiom": "universal", "platform": "ios", "size": "1024x1024",
      "filename": "Icon-1024.png" }
  ],
  "info": { "author": "xcode", "version": 1 }
}
JSON
# XcodeGen only includes Sources by default. Add the asset catalog locally
# for archive builds; do not modify the committed XcodeGen project.
python3 - <<'PY'
from pathlib import Path
config = Path("ios/project.yml")
text = config.read_text()
needle = "    sources:\n      - path: BLINKiOS/Sources\n"
if text.count(needle) != 1:
    raise SystemExit("Unexpected iOS project shape")
text = text.replace(
    needle,
    needle + "      - path: BLINKiOS/Assets.xcassets\n",
    1,
)
config.write_text(text)
PY
echo "Release icon derived from existing BLINK icon: $OUT"
echo "Visual review required before any public App Store release."
