"""Prevent changes that bypass exact-artifact validation or downgrade update checks."""
from pathlib import Path
import unittest

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


if __name__ == '__main__':
    unittest.main()
