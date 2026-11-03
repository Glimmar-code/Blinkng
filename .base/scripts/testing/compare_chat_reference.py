#!/usr/bin/env python3
"""Compare the real Android chat screenshot to the supplied reference.

Use the SAME fixed conversation fixture, font scale and viewport on each run.
The supplied image includes a decorative phone frame: crop its inner display
before comparing. This tool does not claim a match simply because CI builds.
Requires Pillow: python -m pip install Pillow
Example:
  python scripts/testing/compare_chat_reference.py reference.jpg actual.png \\
    --reference-crop 35,65,656,1460 --resize-actual \\
    --out-dir build/chat-reference-audit

Only enable --resize-actual when the captured device viewport cannot match
the artwork's pixels. The report records this normalization explicitly.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageEnhance, ImageStat

def crop_arg(raw: str | None, image: Image.Image) -> Image.Image:
    if not raw:
        return image
    coordinates = tuple(int(part.strip()) for part in raw.split(","))
    if len(coordinates) != 4:
        raise ValueError("Crop must be x1,y1,x2,y2")
    left, top, right, bottom = coordinates
    if not (0 <= left < right <= image.width and 0 <= top < bottom <= image.height):
        raise ValueError("Crop falls outside the screenshot")
    return image.crop(coordinates)

def audit(reference: Image.Image, actual: Image.Image, *, tolerance: int):
    if reference.size != actual.size:
        raise ValueError(
            f"Viewport mismatch: reference={reference.size}, actual={actual.size}; "
            "capture at the same viewport or explicitly set --resize-actual"
        )
    diff = ImageChops.difference(reference, actual)
    stat = ImageStat.Stat(diff)
    mean_absolute_error = sum(stat.mean) / 3.0
    # Largest RGB-channel delta for each pixel, not average brightness.
    channel_differences = diff.split()
    different_pixels = sum(
        max(r, g, b) > tolerance
        for r, g, b in zip(
            channel_differences[0].getdata(),
            channel_differences[1].getdata(),
            channel_differences[2].getdata()
        )
    )
    total_pixels = reference.width * reference.height
    return diff, {
        "width": reference.width, "height": reference.height,
        "pixel_tolerance": tolerance,
        "mean_absolute_rgb_error": round(mean_absolute_error, 4),
        "pixels_above_tolerance": different_pixels,
        "mismatch_percent": round(100.0 * different_pixels / total_pixels, 4),
    }

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    parser.add_argument("actual", type=Path)
    parser.add_argument("--reference-crop", help="x1,y1,x2,y2 of artwork's inner chat display")
    parser.add_argument("--actual-crop", help="x1,y1,x2,y2 of Android app display")
    parser.add_argument("--resize-actual", action="store_true",
                        help="Normalize actual dimensions (reported as non-pixel-exact)")
    parser.add_argument("--tolerance", type=int, default=12,
                        help="Maximum allowed RGB-channel difference per pixel")
    parser.add_argument("--max-mismatch-percent", type=float, default=2.0)
    parser.add_argument("--max-mae", type=float, default=6.0)
    parser.add_argument("--out-dir", type=Path, default=Path("build/chat-reference-audit"))
    args = parser.parse_args()
    if not 0 <= args.tolerance <= 255:
        parser.error("tolerance must be 0..255")
    reference = crop_arg(Image.open(args.reference).convert("RGB"), args.reference_crop)
    actual = crop_arg(Image.open(args.actual).convert("RGB"), args.actual_crop)
    resized = False
    if actual.size != reference.size and args.resize_actual:
        actual = actual.resize(reference.size, Image.Resampling.LANCZOS)
        resized = True
    diff, report = audit(reference, actual, tolerance=args.tolerance)
    report.update({
        "reference": str(args.reference),
        "actual": str(args.actual),
        "actual_resized": resized,
        "strict_pixel_alignment": not resized,
        "max_mismatch_percent": args.max_mismatch_percent,
        "max_mae": args.max_mae,
    })
    report["passed"] = (report["mismatch_percent"] <= args.max_mismatch_percent
                        and report["mean_absolute_rgb_error"] <= args.max_mae)
    args.out_dir.mkdir(parents=True, exist_ok=True)
    reference.save(args.out_dir / "reference-cropped.png")
    actual.save(args.out_dir / "actual-aligned.png")
    Image.blend(reference, actual, 0.5).save(args.out_dir / "overlay.png")
    ImageEnhance.Contrast(diff).enhance(4).save(args.out_dir / "difference.png")
    (args.out_dir / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    return 0 if report["passed"] else 1

if __name__ == "__main__":
    raise SystemExit(main())
