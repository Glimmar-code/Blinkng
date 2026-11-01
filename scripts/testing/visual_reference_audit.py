#!/usr/bin/env python3
"""Compare an owner's reference screen against an actual capture.

This tool requires real input images; it does not capture devices or fabricate
approvals. Compare the same UI fixture, theme, font scale and pixel dimensions.
Resizing for inspection can be requested but NEVER counts as exact sign-off.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageEnhance


def compare_images(reference: Image.Image, actual: Image.Image,
                   tolerance: int = 0) -> tuple[Image.Image, dict]:
    """Return difference image and measurements for equal-sized RGB screens."""
    if reference.size != actual.size:
        raise ValueError(f"Viewport mismatch: {reference.size} != {actual.size}")
    diff = ImageChops.difference(reference.convert("RGB"), actual.convert("RGB"))
    changed = 0
    total_error = 0
    for red, green, blue in diff.getdata():
        if max(red, green, blue) > tolerance:
            changed += 1
        total_error += red + green + blue
    pixel_count = reference.width * reference.height
    return diff, {
        "pixels": pixel_count,
        "mismatched_pixels": changed,
        "mismatch_percent": round(100 * changed / pixel_count, 6),
        "mean_absolute_rgb_error": round(total_error / (3 * pixel_count), 6),
    }


def audit(reference_path: Path, actual_path: Path, out: Path,
          tolerance: int = 0, max_mismatch_percent: float = 0,
          resize_actual: bool = False) -> dict:
    if not 0 <= tolerance <= 255 or not 0 <= max_mismatch_percent <= 100:
        raise ValueError("Invalid tolerance or mismatch threshold")
    with Image.open(reference_path) as src:
        reference = src.convert("RGB")
    with Image.open(actual_path) as src:
        actual = src.convert("RGB")

    initial_actual_size = list(actual.size)
    resized = reference.size != actual.size and resize_actual
    if resized:
        actual = actual.resize(reference.size, Image.Resampling.LANCZOS)
    out.mkdir(parents=True, exist_ok=True)
    report = {
        "reference": str(reference_path),
        "actual": str(actual_path),
        "reference_size": list(reference.size),
        "original_actual_size": initial_actual_size,
        "actual_resized": resized,
        "tolerance": tolerance,
        "max_mismatch_percent": max_mismatch_percent,
        "exact_visual_approval": False,
    }
    if actual.size != reference.size:
        report["error"] = "Viewport mismatch; capture at matching dimensions"
    else:
        diff, measurements = compare_images(reference, actual, tolerance)
        report.update(measurements)
        report["exact_visual_approval"] = (
            not resized and measurements["mismatch_percent"] <= max_mismatch_percent
        )
        reference.save(out / "reference.png")
        actual.save(out / "actual.png")
        Image.blend(reference, actual, 0.5).save(out / "overlay.png")
        ImageEnhance.Contrast(diff).enhance(4).save(out / "difference.png")
    (out / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    return report


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("reference", type=Path)
    p.add_argument("actual", type=Path)
    p.add_argument("--out-dir", type=Path, default=Path("build/visual-reference-audit"))
    p.add_argument("--tolerance", type=int, default=0)
    p.add_argument("--max-mismatch-percent", type=float, default=0)
    p.add_argument("--resize-actual", action="store_true",
                   help="Inspection-only normalization, never exact visual approval")
    args = p.parse_args(argv)
    report = audit(args.reference, args.actual, args.out_dir, args.tolerance,
                   args.max_mismatch_percent, args.resize_actual)
    print(json.dumps(report, indent=2))
    return 0 if report["exact_visual_approval"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
