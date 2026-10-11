import hashlib
import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location("publisher", Path(__file__).with_name("publish_verified_apk.py"))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class ReleaseIntegrityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / "app.apk"
        with zipfile.ZipFile(self.apk, "w") as archive:
            for name in ("AndroidManifest.xml", "classes.dex", "resources.arsc"):
                archive.writestr(name, b"test payload")
        self.sha = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        self.info = self.root / "release-info.txt"
        self.info.write_text("applicationId=com.aistudio.blink.appvtwo\nversionCode=123\n"
                             "versionName=1.0.123\ncommit=abc\nproductionSource=main\n"
                             f"signingCertificateSha256={'a' * 64}\napkSha256={self.sha}\n")
        self.calls = []
        self.assets = {}
        self.corrupt = False
        self.published = False
        self.download_failure = False

    def fake_gh(self, repo, *args, check=True):
        self.calls.append(args)
        if args[0] == "view":
            if self.published:
                return subprocess.CompletedProcess(args, 0, '{"isDraft":false,"targetCommitish":"abc"}', '')
            return subprocess.CompletedProcess(args, 1, '', 'release not found')
        if args[0] == "upload":
            self.assets = {Path(p).name: Path(p).read_bytes() for p in args[2:-1]}
        if args[0] == "download":
            if self.download_failure:
                raise subprocess.CalledProcessError(1, args)
            dest = Path(args[args.index("--dir") + 1])
            dest.mkdir()
            for name, content in self.assets.items():
                if self.corrupt and name.endswith(".apk"):
                    content = content[:-1] + bytes([content[-1] ^ 1])
                (dest / name).write_bytes(content)
        return subprocess.CompletedProcess(args, 0, '', '')

    def run_publish(self):
        with patch.object(publisher, "gh", self.fake_gh):
            publisher.publish(self.root, "owner/repo", "abc")

    def test_valid_release_is_promoted_only_after_download(self):
        self.run_publish()
        self.assertEqual([call[0] for call in self.calls], ["view", "create", "upload", "download", "edit"])
        self.assertIn("--draft", self.calls[1])
        self.assertIn("--draft=false", self.calls[-1])

    def test_damaged_download_is_never_published(self):
        self.corrupt = True
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            self.run_publish()
        self.assertNotIn("edit", [call[0] for call in self.calls])

    def test_failed_download_is_never_published(self):
        self.download_failure = True
        with self.assertRaises(subprocess.CalledProcessError):
            self.run_publish()
        self.assertNotIn("edit", [call[0] for call in self.calls])

    def test_public_assets_are_never_overwritten(self):
        self.published = True
        with self.assertRaisesRegex(ValueError, "overwrite"):
            self.run_publish()
        self.assertEqual(len(self.calls), 1)

    def test_missing_checksum_fails_before_network(self):
        self.info.write_text(self.info.read_text().replace(f"apkSha256={self.sha}\n", ""))
        with self.assertRaisesRegex(ValueError, "mandatory"):
            self.run_publish()
        self.assertEqual(self.calls, [])

    def test_wrong_source_commit_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "successful main build"):
            publisher.metadata(self.info, "other")

    def test_duplicate_metadata_is_rejected(self):
        self.info.write_text(self.info.read_text() + "versionCode=999\n")
        with self.assertRaisesRegex(ValueError, "duplicate"):
            publisher.metadata(self.info, "abc")

    def test_ambiguous_artifacts_are_rejected(self):
        shutil.copyfile(self.apk, self.root / "second.apk")
        with self.assertRaisesRegex(ValueError, "exactly one"):
            self.run_publish()

    def test_corrupt_zip_is_rejected_even_with_matching_hash(self):
        content = self.apk.read_bytes().replace(b"test payload", b"bad! payload", 1)
        self.apk.write_bytes(content)
        with self.assertRaisesRegex(ValueError, "ZIP integrity"):
            publisher.verify_apk(self.apk, hashlib.sha256(content).hexdigest())

    def test_non_apk_zip_is_rejected(self):
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("hello.txt", "not an APK")
        with self.assertRaisesRegex(ValueError, "required Android files"):
            publisher.verify_apk(self.apk, hashlib.sha256(self.apk.read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()
