#!/usr/bin/env python3
"""Fail-closed production release gate: publish only after same-commit checks pass.

The signed-release workflow can complete before Android release installation smoke.
This checker waits for every mandatory main-branch gate before the separate APK
publisher makes a release visible to users.
"""
import argparse
import json
import os
import subprocess
import time

MANDATORY_WORKFLOWS = (
    "Android quality gate",
    "Android release smoke test",
    "Android runtime smoke",
    "Windows desktop quality gate",
    "Supabase safety gate",
    "Secret leak guard",
)


class GateFailure(RuntimeError):
    pass


def github_json(path):
    response = subprocess.run(
        ["gh", "api", "--method", "GET", path],
        check=True, capture_output=True, text=True,
    )
    return json.loads(response.stdout)


def inspect(repo, commit, fetch=github_json):
    if not (repo and commit and len(commit) == 40 and
            all(c in "0123456789abcdefABCDEF" for c in commit)):
        raise GateFailure("Invalid repository or source commit")
    current = fetch(f"repos/{repo}/branches/main")["commit"]["sha"]
    if current != commit:
        raise GateFailure(
            f"Release commit {commit[:12]} is no longer main HEAD ({current[:12]}); "
            "an older APK must not replace a newer release"
        )
    response = fetch(f"repos/{repo}/actions/runs?head_sha={commit}&per_page=100")
    if response.get("total_count", 0) > 100:
        raise GateFailure("Too many workflow runs to verify completely")
    runs = [
        run for run in response.get("workflow_runs", [])
        if run.get("head_sha") == commit
        and run.get("head_branch") == "main"
        and run.get("event") == "push"
    ]
    pending = []
    for workflow in MANDATORY_WORKFLOWS:
        matches = [r for r in runs if r.get("name") == workflow]
        if not matches:
            pending.append(f"{workflow} (missing)")
            continue
        # Prefer the most recent retry, not an earlier succeeded or failed run.
        latest = max(matches, key=lambda r: (r.get("run_attempt", 1), r.get("id", 0)))
        if latest.get("status") != "completed":
            pending.append(f"{workflow} ({latest.get('status', 'unknown')})")
            continue
        if latest.get("conclusion") != "success":
            raise GateFailure(
                f"{workflow} did not pass: {latest.get('conclusion')} "
                f"({latest.get('html_url', 'no URL')})"
            )
    return pending


def wait_until_green(repo, commit, timeout_seconds, interval_seconds=25,
                     fetch=github_json, sleep=time.sleep, clock=time.monotonic):
    deadline = clock() + timeout_seconds
    while True:
        pending = inspect(repo, commit, fetch=fetch)
        if not pending:
            print(f"Every main-commit release gate passed for {commit}", flush=True)
            return
        print("Release blocked: waiting for " + "; ".join(pending), flush=True)
        if clock() + interval_seconds > deadline:
            raise GateFailure(
                "Mandatory release tests did not all pass before timeout; "
                "the APK will NOT be published"
            )
        sleep(interval_seconds)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--timeout-seconds", type=int, default=2700)
    args = parser.parse_args()
    if args.timeout_seconds < 1:
        parser.error("timeout must be positive")
    if not os.environ.get("GH_TOKEN"):
        parser.error("GH_TOKEN is required")
    wait_until_green(args.repository, args.commit, args.timeout_seconds)


if __name__ == "__main__":
    main()
