"""Independent finite geometry, ownership, evidence and negative controls."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest
from shapely.geometry import LineString, Point
from scripts.ncn_five_case_correspondence import load_review,corrected_path,ROOT
from scripts.ncn_trial.journey_replay import projector
from scripts.ncn_trial.open_completion import POLICY,edge_interval
from scripts.ncn_binding_attainability import network_intervals
from scripts.ncn_trial.coverage import merge_intervals
from collections import defaultdict

HERE=ROOT/'docs/ncn-corridor-policy/resolution'

class FiveCaseTests(unittest.TestCase):
    def setUp(self):self.review=load_review()

    def test_continuous_bound_finite_order_and_prior_ownership(self):
        for case,bound in zip(self.review['cases'],[6.94,10.56]):
            p=case['path'];project=projector({'projection_origin':p['origin']})
            line=LineString([project(c) for c in p['coordinates']]);targets={t['key']:t for t in case['targets']}
            for b in case['bindings']:
                t=targets[b['target']];target=LineString([project(c)for c in t['coords']])
                self.assertTrue(0<=b['start']<b['end']<=line.length+1e-7)
                self.assertTrue(0<=min(b['low'],b['high'])<max(b['low'],b['high'])<=t['length'])
                # Every source bend is an explicit binding boundary. Affine
                # endpoint norms therefore prove the bound for the WHOLE span.
                self.assertFalse(any(b['start']<s<b['end']for s in p['positions']))
                for s,t in [(b['start'],b['low']),(b['end'],b['high'])]:
                    self.assertLess(line.interpolate(s).distance(target.interpolate(t)),bound)
                for old in p['bindings']:
                    self.assertLessEqual(min(old['end'],b['end'])-max(old['start'],b['start']),1e-7)
            self.assertEqual(corrected_path(p,case)['bindings'][:len(p['bindings'])],p['bindings'])

    def test_changed_source_fails_closed(self):
        for case in self.review['cases']:
            for change in [lambda p:p.update(way='77611523'),lambda p:p.update(route='NCN:7'),lambda p:p.update(modes=['cycling']),lambda p:p.update(source_geometry_sha256='0'*64),lambda p:p['nodes'].reverse(),lambda p:p['coordinates'][0].__setitem__(0,0),lambda p:p['bindings'][0].update(high=p['bindings'][0]['high']+1)]:
                p=copy.deepcopy(case['path']);change(p)
                with self.assertRaises(ValueError):corrected_path(p,case)

    def test_no_national_gate_change_and_bradford_third_arm_excluded(self):
        self.assertEqual((POLICY['maximum_offset_m'],POLICY['maximum_angle_degrees']),(10.,15.))
        self.assertFalse(POLICY['endpoint_extrapolation'])
        case=self.review['cases'][0];p=case['path'];project=projector({'projection_origin':p['origin']});xy=[project(c)for c in p['coordinates']]
        t=next(t for t in case['targets']if t['key'].startswith('open-edge:217a'))
        self.assertIsNone(edge_interval(xy[1],xy[2],LineString([project(c)for c in t['coords']])))
        self.assertFalse(any(b['target'].startswith('open-edge:2a8582')for b in corrected_path(p,case)['bindings']))
        self.assertEqual({c['way']for c in self.review['cases']},{'159311690','30268009'})

    def test_current_publisher_exactly_preserves_all_reviewed_target_vertices(self):
        for case,name in zip(self.review['cases'],['bradford','derry']):
            features=json.loads((HERE/'sources'/f'{name}-official.json').read_text())['features']
            vertices=set()
            for f in features:
                a=f['attributes']
                if a['RouteNo']==int(case['route'].split(':')[1])and a['OpenStatus']=='Open'and a['RouteCat']=='Main Route':
                    vertices.update(tuple(round(v,10)for v in c)for p in f['geometry']['paths']for c in p)
            for t in case['targets']:
                self.assertTrue(all(tuple(c)in vertices for c in t['coords']))

    def test_derry_held_and_admitted_pieces_resolved_without_reclassified_road(self):
        case=self.review['cases'][1];p=case['path'];allb=corrected_path(p,case)['bindings']
        selected=json.loads((HERE.parent/'evidence.json').read_text())['cases'];case_e=next(c for c in selected if c['way']==p['way'])
        for r in case_e['candidates']:
            covered=merge_intervals([sorted([b['low'],b['high']])for b in allb if b['target']==r['target_key']])
            got=sum(max(0,min(r['high_m'],z)-max(r['low_m'],a))for a,z in covered)
            self.assertAlmostEqual(got,r['high_m']-r['low_m'],places=7)
        # Historical hold was geometry, not a dated de-designation. No way on
        # the reclassified approach road is added by this review.
        osm=json.loads((HERE/'sources/osm-local.json').read_text())['derry']
        path=next(w for w in osm['ways']if str(w['id'])==p['way'])
        self.assertEqual(path['nodes'],p['nodes']);self.assertEqual(path['coordinates'],p['coordinates'])
        self.assertEqual(path['tags']['highway'],'cycleway')

    def test_partial_repeat_modes_and_shared_canonical_geometry_union_once(self):
        for c in self.review['cases']:
            b=c['bindings'][0];t=next(t for t in c['targets']if t['key']==b['target'])
            half=(b['low']+b['high'])/2;spans=sorted([b['low'],half]);mapped=defaultdict(list)
            network_intervals([t],{t['key']:[spans]},mapped)
            first=sum(z-a for v in mapped.values()for a,z in merge_intervals(v))
            for unused in range(5):network_intervals([t],{t['key']:[spans]},mapped)
            self.assertAlmostEqual(first,sum(z-a for v in mapped.values()for a,z in merge_intervals(v)))
            self.assertGreater(first,0);mapped.clear();self.assertEqual(dict(mapped),{})

if __name__=='__main__':unittest.main()
