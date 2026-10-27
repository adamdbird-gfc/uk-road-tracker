"""Offline contract/transport tests for the proposed one-off artifact workflow."""
import hashlib
import io
import json
import os
from pathlib import Path
import socket
import tempfile
import unittest
from unittest.mock import Mock,patch
import yaml

ROOT=Path(__file__).resolve().parents[1]
WORKFLOW=ROOT/'.github/workflows/reference-artifact-stage.yml'
URL='https://release-assets.githubusercontent.com/github-production-release-asset/1347394235/synthetic?sig=private-synthetic'

class ArtifactStage(unittest.TestCase):
    def setUp(self):
        blocker=patch.object(socket.socket,'connect',side_effect=AssertionError('Network forbidden'))
        blocker.start();self.addCleanup(blocker.stop)
        self.workflow=yaml.load(WORKFLOW.read_text(),Loader=yaml.BaseLoader)
        code=self.workflow['jobs']['stage']['steps'][2]['run']
        source=code.split("python3 - <<'PY'\n",1)[1].rsplit('\nPY',1)[0]
        self.ns={'__name__':'offline_contract'}
        exec(compile(source,str(WORKFLOW),'exec'),self.ns)
    def test_workflow_has_no_cache_build_publication_or_permission_expansion(self):
        d=self.workflow
        self.assertEqual(['workflow_dispatch'],list(d['on']))
        self.assertEqual({'contents':'read'},d['permissions'])
        self.assertIn('github.actor == github.repository_owner',d['jobs']['stage']['if'])
        self.assertIn('github.event.repository.default_branch',d['jobs']['stage']['if'])
        steps=d['jobs']['stage']['steps']
        self.assertEqual('79bcc970c176c6526224a91c24c89525f10bf152',steps[0]['with']['ref'])
        self.assertEqual('false',steps[0]['with']['persist-credentials'])
        self.assertEqual('${{ secrets.ROADPRINTS_REFERENCE_ZIP_URL }}',steps[2]['env']['REFERENCE_ZIP_URL'])
        upload=steps[-1]
        self.assertEqual('roadprints-reference-pins-v2',upload['with']['name'])
        self.assertEqual(['reference-input/catalogue.db','reference-input/boundaries.bin',
                         'reference-input/ncn-manifest.json','reference-input/county-catalogue.json',
                         'reference-input/REFERENCE-LICENCES.txt'],upload['with']['path'].splitlines())
        for step in steps:
            if 'uses' in step:self.assertEqual(40,len(step['uses'].rsplit('@',1)[1]))
        for banned in ['actions/cache','gradle ','assemble','firebase ','render ','GH_TOKEN','GITHUB_TOKEN','pip install','browser_download_url']:
            self.assertNotIn(banned,json.dumps(steps))
    def test_rejects_non_github_or_wrong_repository_url_before_transport(self):
        for url in ['', 'https://roadprints-data-822cc.web.app/a',
                    'https://api.github.com/repos/a/b/releases/assets/1',
                    URL.replace('/1347394235/','/other/'),URL.replace('https:','http:'),
                    URL.replace('release-assets.githubusercontent.com','evil.invalid'),
                    URL.replace('https://','https://user:password@'),URL+'#fragment']:
            with self.subTest(url=url),patch('urllib.request.build_opener') as opener:
                with self.assertRaises(ValueError):self.ns['stage'](url)
                opener.assert_not_called()
    def test_redirect_cannot_leave_github_or_repository_scope(self):
        import urllib.request
        handler=self.ns['AssetRedirect']();request=urllib.request.Request(URL)
        with self.assertRaises(ValueError):handler.redirect_request(request,None,302,'found',{},'https://roadprints-data-822cc.web.app/a')
        self.assertEqual(URL,handler.redirect_request(request,None,302,'found',{},URL).full_url)
    def test_expired_url_does_not_retry_or_create_output(self):
        import urllib.error
        opener=Mock();opener.open.side_effect=urllib.error.HTTPError(URL,403,'Expired',{},None)
        with tempfile.TemporaryDirectory() as work,patch('urllib.request.build_opener',return_value=opener):
            out=Path(work)/'out'
            with self.assertRaises(urllib.error.HTTPError):self.ns['stage'](URL,out)
            self.assertFalse(out.exists())
        self.assertEqual(1,opener.open.call_count)
    def test_download_is_bounded_before_extraction(self):
        opener=Mock();opener.open.return_value=io.BytesIO(b'oversized')
        self.ns['BUNDLE_BYTES']=2
        with tempfile.TemporaryDirectory() as work,patch('urllib.request.build_opener',return_value=opener):
            out=Path(work)/'out'
            with self.assertRaisesRegex(ValueError,'exceeds'):self.ns['stage'](URL,out)
            self.assertFalse(out.exists())
    def test_corrupt_zip_is_rejected_before_output(self):
        opener=Mock();opener.open.return_value=io.BytesIO(b'corrupt')
        with tempfile.TemporaryDirectory() as work,patch('urllib.request.build_opener',return_value=opener):
            out=Path(work)/'out'
            with self.assertRaisesRegex(ValueError,'size'):self.ns['stage'](URL,out)
            self.assertFalse(out.exists())
    def test_validation_precedes_output_and_has_no_repository_credentials(self):
        # Transport uses a synthetic body. Pinned extraction/SQLite routines already
        # have separate integrity tests; this checks workflow ordering and headers.
        events=[]
        opener=Mock();opener.open.return_value=io.BytesIO(b'synthetic')
        def extract(bundle,directory):
            events.append('zip-and-member-pins');directory.mkdir()
            (directory/'catalogue.db').write_bytes(b'db')
            (directory/'boundaries.bin').write_bytes(b'county')
        self.ns['extract_verified']=extract
        self.ns['verify_database']=lambda *args:events.append('sqlite')
        with tempfile.TemporaryDirectory() as work,patch('urllib.request.build_opener',return_value=opener):
            previous=Path.cwd();os.chdir(work)
            try:
                for folder,contents in [('ncn-national',{}),('historic-counties',{'counties':[{'offset':0,'length':6}],
                        'data_sha256':hashlib.sha256(b'county').hexdigest()})]:
                    p=Path('android-prototype/app/src/main/assets')/folder;p.mkdir(parents=True)
                    (p/('manifest.json' if folder=='ncn-national' else 'catalogue.json')).write_text(json.dumps(contents))
                self.ns['stage'](URL)
                self.assertEqual(['zip-and-member-pins','sqlite'],events)
                self.assertEqual(b'county',Path('reference-input/boundaries.bin').read_bytes())
                notice=Path('reference-input/REFERENCE-LICENCES.txt').read_text()
                for required in ['ODbL 1.0','OGL 3.0','Historic County Borders Project','https://county-borders.co.uk/']:
                    self.assertIn(required,notice)
                request=opener.open.call_args.args[0]
                self.assertEqual('GET',request.get_method())
                self.assertIsNone(request.get_header('Authorization'))
                self.assertIsNone(request.get_header('Cookie'))
            finally:os.chdir(previous)

if __name__=='__main__':unittest.main()
