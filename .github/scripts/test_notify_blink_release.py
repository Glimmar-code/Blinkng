import unittest
from notify_blink_release import release_alert_payload

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

if __name__ == "__main__":
    unittest.main()
