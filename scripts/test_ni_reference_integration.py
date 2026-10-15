import gzip,hashlib,json,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
from shapely.geometry import Point
import stage_ni_reference_additions as staging
from prepare_ni_integration import decode_county
from preview_ni_reference_data import Hosting,preservation_check,source_coverage_check,version_name,SITE,MANIFEST


def uint(value):
    out=bytearray()
    while value>=128:out.append((value&127)|128);value>>=7
    out.append(value);return out


def packed(rings):
    out=bytearray(uint(len(rings)))
    for ring in rings:
        out+=uint(len(ring));x=y=0
        for a,b in ring:
            a,b=round(a*1e6),round(b*1e6)
            for value in (a-x,b-y):out+=uint(value*2 if value>=0 else -value*2-1)
            x,y=a,b
    return gzip.compress(out)


class CountyDecodingTests(unittest.TestCase):
    def test_holes_and_detached_islands_survive_even_when_hole_centre_is_an_island(self):
        outer=[(0,0),(0,4),(4,4),(4,0)]
        hole=[(1,1),(3,1),(3,3),(1,3)]
        island=[(1.5,1.5),(1.5,2.5),(2.5,2.5),(2.5,1.5)]
        shape=decode_county(packed([outer,hole,island]))
        self.assertTrue(shape.is_valid);self.assertAlmostEqual(shape.area,13)
        self.assertTrue(shape.contains(Point(.5,.5)));self.assertFalse(shape.contains(Point(1.1,1.1)))
        self.assertTrue(shape.contains(Point(2,2)))
    def test_missing_exterior_and_trailing_bytes_fail(self):
        with self.assertRaises(ValueError):decode_county(packed([[(0,0),(4,0),(4,4),(0,4)]]))
        data=gzip.decompress(packed([[(0,0),(0,4),(4,4),(4,0)]]))
        with self.assertRaises(ValueError):decode_county(gzip.compress(data+b'X'))


class HostingSafetyTests(unittest.TestCase):
    def test_only_firebase_managed_extras_are_permitted(self):
        expected={'gb.json':'a',MANIFEST:'m'}
        source_coverage_check(expected,expected)
        source_coverage_check({**expected,'__/firebase/init.js':'b','__/firebase/init.json':'c'},expected)
        for before in [{'gb.json':'a'},{**expected,'unexpected.json':'x'}]:
            with self.assertRaises(ValueError):source_coverage_check(before,expected)
    def test_only_additions_and_manifest_may_change(self):
        before={'gb.json':'a',MANIFEST:'old'};added={'ni.json':'b',MANIFEST:'new'}
        preservation_check(before,dict(before,**added),added)
        for after in [{'gb.json':'x',**added},{**added},{**before,**added,'source.py':'secret'}]:
            with self.assertRaises(ValueError):preservation_check(before,after,added)
    def test_other_site_and_live_mutations_rejected_before_network(self):
        with self.assertRaises(ValueError):version_name('sites/other/versions/a')
        hosting=Hosting(None)
        for path in [f'sites/{SITE}/releases',f'sites/{SITE}/channels/live/releases']:
            with self.assertRaises(ValueError):hosting.request('POST',path,json={})
    def test_paginated_file_listing_keeps_exact_hashes(self):
        class Fake:
            def __init__(self):self.calls=0
            def request(self,*a,**kw):
                self.calls+=1
                class Response:
                    status_code=200
                    def __init__(self,data):self.data=data
                    def json(self):return self.data
                return Response({'files':[{'path':'/a.json' if self.calls==1 else '/b.json','hash':'a'*64}],**({'nextPageToken':'next'} if self.calls==1 else {})})
        self.assertEqual(Hosting(Fake()).files(f'sites/{SITE}/versions/a'),{'a.json':'a'*64,'b.json':'a'*64})


class StagingTests(unittest.TestCase):
    def fixture(self,root):
        gb=root/'gb.json';gb.write_text(json.dumps({'version':1,'inventories':[{'code':f'E{i:08d}','status':'available','count':i,'extra':'preserve'} for i in range(8545)]}))
        bounds=root/'bounds';ni=root/'ni';bounds.mkdir();ni.mkdir();features=[];rows=[]
        for i in range(527):
            code=f'N11{i:06d}';payload=(json.dumps({'version':1,'source':'NISRA','code':code,'name':code,'count':1,'roads':['Road']})+'\n').encode()
            (ni/(code+'.json')).write_bytes(payload)
            rows.append({'code':code,'count':1,'bytes':len(payload),'sha256':hashlib.sha256(payload).hexdigest()})
            features.append({'type':'Feature','properties':{'code':code,'name':code},'geometry':{'type':'Polygon','coordinates':[[[-7,54.5],[-6.9,54.5],[-6.9,54.6],[-7,54.6],[-7,54.5]]]}})
        raw=json.dumps({'type':'FeatureCollection','features':features}).encode();(bounds/'ni-boundary-candidates.geojson.gz').write_bytes(gzip.compress(raw))
        manifest=json.dumps({'inventories':rows}).encode();(ni/'candidate-manifest.json').write_bytes(manifest)
        return gb,bounds,ni,hashlib.sha256(raw).hexdigest(),hashlib.sha256(manifest).hexdigest()
    def test_all_ni_additions_preserve_every_gb_manifest_record(self):
        with tempfile.TemporaryDirectory() as f:
            root=Path(f);gb,bounds,ni,bhash,nhash=self.fixture(root)
            with patch.multiple(staging,GB_MANIFEST_SHA256=hashlib.sha256(gb.read_bytes()).hexdigest(),NI_MANIFEST_SHA256=nhash,NI_BOUNDARY_SHA256=bhash):
                report=staging.stage(gb,bounds,ni,root/'public')
                self.assertEqual(len(report['files']),1055)
                combined=json.loads((root/'public'/MANIFEST).read_text())['inventories']
                self.assertEqual(len(combined),9072)
                self.assertEqual(combined[:8545],json.loads(gb.read_text())['inventories'])
                (ni/'N11000000.json').write_bytes(b'changed')
                with self.assertRaises(ValueError):staging.stage(gb,bounds,ni,root/'changed')
    def test_reviewed_gb_drift_stops_before_output(self):
        with tempfile.TemporaryDirectory() as f:
            root=Path(f);gb=root/'changed.json';gb.write_text('{}')
            with self.assertRaises(ValueError):staging.stage(gb,root,root,root/'out')
            self.assertFalse((root/'out').exists())


if __name__=='__main__':unittest.main()
