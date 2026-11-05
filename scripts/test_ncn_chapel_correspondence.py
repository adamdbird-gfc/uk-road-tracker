"""Finite reviewed source ownership and accounting, independent of publication."""
import copy
import unittest
from collections import defaultdict
from shapely.geometry import LineString, Point

from scripts.ncn_chapel_correspondence import load_review, corrected_path
from scripts.ncn_trial.journey_replay import projector
from scripts.ncn_trial.open_completion import POLICY, edge_interval
from scripts.ncn_trial.coverage import merge_intervals
from scripts.ncn_binding_attainability import network_intervals


class ChapelTests(unittest.TestCase):
    def setUp(self):
        self.review = load_review()
        self.path = self.review['path']
        self.binding = self.review['binding']

    def test_endpoint_representation_has_finite_two_sided_road_support(self):
        r = self.review
        project = projector({'projection_origin': self.path['origin']})
        xy = [project(c) for c in self.path['coordinates']]
        target = next(t for t in r['full_precision_targets'] if t['key'] == self.binding['target'])
        # The ordinary 15-degree gate stays rejected; only the exact review opts in.
        self.assertIsNone(edge_interval(xy[-2], xy[-1], LineString(target['metric_coordinates'])))
        junction = Point(target['metric_coordinates'][-1])
        self.assertLess(junction.distance(Point(xy[-1])), 2.)
        finite=LineString(target['metric_coordinates'])
        # Difference between linearly corresponding points is affine: its norm
        # is bounded by the two endpoint norms, so this checks the full span.
        self.assertLess(Point(xy[-2]).distance(finite.interpolate(self.binding['low'])),2.)
        self.assertLess(Point(xy[-1]).distance(finite.interpolate(self.binding['high'])),2.)
        self.assertEqual(r['neighbour']['nodes'][0], self.path['nodes'][-1])
        self.assertEqual(r['target']['coords'][-1], r['following_target']['coords'][0])
        # All three original incident edges are retained; precisely two touch here.
        joint = target['metric_coordinates'][-1]
        self.assertEqual(sum(joint in t['metric_coordinates'] for t in r['full_precision_targets']), 2)
        self.assertEqual((POLICY['maximum_offset_m'], POLICY['maximum_angle_degrees']), (10.,15.))
        self.assertFalse(POLICY['endpoint_extrapolation'])

    def test_source_geometry_nodes_route_modes_and_previous_bindings_fail_closed(self):
        mutations = [lambda p:p.update(way='77611523'), lambda p:p.update(route='NCN:7'),
                     lambda p:p.update(modes=['cycling']), lambda p:p.update(source_geometry_sha256='0'*64),
                     lambda p:p['coordinates'][-1].__setitem__(0, 1.4),
                     lambda p:p['nodes'].__setitem__(-1, 123),
                     lambda p:p['bindings'][-1].update(high=40.55143977293073)]
        for mutate in mutations:
            p=copy.deepcopy(self.path);mutate(p)
            with self.assertRaises(ValueError):corrected_path(p,self.review)

    def test_only_final_edge_gets_one_finite_binding_and_old_bindings_survive(self):
        p=corrected_path(self.path,self.review)
        self.assertEqual(p['bindings'][:-1],self.path['bindings'])
        b=p['bindings'][-1]
        self.assertEqual((b['start'],b['end']),tuple(self.path['positions'][-2:]))
        self.assertAlmostEqual(b['high']-b['low'],8.88267804313356)
        self.assertEqual(self.path,self.review['path'])
        self.assertTrue(all(old['end']<=b['start'] for old in p['bindings'][:-1]))

    def test_partial_travel_repeat_modes_and_overlapping_alternative_union_once(self):
        t=dict(self.review['target']);b=self.binding
        t['key']=b['target']; mapped=defaultdict(list)
        half=b['low']+.5*(b['high']-b['low'])
        # Two modes, repeated journeys and an alternative share the same target.
        for unused in range(4):network_intervals([t],{t['key']:[(b['low'],half)]},mapped)
        covered=sum(z-a for spans in mapped.values() for a,z in merge_intervals(spans))
        self.assertAlmostEqual(covered,9.155569770460744*.5)
        mapped.clear() # deletion removes all evidence; the reference awards nothing itself.
        self.assertEqual(sum(z-a for spans in mapped.values() for a,z in spans),0)


if __name__ == '__main__':unittest.main()
