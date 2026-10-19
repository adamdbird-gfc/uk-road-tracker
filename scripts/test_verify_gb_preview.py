import json
import tempfile
import unittest
from pathlib import Path
from verify_gb_preview import transient_fetch, verify_batches


class BatchVerificationTests(unittest.TestCase):
    def report(self):
        return {'schema_version': 1, 'files': [
            {'path': f'{i}.json', 'sha256': str(i), 'bytes': 1} for i in range(5)]}

    def test_resume_preserves_completed_batch_after_later_failure(self):
        report = self.report()
        seen = []
        def verifier(url, data, fetch):
            paths = [r['path'] for r in data['files']]
            seen.extend(paths)
            if '2.json' in paths:
                raise ValueError('Mismatch')
        with tempfile.TemporaryDirectory() as temp:
            checkpoint = Path(temp) / 'progress.json'
            with self.assertRaises(ValueError):
                verify_batches('https://example.web.app', report, verifier, None,
                               checkpoint, batch_size=2, workers=1)
            state = json.loads(checkpoint.read_text())
            self.assertEqual(set(state['verified']), {'0.json', '1.json'})
            self.assertNotIn('4.json', seen)
            resumed = []
            def success(url, data, fetch):
                resumed.extend(r['path'] for r in data['files'])
            result = verify_batches('https://example.web.app', report, success, None,
                                    checkpoint, batch_size=2, workers=1)
            self.assertEqual(resumed, ['2.json', '3.json', '4.json', '0.json'])
            self.assertEqual(result['files_verified'], 5)
            with self.assertRaises(ValueError):
                verify_batches('https://different.web.app', report, success, None, checkpoint)

    def test_transient_asset_404_retried_but_negative_probe_is_not(self):
        replies = iter([(404, {}, b''), (200, {}, b'ok')])
        sleeps = []
        fetch = transient_fetch(lambda url, limit: next(replies), sleep=sleeps.append)
        self.assertEqual(fetch('https://example.web.app/catalogue.json', 10)[0], 200)
        self.assertEqual(sleeps, [1])
        sleeps.clear()
        fetch = transient_fetch(lambda url, limit: (404, {}, b''), sleep=sleeps.append)
        self.assertEqual(fetch('https://example.web.app/firebase.json', 10)[0], 404)
        self.assertFalse(sleeps)

    def test_duplicate_paths_and_excessive_concurrency_rejected(self):
        report = self.report()
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'progress.json'
            with self.assertRaises(ValueError):
                verify_batches('https://example.web.app', report, None, None, path, workers=5)
            report['files'].append(report['files'][0])
            with self.assertRaises(ValueError):
                verify_batches('https://example.web.app', report, None, None, path)


if __name__ == '__main__':
    unittest.main()
