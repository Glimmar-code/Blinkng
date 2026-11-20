"""Isolated tests of fast pre-push test selection."""
import unittest

import blink_preflight as p


class PreflightPlanTest(unittest.TestCase):
    def test_docs_do_not_build_platforms(self):
        self.assertEqual(p.affected(["docs/notes.md"])["android"], False)
        checks = p.planned_commands(["docs/notes.md"], full=True)
        self.assertFalse(any("gradle" in c for command in checks for c in command))

    def test_android_changes_get_full_platform_verification_when_requested(self):
        paths = ["app/src/main/java/com/example/ui/screens/BlinkChat.kt"]
        self.assertTrue(p.affected(paths)["android"])
        self.assertTrue(p.affected(paths)["ui"])
        self.assertTrue(any(command[0] == "gradle" and ":app:assembleDebug" in command
                            for command in p.planned_commands(paths, full=True)))
        self.assertFalse(any(command[0] == "gradle" for command in
                             p.planned_commands(paths, full=False)))

    def test_shared_changes_require_both_platforms(self):
        paths = ["shared/src/commonMain/kotlin/Policy.kt"]
        self.assertTrue(p.affected(paths)["windows"])
        self.assertTrue(p.affected(paths)["android"])
        commands = p.planned_commands(paths, full=True)
        self.assertTrue(any(command[:3] == ["gradle", "-p", "desktopApp"] for command in commands))

    def test_visual_tooling_change_is_android_relevant(self):
        self.assertTrue(p.affected(["scripts/testing/visual_reference_audit.py"])["android"])


if __name__ == "__main__":
    unittest.main()
