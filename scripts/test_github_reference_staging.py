"""Tests for the newly prepared staging path; no external connections permitted."""
import json
import hashlib
import io
import socket
import tempfile
import unittest
import urllib.request
import urllib.error
import zipfile
from pathlib import Path
from unittest.mock import patch, Mock
import stage_github_reference_bundle as staging

ROOT=Path(__file__).resolve().parents[1]
TAG='reference-pins-2026-10-27-bootstrap'


def release(**changes):
    return dict({'id':425479238,'tag_name':TAG,'draft':True},**changes)


def asset(**changes):
    result={'id':681638825,'name':staging.ASSET,'size':staging.BUNDLE_BYTES,
            'digest':'sha256:'+staging.BUNDLE_SHA256,'state':'uploaded',
            'browser_download_url':'https://forbidden.invalid/never-use'}
    result.update(changes)
    return result


def response(value):
    return io.BytesIO(json.dumps(value).encode())

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
        request=staging.github_request('/assets/1','synthetic-test-token','application/octet-stream')
        handler=staging.GitHubOnlyRedirect()
        with self.assertRaises(ValueError):handler.redirect_request(request,None,302,'found',{},'https://roadprints-data-822cc.web.app/a')
        redirected=handler.redirect_request(request,None,302,'found',{},'https://release-assets.githubusercontent.com/a')
        self.assertIsNone(redirected.get_header('Authorization'))
    def test_authenticated_discovery_uses_lists_then_asset_api(self):
        opener=Mock()
        opener.open.side_effect=[response([release()]),response([asset()])]
        self.assertEqual(681638825,staging.discover_asset(opener,TAG,'synthetic'))
        urls=[call.args[0].full_url for call in opener.open.call_args_list]
        self.assertEqual([
            'https://api.github.com/repos/'+staging.REPOSITORY+'/releases?per_page=100&page=1',
            'https://api.github.com/repos/'+staging.REPOSITORY+'/releases/425479238/assets?per_page=100&page=1'],urls)
        for call in opener.open.call_args_list:
            self.assertEqual('Bearer synthetic',call.args[0].get_header('Authorization'))
    def test_discovery_paginates_and_matches_exact_tag(self):
        opener=Mock()
        opener.open.side_effect=[response([release(tag_name=TAG+'-other'),release(tag_name='older')]),
                                  response([release()]),response([asset()])]
        with patch.object(staging,'PAGE_SIZE',2):
            self.assertEqual(681638825,staging.discover_asset(opener,TAG,'synthetic'))
        self.assertIn('page=2',opener.open.call_args_list[1].args[0].full_url)
    def test_missing_ambiguous_or_published_release_is_rejected(self):
        for releases in [[],[release(tag_name=TAG+'-other')],[release(),release()],
                         [release(draft=False)]]:
            with self.subTest(releases=releases):
                opener=Mock();opener.open.return_value=response(releases)
                with self.assertRaises(ValueError):staging.discover_asset(opener,TAG,'synthetic')
                self.assertEqual(1,opener.open.call_count)
    def test_pagination_limit_fails_instead_of_accepting_partial_match(self):
        opener=Mock();opener.open.return_value=response([release()])
        with patch.object(staging,'PAGE_SIZE',1),patch.object(staging,'MAX_PAGES',1):
            with self.assertRaisesRegex(ValueError,'pagination limit'):
                staging.discover_asset(opener,TAG,'synthetic')
    def test_missing_ambiguous_or_incorrect_asset_is_rejected_before_download(self):
        cases=[[],[asset(name='different.zip')],[asset(),asset()],
               [asset(size=1)],[asset(digest='sha256:'+'0'*64)],
               [asset(digest=None)],[asset(state='new')],[asset(id=True)]]
        for assets in cases:
            with self.subTest(assets=assets):
                opener=Mock();opener.open.side_effect=[response([release()]),response(assets)]
                with tempfile.TemporaryDirectory() as work,patch('urllib.request.build_opener',return_value=opener):
                    out=Path(work)/'out'
                    with self.assertRaises(ValueError):staging.stage(TAG,out,'synthetic')
                    self.assertFalse(out.exists())
                self.assertEqual(2,opener.open.call_count)
    def test_duplicate_asset_on_later_page_is_rejected(self):
        opener=Mock();opener.open.side_effect=[response([release()]),
            response([asset(),asset(name='unrelated')]),response([asset()])]
        with patch.object(staging,'PAGE_SIZE',2):
            with self.assertRaisesRegex(ValueError,'exactly one approved asset'):
                staging.discover_asset(opener,TAG,'synthetic')
    def test_http_errors_fail_without_retry_or_token_disclosure(self):
        for operation in ['list','asset-list','download']:
            for code in [401,403,404]:
                with self.subTest(operation=operation,code=code):
                    error=urllib.error.HTTPError('https://api.github.com/secret-url',code,
                                                 'secret-response',{},io.BytesIO(b'secret-body'))
                    before=[] if operation=='list' else [response([release()])]
                    if operation=='download':before.append(response([asset()]))
                    opener=Mock();opener.open.side_effect=before+[error]
                    with tempfile.TemporaryDirectory() as work,patch('urllib.request.build_opener',return_value=opener):
                        out=Path(work)/'out'
                        with self.assertRaisesRegex(RuntimeError,'HTTP '+str(code)) as caught:
                            staging.stage(TAG,out,'synthetic-private-token')
                        self.assertFalse(out.exists())
                        for secret in ['secret-url','secret-body','synthetic-private-token']:
                            self.assertNotIn(secret,str(caught.exception))
                    self.assertEqual(len(before)+1,opener.open.call_count)
    def test_success_downloads_authenticated_api_and_verifies_synthetic_zip(self):
        members={'catalogue.db':b'synthetic database','boundaries.bin':b'synthetic boundary'}
        buf=io.BytesIO()
        with zipfile.ZipFile(buf,'w') as archive:
            for name,content in members.items():archive.writestr(name,content)
        data=buf.getvalue();sha=hashlib.sha256(data).hexdigest()
        with patch.object(staging,'BUNDLE_BYTES',len(data)),patch.object(staging,'BUNDLE_SHA256',sha),\
             patch.object(staging,'MEMBERS',{name:(len(value),hashlib.sha256(value).hexdigest()) for name,value in members.items()}):
            opener=Mock();opener.open.side_effect=[response([release()]),response([asset()]),io.BytesIO(data)]
            with tempfile.TemporaryDirectory() as work,patch('urllib.request.build_opener',return_value=opener):
                out=Path(work)/'out';staging.stage(TAG,out,'synthetic')
                self.assertEqual(members,{p.name:p.read_bytes() for p in out.iterdir()})
            request=opener.open.call_args_list[-1].args[0]
            self.assertTrue(request.full_url.endswith('/releases/assets/681638825'))
            self.assertEqual('application/octet-stream',request.get_header('Accept'))
            self.assertEqual('Bearer synthetic',request.get_header('Authorization'))
    def test_corrupt_download_never_installs_members(self):
        data=b'corrupt'
        with patch.object(staging,'BUNDLE_BYTES',len(data)):
            opener=Mock();opener.open.side_effect=[response([release()]),response([asset()]),io.BytesIO(data)]
            with tempfile.TemporaryDirectory() as work,patch('urllib.request.build_opener',return_value=opener):
                out=Path(work)/'out'
                with self.assertRaisesRegex(ValueError,'checksum'):staging.stage(TAG,out,'synthetic')
                self.assertFalse(out.exists())
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
