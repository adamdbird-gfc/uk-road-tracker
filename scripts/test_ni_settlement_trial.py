import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from unittest.mock import patch

import osmium
from shapely.geometry import Polygon
from bulk_settlement_inventory_trial import digest
from prepare_ni_boundaries import normalise_records, prepare
from ni_settlement_inventory_trial import generate


def identity(x,y,z=None):
    return x,y


def props(code='N11000001', name='EXAMPLE'):
    return {'Code':code,'Name':name,'Band':'H'}


def square():
    return Polygon([(-7,54.5),(-6.99,54.5),(-6.99,54.51),(-7,54.51),(-7,54.5)])


class BoundaryTests(unittest.TestCase):
    def test_unique_codes_count_and_coordinate_envelope(self):
        rows=[(props(),square())]
        result,repairs,overlaps=normalise_records(rows,identity,expected_count=1)
        self.assertEqual(result['features'][0]['properties']['code'],'N11000001')
        self.assertFalse(repairs or overlaps)
        for bad in [rows*2,[(props(code='E63000001'),square())],[(props(name=''),square())],
                    [(props(),Polygon([(0,0),(1,0),(1,1),(0,1),(0,0)]))]]:
            with self.assertRaises(ValueError):normalise_records(bad,identity,expected_count=len(bad))
        with self.assertRaises(ValueError):normalise_records(rows,identity,expected_count=527)

    def test_empty_and_non_polygon_rejected(self):
        from shapely.geometry import LineString
        for g in [Polygon(),LineString([(0,0),(1,1)])]:
            with self.assertRaises(ValueError):normalise_records([(props(),g)],identity,expected_count=1)

    def test_repair_retains_footprint_and_rejects_material_changes(self):
        g=Polygon([(-7,54.5),(-6.99,54.5),(-6.99,54.51),(-6.995,54.51),
                   (-6.995,54.505),(-6.995,54.51),(-7,54.51),(-7,54.5)])
        self.assertFalse(g.is_valid)
        # This source produces non-polygon remnants; the conservative gate rejects it.
        with self.assertRaises(ValueError):normalise_records([(props(),g)],identity,expected_count=1)
        touching=Polygon([(-7,54.5),(-6.995,54.5),(-6.995,54.505),(-7,54.505),
                          (-7,54.5),(-7.005,54.5),(-7.005,54.495),(-7,54.495),(-7,54.5)])
        result,repairs,_=normalise_records([(props(),touching)],identity,expected_count=1)
        self.assertEqual(len(repairs),1)
        self.assertLess(repairs[0]['relative_area_change'],1e-8)
        bigger=Polygon([(-7,54.5),(-6.98,54.5),(-6.98,54.51),(-7,54.51),(-7,54.5)])
        with patch('prepare_ni_boundaries.shapely.make_valid',return_value=bigger):
            with self.assertRaises(ValueError):normalise_records([(props(),g)],identity,expected_count=1)

    def test_source_overlaps_reported_without_reassigning_boundaries(self):
        a=Polygon([(0,0),(100,0),(100,100),(0,100),(0,0)])
        b=Polygon([(50,0),(150,0),(150,100),(50,100),(50,0)])
        def to_ni(x,y,z=None):return x/100000-7,y/100000+54.5
        features,_,overlaps=normalise_records([(props(),a),(props('N11000002'),b)],to_ni,expected_count=2)
        self.assertEqual(len(features['features']),2)
        self.assertEqual(overlaps[0]['area_square_metres'],5000)

    def test_declared_irish_grid_is_converted_in_real_shapefile_pipeline(self):
        import io,zipfile,shapefile,pyproj
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);stem=root/'settlements-2015-above-500-threshold'
            with shapefile.Writer(str(stem),shapeType=shapefile.POLYGON) as writer:
                writer.field('Code','C',size=9);writer.field('Name','C',size=40);writer.field('Band','C',size=1)
                for i in range(527):
                    x=320000+(i%27)*200;y=370000+(i//27)*200
                    writer.poly([[(x,y),(x,y+100),(x+100,y+100),(x+100,y),(x,y)]])
                    writer.record(f'N11{i+1:06d}',f'EXAMPLE {i+1}','H')
            stem.with_suffix('.prj').write_text(pyproj.CRS.from_epsg(29902).to_wkt())
            archive=root/'source.zip'
            with zipfile.ZipFile(archive,'w') as z:
                for suffix in ('.shp','.shx','.dbf','.prj'):
                    path=stem.with_suffix(suffix);z.write(path,path.name)
            with patch('prepare_ni_boundaries.SOURCE_SHA256',hashlib.sha256(archive.read_bytes()).hexdigest()):
                with redirect_stdout(StringIO()):audit=prepare(archive,root/'out')
            self.assertEqual(audit['settlement_count'],527)
            self.assertEqual(audit['source_crs'],'EPSG:29902')
            self.assertEqual(audit['target_crs'],'EPSG:4326')
            self.assertLessEqual(audit['operation_accuracy_metres'],1)
            with gzip.open(root/'out/ni-boundary-candidates.geojson.gz','rt') as stream:
                first=json.load(stream)['features'][0]['geometry']['coordinates'][0][0]
            self.assertTrue(-9<first[0]<-5 and 54<first[1]<56)

    def test_source_digest_checked_before_parsing(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'fake.zip';p.write_bytes(b'not the reviewed source')
            with self.assertRaises(ValueError):prepare(p,Path(folder)/'out')


class TrialTests(unittest.TestCase):
    def fixture(self,root):
        boundaries=root/'boundaries';sources=root/'sources';boundaries.mkdir();sources.mkdir()
        data={'type':'FeatureCollection','features':[{'type':'Feature','properties':{
              'code':'N11000001','name':'EXAMPLE'},'geometry':square().__geo_interface__}]}
        raw=json.dumps(data).encode()
        (boundaries/'ni-boundary-candidates.geojson.gz').write_bytes(gzip.compress(raw))
        (boundaries/'boundary-audit.json').write_text(json.dumps({'candidate_geojson_sha256':hashlib.sha256(raw).hexdigest()}))
        pbf=sources/'fixture.osm.pbf'
        with osmium.SimpleWriter(str(pbf)) as writer:
            for i,xy in enumerate([(-6.999,54.501),(-6.991,54.509),(-6.8,54.7),(-6.79,54.71)],1):
                writer.add_node(osmium.osm.mutable.Node(id=i,location=xy))
            for i,name,highway,nodes in [(1,' Local Road ','residential',[1,2]),(2,'Local Road','tertiary',[1,2]),
                  (3,'Excluded Path','footway',[1,2]),(4,'ROI / Outside Road','residential',[3,4])]:
                writer.add_way(osmium.osm.mutable.Way(id=i,nodes=nodes,tags={'name':name,'highway':highway}))
        poly=sources/'ireland-and-northern-ireland.poly'
        poly.write_text('test\n1\n-8 54\n-6 54\n-6 56\n-8 56\n-8 54\nEND\nEND\n')
        (sources/'downloads.json').write_text(json.dumps({'file':pbf.name,'sha256':digest(pbf),'poly_sha256':digest(poly)}))
        return boundaries,sources

    def test_real_pbf_uses_gb_rules_and_excludes_roads_outside_ni_polygon(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);bounds,sources=self.fixture(root);out=root/'candidates'
            with redirect_stdout(StringIO()):report=generate(bounds,sources,out,['N11000001'])
            data=json.loads((out/'N11000001.json').read_text())
            self.assertEqual(data['roads'],['Local Road']);self.assertEqual(data['count'],1)
            self.assertIn('NISRA',data['source']);self.assertEqual(report['status'],'trial_only_not_published')
            self.assertFalse((out/'node-locations.index').exists())

    def test_source_drift_and_wrong_identity_abort(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);bounds,sources=self.fixture(root)
            for codes in [[],['N11000002'],['N11000001','N11000001']]:
                with self.assertRaises(ValueError):generate(bounds,sources,root/'out',codes)
            (sources/'fixture.osm.pbf').write_bytes(b'changed')
            with self.assertRaises(ValueError):generate(bounds,sources,root/'out',['N11000001'])

    def test_published_output_is_protected(self):
        with self.assertRaises(ValueError):generate('unused','unused','settlement-inventories-v1',['N11000001'])


if __name__=='__main__':unittest.main()
