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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Fail if a resource is stale.")
    options = parser.parse_args()
    master = Image.open(SOURCE).convert("RGBA")
    if master.width != master.height:
        raise SystemExit("The approved BLINK launcher master must be square.")

    out_of_sync: list[str] = []
    for density, pixels in DENSITIES.items():
        icon = master.resize((pixels, pixels), Image.Resampling.LANCZOS)
        round_icon = icon.copy()
        alpha = Image.new("L", (pixels * 4, pixels * 4), 0)
        ImageDraw.Draw(alpha).ellipse((0, 0, pixels * 4 - 1, pixels * 4 - 1), fill=255)
        alpha = alpha.resize((pixels, pixels), Image.Resampling.LANCZOS)
        round_icon.putalpha(ImageChops.multiply(round_icon.getchannel("A"), alpha))

        for filename, variant in (("ic_launcher.png", icon), ("ic_launcher_round.png", round_icon)):
            dest = RES / f"mipmap-{density}" / filename
            expected = encoded_png(variant)
            if options.check:
                if not dest.exists() or dest.read_bytes() != expected:
                    out_of_sync.append(str(dest.relative_to(ROOT)))
            else:
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(expected)

    for resource in ("ic_launcher_foreground.xml", "ic_splash_b.xml"):
        path = RES / "drawable" / resource
        if '@drawable/app_icon' not in path.read_text():
            out_of_sync.append(str(path.relative_to(ROOT)))
    if (RES / "drawable/blink_logo_foreground.png").read_bytes() != SOURCE.read_bytes():
        out_of_sync.append("app/src/main/res/drawable/blink_logo_foreground.png")

    if out_of_sync:
        raise SystemExit("Out-of-sync BLINK launcher assets:\n" + "\n".join(out_of_sync))
    print("BLINK launcher resources synchronized with approved app_icon.png master.")


if __name__ == "__main__":
    main()
