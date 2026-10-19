"""Read back the shipped indexed asset; no network/research dependency."""
import gzip,hashlib,json,sqlite3,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class NationalAssetTests(unittest.TestCase):
 def test_pins_identities_and_all_canonical_network_mappings(self):
    folder=ROOT/'android-prototype/app/src/main/assets/ncn-national';manifest=json.loads((folder/'manifest.json').read_text())
    with tempfile.TemporaryDirectory()as work:
        compressed=Path(work)/'catalogue.gz';database=Path(work)/'catalogue.db'
        with compressed.open('wb')as target:
            for part in manifest['asset_parts']:target.write((folder/part).read_bytes())
        import shutil
        with gzip.open(compressed,'rb')as source,database.open('wb')as target:shutil.copyfileobj(source,target)
        self.assertEqual(manifest['database_bytes'],database.stat().st_size);self.assertEqual(manifest['sha256'],hashlib.sha256(database.read_bytes()).hexdigest())
        db=sqlite3.connect(database);self.assertEqual('ok',db.execute('PRAGMA quick_check').fetchone()[0]);routes={k:json.loads(v)for k,v in db.execute('SELECT key,data FROM routes')}
        self.assertEqual(219,len(routes));self.assertEqual(62399,db.execute('SELECT COUNT(DISTINCT way) FROM paths').fetchone()[0]);self.assertEqual(62487,db.execute('SELECT COUNT(*) FROM paths').fetchone()[0]);self.assertEqual(34,len(manifest['held_route_keys']));self.assertFalse(set(routes)&set(manifest['held_route_keys']));self.assertEqual(218,sum(v['measurable']for v in routes.values()));self.assertFalse(routes['NCN:228']['measurable'])
        self.assertAlmostEqual(manifest['network_length_m'],db.execute('SELECT SUM(length) FROM network').fetchone()[0],places=4)
        failures=[];count=0
        for route,key,length,network in db.execute('SELECT route,key,length,network FROM targets'):
            if not routes[route]['measurable']:continue
            pieces=json.loads(network);count+=1;mapped=sum(abs(p[2]-p[1])for p in pieces)
            if abs(mapped-length)>max(1e-3,length*1e-7):failures.append((route,key,length,mapped))
            for edge,a,z,n in pieces:self.assertGreater(n,0);self.assertGreaterEqual(min(a,z),-1e-5);self.assertLessEqual(max(a,z),length+1e-5)
        self.assertGreater(count,10000);self.assertEqual([],failures[:10],f'{len(failures)} open targets lost or duplicated physical correspondence')
        self.assertEqual(219,db.execute('SELECT COUNT(*) FROM overview').fetchone()[0]);db.close()
if __name__=='__main__':unittest.main()
