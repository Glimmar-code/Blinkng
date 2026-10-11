#!/usr/bin/env python3
"""Deterministic BLINK Android launcher resources from the approved app_icon.png master."""
from __future__ import annotations

import argparse
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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Fail if a resource is stale.")
    options = parser.parse_args()
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
