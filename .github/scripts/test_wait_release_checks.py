import unittest
from wait_release_checks import gate, REQUIRED


def passing():
    return [dict(id=i, path='.github/workflows/'+path, head_sha='abc', head_branch='main',
                 event='push', status='completed', conclusion='success') for i, path in enumerate(REQUIRED)]


class GateTests(unittest.TestCase):
    def test_all_pass(self):
        self.assertEqual(gate(passing(), 'abc'), [])

    def test_missing_pending_wrong_commit_and_pr_checks_do_not_count(self):
        for changes in [{'head_sha': 'other'}, {'head_branch': 'Testlab'}, {'event': 'pull_request'},
                        {'status': 'in_progress'}, {'status': 'queued'}]:
            runs = passing()
            runs[0].update(changes)
            self.assertIn(REQUIRED[0], gate(runs, 'abc'))
        self.assertIn(REQUIRED[0], gate(passing()[1:], 'abc'))

    def test_failures_skips_and_cancellation_block(self):
        for result in ['failure', 'cancelled', 'skipped', 'timed_out', 'action_required', None]:
            runs = passing()
            runs[0]['conclusion'] = result
            with self.assertRaises(ValueError):
                gate(runs, 'abc')

    def test_new_failure_cannot_hide_behind_old_success(self):
        runs = passing()
        runs.append(dict(runs[0], id=500, conclusion='failure'))
        with self.assertRaises(ValueError):
            gate(runs, 'abc')


if __name__ == '__main__':
    unittest.main()
