#!/usr/bin/env python3
"""Deterministic BLINK Android launcher resources from the approved app_icon.png master."""
from __future__ import annotations

import argparse
import hashlib
import json
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = RES / "drawable/app_icon.png"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def encoded_png(image: Image.Image) -> bytes:
    output = BytesIO()
    image.save(output, format="PNG", optimize=False, compress_level=9)
    return output.getvalue()


def circular_master(master: Image.Image) -> Image.Image:
    """Preserve the approved logo while masking only its outside corners."""
    pixels = master.width
    scale = 4
    alpha = Image.new("L", (pixels * scale, pixels * scale), 0)
    ImageDraw.Draw(alpha).ellipse((0, 0, pixels * scale - 1, pixels * scale - 1), fill=255)
    alpha = alpha.resize((pixels, pixels), Image.Resampling.LANCZOS)
    circle = master.copy()
    circle.putalpha(ImageChops.multiply(circle.getchannel("A"), alpha))
    return circle



def verify_current_approved_assets() -> None:
    """Verify the new BLINK b/blue-dot assets without regenerating old circular icons.

    A different sync algorithm produced the old round launcher icons. Re-running
    that algorithm would overwrite the approved visual design, so the new pack
    must instead be checked against its immutable source and output SHA-256s.
    """
    source = ROOT / "branding/approved/BLINK-logo-source.webp"
    inventory_file = ROOT / "branding/approved/exports/manifest.json"
    inventory = json.loads(inventory_file.read_text(encoding="utf-8"))
    expected_source = inventory.get("approved_derived_source_sha256")
    assert expected_source and len(expected_source) == 64
    if not source.is_file() or hashlib.sha256(source.read_bytes()).hexdigest() != expected_source:
        raise SystemExit("Approved BLINK source artwork has changed unexpectedly")
    files = inventory.get("generated_assets")
    if not isinstance(files, list) or len(files) < 80:
        raise SystemExit("Incomplete BLINK approved-asset catalogue")
    out_of_sync = []
    verified = set()
    for item in files:
        name = item["path"]
        relative = Path(name)
        if relative.is_absolute() or ".." in relative.parts:
            raise SystemExit("Unsafe approved image path: " + name)
        file = ROOT / relative
        verified.add(name)
        expected_hash = item["sha256"]
        if not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest() != expected_hash:
            out_of_sync.append(name)
        elif name.endswith(".png"):
            with Image.open(file) as art:
                if art.size != (item["width"], item["height"]):
                    out_of_sync.append(name + " (image dimensions)")
    for required in (
        "app/src/main/res/drawable/app_icon.png",
        "app/src/main/res/drawable/blink_logo_foreground.png",
        "web/blink-logo-v2.png",
        "web/blink-logo.png",
        "desktopApp/src/main/resources/blink-logo.png",
    ):
        if required not in verified:
            out_of_sync.append(required + " (missing from catalogue)")
    for density in DENSITIES:
        for name in ("ic_launcher.png", "ic_launcher_round.png", "ic_launcher_foreground.png"):
            target = "app/src/main/res/mipmap-" + density + "/" + name
            if target not in verified:
                out_of_sync.append(target + " (missing from catalogue)")
        target = "app/src/main/res/drawable-" + density + "/ic_stat_blink.png"
        if target not in verified:
            out_of_sync.append(target + " (missing from catalogue)")
        elif (ROOT / target).exists():
            with Image.open(ROOT / target) as icon:
                alpha = icon.convert("RGBA")
                colored = any((r, g, b) != (255, 255, 255) for r, g, b, a in alpha.getdata() if a > 0)
                if colored:
                    out_of_sync.append(target + " (small notifications must be white only)")
    manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    if 'android:icon="@mipmap/ic_launcher"' not in manifest or 'android:roundIcon="@mipmap/ic_launcher_round"' not in manifest:
        out_of_sync.append("app/src/main/AndroidManifest.xml (launcher references)")
    foreground = (RES / "drawable/ic_launcher_foreground.xml").read_text(encoding="utf-8")
    if "@mipmap/ic_launcher_foreground" not in foreground:
        out_of_sync.append("app/src/main/res/drawable/ic_launcher_foreground.xml")
    if "@drawable/app_icon" not in (RES / "drawable/ic_splash_b.xml").read_text(encoding="utf-8"):
        out_of_sync.append("app/src/main/res/drawable/ic_splash_b.xml")
    web_manifest = json.loads((ROOT / "web/manifest.webmanifest").read_text(encoding="utf-8"))
    if {x["src"] for x in web_manifest["icons"]} != {"pwa-192.png", "pwa-512.png", "maskable-512.png"}:
        out_of_sync.append("web/manifest.webmanifest (PWA icon paths)")
    if out_of_sync:
        raise SystemExit("Out-of-sync owner-approved BLINK assets:\\n" + "\\n".join(out_of_sync))
    print(f"BLINK approved logo verified: {len(files)} source-matched assets.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Fail if a resource is stale.")
    options = parser.parse_args()
    if (ROOT / 'branding/approved/exports/manifest.json').exists():
        verify_current_approved_assets()
        return
    master = Image.open(SOURCE).convert("RGBA")
    if master.width != master.height:
        raise SystemExit("The approved BLINK launcher master must be square.")

    out_of_sync: list[str] = []
    # A transparent circle, rather than a black square, works on Xiaomi/MIUI
    # and round or rounded-square Android launcher masks alike.
    circle = circular_master(master)
    circle_path = RES / "drawable/app_icon_circle.png"
    circle_png = encoded_png(circle)
    if options.check:
        if not circle_path.exists() or circle_path.read_bytes() != circle_png:
            out_of_sync.append(str(circle_path.relative_to(ROOT)))
    else:
        circle_path.write_bytes(circle_png)

    for density, pixels in DENSITIES.items():
        icon = circle.resize((pixels, pixels), Image.Resampling.LANCZOS)
        round_icon = icon

        for filename, variant in (("ic_launcher.png", icon), ("ic_launcher_round.png", round_icon)):
            dest = RES / f"mipmap-{density}" / filename
            expected = encoded_png(variant)
            if options.check:
                if not dest.exists() or dest.read_bytes() != expected:
                    out_of_sync.append(str(dest.relative_to(ROOT)))
            else:
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(expected)

    # The same approved B master is used for both web icons; manifest sizes must match.
    web = ROOT / "web"
    for filename, pixels in (("blink-logo-v2.png", 192), ("blink-logo.png", 512)):
        destination = web / filename
        content = encoded_png(master.resize((pixels, pixels), Image.Resampling.LANCZOS))
        if options.check:
            if not destination.exists() or destination.read_bytes() != content:
                out_of_sync.append(str(destination.relative_to(ROOT)))
        else:
            destination.write_bytes(content)

    for resource, expected_drawable in (
        ("ic_launcher_foreground.xml", "@drawable/app_icon_circle"),
        ("ic_splash_b.xml", "@drawable/app_icon"),
    ):
        path = RES / "drawable" / resource
        if expected_drawable not in path.read_text():
            out_of_sync.append(str(path.relative_to(ROOT)))
    if "@android:color/transparent" not in (RES / "drawable/ic_launcher_background.xml").read_text():
        out_of_sync.append("app/src/main/res/drawable/ic_launcher_background.xml")
    if (RES / "drawable/blink_logo_foreground.png").read_bytes() != SOURCE.read_bytes():
        out_of_sync.append("app/src/main/res/drawable/blink_logo_foreground.png")

    if out_of_sync:
        raise SystemExit("Out-of-sync BLINK launcher assets:\n" + "\n".join(out_of_sync))
    print("BLINK Android and web branding synchronized with approved app_icon.png master.")


if __name__ == "__main__":
    main()
