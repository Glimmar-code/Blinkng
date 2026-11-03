#!/usr/bin/env python3
"""Verify BLINK launcher artwork stays faithful to the approved design.

The old circular-only icon check is maintained for projects without the
new, hash-verified b/blue-dot asset pack. The approved design is a rounded
black square with a white b and an electric-blue inner dot; requiring
transparent corners would accidentally reject the owner's approved icon.
"""
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT / "app/src/main/res"
DENSITIES=("mdpi","hdpi","xhdpi","xxhdpi","xxxhdpi")
APPROVED=(ROOT/"branding/approved/exports/manifest.json").is_file()

if APPROVED:
    # Hashes and dimensions were verified by sync_android_launcher_assets.py.
    # Independently confirm the *visible* b and blue dot remain legible at 48px.
    source=RES/"drawable/app_icon.png"
    paths=[source]
    for density in DENSITIES:
        for filename in ("ic_launcher.png","ic_launcher_round.png","ic_launcher_foreground.png"):
            paths.append(RES/f"mipmap-{density}"/filename)
    for path in paths:
        with Image.open(path) as loaded:
            image=loaded.convert("RGBA")
        width,height=image.size
        if width!=height or width<48:
            raise SystemExit(f"{path}: invalid launcher geometry")
        pix=list(image.getdata())
        bright=sum(1 for r,g,b,a in pix if a>=150 and min(r,g,b)>=190)
        blue=sum(1 for r,g,b,a in pix
                 if a>=150 and b>r*1.25 and b>g*1.10 and r<180)
        dark=sum(1 for r,g,b,a in pix if a>=150 and max(r,g,b)<65)
        if bright<max(1,width*height//100) or blue<1 or dark<max(1,width*height//5):
            raise SystemExit(f"{path}: BLINK white b, black face or blue dot is missing")
    print(f"Verified approved BLINK launcher geometry, white b and blue dot in {len(paths)} resources.")
else:
    FILES=[RES/"drawable/app_icon_circle.png"]
    for density in DENSITIES:
        folder=RES/f"mipmap-{density}"
        FILES.extend((folder/"ic_launcher.png",folder/"ic_launcher_round.png"))
    for path in FILES:
        with Image.open(path) as loaded:
            image=loaded.convert("RGBA")
        width,height=image.size
        if width!=height:
            raise SystemExit(f"{path}: launcher art is not square")
        alpha=image.getchannel("A")
        if any(alpha.getpixel(point)>8 for point in
               ((0,0),(width-1,0),(0,height-1),(width-1,height-1))):
            raise SystemExit(f"{path}: the outer corners must be transparent")
        if alpha.getpixel((width//2,height//2))<240:
            raise SystemExit(f"{path}: the circular face is missing")
        bright=sum(1 for red,green,blue,opacity in image.getdata()
                   if opacity>=150 and min(red,green,blue)>=180)
        if bright<max(1,width*height//100):
            raise SystemExit(f"{path}: the approved B is missing or too small")
    print(f"Verified legacy circular BLINK icon silhouette in {len(FILES)} resources.")
