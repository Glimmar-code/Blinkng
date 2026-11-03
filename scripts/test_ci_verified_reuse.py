#!/usr/bin/env python3
"""Tests for exact-input, fail-closed CI evidence reuse. No network needed."""
from __future__ import annotations
import importlib.util
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "ci_verified_reuse", Path(__file__).with_name("ci_verified_reuse.py"))
reuse = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(reuse)

PR_HEAD = "a" * 40
OLD = "b" * 40
CHECKOUT = "c" * 40
OTHER = "d" * 40
MAIN = "e" * 40
FP = "f" * 64


def event():
    return {"pull_request": {
        "head": {"sha": PR_HEAD, "repo": {"full_name": reuse.REPO}},
        "base": {"repo": {"full_name": reuse.REPO}},
        "number": 517,
    }}


def checks(scope="android", status="success", id=21, pr=517, sha=OLD):
    return [{"id": id, "name": reuse.JOB_NAME[scope],
             "app": {"slug": "github-actions"}, "head_sha": sha,
             "status": "completed", "conclusion": status,
             "pull_requests": [{"number": pr}]}]


def job(scope="android", id=21, sha=OLD):
    names = [*reuse.REQUIRED_STEPS[scope],
             reuse.DIGEST_LABEL.format(scope=scope, digest=FP)]
    return {"id": id, "head_sha": sha, "status": "completed",
            "conclusion": "success",
            "steps": [{"name": name, "status": "completed",
                       "conclusion": "success"} for name in names]}


class TestInputScope(unittest.TestCase):
    def test_android_sources_and_dependency_changes_invalidate(self):
        for path in ("app/src/main/Screen.kt", "shared/src/Common.kt",
                     "supabase/migrations/123.sql", "gradle/libs.versions.toml",
                     "app/src/main/res/drawable/logo.png",
                     "scripts/ci_verified_reuse.py",
                     ".github/scripts/testlab_batch_queue.cjs",
                     ".github/workflows/android-build-check.yml",
                     "app/build.gradle.kts", ".github/workflows/unknown-new.yml"):
            with self.subTest(path=path):
                self.assertTrue(reuse.relevant(path, "android"))

    def test_known_unrelated_inputs_do_not_invalidate(self):
        for path in ("docs/notes.md", "web/index.html",
                     "web/landing.css", ".github/workflows/deploy-web-preview.yml",
                     ".github/workflows/web-parity-test.yml", "README.md"):
            for scope in ("windows", "android"):
                with self.subTest(path=path, scope=scope):
                    self.assertFalse(reuse.relevant(path, scope))

    def test_windows_packaging_invalidation(self):
        for path in ("desktopApp/src/main/kotlin/Chat.kt",
                     "shared/src/Common.kt", "game3d/project.godot",
                     "scripts/check_windows_parity.py",
                     ".github/workflows/windows-desktop-check.yml"):
            with self.subTest(path=path):
                self.assertTrue(reuse.relevant(path, "windows"))


class TestActualFingerprint(unittest.TestCase):
    def test_exact_blob_and_path_changes_invalidate_the_digest(self):
        base = (b"100644 blob " + b"a" * 40 + b"\tapp/src/main/Foo.kt\0"
                + b"100644 blob " + b"b" * 40 + b"\tREADME.md\0")
        changing_doc = base.replace(b"b" * 40, b"c" * 40)
        changing_source = base.replace(b"a" * 40, b"d" * 40)
        with patch.object(reuse.subprocess, "check_output") as mock:
            mock.return_value = base
            first = reuse.fingerprint_inputs("android")
            mock.return_value = changing_doc
            second = reuse.fingerprint_inputs("android")
            mock.return_value = changing_source
            third = reuse.fingerprint_inputs("android")
        self.assertEqual(first, second)
        self.assertNotEqual(first, third)
        self.assertEqual(len(first), 64)


class TestSourceEvidence(unittest.TestCase):
    def test_requires_successful_real_steps_and_exact_sha(self):
        self.assertTrue(reuse.validate_evidence(checks(), job(), OLD, "android", 517, FP))
        self.assertTrue(reuse.validate_evidence(checks(), job(sha=OTHER),
                                                OLD, "android", 517, FP))
        self.assertFalse(reuse.validate_evidence(checks(pr=518), job(),
                                                 OLD, "android", 517, FP))
        self.assertFalse(reuse.validate_evidence(checks(sha=OTHER), job(),
                                                 OLD, "android", 517, FP))
        self.assertFalse(reuse.validate_evidence(checks(status="failure"), job(),
                                                 OLD, "android", 517, FP))
        self.assertFalse(reuse.validate_evidence(checks(), job(id=19),
                                                 OLD, "android", 517, FP))
        no_steps = job()
        no_steps["steps"][0]["conclusion"] = "skipped"
        self.assertFalse(reuse.validate_evidence(checks(), no_steps,
                                                 OLD, "android", 517, FP))
        missing_step = job()
        missing_step["steps"] = missing_step["steps"][:-1]
        self.assertFalse(reuse.validate_evidence(checks(), missing_step,
                                                 OLD, "android", 517, FP))

    def test_forged_or_missing_digest_does_not_count(self):
        self.assertFalse(reuse.validate_evidence(
            checks(), job(), OLD, "android", 517, "0" * 64))
        evidence = job()
        evidence["steps"] = evidence["steps"][:-1]
        self.assertFalse(reuse.validate_evidence(
            checks(), evidence, OLD, "android", 517, FP))

    def test_new_failure_supersedes_earlier_success(self):
        self.assertFalse(reuse.validate_evidence(
            checks(id=20) + checks(id=21, status="failure"),
            job(id=20), OLD, "android", 517, FP))

    def test_windows_requires_actual_installer_steps(self):
        self.assertTrue(reuse.validate_evidence(
            checks("windows"), job("windows"), OLD, "windows", 517, FP))
        invalid = job("windows")
        invalid["steps"][-1]["conclusion"] = "skipped"
        self.assertFalse(reuse.validate_evidence(
            checks("windows"), invalid, OLD, "windows", 517, FP))


class TestEndToEndDecision(unittest.TestCase):
    def setup_case(self, scope="android", files="", state="success"):
        paths = ["docs/new-note.md"] if files == "" else [files]
        responses = {
            "/commits/" + OLD + "/check-runs?per_page=100":
                {"total_count": 1, "check_runs": checks(scope=scope, status=state)},
            "/actions/jobs/21": job(scope),
        }
        paths_seen = []

        def fetch(path, token):
            self.assertEqual(token, "read-token")
            paths_seen.append(path)
            return responses[path]

        def git(*args):
            if args == ("rev-parse", PR_HEAD + "^1"):
                return OLD
            if args == ("rev-list", "--first-parent", "--max-count=24", OLD):
                return OLD
            if args[:4] == ("diff", "--name-only", "--no-renames", OLD):
                self.assertEqual(args[-1], CHECKOUT)
                return "\n".join(paths)
            raise AssertionError(f"Unexpected git invocation {args}")

        return fetch, git, responses, paths_seen

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": reuse.REPO})
    def test_unrelated_main_change_reuses_completed_android_suite(self):
        fetch, git, _, paths_seen = self.setup_case()
        ok, reason = reuse.decide("android", "pull_request", event(), CHECKOUT,
                                  "read-token", fetch=fetch, git=git, fingerprint=lambda scope: FP)
        self.assertTrue(ok, reason)
        self.assertIn("identical inputs", reason)
        self.assertEqual(len(paths_seen), 2)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": reuse.REPO})
    def test_changed_android_files_do_not_query_or_reuse_previous_evidence(self):
        fetch, git, _, paths_seen = self.setup_case(files="app/src/main/java/Screen.kt")
        ok, reason = reuse.decide("android", "pull_request", event(), CHECKOUT,
                                  "read-token", fetch=fetch, git=git, fingerprint=lambda scope: FP)
        self.assertFalse(ok)
        self.assertIn("Changed build/test inputs", reason)
        self.assertEqual(paths_seen, [])

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": reuse.REPO})
    def test_missing_or_failed_previous_check_fails_closed(self):
        for status in ("failure", "cancelled", "in_progress"):
            with self.subTest(status=status):
                fetch, git, _, _ = self.setup_case(state=status)
                ok, reason = reuse.decide("android", "pull_request", event(), CHECKOUT,
                                          "read-token", fetch=fetch, git=git, fingerprint=lambda scope: FP)
                self.assertFalse(ok, reason)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": reuse.REPO})
    def test_incomplete_api_history_blocks_reuse(self):
        fetch, git, responses, _ = self.setup_case()
        responses["/commits/" + OLD + "/check-runs?per_page=100"]["total_count"] = 500
        ok, reason = reuse.decide("android", "pull_request", event(), CHECKOUT,
                                  "read-token", fetch=fetch, git=git, fingerprint=lambda scope: FP)
        self.assertFalse(ok)
        self.assertIn("Incomplete prior check", reason)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": reuse.REPO})
    def test_newer_failed_retry_blocks_old_success(self):
        fetch, git, responses, _ = self.setup_case()
        responses["/commits/" + OLD + "/check-runs?per_page=100"] = {
            "total_count": 2,
            "check_runs": checks(id=21) + checks(status="failure", id=22)
        }
        ok, reason = reuse.decide("android", "pull_request", event(), CHECKOUT,
                                  "read-token", fetch=fetch, git=git, fingerprint=lambda scope: FP)
        self.assertFalse(ok)
        self.assertIn("pending/failed", reason)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": reuse.REPO})
    def test_updated_workflow_or_policy_triggers_new_tests(self):
        for path in (".github/workflows/android-build-check.yml",
                     "scripts/ci_verified_reuse.py",
                     ".github/scripts/restore_google_services.sh"):
            with self.subTest(path=path):
                fetch, git, _, _ = self.setup_case(files=path)
                ok, _ = reuse.decide("android", "pull_request", event(), CHECKOUT,
                                     "read-token", fetch=fetch, git=git, fingerprint=lambda scope: FP)
                self.assertFalse(ok)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": reuse.REPO})
    def test_untrusted_fork_and_no_token_trigger_full_tests(self):
        fetch, git, _, _ = self.setup_case()
        fork = event()
        fork["pull_request"]["head"]["repo"]["full_name"] = "fork/Blinkng"
        ok, _ = reuse.decide("android", "pull_request", fork, CHECKOUT,
                             "read-token", fetch=fetch, git=git, fingerprint=lambda scope: FP)
        self.assertFalse(ok)
        ok, _ = reuse.decide("android", "pull_request", event(), CHECKOUT,
                             "", fetch=fetch, git=git, fingerprint=lambda scope: FP)
        self.assertFalse(ok)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": reuse.REPO})
    def test_main_merge_with_verified_pr_parent_can_reuse(self):
        fetch, _, _, _ = self.setup_case()
        def git(*args):
            if args[:2] == ("rev-list", "--parents"):
                return CHECKOUT + " " + MAIN + " " + OLD
            if args[:2] == ("rev-list", "--first-parent"):
                return OLD
            if args[0] == "diff":
                return "docs/new-note.md"
            raise AssertionError(args)
        ok, reason = reuse.decide(
            "android", "push", {"ref": "refs/heads/main"}, CHECKOUT,
            "read-token", fetch=fetch, git=git, fingerprint=lambda scope: FP)
        self.assertTrue(ok, reason)


    @patch.dict(os.environ, {"GITHUB_REPOSITORY": reuse.REPO})
    def test_many_unrelated_main_merges_reuse_original_full_pass(self):
        fetch, base_git, responses, _ = self.setup_case()
        older = "9" * 40
        recent = checks(id=22)
        responses["/commits/" + OLD + "/check-runs?per_page=100"] = {
            "total_count": 1, "check_runs": recent,
        }
        # A completed check which skipped costly steps is NOT full evidence.
        responses["/actions/jobs/22"] = {
            **job(id=22),
            "steps": [{"name": "Check reuse status", "status": "completed",
                       "conclusion": "success"}]
        }
        responses["/commits/" + older + "/check-runs?per_page=100"] = {
            "total_count": 1,
            "check_runs": checks(id=31, sha=older),
        }
        responses["/actions/jobs/31"] = job(id=31, sha=older)

        def git(*args):
            if args == ("rev-list", "--first-parent", "--max-count=24", OLD):
                return OLD + "\n" + older
            if args[:3] == ("diff", "--name-only", "--no-renames"):
                return "docs/unrelated.md"
            return base_git(*args)

        ok, reason = reuse.decide("android", "pull_request", event(), CHECKOUT,
                                  "read-token", fetch=fetch, git=git, fingerprint=lambda scope: FP)
        self.assertTrue(ok, reason)
        self.assertIn(older[:12], reason)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": reuse.REPO})
    def test_newer_failed_check_blocks_older_full_pass(self):
        fetch, base_git, responses, seen = self.setup_case()
        older = "9" * 40
        responses["/commits/" + OLD + "/check-runs?per_page=100"] = {
            "total_count": 1, "check_runs": checks(status="failure")
        }
        responses["/commits/" + older + "/check-runs?per_page=100"] = {
            "total_count": 1, "check_runs": checks(sha=older),
        }

        def git(*args):
            if args == ("rev-list", "--first-parent", "--max-count=24", OLD):
                return OLD + "\n" + older
            return base_git(*args)

        ok, reason = reuse.decide("android", "pull_request", event(), CHECKOUT,
                                  "read-token", fetch=fetch, git=git, fingerprint=lambda scope: FP)
        self.assertFalse(ok, reason)
        self.assertNotIn("/commits/" + older + "/check-runs?per_page=100", seen)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": reuse.REPO})
    def test_relevant_changes_between_original_pass_and_now_block_reuse(self):
        fetch, base_git, responses, _ = self.setup_case()
        older = "9" * 40
        responses["/commits/" + OLD + "/check-runs?per_page=100"] = {
            "total_count": 0, "check_runs": []
        }
        def git(*args):
            if args == ("rev-list", "--first-parent", "--max-count=24", OLD):
                return OLD + "\n" + older
            if args[:4] == ("diff", "--name-only", "--no-renames", older):
                return "shared/src/commonMain/kotlin/Contract.kt"
            return base_git(*args)
        ok, reason = reuse.decide("android", "pull_request", event(), CHECKOUT,
                                  "read-token", fetch=fetch, git=git, fingerprint=lambda scope: FP)
        self.assertFalse(ok, reason)
        self.assertIn("Changed build/test inputs", reason)

    @patch.dict(os.environ, {"GITHUB_REPOSITORY": reuse.REPO})
    def test_squash_or_untrusted_main_push_runs_full_tests(self):
        fetch, _, _, _ = self.setup_case()
        def git(*args):
            return CHECKOUT + " " + MAIN
        ok, _ = reuse.decide(
            "android", "push", {"ref": "refs/heads/main"}, CHECKOUT,
            "read-token", fetch=fetch, git=git, fingerprint=lambda scope: FP)
        self.assertFalse(ok)


if __name__ == "__main__":
    unittest.main()
