#!/usr/bin/env python3
"""Offline regression cases for BLINK's read-only published release auditor."""
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import audit_published_apk as auditor


class PublishedReleaseAuditTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.commit = "d" * 40
        self.tag = "v1.2.3"
        self.release = {"tagName": self.tag, "targetCommitish": self.commit}
        self.apk = self.folder / "Blink-latest.apk"
        with zipfile.ZipFile(self.apk, "w") as archive:
            for path in ("AndroidManifest.xml", "classes.dex", "resources.arsc"):
                archive.writestr(path, b"test")
        self.pack = self.folder / "blink-life-web.zip"
        with zipfile.ZipFile(self.pack, "w") as archive:
            for path in ("index.html", "index.js", "index.wasm", "index.pck"):
                archive.writestr(path, b"test")
        self.apk_hash = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        self.pack_hash = hashlib.sha256(self.pack.read_bytes()).hexdigest()
        self.info = self.folder / "release-info.txt"
        self.info.write_text(
            f"applicationId=com.aistudio.blink.appvtwo\n"
            f"versionCode=12345678\nversionName=1.2.3\n"
            f"commit={self.commit}\nproductionSource=main\n"
            f"signingCertificateSha256={'a' * 64}\n"
            f"apkSha256={self.apk_hash}\n"
            f"gamePackSha256={self.pack_hash}\n"
            f"gamePackSize={self.pack.stat().st_size}\n"
        )
        (self.folder / "Blink-latest.apk.sha256").write_text(
            f"{self.apk_hash}  Blink-latest.apk\n"
        )
        (self.folder / "blink-life-web.zip.sha256").write_text(
            f"{self.pack_hash}  blink-life-web.zip\n"
        )

    def test_valid_published_artifacts(self):
        value = auditor.inspect_download(self.folder, self.release)
        self.assertEqual(value["commit"], self.commit)

    def test_changed_apk_is_detected(self):
        self.apk.write_bytes(self.apk.read_bytes() + b"tampered")
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            auditor.inspect_download(self.folder, self.release)

    def test_wrong_release_tag_blocks_audit(self):
        with self.assertRaisesRegex(ValueError, "tag"):
            auditor.inspect_download(self.folder, {**self.release, "tagName": "v9.9.9"})

    def test_wrong_or_non_sha_source_is_blocked(self):
        with self.assertRaisesRegex(ValueError, "full signed source"):
            auditor.inspect_download(self.folder, {**self.release, "targetCommitish": "main"})
        with self.assertRaisesRegex(ValueError, "successful main build"):
            auditor.inspect_download(self.folder, {**self.release, "targetCommitish": "b" * 40})

    def test_tampered_checksum_manifest_is_blocked(self):
        (self.folder / "Blink-latest.apk.sha256").write_text("fake\n")
        with self.assertRaisesRegex(ValueError, "checksum file"):
            auditor.inspect_download(self.folder, self.release)

    def test_tampered_game_archive_is_blocked(self):
        self.pack.write_bytes(self.pack.read_bytes() + b"extra")
        with self.assertRaisesRegex(ValueError, "Game pack SHA-256 mismatch"):
            auditor.inspect_download(self.folder, self.release)

    def test_extra_asset_or_missing_asset_is_blocked(self):
        (self.folder / "malware.apk").write_bytes(b"fake")
        with self.assertRaisesRegex(ValueError, "unexpected asset set"):
            auditor.inspect_download(self.folder, self.release)
        (self.folder / "malware.apk").unlink()
        self.apk.unlink()
        with self.assertRaisesRegex(ValueError, "unexpected asset set"):
            auditor.inspect_download(self.folder, self.release)

    def test_release_list_rejects_draft_missing_duplicate_and_empty_assets(self):
        base = {"tagName": self.tag, "isDraft": False,
                "assets": [{"name": name, "size": 1} for name in auditor.EXPECTED_ASSETS]}
        with patch.object(auditor, "command", return_value=json.dumps(base)):
            self.assertEqual(auditor.release_snapshot("owner/repo")["tagName"], self.tag)
        for changed in [
            {**base, "isDraft": True},
            {**base, "assets": base["assets"][:-1]},
            {**base, "assets": base["assets"] + [base["assets"][0]]},
            {**base, "assets": [{**asset, "size": 0} for asset in base["assets"]]},
        ]:
            with patch.object(auditor, "command", return_value=json.dumps(changed)):
                with self.assertRaises(ValueError):
                    auditor.release_snapshot("owner/repo")

    def test_public_latest_alias_must_match_downloaded_verified_metadata(self):
        info = {"raw": self.info.read_text()}
        responses = [io.BytesIO(info["raw"].encode()), io.BytesIO(b"PK")]
        with patch.object(auditor.urllib.request, "urlopen", side_effect=responses) as urlopen:
            auditor.verify_public_latest_alias(info)
            self.assertEqual(urlopen.call_count, 2)

    def test_public_alias_tampering_fails_closed(self):
        with patch.object(auditor.urllib.request, "urlopen", side_effect=[
            io.BytesIO(b"not our metadata"),
            io.BytesIO(b"not our metadata"),
            io.BytesIO(b"not our metadata"),
        ]), patch.object(auditor.time, "sleep", return_value=None):
            with self.assertRaisesRegex(RuntimeError, "Latest public download"):
                auditor.verify_public_latest_alias({"raw": self.info.read_text()})


if __name__ == "__main__":
    unittest.main()
