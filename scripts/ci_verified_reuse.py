#!/usr/bin/env python3
"""Fail-closed BLINK CI work reuse.

A required GitHub check always runs for the NEW SHA. Expensive suites may be
skipped only if the same scope has already passed on an immediate predecessor
PR head (or the tested PR parent of a main merge) AND its build inputs are
byte-for-byte identical to the current checked-out integration tree.

Source evidence is a successful GitHub Actions job whose individual costly
steps actually completed successfully. A successful check that had itself
reused/skipped those steps is NEVER accepted as fresh source evidence. This
deliberately avoids transitive, stale, or forgeable cache attestations.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import subprocess
import sys
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

REPO = "Glimmar-code/Blinkng"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
REQUIRED_STEPS = {
    "android": (
        "Android unit tests",
        "Record real responsive authentication screenshots",
        "Record actual Compose accessibility screenshots with Robolectric",
        "Capture full-screen BLINK profile screenshots",
        "Capture campus Search reference screenshots",
        "Android lint",
        "Compile instrumentation tests",
        "Build debug APK",
        "Build debug App Bundle",
    ),
    "windows": (
        "Compile and test desktop app",
        "Build Windows EXE and MSI",
        "Verify native Godot assets in Windows installer build",
    ),
}
JOB_NAME = {"android": "validate", "windows": "windows-build"}

# Include every known source, test, asset, build tool, and backend contract
# that could affect compiled code. Anything unknown or policy-sensitive
# conservatively invalidates reuse; only known unrelated categories are safe.
COMMON_PREFIXES = ("shared/", "game3d/", "gradle/", "supabase/")
ANDROID_PREFIXES = ("app/", "blinkLifeFeature/", ".github/scripts/")
WINDOWS_PREFIXES = ("desktopApp/", ".github/scripts/")
COMMON_ROOTS = {
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    "gradlew", "gradlew.bat", "local.properties", "libs.versions.toml",
}
ANDROID_WORKFLOWS = {
    ".github/workflows/android-build-check.yml",
    ".github/workflows/release-smoke-test.yml",
    ".github/workflows/android-runtime-smoke.yml",
    ".github/workflows/auto-promote-testlab.yml",
}
WINDOWS_WORKFLOWS = {
    ".github/workflows/windows-desktop-check.yml",
    ".github/workflows/auto-promote-testlab.yml",
}
COMMON_SCRIPTS = {
    "scripts/ci_change_scope.py",
    "scripts/ci_verified_reuse.py",
    "scripts/check_premium_dark_tokens.py",
}
ANDROID_SCRIPTS = {
    "scripts/sync_android_launcher_assets.py",
    "scripts/test_launcher_circle.py",
    "scripts/test_inapp_apk_update.py",
    "scripts/check_supabase_safety.py",
    "scripts/check_supabase_sql_syntax.py",
    "scripts/check_github_pages_site.py",
}
WINDOWS_SCRIPTS = {
    "scripts/check_windows_parity.py",
    "scripts/build_blink_life_web_pack.sh",
}
# Explicitly known *unrelated* CI workflow families. Any new workflow
# defaults to affected until evaluated, rather than silently weakening tests.
UNRELATED_WORKFLOWS = {
    ".github/workflows/deploy-web-preview.yml",
    ".github/workflows/cloudflare-seo-preview.yml",
    ".github/workflows/web-parity-test.yml",
    ".github/workflows/submit-blink-indexnow.yml",
    ".github/workflows/seo-check.yml",
}


def relevant(path: str, scope: str) -> bool:
    if not path or path.startswith("/") or ".." in Path(path).parts:
        return True
    if path.startswith(COMMON_PREFIXES) or path in COMMON_ROOTS:
        return True
    if path in COMMON_SCRIPTS:
        return True
    if scope == "android":
        if path.startswith(ANDROID_PREFIXES) or path in ANDROID_SCRIPTS:
            return True
        if path in ANDROID_WORKFLOWS:
            return True
    elif scope == "windows":
        if path.startswith(WINDOWS_PREFIXES) or path in WINDOWS_SCRIPTS:
            return True
        if path in WINDOWS_WORKFLOWS:
            return True
    else:
        return True
    if path.startswith(".github/workflows/"):
        return path not in UNRELATED_WORKFLOWS
    if path.startswith("scripts/"):
        # Unknown test/helper script might be invoked indirectly.
        return True
    if path.startswith(".github/"):
        return path.startswith(".github/actions/")
    if path.startswith(("web/", "docs/", "design/", "assets/")):
        return False
    if path in {"README.md", "LICENSE", ".gitignore", "AGENTS.md"}:
        return False
    # Unknown root inputs might affect generated binaries or their tests.
    return True


def run_git(*args: str) -> str:
    return subprocess.check_output(
        ["git", *args], stderr=subprocess.PIPE, text=True, timeout=18
    ).strip()


def event_source(event: str, payload: dict[str, Any], head: str,
                 git=run_git) -> tuple[str, int | None]:
    """The candidate MUST be a direct tested predecessor, not arbitrary SHA."""
    if event == "pull_request":
        pr = payload["pull_request"]
        if (pr["head"]["repo"]["full_name"] != REPO or
                pr["base"]["repo"]["full_name"] != REPO):
            raise ValueError("fork or wrong repository: run tests")
        prhead = pr["head"]["sha"]
        if not SHA_RE.fullmatch(prhead):
            raise ValueError("invalid PR head SHA")
        # The PR checkout might be a synthetic merge commit. First parent of
        # the actual PR *source* commit is its previous revision.
        parent = git("rev-parse", prhead + "^1")
        return parent, int(pr["number"])
    if event == "push":
        if payload["ref"] != "refs/heads/main":
            raise ValueError("not a main push")
        parents = git("rev-list", "--parents", "-n", "1", head).split()
        if len(parents) != 3:
            raise ValueError("only 2-parent verified PR merges support reuse")
        return parents[2], None
    raise ValueError("only PRs and main merges support reuse")


def validate_evidence(checks: list[dict], job: dict, tested_sha: str,
                      scope: str, pr_number: int | None) -> bool:
    if not SHA_RE.fullmatch(tested_sha):
        return False
    linked = [
        c for c in checks
        if c.get("name") == JOB_NAME[scope]
        and c.get("app", {}).get("slug") == "github-actions"
        and c.get("head_sha") == tested_sha
        and (pr_number is None and bool(c.get("pull_requests"))
             or pr_number is not None and any(
                p.get("number") == pr_number for p in c.get("pull_requests", [])))
    ]
    if not linked:
        return False
    # A failed newer retry must INVALIDATE an earlier passing check.
    newest = max(linked, key=lambda c: int(c.get("id", 0)))
    if newest.get("status") != "completed" or newest.get("conclusion") != "success":
        return False
    if int(job.get("id", -1)) != int(newest["id"]):
        return False
    if (job.get("conclusion") != "success" or job.get("status") != "completed"):
        return False
    successful = {step["name"] for step in job.get("steps", [])
                  if step.get("status") == "completed" and step.get("conclusion") == "success"}
    return set(REQUIRED_STEPS[scope]).issubset(successful)


def request_api(path: str, token: str) -> dict:
    req = Request(
        "https://api.github.com/repos/" + REPO + path,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": "Bearer " + token,
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "blink-trusted-ci-reuse",
        },
    )
    with urlopen(req, timeout=12) as response:
        return json.load(response)


def decide(scope: str, event: str, payload: dict, head: str, token: str,
           fetch=request_api, git=run_git) -> tuple[bool, str]:
    if scope not in REQUIRED_STEPS or not SHA_RE.fullmatch(head):
        return False, "Unknown suite or SHA"
    if not token or os.getenv("GITHUB_REPOSITORY") != REPO:
        return False, "Repository or read-only API credentials unavailable"
    try:
        # event_source deliberately uses local Git ancestry; never accept
        # user-supplied SHA/branch as a previously-tested revision.
        tested_sha, pr_number = event_source(event, payload, head, git)
        if not SHA_RE.fullmatch(tested_sha) or tested_sha == head:
            return False, "Invalid predecessor"
        changed = git("diff", "--name-only", "--no-renames", tested_sha, head).splitlines()
        affected = [p for p in changed if relevant(p, scope)]
        if affected:
            return False, "Changed build/test inputs: " + ", ".join(affected[:5])
        checks = fetch("/commits/" + tested_sha + "/check-runs?per_page=100", token)
        results = checks.get("check_runs", [])
        if int(checks.get("total_count", len(results))) > len(results):
            return False, "Incomplete prior check-run history; run full suite"
        candidates = [
            c for c in results
            if c.get("name") == JOB_NAME[scope]
            and c.get("head_sha") == tested_sha
            and c.get("app", {}).get("slug") == "github-actions"
            and (pr_number is None and bool(c.get("pull_requests"))
                 or pr_number is not None and any(
                    p.get("number") == pr_number for p in c.get("pull_requests", [])))
        ]
        if not candidates:
            return False, "No prior linked required check on predecessor"
        newest = max(candidates, key=lambda c: int(c.get("id", 0)))
        if newest.get("status") != "completed" or newest.get("conclusion") != "success":
            return False, "Previous required check is pending/failed"
        prior_job = fetch("/actions/jobs/" + str(newest["id"]), token)
        if not validate_evidence(checks.get("check_runs", []), prior_job,
                                 tested_sha, scope, pr_number):
            return False, "Earlier run did not actually execute and pass every expensive step"
        return True, "Verified prior full " + scope + " suite on " + tested_sha[:12] + "; identical input files"
    except (ValueError, KeyError, IndexError, OSError, subprocess.SubprocessError,
            HTTPError, URLError, TimeoutError, TypeError) as exc:
        return False, "Cannot verify previous evidence (" + type(exc).__name__ + "); run full suite"


def main() -> None:
    scope = os.getenv("BLINK_CI_SCOPE", "")
    head = run_git("rev-parse", "HEAD")
    try:
        payload = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
        reuse, reason = decide(
            scope, os.getenv("GITHUB_EVENT_NAME", ""), payload, head,
            os.getenv("GITHUB_TOKEN", ""),
        )
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as exc:
        reuse, reason = False, "Could not read trusted CI inputs: " + type(exc).__name__
    print("BLINK expensive " + scope + " CI: " + ("REUSE" if reuse else "RUN") + " — " + reason)
    if os.getenv("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf8") as f:
            f.write("reuse=" + str(reuse).lower() + "\n")
    if os.getenv("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf8") as f:
            f.write("\n### Verified " + scope + " build evidence\n" +
                    ("Reused prior fully successful, unchanged-input suite." if reuse else
                     "Running the full suite; no reusable verified predecessor found.") +
                    "\n\n" + reason.replace("|", " ").replace("\n", " ") + "\n")
    # Fail OPEN into full testing, NEVER falsely pass or skip tests on errors.


if __name__ == "__main__":
    main()
