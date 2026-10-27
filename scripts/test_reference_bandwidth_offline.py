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
            self.assertIn('actions/cache/restore@v4',text);self.assertNotIn('--allow-firebase-download',text)
        script=(ROOT/'android-prototype/scripts/prepare_county_reference.sh').read_text()
        self.assertNotIn('curl ',script);self.assertNotIn('web.app',script)
    def test_all_three_hosted_transports_are_instrumented(self):
        source=ROOT/'android-prototype/app/src/main/java/com/roadprints/capture'
        for name in ['NcnCatalogueFiles.java','VenueCatalogueUpdates.java','LocalRoadSettlementMatcher.java']:
            self.assertIn('new ReferenceDataConnection',(source/name).read_text())
        self.assertIn('synchronized(INSTALL_LOCK)',(source/'NcnCatalogueFiles.java').read_text())
        for name in ['MapActivity.java','RoadDetailModel.java']:
            self.assertRegex((source/name).read_text(),r'LocalRoadSettlementMatcher.boundary\((this|context),')

    def test_local_database_import_is_verified_and_reusable(self):
        with tempfile.TemporaryDirectory() as t:
            m,d,db=self.fixture(Path(t));dest=Path(t)/'destination'
            self.assertEqual(db.read_bytes(),prepare(m,dest,source_database=db).read_bytes())
            self.assertEqual(db.read_bytes(),prepare(m,dest).read_bytes())
    def test_local_compressed_import_is_verified(self):
        with tempfile.TemporaryDirectory() as t:
            m,d,db=self.fixture(Path(t));packed=Path(t)/'input.gz';packed.write_bytes(gzip.compress(db.read_bytes()))
            self.assertEqual(db.read_bytes(),prepare(m,Path(t)/'destination',source_archive=packed).read_bytes())
    def test_corrupt_import_preserves_existing_destination(self):
        with tempfile.TemporaryDirectory() as t:
            m,d,db=self.fixture(Path(t));db.write_bytes(b'previous-generation')
            bad=Path(t)/'bad.gz';bad.write_bytes(b'corrupt')
            with self.assertRaises(ValueError):prepare(m,d,source_archive=bad)
            self.assertEqual(b'previous-generation',db.read_bytes());self.assertFalse(list(d.glob('*.tmp')))
    def test_no_network_escape_in_pin_preparation(self):
        from verify_offline_build_controls import verify
        verify()
    def test_historical_download_helpers_are_rejected(self):
        from verify_offline_build_controls import verify
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);(root/'scripts').mkdir();(root/'android-prototype/scripts').mkdir(parents=True)
            (root/'scripts/prepare_ncn_reference.py').write_text('import urllib.request')
            with self.assertRaisesRegex(ValueError,'network transport'):verify(root)
            (root/'scripts/prepare_ncn_reference.py').write_text('source_database = None')
            (root/'android-prototype/scripts/prepare_county_reference.sh').write_text('curl https://synthetic.invalid')
            with self.assertRaisesRegex(ValueError,'local-only'):verify(root)
    def test_workflows_guard_source_before_preparation_and_use_local_packager(self):
        for name in ['android-prototype-build.yml','build-android-prototype.yml']:
            text=(ROOT/'.github/workflows'/name).read_text()
            self.assertLess(text.index('python3 scripts/verify_offline_build_controls.py'),text.index('python3 scripts/prepare_ncn_reference.py'))
            self.assertLess(text.index('python3 scripts/verify_offline_build_controls.py'),text.index('bash android-prototype/scripts/prepare_county_reference.sh'))
            self.assertIn('reference-pins-v2-',text);self.assertNotIn('actions/cache/save@',text)
        for name in ['android-release-request.yml','ncn-reference-publication.yml','android-development-request.yml']:
            text=(ROOT/'.github/workflows'/name).read_text()
            self.assertIn('uses: ./.github/workflows/build-android-prototype.yml',text)
            self.assertNotIn('build-android-prototype.yml@',text)
    def test_seed_is_manual_github_artifact_only_and_validates_before_save(self):
        text=(ROOT/'.github/workflows/reference-pin-cache.yml').read_text()
        self.assertIn('workflow_dispatch:',text);self.assertNotIn('  push:',text);self.assertNotIn('  pull_request:',text)
        self.assertIn('actions/download-artifact@v4',text)
        self.assertLess(text.index('python3 scripts/seed_reference_pins.py'),text.index('actions/cache/save@v4'))
        self.assertIn('github.actor == github.repository_owner',text)
    def test_gradle_blocks_reference_transport_before_url_open(self):
        text=(ROOT/'android-prototype/app/build.gradle.kts').read_text()
        self.assertIn('systemProperty("roadprints.referenceNetworkDisabled", "true")',text)
        text=(ROOT/'android-prototype/app/src/main/java/com/roadprints/capture/ReferenceDataConnection.java').read_text()
        self.assertLess(text.index('Boolean.getBoolean'),text.index('url.openConnection()'))
    def test_bundle_seed_and_corruption_rejection(self):
        from seed_reference_pins import seed
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);manifest,d,db=self.fixture(root)
            source=root/'inputs';source.mkdir();(source/'catalogue.db').write_bytes(db.read_bytes())
            data=b'synthetic county boundary';(source/'boundaries.bin').write_bytes(data)
            county=root/'android-prototype/app/src/main/assets/historic-counties';county.mkdir(parents=True)
            (county/'catalogue.json').write_text(json.dumps({'data_sha256':hashlib.sha256(data).hexdigest(),'counties':[{'offset':0,'length':len(data)}]}))
            ncn=root/'android-prototype/app/src/main/assets/ncn-national';ncn.mkdir();(ncn/'manifest.json').write_bytes(manifest.read_bytes())
            seed(source,root);self.assertEqual(data,(county/'boundaries.bin').read_bytes())
            (source/'boundaries.bin').write_bytes(b'x'*len(data))
            with self.assertRaisesRegex(ValueError,'checksum'):seed(source,root)
            self.assertEqual(data,(county/'boundaries.bin').read_bytes())

if __name__=='__main__':unittest.main()
