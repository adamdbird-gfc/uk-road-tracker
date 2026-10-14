import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from validate_android_release_request import validate, changed_request, git


class ReleaseRequestTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        git(self.repo, 'init', '-q')
        git(self.repo, 'config', 'user.name', 'Fixture')
        git(self.repo, 'config', 'user.email', 'fixture@example.invalid')
        gradle = self.repo / 'android-prototype/app/build.gradle.kts'
        gradle.parent.mkdir(parents=True)
        gradle.write_text('versionName = "0.25.200"\nversionCode = 246\n')
        self.commit()
        self.sha = git(self.repo, 'rev-parse', 'HEAD')
        self.path = '.github/release-requests/android/0.25.200.json'
        self.data = dict(schema_version=1, version_name='0.25.200', version_code=246, source_sha=self.sha, firebase_upload=True)

    def commit(self):
        git(self.repo, 'add', '.')
        git(self.repo, 'commit', '-qm', 'fixture')

    def request(self):
        path = self.repo / self.path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(self.data))
        self.commit()

    def test_valid_pinned_request(self):
        self.request()
        self.assertEqual(changed_request(self.repo), self.path)
        self.assertEqual(validate(self.repo, self.path)['source_sha'], self.sha)

    def test_mismatched_version_rejected(self):
        self.data['version_code'] = 245
        self.request()
        with self.assertRaisesRegex(ValueError, 'version'):
            validate(self.repo, self.path)

    def test_request_is_immutable(self):
        self.request()
        self.data['firebase_upload'] = False
        self.request()
        with self.assertRaisesRegex(ValueError, 'already exists'):
            validate(self.repo, self.path)

    def test_deleted_request_cannot_be_reused(self):
        self.request()
        (self.repo / self.path).unlink()
        self.commit()
        self.request()
        with self.assertRaisesRegex(ValueError, 'already exists'):
            validate(self.repo, self.path)

    def test_unknown_source_rejected(self):
        self.data['source_sha'] = 'f' * 40
        self.request()
        with self.assertRaises(subprocess.CalledProcessError):
            validate(self.repo, self.path)

    def test_boolean_upload_required(self):
        self.data['firebase_upload'] = 'true'
        self.request()
        with self.assertRaisesRegex(ValueError, 'Invalid'):
            validate(self.repo, self.path)

    def test_multiple_requests_rejected(self):
        path = self.repo / '.github/release-requests/android/0.25.201.json'
        path.parent.mkdir(parents=True)
        path.write_text('{}')
        self.request()
        with self.assertRaisesRegex(ValueError, 'exactly one'):
            changed_request(self.repo)


if __name__ == '__main__':
    unittest.main()
