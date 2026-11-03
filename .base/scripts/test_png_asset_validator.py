#!/usr/bin/env python3
"""Regression: CRC-correct but undecodable images must fail the asset gate."""
import pathlib
import struct
import subprocess
import tempfile
import unittest
import zlib


VALIDATOR = pathlib.Path(__file__).resolve().with_name("ValidatePngAssets.java")


def chunk(kind, payload):
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))


def png(pixel_stream):
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", pixel_stream)
        + chunk(b"IEND", b"")
    )


class PngAssetValidatorTest(unittest.TestCase):
    def validate(self, asset):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            path = root / "app/src/main/res/drawable/brand.png"
            path.parent.mkdir(parents=True)
            path.write_bytes(asset)
            return subprocess.run(
                ["java", str(VALIDATOR), str(root)], capture_output=True, text=True, check=False
            )

    def test_decodable_pixels_pass(self):
        result = self.validate(png(zlib.compress(b"\x00\xff\xff\xff")))
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_correct_crcs_do_not_hide_corrupt_compression(self):
        result = self.validate(png(b"invalid compressed pixel stream"))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("pixel data could not be decoded", result.stderr)
        self.assertNotIn("CRC mismatch", result.stderr)


if __name__ == "__main__":
    unittest.main()
