#!/usr/bin/env python3
"""Synthetic ZIP regression checks; actual APKs are inspected by release CI."""
import tempfile
import unittest
import zipfile
from pathlib import Path

from check_webrtc_apk_integrity import REQUIRED_DEX_CLASSES, NATIVE_SO, inspect_apk


class WebRtcApkIntegrityTest(unittest.TestCase):
    def make_apk(self, path, descriptors=REQUIRED_DEX_CLASSES, native=True):
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("classes.dex", b"\x00".join(descriptors))
            if native:
                archive.writestr("lib/arm64-v8a/" + NATIVE_SO, b"\x7fELF\x01")

    def test_complete_apk_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "blink.apk"
            self.make_apk(path)
            self.assertEqual([], inspect_apk(path))

    def test_r8_removed_class_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "blink.apk"
            self.make_apk(path, descriptors=REQUIRED_DEX_CLASSES[:-1])
            self.assertIn("Missing JNI class", " ".join(inspect_apk(path)))

    def test_missing_arm64_library_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "blink.apk"
            self.make_apk(path, native=False)
            self.assertIn("Missing ARM64", " ".join(inspect_apk(path)))

    def test_bad_apk_fails_safely(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "blink.apk"
            path.write_text("not an apk")
            self.assertIn("could not be inspected", " ".join(inspect_apk(path)))


if __name__ == "__main__":
    unittest.main()
