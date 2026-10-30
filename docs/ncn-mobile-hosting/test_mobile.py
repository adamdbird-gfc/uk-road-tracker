import hashlib,json,re,unittest
from pathlib import Path
HERE=Path(__file__).resolve().parent

class MobileViewerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.hosted=(HERE/'site/index.html').read_text()
        cls.offline=(HERE/'site/offline.html').read_bytes()
    def test_offline_fallback_is_exact_verified_249_artifact(self):
        self.assertEqual(hashlib.sha256(self.offline).hexdigest(),'b2da01d979afa696ca9affedef721cbb96cdc932c6128c2a591708e54e0bbba7')
    def test_hosted_diagnostic_data_is_unchanged(self):
        extract=lambda s:json.loads(re.search(r'<script id="evidence" type="application/json">(.*?)</script>',s,re.S).group(1))
        self.assertEqual(extract(self.hosted),extract(self.offline.decode()))
        self.assertEqual(len(extract(self.hosted)['cases']),8)
    def test_no_private_capture_payload_or_external_assets(self):
        for value in ['raw_capture_samples','roadprints_journey_diagnostics','<script src=','<link ']:self.assertNotIn(value,self.hosted)
        self.assertIn("connect-src 'none'",self.hosted)
    def test_mobile_controls_and_download(self):
        self.assertIn('min-height:44px',self.hosted)
        self.assertIn('min-width:44px',self.hosted)
        self.assertIn('width=device-width,initial-scale=1',self.hosted)
        self.assertIn('href="offline.html" download="Roadprints_NCN_Viewer.html"',self.hosted)

if __name__=='__main__':unittest.main()
