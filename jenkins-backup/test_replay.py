"""Failure propagation, immutable checkout, command-file env and always() cleanup."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import replay


class ReplayTests(unittest.TestCase):
    def test_migration_gate_requires_both_successes(self):
        condition = "${{ steps.migration.outcome == 'success' && steps.stop_migration.outcome == 'success' }}"
        self.assertTrue(replay.should_run(condition, False, {'migration': 'success', 'stop_migration': 'success'}))
        self.assertFalse(replay.should_run(condition, True, {'migration': 'failure', 'stop_migration': 'success'}))

    def test_unknown_expression_fails_closed(self):
        with self.assertRaisesRegex(ValueError, 'Unsupported expression'):
            replay.expand('${{ secrets.UNKNOWN }}', {}, 'a' * 40)

    def test_failure_preserves_always_steps_and_reports_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'source'
            source.mkdir()
            def git(*args):
                return subprocess.check_output(['git', '-C', str(source), *args], text=True).strip()
            git('init', '--quiet')
            git('config', 'user.name', 'CI adapter test')
            git('config', 'user.email', 'ci-test@example.invalid')
            workflows = source / '.github/workflows'
            workflows.mkdir(parents=True)
            (workflows / 'test.yml').write_text('''name: Adapter fixture
jobs:
  failure:
    steps:
      - run: echo 'CARRIED=value' >> "$GITHUB_ENV"
      - run: test "$CARRIED" = value
      - run: exit 7
      - run: touch should-not-exist
      - if: always()
        run: test ! -f should-not-exist && echo cleanup > evidence.txt
      - if: always()
        uses: actions/upload-artifact@v4
        with:
          path: evidence.txt
  unsupported:
    steps:
      - uses: unknown/action@v1
  manual-kubernetes:
    if: github.event_name == 'workflow_dispatch'
    steps:
      - uses: helm/kind-action@v1
''')
            git('add', '.')
            git('commit', '--quiet', '-m', 'CI fixture')
            sha = git('rev-parse', 'HEAD')
            output = root / 'output'
            result = subprocess.run([sys.executable, str(Path(replay.__file__)), '--sha', sha,
                '--repository', str(source / '.git'), '--output', str(output)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 1, result.stderr)
            summary = json.loads((output / 'summary.json').read_text())
            self.assertEqual(summary['sha'], sha)
            self.assertEqual(summary['result'], 'failure')
            self.assertEqual([s['status'] for s in summary['jobs'][0]['steps']],
                ['success', 'success', 'failure', 'skipped', 'success', 'success'])
            self.assertIn('Unsupported action', summary['jobs'][1]['steps'][0]['error'])
            self.assertEqual(summary['jobs'][2]['result'], 'skipped')
            self.assertIn('Manual job gate', summary['jobs'][2]['reason'])
            self.assertEqual((output / 'test-failure/artifacts-5/evidence.txt').read_text().strip(), 'cleanup')
            self.assertTrue((output / 'summary.junit.xml').exists())
            self.assertFalse((output / 'test-failure/step.env').exists())


if __name__ == '__main__':
    unittest.main()
