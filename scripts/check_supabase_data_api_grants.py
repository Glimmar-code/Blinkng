#!/usr/bin/env python3
"""Fail CI when a new public table has no explicit Data API permissions.

From 2026-10-30, new public tables do not automatically inherit API grants.
Review only new-generation migration files; legacy schema replay needs a
separate compatibility audit before a complete database reset.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

IDENT = r'(?:"(?:[^"]|"")*"|[a-z_][a-z_0-9$]*)'
RELATION = rf'(?:(?P<schema>{IDENT})\s*\.\s*)?(?P<table>{IDENT})'
CREATE = re.compile(rf'\bCREATE\s+(?:UNLOGGED\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?{RELATION}', re.I)
ALTER_RLS = re.compile(rf'\bALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?(?:ONLY\s+)?{RELATION}\s+ENABLE\s+ROW\s+LEVEL\s+SECURITY\b', re.I)
ACCESS = re.compile(rf'\b(?P<verb>GRANT|REVOKE)\s+[^;]*?\bON\s+(?:TABLE\s+)?{RELATION}\s+(?P<direction>TO|FROM)\s+(?P<roles>[^;]+)', re.I)
ROLE = re.compile(r'\b(anon|authenticated|service_role)\b', re.I)
REQUIRED = {'anon', 'authenticated', 'service_role'}
DOLLAR_TAG = re.compile(r'\$[a-z_][a-z_0-9]*\$|\$\$', re.I)


def normalize(name: str | None) -> str:
    if not name:
        return 'public'
    return name[1:-1].replace('""', '"') if name.startswith('"') else name.lower()


def mask_noncode(sql: str) -> str:
    """Hide SQL comments, string literals and dollar-quoted function bodies."""
    chars = list(sql)
    n = len(sql)

    def mask(start: int, end: int) -> None:
        for j in range(start, end):
            if chars[j] != '\n':
                chars[j] = ' '

    i = 0
    while i < n:
        if sql.startswith('--', i):
            end = sql.find('\n', i)
            if end < 0:
                end = n
            mask(i, end)
            i = end
        elif sql.startswith('/*', i):
            start = i
            depth = 1
            i += 2
            while i < n and depth:
                if sql.startswith('/*', i):
                    depth += 1
                    i += 2
                elif sql.startswith('*/', i):
                    depth -= 1
                    i += 2
                else:
                    i += 1
            mask(start, i)
        elif sql[i] == "'":
            start = i
            i += 1
            while i < n:
                if sql.startswith("''", i):
                    i += 2
                elif sql[i] == "'":
                    i += 1
                    break
                else:
                    i += 1
            mask(start, i)
        elif sql[i] == '"':
            i += 1
            while i < n:
                if sql.startswith('""', i):
                    i += 2
                elif sql[i] == '"':
                    i += 1
                    break
                else:
                    i += 1
        elif sql[i] == '$':
            tag = DOLLAR_TAG.match(sql, i)
            if tag:
                start = i
                end = sql.find(tag.group(), tag.end())
                i = n if end < 0 else end + len(tag.group())
                mask(start, i)
            else:
                i += 1
        else:
            i += 1
    return ''.join(chars)


def check_sql(sql: str) -> list[str]:
    clean = mask_noncode(sql)
    created = {
        normalize(m.group('table')) for m in CREATE.finditer(clean)
        if normalize(m.group('schema')) == 'public'
    }
    if not created:
        return []

    rls = {
        normalize(m.group('table')) for m in ALTER_RLS.finditer(clean)
        if normalize(m.group('schema')) == 'public'
    }
    decisions = {table: set() for table in created}
    for m in ACCESS.finditer(clean):
        if normalize(m.group('schema')) != 'public':
            continue
        table = normalize(m.group('table'))
        if table not in decisions:
            continue
        if (m.group('verb').upper(), m.group('direction').upper()) not in {
            ('GRANT', 'TO'), ('REVOKE', 'FROM')
        }:
            continue
        decisions[table].update(role.lower() for role in ROLE.findall(m.group('roles')))

    errors = []
    for table in sorted(created):
        if table not in rls:
            errors.append(f'public.{table}: missing ENABLE ROW LEVEL SECURITY')
        missing = REQUIRED - decisions[table]
        if missing:
            errors.append(
                f'public.{table}: missing explicit table GRANT/REVOKE for '
                f'{", ".join(sorted(missing))}; choose least privilege, not blanket grants'
            )
    return errors


def main() -> int:
    from check_supabase_safety import ROOT, changed_files, determine_base

    base = determine_base()
    migrations = []
    grandfathered = 0
    for status, filename in changed_files(base):
        if not filename.startswith('supabase/migrations/') or not filename.endswith('.sql'):
            continue
        if status.startswith('D'):
            continue  # deletion already blocked by existing safety gate
        if Path(filename).name[:8] < '20261020':
            grandfathered += 1
            continue  # legacy migration inventory needs separate clean-rebuild audit
        migrations.append(ROOT / filename)

    failures = []
    for path in migrations:
        if not path.is_file():
            continue
        for error in check_sql(path.read_text(encoding='utf-8')):
            failures.append(f'{path.relative_to(ROOT)}: {error}')

    if failures:
        print('Supabase Data API privilege gate FAILED:', file=sys.stderr)
        for failure in failures:
            print(f'  - {failure}', file=sys.stderr)
        return 1

    if grandfathered:
        print(f'NOTICE: {grandfathered} legacy migration(s) not checked; audit before full db reset.')
    print(f'Supabase Data API privilege gate passed ({len(migrations)} migration(s) checked).')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
