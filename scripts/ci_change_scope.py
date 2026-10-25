#!/usr/bin/env python3
"""Select expensive BLINK CI checks by changed paths, failing open when uncertain.

The required GitHub checks keep running; only irrelevant build/test steps are skipped.
Unknown diffs and manual runs execute all suites rather than accidentally skipping one.
"""
from __future__ import annotations

import os
import re
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
    "scripts/ci_verified_reuse.py",
    "scripts/test_ci_verified_reuse.py",
    ".github/scripts/testlab_auto_promotion.test.cjs",
    ".github/scripts/test_blink_delta.py",
    "scripts/test_inapp_apk_update.py",
    "scripts/test_launcher_circle.py",
    "scripts/test_png_asset_validator.py",
    "scripts/test_no_google_maps_sdk.py",
    "scripts/test_blink_store_pause.py",
    "scripts/test_home_reference_contract.py",
    "scripts/check_premium_dark_tokens.py",
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
    "scripts/ci_verified_reuse.py",
    "scripts/test_ci_verified_reuse.py",
    "scripts/check_windows_parity.py",
    "scripts/check_premium_dark_tokens.py",
    "platform-parity/changes.md",
}
BACKEND_FILES = {
    ".github/workflows/supabase-safety-check.yml",
    "scripts/check_supabase_safety.py",
    "scripts/check_supabase_sql_syntax.py",
    "scripts/check_supabase_data_api_grants.py",
    "scripts/test_supabase_data_api_grants.py",
    "scripts/test_paystack_integrity.mjs",
}


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True, stderr=subprocess.PIPE).strip()


def changed_files() -> list[str] | None:
    event = os.getenv("GITHUB_EVENT_NAME", "")
    if not event:
        return None
    try:
        if event == "workflow_dispatch":
            # Our trusted batch controller explicitly dispatches quality gates
            # on a Testlab-batch SHA. Unlike arbitrary manual dispatches, its
            # complete main...candidate diff can safely scope costly builds.
            branch = os.getenv("GITHUB_REF_NAME", "")
            if not re.fullmatch(r"Testlab-batch-[0-9]+", branch):
                return None  # Manual/unknown dispatch: run every suite.
            subprocess.run(
                ["git", "fetch", "--no-tags", "origin",
                 "refs/heads/main:refs/remotes/origin/main"],
                check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
            )
            diff_range = "origin/main...HEAD"
        elif event == "pull_request":
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
