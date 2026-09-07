#!/usr/bin/env python3
"""Enforce Blinkng Android ↔ Windows feature parity in pull requests."""

from __future__ import annotations

import os
import subprocess
import sys

ANDROID_USER_SURFACES = (
    "app/src/main/java/",
    "app/src/main/res/",
    "app/src/main/AndroidManifest.xml",
)
IMPLEMENTATION_PREFIXES = (
    "desktopApp/",
    "shared/",
)
PARITY_LEDGER = "platform-parity/changes.md"
EXCEPTION_MARKER = "PARITY-EXCEPTION:"


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True).strip()


def diff_range() -> str | None:
    base_ref = os.getenv("GITHUB_BASE_REF", "").strip()
    if base_ref:
        subprocess.run(
            ["git", "fetch", "origin", base_ref, "--depth=1"],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        return f"origin/{base_ref}...HEAD"

    try:
        git("rev-parse", "HEAD^")
        return "HEAD^...HEAD"
    except subprocess.CalledProcessError:
        return None


def changed_files(range_: str | None) -> list[str]:
    if not range_:
        return []
    output = git("diff", "--name-only", range_)
    return [line.strip() for line in output.splitlines() if line.strip()]


def added_exception_marker(range_: str) -> bool:
    try:
        patch = git("diff", "--unified=0", range_, "--", PARITY_LEDGER)
    except subprocess.CalledProcessError:
        return False
    return any(
        line.startswith("+") and not line.startswith("+++") and EXCEPTION_MARKER in line
        for line in patch.splitlines()
    )


def main() -> int:
    range_ = diff_range()
    files = changed_files(range_)

    android_changes = [
        path for path in files
        if any(path == prefix or path.startswith(prefix) for prefix in ANDROID_USER_SURFACES)
    ]

    if not android_changes:
        print("Windows parity gate: PASS — no Android user-surface changes detected.")
        return 0

    implementation_changes = [
        path for path in files if path.startswith(IMPLEMENTATION_PREFIXES)
    ]

    if implementation_changes:
        print("Windows parity gate: PASS — Windows/shared implementation detected.")
        print("Android changes:")
        for path in android_changes:
            print(f"  - {path}")
        print("Windows/shared implementation changes:")
        for path in implementation_changes:
            print(f"  - {path}")
        return 0

    ledger_changed = PARITY_LEDGER in files
    explicit_exception = bool(range_) and ledger_changed and added_exception_marker(range_)

    if explicit_exception:
        print("Windows parity gate: PASS WITH EXPLICIT PLATFORM EXCEPTION")
        print(
            "Reviewers must verify that the documented exception is genuinely platform-specific "
            "and includes a Windows equivalent where appropriate."
        )
        return 0

    print("Windows parity gate: FAIL", file=sys.stderr)
    print(
        "This PR changes an Android user surface without a Windows/shared implementation.",
        file=sys.stderr,
    )
    print(
        "Implement the change in shared code, update desktopApp, or add a genuine "
        f"'{EXCEPTION_MARKER} <feature-id>' entry to {PARITY_LEDGER} with a reason and Windows equivalent.",
        file=sys.stderr,
    )
    print("Android files detected:", file=sys.stderr)
    for path in android_changes:
        print(f"  - {path}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
