#!/usr/bin/env python3
"""Fail CI when new Supabase changes bypass the migration/staging safety rules."""

from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIGRATIONS = ROOT / "supabase" / "migrations"

SCHEMA_DDL = re.compile(
    r"\b(?:CREATE|ALTER|DROP)\s+(?:TABLE|POLICY|FUNCTION|TRIGGER|VIEW|TYPE|SCHEMA)\b|"
    r"\b(?:GRANT|REVOKE)\b",
    re.IGNORECASE,
)
DESTRUCTIVE = re.compile(
    r"\bDROP\s+(?:TABLE|COLUMN|SCHEMA|TYPE)\b|\bTRUNCATE\b|\bDELETE\s+FROM\b",
    re.IGNORECASE,
)
TIMESTAMPED_MIGRATION = re.compile(r"^\d{14}_.+\.sql$")


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def determine_base() -> str:
    base_sha = os.getenv("BASE_SHA", "").strip()
    if base_sha and set(base_sha) != {"0"}:
        return base_sha

    base_ref = os.getenv("GITHUB_BASE_REF", "").strip()
    if base_ref:
        try:
            git("fetch", "origin", base_ref, "--depth=1")
        except subprocess.CalledProcessError:
            pass
        return git("merge-base", "HEAD", f"origin/{base_ref}")

    try:
        return git("rev-parse", "HEAD^")
    except subprocess.CalledProcessError:
        return git("rev-list", "--max-parents=0", "HEAD")


def changed_files(base: str) -> list[tuple[str, str]]:
    output = git("diff", "--name-status", f"{base}...HEAD")
    rows: list[tuple[str, str]] = []
    for line in output.splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        status = parts[0]
        path = parts[-1]
        rows.append((status, path))
    return rows


def main() -> int:
    base = determine_base()
    failures: list[str] = []
    notices: list[str] = []

    for status, relative in changed_files(base):
        if not relative.startswith("supabase/") or not relative.endswith(".sql"):
            continue
        if status.startswith("D"):
            failures.append(f"Do not delete tracked Supabase SQL without an explicit replacement/rollback plan: {relative}")
            continue

        path = ROOT / relative
        if not path.exists():
            continue
        sql = path.read_text(encoding="utf-8", errors="replace")

        is_migration = relative.startswith("supabase/migrations/")
        is_new = status.startswith("A")

        if not is_migration and SCHEMA_DDL.search(sql):
            failures.append(
                f"Schema-changing SQL must be versioned under supabase/migrations/: {relative}"
            )

        if is_migration and is_new and not TIMESTAMPED_MIGRATION.match(path.name):
            failures.append(
                f"New migration must use a 14-digit timestamp prefix (YYYYMMDDHHMMSS_name.sql): {relative}"
            )

        if is_migration and DESTRUCTIVE.search(sql):
            reviewed = "-- destructive-change-reviewed" in sql.lower()
            rollback = "-- rollback-plan:" in sql.lower()
            if not reviewed or not rollback:
                failures.append(
                    f"Destructive migration requires both '-- destructive-change-reviewed' and '-- rollback-plan:' comments: {relative}"
                )
            else:
                notices.append(f"Reviewed destructive migration: {relative}")

    if notices:
        print("Supabase safety notices:")
        for item in notices:
            print(f"  - {item}")

    if failures:
        print("\nSupabase safety gate FAILED:", file=sys.stderr)
        for item in failures:
            print(f"  - {item}", file=sys.stderr)
        print(
            "\nUse Testlab + Supabase staging first. Keep schema changes in versioned migrations and document rollback for destructive operations.",
            file=sys.stderr,
        )
        return 1

    print("Supabase safety gate passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
