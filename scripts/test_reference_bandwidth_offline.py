"""No network: pin preparation and source integration contract checks."""
import gzip,hashlib,json,socket,sqlite3,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
from prepare_ncn_reference import prepare
ROOT=Path(__file__).resolve().parents[1]

class OfflineReferences(unittest.TestCase):
    def setUp(self):
        self.block=patch.object(socket.socket,'connect',side_effect=AssertionError('Network forbidden'))
        self.block.start();self.addCleanup(self.block.stop)
    def fixture(self,d):
        directory=d/'cache';directory.mkdir();db=directory/'catalogue.db'
        with sqlite3.connect(db) as c:
            c.execute('CREATE TABLE routes(id INTEGER)');c.execute('INSERT INTO routes VALUES(1)')
        raw=db.read_bytes();packed=gzip.compress(raw)
        sha=hashlib.sha256(raw).hexdigest()
        m=dict(schema_version=2,scope='national-verified-open-checkpoint',whole_published_route_ready=False,
               sha256=sha,compressed_sha256=hashlib.sha256(packed).hexdigest(),filename=f'collections/published/ncn/{sha}/catalogue.db.gz',
               database_bytes=len(raw),compressed_bytes=len(packed),supported_identities=1,revision=1)
        manifest=d/'manifest.json';manifest.write_text(json.dumps(m));return manifest,directory,db
    def test_missing_pin_fails_before_urlopen(self):
        with tempfile.TemporaryDirectory() as t,patch('urllib.request.urlopen',side_effect=AssertionError('Forbidden')):
            m,d,db=self.fixture(Path(t));db.unlink()
            with self.assertRaisesRegex(RuntimeError,'offline mode'):prepare(m,d)
    def test_valid_pin_never_opens_network(self):
        with tempfile.TemporaryDirectory() as t,patch('urllib.request.urlopen',side_effect=AssertionError('Forbidden')):
            m,d,db=self.fixture(Path(t));self.assertEqual(db,prepare(m,d))
    def test_corrupt_pin_fails_without_network(self):
        with tempfile.TemporaryDirectory() as t,patch('urllib.request.urlopen',side_effect=AssertionError('Forbidden')):
            m,d,db=self.fixture(Path(t));db.write_bytes(b'broken')
            with self.assertRaisesRegex(RuntimeError,'offline mode'):prepare(m,d)
    def test_build_workflows_never_opt_into_firebase_fetch(self):
        for name in ['android-prototype-build.yml','build-android-prototype.yml']:
            text=(ROOT/'.github/workflows'/name).read_text()
            self.assertIn('actions/cache@v4',text);self.assertNotIn('--allow-firebase-download',text)
        script=(ROOT/'android-prototype/scripts/prepare_county_reference.sh').read_text()
        self.assertNotIn('curl ',script);self.assertNotIn('web.app',script)
    def test_all_three_hosted_transports_are_instrumented(self):
        source=ROOT/'android-prototype/app/src/main/java/com/roadprints/capture'
        for name in ['NcnCatalogueFiles.java','VenueCatalogueUpdates.java','LocalRoadSettlementMatcher.java']:
            self.assertIn('new ReferenceDataConnection',(source/name).read_text())
        self.assertIn('synchronized(INSTALL_LOCK)',(source/'NcnCatalogueFiles.java').read_text())
        for name in ['MapActivity.java','RoadDetailModel.java']:
            self.assertRegex((source/name).read_text(),r'LocalRoadSettlementMatcher.boundary\((this|context),')

if __name__=='__main__':unittest.main()
