"""Prevent changes that bypass exact-artifact validation or downgrade update checks."""
from pathlib import Path
import unittest
from unittest.mock import patch
import verify_production_apk as device

ROOT = Path(__file__).resolve().parents[2]


class ContractTests(unittest.TestCase):
    def test_publication_depends_on_full_signed_workflow_and_exact_commit_checks(self):
        workflow = (ROOT / '.github/workflows/publish-latest-apk.yml').read_text()
        self.assertIn("github.event.workflow_run.conclusion == 'success'", workflow)
        self.assertIn("github.event.workflow_run.head_branch == 'main'", workflow)
        self.assertLess(workflow.index('wait_release_checks.py'), workflow.index('publish_verified_apk.py'))
        self.assertIn('run-id: ${{ github.event.workflow_run.id }}', workflow)

    def test_candidate_is_downloaded_from_same_build_without_rebuilding(self):
        workflow = (ROOT / '.github/workflows/signed-release.yml').read_text()
        validation = workflow.split('  production-apk:\n')[1]
        self.assertIn('needs: release', validation)
        self.assertIn('name: ${{ needs.release.outputs.artifact }}', validation)
        self.assertNotIn('assembleRelease', validation)
        self.assertIn('api: [24, 35]', validation)
        self.assertIn('verify_production_apk.py', validation)

    def test_upgrade_never_allows_downgrades_or_uninstalls_between_versions(self):
        script = (ROOT / '.github/scripts/verify_production_apk.py').read_text()
        between = script.split("adb('install', str(args.previous))")[1].split('exercise()')[0]
        self.assertIn("adb('install', '-r', str(args.apk))", between)
        self.assertNotIn("'uninstall'", between)
        self.assertNotIn("'-d'", between)


class OfflineTests(unittest.TestCase):
    def run_network(self, api, online, connected, console='OK'):
        calls = []
        def fake_adb(*args):
            calls.append(args)
            if args[:2] == ('shell', 'getprop'):
                return str(api)
            if args[0] == 'emu':
                return console
            if args[:2] == ('shell', 'dumpsys'):
                return 'Active default network: ' + ('100' if connected else 'none')
            return ''
        with patch.object(device, 'adb', fake_adb), patch.object(device.time, 'sleep'):
            device.set_network(online)
        return calls

    def test_android_24_controls_mobile_data_without_unsupported_wifi(self):
        calls = self.run_network(24, False, False)
        self.assertIn(('shell', 'svc', 'data', 'disable'), calls)
        self.assertFalse(any('wifi' in call for call in calls))
        self.assertIn(('shell', 'svc', 'data', 'enable'), self.run_network(24, True, True))

    def test_android_35_controls_both_radios(self):
        calls = self.run_network(35, False, False)
        for radio in ('wifi', 'data'):
            self.assertIn(('shell', 'svc', radio, 'disable'), calls)

    def test_unconfirmed_disconnect_or_reconnect_cannot_pass(self):
        for api in (24, 35):
            with self.assertRaisesRegex(RuntimeError, 'required online/offline'):
                self.run_network(api, False, True)
            with self.assertRaisesRegex(RuntimeError, 'required online/offline'):
                self.run_network(api, True, False)


if __name__ == '__main__':
    unittest.main()
