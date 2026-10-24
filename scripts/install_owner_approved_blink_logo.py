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
import re
import shutil
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
    cp("android/drawable/app_icon.png", "app/src/main/res/drawable/app_icon.png")
    cp("android/drawable/blink_logo_foreground.png", "app/src/main/res/drawable/blink_logo_foreground.png")
    for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
        for file in ("ic_launcher.png", "ic_launcher_round.png"):
            cp(f"android/mipmap-{density}/{file}", f"app/src/main/res/mipmap-{density}/{file}")
        cp(f"android/notification/drawable-{density}/ic_stat_blink.png", f"app/src/main/res/drawable-{density}/ic_stat_blink.png")
    # Bitmap density variants may coexist with the default vector; prefer one
    # consistent white silhouette at every density by removing the old vector.
    old_status = ROOT / "app/src/main/res/drawable/ic_stat_blink.xml"
    if old_status.exists():
        if args.dry_run: print("REMOVE", old_status.relative_to(ROOT))
        else: old_status.unlink()
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
    cp("windows/blink-logo.png", "desktopApp/src/main/resources/blink-logo.png")
    cp("windows/BLINK.ico", "desktopApp/src/main/resources/BLINK.ico")
    cp("ios/AppIcon-1024.png", "ios/BLINKiOS/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png")
    cp("ios/Contents.json", "ios/BLINKiOS/Assets.xcassets/AppIcon.appiconset/Contents.json")
    # Do not silently switch Brevo/Paystack hosted branding or app-store
    # listings: those require separate platform configuration + real tests.
    print("DONE: staged local file changes only." if not args.dry_run else "DONE: dry run.")
    print("NEXT: inspect screenshots, run Android+Windows+iOS+web checks on Testlab,")
    print("then separately verify email merchant dashboards, stores and delivery.")

if __name__ == "__main__":
    main()
