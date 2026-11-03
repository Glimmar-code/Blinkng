import io
import json
import unittest
from unittest.mock import patch
from notify_blink_release import (
    MAX_RELEASE_JSON_BYTES,
    read_url,
    release_alert_payload,
)

COMMIT = "a" * 40

class ReleaseAlertTest(unittest.TestCase):
    def setUp(self):
        self.release = {
            "tag_name": "v1.0.123", "target_commitish": COMMIT,
            "draft": False, "prerelease": False,
            "assets": [{"name": name} for name in (
                "Blink-latest.apk", "Blink-latest.apk.sha256", "release-info.txt")]
        }
        self.info = {
            "versionName": "1.0.123", "versionCode": "123",
            "applicationId": "com.aistudio.blink.appvtwo",
            "commit": COMMIT, "productionSource": "main", "apkSha256": "b" * 64
        }

    def test_valid_release_has_no_installer_url_or_private_user_data(self):
        alert = release_alert_payload(self.release, self.info, COMMIT)
        self.assertEqual(alert["message"]["topic"], "blink_android_website_releases")
        self.assertEqual(alert["message"]["data"]["version_code"], "123")
        self.assertEqual(set(alert["message"]["data"]), {"type", "version_code", "version_name"})

    def test_rejects_wrong_main_commit(self):
        self.release["target_commitish"] = "c" * 40
        with self.assertRaises(ValueError):
            release_alert_payload(self.release, self.info, COMMIT)

    def test_rejects_draft_or_missing_integrity(self):
        self.release["draft"] = True
        with self.assertRaises(ValueError):
            release_alert_payload(self.release, self.info, COMMIT)
        self.release["draft"] = False
        self.info["apkSha256"] = "oops"
        with self.assertRaises(ValueError):
            release_alert_payload(self.release, self.info, COMMIT)

    def test_rejects_mismatched_version_or_package(self):
        self.release["tag_name"] = "v1.0.999"
        with self.assertRaises(ValueError):
            release_alert_payload(self.release, self.info, COMMIT)
        self.release["tag_name"] = "v1.0.123"
        self.info["applicationId"] = "someone.else"
        with self.assertRaises(ValueError):
            release_alert_payload(self.release, self.info, COMMIT)

    def test_large_valid_github_release_json_is_read_completely(self):
        # Regression: Oct 19 release contained >8 KiB of asset metadata.
        release = {"tag_name": "v1.0.123", "assets": [{"id": i, "name": "file"} for i in range(320)]}
        encoded = json.dumps(release).encode()
        self.assertGreater(len(encoded), 8192)
        self.assertLess(len(encoded), MAX_RELEASE_JSON_BYTES)
        with patch("notify_blink_release.urllib.request.urlopen",
                   return_value=io.BytesIO(encoded)):
            self.assertEqual(json.loads(read_url("https://api.github.com/releases/latest")), release)

    def test_oversized_github_response_still_fails_closed(self):
        with patch("notify_blink_release.urllib.request.urlopen",
                   return_value=io.BytesIO(b"x" * (MAX_RELEASE_JSON_BYTES + 1))):
            with self.assertRaisesRegex(ValueError, "maximum size"):
                read_url("https://api.github.com/releases/latest")

    def test_release_info_remains_strictly_size_bounded(self):
        with patch("notify_blink_release.urllib.request.urlopen",
                   return_value=io.BytesIO(b"a" * 4097)):
            with self.assertRaisesRegex(ValueError, "maximum size"):
                read_url("https://github.com/Glimmar-code/Blinkng/releases/download/v/release-info.txt",
                         limit=4096)

    def test_fcm_validate_only_is_an_explicit_optional_envelope(self):
        payload = release_alert_payload(self.release, self.info, COMMIT)
        dry_run = {"validate_only": True, **payload}
        self.assertTrue(dry_run["validate_only"])
        self.assertNotIn("validate_only", payload)
        self.assertEqual(dry_run["message"]["topic"], "blink_android_website_releases")

if __name__ == "__main__":
    unittest.main()
