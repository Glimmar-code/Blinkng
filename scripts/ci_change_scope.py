#!/usr/bin/env python3
"""Select expensive BLINK CI checks by changed paths, failing open when uncertain.

The required GitHub checks keep running; only irrelevant build/test steps are skipped.
Unknown diffs and manual runs execute all suites rather than accidentally skipping one.
"""
from __future__ import annotations

import os
import subprocess
import sys

BUILD_INPUTS = {
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    "gradlew", "gradlew.bat",
}
ANDROID_ROOTS = ("app/", "shared/", "blinkLifeFeature/", "game3d/", "gradle/")
WINDOWS_ROOTS = ("desktopApp/", "shared/", "game3d/", "gradle/")
BACKEND_ROOTS = ("supabase/",)
ANDROID_FILES = BUILD_INPUTS | {
    ".github/workflows/android-build-check.yml",
    ".github/workflows/domain-integration-check.yml",
    ".github/workflows/release-smoke-test.yml",
    ".github/scripts/restore_google_services.sh",
    ".github/scripts/build_full_website_apk.sh",
    "scripts/build_blink_life_web_pack.sh",
    "scripts/setup_godot_43_web.sh",
    "scripts/sync_android_launcher_assets.py",
}
WINDOWS_FILES = BUILD_INPUTS | {
    ".github/workflows/windows-desktop-check.yml",
    "scripts/check_windows_parity.py",
}
BACKEND_FILES = {
    ".github/workflows/supabase-safety-check.yml",
    "scripts/check_supabase_safety.py",
    "scripts/check_supabase_sql_syntax.py",
    "scripts/test_paystack_integrity.mjs",
}


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True, stderr=subprocess.PIPE).strip()


def changed_files() -> list[str] | None:
    event = os.getenv("GITHUB_EVENT_NAME", "")
    if event == "workflow_dispatch" or not event:
        return None  # Always run all checks when manually dispatched or in doubt.
    try:
        if event == "pull_request":
            base = os.getenv("GITHUB_BASE_REF", "")
            if not base:
                return None
            subprocess.run(
                ["git", "fetch", "--no-tags", "origin",
                 f"refs/heads/{base}:refs/remotes/origin/{base}"],
                check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
            )
            diff_range = f"origin/{base}...HEAD"
        elif event == "push":
            before = os.getenv("GITHUB_EVENT_BEFORE", "")
            if not before or set(before) == {"0"}:
                return None
            # checkout uses fetch-depth: 0 so even multi-commit pushes are handled.
            git("cat-file", "-e", f"{before}^{{commit}}")
            diff_range = f"{before}..HEAD"
        else:
            return None
        return [p for p in git("diff", "--name-only", diff_range).splitlines() if p]
    except (OSError, subprocess.CalledProcessError) as exc:
        print(f"Cannot establish complete diff ({exc}); run all CI checks.", file=sys.stderr)
        return None


def matches(path: str, roots: tuple[str, ...], files: set[str]) -> bool:
    return path in files or path.startswith(roots)


def main() -> None:
    paths = changed_files()
    if paths is None or "scripts/ci_change_scope.py" in paths:
        scope = {"android": True, "windows": True, "backend": True}
    else:
        scope = {
            "android": any(matches(p, ANDROID_ROOTS, ANDROID_FILES) for p in paths),
            "windows": any(matches(p, WINDOWS_ROOTS, WINDOWS_FILES) for p in paths),
            "backend": any(matches(p, BACKEND_ROOTS, BACKEND_FILES) for p in paths),
        }
    for name, enabled in scope.items():
        print(f"{name}={str(enabled).lower()}")
    print(
        f"CI scope: {len(paths) if paths is not None else 'unknown'} changed files; "
        + ", ".join(f"{name}={'run' if value else 'not affected'}" for name, value in scope.items()),
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
