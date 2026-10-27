"""Tests for the newly prepared staging path; no external connections permitted."""
import json
import socket
import tempfile
import unittest
import urllib.request
from pathlib import Path
from unittest.mock import patch
import stage_github_reference_bundle as staging

ROOT=Path(__file__).resolve().parents[1]

class StagingSafety(unittest.TestCase):
    def setUp(self):
        blocker=patch.object(socket.socket,'connect',side_effect=AssertionError('Network forbidden'))
        blocker.start();self.addCleanup(blocker.stop)
    def test_only_github_release_hosts_are_allowed(self):
        self.assertTrue(staging.allowed_url('https://api.github.com/repos/adamdbird-gfc/uk-road-tracker/releases/assets/1'))
        self.assertTrue(staging.allowed_url('https://release-assets.githubusercontent.com/signed-asset?signature=synthetic'))
        for url in ['https://roadprints-data-822cc.web.app/file','https://firestore.googleapis.com/file',
                    'https://api.github.com.evil.invalid/a','http://api.github.com/repos/adamdbird-gfc/uk-road-tracker/releases/a',
                    'https://api.github.com/repos/other/repo/releases/a','https://user:pass@release-assets.githubusercontent.com/a',
                    'https://release-assets.githubusercontent.com:443/a']:
            self.assertFalse(staging.allowed_url(url),url)
    def test_redirect_rejects_firebase_and_strips_api_token(self):
        request=staging.github_request('assets/1','synthetic-test-token','application/octet-stream')
        handler=staging.GitHubOnlyRedirect()
        with self.assertRaises(ValueError):handler.redirect_request(request,None,302,'found',{},'https://roadprints-data-822cc.web.app/a')
        redirected=handler.redirect_request(request,None,302,'found',{},'https://release-assets.githubusercontent.com/a')
        self.assertIsNone(redirected.get_header('Authorization'))
    def test_bad_bundle_is_rejected_before_extraction(self):
        with tempfile.TemporaryDirectory() as work:
            path=Path(work)/'bundle.zip';path.write_bytes(b'invalid')
            with self.assertRaisesRegex(ValueError,'size'):staging.extract_verified(path,Path(work)/'out')
            self.assertFalse((Path(work)/'out').exists())
    def test_invalid_tag_never_creates_network_client(self):
        with patch('urllib.request.build_opener',side_effect=AssertionError('Unexpected transport')):
            with self.assertRaises(ValueError):staging.stage('../../other',Path('out'),'synthetic')
    def test_workflow_is_manual_scoped_and_has_no_build_or_production_credentials(self):
        import yaml
        d=yaml.load((ROOT/'.github/workflows/reference-pin-cache.yml').read_text(),Loader=yaml.BaseLoader)
        self.assertEqual(['workflow_dispatch'],list(d['on']))
        self.assertEqual({'contents':'read','actions':'read'},d['permissions'])
        self.assertIn('github.event.repository.default_branch',d['jobs']['seed']['if'])
        steps=d['jobs']['seed']['steps'];runs='\n'.join(s.get('run','') for s in steps)
        for value in ['gradle ','assembleDebug','firebase ','FIREBASE_','scripts/publish_','startActivity']:
            self.assertNotIn(value,json.dumps(steps))
        uses=[s['uses'] for s in steps if 'uses' in s]
        self.assertTrue(all(len(u.rsplit('@',1)[1])==40 for u in uses))
        self.assertLess(runs.index('seed_reference_pins.py'),runs.index('Path(path).unlink'))
        self.assertIn('fail-on-cache-miss',json.dumps(steps))

if __name__=='__main__':unittest.main()
