import copy,gzip,hashlib,json,tempfile,unittest,sys
from types import SimpleNamespace
from unittest.mock import Mock,patch
from pathlib import Path
from ncn_reference import validate,verify_database
from publish_ncn_reference import configuration,preservation,next_manifest,served_manifest

class ReferenceTests(unittest.TestCase):
 def setUp(self):self.m=json.loads((Path(__file__).resolve().parents[1]/'android-prototype/app/src/main/assets/ncn-national/manifest.json').read_text())
 def test_release_contract_and_bounds(self):
  validate(self.m)
  for field,value in [('filename','https://other.invalid/a'),('sha256','invalid'),('compressed_bytes',300*1024*1024),('database_bytes',1024),('whole_published_route_ready',True),('revision',0),('scope','whole-network')]:
   with self.subTest(field=field),self.assertRaises(ValueError):validate({**self.m,field:value})
 def test_revision_rollbacks_must_be_explicit(self):
  next_manifest(None,self.m);next_manifest(self.m,self.m)
  newer={**self.m,'revision':2};next_manifest(self.m,newer)
  with self.assertRaises(ValueError):next_manifest(newer,self.m)
  with self.assertRaises(ValueError):next_manifest(self.m,{**self.m,'compressed_sha256':'0'*64})
  # A higher manifest revision may intentionally point back at a retained hash.
  next_manifest(newer,{**self.m,'revision':3})
 def test_clone_preserves_every_non_ncn_file(self):
  before={'settlements/a':'1','collections/other':'2','old-ncn':'3'};added={'new-ncn':'4'}
  preservation(before,{**before,**added},added)
  for after in [{'new-ncn':'4'},{**before,**added,'collections/other':'5'}]:
   with self.assertRaises(ValueError):preservation(before,after,added)
 def test_configuration_retains_existing_rules_and_adds_only_immutable_ncn_rule(self):
  original={'headers':[{'glob':'**','headers':{'X-Content-Type-Options':'nosniff'}}],'redirects':[{'glob':'/a','location':'/b','statusCode':302}]};before=copy.deepcopy(original)
  changed=configuration(original);self.assertEqual(before,original);self.assertEqual(original['headers'],changed['headers'][:-1]);self.assertEqual(changed,configuration(changed));self.assertEqual(original['redirects'],changed['redirects'])
 def test_corrupt_database_rejected_before_sqlite(self):
  with tempfile.TemporaryDirectory()as work:
   p=Path(work)/'db';p.write_bytes(b'not the catalogue')
   with self.assertRaises(ValueError):verify_database(p,self.m)
 def test_live_manifest_check_does_not_download_asset_again(self):
  get=Mock(return_value=SimpleNamespace(status_code=200,content=b'{}',json=lambda:self.m))
  with patch.dict(sys.modules,{'requests':SimpleNamespace(get=get)}):served_manifest('https://example.invalid',self.m)
  self.assertEqual(1,get.call_count)
  self.assertTrue(get.call_args.args[0].endswith('/collections/published/ncn/manifest-v2.json'))
  self.assertFalse(get.call_args.kwargs['allow_redirects'])
 def test_live_manifest_mismatch_or_redirect_rejected(self):
  for status,m in [(302,self.m),(200,{**self.m,'revision':99})]:
   get=Mock(return_value=SimpleNamespace(status_code=status,content=b'{}',json=lambda:m))
   with patch.dict(sys.modules,{'requests':SimpleNamespace(get=get)}),self.assertRaises(ValueError):served_manifest('https://example.invalid',self.m)
if __name__=='__main__':unittest.main()
