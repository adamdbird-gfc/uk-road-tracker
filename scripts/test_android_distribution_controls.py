import unittest
from pathlib import Path
from verify_android_beta_approval import validate

ROOT=Path(__file__).resolve().parents[1]
class DistributionControlsTest(unittest.TestCase):
 def setUp(self):
  self.sha='a'*40;self.digest='b'*64
  self.run={'head_sha':self.sha,'conclusion':'success','path':'.github/workflows/android-development-request.yml'}
 def test_only_manual_owner_exact_artifact_approval(self):
  args=['workflow_dispatch','owner','owner','APPROVE_BETA '+self.sha,self.sha,self.digest,self.run]
  validate(*args)
  for index,value in [(0,'push'),(0,'pull_request'),(1,'collaborator'),(3,'APPROVE_BETA'),(4,'c'*40),(5,'')]:
   bad=list(args);bad[index]=value
   with self.assertRaises(ValueError):validate(*bad)
  for field,value in [('head_sha','c'*40),('conclusion','failure'),('path','.github/workflows/other.yml')]:
   bad=dict(self.run);bad[field]=value
   with self.assertRaises(ValueError):validate(*args[:-1],bad)
 def test_no_automatic_workflow_can_distribute(self):
  for p in (ROOT/'.github/workflows').glob('*.yml'):
   text=p.read_text()
   if p.name=='android-approved-beta.yml':
    self.assertIn('workflow_dispatch:',text);self.assertNotIn('workflow_call:',text);self.assertNotIn('  push:',text)
    self.assertIn('github.actor == github.repository_owner',text)
    self.assertIn('environment: approved-beta',text)
    self.assertIn('scripts/verify_android_beta_approval.py',text)
   else:
    self.assertNotIn('bash scripts/upload_android_firebase.sh',text,p.name)
    self.assertNotIn('FIREBASE_SERVICE_ACCOUNT_JSON',text,p.name)
    self.assertNotIn('firebase_upload: true',text,p.name)
    self.assertNotIn('distribute_existing_testers: true',text,p.name)
 def test_packaging_fails_closed_for_legacy_flags(self):
  s=(ROOT/'.github/workflows/build-android-prototype.yml').read_text()
  self.assertIn('if: inputs.firebase_upload || inputs.distribute_existing_testers',s)
  self.assertNotIn('\n  firebase:',s)
  self.assertIn('testDebugUnitTest lintDebug assembleDebug',s)
  self.assertIn('prepare_ncn_reference.py',s)
  self.assertNotIn('ncnOfflineChecks',s)
 def test_uploader_checks_approval_before_any_firebase_call(self):
  s=(ROOT/'scripts/upload_android_firebase.sh').read_text()
  self.assertLess(s.index('ROADPRINTS_APPROVED_APK_SHA256'),s.index('firebase appdistribution:distribute'))
  self.assertIn('workflow_dispatch',s);self.assertIn('GITHUB_REPOSITORY_OWNER',s)
if __name__=='__main__':unittest.main()
