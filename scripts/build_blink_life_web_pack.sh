#!/usr/bin/env bash
# Build a portable HTML5 BLINK LIFE game: no native Android libraries in the base APK.
set -euo pipefail
GODOT_BIN="${GODOT_BIN:-godot}"
OUT="${1:-app/build/outputs/publish}"
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

"$GODOT_BIN" --headless --path game3d --editor --quit
"$GODOT_BIN" --headless --path game3d --export-release Web "$STAGE/index.html"
for name in index.html index.js index.wasm index.pck; do
  test -s "$STAGE/$name" || { echo "::error::Godot web export is missing $name"; exit 1; }
done
# Use an allowlisted file type set; never ship raw game project secrets or server config.
python3 - "$STAGE" "$OUT/blink-life-web.zip" <<'PY'
import pathlib, sys, zipfile
stage, destination = (pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]))
allowed = {".html", ".js", ".wasm", ".pck", ".png", ".ico", ".svg", ".webp", ".json"}
items = sorted(x for x in stage.iterdir() if x.is_file())
if not items or any(x.suffix.lower() not in allowed for x in items):
    raise SystemExit("Unsafe or unexpected Godot web-export asset type")
with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for item in items:
        archive.write(item, item.name)
print("Packaged game assets:", [p.name for p in items])
PY
sha256sum "$OUT/blink-life-web.zip" | tee "$OUT/blink-life-web.zip.sha256"
du -h "$OUT/blink-life-web.zip"
