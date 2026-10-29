from copy import deepcopy
import json
from pathlib import Path
import unittest
from .build_route177_alternatives import build,chain,NORTH_WEST
from .coverage import merge_intervals
from .journey_replay import projector
from .route177_alternatives import load_trial


class Route177JoinTests(unittest.TestCase):
    def setUp(self):
        self.folder=Path(__file__).resolve().parents[2]/'tests/fixtures/ncn-177-discrepancy'
        self.ledger,self.data,self.paths,self.manifest=load_trial()

    def test_manifest_rebuild_is_reproducible(self):
        self.assertEqual(build(self.folder),self.manifest)
        self.assertEqual(len(self.manifest['bindings']),27)
        self.assertAlmostEqual(sum(s['length_m'] for s in self.manifest['logical_sections']),1847.113208352,places=6)

    def test_each_reviewed_itinerary_has_no_logical_gap_or_double_span(self):
        for itinerary in self.manifest['itineraries']:
            section=next(s for s in self.manifest['logical_sections'] if s['key']==itinerary['logical_key'])
            mode=itinerary['modes'][0]
            spans=[(b['logical_start'],b['logical_end']) for b in self.manifest['bindings']
                if b['logical_key']==section['key'] and b['physical_key'] in itinerary['way_ids'] and mode in b['modes']]
            merged=merge_intervals(spans)
            self.assertEqual(len(merged),1)
            self.assertAlmostEqual(merged[0][0],0,places=6)
            self.assertAlmostEqual(merged[0][1],section['length_m'],places=6)
            self.assertAlmostEqual(sum(abs(z-a) for a,z in spans),section['length_m'],places=6)

    def test_identical_coordinate_without_shared_node_cannot_form_join(self):
        ways={str(w['way_id']):deepcopy(w) for w in self.data['source_member_ways']+self.data['nearby_nonmember_ways']}
        ways[str(NORTH_WEST[1])]['node_ids'][0]=-1
        with self.assertRaisesRegex(ValueError,'Disconnected'):
            chain(ways,NORTH_WEST,projector(self.data))

    def test_context_roads_are_not_promoted_and_eastern_terminal_is_observed(self):
        context=self.data['endpoint_context']
        self.assertFalse(set(str(i) for i in context['adjoining_road_way_ids']) & {p.way_id for p in self.paths})
        self.assertEqual(self.manifest['eastern_south_terminal_node'],context['south_east_node'])
        self.assertLess(self.manifest['eastern_terminal_offset_m'],1.1)
        self.assertGreater(self.manifest['eastern_terminal_offset_m'],0)
