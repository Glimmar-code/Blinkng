#!/usr/bin/env bash
# Release-only conversion of BLINK's existing Android launcher art.
# Does not invent a replacement logo or modify Android's source.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="ios/BLINKiOS/Assets.xcassets/AppIcon.appiconset"
command -v sips >/dev/null || { echo "::error::macOS sips is required"; exit 1; }
mkdir -p "$OUT"
TMP_JPEG="$(mktemp "${RUNNER_TEMP:-/tmp}/blink-icon.XXXXXX")"
trap 'rm -f "$TMP_JPEG"' EXIT
APPROVED_ICON="$OUT/AppIcon-1024.png"
APPROVED_ICON_SHA256="9a051cdde15465c117250fa67347535ba862ff5257d81f64219a0b86cc332785"
if [[ -s "$APPROVED_ICON" ]]; then
  ACTUAL_SHA256="$(shasum -a 256 "$APPROVED_ICON" | cut -d " " -f 1)"
  if [[ "$ACTUAL_SHA256" == "$APPROVED_ICON_SHA256" ]]; then
    echo "Using verified, owner-approved BLINK iOS app icon."
  elif [[ -s branding/approved/BLINK-ORIGINAL-UNMODIFIED.jpg ]]; then
    echo "::error::Owner-approved master exists, but iOS app icon hash is wrong." >&2
    exit 1
  else
    echo "Approved icon has not yet been staged; regenerating legacy fallback."
    rm -f "$APPROVED_ICON"
  fi
fi
if [[ ! -s "$APPROVED_ICON" ]]; then

# Use only existing BLINK artwork. Earlier launcher assets may be encoded
# as unsupported placeholders, so inspect/convert the first valid asset.
# Do not fabricate a new icon or silently rename another brand.
SELECTED=""
for SOURCE in \
  "app/src/main/res/drawable/app_icon.png" \
  "web/blink-logo-v2.png" \
  "web/blink-logo.png" \
  "desktopApp/src/main/resources/blink-logo.png"; do
  if [[ -s "$SOURCE" ]] && \
     sips -g format "$SOURCE" >/dev/null 2>&1 && \
     sips -s format jpeg -z 1024 1024 "$SOURCE" --out "$TMP_JPEG" >/dev/null 2>&1; then
    SELECTED="$SOURCE"
    break
  fi
done
if [[ -z "$SELECTED" ]]; then
  echo "::error::No valid existing BLINK image could be converted to a TestFlight icon." >&2
  for candidate in app/src/main/res/drawable/app_icon.png web/blink-logo-v2.png web/blink-logo.png; do
    [[ ! -f "$candidate" ]] || file "$candidate"
  done
  exit 1
fi
# JPEG intermediate flattens transparency. The App Store icon is opaque.
sips -s format png "$TMP_JPEG" --out "$APPROVED_ICON" >/dev/null
echo "Derived legacy BLINK release icon from: $SELECTED (approved asset not installed yet)."
fi
cat > "$OUT/Contents.json" <<'JSON'
{
  "images": [
    { "idiom": "universal", "platform": "ios", "size": "1024x1024",
      "filename": "AppIcon-1024.png" }
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
if "      - path: BLINKiOS/Assets.xcassets\n" in text:
    print("App icon asset catalog already included")
    raise SystemExit(0)
text = text.replace(
    needle,
    needle + "      - path: BLINKiOS/Assets.xcassets\n",
    1,
)
config.write_text(text)
PY
echo "BLINK iOS icon catalog prepared: $OUT"
echo "Visual review required before any public App Store release."
