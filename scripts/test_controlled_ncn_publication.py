import contextlib
import copy
import datetime as dt
import io
import json
import os
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import urllib.error

import controlled_ncn_publication as control
from ncn_publication_transfers import Transfers
import publish_ncn_reference as publisher

POLICY = '.github/ncn-reference/controlled-authorisation.json'
NOW = dt.datetime(2026, 11, 4, 18, tzinfo=dt.timezone.utc)


class ControlledTests(unittest.TestCase):
    def setUp(self):
        self.auth = control.read(POLICY)
        self.manifest = control.read(control.ROOT/'candidate-manifest.json')

    def test_owner_evidence_budget_with_conservative_units_and_reserve(self):
        result = control.assess(self.auth, self.manifest, NOW)
        self.assertEqual('controlled_budget_verified', result['status'])
        self.assertEqual(288724969, result['estimated_reported_download_bytes_after_preview'])
        self.assertLess(result['planned_max_consumed_body_bytes'], 200000000)
        self.assertEqual(2000000000, result['usage_evidence']['traffic_and_lag_reserve_bytes'])

    def test_missing_stale_future_wrong_period_or_unapproved_evidence_rejected(self):
        for change in [{'observed_on':'2026-11-01'}, {'observed_on':'2026-11-05'},
                       {'period':'2026-10'}, {'source':'assumed_zero'}, {'plan':'unknown'}]:
            auth=copy.deepcopy(self.auth);auth['usage_evidence'].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                control.assess(auth,self.manifest,NOW)
        for key in ['usage_evidence','candidate','owner_authorised']:
            auth=copy.deepcopy(self.auth);auth.pop(key)
            with self.assertRaises((ValueError,KeyError)):control.assess(auth,self.manifest,NOW)

    def test_candidate_revision_size_and_both_hashes_are_pinned(self):
        for key in control.FIELDS:
            m=copy.deepcopy(self.manifest);m[key]=m[key]+1 if type(m[key]) is int else '0'*64
            with self.subTest(key=key),self.assertRaises(ValueError):control.assess(self.auth,m,NOW)

    def test_excessive_transfer_or_inadequate_budget_rejected(self):
        for cap in [145856489,200000001]:
            auth=copy.deepcopy(self.auth);auth['max_preview_transfer_bytes']=cap
            with self.assertRaises(ValueError):control.assess(auth,self.manifest,NOW)
        for change in [{'reported_download_bytes':9000000000}, {'traffic_and_lag_reserve_bytes':0},
                       {'reported_storage_bytes':9000000000}, {'storage_reserve_bytes':0}]:
            auth=copy.deepcopy(self.auth);auth['usage_evidence'].update(change)
            with self.assertRaises(ValueError):control.assess(auth,self.manifest,NOW)

    def test_rollback_reference_must_retain_original_digest(self):
        real=control.read
        rollback=real(self.auth['rollback_manifest']);rollback['sha256']='0'*64
        with patch.object(control,'read',side_effect=lambda p:rollback if str(p)==self.auth['rollback_manifest'] else real(p)),self.assertRaises(ValueError):
            control.assess(self.auth,self.manifest,NOW)

    def preflight(self, live=None, settings=None):
        env={'NCN_CONTROLLED_AUTHORISATION':POLICY,'ROADPRINTS_DATA_HOSTING_SITE':control.SITE,
             'FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON':json.dumps({'type':'service_account','project_id':control.PROJECT})}
        env.update(settings or {})
        response=SimpleNamespace(status_code=200,json=lambda:{'release':{'version':{'name':live or self.auth['expected_live_version']}}})
        session=Mock();session.get.return_value=response
        with tempfile.TemporaryDirectory() as folder,patch.dict(os.environ,env,clear=True),\
                patch('check_ncn_publication_quota.authenticated_session',return_value=session),\
                patch.object(control,'assess',wraps=lambda a,m:original_assess(a,m,NOW)),\
                contextlib.redirect_stdout(io.StringIO()):
            try:control.preflight(Path(folder));code=0
            except SystemExit as e:code=e.code
            return code,control.read(Path(folder)/'quota.json'),session

    def test_controlled_preflight_verifies_hosting_without_monitoring(self):
        code,result,session=self.preflight()
        self.assertEqual(0,code)
        self.assertEqual('controlled_budget_verified',result['status'])
        self.assertEqual(1,session.get.call_count)
        self.assertIn('/channels/live',session.get.call_args.args[0])

    def test_unexpected_live_identity_site_or_credentials_rejected(self):
        for live,env in [('sites/wrong/versions/x',{}),('sites/'+control.SITE+'/versions/changed',{}),
                         (None,{'ROADPRINTS_DATA_HOSTING_SITE':'wrong'}),
                         (None,{'FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON':'{}'})]:
            with self.subTest(live=live,env=env):self.assertEqual(1,self.preflight(live,env)[0])

    def test_attempt_is_create_only_and_existing_claim_is_not_retried(self):
        env={'NCN_CONTROLLED_AUTHORISATION':POLICY,'GITHUB_REPOSITORY':control.REPOSITORY,
             'GITHUB_REF':'refs/heads/main','GITHUB_EVENT_NAME':'push','GITHUB_RUN_ATTEMPT':'1',
             'GITHUB_SHA':'a'*40,'GITHUB_RUN_ID':'123','GH_TOKEN':'mock-token'}
        ref='refs/tags/ncn-publication-attempt-'+self.auth['authorisation_id']
        response=Mock();response.__enter__=Mock(return_value=response);response.__exit__=Mock(return_value=False)
        response.status=201;response.read.return_value=json.dumps({'ref':ref,'object':{'sha':'a'*40}}).encode()
        opener=Mock();opener.open.return_value=response
        with tempfile.TemporaryDirectory() as folder,patch.dict(os.environ,env,clear=True),\
                patch.object(control,'assess',return_value={}),patch('urllib.request.build_opener',return_value=opener):
            work=Path(folder);(work/'quota.json').write_text(json.dumps({'status':'controlled_budget_verified','authorisation_id':self.auth['authorisation_id']}))
            control.claim(work)
            self.assertEqual(ref,control.read(work/'attempt.json')['ref'])
            request=opener.open.call_args.args[0]
            self.assertEqual('POST',request.method)
            opener.open.side_effect=urllib.error.HTTPError(request.full_url,422,'exists',{},None)
            opener.open.reset_mock()
            with self.assertRaises(urllib.error.HTTPError):control.claim(work)
            self.assertEqual(1,opener.open.call_count)
            with patch.dict(os.environ,{'GITHUB_RUN_ATTEMPT':'2'}),self.assertRaises(ValueError):control.claim(work)

    def test_workflow_lock_claim_order_and_single_promoting_invocation(self):
        text=Path('.github/workflows/ncn-reference-publication.yml').read_text()
        self.assertIn('group: firebase-reference-publication',text)
        self.assertIn('cancel-in-progress: false',text)
        self.assertLess(text.index('run: python scripts/check_ncn_publication_quota.py'),text.index('run: python scripts/controlled_ncn_publication.py'))
        self.assertLess(text.index('run: python scripts/controlled_ncn_publication.py'),text.index('uses: actions/cache/restore@v4'))
        self.assertEqual(1,text.count('python scripts/publish_ncn_reference.py'))
        self.assertIn('--promote',text)
        self.assertNotIn('rerun',text)
        self.assertIn('ncn-publication-evidence/attempt.json',text)


original_assess=control.assess


class Response:
    def __init__(self,raw,status=200):self.raw=io.BytesIO(raw);self.status_code=status;self.headers={}
    def __enter__(self):return self
    def __exit__(self,*args):return False


class TransferTests(unittest.TestCase):
    def test_verified_preview_once_then_manifest_only_live(self):
        raw=b'test catalogue';m={'filename':'catalogue.db.gz','compressed_bytes':len(raw),'compressed_sha256':publisher.sha(raw)}
        get=Mock(side_effect=[Response(raw),Response(json.dumps(m).encode()),Response(json.dumps(m).encode())])
        with tempfile.TemporaryDirectory() as folder,patch.dict(sys.modules,{'requests':SimpleNamespace(get=get)}):
            t=Transfers(Path(folder),200000000)
            publisher.served('https://preview.invalid',m,t)
            publisher.served_manifest('https://live.invalid',m,t)
            self.assertEqual(1,sum(c.args[0].endswith('/catalogue.db.gz') for c in get.call_args_list))
            with self.assertRaises(ValueError):publisher.served('https://preview.invalid',m,t)
            self.assertEqual(3,get.call_count)
            self.assertEqual('integrity_verified',t.operations[0]['status'])

    def test_failed_preview_hash_or_http_has_one_download_no_retry(self):
        for response in [Response(b'wrong'),Response(b'',503),Response(b'too many bytes')]:
            m={'filename':'catalogue.db.gz','compressed_bytes':5,'compressed_sha256':publisher.sha(b'right')}
            get=Mock(return_value=response)
            with tempfile.TemporaryDirectory() as folder,patch.dict(sys.modules,{'requests':SimpleNamespace(get=get)}):
                t=Transfers(Path(folder),200000000)
                with self.assertRaises(ValueError):publisher.served('https://preview.invalid',m,t)
                self.assertEqual(1,get.call_count)
                self.assertLessEqual(t.operations[0]['consumed_body_bytes'],6)
                self.assertIn('failed',t.operations[0]['status'])
                with self.assertRaises(ValueError):Transfers(Path(folder),200000000)

    def test_excessive_request_rejected_before_network(self):
        get=Mock()
        with tempfile.TemporaryDirectory() as folder,patch.dict(sys.modules,{'requests':SimpleNamespace(get=get)}):
            t=Transfers(Path(folder),100)
            with self.assertRaises(ValueError):t.fetch('https://invalid',100,'catalogue')
            get.assert_not_called()

    def test_stream_failure_retains_partial_actual_bytes(self):
        response=Response(b'')
        response.raw=Mock();response.raw.read.side_effect=[b'partial',OSError('interrupted')]
        get=Mock(return_value=response)
        with tempfile.TemporaryDirectory() as folder,patch.dict(sys.modules,{'requests':SimpleNamespace(get=get)}):
            t=Transfers(Path(folder),200000000)
            with self.assertRaises(OSError):t.fetch('https://invalid',100,'catalogue')
            self.assertEqual(7,control.read(t.path)['consumed_cdn_body_bytes'])
            self.assertEqual('failed_no_retry',t.operations[0]['status'])


class PublisherFlowTests(unittest.TestCase):
    def flow(self, fail_preview=False):
        from types import SimpleNamespace
        raw=b'bounded synthetic catalogue'*10
        auth=control.read(POLICY)
        old=control.read(auth['baseline_manifest'])
        m=control.read(control.ROOT/'candidate-manifest.json')
        m.update(compressed_bytes=len(raw),compressed_sha256=publisher.sha(raw))
        source=auth['expected_live_version'];target='sites/'+control.SITE+'/versions/testtarget'
        before={publisher.MANIFEST:'1'*64,old['filename']:'2'*64,'other-collection':'3'*64}
        class Hosting:
            def __init__(self,session):
                self.live=source;self.after=dict(before);self.config={};self.promotions=0
            def files(self,version):return dict(before if version==source else self.after)
            def wait(self,operation):return operation
            def request(self,method,path,**kwargs):
                if method=='GET' and path.endswith('/channels/live'):
                    return {'release':{'version':{'name':self.live}}}
                if method=='GET' and '/channels/' in path:
                    return {'url':'https://'+control.SITE+'--ncn-reference-test.web.app'}
                if method=='GET':return {'config':self.config if path==target else {}}
                if path.endswith('/versions:clone'):return {'name':target}
                if path.endswith(':populateFiles'):
                    added={p.lstrip('/'):h for p,h in kwargs['json']['files'].items()}
                    self.after.update(added)
                    return {'uploadRequiredHashes':list(added.values()),
                            'uploadUrl':'https://upload-firebasehosting.googleapis.com/upload/'+target+'/files'}
                if method=='PATCH':self.config=kwargs['json']['config']
                if path=='sites/'+control.SITE+'/releases':
                    self.live=kwargs['params']['versionName'];self.promotions+=1
                return {}
        session=Mock();session.post.return_value=SimpleNamespace(status_code=200)
        session.get.return_value=SimpleNamespace(status_code=200,raise_for_status=lambda:None)
        host=Hosting(session)
        get=Mock(side_effect=[Response(json.dumps(old).encode()),
                             Response(raw if not fail_preview else b'wrong'),
                             Response(json.dumps(m).encode()),Response(json.dumps(m).encode())])
        factory=Mock(return_value=session)
        modules={
          'requests':SimpleNamespace(get=get),
          'publish_county_build_reference':SimpleNamespace(Publisher=lambda _:host),
          'google.auth.transport.requests':SimpleNamespace(AuthorizedSession=factory),
          'google.oauth2.service_account':SimpleNamespace(Credentials=SimpleNamespace(from_service_account_info=lambda *a,**k:None))}
        with tempfile.TemporaryDirectory() as folder,patch.dict(sys.modules,modules),\
             patch.dict(os.environ,{'NCN_CONTROLLED_AUTHORISATION':POLICY,
              'FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON':json.dumps({'project_id':control.PROJECT,'type':'service_account'}),
              'ROADPRINTS_DATA_HOSTING_SITE':control.SITE}),\
             patch.object(control,'publication_guard',return_value=auth),\
             patch.object(publisher,'local_check'),contextlib.redirect_stdout(io.StringIO()):
            work=Path(folder);asset=work/'asset.gz';asset.write_bytes(raw)
            manifest=work/'manifest.json';manifest.write_text(json.dumps(m))
            args=SimpleNamespace(asset=asset,manifest=manifest,work=work/'hosting',promote=True)
            if fail_preview:
                with self.assertRaises(ValueError):publisher.run(args)
                self.assertEqual(0,host.promotions)
                self.assertEqual(source,host.live)
                self.assertTrue((args.work/'rollback.json').exists())
                self.assertEqual(2,get.call_count)
            else:
                publisher.run(args)
                result=control.read(args.work/'publication.json')
                self.assertEqual('live_verified',result['status'])
                self.assertEqual(target,result['version'])
                self.assertEqual(source,result['source_version'])
                self.assertEqual(1,host.promotions)
                self.assertEqual(before,control.read(args.work/'source-files.json'))
                self.assertEqual(old['filename'],control.read(args.work/'rollback.json')['manifest']['filename'])
                self.assertEqual(1,sum(c.args[0].endswith('catalogue.db.gz') for c in get.call_args_list))
                self.assertEqual(4,get.call_count)
                self.assertEqual(2,session.post.call_count)
                self.assertEqual(0,factory.call_args.kwargs['max_refresh_attempts'])

    def test_full_flow_preserves_rollback_and_promotes_same_preview_version(self):
        self.flow()

    def test_failed_preview_never_promotes_or_retries(self):
        self.flow(True)

    def test_local_candidate_corruption_rejected_before_publication(self):
        m=control.read(control.ROOT/'candidate-manifest.json')
        with tempfile.TemporaryDirectory() as folder:
            asset=Path(folder)/'catalogue.db.gz';asset.write_bytes(b'wrong')
            with self.assertRaisesRegex(ValueError,'Compressed NCN asset differs'):
                publisher.local_check(asset,m)

if __name__ == '__main__':unittest.main()
