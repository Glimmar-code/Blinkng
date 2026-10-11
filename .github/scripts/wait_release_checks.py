#!/usr/bin/env python3
"""Publication fails closed unless every required workflow passed for this exact SHA."""
import argparse
import json
import subprocess
import time

REQUIRED = (
    'android-build-check.yml', 'android-runtime-smoke.yml', 'release-smoke-test.yml',
    'windows-desktop-check.yml', 'supabase-safety-check.yml', 'secret-leak-guard.yml',
)


def gate(runs, commit):
    latest = {}
    for run in runs:
        if run.get('head_sha') != commit or run.get('head_branch') != 'main':
            continue
        if run.get('event') not in ('push', 'workflow_dispatch'):
            continue
        path = run.get('path', '').split('/')[-1].split('@')[0]
        if path not in REQUIRED:
            continue
        if path not in latest or run['id'] > latest[path]['id']:
            latest[path] = run
    pending = []
    for path in REQUIRED:
        run = latest.get(path)
        if not run or run['status'] != 'completed':
            pending.append(path)
        elif run['conclusion'] != 'success':
            raise ValueError(f"Required release check did not pass: {path}: {run['conclusion']}")
    return pending


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--timeout', type=int, default=2700)
    args = parser.parse_args()
    deadline = time.monotonic() + args.timeout
    while True:
        pages = json.loads(subprocess.check_output([
            'gh', 'api', '--paginate', '--slurp',
            f'repos/{args.repository}/actions/runs?head_sha={args.commit}&per_page=100'
        ], text=True))
        pending = gate([r for p in pages for r in p['workflow_runs']], args.commit)
        if not pending:
            print('All required checks passed for exact release commit ' + args.commit)
            return
        if time.monotonic() >= deadline:
            raise RuntimeError('Release blocked: missing or unfinished checks: ' + ', '.join(pending))
        print('Waiting for: ' + ', '.join(pending), flush=True)
        time.sleep(20)


if __name__ == '__main__':
    main()
