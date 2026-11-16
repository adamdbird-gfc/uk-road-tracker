"""Classifier accounting tests; never grant source membership or credit."""
import unittest
from shapely.geometry import LineString, Point, GeometryCollection
from scripts.classify_ncn_binding_gaps import partition, geometry_spans, interval_without_direction
from scripts.ncn_trial.open_completion import edge_interval, POLICY

class GapClassificationTests(unittest.TestCase):
    def test_exclusive_priority_partition_preserves_the_complete_gap(self):
        layers={'deferred_binding_withheld':[(0,2)],'ambiguous_target_correspondence':[(1,5)],
            'direction_gate':[(5,8)],'finite_endpoint_or_alignment':[(0,9)]}
        actual=partition([(0,10)],layers)
        self.assertEqual(actual,[(0,2,'deferred_binding_withheld'),(2,5,'ambiguous_target_correspondence'),
            (5,8,'direction_gate'),(8,9,'finite_endpoint_or_alignment'),(9,10,'no_admitted_source_correspondence')])
        self.assertEqual(sum(z-a for a,z,c in actual),10)

    def test_nearby_and_unadmitted_evidence_are_distinct_and_keep_gaps(self):
        result=partition([(0,2),(4,10)],{'unadmitted_span_of_known_way':[(4,6)],'finite_endpoint_or_alignment':[(0,10)]})
        self.assertEqual(result,[(0,2,'finite_endpoint_or_alignment'),(4,6,'unadmitted_span_of_known_way'),(6,10,'finite_endpoint_or_alignment')])
        self.assertEqual(sum(z-a for a,z,c in result),8)

    def test_isolated_crossing_point_has_no_interval(self):
        line=LineString([(0,0),(10,0)])
        self.assertEqual(geometry_spans(line,Point(5,0)),[])
        self.assertEqual(geometry_spans(line,GeometryCollection([Point(5,0),LineString([(2,0),(4,0)])])),[(2,4)])

    def test_direction_diagnostic_does_not_change_production_policy(self):
        before=dict(POLICY);target=LineString([(0,0),(100,0)])
        self.assertIsNone(edge_interval((0,0),(100,30),target))
        v=interval_without_direction((0,0),(100,30),target)
        self.assertIsNotNone(v);self.assertLessEqual(max(v[2:]),100/3+1e-7)
        self.assertEqual(POLICY,before);self.assertEqual(POLICY['maximum_angle_degrees'],15)
        self.assertIsNone(interval_without_direction((50,-10),(50,10),target))

    def test_ordinary_seam_excludes_hairpins_branches_and_unbracketed_sources(self):
        from collections import Counter
        from scripts.probe_ncn_target_seams import bracketed_continuation
        ordinary=[LineString([(-100,0),(0,0)]),LineString([(0,0),(100,10)])]
        degree=Counter({(-100.,0.):1,(0.,0.):2,(100.,10.):1})
        self.assertTrue(bracketed_continuation((-10,1),(10,1),ordinary,degree,(0,20)))
        hairpin=[ordinary[0],LineString([(0,0),(-100,10)])]
        self.assertFalse(bracketed_continuation((-10,1),(10,1),hairpin,degree,(0,20)))
        degree[(0.,0.)]=3
        self.assertFalse(bracketed_continuation((-10,1),(10,1),ordinary,degree,(0,20)))
        degree[(0.,0.)]=2
        self.assertFalse(bracketed_continuation((10,1),(20,1),ordinary,degree,(0,10)))

    def test_pinned_ordinary_witnesses_and_hairpin_remain_distinct(self):
        import json
        from pathlib import Path
        from collections import Counter
        from scripts.probe_ncn_target_seams import bracketed_continuation
        root=Path(__file__).resolve().parents[2]
        fixture=json.loads((root/'tests/fixtures/ncn-gap-seams/diagnostic-witnesses.json').read_text())
        for e in fixture['ordinary_candidates']:
            lines=[LineString(c)for c in e['target_edges_metric']]
            degrees=Counter(tuple(c)for line in lines for c in line.coords)
            self.assertTrue(bracketed_continuation(*e['source_edge_metric'],lines,degrees,e['source_window_m']))
        e=fixture['genuine_hairpin_negative'];lines=[LineString(c)for c in e['target_edges_metric']]
        degrees=Counter(tuple(c)for line in lines for c in line.coords)
        self.assertFalse(bracketed_continuation(*e['source_edge_metric'],lines,degrees,e['source_window_m']))

if __name__=='__main__':unittest.main()
