#!/usr/bin/env python3
"""BLINK pre-push checks. Fast local checks first, relevant platform builds on --full.

This is a developer aid, NOT proof of GitHub CI, production integration,
independent device testing, or reference screenshot fidelity.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys

import ci_change_scope as scope


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True).strip()


def changed_paths(base: str) -> list[str]:
    """Capture branch changes plus uncommitted/staged changes, without guessing."""
    common = git("merge-base", base, "HEAD")
    output = set()
    for args in (
        ("diff", "--name-only", common, "HEAD"),
        ("diff", "--name-only", "HEAD"),
        ("diff", "--name-only", "--cached"),
    ):
        output.update(p for p in git(*args).splitlines() if p)
    return sorted(output)


def affected(paths: list[str]) -> dict[str, bool]:
    return {
        "android": any(scope.matches(p, scope.ANDROID_ROOTS, scope.ANDROID_FILES) for p in paths),
        "windows": any(scope.matches(p, scope.WINDOWS_ROOTS, scope.WINDOWS_FILES) for p in paths),
        "backend": any(scope.matches(p, scope.BACKEND_ROOTS, scope.BACKEND_FILES) for p in paths),
        "web": any(p.startswith("web/") for p in paths),
        "ui": any(
            (p.startswith("app/src/main/") and "/ui/" in p and p.endswith(".kt"))
            or (p.startswith("desktopApp/src/main/") and p.endswith(".kt"))
            or (p.startswith("web/") and p.endswith((".css", ".html", ".js", ".ts", ".jsx", ".tsx")))
            for p in paths
        ),
    }


def planned_commands(paths: list[str], full: bool) -> list[list[str]]:
    status = affected(paths)
    checks = [
        ["node", "--test", ".github/scripts/testlab_auto_promotion.test.cjs",
         ".github/scripts/ci_auto_recovery.test.cjs",
         ".github/scripts/testlab_batch_queue.test.cjs"],
        [sys.executable, "-m", "unittest", "discover", "-s", "scripts",
         "-p", "test_blink_preflight.py", "-v"],
        [sys.executable, "-m", "unittest", "discover", "-s", "scripts/testing",
         "-p", "test_visual_reference_audit.py", "-v"],
    ]
    if status["android"] or status["windows"]:
        checks.append([sys.executable, "-m", "unittest", "discover", "-s",
                       "scripts", "-p", "test_ci_verified_reuse.py", "-v"])
    if full and status["android"]:
        checks.append(["gradle", ":app:testDebugUnitTest", ":app:lintDebug",
                       ":app:assembleDebug", ":app:assembleDebugAndroidTest",
                       "--stacktrace", "--no-daemon"])
    if full and status["windows"]:
        checks.append(["gradle", "-p", "desktopApp", "build",
                       "--stacktrace", "--no-daemon"])
    return checks


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="origin/main",
                        help="Main ref available in local checkout")
    parser.add_argument("--full", action="store_true",
                        help="Also run affected local Android/Windows builds")
    parser.add_argument("--dry-run", action="store_true",
                        help="Show selected checks without running them")
    args = parser.parse_args(argv)
    try:
        paths = changed_paths(args.base)
        common = git("merge-base", args.base, "HEAD")
    except (OSError, subprocess.CalledProcessError) as exc:
        print(f"Cannot establish a trustworthy main diff: {exc}", file=sys.stderr)
        return 2

    checks = planned_commands(paths, args.full)
    print(json.dumps({"changed_files": paths, "scope": affected(paths),
                      "checks": checks, "full": args.full}, indent=2))
    if args.dry_run:
        return 0

    # Check the entire branch as well as staged and unstaged edits.
    for command in (
        ["git", "diff", "--check", common, "HEAD"],
        ["git", "diff", "--check"],
        ["git", "diff", "--cached", "--check"],
        *checks,
    ):
        print("+ " + " ".join(command), flush=True)
        result = subprocess.run(command, check=False)
        if result.returncode:
            print(f"Preflight blocked: {command[0]} failed ({result.returncode}).",
                  file=sys.stderr)
            return result.returncode
    print("Local preflight PASS. Full CI, screenshot evidence, and staging may still be required.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
