import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "ios_testflight_preflight.py"
spec = importlib.util.spec_from_file_location("ios_testflight_preflight", MODULE_PATH)
assert spec and spec.loader
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class TestReleaseGate(unittest.TestCase):
    def test_actual_repository_source_is_conservative(self):
        root = MODULE_PATH.parents[1]
        self.assertEqual(m.check_source(root), [])

    def test_semantic_version_and_build(self):
        self.assertEqual(m.check_version("0.1.0", "1"), [])
        self.assertEqual(m.check_version("1.12.345", "1999"), [])
        for version in ("bad", "1.1", "1.1.1-beta", "../1.2.3", "1.2.3.4"):
            self.assertTrue(m.check_version(version, "1"), version)
        for build in ("0", "-1", "a", "1.0", "9999999999"):
            self.assertTrue(m.check_version("1.0.0", build), build)

    def test_brand_and_release_flags_fail_closed(self):
        root = MODULE_PATH.parents[1]
        # Audit a temporary copy; never edit tracked files to simulate failure.
        with tempfile.TemporaryDirectory() as d:
            path = Path(d)
            (path / "ios").mkdir()
            (path / "ios/BLINKiOS/Sources/Calls").mkdir(parents=True)
            (path / "app/src/main/res/drawable").mkdir(parents=True)
            (path / "scripts").mkdir()
            project = (root / "ios/project.yml").read_text()
            (path / "ios/project.yml").write_text(
                project.replace('BLINK_VOIP_PUSH_ENABLED: "NO"',
                                'BLINK_VOIP_PUSH_ENABLED: "YES"')
            )
            (path / "ios/BLINKiOS/Sources/Calls/BLINKVoIPConfiguration.swift").write_text(
                "enabledInBuild"
            )
            (path / "app/src/main/res/drawable/app_icon.png").write_bytes(b"placeholder")
            (path / "scripts/prepare_ios_icon.sh").write_text("safe")
            errors = m.check_source(path)
            self.assertTrue(any("VoIP" in item for item in errors))
            self.assertFalse(any("Bundle ID" in item for item in errors))


if __name__ == "__main__":
    unittest.main()
