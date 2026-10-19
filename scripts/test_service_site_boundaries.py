import gzip,json,math,unittest
from pathlib import Path
from shapely.geometry import shape,Point,LineString
from shapely.ops import transform
from build_service_site_boundaries import SOURCE,CATALOGUES,footprint,build

class ServiceFootprintTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.snapshot=json.loads(gzip.decompress(SOURCE.read_bytes()))
        cls.rows={s['id']:s for s in json.loads(CATALOGUES[1].read_text())['services']}
    def test_pinned_sources_cover_every_existing_identity(self):
        self.assertEqual(set(self.rows),{s['id'] for s in self.snapshot['services']})
        self.assertEqual(112,len(self.rows))
    def test_every_site_is_valid_and_reproduces_without_network(self):
        build(check=True)
        for source in self.snapshot['services']:
            g=shape(self.rows[source['id']]['site_boundary'])
            self.assertTrue(g.is_valid,source['id']);self.assertFalse(g.is_empty)
    def test_every_source_main_carriageway_is_excluded(self):
        for source in self.snapshot['services']:
            g=shape(self.rows[source['id']]['site_boundary'])
            for road in source['main_roads']:
                self.assertFalse(g.intersects(LineString(road['coordinates'])),(source['id'],road['source_id']))
    def test_warwick_main_building_fits_with_good_accuracy(self):
        north=shape(self.rows['msa:m40 warwick:52.2174:-1.504']['site_boundary'])
        south=shape(self.rows['msa:warwick m40:52.2188:-1.5033']['site_boundary'])
        p=Point(-1.5053546,52.2165186);self.assertTrue(north.contains(p));self.assertFalse(south.contains(p))
        sx=111195*math.cos(math.radians(p.y));sy=111195
        metres=transform(lambda x,y,z=None:((x-p.x)*sx,(y-p.y)*sy),north)
        self.assertGreater(metres.boundary.distance(Point(0,0)),31.18)
    def test_invalid_or_incomplete_sources_fail_instead_of_using_centroid(self):
        with self.assertRaises(ValueError):footprint({'id':'bad','sites':[{'kind':'way','source_id':'osm:way:1','rings':[[[0,0],[1,0],[1,1]]]}],'main_roads':[]})
        with self.assertRaises(ValueError):footprint({'id':'bad','sites':[{'kind':'relation','source_id':'osm:relation:1','outer':[],'inner':[]}],'main_roads':[]})
if __name__=='__main__':unittest.main()
