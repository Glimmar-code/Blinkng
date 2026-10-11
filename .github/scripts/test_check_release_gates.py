#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

path = Path(__file__).with_name("check_release_gates.py")
spec = importlib.util.spec_from_file_location("gates", path)
gates = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gates)

COMMIT = "a" * 40
REPO = "Glimmar-code/Blinkng"


def fixture(overrides=None, main=COMMIT, event="push", sha=COMMIT):
    overrides = overrides or {}
    runs = [
        {"name": workflow, "head_sha": sha, "head_branch": "main",
         "event": event, "status": "completed", "conclusion": "success",
         "run_attempt": 1, "id": n + 1, "html_url": "https://github.com/example"}
        for n, workflow in enumerate(gates.MANDATORY_WORKFLOWS)
    ]
    for index, modifications in overrides.items():
        runs[index].update(modifications)
    def fetch(url):
        if url.endswith("/branches/main"):
            return {"commit": {"sha": main}}
        return {"total_count": len(runs), "workflow_runs": runs}
    return fetch


class GateTests(unittest.TestCase):
    def test_all_checks_green(self):
        self.assertEqual(gates.inspect(REPO, COMMIT, fetch=fixture()), [])

    def test_pending_does_not_pass(self):
        fetch = fixture({0: {"status": "in_progress", "conclusion": None}})
        self.assertIn("Android quality gate (in_progress)", gates.inspect(REPO, COMMIT, fetch))

    def test_failed_check_blocks(self):
        fetch = fixture({1: {"conclusion": "failure"}})
        with self.assertRaisesRegex(gates.GateFailure, "Android release smoke test did not pass"):
            gates.inspect(REPO, COMMIT, fetch)

    def test_skipped_check_blocks(self):
        fetch = fixture({2: {"conclusion": "skipped"}})
        with self.assertRaisesRegex(gates.GateFailure, "did not pass"):
            gates.inspect(REPO, COMMIT, fetch)

    def test_other_commits_do_not_count(self):
        pending = gates.inspect(REPO, COMMIT, fetch=fixture(sha="b" * 40))
        self.assertEqual(len(pending), len(gates.MANDATORY_WORKFLOWS))

    def test_pull_request_event_does_not_count(self):
        pending = gates.inspect(REPO, COMMIT, fetch=fixture(event="pull_request"))
        self.assertEqual(len(pending), len(gates.MANDATORY_WORKFLOWS))

    def test_stale_commit_blocks(self):
        with self.assertRaisesRegex(gates.GateFailure, "no longer main HEAD"):
            gates.inspect(REPO, COMMIT, fetch=fixture(main="b" * 40))

    def test_missing_checks_cannot_publish(self):
        def fetch(url):
            if url.endswith("/branches/main"):
                return {"commit": {"sha": COMMIT}}
            return {"total_count": 0, "workflow_runs": []}
        with self.assertRaisesRegex(gates.GateFailure, "will NOT be published"):
            gates.wait_until_green(REPO, COMMIT, 2, 3, fetch, lambda _: None, lambda: 0)

    def test_last_retry_must_pass(self):
        def fetch(url):
            base = fixture()(url)
            if "workflow_runs" in base:
                latest = dict(base["workflow_runs"][0])
                latest.update({"run_attempt": 2, "id": 99, "status": "completed",
                               "conclusion": "failure"})
                base["workflow_runs"].append(latest)
                base["total_count"] += 1
            return base
        with self.assertRaisesRegex(gates.GateFailure, "did not pass"):
            gates.inspect(REPO, COMMIT, fetch)


if __name__ == "__main__":
    unittest.main()
