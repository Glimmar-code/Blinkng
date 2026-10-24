#!/usr/bin/env python3
"""Stage the exact owner-approved BLINK image assets into a Blinkng checkout.

Input is the exported BLINK_Official_Logo_Pack directory. This program does
NOT generate a new logo, recolor the approved artwork, or deploy anything.
Run on a feature branch targeting Testlab, then inspect git diff and run CI.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import struct
from pathlib import Path

APPROVED_SHA256 = "9c1b905cbbdf904462d909b799c4c2f6927b665e59bc63c2c77ce5aba79fff68"
ROOT = Path(__file__).resolve().parent.parent

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("asset_pack", type=Path)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    pack = args.asset_pack.resolve()
    original = pack / "master" / "BLINK-ORIGINAL-UNMODIFIED.jpg"
    if not original.is_file():
        raise SystemExit("Missing owner-approved original: " + str(original))
    digest = hashlib.sha256(original.read_bytes()).hexdigest()
    if digest != APPROVED_SHA256:
        raise SystemExit("REFUSING: asset pack does not match approved logo SHA256")
    manifest_file = pack / "manifest.json"
    if not manifest_file.is_file():
        raise SystemExit("Missing logo pack export manifest")
    manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
    if manifest.get("source_sha256") != APPROVED_SHA256:
        raise SystemExit("Logo manifest provenance does not match owner's original")
    exported = manifest.get("files", [])
    if not exported or len(exported) < 90:
        raise SystemExit("Incomplete owner logo package: fewer than 90 exports")
    for asset in exported:
        rel = asset["path"]
        if Path(rel).is_absolute() or ".." in Path(rel).parts:
            raise SystemExit("Unsafe logo pack path: " + rel)
        infile = pack / rel
        if not infile.is_file() or infile.stat().st_size == 0:
            raise SystemExit("Missing/empty logo export: " + rel)
        # Audit all 99+ exported variants; keep them accessible for external
        # channels even where the app references only a subset directly.
        if asset.get("format") == "PNG":
            data = infile.read_bytes()
            if not data.startswith(b"\\x89PNG\\r\\n\\x1a\\n"):
                raise SystemExit("Not a real PNG: " + rel)
            width, height = struct.unpack(">II", data[16:24])
            expected = asset.get("size", "")
            if expected and expected.lower() != f"{width}x{height}":
                raise SystemExit(f"Wrong size {rel}: got {width}x{height}; expected {expected}")

    def cp(src: str, dst: str):
        infile = pack / src
        target = ROOT / dst
        if not infile.is_file():
            raise SystemExit("Missing exported asset: " + src)
        if args.dry_run:
            print("COPY", src, "=>", dst)
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(infile, target)
    def edit(path: str, before: str, after: str):
        target = ROOT / path
        text = target.read_text()
        if before not in text and after not in text:
            raise SystemExit("Unexpected source layout; not changing " + path)
        if before in text:
            if args.dry_run: print("EDIT", path, before[:50])
            else: target.write_text(text.replace(before, after), encoding="utf-8")
    cp("master/BLINK-ORIGINAL-UNMODIFIED.jpg", "branding/approved/BLINK-ORIGINAL-UNMODIFIED.jpg")
    cp("manifest.json", "branding/approved/manifest.json")
    # Preserve every exported size/type rather than silently omitting any
    # marketplace, payment, notification, email or store asset.
    for asset in exported:
        cp(asset["path"], "branding/approved/exports/" + asset["path"])
    cp("android/drawable/app_icon.png", "app/src/main/res/drawable/app_icon.png")
    cp("android/drawable/blink_logo_foreground.png", "app/src/main/res/drawable/blink_logo_foreground.png")
    for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
        for file in ("ic_launcher.png", "ic_launcher_round.png"):
            cp(f"android/mipmap-{density}/{file}", f"app/src/main/res/mipmap-{density}/{file}")
        cp(f"android/notification/drawable-{density}/ic_stat_blink.png", f"app/src/main/res/drawable-{density}/ic_stat_blink.png")
        cp(f"android/adaptive-foreground/mipmap-{density}/ic_launcher_foreground.png",
           f"app/src/main/res/mipmap-{density}/ic_launcher_foreground.png")
    # Bitmap density variants may coexist with the default vector; prefer one
    # consistent white silhouette at every density by removing the old vector.
    old_status = ROOT / "app/src/main/res/drawable/ic_stat_blink.xml"
    if old_status.exists():
        if args.dry_run: print("REMOVE", old_status.relative_to(ROOT))
        else: old_status.unlink()
    edit("app/src/main/res/drawable/ic_launcher_foreground.xml",
         "@drawable/blink_logo_foreground", "@mipmap/ic_launcher_foreground")
    edit("app/src/main/AndroidManifest.xml",
         'android:icon="@drawable/app_icon"', 'android:icon="@mipmap/ic_launcher"')
    edit("app/src/main/AndroidManifest.xml",
         'android:roundIcon="@drawable/app_icon"', 'android:roundIcon="@mipmap/ic_launcher_round"')
    for file in ("blink-logo.png", "blink-logo-v2.png", "favicon.ico", "favicon-32.png",
                 "apple-touch-icon.png", "pwa-192.png", "pwa-512.png", "maskable-512.png"):
        cp("web/" + file, "web/" + file)
    cp("social/og-1200x630.png", "web/og-blink.png")
    # Existing web UI elements refer to blink-logo-v2.png: replacing the
    # referenced asset updates every such element without disruptive UI edits.
    edit("web/index.html",
         '<link rel="icon" type="image/png" sizes="512x512" href="./blink-logo-v2.png" />',
         '<link rel="icon" sizes="any" href="./blink-icon.svg" />\n'
         '  <link rel="icon" type="image/png" sizes="32x32" href="./favicon-32.png" />\n'
         '  <link rel="shortcut icon" href="./favicon.ico" />')
    edit("web/index.html",
         '<link rel="apple-touch-icon" href="./blink-logo-v2.png" />',
         '<link rel="apple-touch-icon" sizes="180x180" href="./apple-touch-icon.png" />')
    edit("web/index.html",
         'https://www.blink.com.ng/blink-logo-v2.png',
         'https://www.blink.com.ng/og-blink.png')
    edit("web/index.html",
         '<meta name="twitter:card" content="summary" />',
         '<meta name="twitter:card" content="summary_large_image" />')
    if not args.dry_run:
        manifest_path = ROOT / "web/manifest.webmanifest"
        manifest = json.loads(manifest_path.read_text())
        manifest["icons"] = [
            {"src": "pwa-192.png", "sizes": "192x192", "type": "image/png", "purpose": "any"},
            {"src": "pwa-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any"},
            {"src": "maskable-512.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable"}
        ]
        manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
    else:
        print("EDIT web/manifest.webmanifest")
    edit("web/sw.js", "blink-web-v10-logo-display-fix", "blink-web-v11-approved-logo")
    edit("web/sw.js", "  './blink-logo-v2.png',",
         "  './blink-logo-v2.png',\\n"
         "  './favicon.ico',\\n"
         "  './favicon-32.png',\\n"
         "  './apple-touch-icon.png',\\n"
         "  './pwa-192.png',\\n"
         "  './pwa-512.png',\\n"
         "  './maskable-512.png',\\n"
         "  './og-blink.png',")
    cp("windows/blink-logo.png", "desktopApp/src/main/resources/blink-logo.png")
    cp("windows/BLINK.ico", "desktopApp/src/main/resources/BLINK.ico")
    cp("ios/AppIcon-1024.png", "ios/BLINKiOS/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png")
    cp("ios/Contents.json", "ios/BLINKiOS/Assets.xcassets/AppIcon.appiconset/Contents.json")
    # Do not silently switch Brevo/Paystack hosted branding or app-store
    # listings: those require separate platform configuration + real tests.
    print(f"Validated {len(exported)} official logo exports.")
    print("DONE: staged local file changes only." if not args.dry_run else "DONE: dry run.")
    print("NEXT: inspect screenshots, run Android+Windows+iOS+web checks on Testlab,")
    print("then separately verify email merchant dashboards, stores and delivery.")

if __name__ == "__main__":
    main()
