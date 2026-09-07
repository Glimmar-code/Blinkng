#!/usr/bin/env python3
"""Fail CI when an Android user-facing code change forgets Windows/shared parity."""

from __future__ import annotations

import os
import subprocess
import sys

ANDROID_PREFIX = "app/src/main/java/"
PARITY_PREFIXES = (
    "desktopApp/",
    "shared/",
    "platform-parity/",
)


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True).strip()


def changed_files() -> list[str]:
    base_ref = os.getenv("GITHUB_BASE_REF", "").strip()
    if base_ref:
        subprocess.run(
            ["git", "fetch", "origin", base_ref, "--depth=1"],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        base = f"origin/{base_ref}"
        diff_range = f"{base}...HEAD"
    else:
        try:
            git("rev-parse", "HEAD^")
            diff_range = "HEAD^...HEAD"
        except subprocess.CalledProcessError:
            return []

    output = git("diff", "--name-only", diff_range)
    return [line.strip() for line in output.splitlines() if line.strip()]


def main() -> int:
    files = changed_files()
    android_changes = [path for path in files if path.startswith(ANDROID_PREFIX) and path.endswith(".kt")]

    if not android_changes:
        print("Windows parity gate: no Android Kotlin feature changes detected.")
        return 0

    parity_changes = [path for path in files if path.startswith(PARITY_PREFIXES)]
    if parity_changes:
        print("Windows parity gate: PASS")
        print("Android changes:")
        for path in android_changes:
            print(f"  - {path}")
        print("Parity/shared changes:")
        for path in parity_changes:
            print(f"  - {path}")
        return 0

    print("Windows parity gate: FAIL", file=sys.stderr)
    print(
        "This PR changes Android Kotlin code but contains no Windows/shared/parity update.",
        file=sys.stderr,
    )
    print(
        "Implement the feature in shared code, update desktopApp, or document a genuine platform exception in platform-parity/changes.md.",
        file=sys.stderr,
    )
    print("Android files detected:", file=sys.stderr)
    for path in android_changes:
        print(f"  - {path}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
