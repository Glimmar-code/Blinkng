#!/usr/bin/env python3
"""BLINK Supabase Storage -> Cloudflare R2: copy-first, resumable migration.

Requires rclone remotes configured securely *outside this repository*:
  blink-supabase:  (Supabase Storage S3-compatible endpoint)
  blink-r2:        (Cloudflare R2 S3 endpoint)

By default only inventories source buckets. It NEVER deletes originals, rewrites
application URLs, or makes private buckets public. Existing clients keep working
until a separately verified read/write cutover is approved.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

# Source privacy is authoritative. Private buckets are NEVER copied to public R2.
DESTINATIONS = {
    "avatars": "blink-public-media",
    "covers": "blink-public-media",
    "blink-live-banners": "blink-public-media",
    "market-media": "blink-public-media",
    "market-video": "blink-public-media",
    "marketplace-media": "blink-public-media",
    "post-media": "blink-public-media",
    "posts": "blink-public-media",
    "profile-media": "blink-public-media",
    "blink-channel-media": "blink-private-media",
    "feed-media": "blink-private-media",
    "message-media": "blink-private-media",
    "blink-bug-reports": "blink-private-media",
    "status-media": "blink-temporary-media",
    "story-media": "blink-temporary-media",
}
# View-once is consumed atomically and cannot safely undergo a generic bulk
# migration: the existing one-time-read/delete transaction must stay in Supabase.
EXCLUDED = {"message-view-once"}

REMOTE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_-]*:$")


def remote(value: str) -> str:
    if not REMOTE_RE.fullmatch(value):
        raise argparse.ArgumentTypeError("Use a named rclone remote such as blink-r2: (no paths).")
    return value


def object_paths(source_remote: str, target_remote: str, bucket: str) -> tuple[str, str]:
    if bucket not in DESTINATIONS:
        raise ValueError(f"Bucket not on the reviewed allowlist: {bucket}")
    # Include the original bucket under /legacy to avoid name collisions.
    return f"{source_remote}{bucket}", f"{target_remote}{DESTINATIONS[bucket]}/legacy/{bucket}"


def run_rclone(arguments: list[str], *, capture: bool = False) -> str:
    proc = subprocess.run(
        ["rclone", *arguments],
        check=True, text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )
    return proc.stdout if capture else ""


def inventory(path: str) -> dict:
    size = json.loads(run_rclone(["size", "--json", path], capture=True))
    return {"objects": int(size.get("count", 0)), "bytes": int(size.get("bytes", 0))}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=remote, default="blink-supabase:")
    parser.add_argument("--target", type=remote, default="blink-r2:")
    parser.add_argument("--mode", choices=("plan", "copy", "verify"), default="plan")
    parser.add_argument("--buckets", nargs="+", choices=sorted(DESTINATIONS), default=sorted(DESTINATIONS))
    parser.add_argument("--report", type=Path, default=Path("r2-migration-report.json"))
    args = parser.parse_args(argv)
    if args.source == args.target:
        parser.error("Source and destination rclone remotes must differ.")

    report = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "mode": args.mode,
        "sources_preserved": True,
        "url_cutover_performed": False,
        "view_once_excluded": True,
        "buckets": [],
    }
    ok = True
    for bucket in args.buckets:
        source, target = object_paths(args.source, args.target, bucket)
        row = {"source_bucket": bucket, "destination_bucket": DESTINATIONS[bucket]}
        try:
            row["source"] = inventory(source)
            if args.mode == "copy":
                # Safe to resume. Does not overwrite R2 objects already present.
                # Existing inconsistent copies are detected in --mode verify.
                run_rclone(["copy", source, target, "--ignore-existing", "--transfers", "4", "--checkers", "8"])
            if args.mode == "verify":
                # Full byte-for-byte comparison, not merely sizes or multipart ETags.
                run_rclone(["check", source, target, "--one-way", "--download", "--checkers", "4"])
                row["verified"] = True
            row["destination"] = inventory(target) if args.mode != "plan" else None
            row["status"] = "ok"
        except (subprocess.CalledProcessError, ValueError, json.JSONDecodeError) as exc:
            row["status"] = "failed"
            row["error"] = type(exc).__name__  # No credentials, object names or URLs in reports.
            ok = False
        report["buckets"].append(row)
        print(f"{bucket} -> {DESTINATIONS[bucket]}: {row['status']}")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Report: {args.report}")
    if args.mode == "copy":
        print("Supabase originals retained. Run --mode verify before any read/write cutover.")
    if args.mode == "verify":
        print("Verification alone never rewrites database references or deletes source files.")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
