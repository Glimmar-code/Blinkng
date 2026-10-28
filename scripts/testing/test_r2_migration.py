"""Offline safety/command-construction tests for the non-destructive R2 migration."""
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1] / "migrate_supabase_media_to_r2.py"
spec = importlib.util.spec_from_file_location("blink_r2_migration", MODULE)
migration = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = migration
spec.loader.exec_module(migration)


class R2MigrationTests(unittest.TestCase):
    def test_private_sources_never_have_public_destinations(self):
        private = ("message-media", "blink-channel-media", "feed-media", "blink-bug-reports")
        for source in private:
            self.assertEqual(migration.DESTINATIONS[source], "blink-private-media")
            src, dst = migration.object_paths("source:", "r2:", source)
            self.assertEqual(src, "source:" + source)
            self.assertIn("/legacy/" + source, dst)
            self.assertNotIn("blink-public-media", dst)
        self.assertNotIn("message-view-once", migration.DESTINATIONS)

    def test_invalid_rclone_remote_is_rejected(self):
        import argparse
        for name in ("../../", "name:/bad", "name;rm -rf", ":"):
            with self.assertRaises(argparse.ArgumentTypeError):
                migration.remote(name)

    def test_default_is_inventory_only(self):
        commands = []
        def fake(args, *, capture=False):
            commands.append(args)
            return '{"count":2,"bytes":400}' if args[0] == "size" else ""
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "report.json"
            with patch.object(migration, "run_rclone", side_effect=fake):
                code = migration.main(["--source", "source:", "--target", "r2:",
                    "--buckets", "message-media", "--report", str(report)])
            self.assertEqual(code, 0)
            data = json.loads(report.read_text())
            self.assertTrue(data["sources_preserved"])
            self.assertFalse(data["url_cutover_performed"])
            self.assertTrue(data["view_once_excluded"])
            self.assertTrue(all(line[0] == "size" for line in commands))

    def test_copy_never_deletes_and_verify_downloads_bytes(self):
        commands = []
        def fake(args, *, capture=False):
            commands.append(args)
            return '{"count":1,"bytes":99}' if args[0] == "size" else ""
        with tempfile.TemporaryDirectory() as directory:
            report = str(Path(directory) / "report.json")
            with patch.object(migration, "run_rclone", side_effect=fake):
                self.assertEqual(migration.main(["--source","source:","--target","r2:",
                    "--mode","copy","--buckets","post-media","--report",report]), 0)
                self.assertEqual(migration.main(["--source","source:","--target","r2:",
                    "--mode","verify","--buckets","post-media","--report",report]), 0)
        self.assertTrue(any(cmd[0] == "copy" and "--ignore-existing" in cmd for cmd in commands))
        self.assertTrue(any(cmd[0] == "check" and "--download" in cmd and
                            "--one-way" in cmd for cmd in commands))
        self.assertFalse(any(cmd[0] in ("delete", "sync", "purge", "move") for cmd in commands))


if __name__ == "__main__":
    unittest.main()
