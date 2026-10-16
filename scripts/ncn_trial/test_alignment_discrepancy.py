"""Pinned real-data regressions: nearby OSM linework is not route membership."""
import json
from pathlib import Path
import unittest
from shapely.geometry import LineString
from shapely.ops import unary_union
from .journey_replay import projector
from .physical_crosswalk import compare_route


class AlignmentDiscrepancyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data=json.loads((Path(__file__).resolve().parents[2]/
            'tests/fixtures/ncn-177-discrepancy/public-snapshot.json').read_text())
        project=projector(cls.data)
        cls.sections=[{'key':str(f['properties']['FID']), 'route_type':'NCN','route_no':177,
            'line':LineString([project(c) for c in f['geometry']['coordinates']])}
            for f in cls.data['official_features']]
        cls.source=[{'way_id':w['way_id'],'line':LineString([project(c) for c in w['coordinates']])}
            for w in cls.data['source_member_ways']]
        cls.nearby={w['way_id']:LineString([project(c) for c in w['coordinates']])
            for w in cls.data['nearby_nonmember_ways']}

    def test_nearby_paths_are_not_members(self):
        members={m['ref'] for m in self.data['relation']['members'] if m['type']=='way'}
        self.assertEqual(len(self.source),66)
        self.assertFalse(members.intersection(self.nearby))
        self.assertTrue({62216492,94920359}.issubset(members))

    def test_official_sections_disagree_with_member_alignment(self):
        r=compare_route(self.source,self.sections,'NCN',177)
        self.assertLess(r['corridor_sensitivity']['10']['official_fraction'],.01)
        self.assertFalse(r['association_approved'])

    def test_northern_cycle_paths_explain_most_official_geometry(self):
        ways=[{'way_id':i,'line':self.nearby[i]} for i in [44332298,1302385780,1251416153]]
        r=compare_route(ways,self.sections,'NCN',177)
        self.assertGreater(r['corridor_sensitivity']['10']['official_fraction'],.94)
        self.assertFalse(r['completion_ready'])

    def test_separate_footway_and_cycleway_both_fit_corridor(self):
        section=next(s['line'] for s in self.sections if s['key']=='7345')
        for i in [44332298,1460076188]:
            self.assertGreater(section.intersection(self.nearby[i].buffer(10)).length/section.length,.94)
        self.assertNotEqual(self.nearby[44332298],self.nearby[1460076188])


if __name__=='__main__':unittest.main()
