#!/usr/bin/env python3
"""Parse every tracked Supabase migration with PostgreSQL's parser.

This catches SQL syntax errors without pretending the repository's historical
migration folder is a complete from-scratch schema baseline.
"""

from __future__ import annotations

import sys
from pathlib import Path

from pglast import parser

ROOT = Path(__file__).resolve().parents[1]
MIGRATIONS = ROOT / "supabase" / "migrations"


def main() -> int:
    failures: list[str] = []
    files = sorted(MIGRATIONS.glob("*.sql"))
    if not files:
        print("No Supabase migrations found.")
        return 0

    for path in files:
        sql = path.read_text(encoding="utf-8", errors="strict")
        try:
            parser.parse_sql(sql)
        except Exception as exc:  # pglast exposes parser-specific exception types across versions
            failures.append(f"{path.relative_to(ROOT)}: {exc}")

    if failures:
        print("Supabase SQL syntax validation FAILED:", file=sys.stderr)
        for item in failures:
            print(f"  - {item}", file=sys.stderr)
        return 1

    print(f"Supabase SQL syntax validation passed for {len(files)} migration(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
