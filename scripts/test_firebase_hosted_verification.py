import hashlib
import unittest
from verify_firebase_reference_data import verify


class HostedVerificationTest(unittest.TestCase):
    def setUp(self):
        self.body = b'{}'
        self.path = 'collections/published/services/manifest-v1.json'
        self.report = {'schema_version': 1, 'files': [{'path': self.path, 'bytes': 2, 'sha256': hashlib.sha256(self.body).hexdigest()}]}
        self.headers = {'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'public, max-age=300', 'X-Content-Type-Options': 'nosniff'}
        self.status = 200

    def fetch(self, url, limit):
        if url.endswith(self.path):
            return self.status, self.headers, self.body
        return 404, {}, b''

    def test_exact_release_passes(self):
        result = verify('https://roadprints-test.web.app', self.report, self.fetch)
        self.assertEqual(result['files_verified'], 1)

    def test_changed_payload_fails(self):
        self.body = b'[]'
        with self.assertRaisesRegex(ValueError, 'bytes differ'):
            verify('https://roadprints-test.web.app', self.report, self.fetch)

    def test_redirect_is_not_accepted(self):
        self.status = 302
        with self.assertRaisesRegex(ValueError, 'HTTP 302'):
            verify('https://roadprints-test.web.app', self.report, self.fetch)

    def test_html_response_is_not_accepted(self):
        self.headers['Content-Type'] = 'text/html'
        with self.assertRaisesRegex(ValueError, 'content type'):
            verify('https://roadprints-test.web.app', self.report, self.fetch)

    def test_manifest_cannot_be_immutable(self):
        self.headers['Cache-Control'] = 'public, max-age=31536000, immutable'
        with self.assertRaisesRegex(ValueError, 'cache policy'):
            verify('https://roadprints-test.web.app', self.report, self.fetch)

    def test_unexpected_public_source_fails(self):
        def fetch(url, limit):
            if url.endswith('README.md'):
                return 200, {}, b'source'
            return self.fetch(url, limit)
        with self.assertRaisesRegex(ValueError, 'Non-public path'):
            verify('https://roadprints-test.web.app', self.report, fetch)

    def test_report_path_cannot_escape(self):
        self.report['files'][0]['path'] = '../credentials.json'
        with self.assertRaisesRegex(ValueError, 'Unsafe'):
            verify('https://roadprints-test.web.app', self.report, self.fetch)

    def test_immutable_catalogue_policy(self):
        self.path = 'collections/published/services/catalogue-2026101301.json'
        self.report['files'][0]['path'] = self.path
        self.headers['Cache-Control'] = 'public, max-age=31536000, immutable'
        self.assertEqual(verify('https://roadprints-test.web.app', self.report, self.fetch)['files_verified'], 1)


if __name__ == '__main__':
    unittest.main()
