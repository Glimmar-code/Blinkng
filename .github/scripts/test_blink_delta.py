import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("blink_delta", Path(__file__).with_name("blink_delta.py"))
delta = importlib.util.module_from_spec(spec)
spec.loader.exec_module(delta)


class BlinkDeltaTests(unittest.TestCase):
    def test_skipping_ten_updates_reconstructs_latest_exactly(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            # Incompressible data makes COPY instructions and byte shifting meaningful.
            blocks = b"".join(hashlib.sha256(i.to_bytes(4, "big")).digest()
                              for i in range(12_000))
            current = root / "new.apk"
            current.write_bytes(b"new-preface" + blocks + b"latest-release")
            expected = delta.digest(current)
            manifest_rows = []
            for version in range(101, 111):
                base = root / f"v{version}.apk"
                base.write_bytes(b"old-preface" + blocks[:version * 200] +
                                 b"changed-byte-offset" + blocks[version * 200:] +
                                 b"old-build")
                patch = root / f"delta-{version}.bdp"
                delta.create_patch(base, current, patch)
                output = root / f"out-{version}.apk"
                delta.apply_patch(base, patch, output)
                self.assertEqual(delta.digest(output), expected)
                manifest_rows.append((version, delta.digest(base),
                                      patch.name, delta.digest(patch), patch.stat().st_size))
            text = delta.manifest(111, expected, manifest_rows)
            self.assertEqual(len([line for line in text.splitlines() if line.startswith("base=")]), 10)
            self.assertIn("base=101,", text)

    def test_corruption_truncation_and_bad_commands_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            base, target, patch, output = (root / name for name in
                                           ("base.apk", "target.apk", "patch.bdp", "output.apk"))
            base.write_bytes(b"A" * 100_000 + b"B" * 100_000)
            target.write_bytes(b"new" + base.read_bytes() + b"latest")
            delta.create_patch(base, target, patch)
            delta.apply_patch(base, patch, output)
            self.assertEqual(target.read_bytes(), output.read_bytes())
            patch.write_bytes(patch.read_bytes()[:-8])
            with self.assertRaises(ValueError):
                delta.apply_patch(base, patch, output)
            patch.write_bytes(b"WRONG" + patch.read_bytes()[5:])
            with self.assertRaises(ValueError):
                delta.apply_patch(base, patch, output)


if __name__ == "__main__":
    unittest.main()
