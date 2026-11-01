"""Synthetic comparator regression; does NOT claim any actual BLINK screen matches."""
import tempfile
import unittest
from pathlib import Path

from PIL import Image

from visual_reference_audit import audit


class ReferenceComparisonTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.ref = self.root / "reference.png"
        self.actual = self.root / "actual.png"

    def save(self, actual_size=(8, 8), change=False):
        Image.new("RGB", (8, 8), (25, 28, 32)).save(self.ref)
        image = Image.new("RGB", actual_size, (25, 28, 32))
        if change:
            image.putpixel((2, 2), (30, 28, 32))
        image.save(self.actual)

    def test_identical_images_produce_all_evidence(self):
        self.save()
        result = audit(self.ref, self.actual, self.root / "out")
        self.assertTrue(result["exact_visual_approval"])
        for filename in ("reference.png", "actual.png", "overlay.png",
                         "difference.png", "report.json"):
            self.assertTrue((self.root / "out" / filename).is_file())

    def test_single_pixel_mismatch_blocks_exact_approval(self):
        self.save(change=True)
        result = audit(self.ref, self.actual, self.root / "out")
        self.assertEqual(result["mismatched_pixels"], 1)
        self.assertFalse(result["exact_visual_approval"])

    def test_mismatched_dimensions_fail_closed(self):
        self.save(actual_size=(10, 10))
        result = audit(self.ref, self.actual, self.root / "out")
        self.assertFalse(result["exact_visual_approval"])
        self.assertIn("Viewport mismatch", result["error"])

    def test_resizing_is_never_an_exact_match(self):
        self.save(actual_size=(10, 10))
        result = audit(self.ref, self.actual, self.root / "out", resize_actual=True)
        self.assertTrue(result["actual_resized"])
        self.assertFalse(result["exact_visual_approval"])


if __name__ == "__main__":
    unittest.main()
