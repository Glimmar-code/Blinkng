#!/usr/bin/env python3
"""Regression checks for BLINK's circular launcher artwork, including old Android."""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
FILES = [RES / "drawable/app_icon_circle.png"]
for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
    folder = RES / f"mipmap-{density}"
    FILES.extend((folder / "ic_launcher.png", folder / "ic_launcher_round.png"))

for path in FILES:
    with Image.open(path) as loaded:
        image = loaded.convert("RGBA")
    width, height = image.size
    if width != height:
        raise SystemExit(f"{path}: launcher art is not square")
    alpha = image.getchannel("A")
    if any(alpha.getpixel(point) > 8 for point in (
        (0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1)
    )):
        raise SystemExit(f"{path}: the outer corners must be transparent")
    if alpha.getpixel((width // 2, height // 2)) < 240:
        raise SystemExit(f"{path}: the circular face is missing")
    # Confirm all generated icon sizes still display the approved bright B artwork.
    bright = sum(1 for red, green, blue, opacity in image.getdata()
                 if opacity >= 150 and min(red, green, blue) >= 180)
    if bright < max(1, width * height // 100):
        raise SystemExit(f"{path}: the approved B is missing or too small")

print(f"Verified circular BLINK icon silhouette and readable B artwork in {len(FILES)} resources.")
